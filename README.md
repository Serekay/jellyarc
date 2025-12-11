# Jellyarc - Jellyfin Android TV Client

Android TV client for Jellyfin with built-in Jellyseerr integration and Tailscale VPN support.

## Quick Install on TV

Open a browser on your TV and go to:

**https://serekay.github.io/jellyarc**

The download starts automatically. Then open the APK and install.

---

## Requirements

- Android TV 11 or newer
- Jellyfin server with [Requests Bridge Plugin](https://github.com/Serekay/jellyfin-requests-bridge) installed
- Optional: Tailscale account for remote access

---

## Features

- Jellyseerr Discover & Request integration
- Built-in Tailscale VPN client
- Automatic in-app updates

---

## Installation Options

### Option A: Direct download on TV (easiest)
1. Open a browser on the TV (e.g. **BrowseHere**)
2. Go to: `serekay.github.io/jellyarc`
3. Download starts automatically
4. Open the APK and install (enable "Unknown sources" if prompted)

### Option B: USB stick / FTP transfer
1. Download the APK from [GitHub Releases](https://github.com/Serekay/jellyarc/releases)
2. Transfer to TV via USB stick or FTP (e.g. CX File Explorer)
3. Open the APK on TV and install

### After installation
1. Start the app
2. Connect to your Jellyfin server
3. Done

---

## Remote Access with Tailscale

This app includes an integrated Tailscale client for secure remote access without opening ports.

### Setup
1. When adding a server, choose "Connect via Tailscale"
2. The app shows a code - authorize it in your Tailscale admin dashboard
3. Use your server's Tailscale address (e.g. `http://100.x.x.x:8096`)

You can also switch existing servers to Tailscale in Settings -> Edit Server.

---

## In-App Updates

The app checks for updates on startup and can update itself. When prompted:
1. Allow "Install unknown apps" for Jellyarc
2. Confirm the installation

---

## Troubleshooting

- **"App not installed"**: Check free storage on TV, free up space and retry
- **"Unknown sources"**: Enable per app in TV security settings
- **Tailscale timeout**: Restart the TV and try again (known Android TV bug)
