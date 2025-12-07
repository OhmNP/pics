# PhotoSync Build Script
param(
    [string]$VcpkgPath = "C:\Users\parim\source\repos\vcpkg",
    [switch]$SkipUI
)
$ErrorActionPreference = "Stop"
Write-Host "Building PhotoSync Server..." -ForegroundColor Cyan
cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE="$VcpkgPath\scripts\buildsystems\vcpkg.cmake"
if ($LASTEXITCODE -ne 0) { exit 1 }
cmake --build build --config Release
if ($LASTEXITCODE -ne 0) { exit 1 }

# Build UI Dashboard
if (-not $SkipUI) {
    Write-Host "Building UI Dashboard..." -ForegroundColor Cyan
    $uiPath = "$PSScriptRoot\ui-dashboard"
    if (Test-Path $uiPath) {
        Push-Location $uiPath
        
        Write-Host "Installing npm dependencies..." -ForegroundColor Gray
        npm install --silent
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Building production bundle..." -ForegroundColor Gray
            npm run build
            
            if ($LASTEXITCODE -eq 0) {
                Write-Host "UI Dashboard built successfully" -ForegroundColor Green
            }
            else {
                Write-Warning "UI Dashboard build failed."
            }
        }
        else {
            Write-Warning "npm install failed."
        }
        
        Pop-Location
    }
    else {
        Write-Warning "UI Dashboard source not found at $uiPath"
    }
}

Write-Host "Server built successfully!" -ForegroundColor Green
Write-Host "Next: Run Install.ps1" -ForegroundColor Gray
