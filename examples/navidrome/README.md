# Navidrome for Musonic

A Docker Compose example for serving your own music to Musonic. Your music is mounted read-only; the database and cache live in a separate data directory.

Follow the [English setup guide](../../docs/SERVER-SETUP.md) or [한국어 안내](../../docs/SERVER-SETUP.ko.md) before starting the container. Copy `.env.example` to `.env`, set your music path and file permissions, and create the data directory first.

```sh
docker compose up -d
```

The initial binding is **127.0.0.1:4533**, so first-run account creation is local to the Docker host. The guide explains how to enable phone access afterward.

Navidrome is a separate project. See its [documentation](https://www.navidrome.org/docs/) and [license](https://github.com/navidrome/navidrome/blob/master/LICENSE).
