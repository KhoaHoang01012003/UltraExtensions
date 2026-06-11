# Burp Python IDE Enhanced

Single-JAR Burp Suite extension that embeds GraalPy and provides a Python IDE for pentest scripting.

## Build

```powershell
./gradlew.bat clean build
```

The Burp-loadable JAR is:

```text
build/libs/burp-python-ide-enhanced-0.1.2-all.jar
```

## Manual Burp Smoke Test

1. Open Burp Suite.
2. Go to Extensions > Installed > Add.
3. Select Extension type: Java.
4. Select `build/libs/burp-python-ide-enhanced-0.1.2-all.jar`.
5. Confirm a `Python IDE` suite tab appears.
6. Run:

```python
from burp import encoder, crypto

print(encoder.base64_encode(b"abc"))
print(crypto.sha256_hex(b"abc"))
```

7. Confirm the console prints `YWJj` and the SHA-256 hash.
8. While the script runs, switch Burp tabs and confirm Burp remains responsive.
