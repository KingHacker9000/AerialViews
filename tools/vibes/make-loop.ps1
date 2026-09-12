param(
    [Parameter(Mandatory = $true)]
    [string]$Input,

    [Parameter(Mandatory = $true)]
    [string]$Output,

    [double]$Crossfade = 1.0,
    [int]$Width = 1280,
    [int]$Height = 720,
    [int]$Fps = 24,
    [int]$Crf = 20
)

$ErrorActionPreference = "Stop"
$Invariant = [System.Globalization.CultureInfo]::InvariantCulture

if (-not (Test-Path -LiteralPath $Input)) {
    throw "Input video not found: $Input"
}
if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    throw "ffmpeg was not found on PATH. Install FFmpeg first; see VIBES.md."
}
if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
    throw "ffprobe was not found on PATH. Install FFmpeg first; see VIBES.md."
}

$DurationText = (& ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 $Input | Select-Object -First 1)
if (-not $DurationText) {
    throw "Could not read the video duration from: $Input"
}

$Duration = [double]::Parse($DurationText.Trim(), $Invariant)
if ($Duration -le ($Crossfade * 2.0 + 0.25)) {
    throw "Video is too short for a $Crossfade second crossfade. Use a longer clip or reduce -Crossfade."
}

$MainEnd = $Duration - $Crossfade
$D = $Crossfade.ToString("0.###", $Invariant)
$T = $Duration.ToString("0.###", $Invariant)
$End = $MainEnd.ToString("0.###", $Invariant)

# Build a circular dissolve without reversing motion:
#   1) keep source [D, T-D]
#   2) dissolve source tail [T-D, T] into source head [0, D]
#   3) append the dissolve after the middle section
# The rendered clip therefore starts and ends at the same point in the source timeline.
# fps + settb on each branch gives xfade a constant frame rate/timebase even for odd source files.
$Filter = "[0:v]split=3[vmain][vtail][vhead];" +
          "[vmain]trim=start=${D}:end=${End},setpts=PTS-STARTPTS,fps=${Fps},settb=AVTB[main];" +
          "[vtail]trim=start=${End}:end=${T},setpts=PTS-STARTPTS,fps=${Fps},settb=AVTB[tail];" +
          "[vhead]trim=start=0:end=${D},setpts=PTS-STARTPTS,fps=${Fps},settb=AVTB[head];" +
          "[tail][head]xfade=transition=fade:duration=${D}:offset=0[cross];" +
          "[main][cross]concat=n=2:v=1:a=0," +
          "scale=${Width}:${Height}:force_original_aspect_ratio=decrease," +
          "pad=${Width}:${Height}:(ow-iw)/2:(oh-ih)/2:black," +
          "setsar=1,format=yuv420p[outv]"

$OutputDirectory = Split-Path -Parent $Output
if ($OutputDirectory -and -not (Test-Path -LiteralPath $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
}

Write-Host "Creating loop: $Input -> $Output"
& ffmpeg -y -hide_banner -loglevel warning -i $Input `
    -filter_complex $Filter `
    -map "[outv]" `
    -an `
    -c:v libx264 `
    -preset medium `
    -crf $Crf `
    -profile:v main `
    -level:v 4.0 `
    -movflags +faststart `
    $Output

if ($LASTEXITCODE -ne 0) {
    throw "ffmpeg failed with exit code $LASTEXITCODE"
}

Write-Host "Done. Projector-ready H.264 loop written to: $Output"
