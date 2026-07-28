param(
    [Parameter(Mandatory = $true)]
    [string] $NmapVersion,

    [Parameter(Mandatory = $true)]
    [string] $InstallerSha256,

    [Parameter(Mandatory = $true)]
    [string] $OutputDir,

    [Parameter(Mandatory = $true)]
    [string] $DownloadDir
)

$ErrorActionPreference = "Stop"

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
    throw "7-Zip is required to extract the Nmap installer. Install 7-Zip or set BURP_PYTHON_7Z to 7z.exe."
}

$installerName = "nmap-$NmapVersion-setup.exe"
$installerUrl = "https://nmap.org/dist/$installerName"
$installer = Join-Path $DownloadDir $installerName
$normalizedHash = $InstallerSha256.Trim().ToUpperInvariant()
$python = Join-Path $OutputDir "zenmap\bin\python.exe"

New-Item -ItemType Directory -Path $DownloadDir -Force | Out-Null
if (-not (Test-Path -LiteralPath $installer)) {
    Write-Host "Downloading $installerUrl"
    Invoke-WebRequest -Uri $installerUrl -OutFile $installer -UseBasicParsing
}

$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $installer).Hash.ToUpperInvariant()
if ($actualHash -ne $normalizedHash) {
    throw "Nmap installer SHA-256 mismatch for $installerName. Expected $normalizedHash but found $actualHash."
}

if (Test-Path -LiteralPath $python) {
    Write-Host "Nmap test runtime already prepared at $OutputDir"
    exit 0
}

if (Test-Path -LiteralPath $OutputDir) {
    Remove-Item -LiteralPath $OutputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$sevenZip = Get-SevenZip
& $sevenZip x $installer "-o$OutputDir" -y | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "7-Zip failed to extract $installerName with exit code $LASTEXITCODE."
}

if (-not (Test-Path -LiteralPath $python)) {
    throw "Extracted Nmap installer did not contain Zenmap Python at $python."
}
