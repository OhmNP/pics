# PhotoSync Build Script
param(
    [string]$VcpkgPath = "C:\Users\parim\Desktop\vcpkg",
    [switch]$SkipUI
)
$ErrorActionPreference = "Stop"
Write-Host "Building PhotoSync Server..." -ForegroundColor Cyan
cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE="$VcpkgPath\scripts\buildsystems\vcpkg.cmake"
if ($LASTEXITCODE -ne 0) { exit 1 }
cmake --build build --config Release
if ($LASTEXITCODE -ne 0) { exit 1 }
Write-Host "Server built successfully!" -ForegroundColor Green
Write-Host "Next: Run Install.ps1" -ForegroundColor Gray
