# <!-- mod_name -->Lia's Media Player<!-- /mod_name --> — <!-- mod_id -->`liasmediaplayer`<!-- /mod_id -->

A **client-side** NeoForge mod for Minecraft **<!-- minecraft_version -->1.21.1<!-- /minecraft_version -->**. It improves how image, GIF,
**video** and **audio** links that appear in the in-game chat are displayed: every
supported URL is rewritten into a compact, clickable label. Hovering an image/GIF
label renders a live preview of the picture (animated GIFs included) above the chat;
clicking it pins the image as a movable, resizable window. Clicking a video label
opens a fully-featured **in-game video player** (with sound, a seek bar and a play
queue); clicking an audio label opens a compact **audio bar** with its own queue. A
**playlist manager** (a chat button or a keybind) lets you save named playlists of
audio/YouTube links and play them in order or shuffled, and a set of **configurable
keybinds** drives the active audio player.

- **Mod id:** <!-- mod_id -->`liasmediaplayer`<!-- /mod_id --> · **Group:** <!-- mod_group_id -->`com.lia.mediaplayer`<!-- /mod_group_id --> · **Version:** <!-- mod_version -->`1.2.4`<!-- /mod_version -->
- **Loader:** NeoForge <!-- neo_version -->`21.1.230`<!-- /neo_version --> · **Minecraft:** <!-- minecraft_version -->`1.21.1`<!-- /minecraft_version --> · **Java:** 21
- **Side:** **client-only** (`@Mod(dist = Dist.CLIENT)`) — it has no effect on a
  server and is not required by anyone else on the server.
