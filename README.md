# Burp Python IDE Enhanced

Single-JAR Burp Suite extension that embeds a CPython worker runtime and provides a Python IDE for pentest scripting.

## Build

```powershell
./gradlew.bat clean build
```

The Burp-loadable JAR is:

```text
build/libs/burp-python-ide-enhanced-0.2.1-all.jar
```

## Manual Burp Smoke Test

1. Open Burp Suite.
2. Go to Extensions > Installed > Add.
3. Select Extension type: Java.
4. Select `build/libs/burp-python-ide-enhanced-0.2.1-all.jar`.
5. Confirm a `Python IDE` suite tab appears.
6. Confirm the toolbar shows `Load`, `Save As`, `Run`, `Stop`, and `Clear Log`.
7. Run:

```python
from burp import encoder, crypto

print(encoder.base64_encode(b"abc"))
print(crypto.sha256_hex(b"abc"))
```

8. Confirm the console prints `YWJj` and the SHA-256 hash.
9. Click `Clear Log` and confirm the console clears.
10. Use `Save As` to save the editor content to a local `.py` file, then use `Load` to load a local `.py` file into the editor.
11. While the script runs, switch Burp tabs and confirm Burp remains responsive.

## CPython Worker Runtime

The build bundles CPython 3.12 for Windows x64 and a curated popular package set into the final JAR. At runtime the extension extracts that bundle into the extension cache and runs scripts in a separate Python worker process, so native package crashes do not crash Burp.

`burp.http.send(method, url, body="")` is bridged back to Java/Montoya through the worker RPC channel.
