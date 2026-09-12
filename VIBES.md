# Vibes — projector ambient loops

This fork keeps Aerial Views' existing screensaver/settings stack and adds a simple projector-first launcher called **Vibes**.

Vibes intentionally keeps the videos **outside the APK**. Put generated loops in one folder on internal storage or a USB drive, choose that folder once in the app, and each video appears as a large remote-friendly button. Tap a scene and it loops forever in an immersive fullscreen player until you press Back.

The standalone player does **not** depend on Android Dream/screensaver settings, so it should also work on cheap projector Android builds that hide those settings.

## Folder format

Keep the selected folder flat for v1 (no subfolders):

```text
Vibes/
  01-snowy-gothic-window.mp4
  02-rainy-tokyo-apartment.mp4
  03-fireplace-library.mp4
  04-snowstorm-cabin-window.mp4
  ...
```

The numeric prefix is only for sorting. Vibes removes it, hyphens, underscores, and the extension when showing the scene name.

For maximum compatibility with random Android projector hardware, the included conversion tool outputs:

- H.264/AVC MP4
- 1280×720 by default
- 24 fps
- yuv420p
- H.264 Main profile, level 4.0
- no audio in v1

You can request 1920×1080 from the script later if the projector decodes it reliably.

---

# Free local video-generation pipeline

Everything below runs locally and has no per-video/API cost.

## 1. Install ComfyUI for the seed images

1. Go to the official ComfyUI repository: <https://github.com/Comfy-Org/ComfyUI>
2. Under **Installing → Windows Portable**, download the current NVIDIA portable package from the official releases.
3. Extract it somewhere with plenty of free space.
4. Download the official SDXL Base 1.0 checkpoint from Stability AI: <https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/blob/main/sd_xl_base_1.0.safetensors>
5. Put `sd_xl_base_1.0.safetensors` in:

   ```text
   ComfyUI_windows_portable\ComfyUI\models\checkpoints\
   ```

6. Start ComfyUI with `run_nvidia_gpu.bat`.
7. Open the local URL it prints, normally `http://127.0.0.1:8188`.

### Seed-image settings

Use the normal text-to-image workflow with:

- **Checkpoint:** `sd_xl_base_1.0.safetensors`
- **Width × height:** `1024 × 576`
- **Steps:** `25`
- **CFG:** `6.0`
- **Sampler:** `dpmpp_2m`
- **Scheduler:** `karras`
- **Denoise:** `1.0`

If memory is tight, use `896 × 512` instead.

Copy one **Seed image** prompt and the shared negative prompt from `tools/vibes/scene-prompts.md`, queue the prompt, and save the best result as a PNG. Do not spend time perfecting every image: one good still per scene is enough.

A good folder layout on the PC is:

```text
vibe-content/
  seeds/
  raw/
  ready/
```

Put the generated PNGs in `seeds/`.

## 2. Install FramePack for image-to-video

Only use the official project: <https://github.com/lllyasviel/FramePack>

The FramePack authors explicitly warn that similarly named FramePack websites are unofficial/fake. Do not pay for a FramePack website or download a random repack.

On Windows:

1. Open the official FramePack README.
2. Under **Installation → Windows**, use its **one-click package** link.
3. Extract the package.
4. Run `update.bat` once.
5. Run `run.bat`.
6. On first use, allow it to download the required models. The official README says the model download is over 30 GB.

FramePack's official requirements list Windows/Linux, an NVIDIA RTX 30/40/50-series GPU, and at least 6 GB VRAM.

### Generate each scene

For each seed PNG:

1. Upload the image on the left side of FramePack.
2. Copy the matching **Motion** prompt from `tools/vibes/scene-prompts.md`.
3. Generate **5 seconds** for the first test.
4. TeaCache can be enabled for quick drafts. When a scene looks good, disable TeaCache and render the keeper at full quality, as recommended by FramePack's README.
5. Save/download the resulting MP4 into `vibe-content\raw\` and give it the desired final name, for example:

   ```text
   01-snowy-gothic-window.mp4
   ```

For ambient loops, reject generations where the camera pans/zooms or architecture visibly bends. Small changes in snow, rain, fog, flames, water, clouds, or distant lights are ideal.

Start with the first 5–6 scenes. Once the settings look good on the actual projector, generate the rest unattended rather than hand-tuning 20 scenes up front.

## 3. Install FFmpeg

Open PowerShell and run:

```powershell
winget install --id Gyan.FFmpeg -e
```

Close and reopen PowerShell, then verify:

```powershell
ffmpeg -version
ffprobe -version
```

If `winget` cannot find that package on your machine, use the official FFmpeg download page at <https://ffmpeg.org/download.html> and add `ffmpeg`/`ffprobe` to PATH.

## 4. Turn the generated clips into projector-ready loops

From this repository:

```powershell
cd tools\vibes
```

To process one FramePack video:

```powershell
.\make-loop.ps1 `
  -Input "C:\path\to\vibe-content\raw\01-snowy-gothic-window.mp4" `
  -Output "C:\path\to\vibe-content\ready\01-snowy-gothic-window.mp4"
```

The script does a circular dissolve from the tail back into the beginning without reversing the video. That matters for rain/snow: a ping-pong loop would visibly make precipitation travel upward every other cycle.

To process a whole folder at once:

```powershell
.\batch-vibes.ps1 `
  -RawDir "C:\path\to\vibe-content\raw" `
  -ReadyDir "C:\path\to\vibe-content\ready"
```

Default output is 720p/24 fps. If the projector proves it can handle 1080p smoothly:

```powershell
.\batch-vibes.ps1 `
  -RawDir "C:\path\to\vibe-content\raw" `
  -ReadyDir "C:\path\to\vibe-content\ready" `
  -Width 1920 `
  -Height 1080
```

## 5. Copy videos to the projector

The easiest method is usually a USB drive:

1. Create a folder called `Vibes` on the USB drive.
2. Copy every MP4 from `vibe-content\ready\` into that folder.
3. Plug the drive into the projector.
4. Open Vibes.
5. Select **Choose Vibes folder**.
6. Pick the USB `Vibes` folder and grant access.
7. Select a scene. It should go fullscreen and loop indefinitely.
8. Press **Back** on the projector remote to return to the scene list.

Internal storage works the same way if you prefer to copy the files onto the projector.

---

# APK build and sideload

The repository's CI workflow produces a debug APK artifact for Vibes branches/PRs. For a local build you can also run:

```powershell
.\gradlew.bat assembleGithubDebug
```

The expected APK location is:

```text
app\build\outputs\apk\github\debug\app-github-debug.apk
```

## USB install

On many inexpensive Android projectors:

1. Copy the APK to a USB drive.
2. Open the projector's file manager.
3. Open the APK.
4. If prompted, enable **Install unknown apps** for that file manager.
5. Install and launch **Vibes**.

## ADB install fallback

If normal APK installation is awkward but the projector exposes Developer Options/ADB:

```powershell
adb install -r app\build\outputs\apk\github\debug\app-github-debug.apk
```

If the device supports Android's Dream/screensaver settings, the original Aerial Views screensaver stack remains available under **Advanced settings**. If it does not, just use Vibes' standalone fullscreen player.
