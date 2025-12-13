# UI/UX Plan: Seamless Pairing Process

This document outlines the improved User Experience (UX) and User Interface (UI) design for pairing the PhotoSync Android Client with the Desktop Server.

## 1. Problem Statement
The current pairing process relies on manual IP address entry or a basic TCP connection flow without clear guidance on Token entry. Users may struggle to find the "Pairing Token" field or the Server's IP address.

## 2. Proposed Goals
- **Zero-Configuration Discovery:** The app should automatically find the server.
- **Seamless Pairing:** The user should ideally scan a QR code or enter a single 6-digit PIN to pair.
- **Clear Feedback:** The UI must communicate connection status, authentication requirements, and success clearly.

## 3. Detailed UX Flow

### A. Server Side (Dashboard)
**Component:** `Pairing.tsx` (Pair New Device Dialog)
**Updates:**
1.  **QR Code Display:**
    - Generate a QR Code containing a JSON payload: `{"ip": "<SERVER_LAN_IP>", "port": 50505, "token": "<GENERATED_TOKEN>"}`.
    - Display this QR code prominently alongside the human-readable 6-digit token.
    - *Note:* The backend needs to expose its LAN IP via the API (e.g., `/api/info`) for the frontend to encode it.

### B. Client Side (Android App)
**Screen:** `PairingScreen.kt`
**Updates:**

#### State 1: Discovery (Default View)
- **Top Section:** "Scanning for PhotoSync Server..." (Radar animation).
- **Middle Section:** List of Discovered Servers (via UDP).
    - Card: `[Server Name / Hostname] (192.168.1.X)` -> Button: **"Connect"**
- **Bottom Section:** "Having trouble?"
    - Button: **"Scan QR Code"** (Opens Camera).
    - Button: **"Enter IP Manually"** (Opens Form).

#### State 2: Found Server (User taps "Connect")
- **Prompt:** "Enter Pairing Code"
    - An overlay or new screen requesting *only* the 6-digit code.
    - The IP is already known from Discovery.
    - Input: 6-digit numeric field.
    - Action: User enters code -> App sends `HELLO <current_device_id> <entered_token>`.

#### State 3: Manual QR Scan
- User scans the QR code from the Dashboard.
- App parses JSON.
- **Success:** Automatically sets IP, Port, and Token -> Initiates Connection -> Shows "Success".

#### State 4: Manual Entry (Fallback)
- Fields:
    - Server IP (e.g., 192.168.1.5)
    - Pairing Token (e.g., 123456)
- Action: "Pair Device".

## 4. Implementation Steps

### Phase 1: Server Updates
1.  **Backend API:** Add `/api/info` to return Server IP (detected via socket or config).
2.  **Dashboard UI:**
    - Install `qrcode.react` (or similar).
    - Fetch server IP.
    - Render QR Code with JSON payload.

### Phase 2: Android Updates
1.  **ViewModel:** Refactor `PairingViewModel` to handle "Discovered Server" state separately from "Connected".
2.  **Discovery Logic:** Ensure `UdpDiscoveryListener` runs on screen open.
3.  **UI Redesign:**
    - Replace basic manual form with the "Discovery List" layout.
    - Implement the "Enter Token" dialog for discovered servers.
    - Create a dedicated "Manual Setup" sub-screen.

## 5. Technical Considerations
- **IP Detection:** The server might have multiple IPs. The UDP broadcast sends on `0.0.0.0` (all interfaces) but the message payload could include the specific IP, or the client detects the sender IP from the UDP packet header (current implementation does this).
- **Security:** The QR code contains the token, so it must be treated as sensitive (expires in 15m).