- **Dependencies:** NeoForge + Minecraft only. There are **no bundled native
  libraries**: video playback shells out to the external `ffmpeg`/`ffprobe` and
  `yt-dlp` command-line tools, which the mod downloads automatically into the game
  folder on first launch (see [`MediaBinaries`](#external-tools-toolsmediabinaries)).

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

## Architecture at a glance

The code is split into small, single-responsibility packages under
`src/main/java/com/lia/mediaplayer`. Each package depends only on the ones below
it, so the dependency graph is acyclic and there is one obvious place for every
concern:

| Package | Responsibility | Depends on |
|---|---|---|
| `api` | **Public API for other mods.** Interfaces, enums, the `MediaPlayerAPI` facade, and events. This package is the only thing external mods should import. | (NeoForge only) |
| `source` | **What is this link?** URL classification and chat labels. The extension point. | (Minecraft only) |
| `image` | Image/GIF download, decode and texture cache. | `source` |
| `media` | Cross-cutting playback helpers shared by the two engines: the single shared **volume**, the **URL resolver** (incl. yt-dlp) and the **title cache**. | `source`, `tools` |
| `video` | Video playback engine (decode, audio, thumbnails). | `source`, `tools`, `image`, `media` |
| `audio` | Audio-only playback engine (probe, PCM pump, clock, seek). | `source`, `tools`, `media` |
| `tools` | Locating/downloading and invoking the external `ffmpeg`/`ffprobe`/`yt-dlp` binaries. | (root) |
| `playlist` | Saved named playlists and their JSON persistence. | (Minecraft only) |
| `gui` | Everything drawn on screen: the window base, the overlay coordinator, the image/video/audio windows, their registries, the hover preview and the playlist screen. | `source`, `image`, `video`, `audio`, `media`, `playlist` |
| `input` | The configurable keybinds and the handler that drives the active audio player. | `gui` |
| `chat` | Hooking the chat events and rewriting links into labels. | `source`, `image`, `video`, `audio`, `gui` |
| *(root)* | The `@Mod` entry points (main mod + API). Fires `MediaSourceRegistrationEvent` during client setup. | `tools`, `api` |

The two playback engines (`video`, `audio`) are siblings that share their common
machinery through the lower `media` layer rather than depending on each other, so the
dependency graph stays acyclic. In particular the **volume is a single shared value**
(`media.Volume`) used by both, so one level controls everything and carries over when a
window swaps tracks.

The root `LiasMediaPlayer` class holds the shared `MODID`/`LOGGER` constants used
across every package and kicks off the background tool download.

### Source classification: the extension point (`source`)

Adding a new kind of media link is the most common way the mod grows, so it is the
one thing made trivially extensible. A `MediaSource` answers three questions about a
URL — does it `matches(...)`, what `kind()` is it (`IMAGE` or `VIDEO`), and what chat
`label(...)` should it show — and nothing else. The built-in sources are:

| Source | Recognizes | Kind | Label |
|---|---|---|---|
| `ImageFileSource` | a path ending in `.png`/`.jpg`/`.jpeg`/`.gif`/`.bmp` | IMAGE | `[picture]` |
| `TenorSource` | a `tenor.com/view/...` share page (locale prefix allowed) | IMAGE | `[gif]` |
| `DirectVideoSource` | a path ending in `.mp4`/`.webm`/`.mov`/`.mkv`/`.m4v`/`.avi`/`.flv`/`.ogv`/`.ts` | VIDEO | `[video]` |
| `StreamSource` | an `.m3u8` (HLS) or `.mpd` (DASH) manifest | VIDEO | `[video]` |
| `YouTubeSource` | a `youtube.com`/`youtu.be`/Shorts/embed/live link | VIDEO | `[youtube]` |
| `AudioFileSource` | a path ending in `.mp3`/`.wav`/`.ogg`/`.oga`/`.flac`/`.m4a`/`.aac`/`.opus`/`.weba`/`.wma`/`.aiff`/`.aif` | AUDIO | `[audio]` |

`MediaSources` is the registry: it holds the ordered list of sources and exposes
the lookups everyone else uses — `find`, `kindOf`, `isImage`, `isVideo`, `isAudio`,
`isSupported` and `labelFor`. Because every caller (the chat handlers, the overlay's
click routing, the labels) goes through these lookups, teaching the mod is one new class plus one line in the registry** — nothing in the chat, GUI or
playback code changes. The image, video and audio kinds are kept **disjoint** across
all sources, so a single link is only ever claimed by one feature (a `.gif` is an
image; a `.mp4` is a video; a `.mp3` is audio — and audio-only siblings like `.weba`/
`.oga`/`.m4a` stay audio while `.webm`/`.ogv`/`.m4v` stay video). `Urls` is a small
package-private helper for the shared path/host parsing.

External mods can also register sources through the public API: either by calling
`MediaPlayerAPI.registerSource()` at any time, or by listening for
`MediaSourceRegistrationEvent` on the mod event bus during initialization. Both
paths append to the same `REGISTERED` list. The `MediaSources.REGISTERED` list is
now a mutable `ArrayList` (was `List.of`), and `MediaSources.register()` is the
public entry point.

## Source layout

| File | Role |
|---|---|
| `LiasMediaPlayer.java` | `@Mod(dist = Dist.CLIENT)` entry point. Holds the mod id (`liasmediaplayer`) and the shared logger, kicks off the background tool download, and fires `MediaSourceRegistrationEvent` during `FMLClientSetupEvent` so addons can register custom sources. Event handlers are discovered separately via `@EventBusSubscriber`. |
| **`api/`** | |
| `LiasMediaPlayerApi.java` | `@Mod("liasmediaplayerapi", dist = Dist.CLIENT)` — the API mod entry point, shown as a separate entry in the Mods menu. No logic of its own. |
| `MediaPlayerAPI.java` | The public façade. Static methods for source registration, playback control, volume, media queries, and playlist access. Delegates to internal classes. |
| `MediaSource.java` | The public extension interface: `matches` / `kind` / `label`. Other mods implement this to teach the player about new link formats. |
| `MediaKind.java` | Public enum: `IMAGE`, `VIDEO`, `AUDIO`. |
| `PlaybackState.java` | Public enum: `LOADING`, `PLAYING`, `PAUSED`, `ENDED`, `FAILED`. |
| `event/MediaSourceRegistrationEvent.java` | Mod-bus event fired during `FMLClientSetupEvent`. Addons listen to register custom `MediaSource`s. |
| `event/PlaybackEvent.java` | Game-bus event fired on playback state changes (STARTED, PAUSED, RESUMED, SEEKED, ENDED, FAILED, STOPPED). Enables sync addons. |
| **`source/`** | |
| `MediaKind.java` | The three disjoint media kinds: `IMAGE`, `VIDEO` and `AUDIO`. |
| `MediaSource.java` | The extension interface: `matches` / `kind` / `label` for one recognizable link shape. |
| `MediaSources.java` | The registry of all sources and the single place the rest of the mod asks "what is this link?" (`find`/`kindOf`/`isImage`/`isVideo`/`isAudio`/`labelFor`). |
| `ImageFileSource.java` · `TenorSource.java` | The two `IMAGE` sources (direct image files; Tenor share pages). `TenorSource.isTenorPage` is reused by the image download path. |
| `DirectVideoSource.java` · `StreamSource.java` · `YouTubeSource.java` | The three `VIDEO` sources. `YouTubeSource.isYouTube` is reused by the playback engines for their YouTube-specific paths. |
| `AudioFileSource.java` | The `AUDIO` source: a direct audio file (`AudioFileSource.isAudioFile`). |
| `Urls.java` | Package-private URL path/host parsing shared by the sources. |
| **`media/`** | |
| `Volume.java` | The single, shared playback level (0..1) used by both engines, plus the dB-gain math that applies it to a `SourceDataLine` (master-volume-scaled). |
| `MediaUrlResolver.java` | Turns a chat link into something ffmpeg can open (direct/streams pass through; YouTube resolves via `yt-dlp -g`). Shared by both engines. |
| `MediaTitleCache.java` | Resolves and caches a human-readable title per URL (YouTube oEmbed, or the file name) for the queue/playlist panels. Shared by both engines. |
| **`chat/`** | |
| `ChatLinkRewriter.java` | The shared chat-rewrite engine: walks a message component-by-component (preserving inherited styles) and replaces each URL claimed by a `LinkRewrite` rule with its label. Both handlers reuse this, so the walk lives in exactly one place. |
| `ImageChatHandler.java` | Subscribes to chat-received / logout events. Supplies the image rule (gold `[picture]`/`[gif]` label; registers the URL with `ImagePreviewCache`) and disposes the image side on disconnect. |
| `VideoChatHandler.java` | Subscribes to chat-received / logout events. Supplies the video rule (aqua underlined `[video]`/`[youtube]` label) and disposes the video side on disconnect. |
| `AudioChatHandler.java` | Subscribes to chat-received / logout events. Supplies the audio rule (green underlined `[audio]` label) and disposes the audio side on disconnect. |
| **`gui/`** | |
| `MediaWindow.java` | Shared base for the on-screen windows. Owns the box geometry, the corner buttons (open-in-browser link, hide, close), the bottom-right resize grip, the move/resize/zoom gestures, and the global **z-order**. Declares the subclass contract, including a polymorphic `close()` and an `anchorGroup()` so the overlay never needs to know a window's concrete type. |
| `MediaWindowOverlay.java` | Single coordinator that renders and routes input for *all* windows (images + videos) as one z-ordered stack. Owns the chat-screen render pass, the HUD overlay pass, mouse handling (click-to-front, click-on-link to open/queue via `MediaSources`), the "reveal hidden videos" button, and the auto-advance/auto-close of finished videos. |
| `ImageWindow.java` | A pinned image/GIF preview drawn as a movable + resizable window (extends `MediaWindow`). Owns no textures of its own — it draws the current frame from `ImagePreviewCache`. |
| `ImageWindowManager.java` | Registry of pinned image windows keyed by URL; shows/closes them and caps how many are alive (6). Closing is just a map removal (textures live in the cache). |
| `ImageHoverPreview.java` | Draws the floating image/GIF preview shown when hovering an image label in chat (loading/failed/loaded states). Invoked by the overlay after the pinned windows so it always sits on top. |
| `VideoWindow.java` | The on-screen player UI (extends `MediaWindow`): the video image, a control bar (play/pause, next, queue, speaker/volume pop-up, seek bar + time) and the per-window **play queue** (a shared `PlayQueue`) with a reorderable playlist panel. |
| `VideoPlayerManager.java` | Registry of active video windows. Default behaviour is to **queue** a link into the front-most player; an independent window is only created on demand (shift-click) or when none exists. Caps the number alive (4) and disposes everything on disconnect. |
| `AudioWindow.java` | The compact audio **bar** (extends `MediaWindow`): a music note + the track name, and a control row (play/pause, previous, next, speaker, seek + time). No picture; backed by an `AudioPlayer`, a shared `PlayQueue` and a short play history for "previous". |
| `AudioPlayerManager.java` | Registry of active audio bars. Same queue-into-front-most default as video, plus `playAll(urls, shuffle)` to start a whole playlist and the transport helpers the keybinds call (`togglePauseFrontMost`/`nextFrontMost`/`previousFrontMost`). |
| `PlayQueue.java` | The ordered URL queue model (append/jump/remove/reorder) shared by `VideoWindow` and `AudioWindow`, so the queue mechanics live in one place. |
| `Glyphs.java` | Shared pixel-art control glyphs (play/pause, next, previous, speaker, note) and a text-ellipsis helper, drawn with plain rectangles (no textures). |
| `PlaylistScreen.java` | The playlist manager screen: a list of saved playlists on the left (select / create), the selected playlist's entries on the right (rename, add a link, remove, play in order, play shuffled, delete). Persists via `PlaylistStore`. |
| **`image/`** | |
| `ImagePreviewCache.java` | Bounded, lazy cache of downloaded previews keyed by URL. Downloads/decodes on a background IO pool, uploads textures on the main thread, evicts the oldest entry past 100 (mirroring vanilla chat history). Resolves Tenor pages (via `TenorSource.isTenorPage` + `TenorResolver`) before downloading. |
| `GifDecoder.java` | Decodes animated GIFs into a sequence of fully composited frames with per-frame delays, with caps on frame count and total pixels to bound VRAM. Also exposes `toNativeImage` helpers used by the image and thumbnail caches. |
| `TenorResolver.java` | Turns a `tenor.com/view/...` share page into a direct, downloadable GIF URL by scraping the page markup. (Recognizing a Tenor link is `TenorSource`'s job; this class only resolves one.) |
| **`video/`** | |
| `VideoPlayer.java` | The decode/playback engine. Drives a pair of background `ffmpeg` processes (one video, one audio) through `FFmpegCli`, buffers decoded frames ahead of the clock, plays synced PCM audio through a `SourceDataLine`, and exposes play/pause/seek; the render thread uploads the current frame to a `DynamicTexture`. Resolves URLs via `media.MediaUrlResolver` and uses the shared `media.Volume`. |
| `VideoThumbnailCache.java` | Builds and caches a small still image for each queued video (the YouTube thumbnail, or the first decoded frame for direct files) so the queue panel can show what each entry is. |
| **`audio/`** | |
| `AudioPlayer.java` | The sound-only playback engine — the audio counterpart of `VideoPlayer`. Probes the stream, opens a `SourceDataLine`, and runs a control thread (resolve/probe/launch/seek) plus a per-session **pump thread** that blocking-writes PCM to the line. Reuses `FFmpegCli`, `media.MediaUrlResolver` and `media.Volume`; YouTube links play as sound only (ffmpeg opens the resolved stream with `-vn`). |
| **`playlist/`** | |
| `Playlist.java` | A named, ordered list of media URLs (its fields are the JSON schema). |
| `PlaylistStore.java` | Loads/saves the playlists to `<gamedir>/liasmediaplayer/playlists.json` (Gson), lazily on first access and after every change. |
| **`input/`** | |
| `ModKeybinds.java` | The four configurable key bindings (play/pause, next, previous, open playlists), unbound by default, registered on the mod bus via `RegisterKeyMappingsEvent` under a "Lia's Media Player" category. |
| `KeybindHandler.java` | Polls the bindings each client tick (`consumeClick`) and drives the front-most audio bar / opens `PlaylistScreen`. |
| **`tools/`** | |
| `FFmpegCli.java` | Thin wrapper around the `ffmpeg`/`ffprobe` binaries. Probes stream metadata (via `ffprobe` JSON, parsed with Gson) and starts ffmpeg processes that pipe raw `rgba` video and `s16le` PCM audio to stdout. |
| `MediaBinaries.java` | Locates — and, if missing, downloads — the external `yt-dlp`, `ffmpeg` and `ffprobe` tools. Shared plumbing for finding binaries, fetching the official releases, and unpacking them into the game folder. |

Resources:

- `src/main/resources/META-INF/neoforge.mods.toml` — mod metadata. Declares two
  `[[mods]]` entries (`liasmediaplayer` and `liasmediaplayerapi`) and only
  `neoforge` and `minecraft` as required dependencies. The `mod_id`, `mod_name`,
  `mod_version`, etc. are expanded from `gradle.properties` at build time.
  `@EventBusSubscriber` handlers are discovered by annotation scanning regardless of
  which sub-package they live in, so moving them between packages needs no config
  change.

## How a link becomes media

Three `@EventBusSubscriber` chat handlers rewrite incoming chat (all through one
shared rewriter), and a single overlay coordinator does all the drawing and input.

### 1. Rewrite incoming chat

`ImageChatHandler`, `VideoChatHandler` and `AudioChatHandler` subscribe to
`ClientChatReceivedEvent.System` / `.Player`. Each handler hands the message to the
shared `ChatLinkRewriter` together with a small `LinkRewrite` rule describing what
it claims and how it styles a match. The rewriter walks the message
component-by-component (preserving inherited styles); for every URL matching
`https?://\S+` it asks the rule whether it `matches`, and if so replaces the URL
with the rule's `label` carrying the rule's `style`:

- **Images** (`MediaSources.isImage`): a direct image file or a Tenor share page.
  Replaced by a gold `[gif]` (Tenor) or `[picture]` label, and the URL is
  registered with `ImagePreviewCache.track` for lazy loading.
- **Videos** (`MediaSources.isVideo`): a direct video file, an HLS/DASH manifest,
  or a YouTube link. Replaced by an aqua, underlined `[video]` / `[youtube]` label.
- **Audio** (`MediaSources.isAudio`): a direct audio file. Replaced by a green,
  underlined `[audio]` label.

Every label carries an `OPEN_URL` click event pointing at the original URL — that is
how the overlay finds the URL again under the cursor. The image, video and audio
sources are intentionally **disjoint**, so the three handlers compose on the same
chat message without fighting over a link. Messages with no supported link are left
untouched.

### 2. Render & input — the shared window stack (`MediaWindowOverlay`)

All on-screen windows — pinned `ImageWindow`s and `VideoWindow`s — live in one
stack ordered by `MediaWindow.zOrder()`:

- **On the chat screen** (`ScreenEvent.Render.Post` over a `ChatScreen`): every
  visible window is drawn back-to-front, each in its own depth band, with the text
  buffer flushed after each one so a front window fully occludes the one behind it
  (content *and* batched text like the seek time / volume pop-up). Then the
  "reveal hidden videos" button and finally the image hover preview
  (`ImageHoverPreview`) are drawn on top. Each window's default cascade position
  comes from its `anchorGroup()`, so images and videos fan out independently.
- **During gameplay** (`RenderGuiEvent.Post`, no screen open): the same windows are
  drawn on the HUD as **picture only** (no controls), so a clip keeps showing while
  you play.
- **Mouse input** (`ScreenEvent.MouseButtonPressed/Released/Dragged/Scrolled`) is
  tested **top-first**, so only the front-most window under the cursor reacts;
  clicking a window raises it (`bringToFront`), and the close button calls the
  window's polymorphic `close()` (no `instanceof` needed). If no window consumes the
  click but the cursor is over a media link, the overlay asks `MediaSources.kindOf`
  and spawns/queues the right thing:
  - **Image link** → pins it via `ImageWindowManager.show` and brings it to front.
  - **Video link** → **queues** it into the front-most player by default
    (`VideoPlayerManager.enqueue`); **Shift-click** opens a separate, independent
    window (`VideoPlayerManager.open`).
  - **Audio link** → **queues** it into the front-most audio bar by default
    (`AudioPlayerManager.enqueue`); **Shift-click** opens a separate bar
    (`AudioPlayerManager.open`).

The overlay also draws two top-left chat buttons: an always-present **Playlists**
button (opens `PlaylistScreen`) and, below it, a **reveal hidden players** button shown
only while at least one video/audio player is hidden.

### 3. Cleanup

On `ClientPlayerNetworkEvent.LoggingOut`, the image side (`ImageChatHandler`)
disposes pinned windows and clears `ImagePreviewCache`; the video side
(`VideoChatHandler`) disposes all players and clears `VideoThumbnailCache` /
`MediaTitleCache`; the audio side (`AudioChatHandler`) disposes all audio bars.

## Images & GIFs

### Preview cache (`image/ImagePreviewCache`)

A `LinkedHashMap` keyed by URL, capped at **100 entries** (mirrors
`ChatComponent.MAX_CHAT_HISTORY`), evicting and releasing the texture of the oldest
entry when full. Threading rules are strict:

- **IO pool (`Util.ioPool()`):** the HTTP download and image/GIF decode. Tenor
  pages (detected with `TenorSource.isTenorPage`) are resolved to a direct GIF first
  via `TenorResolver`. Hard limits: connect/read timeouts (5 s / 10 s), an **8 MB**
  max image size, and a browser-like `User-Agent` / `Accept: image/*` header. Format
  is sniffed by magic bytes (PNG signature, `GIF87a`/`GIF89a`): GIFs go through
  `GifDecoder`, PNG via `NativeImage`, everything else via `ImageIO` normalized to
  ARGB.
- **Main thread:** texture creation (`DynamicTexture` registered under
  `liasmediaplayer:preview/<n>`), cache mutation, and publishing the result back to
  the `Entry`. If the entry was evicted while the download was in flight, the frames
  are closed and discarded.

Each `Entry` tracks its state (`IDLE`/`LOADING`/`LOADED`/`FAILED`), the per-frame
texture locations, per-frame delays, total duration and image size, and exposes
`currentFrame()` which picks the right GIF frame from the wall clock (and always
returns the single frame for static images).

### Hover preview & pinning

While a `ChatScreen` is open, `ImageHoverPreview.render` (called by the overlay)
reads the `OPEN_URL` style under the cursor and, for an image URL
(`MediaSources.isImage`), calls `ImagePreviewCache.getOrLoad` (which kicks off the
download on first hover):

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

### GIF decoding (`image/GifDecoder`)

A GIF stores each frame as a (possibly partial) patch over the previous canvas plus
a disposal method. `GifDecoder` composites every frame onto a persistent canvas
**once**, on the IO thread, producing ready-to-upload coalesced frames so the render
path never re-decodes. To bound VRAM it caps frames at **256** and total kept pixels
at **24M** (~96 MB RGBA), dropping frames evenly and folding their delays into the
kept ones so timing stays correct. Per-frame delays are normalized: a 0/absent delay
becomes 100 ms (browser-like) and nothing animates faster than 20 ms.

### Tenor resolution (`source/TenorSource` + `image/TenorResolver`)

A Tenor `/view/` link is an HTML page, not an image, so it never matches the
file-extension check. `TenorSource.isTenorPage` recognizes it (host `tenor.com`,
`/view/` in the path, locale prefix allowed); `TenorResolver.resolve` then fetches
the page (capped at 512 KB, browser-like `User-Agent`) and extracts a media id from
its markup, trying in order: the `contentUrl` meta tag (either attribute order),
then any `media*.tenor.com/m/<id>/` URL on the page. Because those `/m/<id>/` URLs
are hot-link protected, it rebuilds the canonical direct-download endpoint
`https://c.tenor.com/<id>/tenor.gif`. Older page layouts fall back to a plain
`og:image` GIF URL. `extractMediaUrl` is package-private for testing.

## Video player

### Recognized links (`source` video sources)

`MediaSources.isVideo` is true for three families, kept disjoint from the image
sources:

- **Direct video files** (`DirectVideoSource`) — path ends in `.mp4`, `.webm`,
  `.mov`, `.mkv`, `.m4v`, `.avi`, `.flv`, `.ogv`, or `.ts`.
- **Adaptive streams** (`StreamSource`) — an `.m3u8` (HLS) or `.mpd` (DASH) manifest.
- **YouTube** (`YouTubeSource`) — `youtube.com/watch`, `/shorts/`, `/embed/`,
  `/live/`, the mobile/music hosts, or a `youtu.be/...` short link.

The chat label is `[youtube]` for YouTube links and `[video]` otherwise.

### URL resolution (`media/MediaUrlResolver`)

Direct files and manifests are handed to ffmpeg unchanged. A YouTube page (via
`YouTubeSource.isYouTube`) is not a media file and there is no reliable pure-Java
extractor, so the resolver shells out to `yt-dlp`
(`yt-dlp -g -f "best[height<=720][acodec!=none][vcodec!=none]/best[height<=720]/best"`,
plus `--no-playlist --quiet --no-warnings`) and takes the first direct URL it prints.
It prefers a single progressive stream that already muxes audio + video. If `yt-dlp`
is missing or times out (25 s), the player fails with a clear message instead of
hanging. This always runs on a background thread.

### External tools (`tools/MediaBinaries`)

FFmpeg is **not embedded** in the jar. Instead the mod manages three external
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

### FFmpeg wrapper (`tools/FFmpegCli`)

A thin wrapper around the binaries — no native libraries on the classpath:

- **Probe** (`probe`): runs `ffprobe -print_format json -show_format -show_streams`
  and parses the JSON with Gson into a `MediaInfo` record (width, height, fps,
  duration, audio sample-rate/channels). HTTP inputs get resilience flags
  (`-reconnect`, `-rw_timeout`, a `User-Agent`); local/other protocols don't.
- **Video** (`openVideo`): `ffmpeg … -vf scale=W:H -pix_fmt rgba -f rawvideo -`
  writes tightly-packed `W*H*4`-byte frames to stdout, already scaled. It is
  **deliberately not** paced with `-re`: the decoder is free to read ahead and build
  a buffer, and pacing happens on the consumer side from the audio master clock (see
  [Decoding & playback](#decoding--playback-videovideoplayer)). This is what lets the
  picture absorb network jitter instead of drifting behind the sound.
- **Audio** (`openAudio`): `ffmpeg … -f s16le -acodec pcm_s16le -ar … -ac … -` writes
  signed 16-bit little-endian PCM, ready to hand straight to a `SourceDataLine`.
- **Single frame** (`grabRawFrame`): grabs one scaled `rgba` frame at a timestamp
  (used by the thumbnail cache).

Both seek (`-ss`) and end-of-stream are handled by the caller. stderr is discarded;
a real failure surfaces as an early stdout EOF (the URL was already validated by the
probe). Everything here runs on background threads.

### Decoding & playback (`video/VideoPlayer`)

Each player owns:

- a **decode thread** that resolves the URL, probes it, computes a target size that
  fits within **854×480** (never upscaled, even dimensions), launches the ffmpeg
  video (and, if present, audio) session, then reads `rgba` frames in order. Each
  frame is timestamped from the frame index × frame duration, converted to the
  `abgr` ints `NativeImage` expects, and pushed onto a bounded queue (capacity
  **64**). The decode thread **blocks (back-pressure) while the queue is full**, so
  ffmpeg reads ahead and keeps the queue full — a **jitter buffer** that absorbs an
  uneven or slow connection instead of letting the picture freeze. (Blocking the
  decode thread is safe: audio runs on its own thread and process.) The wait stays
  responsive to pause/seek/dispose. It also handles pause and seek, and parks in
  `ENDED` at end-of-stream until sought or disposed.
- an **audio thread** per session that reads PCM from the audio process and
  blocking-writes it to the `SourceDataLine` (whose blocking write paces playback);
  a stopped line back-pressures into a clean pause.
- the **render/main thread** path: `prepareFrame()` advances to the queued frame
  whose timestamp is ≤ the playback clock and uploads it to a reused
  `DynamicTexture` (`liasmediaplayer:video/<n>`). Textures and GL are touched only
  here.

**Clock & sync.** When the video has audio, the line's `getMicrosecondPosition()` is
the master clock so the picture follows the sound; otherwise a wall clock that only
advances while playing is used. Video frames are shown once their timestamp is ≤ the
clock; late frames are skipped, and the jitter buffer keeps enough decoded frames
ahead of the clock that ordinary network unevenness never starves the picture.

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

### Window, controls & queue (`gui/VideoWindow`)

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
showing each entry's thumbnail and title. Each row's title comes from
`media.MediaTitleCache` (the resolved YouTube video name, or the file name for direct
links) and is ellipsis-truncated to the row width so it never spills past the panel;
the compact "mini" panel next to a small player shows thumbnails only. The panel
matches the player's height and
**scrolls** when there are more entries than fit, with a scrollbar on the right
gutter; rows can be **clicked to jump**, **reordered** (up/down arrows) or
**removed** (×); the mouse wheel scrolls the panel.

**Volume wheel.** With the cursor over the window (and the panel closed), the plain
mouse wheel changes the volume in 10% steps; `Ctrl`+wheel always zooms the window.

### Queue thumbnails (`video/VideoThumbnailCache`)

A bounded cache (64 entries) keyed by URL. For YouTube links it downloads the
predictable `i.ytimg.com/vi/<id>/…jpg` thumbnail (no yt-dlp needed); for direct
files/streams it opens the media with ffmpeg just long enough to grab the first
decoded frame (seeking a touch in to avoid a black intro frame). Loading happens on
the IO pool; the `DynamicTexture` (`liasmediaplayer:videothumb/<n>`) is created back
on the main thread. Each `Thumb` tracks `IDLE`/`LOADING`/`LOADED`/`FAILED`.

## Audio player

### Recognized links and the chat label

`MediaSources.isAudio` is true for a single family, disjoint from the image and video
sources: a **direct audio file** (`AudioFileSource` — a path ending in `.mp3`, `.wav`,
`.ogg`, `.oga`, `.flac`, `.m4a`, `.aac`, `.opus`, `.weba`, `.wma`, `.aiff` or `.aif`).
The chat label is a green, underlined `[audio]`. (YouTube links stay `VIDEO` in chat —
their click opens the video player; YouTube only becomes audio-only when it is added to
a **playlist**, see below.)

### Playback engine (`audio/AudioPlayer`)

`AudioPlayer` is the sound-only sibling of `VideoPlayer`. It deliberately reuses the
heavy machinery rather than copying it: `media.MediaUrlResolver` resolves the URL
(YouTube via `yt-dlp`), `FFmpegCli.openAudio` pipes `s16le` PCM (opening the input with
`-vn`, so a YouTube stream plays as sound only), and `media.Volume` holds the shared
level and the dB-gain math.

It runs two threads, mirroring the proven video model:

- a **control thread** resolves and probes the URL, opens a `SourceDataLine`, launches
  the first ffmpeg session, then parks on a gate until a **seek**, **end-of-stream** or
  **dispose** needs handling. A seek relaunches the ffmpeg session from the new position
  (the same recovery path the video player uses), and — like the video player — a long
  pause (> 3 s) relaunches on resume so an idle network stream that was dropped recovers
  cleanly.
- a **pump thread** (one per session) reads PCM and blocking-writes it to the line,
  which paces playback. Stopping the line back-pressures the write into a clean pause and
  freezes the master clock; on end-of-stream the pump flags the control thread, which
  drains the line and parks the player in `ENDED`.

The line's `getMicrosecondPosition()` is the master clock (a stopped line freezes it, so
pausing needs no separate wall-clock bookkeeping). `durationMicros`/`progress` drive the
seek bar; a live stream reports `LIVE`.

### Window, controls & queue (`gui/AudioWindow`)

The audio bar is a `MediaWindow` (anchor group 2, so audio bars cascade independently of
images and videos), anchored bottom-right and stacked upward. Its content row is just a
music-note glyph and the **track name** (from `media.MediaTitleCache`), so on the HUD —
where windows draw "picture only" — it stays a tidy bar with the name. Its control row
carries play/pause, **previous**, **next**, a speaker/mute toggle and a seek bar with an
elapsed `/` total read-out (a `+N` suffix shows how many tracks are queued). The mouse
wheel over the bar changes the (shared) volume.

The bar owns a shared `PlayQueue` (the same model the video window uses) plus a short
**history** list: `advance()` plays the next queued URL (pushing the current one onto the
history) and `previous()` re-queues the current URL at the front and replays the last
history entry. `AudioPlayerManager` is the registry — queue-into-front-most by default,
shift-click for a separate bar, `playAll(urls, shuffle)` to start a whole playlist, plus
the transport helpers the keybinds call. When a track ends with nothing queued, the
overlay's tick closes the bar (exactly as it does for finished videos).

## Playlists (`playlist/`, `gui/PlaylistScreen`)

A `Playlist` is a **name** plus an ordered list of media **URLs** (direct audio files or
YouTube links). `PlaylistStore` persists every change to
`<gamedir>/liasmediaplayer/playlists.json` with Gson, loaded lazily on first access — so
playlists survive between sessions. The GUI never touches the file directly; it calls the
store, which saves immediately.

`PlaylistScreen` (opened from the chat **Playlists** button or its keybind) is a plain
vanilla `Screen`: the left column lists saved playlists (click to select; an edit box +
`+` button creates one), and the right column edits the selected playlist — rename it,
paste a link to **add** an entry, remove entries, and **Play** (in order) or **Shuffle**
(randomised once, up front). Play hands the URLs to `AudioPlayerManager.playAll`, which
opens a fresh bar playing the first track with the rest queued behind it. Entry names in
the list come from the shared `MediaTitleCache` (real YouTube titles, or file names).

## Keybinds (`input/`)

`ModKeybinds` declares four `KeyMapping`s — play/pause, next, previous, open playlists —
under a "Lia's Media Player" category, registered on the **mod** event bus via
`RegisterKeyMappingsEvent`. They are **unbound by default** (so they can never clash with
a vanilla or other-mod key out of the box; the player assigns them in *Options →
Controls*). `KeybindHandler` polls them each client tick with `consumeClick()` and drives
the front-most audio bar (or opens `PlaylistScreen`); an unbound binding simply never
fires. A small `assets/liasmediaplayer/lang/{en_us,fr_fr}.json` provides the readable
names.

## Windows, move & resize (`gui/MediaWindow`)

The shared base for the image, video and audio windows owns:

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
their `anchorGroup()` and how to `close()`, and any control bar. `ImageWindow`
centers itself (anchor group 0) and has no control bar; `VideoWindow` anchors
bottom-right (anchor group 1), reserves an 18 px control bar and adds a hide button;
`AudioWindow` is a compact bar anchored bottom-right (anchor group 2) with a 16 px
control bar and a hide button. Each group cascades independently, so images, videos and
audio bars fan out without landing on top of one another.

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

- **Adding a media source.** This is the main extension point. Write a new
  `MediaSource` in the `source` package (implement `matches`/`kind`/`label`) and add
  it to the `MediaSources.REGISTERED` list. The chat handlers, the overlay's click
  routing and the labels all flow through the registry, so nothing else needs to
  change. Keep the `IMAGE`, `VIDEO` and `AUDIO` sources **disjoint** so they compose on
  the same message. If an engine needs to single out the new source (as both engines do
  for YouTube), expose a small `static` predicate on it.
  External mods add sources through the API: `MediaPlayerAPI.registerSource()` or
  the `MediaSourceRegistrationEvent`. See `API-DOCUMENTATION.md` for the developer
  guide.
- **Shared volume.** There is one level for everything in `media.Volume`. Both
  `VideoPlayer` and `AudioPlayer` read/write it and apply it via `Volume.apply`; don't
  reintroduce a per-engine volume field.
- **Audio vs. video engines.** They are siblings under the shared `media` layer
  (`MediaUrlResolver`, `MediaTitleCache`, `Volume`) and must not depend on each other.
  Put anything they both need in `media`, not in one engine. The audio engine is the
  simpler one (no frame queue / texture); its seek/pause model mirrors the video player's.
- **Shared queue.** `gui/PlayQueue` is the one queue model for both player windows; the
  video window adds a reorderable panel on top of it, the audio bar uses it plus a short
  history for "previous".
- **Playlists.** `PlaylistStore` is the only thing that touches `playlists.json`; mutate
  a `Playlist` then call `PlaylistStore.save()`. The JSON schema is the `Playlist` field
  names (`name`, `urls`) — keep them stable or migrate.
- **Keybinds.** Add a binding by declaring a `KeyMapping` in `ModKeybinds`, registering
  it in `onRegister`, handling it in `KeybindHandler`, and adding its name to the lang
  files. New bindings should stay unbound by default (`InputConstants.UNKNOWN`) to avoid
  clashes.
- **GUI & Internationalization.** Any new UI text elements must be internationalized using `Component.translatable()` and added to all supported language files (e.g., `assets/liasmediaplayer/lang/en_us.json` and `fr_fr.json`). Do not use hardcoded `Component.literal()` strings for UI text.
- **Tenor scraping.** Recognizing a Tenor link lives in `TenorSource`; turning it
  into a GIF lives in `image/TenorResolver`. If Tenor changes its markup, update the
  patterns in `TenorResolver`; `extractMediaUrl` is unit-test-friendly.
- **Queue titles.** `media.MediaTitleCache` resolves YouTube titles through the public
  `youtube.com/oembed` JSON endpoint. If that endpoint changes its shape, update the
  parse in `fetchYouTubeTitle`; a failed lookup falls back to the generic label, so
  the queue/playlist still renders.
- **Playback buffering & sync.** Video is **not** paced with ffmpeg's `-re`; the
  decode thread applies back-pressure and the audio line is the master clock, so the
  picture follows the sound and a `FRAME_QUEUE_CAPACITY`-deep jitter buffer absorbs an
  uneven connection. If you reintroduce `-re`, the picture can drift behind the audio
  after a stall (it can no longer read ahead to catch up).
- **Threading split (images).** All cache/texture access stays on the render/main
  thread; only downloading and decoding run on the IO pool. Keep that split when
  modifying `ImagePreviewCache`, `VideoThumbnailCache` and `media.MediaTitleCache`.
- **Threading split (video/audio).** Only the player's own background threads touch the
  ffmpeg processes and the audio line; only the render thread touches the
  `DynamicTexture` and OpenGL. `FFmpegCli`/`media.MediaUrlResolver`/`MediaBinaries` calls
  must never run on the render thread (they spawn processes and block).
- **External tools.** If ffmpeg/yt-dlp download endpoints or archive layouts change,
  update the URLs and the unpack logic in `MediaBinaries`. If YouTube changes
  formats, updating `yt-dlp` is usually enough (delete the copy in
  `liasmediaplayer/bin/` to force a fresh download).
- **Windows & z-order.** A new on-screen element is a `MediaWindow` subclass in the
  `gui` package: implement the content/anchor/`close()`/`anchorGroup()` contract and
  the overlay handles stacking, input routing and HUD drawing for it automatically.
- **Tuning.** Frame upload converts pixels one-by-one on the main thread; lower
  `MAX_WIDTH`/`MAX_HEIGHT` in `VideoPlayer` if large videos cause hitching. The video
  jitter buffer is `VideoPlayer.FRAME_QUEUE_CAPACITY` (64 decoded frames, ~2 s at
  30 fps): raise it for a deeper cushion on slow/uneven connections at the cost of
  RAM, lower it on memory-constrained machines. Window caps live in
  `ImageWindowManager` (6), `VideoPlayerManager` (4) and `AudioPlayerManager` (4).
