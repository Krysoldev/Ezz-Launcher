$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$distDir = Join-Path $root "app\desktop\build\dist\main-release\app\EzzLauncher"

$isccCandidates = @(
    "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    "C:\Program Files\Inno Setup 6\ISCC.exe"
)

$iscc = $null
foreach ($candidate in $isccCandidates) {
    if (Test-Path $candidate) {
        $iscc = $candidate
        break
    }
}

if (-not $iscc) {
    throw "Inno Setup compiler (ISCC.exe) not found."
}

Write-Host "=== Building Ezz Launcher Production Installer & Updater ===" -ForegroundColor Cyan

if (-not (Test-Path (Join-Path $distDir "EzzLauncher.exe"))) {
    Write-Host "Release distributable not found. Generating via Gradle..." -ForegroundColor Yellow
    & (Join-Path $root "gradlew.bat") ":app:desktop:createReleaseDistributable"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle createReleaseDistributable failed."
    }
}

$issFile = Join-Path $PSScriptRoot "ezzlauncher.iss"
Write-Host "Compiling installer via Inno Setup ($iscc)..." -ForegroundColor Green
& $iscc $issFile
if ($LASTEXITCODE -ne 0) {
    throw "ISCC compilation failed."
}

$releaseDir = Join-Path $root "release"
$setupExe = Join-Path $releaseDir "EzzLauncher-Setup-1.0.0.exe"
$appExe = Join-Path $distDir "EzzLauncher.exe"

Copy-Item -Path $appExe -Destination (Join-Path $releaseDir "EzzLauncher.exe") -Force

$setupSize = (Get-Item $setupExe).Length
$exeSize = (Get-Item (Join-Path $releaseDir "EzzLauncher.exe")).Length

Write-Host "=== Build Complete ===" -ForegroundColor Cyan
Write-Host "EzzLauncher.exe: $exeSize bytes" -ForegroundColor White
Write-Host "EzzLauncher-Setup-1.0.0.exe: $setupSize bytes" -ForegroundColor White
