param(
    [Parameter(Mandatory = $true)]
    [string] $PythonVersion,

    [Parameter(Mandatory = $true)]
    [string] $OutputDir,

    [Parameter(Mandatory = $true)]
    [string] $DownloadDir,

    [Parameter(Mandatory = $false)]
    [string] $FallbackStdlibDir,

    [Parameter(Mandatory = $false)]
    [string] $CompatPythonRoot,

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

function Resolve-CompatPythonRoot {
    param([string] $RequestedRoot)

    if ($RequestedRoot) {
        $candidate = Join-Path $RequestedRoot "python.exe"
        if (Test-Path $candidate) {
            return (Resolve-Path $RequestedRoot).Path
        }
    }

    try {
        $probe = & py -3.14 -c "import sys; print(sys.base_prefix)"
        if ($LASTEXITCODE -eq 0 -and $probe) {
            $resolved = $probe | Select-Object -First 1
            if ($resolved -and (Test-Path (Join-Path $resolved.Trim() "python.exe"))) {
                return (Resolve-Path $resolved.Trim()).Path
            }
        }
    } catch {
    }

    $defaultRoot = "C:\Program"
    if (Test-Path (Join-Path $defaultRoot "python.exe")) {
        return (Resolve-Path $defaultRoot).Path
    }

    return $null
}

$resolvedCompatPythonRoot = Resolve-CompatPythonRoot -RequestedRoot $CompatPythonRoot
$manifest = Join-Path $runtimeDir "burp-python-runtime.txt"
$expectedManifest = @(
    "python=$PythonVersion",
    "packages=$Packages",
    "fallbackStdlibDir=$resolvedFallbackStdlibDir",
    "compatPythonRoot=$resolvedCompatPythonRoot",
    "compatNativeMode=ssl-only-v2"
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

if ($resolvedCompatPythonRoot) {
    $compatRoot = Join-Path $runtimeDir "python-compat-3.14"
    $compatLib = Join-Path $compatRoot "Lib"
    $compatDlls = Join-Path $compatRoot "DLLs"
    if (Test-Path $compatRoot) {
        Remove-Item -LiteralPath $compatRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $compatLib -Force | Out-Null
    New-Item -ItemType Directory -Path $compatDlls -Force | Out-Null

    $sourceLib = Join-Path $resolvedCompatPythonRoot "Lib"
    $sourceDlls = Join-Path $resolvedCompatPythonRoot "DLLs"
    if (-not (Test-Path $sourceLib)) {
        throw "Compat Python Lib directory was not found at $sourceLib"
    }
    if (-not (Test-Path $sourceDlls)) {
        throw "Compat Python DLLs directory was not found at $sourceDlls"
    }

    Write-Host "Copying compat stdlib from $sourceLib"
    $null = robocopy $sourceLib $compatLib /E /NFL /NDL /NJH /NJS /NP /XD "__pycache__" "site-packages" /XF "*.pyc" "*.pyo"
    if ($LASTEXITCODE -ge 8) {
        throw "robocopy compat stdlib failed with exit code $LASTEXITCODE"
    }

    $nativeCompatFiles = @(
        "_ssl.pyd",
        "_hashlib.pyd",
        "libssl-3.dll",
        "libcrypto-3.dll",
        "libffi-8.dll"
    )
    Write-Host "Copying compat native SSL files from $sourceDlls"
    foreach ($fileName in $nativeCompatFiles) {
        $sourceFile = Join-Path $sourceDlls $fileName
        if (-not (Test-Path $sourceFile)) {
            throw "Compat native file was not found at $sourceFile"
        }
        Copy-Item -LiteralPath $sourceFile -Destination (Join-Path $compatDlls $fileName) -Force
    }
    Get-ChildItem -LiteralPath $compatDlls -Force | Where-Object {
        $nativeCompatFiles -notcontains $_.Name
    } | Remove-Item -Recurse -Force
} else {
    Write-Warning "Python 3.14 compatibility root was not found; SSL compatibility pack will not be bundled."
}

Set-Content -Path $manifest -Encoding UTF8 -Value $expectedManifest
Set-Content -Path $marker -Encoding ASCII -Value "ready"
