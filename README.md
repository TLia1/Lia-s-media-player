# Lia's Media Player

A **client-side** NeoForge mod for **Minecraft 1.21.1** that turns image, GIF and
video links shared in the in-game chat into rich, watchable media — without ever
leaving the game.

- Hover any **[picture]** or **[gif]** label to see an instant preview (animated
  GIFs play); click to pin it as a movable, resizable window.
- Click a **[video]** / **[youtube]** label to play it inside Minecraft, with sound,
  a seek bar, a play queue and background playback.
- Supports direct image and video files, animated Tenor GIFs, HLS/DASH streams and
  YouTube — with zero manual setup for most things.

It is purely cosmetic / quality-of-life: it only changes how your own client
displays links it receives in chat. It does not touch gameplay, the world, or what
other players see, and it is **not required by anyone else** on the server.

## At a glance

- **Mod id:** `liasmediaplayer` · **Version:** `1.0.1`
- **Loader:** NeoForge `21.1.230` · **Minecraft:** `1.21.1` · **Java:** 21
- **Side:** client-only (`@Mod(dist = Dist.CLIENT)`)
- **Dependencies:** NeoForge + Minecraft only. No bundled native libraries — video
  playback uses external `ffmpeg`/`ffprobe` and `yt-dlp` tools that the mod
  downloads automatically into the game folder on first launch.

## Documentation

- **[FEATURES.md](FEATURES.md)** — a friendly, user-facing tour of everything the
  mod does in-game.
- **[TECHNICAL-DETAILS.md](TECHNICAL-DETAILS.md)** — the technical reference:
  architecture, source layout, threading model, and how each piece works.

## Installing (for players)

1. Install NeoForge `21.1.230` for Minecraft `1.21.1`.
2. Drop the mod jar (from `build/libs/`, or a release) into your client's `mods/`
   folder. Do **not** put it on a server — it is client-only and does nothing there.
3. Launch the game. The first time you play a video, the mod quietly downloads
   `ffmpeg`/`ffprobe` (and, for YouTube, `yt-dlp`) into
   `<gamedir>/liasmediaplayer/bin/`. Everything else works immediately.

### If the automatic download can't run

If the game machine has no internet access (or the download fails), install the
tools yourself and either add them to your `PATH` or point the mod at them with JVM
arguments:

```
-Dliasmediaplayer.ffmpeg=C:\path\to\ffmpeg.exe
-Dliasmediaplayer.ffprobe=C:\path\to\ffprobe.exe
-Dliasmediaplayer.ytdlp=C:\path\to\yt-dlp.exe
```

(The matching environment variables `FFMPEG_PATH`, `FFPROBE_PATH` and
`YT_DLP_PATH` / `YTDLP_PATH` also work.) `ffmpeg`/`ffprobe` are needed for any
video; `yt-dlp` is only needed for YouTube links.

## Building (for developers)

This is a standard NeoForge mod built with the NeoGradle **userdev** plugin. There
is no shading and no bundled natives, so the jar stays small. From the project root:

```
./gradlew build       # builds the mod jar into build/libs/
./gradlew runClient   # launches a dev client with the mod loaded
```

Useful project settings live in `gradle.properties` (`mod_id`, `mod_version`,
`neo_version`, `minecraft_version`, …) and are expanded into
`src/main/resources/META-INF/neoforge.mods.toml` at build time.

If you ever hit dependency issues in your IDE, `./gradlew --refresh-dependencies`
refreshes the local cache, and `./gradlew clean` resets build outputs (without
touching your code).

## How it works (short version)

Incoming chat messages are scanned for media URLs and rewritten into compact,
clickable labels. Images are downloaded and cached, decoded (GIFs are fully
composited up front), and drawn as a hover preview or a pinned window. Videos are
played by shelling out to the external `ffmpeg` binary, which pipes raw frames and
PCM audio back to the mod; YouTube links are first resolved to a direct stream with
`yt-dlp`. All on-screen windows share one z-ordered stack so they move, resize and
stack predictably. See [minecraft-mod-client.md](TECHNICAL-DETAILS.md) for the
full design.

## License

All Rights Reserved (see `gradle.properties`). This project includes NeoForge MDK
template scaffolding; the Minecraft mappings it builds against are covered by
Mojang's mapping license — see <https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.
