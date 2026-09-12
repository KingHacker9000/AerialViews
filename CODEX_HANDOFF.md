# Codex handoff — finish Vibes end to end

The user wants this project finished with as little manual work as possible. Treat this as an execution task, not a planning exercise.

## Goal

On a Windows PC with an NVIDIA GPU, automate the remaining path from this repository to a folder of ambient projector loops and, when a projector is reachable by ADB, install/sync everything to the projector.

Do **not** use paid APIs or paid generation services. Prefer official upstream repositories/releases and local inference only.

## Current repo state

The Android Vibes launcher/player is already implemented on `master`.

Existing useful files:

- `VIBES.md` — current manual setup notes
- `tools/vibes/scene-prompts.md` — 12 seed-image + motion prompts
- `tools/vibes/make-loop.ps1` — converts one raw video into a circular projector-friendly H.264 loop
- `tools/vibes/batch-vibes.ps1` — batch loop processing
- `.github/workflows/ci.yml` — builds the Android debug APK

Videos intentionally stay outside the APK.

## What to implement

### 1. One-command bootstrap

Create a PowerShell entry point:

```powershell
.\tools\vibes\bootstrap-vibes.ps1
```

It should:

1. Detect Windows, NVIDIA GPU/VRAM, free disk space, Python, Git, FFmpeg, and ADB.
2. Create a working directory outside the repo by default, e.g. `$env:USERPROFILE\VibesContent`.
3. Create `seeds`, `raw`, `ready`, `logs`, and `models`/tool directories as appropriate.
4. Install or clearly bootstrap missing free dependencies with as few prompts as possible.
5. Never download models into the Git repository.
6. Resume safely after interruption instead of regenerating completed assets.

### 2. Automate seed image generation

Use local SDXL through official ComfyUI (or another clearly better fully open/local route if current upstream tooling has changed).

Requirements:

- Parse all scenes from `tools/vibes/scene-prompts.md`.
- Generate at least one 16:9 seed image per scene.
- Use deterministic filenames such as `01-snowy-gothic-window.png`.
- Save generation metadata/settings next to the outputs in JSON.
- Default to settings appropriate for an 8 GB-class NVIDIA GPU; detect and adapt instead of assuming.
- Make camera-locked, no-people, no-text outputs the priority.
- If generation fails due to VRAM, automatically retry at a lower resolution/settings.

If ComfyUI exposes a local HTTP API, use it rather than requiring the user to click through the UI.

### 3. Automate image-to-video generation

Use the official `lllyasviel/FramePack` project if it remains the best compatible free option.

Requirements:

- Use only the official upstream repository/release.
- Download models once and cache them outside the repo.
- Batch every seed image with its matching Motion prompt.
- Start with a short draft duration and conservative settings suitable for the detected GPU.
- Produce files into `raw` using the same numeric scene names.
- Prefer locked camera and subtle ambient motion.
- Retry failed jobs where practical, but cap retries and record failures.
- Never silently substitute a paid cloud service.

If upstream FramePack does not have a clean batch/CLI interface, inspect its Python/Gradio code and create a thin local batch wrapper rather than asking the user to manually upload 12 images.

### 4. Make final loops automatically

Invoke the existing `batch-vibes.ps1` after raw generation.

Default final output:

- H.264/AVC MP4
- 1280x720
- 24 fps
- yuv420p
- no audio
- circular crossfade loop

Run `ffprobe` validation on every final file and write `ready/manifest.json` with filename, duration, resolution, codec, FPS, size, generation status, and source prompt name.

### 5. Build and retrieve the Android APK

Support both paths:

- Local: `gradlew.bat assembleGithubDebug`
- GitHub Actions artifact when convenient/available

Validate that the APK exists before claiming success.

### 6. Optional projector deployment

Create:

```powershell
.\tools\vibes\deploy-projector.ps1
```

Behavior:

- If an ADB device is connected, detect it.
- Install/update the Vibes APK via `adb install -r`.
- Copy the entire `ready` folder to a predictable projector location such as `/sdcard/Movies/Vibes/`.
- Verify the remote files after copy.
- Do not delete unrelated projector files.
- If ADB is unavailable, print the exact USB-copy fallback and stop cleanly.

Do not attempt to automate Android Storage Access Framework approval unless the device genuinely supports a safe ADB/intents route; it is okay for the user to select the Vibes folder once on-device.

### 7. Quality-control artifacts

Produce:

- `tools/vibes/bootstrap-vibes.ps1`
- any Python helpers under `tools/vibes/automation/`
- `tools/vibes/deploy-projector.ps1`
- updated `VIBES.md` reduced to a short one-command path plus troubleshooting
- generated-work directories ignored by Git
- useful logs and machine-readable manifests
- tests for prompt parsing, resumability/state handling, and manifest generation where practical

### 8. Validation before completion

Do not report completion until you have actually checked what can be checked on the machine:

- Gradle Android build succeeds, or document the exact blocking external dependency/error.
- Seed automation produces at least one real image.
- Image-to-video automation produces at least one real video.
- FFmpeg loop processing succeeds on that video.
- `ffprobe` confirms expected codec/resolution/FPS.
- Scripts are rerunnable without destroying previous work.

If large model downloads or long GPU jobs remain in progress, leave the pipeline resumable and state exactly what is pending rather than pretending it is done.

## User-effort target

The intended final experience is approximately:

```powershell
git pull
.\tools\vibes\bootstrap-vibes.ps1
```

Then, when the projector is connected:

```powershell
.\tools\vibes\deploy-projector.ps1
```

The user should not need to manually copy prompts between apps, click Generate 12 times, rename outputs, run FFmpeg per-file, or manually build the APK.
