import random
from datetime import datetime, timedelta

def generate_self_signed_cert():
    try:
        from cryptography import x509
        from cryptography.x509.oid import NameOID
        from cryptography.hazmat.primitives import hashes
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.hazmat.primitives import serialization
        import datetime

        key = rsa.generate_private_key(
            public_exponent=65537,
            key_size=2048,
        )

        subject = issuer = x509.Name([
            x509.NameAttribute(NameOID.COUNTRY_NAME, u"US"),
            x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, u"California"),
            x509.NameAttribute(NameOID.LOCALITY_NAME, u"San Francisco"),
            x509.NameAttribute(NameOID.ORGANIZATION_NAME, u"PhotoSync"),
            x509.NameAttribute(NameOID.COMMON_NAME, u"photosync.local"),
        ])

        cert = x509.CertificateBuilder().subject_name(
            subject
        ).issuer_name(
            issuer
        ).public_key(
            key.public_key()
        ).serial_number(
            x509.random_serial_number()
        ).not_valid_before(
            datetime.datetime.utcnow()
        ).not_valid_after(
            datetime.datetime.utcnow() + datetime.timedelta(days=365)
        ).add_extension(
            x509.SubjectAlternativeName([x509.DNSName(u"photosync.local")]),
            critical=False,
        ).sign(key, hashes.SHA256())

        with open("server.key", "wb") as f:
            f.write(key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.TraditionalOpenSSL,
                encryption_algorithm=serialization.NoEncryption(),
            ))

        with open("server.crt", "wb") as f:
            f.write(cert.public_bytes(serialization.Encoding.PEM))
            
        print("Certificates generated successfully: server.crt, server.key")
        
    except ImportError:
        print("cryptography module not found. Generating dummy files (NOT SECURE, FOR API COMPILATION ONLY).")
        # Fallback to create empty files if cryptography is missing, 
        # though this will fail at runtime if the server tries to load them.
        # But we need the files to exist.
        with open("server.key", "w") as f: f.write("DUMMY KEY")
        with open("server.crt", "w") as f: f.write("DUMMY CERT")

if __name__ == "__main__":
    generate_self_signed_cert()
