param(
    [Parameter(Mandatory = $true)]
    [string] $PythonVersion,

    [Parameter(Mandatory = $true)]
    [string] $CompatPythonVersion,

    [Parameter(Mandatory = $true)]
    [string] $CompatPythonPackageName,

    [Parameter(Mandatory = $true)]
    [string] $CompatPythonPackageSha256,

    [Parameter(Mandatory = $true)]
    [string] $CompatOpenSslPackageName,

    [Parameter(Mandatory = $true)]
    [string] $CompatOpenSslPackageSha256,

    [Parameter(Mandatory = $true)]
    [string] $OutputDir,

    [Parameter(Mandatory = $true)]
    [string] $DownloadDir,

    [Parameter(Mandatory = $true)]
    [string] $Packages,

    [Parameter(Mandatory = $true)]
    [string] $WorkerModulesDir
)

$ErrorActionPreference = "Stop"

$runtimeDir = Join-Path $OutputDir "cpython\windows-x64"
$manifest = Join-Path $runtimeDir "burp-python-runtime.txt"
$marker = Join-Path $runtimeDir ".burp-python-cpython-bundle-ready"
$msys2DownloadDir = Join-Path $DownloadDir "msys2"
$normalizedCompatPythonHash = $CompatPythonPackageSha256.Trim().ToUpperInvariant()
$normalizedCompatOpenSslHash = $CompatOpenSslPackageSha256.Trim().ToUpperInvariant()

function Get-SevenZip {
    $candidates = @()
    if ($env:BURP_PYTHON_7Z) {
        $candidates += $env:BURP_PYTHON_7Z
    }
    $candidates += @(
        "C:\Program Files\7-Zip\7z.exe",
        "C:\Program Files (x86)\7-Zip\7z.exe"
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }
    $command = Get-Command "7z.exe" -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    throw "7-Zip is required to extract MSYS2 .pkg.tar.zst packages. Install 7-Zip or set BURP_PYTHON_7Z to 7z.exe."
}

function Get-DownloadedFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Url,

        [Parameter(Mandatory = $true)]
        [string] $Destination,

        [Parameter(Mandatory = $true)]
        [string] $ExpectedSha256
    )

    if (-not (Test-Path -LiteralPath $Destination)) {
        Write-Host "Downloading $Url"
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
    }

    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash.ToUpperInvariant()
    if ($actualHash -ne $ExpectedSha256) {
        throw "SHA-256 mismatch for $(Split-Path -Leaf $Destination). Expected $ExpectedSha256 but found $actualHash."
    }
}

function Expand-Msys2Package {
    param(
        [Parameter(Mandatory = $true)]
        [string] $PackagePath,

        [Parameter(Mandatory = $true)]
        [string] $Destination
    )

    if (Test-Path -LiteralPath $Destination) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null

    $sevenZip = Get-SevenZip
    & $sevenZip x $PackagePath "-o$Destination" -y | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "7-Zip failed to unpack $(Split-Path -Leaf $PackagePath) with exit code $LASTEXITCODE."
    }

    $tar = Get-ChildItem -LiteralPath $Destination -Filter "*.tar" | Select-Object -First 1
    if (-not $tar) {
        throw "MSYS2 package did not produce a .tar file: $PackagePath"
    }

    & $sevenZip x $tar.FullName "-o$Destination" -y | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "7-Zip failed to extract $($tar.Name) with exit code $LASTEXITCODE."
    }
    Remove-Item -LiteralPath $tar.FullName -Force
}

function Remove-LegacyInterpreterPayload {
    if (-not (Test-Path -LiteralPath $runtimeDir)) {
        return
    }
    $preservedFiles = @(
        (Split-Path -Leaf $manifest),
        (Split-Path -Leaf $marker)
    )
    Get-ChildItem -LiteralPath $runtimeDir -File -Force | Where-Object {
        $preservedFiles -notcontains $_.Name
    } | Remove-Item -Force
}

