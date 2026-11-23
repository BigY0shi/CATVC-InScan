# Field test: shop server on Proxmox, reached over Tailscale

This runs the InScan shop server in a small LXC on the Proxmox homelab. Phones reach it at
`https://<container-name>.<your-tailnet>.ts.net`, using a certificate from `tailscale serve`.
The server itself only listens on `127.0.0.1:8080`, and Tailscale is the only door in. As set up in
sections 1 to 4 nothing is exposed to the public internet. Section 4b opens the same address to the
internet, for a shop that is not on the owner's Tailscale network.

This setup is for field testing. Where the server lives for real (Hetzner or a box at the shop)
is still the owner's call, and the same steps apply anywhere Tailscale runs.

> Section 1 runs in the **Proxmox host shell**. Everything after that runs in the **container's
> console** (Proxmox UI → container → Console). Both are Linux shells. Nothing here runs on Windows.

## 1. Container

- Template: **Debian 13 (trixie)**. It ships `openjdk-21-jre-headless`, and the server needs Java 21 or newer.
- 1 vCPU, 512 MB RAM, 4 GB disk is plenty for four phones.
- Unprivileged is fine. Tailscale needs `/dev/net/tun`, so add these two lines to
  `/etc/pve/lxc/<CTID>.conf` on the Proxmox host, then restart the container:

```
lxc.cgroup2.devices.allow: c 10:200 rwm
lxc.mount.entry: /dev/net/tun dev/net/tun none bind,create=file
```

## 2. Java and a service user

```
apt update && apt install -y openjdk-21-jre-headless unzip sqlite3
useradd --system --home /var/lib/inscan --create-home --shell /usr/sbin/nologin inscan
chmod 700 /var/lib/inscan
```

## 3. Install the server

Download the `inscan-server` artifact from the latest green CI run on GitHub
(Actions → the run → Artifacts). Get `inscan-server.zip` into the container any way you like.
For example, upload it with the Proxmox UI or `scp` to the host, then `pct push`. Then:

```
unzip -o inscan-server.zip -d /opt
/opt/inscan-server/bin/inscan-server help     # prints "Unknown command. Use: serve | add-employee ... | setup-code"
```

Create `/etc/systemd/system/inscan.service`:

```
[Unit]
Description=InScan shop server
After=network-online.target
Wants=network-online.target

[Service]
User=inscan
Environment=INSCAN_DB=/var/lib/inscan/inscan.db
Environment=INSCAN_HOST=127.0.0.1
Environment=INSCAN_PORT=8080
ExecStart=/opt/inscan-server/bin/inscan-server serve
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```
systemctl daemon-reload && systemctl enable --now inscan
curl -s http://127.0.0.1:8080/health        # → {"status":"ok"}
```

## 4. Tailscale and HTTPS

MagicDNS and HTTPS certificates must be on for the tailnet. Both are on by default for new tailnets
(admin console → DNS).

```
curl -fsSL https://tailscale.com/install.sh | sh
tailscale up --hostname=inscan
tailscale serve --bg 8080
tailscale serve status                       # shows https://inscan.<your-tailnet>.ts.net → 127.0.0.1:8080
```

`tailscale serve reset` removes the proxy again.

From any device on the tailnet, opening `https://inscan.<your-tailnet>.ts.net/health` should show
`{"status":"ok"}`.

## 4b. Public address (for a shop that is not on the tailnet)

Tailscale Funnel makes the same `https://inscan.<your-tailnet>.ts.net` address reachable from the
internet, with the same certificate. Phones then need no Tailscale app, only an internet connection.
The server still listens on `127.0.0.1:8080` only.

**Before turning it on,** the server must be a version with the setup-code limit (five wrong codes
from one network address lock that address out for 15 minutes). Without it anyone on the internet can
guess setup codes as fast as they like. Check: `unzip -l /opt/inscan-server/lib/server.jar | grep EnrollLimiter`
prints a line.

Funnel must be allowed for the tailnet (admin console → Access controls; `tailscale funnel` prints a
link if it is not).

```
tailscale funnel --bg 8080
tailscale funnel status                      # shows "(Funnel on)"
```

