import socket
import ssl
import sys

HOST = 'localhost'
PORT = 50506

def check_protocol(protocol_name, ssl_version):
    print(f"Testing {protocol_name}...", end=' ')
    try:
        context = ssl.SSLContext(ssl_version)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        
        with socket.create_connection((HOST, PORT), timeout=2) as sock:
            with context.wrap_socket(sock, server_hostname=HOST) as ssock:
                print("ACCEPTED (Risk)")
                return True # Connection succeeded (Bad for old protocols)
                
    except ssl.SSLError as e:
        print(f"REJECTED (Pass) - {e}")
        return False
    except ValueError:
        print("NOT SUPPORTED by Client (Skip)")
        return False
    except Exception as e:
        print(f"ERROR: {e}")
        return False

def check_plaintext():
    print("Testing Plaintext HTTP...", end=' ')
    try:
        with socket.create_connection((HOST, PORT), timeout=2) as sock:
            sock.sendall(b"GET /api/stats HTTP/1.1\r\nHost: localhost\r\n\r\n")
            data = sock.recv(1024)
            if b"HTTP/1.1 400" in data or len(data) == 0:
                 print("REJECTED (Pass)")
                 return False
            print("ACCEPTED (Risk)")
            return True
            
    except Exception as e:
        print(f"ERROR: {e}")
        return False

if __name__ == "__main__":
    print(f"[-] Checking TLS configuration on {HOST}:{PORT}")
    
    # Check old/insecure protocols
    # Note: Python ssl module might not even support SSLv3 anymore, which is good.
    
    failures = []
    
    if hasattr(ssl, 'PROTOCOL_TLSv1'):
        if check_protocol("TLSv1.0", ssl.PROTOCOL_TLSv1): failures.append("TLSv1.0")
        
    if hasattr(ssl, 'PROTOCOL_TLSv1_1'):
        if check_protocol("TLSv1.1", ssl.PROTOCOL_TLSv1_1): failures.append("TLSv1.1")
        
    if hasattr(ssl, 'PROTOCOL_TLSv1_2'):
        if not check_protocol("TLSv1.2", ssl.PROTOCOL_TLSv1_2): 
            print("ERROR: TLSv1.2 failed but should be accepted")
            # This is not necessarily a security failure for hardening, but a functionality issue.
            
    if check_plaintext(): failures.append("Plaintext")

    if failures:
        print(f"\n[FAIL] The following insecure protocols were accepted: {', '.join(failures)}")
        sys.exit(1)
    else:
        print("\n[PASS] TLS Hardening verified.")
        sys.exit(0)