function Copy-CompatStdlibZip {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SourceStdlib,

        [Parameter(Mandatory = $true)]
        [string] $DestinationZip
    )

    $staging = Join-Path (Split-Path -Parent $DestinationZip) "stdlib-staging"
    if (Test-Path -LiteralPath $staging) {
        Remove-Item -LiteralPath $staging -Recurse -Force
    }
    New-Item -ItemType Directory -Path $staging -Force | Out-Null

    $excludedNames = @(
        "__pycache__",
        "ensurepip",
        "idlelib",
        "lib-dynload",
        "site-packages",
        "test",
        "tkinter",
        "turtledemo",
        "venv"
    )
    Get-ChildItem -LiteralPath $SourceStdlib -Force | Where-Object {
        $excludedNames -notcontains $_.Name
    } | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $staging $_.Name) -Recurse -Force
    }

    if (Test-Path -LiteralPath $DestinationZip) {
        Remove-Item -LiteralPath $DestinationZip -Force
    }
    Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $DestinationZip -CompressionLevel Optimal
    Remove-Item -LiteralPath $staging -Recurse -Force
}

New-Item -ItemType Directory -Path $DownloadDir -Force | Out-Null
New-Item -ItemType Directory -Path $msys2DownloadDir -Force | Out-Null

$compatPythonPackage = Join-Path $msys2DownloadDir $CompatPythonPackageName
$compatOpenSslPackage = Join-Path $msys2DownloadDir $CompatOpenSslPackageName
Get-DownloadedFile `
    -Url "https://repo.msys2.org/mingw/mingw64/$CompatPythonPackageName" `
    -Destination $compatPythonPackage `
    -ExpectedSha256 $normalizedCompatPythonHash
Get-DownloadedFile `
    -Url "https://repo.msys2.org/mingw/mingw64/$CompatOpenSslPackageName" `
    -Destination $compatOpenSslPackage `
    -ExpectedSha256 $normalizedCompatOpenSslHash

$expectedManifest = @(
    "python=$PythonVersion",
    "packages=$Packages",
    "compatPython=$CompatPythonVersion",
    "compatPythonPackage=$CompatPythonPackageName",
    "compatPythonPackageSha256=$normalizedCompatPythonHash",
    "compatOpenSslPackage=$CompatOpenSslPackageName",
    "compatOpenSslPackageSha256=$normalizedCompatOpenSslHash",
    "compatMode=msys2-mingw-stdlib-ssl-v1"
)

if (Test-Path -LiteralPath $marker) {
    $currentManifest = @()
    if (Test-Path -LiteralPath $manifest) {
        $currentManifest = Get-Content -LiteralPath $manifest
    }
    $manifestMatches = (Compare-Object -ReferenceObject $expectedManifest -DifferenceObject $currentManifest -SyncWindow 0).Count -eq 0
    if ($manifestMatches) {
        $sitePackages = Join-Path $runtimeDir "Lib\site-packages"
        if ((Test-Path -LiteralPath $WorkerModulesDir) -and (Test-Path -LiteralPath $sitePackages)) {
            Copy-Item -Path (Join-Path $WorkerModulesDir "*") -Destination $sitePackages -Recurse -Force
        }
        Remove-LegacyInterpreterPayload
        Write-Host "CPython bundle already prepared at $runtimeDir"
        exit 0
    }
    Write-Host "CPython bundle manifest changed; rebuilding $runtimeDir"
}

if (Test-Path -LiteralPath $runtimeDir) {
    Remove-Item -LiteralPath $runtimeDir -Recurse -Force
}
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null

$embedName = "python-$PythonVersion-embed-amd64.zip"
$embedZip = Join-Path $DownloadDir $embedName
$embedUrl = "https://www.python.org/ftp/python/$PythonVersion/$embedName"
if (-not (Test-Path -LiteralPath $embedZip)) {
    Write-Host "Downloading $embedUrl"
    Invoke-WebRequest -Uri $embedUrl -OutFile $embedZip -UseBasicParsing
}

Expand-Archive -LiteralPath $embedZip -DestinationPath $runtimeDir -Force

$minor = ($PythonVersion.Split(".")[0..1] -join "")
$pth = Join-Path $runtimeDir "python$minor._pth"
if (-not (Test-Path -LiteralPath $pth)) {
    throw "Missing embeddable Python path file: $pth"
}
Set-Content -LiteralPath $pth -Encoding ASCII -Value @(
    "python$minor.zip",
    ".",
    "Lib",
    "Lib\site-packages",
    "import site"
)

$sitePackages = Join-Path $runtimeDir "Lib\site-packages"
New-Item -ItemType Directory -Path $sitePackages -Force | Out-Null

