# Test-MultipleClients.ps1
# Script to spawn multiple test clients with different device IDs

param(
    [int]$NumClients = 3,
    [int]$PhotosPerClient = 10,
    [string]$HostName = "localhost",
    [int]$Port = 50505
)

Write-Host "=== Multi-Client Test Script ===" -ForegroundColor Cyan
Write-Host "Building test client..." -ForegroundColor Yellow

# Build the test client
Push-Location test-client
if (!(Test-Path "build")) {
    New-Item -ItemType Directory -Path "build" | Out-Null
}

Push-Location build
cmake .. -G "Visual Studio 17 2022" -A x64 2>&1 | Out-Null
cmake --build . --config Release 2>&1 | Out-Null
Pop-Location

if (!(Test-Path "build/Release/MockClient.exe")) {
    Write-Host "Failed to build MockClient.exe" -ForegroundColor Red
    Pop-Location
    exit 1
}

Write-Host "Test client built successfully" -ForegroundColor Green
Pop-Location

# Device names for test clients
$deviceNames = @(
    "Pixel_7_Pro",
    "Galaxy_S23_Ultra",
    "iPhone_15_Pro",
    "OnePlus_11",
    "Xiaomi_13_Pro",
    "Sony_Xperia_1_V",
    "Motorola_Edge_40",
    "Nothing_Phone_2"
)

Write-Host "`nSpawning $NumClients test clients..." -ForegroundColor Yellow

$jobs = @()
for ($i = 0; $i -lt $NumClients; $i++) {
    $deviceId = $deviceNames[$i % $deviceNames.Length]
    if ($i -ge $deviceNames.Length) {
        $deviceId += "_$i"
    }
    
    $photos = $PhotosPerClient + (Get-Random -Minimum -5 -Maximum 10)
    
    Write-Host "  Starting client: $deviceId ($photos photos)" -ForegroundColor Cyan
    
    $job = Start-Job -ScriptBlock {
        param($exe, $hostname, $port, $deviceId, $photos)
        & $exe --host $hostname --port $port --device-id $deviceId --photos $photos
    } -ArgumentList (Resolve-Path "test-client/build/Release/MockClient.exe"), $HostName, $Port, $deviceId, $photos
    
    $jobs += $job
    
    # Stagger the starts slightly
    Start-Sleep -Milliseconds 500
}

Write-Host "`nAll clients started" -ForegroundColor Green
Write-Host "Waiting for clients to complete..." -ForegroundColor Yellow

# Wait for all jobs to complete
$jobs | Wait-Job | Out-Null

Write-Host "`n=== Client Results ===" -ForegroundColor Cyan
foreach ($job in $jobs) {
    $output = Receive-Job -Job $job
    Write-Host $output
    Remove-Job -Job $job
}

Write-Host "`nAll clients completed" -ForegroundColor Green
Write-Host "`nCheck the dashboard at http://localhost:50506/clients to see the results!" -ForegroundColor Cyan
