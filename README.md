# Jellyfin Jellyseerr TV Integration

This repository provides the client-side integration to bring the full power of Jellyseerr directly to your Android TV via Jellyfin.

## ⚠️ Prerequisite: Server-Side Plugin (Required!)

**Before you start:** This Android TV integration requires the **Jellyfin Requests Bridge** plugin to be installed and configured on your Jellyfin server. Without it, the "Discover" features will not work.

👉 **Step 1: Install the Server Plugin first:**
[**Go to Serekay/jellyfin-requests-bridge**](https://github.com/Serekay/jellyfin-requests-bridge)

Follow the installation instructions there. Once the plugin is running on your server, proceed with the setup below.

---

<details>
<summary><h2>🌐 Bonus: Remote Access via Tailscale (Optional)</h2>
(Click to expand the setup guide for secure remote access)
</summary>

[cite_start]This guide explains how to set up **Tailscale** to access your Jellyfin server securely from anywhere, without opening ports on your router[cite: 4].

### 💻 For Desktop PC / Laptop Users
**How to connect without sharing your account credentials:**

[cite_start]If you want to give a friend or family member access on their computer without giving them your Tailscale username and password[cite: 4]:

1. Install Tailscale on the target computer.
2. Open the Command Prompt (CMD) or Terminal.
3. [cite_start]Type: `tailscale up` and press Enter[cite: 4].
4. A login link will appear in the terminal. **Send this link to the Tailscale Admin.**
5. The Admin opens the link to authorize the device. [cite_start]The user gets connected immediately without ever seeing the login credentials[cite: 4].

---

### 📺 For Android TV Users (Setup Guide)

[cite_start]We will configure an **"Always-On"** feature using ADB, ensuring the VPN starts automatically after a restart while allowing other apps (Netflix, YouTube) to bypass the VPN[cite: 3, 4].

#### Step 1: Install & Connect Tailscale on TV
1. [cite_start]**Download App:** Go to the Google Play Store on your TV and install **"Tailscale"**[cite: 8].
2. **Log In:** Open the app and select **"Log in"**. [cite_start]A code will appear[cite: 10].
3. **Authorize Device:**
   - [cite_start]Go to the [Tailscale Admin Console](https://login.tailscale.com/admin/machines) on your phone/PC[cite: 12].
   - [cite_start]Click **"Add Device"** and enter the code displayed on the TV[cite: 12].
4. [cite_start]**Verify:** The TV app should say "Connected"[cite: 16].

#### Step 2: Connect Jellyfin
1. [cite_start]Install the **Jellyfin** app on your TV[cite: 19].
2. [cite_start]Enter the **Tailscale IP address** of your server (e.g., `http://100.x.x.x:8096`)[cite: 22].
3. [cite_start]Login with your Jellyfin credentials[cite: 24].

#### Step 3: Enable "Always-On" Auto-Connect (ADB Method)
By default, Android TV might close the VPN after a reboot. [cite_start]We use **ADB TV** to fix this[cite: 3, 26].

**How this works:**
* [cite_start]**Auto-Start:** Tailscale connects silently in the background on boot[cite: 40].
* [cite_start]**Split Tunneling:** Only Jellyfin traffic uses the VPN[cite: 42]. [cite_start]**Netflix, YouTube, etc., continue to use your normal home internet**, ensuring no speed loss or geo-blocking issues[cite: 4, 43].

**Instructions:**

1. [cite_start]**Install ADB TV:** Search for "ADB TV" (or "ADB Shell") in the TV Play Store and install it[cite: 28].
2. **Enable Developer Options:**
   - [cite_start]Go to TV Settings → Device Preferences → About[cite: 31].
   - [cite_start]Click the **"Build Number" 7 times** until it says "You are a developer"[cite: 3, 31].
3. **Enable USB Debugging:**
   - [cite_start]Go to Settings → Device Preferences → Developer Options[cite: 32].
   - [cite_start]Turn **ON** "USB Debugging"[cite: 2, 33].
4. **Run Commands:**
   - [cite_start]Open the **ADB TV** app[cite: 36].
   - Enter these two commands (run them one by one):

   **Command 1 (Set Tailscale as Always-On VPN):**
   ```bash
   settings put secure always_on_vpn_app com.tailscale.ipn
    ```

    **Command 2 (Disable Lockdown - allows other apps to use normal internet):**
    ```bash
    settings put secure always_on_vpn_lockdown 0
    ```

✅ Done! Restart your TV. Tailscale will now run automatically in the background.