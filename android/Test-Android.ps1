
param(
    [switch]$Connected = $false
)

$ErrorActionPreference = "Stop"

$ProjectRoot = "c:\Users\parim\Desktop\projects\pics\android"
Set-Location $ProjectRoot

Write-Host "=== PhotoSync Android Test Suite ===" -ForegroundColor Cyan

# 1. Run Unit Tests (Logic)
Write-Host "Running Local Unit Tests (JUnit)..." -ForegroundColor Yellow
cmd /c "gradlew.bat testDebugUnitTest > build_log.txt 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Unit Tests FAILED. See build_log.txt for details." -ForegroundColor Red
    exit 1
}
Write-Host "Unit Tests PASSED" -ForegroundColor Green

# 2. Run Instrumented Tests (If requested)
if ($Connected) {
    Write-Host "Running Connected Android Tests (Espresso/Instrumentation)..." -ForegroundColor Yellow
    Write-Host "Note: Emulator or Device must be attached."
    ./gradlew connectedDebugAndroidTest
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Connected Tests FAILED" -ForegroundColor Red
        exit 1
    }
    Write-Host "Connected Tests PASSED" -ForegroundColor Green
}
else {
    Write-Host "Skipping Connected Tests (Use -Connected to run)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "SUCCESS: All tests passed." -ForegroundColor Green
