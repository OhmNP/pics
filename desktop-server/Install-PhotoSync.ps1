# PhotoSync Server - Automated Installer
# This script automates the installation of PhotoSync server and its dependencies
# Run as Administrator for best results

param(
    [string]$InstallPath = "$env:LOCALAPPDATA\PhotoSync",
    [string]$VcpkgPath = "$env:LOCALAPPDATA\vcpkg",
    [switch]$SkipVcpkg = $false,
    [switch]$BuildOnly = $false,
    [switch]$SkipUI = $false,
    [switch]$UpdateOnly = $false
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PhotoSync Server - Automated Installer" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Functions
function Write-Step {
    param([string]$Message)
    Write-Host "[*] $Message" -ForegroundColor Green
}

function Write-Info {
    param([string]$Message)
    Write-Host "    $Message" -ForegroundColor Gray
}

function Write-Success {
    param([string]$Message)
    Write-Host "[✓] $Message" -ForegroundColor Green
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "[✗] $Message" -ForegroundColor Red
}

function Test-Administrator {
    $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentUser)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# Check prerequisites
Write-Step "Checking prerequisites..."

# Check for Git
try {
    $gitVersion = git --version
    Write-Info "Git found: $gitVersion"
}
catch {
    Write-Error-Custom "Git is not installed. Please install Git from https://git-scm.com/"
    exit 1
}

# Check for CMake
try {
    $cmakeVersion = cmake --version | Select-Object -First 1
    Write-Info "CMake found: $cmakeVersion"
}
catch {
    Write-Error-Custom "CMake is not installed. Please install CMake or Visual Studio with CMake tools."
    exit 1
}

# Check for Visual Studio/Build Tools
$vsWhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (Test-Path $vsWhere) {
    $vsPath = & $vsWhere -latest -property installationPath
    Write-Info "Visual Studio found: $vsPath"
}
else {
    Write-Warning "Visual Studio not found. Build may fail if MSVC compiler is not available."
}

# Check for Node.js (for UI dashboard)
if (-not $SkipUI) {
    try {
        $nodeVersion = node --version
        Write-Info "Node.js found: $nodeVersion"
    }
    catch {
        Write-Warning "Node.js not found. UI dashboard will be skipped. Install from https://nodejs.org/"
        $SkipUI = $true
    }
}

Write-Success "Prerequisites check complete"
Write-Host ""

# Install vcpkg if needed
if (-not $SkipVcpkg) {
    Write-Step "Setting up vcpkg package manager..."
    
    if (-not (Test-Path $VcpkgPath)) {
        Write-Info "Cloning vcpkg to $VcpkgPath..."
        git clone https://github.com/Microsoft/vcpkg.git $VcpkgPath
        
        Write-Info "Bootstrapping vcpkg..."
        Push-Location $VcpkgPath
        .\bootstrap-vcpkg.bat
        Pop-Location
    }
    else {
        Write-Info "vcpkg already exists at $VcpkgPath"
    }
    
    # Install dependencies
    Write-Step "Installing dependencies (this may take 10-30 minutes)..."
    Push-Location $VcpkgPath
    
    Write-Info "Installing Boost.Asio..."
    .\vcpkg install boost-asio:x64-windows
    
    Write-Info "Installing Boost.ProgramOptions..."
    .\vcpkg install boost-program-options:x64-windows
    
    Write-Info "Installing SQLite3..."
    .\vcpkg install sqlite3:x64-windows
    
    Pop-Location
    Write-Success "Dependencies installed successfully"
    Write-Host ""
}

if ($BuildOnly) {
    Write-Host "Skipping vcpkg installation (BuildOnly mode)" -ForegroundColor Yellow
    Write-Host ""
}

# Build PhotoSync Server
Write-Step "Building PhotoSync Server..."

$serverPath = Split-Path -Parent $PSScriptRoot
if (-not $serverPath) {
    $serverPath = Get-Location
}

Write-Info "Server source: $serverPath"

# Configure with CMake
Write-Info "Configuring build with CMake..."
$toolchainFile = "$VcpkgPath\scripts\buildsystems\vcpkg.cmake"

Push-Location $serverPath

# Clean old build
if (Test-Path "build") {
    Write-Info "Cleaning old build directory..."
    Remove-Item -Recurse -Force build
}

cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE=$toolchainFile
if ($LASTEXITCODE -ne 0) {
    Write-Error-Custom "CMake configuration failed!"
    Pop-Location
    exit 1
}

# Build
Write-Info "Building server (Release)..."
cmake --build build --config Release
if ($LASTEXITCODE -ne 0) {
    Write-Error-Custom "Build failed!"
    Pop-Location
    exit 1
}

Write-Success "Server built successfully"
Write-Host ""

# Build test client
Write-Step "Building test client..."
Push-Location test-client

if (Test-Path "build") {
    Remove-Item -Recurse -Force build
}

cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE=$toolchainFile
cmake --build build --config Release

Pop-Location
Write-Success "Test client built successfully"
Write-Host ""

# Build UI Dashboard
if (-not $SkipUI) {
    Write-Step "Building UI Dashboard..."
    
    $uiPath = "$serverPath\ui-dashboard"
    if (Test-Path $uiPath) {
        Push-Location $uiPath
        
        Write-Info "Installing npm dependencies..."
        npm install --silent
        
        if ($LASTEXITCODE -eq 0) {
            Write-Info "Building production bundle..."
            npm run build
            
            if ($LASTEXITCODE -eq 0) {
                Write-Success "UI Dashboard built successfully"
            }
            else {
                Write-Warning "UI Dashboard build failed. Continuing without UI."
            }
        }
        else {
            Write-Warning "npm install failed. Continuing without UI."
        }
        
        Pop-Location
    }
    else {
        Write-Warning "UI Dashboard source not found at $uiPath"
    }
    Write-Host ""
}

# Check if this is an update
$isUpdate = Test-Path "$InstallPath\PhotoSyncServer.exe"
if ($isUpdate) {
    Write-Step "Detected existing installation - performing update..."
    
    # Backup data directory
    $dataPath = "$InstallPath\data"
    if (Test-Path $dataPath) {
        $backupPath = "$InstallPath\data.backup.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Write-Info "Backing up data to $backupPath..."
        Copy-Item -Path $dataPath -Destination $backupPath -Recurse -Force
        Write-Success "Data backed up"
    }
    Write-Host ""
}

# Install to target directory
Write-Step "Installing to $InstallPath..."

if (-not (Test-Path $InstallPath)) {
    New-Item -ItemType Directory -Path $InstallPath | Out-Null
}

# Create directory structure
$binPath = "$InstallPath\bin"
$dataPath = "$InstallPath\data"
$webPath = "$InstallPath\web"
$configPath = "$InstallPath\config"

if (-not (Test-Path $binPath)) { New-Item -ItemType Directory -Path $binPath | Out-Null }
if (-not (Test-Path $dataPath)) { New-Item -ItemType Directory -Path $dataPath | Out-Null }
if (-not (Test-Path $webPath)) { New-Item -ItemType Directory -Path $webPath | Out-Null }
if (-not (Test-Path $configPath)) { New-Item -ItemType Directory -Path $configPath | Out-Null }

# Copy server
Write-Info "Copying server executable..."
Copy-Item "build\Release\PhotoSyncServer.exe" "$binPath\" -Force

# Copy config (don't overwrite if exists)
Write-Info "Copying configuration..."
if (-not (Test-Path "$configPath\config.json")) {
    if (Test-Path "config.json") {
        Copy-Item "config.json" "$configPath\" -Force
    }
}
else {
    Write-Info "Configuration already exists, preserving existing config"
}

# Copy UI Dashboard
if (-not $SkipUI -and (Test-Path "$serverPath\ui-dashboard\dist")) {
    Write-Info "Deploying UI Dashboard..."
    Copy-Item "$serverPath\ui-dashboard\dist\*" "$webPath\" -Recurse -Force
    Write-Success "UI Dashboard deployed to $webPath"
}

# Copy test client
Write-Info "Copying test client..."
$testClientPath = "$InstallPath\test-client"
if (-not (Test-Path $testClientPath)) {
    New-Item -ItemType Directory -Path $testClientPath | Out-Null
}
Copy-Item "test-client\build\Release\MockClient.exe" "$testClientPath\" -Force

# Copy documentation
Write-Info "Copying documentation..."
Copy-Item "SETUP.md" "$InstallPath\" -Force
Copy-Item "TESTING.md" "$InstallPath\" -Force
Copy-Item "BUILD.md" "$InstallPath\" -Force

# Create start script
Write-Info "Creating start script..."
$startScript = @"
@echo off
echo Starting PhotoSync Server...
echo.
cd /d "%~dp0"
bin\PhotoSyncServer.exe
pause
"@
$startScript | Out-File "$InstallPath\Start-PhotoSyncServer.bat" -Encoding ASCII

# Create version file
$version = "0.2.0"
$versionInfo = @"
Version: $version
Installed: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
UI Dashboard: $(-not $SkipUI)
"@
$versionInfo | Out-File "$InstallPath\version.txt" -Encoding ASCII

# Create test script  
$testScript = @"
@echo off
echo Running PhotoSync Test Client...
echo.
cd /d "%~dp0test-client"
MockClient.exe --photos 10
pause
"@
$testScript | Out-File "$InstallPath\Test-PhotoSyncServer.bat" -Encoding ASCII

Pop-Location
Write-Success "Installation complete!"
Write-Host ""

# Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Installation Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Installation Directory: " -NoNewline
Write-Host $InstallPath -ForegroundColor Yellow
Write-Host ""
Write-Host "Files installed:" -ForegroundColor White
Write-Host "  - bin\PhotoSyncServer.exe    (Server executable)"
Write-Host "  - config\config.json         (Configuration)"
Write-Host "  - data\                      (Database and storage)"
if (-not $SkipUI) {
    Write-Host "  - web\                       (UI Dashboard)"
}
Write-Host "  - Start-PhotoSyncServer.bat  (Launch script)"
Write-Host "  - Test-PhotoSyncServer.bat   (Test script)"
Write-Host "  - test-client\MockClient.exe (Test client)"
Write-Host "  - Documentation files"
Write-Host ""
Write-Host "To start the server:" -ForegroundColor White
Write-Host "  1. Navigate to: $InstallPath" -ForegroundColor Yellow
Write-Host "  2. Double-click: Start-PhotoSyncServer.bat" -ForegroundColor Yellow
Write-Host ""
if (-not $SkipUI) {
    Write-Host "To access UI Dashboard:" -ForegroundColor White
    Write-Host "  1. Start the server (above)" 
    Write-Host "  2. Open browser: http://localhost:50506" -ForegroundColor Yellow
    Write-Host ""
}
Write-Host "To test the server:" -ForegroundColor White
Write-Host "  1. Start the server (above)" 
Write-Host "  2. Double-click: Test-PhotoSyncServer.bat" -ForegroundColor Yellow
Write-Host ""
Write-Host "To update PhotoSync:" -ForegroundColor White
Write-Host "  Run this script again - your data will be preserved" -ForegroundColor Yellow
Write-Host ""
Write-Host "For more information, see TESTING.md in the installation directory." -ForegroundColor Gray
Write-Host ""

# Offer to add to PATH
$addToPath = Read-Host "Add PhotoSync to PATH? (y/n)"
if ($addToPath -eq 'y') {
    $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($currentPath -notlike "*$InstallPath*") {
        [Environment]::SetEnvironmentVariable("Path", "$currentPath;$InstallPath", "User")
        Write-Success "Added to PATH. Restart PowerShell to use 'PhotoSyncServer' command."
    }
    else {
        Write-Info "Already in PATH"
    }
}

# Offer to create desktop shortcuts
$createShortcuts = Read-Host "Create desktop shortcuts? (y/n)"
if ($createShortcuts -eq 'y') {
    $WshShell = New-Object -comObject WScript.Shell
    $desktop = [Environment]::GetFolderPath("Desktop")
    
    # Server shortcut
    $shortcut = $WshShell.CreateShortcut("$desktop\PhotoSync Server.lnk")
    $shortcut.TargetPath = "$InstallPath\Start-PhotoSyncServer.bat"
    $shortcut.WorkingDirectory = $InstallPath
    $shortcut.Description = "Start PhotoSync Server"
    $shortcut.Save()
    
    # Test shortcut
    $shortcut = $WshShell.CreateShortcut("$desktop\PhotoSync Test Client.lnk")
    $shortcut.TargetPath = "$InstallPath\Test-PhotoSyncServer.bat"
    $shortcut.WorkingDirectory = $InstallPath
    $shortcut.Description = "Test PhotoSync Server"
    $shortcut.Save()
    
    Write-Success "Desktop shortcuts created"
}

Write-Host ""
Write-Host "Installation complete! 🎉" -ForegroundColor Green
Write-Host ""
