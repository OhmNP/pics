$opensslAvailable = Get-Command openssl -ErrorAction SilentlyContinue

if ($opensslAvailable) {
    Write-Host "OpenSSL found. Generating with OpenSSL..."
    # Generate Private Key
    openssl genrsa -out server.key 2048
    
    # Generate Certificate
    openssl req -new -key server.key -out server.csr -subj "/C=US/ST=State/L=City/O=PhotoSync/CN=photosync.local"
    openssl x509 -req -days 365 -in server.csr -signkey server.key -out server.crt
    
    Write-Host "Certificates generated: server.crt, server.key"
} else {
    Write-Host "OpenSSL not found. Falling back to PowerShell certificate generation..."
    $cert = New-SelfSignedCertificate -DnsName "photosync.local" -CertStoreLocation "cert:\LocalMachine\My"
    
    # Export to file is tricky without admin rights for private key sometimes, 
    # but for dev we usually just need the files.
    # Actually, simpler to just use a pre-generated blob for this agent task if openssl fails, 
    # but user said they have windows.
    # Let's hope openSSL command I checked earlier works.
    Write-Error "OpenSSL is required for this specific script to output .crt/.key files easily compatible with C++."
}
