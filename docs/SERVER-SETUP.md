# Connect your music server

Musonic is the Android player. Navidrome is a separate, open-source server that indexes your music folder and streams it to the app. You can run Navidrome in Docker on a PC, NAS or home server. The music stays on your server; no Musonic cloud account is needed.

Already running a Subsonic or OpenSubsonic server? Skip Docker and enter its URL, username and password in Musonic. A Navidrome account works with the default password/token authentication mode. Other server implementations may expose different features.

## Start Navidrome with Docker

Install Docker with the Compose plugin (included in Docker Desktop). The example pins Navidrome **0.64.2**, and mounts your music read-only.

```sh
git clone https://github.com/ljaehwan/Musonic.git
cd Musonic/examples/navidrome
cp .env.example .env
mkdir -p data
```

On Windows PowerShell, use `Copy-Item .env.example .env` and `New-Item -ItemType Directory -Force data` for the last two commands. Docker Desktop must be running with Linux containers.

Edit `.env`:

| Setting | What to enter |
| --- | --- |
| `MUSIC_PATH` | Your existing music folder, using an absolute path. On Docker Desktop, a path such as `C:/Users/you/Music` works. |
| `DATA_PATH` | Keep `./data`, or use a dedicated writable folder. |
| `NAVIDROME_UID`, `NAVIDROME_GID` | On Linux, run `id -u` and `id -g`. These IDs must own the data folder and be able to read the music folder. |
| `BIND_ADDRESS` | Keep `127.0.0.1` while creating the first account. |
| `HTTP_PORT` | Keep `4533`, or choose another free port. |

Quote paths that contain spaces. The example refuses to create a missing music folder, which helps catch a mistyped path. It does not change ownership or permissions on your music collection.

```sh
docker compose up -d
docker compose logs --tail=30 navidrome
```

Open **http://localhost:4533** on the Docker host. Create the initial admin account and wait for albums to appear. For everyday listening, you can create a separate non-admin user in Navidrome. On a headless NAS, use an SSH tunnel for initial setup or configure a trusted LAN binding before opening the page.

The container must be able to write to `DATA_PATH`. If you get a permission error, correct the configured UID/GID or the ownership of this dedicated data folder. Avoid changing permissions on the entire music collection just to fix the data directory.

## Connect Musonic on your phone

1. In `.env`, set `BIND_ADDRESS` to your server's LAN IP. On a trusted home network, `0.0.0.0` listens on all host interfaces. Run `docker compose up -d` again.
2. Connect the phone to the same Wi-Fi. Open `http://YOUR_SERVER_LAN_IP:4533` in the phone's browser first. Use your chosen port if you changed it.
3. Install the [Musonic APK](https://github.com/ljaehwan/Musonic-releases/releases/latest).
4. Enter that same URL and your Navidrome username/password. For a local HTTP server, open **Advanced settings** and enable the HTTP option before connecting.
5. Open an album and play a song.

`localhost` on the phone refers to the phone, not the computer running Docker. `navidrome` is a Docker service name, not a URL your phone can normally resolve. Use the host's LAN IP or a working HTTPS domain.

The GitHub APK supports HTTP only after opt-in. The Play build requires HTTPS.

## Listen away from home

Use an HTTPS reverse proxy with a valid certificate and connect Musonic to a URL such as `https://music.example.com`. Alternatively, use your own VPN to reach the LAN server. Keep the raw HTTP port off the public internet. If a proxy adds a browser-only login page, Musonic's API requests cannot complete that login; the server endpoint must support the app's normal authentication.

## Everyday commands

```sh
docker compose ps
docker compose logs --tail=50 navidrome
docker compose stop
docker compose up -d
```

Back up the data directory and your music separately. To upgrade, back up first, select a newer supported image version in `compose.yaml`, then run `docker compose pull && docker compose up -d`. Read the server release notes before changing versions; do not downgrade an already migrated database without a suitable backup.

## If something does not work

| Symptom | Check |
| --- | --- |
| Phone cannot open the server page | Same Wi-Fi/VPN, host address, port binding and firewall. |
| Login fails | Use the Navidrome account, not a GitHub account. Avoid adding `/app` or `/rest` to the base URL. |
| Library is empty | Music path, readable file permissions and scan logs. |
| HTTPS certificate error | Certificate validity, domain name and the phone's clock. |
| Quality setting has no effect | Server transcoding availability; replay the track after changing the quality. |

Reference: [Navidrome Docker installation](https://www.navidrome.org/docs/installation/docker/), [first-run setup](https://www.navidrome.org/docs/getting-started/), and [Navidrome releases](https://github.com/navidrome/navidrome/releases).
