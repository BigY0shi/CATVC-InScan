# Field test: shop server on Proxmox, reached over Tailscale

This runs the InScan shop server in a small LXC on the Proxmox homelab. Phones reach it at
`https://<container-name>.<your-tailnet>.ts.net`, using a certificate from `tailscale serve`.
Nothing is exposed to the public internet. The server itself only listens on `127.0.0.1:8080`,
and Tailscale is the only door in.

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

## 5. People and phones

Employees are added on the server. The PIN is typed at the prompt and never put on the command line.

```
sudo -u inscan env INSCAN_DB=/var/lib/inscan/inscan.db /opt/inscan-server/bin/inscan-server add-employee "Owner Name" admin
sudo -u inscan env INSCAN_DB=/var/lib/inscan/inscan.db /opt/inscan-server/bin/inscan-server add-employee "Sam"
```

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

Stop the service, unzip the new `inscan-server.zip` over `/opt`, then start it again. Schema changes
run automatically on start and never drop data. Take a backup first anyway.
