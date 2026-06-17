# Lia's Media Player — `liasmediaplayer`

A **client-side** NeoForge mod for Minecraft **1.21.1**. It improves how image, GIF
and **video** links that appear in the in-game chat are displayed: every supported
URL is rewritten into a compact, clickable label. Hovering an image/GIF label
renders a live preview of the picture (animated GIFs included) above the chat;
clicking it pins the image as a movable, resizable window. Clicking a video label
opens a fully-featured **in-game video player** (with sound, a seek bar and a play
queue).

- **Mod id:** `liasmediaplayer` · **Group:** `com.lia.mediaplayer` · **Version:** `1.0.1`
- **Loader:** NeoForge `21.1.230` · **Minecraft:** `1.21.1` · **Java:** 21
- **Side:** **client-only** (`@Mod(dist = Dist.CLIENT)`) — it has no effect on a
  server and is not required by anyone else on the server.
- **Dependencies:** NeoForge + Minecraft only. There are **no bundled native
  libraries**: video playback shells out to the external `ffmpeg`/`ffprobe` and
  `yt-dlp` command-line tools, which the mod downloads automatically into the game
  folder on first launch (see [`MediaBinaries`](#external-tools-mediabinaries)).

This mod is purely cosmetic / quality-of-life. It only changes how a player's own
client displays links it receives in chat; it does not modify gameplay, the world,
or what other players see.

For a user-facing tour of the features, see [`FEATURES.md`](FEATURES.md). For build
and install instructions, see [`README.md`](README.md).

## Why it exists

When a media link (a Discord attachment URL, a direct file, a Tenor GIF page, a
YouTube link, …) is posted into chat, vanilla Minecraft shows only the raw text:
long attachment URLs are noisy and nothing is actually viewable in-game. This mod
intercepts incoming chat messages on the client, swaps each supported URL for a
tidy label, and renders the real picture or video inline — so media is watchable
without ever leaving the game.

## Source layout (`src/main/java/com/lia/mediaplayer`)

| File | Role |
|---|---|
| `LiasMediaPlayer.java` | `@Mod(dist = Dist.CLIENT)` entry point. Holds the mod id (`liasmediaplayer`) and the shared logger, and kicks off the background download of the external tools (`MediaBinaries.installAllAsync()`) from its constructor. Event handlers are wired separately via `@EventBusSubscriber`. |
| `ChatImagePreviewHandler.java` | The image/GIF feature. Subscribes to chat-received and logout events: rewrites image/Tenor URLs into a gold `[picture]`/`[gif]` label, and renders the hovered preview over the chat (drawing is driven by `MediaWindowOverlay`). |
| `ImagePreviewCache.java` | Bounded, lazy cache of downloaded previews keyed by URL. Downloads/decodes on a background IO pool, uploads textures on the main thread, evicts the oldest entry past 100 (mirroring vanilla chat history). |
| `ImageWindow.java` | A pinned image/GIF preview drawn as a movable + resizable window (extends `MediaWindow`). Owns no textures of its own — it draws the current frame from `ImagePreviewCache`. |
| `ImageWindowManager.java` | Registry of pinned image windows keyed by URL; shows/closes them and caps how many are alive (6). Closing is just a map removal (textures live in the cache). |
| `GifDecoder.java` | Decodes animated GIFs into a sequence of fully composited frames with per-frame delays, with caps on frame count and total pixels to bound VRAM. Also exposes `toNativeImage` helpers used by the image and thumbnail caches. |
| `TenorResolver.java` | Turns a `tenor.com/view/...` share page into a direct, downloadable GIF URL by scraping the page markup. |
| `VideoSupport.java` | URL classification for the video player: direct files (`.mp4`/`.webm`/…), HLS/DASH manifests, and YouTube links; builds the `[video]`/`[youtube]` chat label. |
| `VideoUrlResolver.java` | Turns a chat link into something ffmpeg can open. Direct files/streams pass through; YouTube links are resolved to a direct stream URL by shelling out to `yt-dlp -g` (located via `MediaBinaries`). |
| `VideoChatHandler.java` | Rewrites video URLs into the clickable aqua label and handles cleanup on disconnect (disposes players, clears the thumbnail cache). |
| `VideoPlayerManager.java` | Registry of active video windows. Default behaviour is to **queue** a link into the front-most player; an independent window is only created on demand (shift-click) or when none exists. Caps the number alive (4) and disposes everything on disconnect. |
| `VideoPlayer.java` | The decode/playback engine. Drives a pair of background `ffmpeg` processes (one video, one audio) through `FFmpegCli`, queues decoded frames, plays synced PCM audio through a `SourceDataLine`, and exposes play/pause/seek; the render thread uploads the current frame to a `DynamicTexture`. |
| `VideoWindow.java` | The on-screen player UI (extends `MediaWindow`): the video image, a control bar (play/pause, next, queue, speaker/volume pop-up, seek bar + time) and the per-window **play queue** with a reorderable playlist panel. |
| `VideoThumbnailCache.java` | Builds and caches a small still image for each queued video (the YouTube thumbnail, or the first decoded frame for direct files) so the queue panel can show what each entry is. |
| `FFmpegCli.java` | Thin wrapper around the `ffmpeg`/`ffprobe` binaries. Probes stream metadata (via `ffprobe` JSON, parsed with Gson) and starts ffmpeg processes that pipe raw `rgba` video and `s16le` PCM audio to stdout. Replaces the old in-process JavaCV grabber. |
| `MediaBinaries.java` | Locates — and, if missing, downloads — the external `yt-dlp`, `ffmpeg` and `ffprobe` tools. Shared plumbing for finding binaries, fetching the official releases, and unpacking them into the game folder. |
| `MediaWindow.java` | Shared base for the on-screen windows. Owns the box geometry, the corner buttons (open-in-browser link, hide, close), the bottom-right resize grip, the move/resize/zoom gestures, and the global **z-order**. Subclasses supply the content and any control bar. |
| `MediaWindowOverlay.java` | Single coordinator that renders and routes input for *all* windows (images + videos) as one z-ordered stack. Owns the chat-screen render pass, the HUD overlay pass, mouse handling (click-to-front, click-on-link to open/queue), the "reveal hidden videos" button, and the auto-advance/auto-close of finished videos. |

Resources:

- `src/main/resources/META-INF/neoforge.mods.toml` — mod metadata. Declares only
  `neoforge` and `minecraft` as required dependencies. The `mod_id`, `mod_name`,
  `mod_version`, etc. are expanded from `gradle.properties` at build time.

## How a link becomes media

Two independent `@EventBusSubscriber` handlers rewrite incoming chat, and a single
overlay coordinator does all the drawing and input.

### 1. Rewrite incoming chat

Both `ChatImagePreviewHandler` and `VideoChatHandler` subscribe to
`ClientChatReceivedEvent.System` / `.Player`. Each received message is walked
component-by-component (preserving inherited styles), and every URL matching
`https?://\S+` is tested:

- **Images** (`ChatImagePreviewHandler.isSupportedUrl`): a direct image path ending
  in `.png`, `.jpg`, `.jpeg`, `.gif`, or `.bmp`, **or** a Tenor share page (host
  `tenor.com`/`www.tenor.com` with `/view/` in the path, locale prefix allowed).
  Replaced by a gold `[gif]` (Tenor) or `[picture]` label, and the URL is
  registered with `ImagePreviewCache.track` for lazy loading.
- **Videos** (`VideoSupport.isVideoUrl`): a direct video file, an HLS/DASH
  manifest, or a YouTube link (see [Recognized links](#recognized-links-videosupport)).
  Replaced by an aqua, underlined `[video]` / `[youtube]` label.

Both labels carry an `OPEN_URL` click event pointing at the original URL — that is
how the overlay finds the URL again under the cursor. The image and video matchers
are intentionally **disjoint** (`.gif` is an image, `.mp4` is a video), so the two
features compose on the same chat message without fighting over a link. Messages
with no supported link are left untouched.

### 2. Render & input — the shared window stack (`MediaWindowOverlay`)

All on-screen windows — pinned `ImageWindow`s and `VideoWindow`s — live in one
stack ordered by `MediaWindow.zOrder()`:

- **On the chat screen** (`ScreenEvent.Render.Post` over a `ChatScreen`): every
  visible window is drawn back-to-front, each in its own depth band, with the text
  buffer flushed after each one so a front window fully occludes the one behind it
  (content *and* batched text like the seek time / volume pop-up). Then the
  "reveal hidden videos" button and finally the image hover preview are drawn on
  top.
- **During gameplay** (`RenderGuiEvent.Post`, no screen open): the same windows are
  drawn on the HUD as **picture only** (no controls), so a clip keeps showing while
  you play.
- **Mouse input** (`ScreenEvent.MouseButtonPressed/Released/Dragged/Scrolled`) is
  tested **top-first**, so only the front-most window under the cursor reacts;
  clicking a window raises it (`bringToFront`). If no window consumes the click but
  the cursor is over a media link, the overlay spawns/queues the right thing:
  - **Image link** → pins it via `ImageWindowManager.show` and brings it to front.
  - **Video link** → **queues** it into the front-most player by default
    (`VideoPlayerManager.enqueue`); **Shift-click** opens a separate, independent
    window (`VideoPlayerManager.open`).

### 3. Cleanup

On `ClientPlayerNetworkEvent.LoggingOut`, the image side disposes pinned windows
and clears `ImagePreviewCache`; the video side disposes all players and clears
`VideoThumbnailCache`.

## Images & GIFs

### Preview cache (`ImagePreviewCache`)

A `LinkedHashMap` keyed by URL, capped at **100 entries** (mirrors
`ChatComponent.MAX_CHAT_HISTORY`), evicting and releasing the texture of the oldest
entry when full. Threading rules are strict:

- **IO pool (`Util.ioPool()`):** the HTTP download and image/GIF decode. Tenor
  pages are resolved to a direct GIF first. Hard limits: connect/read timeouts
  (5 s / 10 s), an **8 MB** max image size, and a browser-like `User-Agent` /
  `Accept: image/*` header. Format is sniffed by magic bytes (PNG signature,
  `GIF87a`/`GIF89a`): GIFs go through `GifDecoder`, PNG via `NativeImage`, everything
  else via `ImageIO` normalized to ARGB.
- **Main thread:** texture creation (`DynamicTexture` registered under
  `liasmediaplayer:preview/<n>`), cache mutation, and publishing the result back to
  the `Entry`. If the entry was evicted while the download was in flight, the frames
  are closed and discarded.

Each `Entry` tracks its state (`IDLE`/`LOADING`/`LOADED`/`FAILED`), the per-frame
texture locations, per-frame delays, total duration and image size, and exposes
`currentFrame()` which picks the right GIF frame from the wall clock (and always
returns the single frame for static images).

### Hover preview & pinning

While a `ChatScreen` is open, `ChatImagePreviewHandler.renderHoverPreview` reads the
`OPEN_URL` style under the cursor and, for a supported URL, calls
`ImagePreviewCache.getOrLoad` (which kicks off the download on first hover):

- **LOADING / IDLE** → a `"Loading image..."` tooltip,
- **FAILED** → a `"Couldn't load image"` tooltip,
- **LOADED** → the image, scaled down (never up) to fit within roughly half the
  screen, clamped on-screen, on a dark backing rectangle, above the cursor.

Clicking the label pins it: `ImageWindowManager.show` creates (or re-shows) an
`ImageWindow` that shares the cached texture. It is a `MediaWindow`, so it can be
dragged, resized from the corner grip, zoomed with the mouse wheel (plain or
`Ctrl`), opened in the browser with the ↗ button, and closed with the **×** button.
When a window is pinned and visible, the floating hover preview for that URL is
suppressed so the two don't stack. The number of pinned image windows is capped at
**6** (oldest dropped).

### GIF decoding (`GifDecoder`)

A GIF stores each frame as a (possibly partial) patch over the previous canvas plus
a disposal method. `GifDecoder` composites every frame onto a persistent canvas
**once**, on the IO thread, producing ready-to-upload coalesced frames so the render
path never re-decodes. To bound VRAM it caps frames at **256** and total kept pixels
at **24M** (~96 MB RGBA), dropping frames evenly and folding their delays into the
kept ones so timing stays correct. Per-frame delays are normalized: a 0/absent delay
becomes 100 ms (browser-like) and nothing animates faster than 20 ms.

### Tenor resolution (`TenorResolver`)

A Tenor `/view/` link is an HTML page, not an image, so it never matches the
file-extension check. `TenorResolver.resolve` fetches the page (capped at 512 KB,
browser-like `User-Agent`) and extracts a media id from its markup, trying in order:
the `contentUrl` meta tag (either attribute order), then any `media*.tenor.com/m/<id>/`
URL on the page. Because those `/m/<id>/` URLs are hot-link protected, it rebuilds the
canonical direct-download endpoint `https://c.tenor.com/<id>/tenor.gif`. Older page
layouts fall back to a plain `og:image` GIF URL. `extractMediaUrl` is package-private
for testing.

## Video player

### Recognized links (`VideoSupport`)

`isVideoUrl` matches three families, kept disjoint from the image/GIF matcher:

- **Direct video files** — path ends in `.mp4`, `.webm`, `.mov`, `.mkv`, `.m4v`,
  `.avi`, `.flv`, `.ogv`, or `.ts`.
- **Adaptive streams** — an `.m3u8` (HLS) or `.mpd` (DASH) manifest.
- **YouTube** — `youtube.com/watch`, `/shorts/`, `/embed/`, `/live/`, the
  mobile/music hosts, or a `youtu.be/...` short link.

The chat label is `[youtube]` for YouTube links and `[video]` otherwise.

### URL resolution (`VideoUrlResolver`)

Direct files and manifests are handed to ffmpeg unchanged. A YouTube page is not a
media file and there is no reliable pure-Java extractor, so the resolver shells out
to `yt-dlp` (`yt-dlp -g -f "best[height<=720][acodec!=none][vcodec!=none]/best[height<=720]/best"`,
plus `--no-playlist --quiet --no-warnings`) and takes the first direct URL it prints.
It prefers a single progressive stream that already muxes audio + video. If `yt-dlp`
is missing or times out (25 s), the player fails with a clear message instead of
hanging. This always runs on a background thread.

### External tools (`MediaBinaries`)

FFmpeg is **no longer embedded** in the jar (it used to be linked in via
JavaCV/bytedeco, which made the jar huge). Instead the mod manages three external
command-line tools the same way: `yt-dlp` (YouTube resolution), and `ffmpeg` +
`ffprobe` (decoding/probing). `MediaBinaries.installAllAsync()` runs at launch on a
background daemon thread so the tools are usually ready before the first link is
clicked.

For each tool, it looks in order for:

1. an explicit override — the `-Dliasmediaplayer.<tool>=<path>` JVM argument, or the
   matching environment variable (`YT_DLP_PATH`/`YTDLP_PATH`, `FFMPEG_PATH`,
   `FFPROBE_PATH`);
2. a copy this mod previously downloaded into `<gamedir>/liasmediaplayer/bin/`;
3. every directory on `PATH`;
4. common per-OS install locations (winget Links, scoop shims, Chocolatey, pip
   `Scripts`, Homebrew, `/usr/local/bin`, `~/.local/bin`, …);
5. the bare command name (trusting the launcher's `PATH`).

If none of those yield a usable binary, it downloads the official release into
`<gamedir>/liasmediaplayer/bin/` and uses that — yt-dlp from the project's GitHub
releases, ffmpeg from BtbN's builds (Windows `.zip` / Linux `.tar.xz`, both holding
ffmpeg *and* ffprobe), and evermeet.cx single-binary zips on macOS. Archives are
unpacked with the JDK's zip support (or the system `tar` for `.tar.xz`), with
zip-slip protection. The download is attempted **at most once per tool per game
session**; failures are logged with every location that was checked, so the usual
fix is to add `-Dliasmediaplayer.ffmpeg=...` (etc.) to the launch arguments.

> **Why a GUI launcher needs this.** Minecraft is normally started from a launcher
> whose environment does not include the directories a user added to their shell
> `PATH`, so a bare `ffmpeg`/`yt-dlp` command frequently fails even when it works in
> a terminal. The auto-download into the game folder side-steps that entirely.

### FFmpeg wrapper (`FFmpegCli`)

A thin wrapper around the binaries — no native libraries on the classpath:

- **Probe** (`probe`): runs `ffprobe -print_format json -show_format -show_streams`
  and parses the JSON with Gson into a `MediaInfo` record (width, height, fps,
  duration, audio sample-rate/channels). HTTP inputs get resilience flags
  (`-reconnect`, `-rw_timeout`, a `User-Agent`); local/other protocols don't.
- **Video** (`openVideo`): `ffmpeg … -re -vf scale=W:H -pix_fmt rgba -f rawvideo -`
  writes tightly-packed `W*H*4`-byte frames to stdout, already scaled. `-re` paces
  the pipe to the native frame rate so the picture stays in step with the audio
  clock.
- **Audio** (`openAudio`): `ffmpeg … -f s16le -acodec pcm_s16le -ar … -ac … -` writes
  signed 16-bit little-endian PCM, ready to hand straight to a `SourceDataLine`.
- **Single frame** (`grabRawFrame`): grabs one scaled `rgba` frame at a timestamp
  (used by the thumbnail cache).

Both seek (`-ss`) and end-of-stream are handled by the caller. stderr is discarded;
a real failure surfaces as an early stdout EOF (the URL was already validated by the
probe). Everything here runs on background threads.

### Decoding & playback (`VideoPlayer`)

Each player owns:

- a **decode thread** that resolves the URL, probes it, computes a target size that
  fits within **854×480** (never upscaled, even dimensions), launches the ffmpeg
  video (and, if present, audio) session, then reads `rgba` frames in order. Each
  frame is timestamped from the frame index × frame duration, converted to the
  `abgr` ints `NativeImage` expects, and pushed onto a bounded queue (capacity 48);
  the **oldest** frame is dropped when full so video never blocks audio. It also
  handles pause and seek, and parks in `ENDED` at end-of-stream until sought or
  disposed.
- an **audio thread** per session that reads PCM from the audio process and
  blocking-writes it to the `SourceDataLine` (whose blocking write paces playback);
  a stopped line back-pressures into a clean pause.
- the **render/main thread** path: `prepareFrame()` advances to the queued frame
  whose timestamp is ≤ the playback clock and uploads it to a reused
  `DynamicTexture` (`liasmediaplayer:video/<n>`). Textures and GL are touched only
  here.

**Clock & sync.** When the video has audio, the line's `getMicrosecondPosition()` is
the master clock so the picture follows the sound; otherwise a wall clock that only
advances while playing is used. Late frames are skipped.

**Volume.** The level (0..1) is a **static, shared** value across all players, so it
stays in sync and carries over when a window swaps to the next queued video. It is
applied to the line as dB gain (falling back to a linear `VOLUME` control) and is
continuously re-scaled by Minecraft's live master-volume slider.

**Seek & controls.** `togglePause`/`pause`/`resume` gate the decode thread and
stop/start the line; `seekTo`/`seekToFraction` relaunch the ffmpeg session from the
new position (flushing the frame queue + audio line and re-baselining the clock). A
seek is clamped to ~0.5 s before the end so it can't land in a slice with no
decodable frame. `dispose()` stops the threads, kills the processes, closes the line
and releases the texture.

### Window, controls & queue (`VideoWindow`)

The window is anchored bottom-right by default (so it never covers the left-aligned
chat link), scaled to about a third of the screen. Its control bar carries: a
play/pause button; a **next** button and a **queue** (playlist) button when
something is queued; a **speaker/mute** button with a vertical **volume slider that
pops up above it** on hover (shown only when the video has sound); and a draggable
seek bar with a knob plus an elapsed `/` total time read-out (`LIVE` when the
duration is unknown, with a `+N` suffix showing how many videos are queued). The
top-right corner has the inherited **open-in-browser** (↗), **hide** (`_`) and
**close** (`x`) buttons. Move/resize/zoom come from `MediaWindow`.

**The play queue.** Instead of one window per link, extra videos are appended to the
current window's queue. When the current video ends (or **next** is pressed),
`advance()` disposes the current `VideoPlayer` and starts the next queued URL in the
same window; if nothing is queued, `MediaWindowOverlay` closes the window
automatically. The **queue button** opens a playlist panel docked to the **right**
of the player (the player slides left to make room when it has no fixed position)
showing each entry's thumbnail and label. The panel matches the player's height and
**scrolls** when there are more entries than fit, with a scrollbar on the right
gutter; rows can be **clicked to jump**, **reordered** (up/down arrows) or
**removed** (×); the mouse wheel scrolls the panel.

**Volume wheel.** With the cursor over the window (and the panel closed), the plain
mouse wheel changes the volume in 10% steps; `Ctrl`+wheel always zooms the window.

### Queue thumbnails (`VideoThumbnailCache`)

A bounded cache (64 entries) keyed by URL. For YouTube links it downloads the
predictable `i.ytimg.com/vi/<id>/…jpg` thumbnail (no yt-dlp needed); for direct
files/streams it opens the media with ffmpeg just long enough to grab the first
decoded frame (seeking a touch in to avoid a black intro frame). Loading happens on
the IO pool; the `DynamicTexture` (`liasmediaplayer:videothumb/<n>`) is created back
on the main thread. Each `Thumb` tracks `IDLE`/`LOADING`/`LOADED`/`FAILED`.

## Windows, move & resize (`MediaWindow`)

The shared base for both image and video windows owns:

- **Geometry & chrome** — the box, padding, the top-right corner buttons (link,
  optional hide, close) and the bottom-right resize grip.
- **Move** — drag the window body; the first drag/resize "pins" the position so the
  window stops auto-anchoring and keeps its placement.
- **Resize** — drag the corner grip, or **`Ctrl`+mouse-wheel** to zoom; content is
  scaled between `MIN_CONTENT` (48 px) and 6× and always clamped so the whole box
  (with its control bar) stays on screen and the grip remains grabbable.
- **Z-order** — a monotonic counter hands out a stacking order; `bringToFront()`
  raises a window above all others. `MediaWindowOverlay` draws low-to-high and tests
  input high-to-low.

Subclasses provide the intrinsic content size, how to draw it, the default anchor,
and any control bar. `ImageWindow` centers itself and has no control bar;
`VideoWindow` anchors bottom-right, reserves an 18 px control bar and adds a hide
button.

## Building & installing

This is a standard NeoForge mod built with the **NeoGradle userdev** plugin — no
Shadow/shading and no bundled natives, so the produced jar is small. From the
project root:

```
./gradlew build       # builds the mod jar into build/libs/
./gradlew runClient   # launches a dev client with the mod
```

Install by dropping the built jar from `build/libs/` into the **client's** `mods/`
folder (alongside NeoForge `21.1.230` for Minecraft `1.21.1`). It is a client-only
mod: do not install it on a server, where it does nothing.

The first time you play a video, the mod downloads `ffmpeg`/`ffprobe` and (for
YouTube) `yt-dlp` into `<gamedir>/liasmediaplayer/bin/`. If that automatic download
can't run (no internet on the game machine, etc.), install the tools yourself and
either put them on `PATH` or point the mod at them with JVM arguments, e.g.:

```
-Dliasmediaplayer.ffmpeg=C:\path\to\ffmpeg.exe
-Dliasmediaplayer.ffprobe=C:\path\to\ffprobe.exe
-Dliasmediaplayer.ytdlp=C:\path\to\yt-dlp.exe
```

See [`README.md`](README.md) for the full setup, and [`FEATURES.md`](FEATURES.md)
for what the mod does in-game.

## Maintenance notes

- **URL contract.** The mod reacts to whatever URL shapes it recognizes. Extend
  `ChatImagePreviewHandler.isImageUrl` / `TenorResolver.isTenorPageUrl` for new image
  sources, and `VideoSupport.isVideoUrl` for new video sources. Keep the image and
  video matchers disjoint so they compose on the same message.
- **Tenor scraping.** `TenorResolver` parses page HTML; if Tenor changes its markup,
  update the patterns. `extractMediaUrl` is unit-test-friendly.
- **Threading split (images).** All cache/texture access stays on the render/main
  thread; only downloading and decoding run on the IO pool. Keep that split when
  modifying `ImagePreviewCache` and `VideoThumbnailCache`.
- **Threading split (video).** Only `VideoPlayer`'s decode/audio threads touch the
  ffmpeg processes and the audio line; only the render thread touches the
  `DynamicTexture` and OpenGL. `FFmpegCli`/`VideoUrlResolver`/`MediaBinaries` calls
  must never run on the render thread (they spawn processes and block).
- **External tools.** If ffmpeg/yt-dlp download endpoints or archive layouts change,
  update the URLs and the unpack logic in `MediaBinaries`. If YouTube changes
  formats, updating `yt-dlp` is usually enough (delete the copy in
  `liasmediaplayer/bin/` to force a fresh download).
- **Tuning.** Frame upload converts pixels one-by-one on the main thread; lower
  `MAX_WIDTH`/`MAX_HEIGHT` in `VideoPlayer` if large videos cause hitching. Window
  caps live in `ImageWindowManager` (6) and `VideoPlayerManager` (4).
