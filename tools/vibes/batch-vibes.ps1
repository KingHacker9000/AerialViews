param(
    [string]$RawDir = ".\raw",
    [string]$ReadyDir = ".\ready",
    [double]$Crossfade = 1.0,
    [int]$Width = 1280,
    [int]$Height = 720,
    [int]$Fps = 24,
    [int]$Crf = 20
)

$ErrorActionPreference = "Stop"
$LoopScript = Join-Path $PSScriptRoot "make-loop.ps1"

if (-not (Test-Path -LiteralPath $RawDir)) {
    throw "Raw video folder not found: $RawDir"
}
New-Item -ItemType Directory -Path $ReadyDir -Force | Out-Null

$Videos = Get-ChildItem -LiteralPath $RawDir -File | Where-Object {
    $_.Extension.ToLowerInvariant() -in @(".mp4", ".mov", ".mkv", ".webm")
} | Sort-Object Name

if ($Videos.Count -eq 0) {
    throw "No videos found in $RawDir"
}

foreach ($Video in $Videos) {
    $OutputName = [System.IO.Path]::GetFileNameWithoutExtension($Video.Name) + ".mp4"
    $OutputPath = Join-Path $ReadyDir $OutputName
    & $LoopScript `
        -Input $Video.FullName `
        -Output $OutputPath `
        -Crossfade $Crossfade `
        -Width $Width `
        -Height $Height `
        -Fps $Fps `
        -Crf $Crf
}

Write-Host "Processed $($Videos.Count) vibe video(s). Copy everything in '$ReadyDir' to one folder on the projector or USB drive."
