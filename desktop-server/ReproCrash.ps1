$ErrorActionPreference = "Stop"

try {
    Write-Host "Connecting to 127.0.0.1:50505..."
    $client = New-Object System.Net.Sockets.TcpClient("127.0.0.1", 50505)
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $reader = New-Object System.IO.StreamReader($stream)
    $writer.AutoFlush = $true

    # Send HELLO to start session
    Write-Host "Sending HELLO..."
    $writer.WriteLine("HELLO test_device")
    $response = $reader.ReadLine()
    Write-Host "Received: $response"

    # Send bad DATA_TRANSFER
    Write-Host "Sending bad DATA_TRANSFER..."
    $writer.WriteLine("DATA_TRANSFER invalid_number")
    
    # Wait a bit to see if server crashes
    Start-Sleep -Seconds 1

    # Try to send another command to check if server is still alive
    Write-Host "Sending PING (to check liveness)..."
    # PING is not a valid protocol command but should result in ERROR, not crash/disconnect
    $writer.WriteLine("HELLO check_alive")
    
    try {
        $stream.ReadTimeout = 2000
        $response = $reader.ReadLine()
        Write-Host "Received response: $response"
        
        if ($response) {
            Write-Host "SUCCESS: Server is still running and responded!"
        } else {
            Write-Host "FAILURE: Server returned null (closed connection)"
            exit 1
        }
    } catch {
        Write-Host "FAILURE: Read failed (server likely crashed): $_"
        exit 1
    }

    $client.Close()
} catch {
    Write-Host "FAILURE: Connection error: $_"
    exit 1
}