$getPip = Join-Path $DownloadDir "get-pip.py"
if (-not (Test-Path -LiteralPath $getPip)) {
    Write-Host "Downloading get-pip.py"
    Invoke-WebRequest -Uri "https://bootstrap.pypa.io/get-pip.py" -OutFile $getPip -UseBasicParsing
}

$python = Join-Path $runtimeDir "python.exe"
& $python $getPip --no-warn-script-location
if ($LASTEXITCODE -ne 0) {
    throw "get-pip.py failed with exit code $LASTEXITCODE"
}

$packageList = $Packages.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ }
if ($packageList.Count -gt 0) {
    Write-Host "Installing packages: $($packageList -join ', ')"
    & $python -m pip install --upgrade --no-cache-dir --target $sitePackages @packageList
    if ($LASTEXITCODE -ne 0) {
        throw "pip install failed with exit code $LASTEXITCODE"
    }
}

if (Test-Path -LiteralPath $WorkerModulesDir) {
    Copy-Item -Path (Join-Path $WorkerModulesDir "*") -Destination $sitePackages -Recurse -Force
}

$compatRoot = Join-Path $runtimeDir "python-compat-$CompatPythonVersion"
$compatPythonExtract = Join-Path $DownloadDir "msys2-python-$CompatPythonVersion"
$compatOpenSslExtract = Join-Path $DownloadDir "msys2-openssl"
if (Test-Path -LiteralPath $compatRoot) {
    Remove-Item -LiteralPath $compatRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $compatRoot -Force | Out-Null

Expand-Msys2Package -PackagePath $compatPythonPackage -Destination $compatPythonExtract
Expand-Msys2Package -PackagePath $compatOpenSslPackage -Destination $compatOpenSslExtract

$compatMinorVersion = ($CompatPythonVersion.Split(".")[0..1] -join ".")
$compatStdlib = Join-Path $compatPythonExtract "mingw64\lib\python$compatMinorVersion"
$compatDynload = Join-Path $compatStdlib "lib-dynload"
$compatOpenSslBin = Join-Path $compatOpenSslExtract "mingw64\bin"
if (-not (Test-Path -LiteralPath $compatStdlib)) {
    throw "MSYS2 Python stdlib was not found at $compatStdlib."
}

Copy-CompatStdlibZip -SourceStdlib $compatStdlib -DestinationZip (Join-Path $compatRoot "python314.zip")
Copy-Item -LiteralPath (Join-Path $compatDynload "_ssl.cp314-mingw_x86_64_msvcrt_gnu.pyd") `
    -Destination (Join-Path $compatRoot "_ssl.pyd") -Force
Copy-Item -LiteralPath (Join-Path $compatDynload "_hashlib.cp314-mingw_x86_64_msvcrt_gnu.pyd") `
    -Destination (Join-Path $compatRoot "_hashlib.pyd") -Force
Copy-Item -LiteralPath (Join-Path $compatOpenSslBin "libssl-3-x64.dll") -Destination $compatRoot -Force
Copy-Item -LiteralPath (Join-Path $compatOpenSslBin "libcrypto-3-x64.dll") -Destination $compatRoot -Force

$requiredCompatFiles = @(
    "python314.zip",
    "_ssl.pyd",
    "_hashlib.pyd",
    "libssl-3-x64.dll",
    "libcrypto-3-x64.dll"
)
foreach ($fileName in $requiredCompatFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $compatRoot $fileName))) {
        throw "Compatibility runtime file was not found after extraction: $fileName"
    }
}

$excludedCompatFiles = @(
    "python.exe",
    "pythonw.exe",
    "python3.dll",
    "python314.dll",
    "libpython3.14.dll",
    "libssl-3.dll",
    "libcrypto-3.dll"
)
foreach ($fileName in $excludedCompatFiles) {
    if (Test-Path -LiteralPath (Join-Path $compatRoot $fileName)) {
        throw "Compatibility runtime must not contain interpreter or MSVC payload file: $fileName"
    }
}

Remove-LegacyInterpreterPayload
Set-Content -LiteralPath $manifest -Encoding UTF8 -Value $expectedManifest
Set-Content -LiteralPath $marker -Encoding ASCII -Value "ready"
