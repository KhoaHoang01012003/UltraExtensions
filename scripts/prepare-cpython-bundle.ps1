param(
    [Parameter(Mandatory = $true)]
    [string] $PythonVersion,

    [Parameter(Mandatory = $true)]
    [string] $OutputDir,

    [Parameter(Mandatory = $true)]
    [string] $DownloadDir,

    [Parameter(Mandatory = $false)]
    [string] $FallbackStdlibDir,

    [Parameter(Mandatory = $true)]
    [string] $Packages,

    [Parameter(Mandatory = $true)]
    [string] $WorkerModulesDir
)

$ErrorActionPreference = "Stop"

$runtimeDir = Join-Path $OutputDir "cpython\windows-x64"
$defaultFallbackStdlibDir = "C:\Program Files (x86)\Nmap\zenmap\bin\Lib"
$resolvedFallbackStdlibDir = $null
if ($FallbackStdlibDir -and (Test-Path $FallbackStdlibDir)) {
    $resolvedFallbackStdlibDir = (Resolve-Path $FallbackStdlibDir).Path
} elseif (Test-Path $defaultFallbackStdlibDir) {
    $resolvedFallbackStdlibDir = (Resolve-Path $defaultFallbackStdlibDir).Path
}
$manifest = Join-Path $runtimeDir "burp-python-runtime.txt"
$expectedManifest = @(
    "python=$PythonVersion",
    "packages=$Packages",
    "fallbackStdlibDir=$resolvedFallbackStdlibDir"
)
$marker = Join-Path $runtimeDir ".burp-python-cpython-bundle-ready"
if (Test-Path $marker) {
    $currentManifest = @()
    if (Test-Path $manifest) {
        $currentManifest = Get-Content -Path $manifest
    }
    $manifestMatches = (Compare-Object -ReferenceObject $expectedManifest -DifferenceObject $currentManifest -SyncWindow 0).Count -eq 0
    if ($manifestMatches) {
        $sitePackages = Join-Path $runtimeDir "Lib\site-packages"
        if ((Test-Path $WorkerModulesDir) -and (Test-Path $sitePackages)) {
            Copy-Item -Path (Join-Path $WorkerModulesDir "*") -Destination $sitePackages -Recurse -Force
        }
        Write-Host "CPython bundle already prepared at $runtimeDir"
        exit 0
    }
    Write-Host "CPython bundle manifest changed; rebuilding $runtimeDir"
}

if (Test-Path $runtimeDir) {
    Remove-Item -LiteralPath $runtimeDir -Recurse -Force
}
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
New-Item -ItemType Directory -Path $DownloadDir -Force | Out-Null

$embedName = "python-$PythonVersion-embed-amd64.zip"
$embedZip = Join-Path $DownloadDir $embedName
$embedUrl = "https://www.python.org/ftp/python/$PythonVersion/$embedName"
if (-not (Test-Path $embedZip)) {
    Write-Host "Downloading $embedUrl"
    Invoke-WebRequest -Uri $embedUrl -OutFile $embedZip -UseBasicParsing
}

Expand-Archive -Path $embedZip -DestinationPath $runtimeDir -Force

$minor = ($PythonVersion.Split(".")[0..1] -join "")
$pth = Join-Path $runtimeDir "python$minor._pth"
if (-not (Test-Path $pth)) {
    throw "Missing embeddable Python path file: $pth"
}
Set-Content -Path $pth -Encoding ASCII -Value @(
    "python$minor.zip",
    ".",
    "Lib",
    "Lib\site-packages",
    "import site"
)

$sitePackages = Join-Path $runtimeDir "Lib\site-packages"
New-Item -ItemType Directory -Path $sitePackages -Force | Out-Null

$getPip = Join-Path $DownloadDir "get-pip.py"
if (-not (Test-Path $getPip)) {
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

if (Test-Path $WorkerModulesDir) {
    Copy-Item -Path (Join-Path $WorkerModulesDir "*") -Destination $sitePackages -Recurse -Force
}

if ($resolvedFallbackStdlibDir) {
    $stdlibSource = Join-Path $runtimeDir "stdlib-source"
    if (Test-Path $stdlibSource) {
        Remove-Item -LiteralPath $stdlibSource -Recurse -Force
    }
    New-Item -ItemType Directory -Path $stdlibSource -Force | Out-Null
    Write-Host "Copying fallback stdlib source from $resolvedFallbackStdlibDir"
    $null = robocopy $resolvedFallbackStdlibDir $stdlibSource /E /NFL /NDL /NJH /NJS /NP /XD "__pycache__" "site-packages" /XF "*.pyc" "*.pyo"
    if ($LASTEXITCODE -ge 8) {
        throw "robocopy fallback stdlib source failed with exit code $LASTEXITCODE"
    }
}

Set-Content -Path $manifest -Encoding UTF8 -Value $expectedManifest
Set-Content -Path $marker -Encoding ASCII -Value "ready"
