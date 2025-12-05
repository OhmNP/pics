$port = 50505
$udpClient = New-Object System.Net.Sockets.UdpClient($port)
$udpClient.Client.ReceiveTimeout = 10000 
$remoteEndPoint = New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Any, 0)

Write-Host "Listening for PhotoSync broadcasts on port $port..."

try {
    $content = $udpClient.Receive([ref]$remoteEndPoint)
    $message = [System.Text.Encoding]::UTF8.GetString($content)
    Write-Host "Received from $($remoteEndPoint.Address): $message"
    
    if ($message -match "photosync") {
        Write-Host "Verification SUCCESS"
    } else {
        Write-Host "Verification FAILED: Invalid content"
    }
} catch {
    Write-Host "Verification FAILED: $_"
} finally {
    $udpClient.Close()
}
