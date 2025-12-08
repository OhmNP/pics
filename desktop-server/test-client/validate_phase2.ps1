
$ServerPath = "C:\Users\parim\AppData\Local\PhotoSync\bin\PhotoSyncServer.exe"
$WorkDir = "C:\Users\parim\AppData\Local\PhotoSync"
$BaseUrl = "http://localhost:50506/api"

Write-Host "Starting PhotoSync Server for testing..."
$ServerProcess = Start-Process -FilePath $ServerPath -WorkingDirectory $WorkDir -PassThru -NoNewWindow
Start-Sleep -Seconds 5

try {
    # 1. Login
    Write-Host "1. Testing Login..."
    $LoginBody = @{
        username = "admin"
        password = "admin123"
    } | ConvertTo-Json

    $LoginResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $LoginBody -ContentType "application/json"
    $Token = $LoginResponse.sessionToken
    Write-Host "   Login Successful. Token: $($Token.Substring(0, 10))..." -ForegroundColor Green

    $Headers = @{
        Authorization = "Bearer $Token"
    }

    # 2. Pairing Token
    Write-Host "2. Testing Pairing Token Generation..."
    $TokenResponse = Invoke-RestMethod -Uri "$BaseUrl/tokens" -Method Post -Headers $Headers -ContentType "application/json"
    if ($TokenResponse.token -and $TokenResponse.token.Length -eq 6) {
        Write-Host "   Pairing Token Generated: $($TokenResponse.token)" -ForegroundColor Green
    }
    else {
        Write-Error "   Failed to generate pairing token"
    }

    # 3. Client Details
    Write-Host "3. Testing Client Details..."
    $ClientsResponse = Invoke-RestMethod -Uri "$BaseUrl/clients" -Method Get -Headers $Headers
    $Clients = $ClientsResponse.clients
    
    if ($Clients.Count -gt 0) {
        $ClientId = $Clients[0].id
        Write-Host "   Found client ID: $ClientId. Fetching details..."
        $Details = Invoke-RestMethod -Uri "$BaseUrl/clients/$ClientId" -Method Get -Headers $Headers
        if ($Details.deviceId) {
            Write-Host "   Client Details Fetched: $($Details.deviceId)" -ForegroundColor Green
        }
        else {
            Write-Error "   Failed to fetch client details"
        }
    }
    else {
        Write-Host "   No clients found to test details (Success, but empty)" -ForegroundColor Yellow
        # We can't strictly fail here if no clients exist yet
    }

    # 4. Thumbnail Regeneration
    Write-Host "4. Testing Thumbnail Regeneration..."
    # Test single photo regeneration if we have photos, otherwise test 'all'
    # For safety/speed, let's just test 'all' flag which hits the cache clearing logic
    $RegenBody = @{
        all = $true
    } | ConvertTo-Json
    
    $RegenResponse = Invoke-RestMethod -Uri "$BaseUrl/maintenance/thumbnails" -Method Post -Headers $Headers -Body $RegenBody -ContentType "application/json"
    
    if ($RegenResponse.success) {
        Write-Host "   Regeneration Triggered: $($RegenResponse.message)" -ForegroundColor Green
    }
    else {
        Write-Error "   Regeneration Failed: $($RegenResponse.error)"
    }

}
catch {
    Write-Error "Test Failed: $_"
    if ($_.Exception.Response) {
        $Stream = $_.Exception.Response.GetResponseStream()
        $Reader = New-Object System.IO.StreamReader($Stream)
        Write-Error "Response Content: $($Reader.ReadToEnd())"
    }
}
finally {
    Write-Host "Stopping Server..."
    if ($ServerProcess) {
        Stop-Process -Id $ServerProcess.Id -Force
    }
    Write-Host "Test Complete."
}
