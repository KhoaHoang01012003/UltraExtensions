# Exact Python 3.14.3 Compatibility Runtime Design

## Goal

Run the fixed Zenmap executable at `C:\Program Files (x86)\Nmap\zenmap\bin\python.exe` while supplying a complete, writable CPython 3.14.3 standard-library and native-extension runtime from `LOCALAPPDATA`. The resulting worker must support `socket`, `ssl`, HTTPS package downloads, pip, interactive execution, custom commands, and user-installed packages without writing beneath `Program Files`.

## Root Cause

The previous compatibility pack copied selected files from whichever Python 3.14 happened to be installed on the build machine. That produced a mixed runtime: the target executable was Python 3.14.3, while the bundled standard library and native modules could come from Python 3.14.6. Selecting only `_ssl.pyd` and OpenSSL DLLs was also incomplete because `_ssl` imports the `_socket` C API, and pip later needs additional native modules such as `unicodedata`.

The build was therefore neither reproducible nor a complete dependency closure. Adding missing modules one at a time only moved the failure to the next native dependency.

## Architecture

The build downloads the official `python-3.14.3-embed-amd64.zip` from python.org and embeds its complete contents as a versioned compatibility runtime. At extension startup, the resources are staged beneath the existing `LOCALAPPDATA\BurpPythonIDE\runtime\assets` tree.

The Zenmap `python.exe` remains the launched process image. Before editor code, custom commands, or pip execute, the bootstrap:

1. Retains an `os.add_dll_directory()` handle for the staged compatibility root.
2. Places the compatibility root and its `python314.zip` on `sys.path` ahead of incomplete Zenmap standard-library locations.
3. Places user packages and the Burp helper package ahead of the compatibility runtime.
4. Leaves the interpreter executable path unchanged.

## Compatibility Gate

This release supports only Windows x64 CPython 3.14.3 with an MSVC-compatible build. Startup metadata records the complete `sys.version` compiler string. If the interpreter version, platform, architecture, or compiler family does not match, the extension fails with an actionable compatibility message instead of loading mixed native files.

Package management is enabled only after a bundled-runtime probe imports `socket`, `_socket`, `ssl`, `_ssl`, `hashlib`, `select`, and `unicodedata`, creates a default SSL context, and starts bundled pip successfully. Probe failures retain the full traceback in Burp output.

## Build Reproducibility

The compatibility runtime must not depend on `py -3.14`, `C:\Program`, `C:\Program1`, or another locally installed Python. The source URL, exact version, archive name, and expected SHA-256 digest are build inputs. A versioned staging identifier prevents older partial compatibility assets from being reused.

## Verification

Verification uses a stripped CPython 3.14.3 executable directory containing no native extensions. The extension-provided compatibility runtime must then:

- import the required standard-library and native modules;
- establish HTTPS to PyPI;
- run bundled pip and download a wheel;
- execute editor and custom-command modes;
- pass unit tests, package smoke tests, isolated fat-JAR smoke tests, and the full Gradle `check fatJar` build.

## Local Test Residue

`C:\Program1` is not part of the runtime design. It is residue from an earlier local `winget` test whose requested `Program Files` destination was parsed incorrectly. No production code may reference it. Cleanup is a separate administrator operation because the local Nmap test directory currently contains hardlinks and broken junctions related to that installation.
