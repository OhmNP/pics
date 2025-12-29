import requests
import time
import urllib3

# Suppress insecure request warnings for self-signed certs
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

BASE_URL = "https://localhost:50506/api"
VERIFY = False # Self-signed certs

def test_unauth_endpoints():
    print("\n[+] Testing Unauthenticated Access...")
    endpoints = [
        "/stats",
        "/photos",
        "/config",
        "/network",
        "/connections"
    ]
    
    failed = False
    for ep in endpoints:
        try:
            r = requests.get(f"{BASE_URL}{ep}", verify=VERIFY)
            if r.status_code == 401:
                print(f"  PASS: {ep} blocked (401)")
            else:
                print(f"  FAIL: {ep} got {r.status_code}")
                failed = True
        except Exception as e:
            print(f"  ERROR: {ep} - {e}")
            failed = True
            
    return not failed

def test_rate_limiting():
    print("\n[+] Testing Rate Limiting (Brute Force Protection)...")
    username = "admin"
    password = "wrong_password"
    
    # Try 10 times. Default lockout is usually 3-5 attempts.
    locked = False
    for i in range(1, 11):
        try:
            r = requests.post(f"{BASE_URL}/auth/login", 
                            json={"username": username, "password": password}, 
                            verify=VERIFY)
            
            # Check for error message or 429 (if implemented) or simple lockout message body
            if "Locked" in r.text or "locked" in r.text or r.status_code == 429:
                print(f"  PASS: Locked out on attempt {i}")
                print(f"  Response: {r.text}")
                locked = True
                break
            else:
                print(f"  Attempt {i}: {r.status_code} - {r.text}")
                
        except Exception as e:
            print(f"  ERROR: Attempt {i} - {e}")
            
    if not locked:
        print("  FAIL: Never locked out after 10 attempts")
        return False
    return True

if __name__ == "__main__":
    print("Starting Security Verification...")
    
    # Wait for server to be up (if needed)
    try:
        requests.get(f"{BASE_URL}/stats", verify=VERIFY, timeout=2)
    except:
        print("Server not reachable immediately. Retrying in 2s...")
        time.sleep(2)
        
    auth_pass = test_unauth_endpoints()
    rate_pass = test_rate_limiting()
    
    if auth_pass and rate_pass:
        print("\n[SUCCESS] All security verifications passed!")
        exit(0)
    else:
        print("\n[FAILURE] Security verification failed.")
        exit(1)