Check it from a machine that is **not** on the tailnet: `curl -s https://inscan.<your-tailnet>.ts.net/health`
should show `{"status":"ok"}`. A machine on the tailnet takes the private route and proves nothing.

**Turning it off again** (back to tailnet only):

```
tailscale funnel --https=443 off
tailscale serve --bg 8080
```

With a public address, section 5 step 1 (the Tailscale app on each phone) is not needed.

## 5. People and phones

People are added on the server, each with a role: Employee (the default), Management or Admin.
The PIN is typed at the prompt and never put on the command line.

```
sudo -u inscan env INSCAN_DB=/var/lib/inscan/inscan.db /opt/inscan-server/bin/inscan-server add-employee "Owner Name" Admin
sudo -u inscan env INSCAN_DB=/var/lib/inscan/inscan.db /opt/inscan-server/bin/inscan-server add-employee "Mia" Management
sudo -u inscan env INSCAN_DB=/var/lib/inscan/inscan.db /opt/inscan-server/bin/inscan-server add-employee "Sam"
```

What each role can do is in `docs/ui-rebuild/roles-permissions-spec.md` §3. Management can make
setup codes; Employees can't.

For each phone:

1. Install the Tailscale app and sign it in to the same tailnet.
2. Install the InScan APK (the `app-debug` artifact from the same CI run).
3. On the server, make a one-time setup code. It works once, within 24 hours:

   ```
   sudo -u inscan env INSCAN_DB=/var/lib/inscan/inscan.db /opt/inscan-server/bin/inscan-server setup-code
   ```

4. In the app, enter the address `https://inscan.<your-tailnet>.ts.net`, the code, and a name for the
   phone (e.g. "Front counter"). Then pick a name and enter the PIN.

A phone that loses signal keeps working. Its banner shows how many changes are waiting, and they
go out when the shop system can be reached again. Sign-ins last 12 hours. Changes made while signed
out of an expired session wait until someone signs in again.

## 6. Items already on a phone

If a phone has items from before the shop system, the orange banner offers **Upload**. Complete
items are copied into the shop system. Items with gaps (no price, an unknown vehicle type) stay
listed with the reason, and **Complete it in the form** fixes and logs them one at a time. Nothing
is deleted from the phone.

## 7. Backups

The database is one SQLite file in WAL mode. Take a consistent copy while the server runs:

```
sudo -u inscan sqlite3 /var/lib/inscan/inscan.db ".backup /var/lib/inscan/backup-$(date +%F).db"
```

Copy those backups off the container. A Proxmox scheduled backup of the whole container also works.
The database holds customer details once sales are recorded (M3), so treat backups as sensitive.

## Updating

1. **Back up first,** with the server still running (section 7):

   ```
   sudo -u inscan sqlite3 /var/lib/inscan/inscan.db ".backup /var/lib/inscan/before-update-$(date +%F).db"
   ```

   Don't copy `inscan.db` on its own instead. In WAL mode, recent writes can sit in `inscan.db-wal`
   until the file is checkpointed, and stopping the service doesn't always do that. In a test on
   2026-11-01, a plain copy of `inscan.db` taken after the server stopped held 2 of 13 audit rows.
2. Stop the service, unzip the new `inscan-server.zip` over `/opt`, then start it again.
3. Schema changes run automatically on start (or on the first `add-employee` or `setup-code`), in one
   transaction: either all of them land or the server refuses to start and the file is unchanged.
   They never drop data.
4. Check `curl -s http://127.0.0.1:8080/health`, then sign in on one phone.

**Rolling back** means stopping the service, moving `inscan.db` and any `inscan.db-wal` and
`inscan.db-shm` next to it into a `.exile/<date>/` folder, copying the backup in as `inscan.db`, and
starting the older version. An older server
can't undo a newer schema by itself.

**The server-groundwork update (schema 3 to 8)** rewrites every stored time to one fixed-width
format, adds when-it-happened times, Shop settings, typed status changes, and roles. Every person
keeps a role equal to their old one: `admin` becomes Admin, everyone else Employee. Phones already
in use keep working unchanged.
