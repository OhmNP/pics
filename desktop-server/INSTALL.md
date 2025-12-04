# PhotoSync Installation Guide

## Quick Start

### 1. Build
First, build the server and tools:
```powershell
cd C:\Users\parim\Desktop\pics\pics\desktop-server
.\Build.ps1
```

### 2. Install
Then, install to your local application data folder:
```powershell
.\Install.ps1
```

**That's it!** The server is now installed at `%LOCALAPPDATA%\PhotoSync`.

---

## Installation Details

### Build Script (`Build.ps1`)
Compiles the C++ server, test client, and builds the React UI dashboard.

**Options:**
- `-SkipUI`: Skip building the UI dashboard (useful if Node.js is missing)
- `-Clean`: Remove old build directories before building
- `-VcpkgPath "path"`: Specify custom vcpkg location

### Installation Script (`Install.ps1`)
Copies built files to the final location, **copies required DLLs (OpenSSL, SQLite, Boost)** to both server and test client, and sets up the directory structure.

**Options:**
- `-InstallPath "path"`: Install to a custom location (default: `%LOCALAPPDATA%\PhotoSync`)
- `-VcpkgPath "path"`: Path to vcpkg installation (default: `C:\Users\parim\Desktop\vcpkg`)
- `-SkipUI`: Don't copy UI dashboard files

**Updates are Safe!**
Running `.\Install.ps1` on an existing installation will:
- ✅ **Backup** your data and photos
- ✅ **Preserve** your configuration
- ✅ **Update** only the program files

---

## After Installation

### Starting the Server
Navigate to the installation directory and run the start script:
```powershell
cd $env:LOCALAPPDATA\PhotoSync
.\Start-Server.bat
```

### Accessing the UI
Open your browser to: **http://localhost:50506**

### Testing
Run the included test script to verify everything works:
```powershell
.\Test-Server.bat
```

---

## Directory Structure
```
%LOCALAPPDATA%\PhotoSync\
├── bin\              # PhotoSyncServer.exe
├── config\           # config.json (preserved)
├── data\             # Database & Photos (preserved)
├── web\              # UI Dashboard files
├── test-client\      # MockClient.exe
└── Start-Server.bat  # Launcher
```

## Troubleshooting

**"Server not built!"**
- Run `.\Build.ps1` first.

**"Node.js not found"**
- Install Node.js to build the UI, or run `.\Build.ps1 -SkipUI`.

**"Execution Policy" error**
- Run this command in PowerShell Administrator:
  `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`
