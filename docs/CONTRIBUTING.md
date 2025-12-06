# Contributing to PhotoSync

We welcome contributions! This guide will help you get your development environment set up.

## Prerequisites

*   **Windows 10/11**
*   **Visual Studio 2022** with "Desktop development with C++"
*   **Git**
*   **Android Studio** (for Client development)
*   **Node.js 18+** (for Dashboard development)

## Desktop Server (C++)

The server is located in `./desktop-server`.

### One-Step Setup
We provide a PowerShell script to automate dependency installation (vcpkg) and building:
```powershell
cd desktop-server
.\Install-PhotoSync.ps1
```

### Manual Build
```powershell
cd desktop-server
cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE=C:\path\to\vcpkg\scripts\buildsystems\vcpkg.cmake
cmake --build build --config Release
```

### Running Tests
Start the server, then run the mock client:
```powershell
.\test-client\build\Release\MockClient.exe --photos 10
```

## Android Client (Kotlin)

The client is located in `./android-client`.

1.  Open the folder in **Android Studio**.
2.  Sync Gradle project.
3.  Ensure your `local.properties` has the correct SDK path.
4.  Run on an Emulator or Device.
    *   **Note**: If using Emulator, you may need to forward ports or use `10.0.2.2` to reach the localhost server.

## UI Dashboard (React)

The dashboard source is in `./desktop-server/ui-dashboard`.

### Development
```bash
cd desktop-server/ui-dashboard
npm install
npm run dev
```

### Build
To build the static assets for the C++ server to serve:
```bash
npm run build
```
This generates files in `dist/`.

## Code Style

*   **C++**: Follow standard C++17 practices. Use `clang-format` if available.
*   **Kotlin**: Follow Android Kotlin Style Guide.
*   **Commits**: Use clear, descriptive commit messages.
