# Shop test: shop server on a VPS, reached over the internet

This runs the InScan shop server on a small rented server, at `https://inscan.launchworks.dev`.
Phones reach it over the ordinary internet: no Tailscale on the phone.

It is a separate server from the field-test one on Proxmox (`DEPLOY-PROXMOX.md`), with its own
database. Nothing at the owner's home is exposed.

> Everything here runs in the **VPS's shell over SSH**, as root, except section 2 (DNS).

## What the internet can reach

- Caddy listens on ports 80 and 443 and passes requests to the server. It gets and renews the
  HTTPS certificate by itself.
- The server listens on `127.0.0.1:8080` only. **Keep it that way.** The limit on setup-code
  guesses counts by the address Caddy reports, so the server must not be reachable around Caddy.
- Without a connected phone's token, the only things that answer are `/health` and the
  setup-code check. Five wrong setup codes from one network address lock that address out for
  15 minutes. Names, PIN sign-in and all shop data need a connected phone.

## 1. The VPS

- **Debian 13 (trixie)**, 1 vCPU, 1 GB RAM, 10 GB disk is plenty for a shop's phones.
- A public IPv4 address. Sign in with an SSH key, not a password.
- Open only SSH and the web ports:

```
apt update && apt install -y ufw
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable
```

## 2. DNS

Add one record for `launchworks.dev`: type **A**, host **inscan**, value the VPS's IPv4 address.

As of 2026-11-17 `launchworks.dev` uses Namecheap's hosting nameservers
(`dns1.namecheaphosting.com`), so the record is added in **cPanel → Zone Editor**, not on
Namecheap's Advanced DNS tab.

Check it from the VPS before going on. Caddy can't get a certificate until this answers:

```
getent hosts inscan.launchworks.dev        # → the VPS's own address
```

## 3. Java, a service user and the server

The same as `DEPLOY-PROXMOX.md` sections 2 and 3: install Java, add the `inscan` user, unzip the
`inscan-server` artifact from the latest green CI run on `main` into `/opt`, and create the same
`inscan.service`. Get the zip onto the VPS with `scp`.

```
systemctl daemon-reload && systemctl enable --now inscan
curl -s http://127.0.0.1:8080/health        # → {"status":"ok"}
```

## 4. Caddy and HTTPS

```
apt install -y caddy
```

Replace `/etc/caddy/Caddyfile` with:

```
inscan.launchworks.dev {
    reverse_proxy 127.0.0.1:8080
}
```

```
systemctl reload caddy
curl -s https://inscan.launchworks.dev/health     # → {"status":"ok"}, from anywhere
```

Check the door is the only door. From another computer, this must fail to connect:

```
curl -m 5 http://<the VPS's address>:8080/health
```

## 5. People and phones

The same as `DEPLOY-PROXMOX.md` section 5, without the Tailscale step. The address to enter in
the app is `https://inscan.launchworks.dev`.

A new database has nobody in it. Add the shop's people first, then make one setup code per phone.

## 6. Backups and updates

The same as `DEPLOY-PROXMOX.md` section 7 and "Updating". Copy backups off the VPS: it is one
rented disk. Once the shop enters customer details, the database and its backups are sensitive.

Keep the VPS patched:

```
apt install -y unattended-upgrades
```

## Watching for guessing

Each lockout is one line in the server's log, with the address it came from, and one
`enroll.lockout` row in the shop's records (without the address):

```
journalctl -u inscan | grep "wrong setup codes"
```

A few of these over a week is the internet being the internet. A steady stream from many addresses
is worth a look: make setup codes only when a phone is about to be connected, so there is rarely
a live code to guess.

## Taking it down

Stop both services and take a last backup. Remove the DNS record. Phones keep what they have and
show the offline banner; nothing on them is deleted.
