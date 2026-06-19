# Lia's Media Player

A **client-side** NeoForge mod for **Minecraft 1.21.1** that turns image, GIF, video
and audio links shared in the in-game chat into rich, watchable (and listenable) media
— without ever leaving the game.

- Hover any **[picture]** or **[gif]** label to see an instant preview (animated
  GIFs play); click to pin it as a movable, resizable window.
- Click a **[video]** / **[youtube]** label to play it inside Minecraft, with sound,
  a seek bar, a play queue and background playback.
- Click an **[audio]** label to open a compact audio bar with its own queue, or build
  named **playlists** of audio/YouTube links and play them in order or shuffled.
- Drive the audio player with **configurable keybinds** (play/pause, next, previous,
  open playlists).
- Supports direct image, video and audio files, animated Tenor GIFs, HLS/DASH streams
  and YouTube — with zero manual setup for most things.

It is purely cosmetic / quality-of-life: it only changes how your own client
displays links it receives in chat. It does not touch gameplay, the world, or what
other players see, and it is **not required by anyone else** on the server.

## At a glance

- **Mod id:** <!-- mod_id -->`liasmediaplayer`<!-- /mod_id --> · **Version:** <!-- mod_version -->`1.2.4`<!-- /mod_version -->
- **Loader:** NeoForge <!-- neo_version -->`21.1.230`<!-- /neo_version --> · **Minecraft:** <!-- minecraft_version -->`1.21.1`<!-- /minecraft_version --> · **Java:** 21
- **Side:** client-only (`@Mod(dist = Dist.CLIENT)`)
- **API mod id:** `liasmediaplayerapi` — ships in the same JAR. The API is licensed under the **MIT License**; other mods can freely depend on the API to register custom media sources, control playback, and receive events.
- **Dependencies:** NeoForge + Minecraft only. No bundled native libraries — video
  playback uses external `ffmpeg`/`ffprobe` and `yt-dlp` tools that the mod
  downloads automatically into the game folder on first launch.

## Documentation

- **[FEATURES.md](FEATURES.md)** — a friendly, user-facing tour of everything the
  mod does in-game.
- **[API-DOCUMENTATION.md](API-DOCUMENTATION.md)** — reference for mod developers: how to depend on the API, register custom sources, control playback, and listen to events.
- **[TECHNICAL-DETAILS.md](TECHNICAL-DETAILS.md)** — the technical reference:
  architecture, package layout, threading model, and how each piece works.

## Installing (for players)

1. Install NeoForge <!-- neo_version -->`21.1.230`<!-- /neo_version --> for Minecraft <!-- minecraft_version -->`1.21.1`<!-- /minecraft_version -->.
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

## Contributing

Contributions are welcome! Here's how to get started:

1. **Fork** the repository and create a feature branch from `main`.
2. Make your changes — keep commits focused and well-described.
3. Run `./gradlew build` to make sure everything compiles.
4. Run `./gradlew updateDocs` if you changed any property in `gradle.properties`
   (version, mod id, etc.) — the CI will reject out-of-sync docs.
5. Open a **pull request** against `main`.

### Code guidelines

- **Java 21** — use modern Java features where they help readability.
- **Single responsibility** — each package has one job; keep it that way.
- **No new dependencies** — the mod ships with NeoForge + Minecraft only, no
  extra libraries.
- **Client-only** — everything runs on `Dist.CLIENT`. Don't add server-side code.
- **Internationalization (i18n)** — any new UI elements containing text must be internationalized in all supported languages (e.g., `en_us.json` and `fr_fr.json`) using `Component.translatable()`.
- Preserve existing comments and docstrings unless they are directly related to
  your changes.

### Adding a new media source

This is the most common way to extend the mod. **From within this mod**, write
a new `MediaSource` in the `source` package (implement `matches` / `kind` /
`label`) and register it in `MediaSources`. **From another mod**, depend on the
API (`liasmediaplayerapi`) and either call `MediaPlayerAPI.registerSource()` or
listen for `MediaSourceRegistrationEvent`. See
[API-DOCUMENTATION.md](API-DOCUMENTATION.md) for the full developer guide.

### Keeping docs in sync

Version numbers and mod properties in `README.md`, `TECHNICAL-DETAILS.md`, `API-DOCUMENTATION.md` and
`FEATURES.md` are managed by invisible HTML markers (e.g.
`<!-- mod_version -->1.2.4<!-- /mod_version -->`). **Never edit these values by
hand** — update `gradle.properties` and run:

```
./gradlew updateDocs
```

## How it works (short version)

Incoming chat messages are scanned for media URLs and rewritten into compact,
clickable labels. Images are downloaded and cached, decoded (GIFs are fully
composited up front), and drawn as a hover preview or a pinned window. Videos are
played by shelling out to the external `ffmpeg` binary, which pipes raw frames and
PCM audio back to the mod; YouTube links are first resolved to a direct stream with
`yt-dlp`. All on-screen windows share one z-ordered stack so they move, resize and
stack predictably.

Audio links open a compact bar backed by an audio-only engine that reuses the same
ffmpeg tooling (YouTube playlist entries play as sound only). Saved playlists persist to
a JSON file in the game folder, and a few configurable keybinds drive the active audio
player.

The code is organized into small, single-responsibility packages under
`com.lia.mediaplayer`: `source` (what a link is — the extension point), `chat`
(rewriting chat into labels), `gui` (the on-screen windows, overlay and playlist
screen), `image`, `video` and `audio` (the media engines), `media` (their shared volume,
URL resolver and title cache), `playlist` (saved playlists), `input` (keybinds), and
`tools` (the external binaries). Teaching the mod a new kind of link is normally just
one new `MediaSource` class plus one line in the registry. Other mods can integrate through the public API (`com.lia.mediaplayer.api`), which exposes source registration, playback control, volume, playlists, and playback events.
