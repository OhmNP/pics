# PhotoSync Installation Script
# Copies built files to installation directory

param(
    [string]$InstallPath = "$env:LOCALAPPDATA\PhotoSync",
    [string]$VcpkgPath = "C:\Users\parim\Desktop\vcpkg",
    [switch]$SkipUI = $false
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PhotoSync - Installation Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

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

# Get server path
#$serverPath = $PSScriptRoot

# Check if files are built
if (-not (Test-Path "build\Release\PhotoSyncServer.exe")) {
    Write-Host "ERROR: Server not built!" -ForegroundColor Red
    Write-Host "Run .\Build.ps1 first" -ForegroundColor Yellow
    exit 1
}

# Check if this is an update
$isUpdate = Test-Path "$InstallPath\bin\PhotoSyncServer.exe"
if ($isUpdate) {
    Write-Step "Detected existing installation - performing update..."
    
    # Backup data directory
    $dataPath = "$InstallPath\data"
    if (Test-Path $dataPath) {
        $backupPath = "$InstallPath\data.backup.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Write-Info "Backing up data to: $backupPath"
        Copy-Item -Path $dataPath -Destination $backupPath -Recurse -Force
        Write-Success "Data backed up"
    }
    Write-Host ""
}

# Create directory structure
Write-Step "Installing to $InstallPath..."

$binPath = "$InstallPath\bin"
$dataPath = "$InstallPath\data"
$webPath = "$InstallPath\web"
$configPath = "$InstallPath\config"
$testPath = "$InstallPath\test-client"

foreach ($dir in @($binPath, $dataPath, $webPath, $configPath, $testPath)) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
}

# Copy server executable
Write-Info "Copying server executable..."
Copy-Item "build\Release\PhotoSyncServer.exe" "$binPath\" -Force

# Copy DLLs from vcpkg
Write-Info "Copying dependencies from vcpkg..."
$vcpkgBin = "$VcpkgPath\installed\x64-windows\bin"
if (Test-Path $vcpkgBin) {
    $dlls = @("libcrypto-3-x64.dll", "libssl-3-x64.dll", "zlib1.dll", "sqlite3.dll", "boost_program_options-vc143-mt-x64-1_89.dll")
    foreach ($dll in $dlls) {
        $dllPath = "$vcpkgBin\$dll"
        if (Test-Path $dllPath) {
            Copy-Item $dllPath "$binPath\" -Force
            # Also copy to test client directory for MockClient
            Copy-Item $dllPath "$testPath\" -Force
            Write-Info "  Copied $dll to bin\ and test-client\"
        }
        else {
            Write-Info "  Warning: $dll not found in vcpkg bin"
        }
    }
}
else {
    Write-Host "WARNING: vcpkg bin directory not found at $vcpkgBin" -ForegroundColor Yellow
    Write-Host "You may need to manually copy DLLs or specify correct -VcpkgPath" -ForegroundColor Yellow
}

# Copy config (don't overwrite if exists)
if (-not (Test-Path "$configPath\config.json")) {
    Write-Info "Creating default configuration..."
    $defaultConfig = @{
        sync_port      = 50505
        api_port       = 50506
        storage_dir    = ".\data\storage"
        max_storage_gb = 100
    } | ConvertTo-Json
    $defaultConfig | Out-File "$configPath\config.json" -Encoding UTF8
}
else {
    Write-Info "Configuration already exists, preserving..."
}

# Copy test client
if (Test-Path "test-client\build\Release\MockClient.exe") {
    Write-Info "Copying test client..."
    Copy-Item "test-client\build\Release\MockClient.exe" "$testPath\" -Force
}

# Copy UI Dashboard
if (-not $SkipUI -and (Test-Path "..\dashboard\dist")) {
    Write-Info "Deploying UI Dashboard..."
    Copy-Item "..\dashboard\dist\*" "$webPath\" -Recurse -Force
    Write-Success "UI Dashboard deployed"
}
else {
    if ($SkipUI) {
        Write-Info "Skipping UI Dashboard (as requested)"
    }
    else {
        Write-Warning "UI Dashboard not built - run Build.ps1 without -SkipUI"
    }
}

# Create start script
Write-Info "Creating launcher scripts..."

$startScript = @"
@echo off
echo Starting PhotoSync Server...
echo.
cd /d "%~dp0"
bin\PhotoSyncServer.exe
pause
"@
$startScript | Out-File "$InstallPath\Start-Server.bat" -Encoding ASCII

$testScript = @"
@echo off
echo Running PhotoSync Test Client...
echo.
cd /d "%~dp0test-client"
MockClient.exe --photos 10
pause
"@
$testScript | Out-File "$InstallPath\Test-Server.bat" -Encoding ASCII

# Create version file
$version = "0.2.0"
$versionInfo = @"
Version: $version
Installed: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
UI Dashboard: $(-not $SkipUI)
"@
$versionInfo | Out-File "$InstallPath\version.txt" -Encoding ASCII

Write-Success "Installation complete!"
Write-Host ""

# Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Installation Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Installation Directory:" -ForegroundColor White
Write-Host "  $InstallPath" -ForegroundColor Yellow
Write-Host ""
Write-Host "Directory Structure:" -ForegroundColor White
Write-Host "  bin          - Server executable" -ForegroundColor Gray
Write-Host "  config       - Configuration (preserved on update)" -ForegroundColor Gray
Write-Host "  data         - Database and photos (preserved on update)" -ForegroundColor Gray
if (-not $SkipUI -and (Test-Path "$webPath\index.html")) {
    Write-Host "  web          - UI Dashboard" -ForegroundColor Gray
}
Write-Host "  test-client  - Test tools" -ForegroundColor Gray
Write-Host ""
Write-Host "To start the server:" -ForegroundColor White
Write-Host "  1. Navigate to: $InstallPath" -ForegroundColor Yellow
Write-Host "  2. Double-click: Start-Server.bat" -ForegroundColor Yellow
Write-Host ""
if (-not $SkipUI -and (Test-Path "$webPath\index.html")) {
    Write-Host "To access UI Dashboard:" -ForegroundColor White
    Write-Host "  1. Start the server (above)" -ForegroundColor Gray
    Write-Host "  2. Open browser: http://localhost:50506" -ForegroundColor Yellow
    Write-Host ""
}
Write-Host "To test the server:" -ForegroundColor White
Write-Host "  Double-click: Test-Server.bat" -ForegroundColor Yellow
Write-Host ""
Write-Host "To update PhotoSync:" -ForegroundColor White
Write-Host "  1. Run .\Build.ps1" -ForegroundColor Yellow
Write-Host "  2. Run .\Install.ps1" -ForegroundColor Yellow
Write-Host "  Your data will be automatically preserved!" -ForegroundColor Gray
Write-Host ""
