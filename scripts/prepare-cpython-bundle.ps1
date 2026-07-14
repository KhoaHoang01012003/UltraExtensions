param(
    [Parameter(Mandatory = $true)]
    [string] $PythonVersion,

    [Parameter(Mandatory = $true)]
    [string] $CompatPythonVersion,

    [Parameter(Mandatory = $true)]
    [string] $CompatArchiveSha256,

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
$compatArchiveName = "python-$CompatPythonVersion-embed-amd64.zip"
$compatArchive = Join-Path $DownloadDir $compatArchiveName
$compatArchiveUrl = "https://www.python.org/ftp/python/$CompatPythonVersion/$compatArchiveName"
$normalizedCompatHash = $CompatArchiveSha256.Trim().ToUpperInvariant()

New-Item -ItemType Directory -Path $DownloadDir -Force | Out-Null
if (-not (Test-Path -LiteralPath $compatArchive)) {
    Write-Host "Downloading $compatArchiveUrl"
    Invoke-WebRequest -Uri $compatArchiveUrl -OutFile $compatArchive -UseBasicParsing
}

$actualCompatHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $compatArchive).Hash.ToUpperInvariant()
if ($actualCompatHash -ne $normalizedCompatHash) {
    throw "Compatibility archive SHA-256 mismatch for $compatArchiveName. Expected $normalizedCompatHash but found $actualCompatHash."
}

$expectedManifest = @(
    "python=$PythonVersion",
    "packages=$Packages",
    "compatPython=$CompatPythonVersion",
    "compatArchiveSha256=$normalizedCompatHash",
    "compatMode=embeddable-dependencies-v1"
)

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
$compatExtract = Join-Path $DownloadDir "python-$CompatPythonVersion-embed-amd64-extracted"
if (Test-Path -LiteralPath $compatExtract) {
    Remove-Item -LiteralPath $compatExtract -Recurse -Force
}
if (Test-Path -LiteralPath $compatRoot) {
    Remove-Item -LiteralPath $compatRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $compatExtract -Force | Out-Null
New-Item -ItemType Directory -Path $compatRoot -Force | Out-Null
Expand-Archive -LiteralPath $compatArchive -DestinationPath $compatExtract -Force

$excludedCompatFiles = @(
    "python.exe",
    "pythonw.exe",
    "python3.dll",
    "python314.dll",
    "python314._pth"
)
Get-ChildItem -LiteralPath $compatExtract -Force | Where-Object {
    $excludedCompatFiles -notcontains $_.Name
} | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $compatRoot $_.Name) -Recurse -Force
}

$requiredCompatFiles = @(
    "python314.zip",
    "_socket.pyd",
    "_ssl.pyd",
    "_hashlib.pyd",
    "select.pyd",
    "unicodedata.pyd",
    "libssl-3.dll",
    "libcrypto-3.dll"
)
foreach ($fileName in $requiredCompatFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $compatRoot $fileName))) {
        throw "Compatibility runtime file was not found after extraction: $fileName"
    }
}
foreach ($fileName in $excludedCompatFiles) {
    if (Test-Path -LiteralPath (Join-Path $compatRoot $fileName)) {
        throw "Compatibility runtime must not contain executable/interpreter file: $fileName"
    }
}

Remove-Item -LiteralPath $compatExtract -Recurse -Force
Remove-LegacyInterpreterPayload
Set-Content -LiteralPath $manifest -Encoding UTF8 -Value $expectedManifest
Set-Content -LiteralPath $marker -Encoding ASCII -Value "ready"
