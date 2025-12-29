# Stress Test Script for PhotoSync Server
param (
    [int]$clients = 10,
    [int]$photosPerClient = 5,
    [string]$host = "localhost",
    [int]$port = 50506
)

$executable = ".\build\Debug\MockClientSSL.exe"

if (-not (Test-Path $executable)) {
    Write-Error "MockClientSSL.exe not found. Please build it first."
    exit 1
}

Write-Host "Starting Stress Test with $clients clients, $photosPerClient photos each..." -ForegroundColor Cyan

$jobs = @()

for ($i = 0; $i -lt $clients; $i++) {
    $deviceId = "stress_client_$i"
    $args = "--host $host --port $port --photos $photosPerClient --device-id $deviceId"
    
    Write-Host "Suppressed output for client $i"
    # Start-Process -FilePath $executable -ArgumentList $args -NoNewWindow
    # Using Start-Job to track completion
    $jobs += Start-Job -ScriptBlock {
        param($exe, $a)
        & $exe $a
    } -ArgumentList $executable, $args
}

Write-Host "All clients started. Waiting for completion..." -ForegroundColor Yellow

$jobs | Wait-Job | Remove-Job

Write-Host "Stress Test Complete." -ForegroundColor Green
