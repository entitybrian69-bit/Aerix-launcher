param(
  [string]$Tag = "pc-nightly"
)

$ErrorActionPreference = "Stop"
$Repo = "entitybrian69-bit/Aerix-launcher"
$BaseUrl = if ($Tag -eq "latest") {
  "https://github.com/$Repo/releases/latest/download"
} else {
  "https://github.com/$Repo/releases/download/$Tag"
}

Write-Host "Fetching Aerix Launcher PC checksums ($Tag)..." -ForegroundColor Cyan
$SumsText = (Invoke-WebRequest -Uri "$BaseUrl/SHA256SUMS-PC.txt" -UseBasicParsing).Content
$Lines = $SumsText -split "`r?`n" | Where-Object { $_ -match '\S' }

$TargetLine = $Lines | Where-Object { $_ -match 'Aerix-PC-Setup\.exe$' } | Select-Object -First 1
if (-not $TargetLine) {
  $TargetLine = $Lines | Where-Object { $_ -match 'Aerix-PC-.*-win-x64\.exe$' } | Select-Object -First 1
}
if (-not $TargetLine) {
  throw "Could not find Windows installer entry in SHA256SUMS-PC.txt"
}

$Parts = ($TargetLine.Trim() -split '\s+')
$ExpectedHash = $Parts[0].ToLowerInvariant()
$AssetName = $Parts[-1]
$OutExe = Join-Path $env:TEMP $AssetName

Write-Host "Downloading $AssetName..." -ForegroundColor Cyan
Invoke-WebRequest -Uri "$BaseUrl/$AssetName" -OutFile $OutExe -UseBasicParsing

$ActualHash = (Get-FileHash -Path $OutExe -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ActualHash -ne $ExpectedHash) {
  Remove-Item -Path $OutExe -Force -ErrorAction SilentlyContinue
  throw "SHA-256 verification failed! Expected $ExpectedHash, got $ActualHash"
}

Write-Host "SHA-256 verified ($ActualHash). Starting Aerix Launcher PC installer..." -ForegroundColor Green
Unblock-File -Path $OutExe -ErrorAction SilentlyContinue
Start-Process -FilePath $OutExe
