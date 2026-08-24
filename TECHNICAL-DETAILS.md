# <!-- mod_name -->Lia's Media Player<!-- /mod_name --> — <!-- mod_id -->`liasmediaplayer`<!-- /mod_id -->

A **client-side** mod for **NeoForge and Fabric** on Minecraft **1.21.1 through 26.2** (primary target
**<!-- minecraft_version -->1.21.1<!-- /minecraft_version -->**; see
[Supported versions](#supported-versions)). It
improves how image, GIF,
**video** and **audio** links that appear in the in-game chat are displayed: every
supported URL is rewritten into a compact, clickable label. Hovering an image/GIF
label renders a live preview of the picture (animated GIFs included) above the chat;
clicking it pins the image as a movable, resizable window. Clicking a video label
opens a fully-featured **in-game video player** (with sound, a seek bar and a play
queue); clicking an audio label opens a compact **audio bar** with its own queue. A
**playlist manager** (a chat button or a keybind) lets you save named playlists of
audio/YouTube links — a whole YouTube playlist can be imported into one — and play them
in order, shuffled or looping, and a set of **configurable keybinds** drives the active
audio player.

- **Mod id:** <!-- mod_id -->`liasmediaplayer`<!-- /mod_id --> · **Group:** <!-- mod_group_id -->
  `com.lia.mediaplayer`<!-- /mod_group_id --> · **Version:** <!-- mod_version -->`1.5.0`<!-- /mod_version -->
- **Primary target:** NeoForge <!-- neo_version -->`21.1.230`<!-- /neo_version --> for Minecraft <!-- minecraft_version -->
  `1.21.1`<!-- /minecraft_version --> — see [Supported versions](#supported-versions)
- **Side:** **client-only** (`Dist.CLIENT` / `"environment": "client"`) — it has no
  effect on a server and is not required by anyone else on the server.
- **Loaders:** NeoForge and Fabric, from one source tree. Only the
  [`platform`](#the-loader-seam-platform) package knows which one it is running on.
- **Dependencies:** Minecraft + the loader, plus **Fabric API** on Fabric (that loader's
  event surface) and optionally ModMenu. There are **no bundled native libraries** and
  **no mixins**: video playback shells out to the external `ffmpeg`/`ffprobe` and
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

| Package    | Responsibility                                                                                                                                                     | Depends on                                                         |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `api`      | **Public API for other mods.** Interfaces, enums, `IMediaPlayerAPI`, `LiasMediaPlayerApi`, and its extension points. This package is the only thing external mods should import — and, since API 2.0.0, it names no mod loader at all. | (Minecraft only)                                                   |
| `config`   | **Configuration.** Stores mod options (resolutions, caps) via JSON and persists them to disk. Provides a hub screen for navigating to sub-menus. | (Minecraft only)                                                   |
| `source`   | **What is this link?** URL classification and chat labels. The extension point.                                                                                    | (Minecraft only)                                                   |
| `image`    | Image/GIF download, decode and texture cache.                                                                                                                      | `source`, `config`                                                 |
| `media`    | Cross-cutting playback helpers shared by the two engines: the single shared **volume**, the **URL resolver** (incl. yt-dlp) and the **title cache**.               | `source`, `tools`                                                  |
| `video`    | Video playback engine (decode, audio, thumbnails).                                                                                                                 | `source`, `tools`, `image`, `media`, `config`                      |
| `audio`    | Audio-only playback engine (probe, PCM pump, clock, seek).                                                                                                         | `source`, `tools`, `media`                                         |
| `tools`    | Locating/downloading and invoking the external `ffmpeg`/`ffprobe`/`yt-dlp` binaries.                                                                               | (root)                                                             |
| `playlist` | Saved named playlists and their JSON persistence.                                                                                                                  | (Minecraft only)                                                   |
| `gui`      | Everything drawn on screen: the window base, the overlay coordinator, the image/video/audio windows, their registries, the hover preview, playlists and config hub. | `source`, `image`, `video`, `audio`, `media`, `playlist`, `config` |
| `input`    | The configurable keybinds and the handler that drives the active audio player.                                                                                     | `gui`                                                              |
| `command`  | Registers client commands (like `/show`) to launch media directly.                                                                                                 | `api`                                                              |
| `chat`     | Rewriting links into labels, as plain functions on `Component`.                                                                                                    | `source`, `image`, `video`, `audio`, `gui`                         |
| `platform` | **The loader seam.** `ClientHooks` is the mod's whole catalogue of client hooks in vanilla types; `platform/neoforge` and `platform/fabric` subscribe to their loader's events and forward to it. The only package that imports a loader. | `chat`, `gui`, `input`, `command`, `api`, *(root)*                 |
| *(root)*   | `LiasMediaPlayer` — loader-neutral startup: builds the context, publishes the API singleton, starts the tool download, and applies addon-supplied sources.          | `tools`, `api`, `gui`                                              |

The two playback engines (`video`, `audio`) are siblings that share their common
machinery through the lower `media` layer rather than depending on each other, so the
dependency graph stays acyclic. In particular the **volume is a single shared value**
(`media.Volume`) used by both, so one level controls everything and carries over when a
window swaps tracks.

The root `LiasMediaPlayer` class holds the shared `MODID`/`LOGGER` constants used
across every package, builds the composition root, and kicks off the background tool
download. It carries no loader annotation: `platform/neoforge/NeoForgeMod` and
`platform/fabric/FabricBridge` are the two entry points that call into it.

Nothing depends on `platform`, so adding it above everything keeps the graph acyclic —
and nothing *below* it may import `net.neoforged` or `net.fabricmc`.

### Source classification: the extension point (`source`)

Adding a new kind of media link is the most common way the mod grows, so it is the
one thing made trivially extensible. A `MediaSource` answers three questions about a
URL — does it `matches(...)`, what `kind()` is it (`IMAGE` or `VIDEO`), and what chat
`label(...)` should it show — and nothing else. The built-in sources are:

| Source              | Recognizes                                                                                               | Kind  | Label       |
|---------------------|----------------------------------------------------------------------------------------------------------|-------|-------------|
| `ImageFileSource`   | a path ending in `.png`/`.jpg`/`.jpeg`/`.gif`/`.bmp`                                                     | IMAGE | `[picture]` |
| `TenorSource`       | a `tenor.com/view/...` share page (locale prefix allowed)                                                | IMAGE | `[gif]`     |
| `DirectVideoSource` | a path ending in `.mp4`/`.webm`/`.mov`/`.mkv`/`.m4v`/`.avi`/`.flv`/`.ogv`/`.ts`                          | VIDEO | `[video]`   |
| `StreamSource`      | an `.m3u8` (HLS) or `.mpd` (DASH) manifest                                                               | VIDEO | `[video]`   |
| `YouTubeSource`     | a `youtube.com`/`youtu.be`/Shorts/embed/live link                                                        | VIDEO | `[youtube]` |
| `TwitchSource`      | a `twitch.tv` stream or VOD link                                                                         | VIDEO | `[twitch]`  |
| `AudioFileSource`   | a path ending in `.mp3`/`.wav`/`.ogg`/`.oga`/`.flac`/`.m4a`/`.aac`/`.opus`/`.weba`/`.wma`/`.aiff`/`.aif` | AUDIO | `[audio]`   |

`MediaSources` is the registry: it holds the ordered list of sources and exposes
the lookups everyone else uses — `find`, `kindOf`, `isImage`, `isVideo`, `isAudio`,
`isSupported` and `labelFor`. Because every caller (the chat handlers, the overlay's
click routing, the labels) goes through these lookups, **teaching the mod is one new class plus one line in the registry
** — nothing in the chat, GUI or
playback code changes. The image, video and audio kinds are kept **disjoint** across
all sources, so a single link is only ever claimed by one feature (a `.gif` is an
image; a `.mp4` is a video; a `.mp3` is audio — and audio-only siblings like `.weba`/
`.oga`/`.m4a` stay audio while `.webm`/`.ogv`/`.m4v` stay video). `Urls` is a small
package-private helper for the shared path/host parsing.

External mods can also register sources through the public API: either by calling
`LiasMediaPlayerApi.getInstance().registerSource()` at any time, or by registering a
`MediaSourceProvider` with `LiasMediaPlayerApi.registerSourceProvider()` (or, on Fabric,
declaring a `liasmediaplayer:sources` entrypoint). Providers are collected and run once
during client setup by `LiasMediaPlayer.registerExternalSources`, and every path appends
to the same registered list; `MediaSources.register()` is the public entry point.

The provider indirection is what makes the API loader-neutral. It used to be a NeoForge
mod-bus event, and Fabric has no bus to post one on — so the *discovery* mechanism became
the loader's business and the interface an addon implements became the same on both.

## Source layout

| File                                                                                        | Role                                                                                                                                                                                                                                                                                                                                                                                                                          |
|---------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `LiasMediaPlayer.java`                                                                      | Loader-neutral startup. Holds the mod id (`liasmediaplayer`) and the shared logger; `init()` builds `MediaPlayerContext`, publishes it as the API singleton and kicks off the background tool download; `registerExternalSources(...)` runs every addon-supplied `MediaSourceProvider`. Carries no loader annotation — the two bridges in `platform/` call it.                                                                 |
| **`api/`**                                                                                  |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `LiasMediaPlayerApi.java`                                                                   | The API front door: holds the live `IMediaPlayerAPI` singleton and the `MediaSourceProvider` registry addons register with. No loader imports — the NeoForge `@Mod` entry that gives the API its own line in the Mods menu lives in `platform/neoforge/NeoForgeApiMod`.                                                                                                                                                        |
| `IMediaPlayerAPI.java`                                                                      | The public façade interface. Methods for source registration, playback control, volume, media queries, and playlist access. Exposes unique player IDs for fine-grained control of specific windows.                                                                                                                                                                                                                           |
| `MediaSource.java`                                                                          | The public extension interface: `matches` / `kind` / `label`. Other mods implement this to teach the player about new link formats.                                                                                                                                                                                                                                                                                           |
| `MediaKind.java`                                                                            | Public enum: `IMAGE`, `VIDEO`, `AUDIO`.                                                                                                                                                                                                                                                                                                                                                                                       |
| `PlaybackState.java`                                                                        | Public enum: `LOADING`, `PLAYING`, `PAUSED`, `ENDED`, `FAILED`.                                                                                                                                                                                                                                                                                                                                                               |
| `MediaSourceProvider.java`                                                                  | The extension point addons implement to contribute `MediaSource`s. Registered with `LiasMediaPlayerApi.registerSourceProvider` on either loader, or through the `liasmediaplayer:sources` entrypoint on Fabric.                                                                                                                                                                                                                |
| `event/MediaSourceRegistrationEvent.java`                                                   | Plain object handed to every `MediaSourceProvider` during client setup, collecting the sources they register. (API 1.x: a NeoForge mod-bus event.)                                                                                                                                                                                                                                                                            |
| `event/PlaybackEvent.java`                                                                  | Describes a playback state change (STARTED, PAUSED, RESUMED, SEEKED, ENDED, FAILED, STOPPED). Enables sync addons. (API 1.x: a NeoForge game-bus event.)                                                                                                                                                                                                                                                                      |
| `event/PlaybackEvents.java` · `event/PlaybackListener.java`                                 | The dispatcher for `PlaybackEvent` and the interface it calls. The API owns this because Fabric has no global bus to borrow; a listener that throws is logged and swallowed rather than taking down the player that posted.                                                                                                                                                                                                    |
| `config/ConfigOption.java`                                                                  | Base class for an extensible configuration option. Subclasses like `IntSliderOption`, `StepSliderOption`, `EnumOption` and `BooleanOption` are provided.                                                                                                         |
| `config/EnumOption.java`                                                                    | A `ConfigOption` implementation for enum values, displayed as a button that cycles through the available options.                                                                                                                                                                                                                                                                                                              |
| `config/BooleanOption.java`                                                                 | A `ConfigOption` implementation for a plain on/off setting, displayed as a button reading `Label: ON` / `Label: OFF`. The two states reuse vanilla's own `options.on` / `options.off` keys, so an addon declaring one translates only its label.                                                                                                                                                                               |
| **`config/` (internal)**                                                                    |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `ConfigStore.java`                                                                          | Loads/saves mod options to disk, acting as the registry for all `ConfigOption`s. Provides methods to retrieve options by group.                                                                                                                                                                                                                               |
| **`source/`**                                                                               |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `MediaSources.java`                                                                         | The registry of all sources and the single place the rest of the mod asks "what is this link?" (`find`/`kindOf`/`isImage`/`isVideo`/`isAudio`/`labelFor`).                                                                                                                                                                                                                                                                    |
| `ImageFileSource.java` · `TenorSource.java`                                                 | The two `IMAGE` sources (direct image files; Tenor share pages). `TenorSource.isTenorPage` is reused by the image download path.                                                                                                                                                                                                                                                                                              |
| `DirectVideoSource.java` · `StreamSource.java` · `YouTubeSource.java` · `YouTubePlaylistSource.java` · `TwitchSource.java` | The five `VIDEO` sources. `YouTubePlaylistSource` claims a `youtube.com/playlist?list=…` page (expanded into its videos on click, never played as-is) and stays disjoint from `YouTubeSource`, which keeps a `watch?v=…&list=…` link as the single video it opens. `YouTubeSource.isYouTube` and `TwitchSource.isTwitch` are reused by the playback engines for their dedicated resolution paths.                                                                                                                                                                                                                                                                      |
| `AudioFileSource.java`                                                                      | The `AUDIO` source: a direct audio file (`AudioFileSource.isAudioFile`).                                                                                                                                                                                                                                                                                                                                                      |
| `Urls.java`                                                                                 | Package-private URL path/host parsing shared by the sources.                                                                                                                                                                                                                                                                                                                                                                  |
| **`media/`**                                                                                |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `Volume.java`                                                                               | The single, shared playback level (0..1) used by both engines, plus the dB-gain math that applies it to a `SourceDataLine` (master-volume-scaled).                                                                                                                                                                                                                                                                            |
| `MediaUrlResolver.java`                                                                     | Turns a chat link into something ffmpeg can open (direct/streams pass through; YouTube resolves via `yt-dlp -g`). Shared by both engines. Reads output asynchronously with strict timeouts to prevent deadlocks.                                                                                                                                                                                                              |
| `YouTubePlaylistResolver.java`                                                              | Expands a YouTube playlist page into its watch links via `yt-dlp --flat-playlist` (one index request, no per-video work), with the same strict timeout/stderr-drain handling as `MediaUrlResolver`. `loadAsync` runs it on the IO pool and calls back on the main thread. |
| `MediaTitleCache.java`                                                                      | Resolves and caches a human-readable title per URL (YouTube oEmbed, or the file name) for the queue/playlist panels. Shared by both engines.                                                                                                                                                                                                                                                                                  |
| **`chat/`**                                                                                 |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `ChatLinkRewriter.java`                                                                     | The shared chat-rewrite engine: walks a message component-by-component (preserving inherited styles) and replaces each URL claimed by a `LinkRewrite` rule with its label. Both handlers reuse this, so the walk lives in exactly one place.                                                                                                                                                                                  |
| `ImageChatHandler.java`                                                                     | `rewrite(Component)` / `onDisconnect()`. Supplies the image rule (gold `[picture]`/`[gif]` label; registers the URL with `ImagePreviewCache`) and disposes the image side on disconnect. Takes no event object — `platform.ClientHooks` calls it.                                                                                                                                                                              |
| `VideoChatHandler.java`                                                                     | `rewrite(Component)` / `onDisconnect()`. Supplies the video rule (aqua underlined `[video]`/`[youtube]` label) and disposes the video side on disconnect.                                                                                                                                                                                                                                                                     |
| `AudioChatHandler.java`                                                                     | `rewrite(Component)` / `onDisconnect()`. Supplies the audio rule (green underlined `[audio]` label) and disposes the audio side on disconnect.                                                                                                                                                                                                                                                                                |
| **`gui/`**                                                                                  |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `MediaWindow.java`                                                                          | Shared base for the on-screen windows. Owns the box geometry, the chrome (softened box, title bar carrying the media's name, the 1 px edge that marks the front window, the control-bar strip), the corner buttons, resize grip, move/resize/zoom gestures, the open animation and the click flash, and global z-order. Its initial position is determined by the `default_window_position` config option. Each window has a stable ID for API control. Declares the subclass contract, including a polymorphic `close()` and an `anchorGroup()`. |
| `MediaWindowOverlay.java`                                                                   | Single coordinator that renders and routes input for *all* windows (images + videos) as one z-ordered stack: the chat-screen render pass, the HUD overlay pass, mouse handling (click-to-front, click-on-link to open/queue via `MediaSources`), the "reveal hidden videos" button, the "now playing" banner, the fading outline left by a closed window, and the auto-advance/auto-close of finished videos. Every method is a plain function on vanilla types, returning `true` when it consumed input. |
| `ImageWindow.java`                                                                          | A pinned image/GIF preview drawn as a movable + resizable window (extends `MediaWindow`). Owns no textures of its own — it draws the current frame from `ImagePreviewCache`.                                                                                                                                                                                                                                                  |
| `ImageWindowManager.java`                                                                   | Registry of pinned image windows keyed by URL; shows/closes them and caps how many are alive (6). Closing is just a map removal (textures live in the cache).                                                                                                                                                                                                                                                                 |
| `ImageHoverPreview.java`                                                                    | Draws the floating image/GIF preview shown when hovering an image label in chat (loading/failed/loaded states). Invoked by the overlay after the pinned windows so it always sits on top; the loading/failed states are drawn through `Tooltips`.                                                                                                                                                                              |
| `VideoWindow.java`                                                                          | The on-screen player UI (extends `MediaWindow`): the video image, a control bar (play/pause, next, queue, loop, shuffle, speaker/volume pop-up, seek bar + time) and the per-window **play queue** (a shared `PlayQueue`) with a reorderable playlist panel. Shares layout math with `AudioWindow` via `MediaControls` and `Glyphs`.                                                                                                         |
| `VideoPlayerManager.java`                                                                   | Registry of active video windows. Default behaviour is to **queue** a link into the front-most player; an independent window is only created on demand (shift-click) or when none exists. Caps the number alive (4) and disposes everything on disconnect.                                                                                                                                                                    |
| `AudioWindow.java`                                                                          | The compact audio **bar** (extends `MediaWindow`): a music note + the track name, and a control row (play/pause, previous, next, loop, shuffle, speaker, seek + time). No picture; backed by an `AudioPlayer` and a shared `PlayQueue` (which also holds the history behind "previous").                                                                                                                                                                 |
| `AudioPlayerManager.java`                                                                   | Registry of active audio bars. Same queue-into-front-most default as video, plus `playAll(urls, shuffle[, repeat])` to start a whole playlist and the transport helpers the keybinds call (`togglePauseFrontMost`/`nextFrontMost`/`previousFrontMost`).                                                                                                                                                                                 |
| `PlayQueue.java`                                                                            | The ordered URL queue model (append/jump/remove/reorder) shared by `VideoWindow` and `AudioWindow`, so the queue mechanics live in one place. Also owns the play history and the two playback modes that need it: `next(current)`/`previous(current)` apply the `RepeatMode` and the sticky shuffle flag, so the windows only ever swap their player. |
| `RepeatMode.java`                                                                           | `OFF` / `ALL` / `ONE` — what the queue does when a track ends, cycled by the loop button.                                                                                                                                                                                                                                                                                 |
| `Glyphs.java`                                                                               | Every icon the mod draws, in one place: the transport controls (play/pause, next, previous, stop, loop, shuffle, speaker, volume ±, speed), the window controls (close, minimize, external link, queue, fullscreen, pin), and the list controls (arrow, search, trash, drag handle, heart, note) — plus a text-ellipsis helper. All plain rectangles, so no textures and nothing that changes shape between versions.                          |
| `Theme.java`                                                                                | The whole palette, named by role (`WINDOW_BG`, `ROW_HOVER_BG`, `ICON_HOVER`, `BORDER_FOCUSED`, `DANGER`, …), plus `withAlpha` — the one piece of colour arithmetic, used by everything that fades. Every window, panel, list and overlay reads its colours from here instead of declaring its own constants, so the look is defined in one file. |
| `Tooltips.java`                                                                             | The single point of contact with tooltips. `render` draws one *immediately* (the call was renamed at 1.21.6 and again at 26.1, and the deferred replacement is a frame late for a caller running from the screen-render post hook). `request`/`renderPending` let the windows — which are not screen widgets and so have no `setTooltip` — ask for a tooltip while drawing, which `MediaWindowOverlay` then draws above the stack. |
| `Anim.java`                                                                                 | The clock behind every short UI animation: wall-clock progress (`Util.getMillis`, never ticks — the windows are drawn on the HUD too, and a paused world does not tick) plus the ease-out and in-hold-out curves. |
| `Panels.java`                                                                               | The panel shape: a rectangle with 2 px softened corners and the outline that follows it. Five fills, no texture and no per-version API; one corner radius everywhere is what makes the windows, the queue panel, the chips and the banner read as one UI. |
| `NowPlayingBanner.java`                                                                     | The strip announcing a track that no visible window is showing — raised from `playUrl` when the window doing the playing is hidden, drawn over the chat screen and the bare HUD alike. |
| `MediaControls.java`                                                                        | Shared control logic and utilities (time formatting, volume and seek math, volume pop-up and seek-bar rendering — the bar grows and reveals its handle only while it is being pointed at) used by `VideoWindow` and `AudioWindow`. |
| `PlaylistScreen.java`                                                                       | The playlist manager screen: a list of saved playlists on the left (select / create), the selected playlist's entries on the right (rename, add a link or expand a YouTube playlist into tracks, remove, play in order, play shuffled, loop, delete). Persists via `PlaylistStore`.                                                                                                                                                                                          |
| `ConfigScreen.java`                                                                         | The settings screen: registered groups down the left, the selected group's options beside them, and a search box filtering by translated label. One screen rather than two — it replaced the hub-plus-per-group-screen pair, so switching group is a click and never a screen change. |
| `OptionsList.java`                                                                          | The scrolling column of option widgets, one or two per row by `OptionWidth`. Each row carries a reset button (greyed out while the option is already at its default) and each widget the option's description and warning as one tooltip. Overrides `getRowLeft` so the rows sit in their own column beside the group list. |
| **`image/`**                                                                                |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `ImagePreviewCache.java`                                                                    | Bounded, lazy cache of downloaded previews keyed by URL. Downloads/decodes on a background IO pool, uploads textures on the main thread, evicts the oldest entry past 100 (mirroring vanilla chat history) or when the memory limit is reached. Resolves Tenor pages (via `TenorSource.isTenorPage` + `TenorResolver`) before downloading.                                                                                    |
| `GifDecoder.java`                                                                           | Decodes animated GIFs into a sequence of fully composited frames with per-frame delays, with caps on frame count and total pixels to bound VRAM. Also exposes `toNativeImage` helpers used by the image and thumbnail caches.                                                                                                                                                                                                 |
| `TenorResolver.java`                                                                        | Turns a `tenor.com/view/...` share page into a direct, downloadable GIF URL by scraping the page markup. (Recognizing a Tenor link is `TenorSource`'s job; this class only resolves one.)                                                                                                                                                                                                                                     |
| **`video/`**                                                                                |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `VideoPlayer.java`                                                                          | The orchestrator. Exposes play/pause/seek and coordinates playback state, using `FFmpegSession`, `VideoRenderer`, `AudioOutput`, and `PlaybackClock`.                                                                                                                                                                                                                                                                         |
| `VideoFrame.java`                                                                           | Data record holding the off-heap `ByteBuffer` and timestamp for a decoded frame.                                                                                                                                                                                                                                                                                                                                              |
| `VideoRenderer.java`                                                                        | Handles OpenGL `NativeImage` setup, `DynamicTexture` lifecycle, and uploading frames using `LWJGL MemoryUtil` and reflection.                                                                                                                                                                                                                                                                                                 |
| `AudioOutput.java`                                                                          | Manages the Java Sound `SourceDataLine`, audio volume, and the loop pumping PCM data from the process.                                                                                                                                                                                                                                                                                                                        |
| `PlaybackClock.java`                                                                        | Encapsulates the synchronization logic between wall-clock time and the audio line.                                                                                                                                                                                                                                                                                                                                            |
| `FFmpegSession.java`                                                                        | Wraps process execution for ffmpeg video and audio output, managing streams and process cleanup.                                                                                                                                                                                                                                                                                                                              |
| `VideoThumbnailCache.java`                                                                  | Builds and caches a small still image for each queued video (the YouTube thumbnail, or the first decoded frame for direct files) so the queue panel can show what each entry is.                                                                                                                                                                                                                                              |
| **`audio/`**                                                                                |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `AudioPlayer.java`                                                                          | The sound-only playback engine — the audio counterpart of `VideoPlayer`. Probes the stream, opens a `SourceDataLine`, and runs a control thread (resolve/probe/launch/seek) plus a per-session **pump thread** that blocking-writes PCM to the line. Reuses `FFmpegCli`, `media.MediaUrlResolver` and `media.Volume`; YouTube links play as sound only (ffmpeg opens the resolved stream with `-vn`).                         |
| **`playlist/`**                                                                             |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `Playlist.java`                                                                             | A named, ordered list of media URLs (its fields are the JSON schema).                                                                                                                                                                                                                                                                                                                                                         |
| `PlaylistStore.java`                                                                        | Loads/saves the playlists to `<gamedir>/liasmediaplayer/playlists.json` (Gson), lazily on first access and after every change, using atomic file replacements to prevent corruption.                                                                                                                                                                                                                                          |
| **`input/`**                                                                                |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `ModKeybinds.java`                                                                          | The four configurable key bindings (play/pause, next, previous, open playlists), unbound by default, under a "Lia's Media Player" category. Declares them and exposes `all()`; registering them with the game is each loader bridge's job.                                                                                                                                                                                    |
| `KeybindHandler.java`                                                                       | Polls the bindings each client tick (`consumeClick`) and drives the front-most audio bar / opens `PlaylistScreen`. Called from `platform.ClientHooks.onClientTick`.                                                                                                                                                                                                                                                           |
| **`tools/`**                                                                                |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `FFmpegCli.java`                                                                            | Thin wrapper around the `ffmpeg`/`ffprobe` binaries. Probes stream metadata (via `ffprobe` JSON, parsed with Gson) and starts ffmpeg processes that pipe raw `rgba` video and `s16le` PCM audio to stdout. A shutdown hook tracks and forcibly kills active processes on game exit to prevent orphaned binaries.                                                                                                              |
| `MediaBinaries.java`                                                                        | Public facade that orchestrates `BinaryLocator` and `BinaryDownloader`. Exposes the resolved paths for `yt-dlp`, `ffmpeg` and `ffprobe`, caches results, and manages the once-per-session download guard. The only class the rest of the mod imports from `tools/` (besides `FFmpegCli`).                                                                                                                                     |
| `BinaryLocator.java`                                                                        | Scans for existing installations of each tool: explicit overrides (JVM property / env var), the mod's managed directory, every directory on `PATH`, and common per-OS install locations (winget, scoop, Chocolatey, Homebrew, pip Scripts, …). Probes bare command names as a last resort. Never downloads anything.                                                                                                          |
| `BinaryDownloader.java`                                                                     | Downloads and installs tools when `BinaryLocator` finds nothing. Handles the two download shapes: a single executable (yt-dlp) and a per-platform archive (ffmpeg bundle). Unpacks `.zip` (JDK) and `.tar.xz` (system `tar`) with zip-slip protection. Uses a shared `HttpClient` and temp-file-then-atomic-move for safe writes.                                                                                             |
| **`platform/`**                                                                             |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `ClientHooks.java`                                                                          | The mod's whole catalogue of client hooks — chat, disconnect, tick, screen init/render, HUD render, and the four mouse hooks — written only in vanilla types. The two bridges call these; nothing below this package sees a loader event.                                                                                                                                                                                      |
| `neoforge/NeoForgeMod.java`                                                                 | `@Mod(dist = Dist.CLIENT)`. Registers the `IConfigScreenFactory` extension point, calls `LiasMediaPlayer.init()`, and collects addon sources during `FMLClientSetupEvent`.                                                                                                                                                                                                                                                    |
| `neoforge/NeoForgeApiMod.java`                                                              | `@Mod("liasmediaplayerapi", dist = Dist.CLIENT)` — the second Mods-menu entry. No logic. Exists so `api/` needs no loader import.                                                                                                                                                                                                                                                                                             |
| `neoforge/NeoForgeBridge.java`                                                              | Every `@SubscribeEvent` the mod has, each unwrapping an event object and calling the matching `ClientHooks` method (`setCanceled(true)` where the hook returns `true`). Also registers the key mappings and the `/show` command.                                                                                                                                                                                               |
| `fabric/FabricBridge.java`                                                                  | `ClientModInitializer`. Subscribes the Fabric API events and forwards to `ClientHooks`. Carries the two things Fabric has no direct equivalent for: the `ALLOW_CHAT` + re-inject path for player chat, and the drag reconstruction (see below).                                                                                                                                                                                |
| `fabric/FabricChatSink.java`                                                                | Adds a rewritten player message to the chat overlay by hand, reproducing vanilla's `GuiMessageTag` with `ChatTrustLevel.evaluate`. Guards `addMessage` → `addPlayerMessage` (26.1).                                                                                                                                                                                                                                            |
| `fabric/FabricHud.java` · `fabric/FabricScreens.java` · `fabric/FabricKeyMappings.java`     | One-guard seams for the Fabric API's own churn: `HudRenderCallback` → `HudElementRegistry`, `Screens.getButtons` → `getWidgets`, `KeyBindingHelper` → `KeyMappingHelper` — all at 26.1.                                                                                                                                                                                                                                       |
| `fabric/ModMenuIntegration.java`                                                            | `ModMenuApi` entrypoint putting `ConfigScreen` behind ModMenu's wrench button — Fabric's stand-in for NeoForge's `IConfigScreenFactory`. Optional: compiled against, never required at runtime.                                                                                                                                                                                                                               |
| **`command/`**                                                                              |                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `ShowCommand.java`                                                                          | Builds the `/show` command tree, **generic in its source type** (`tree(BiConsumer<CommandContext<S>, Component>)`) because NeoForge and Fabric hand out command sources with no common ancestor; each bridge supplies how its source reports a failure. Takes `type` (image/video/audio), `url`, and an optional `newPlayer` boolean. Forwards to `LiasMediaPlayerApi`.                                                         |

Resources:

- `src/main/resources/META-INF/neoforge.mods.toml` — NeoForge metadata. Declares two
  `[[mods]]` entries (`liasmediaplayer` and `liasmediaplayerapi`) and only
  `neoforge` and `minecraft` as required dependencies. `@EventBusSubscriber` handlers
  are discovered by annotation scanning regardless of which sub-package they live in,
  so moving them between packages needs no config change.
- `src/main/resources/fabric.mod.json` — Fabric metadata. Declares the `client`
  entrypoint (`FabricBridge`), the optional `modmenu` one, `provides` for the
  `liasmediaplayerapi` id, and `fabric-api` as a required dependency.

Both files live in the same shared resource directory; each buildscript expands only its
own (`mod_id`, `mod_version`, … from `stonecutter.properties.toml`) and excludes the
other from its jar. No `pack.mcmeta` is shipped — both loaders synthesise one for a mod's
own `assets/`.

## The loader seam (`platform`)

The mod ships for two loaders from one source tree, and exactly one package knows it:
`com.lia.mediaplayer.platform`. Everything below it talks to vanilla Minecraft and to
`ClientHooks`, which is the mod's **complete** list of moments it needs the game to call
it — thirteen of them, in vanilla types:

| Hook | NeoForge | Fabric |
|---|---|---|
| `onChatReceived` (system) | `ClientChatReceivedEvent.System` | `ClientReceiveMessageEvents.MODIFY_GAME` |
| `onChatReceived` (player) | `ClientChatReceivedEvent.Player` | `ALLOW_CHAT` + re-inject — see below |
| `onDisconnect` | `ClientPlayerNetworkEvent.LoggingOut` | `ClientPlayConnectionEvents.DISCONNECT` |
| `onClientTick` | `ClientTickEvent.Post` | `ClientTickEvents.END_CLIENT_TICK` |
| `onScreenInit` | `ScreenEvent.Init.Post` | `ScreenEvents.AFTER_INIT` + `Screens.getWidgets` |
| `onScreenRender` | `ScreenEvent.Render.Post` | `ScreenEvents.afterRender` / `afterExtract` |
| `onHudRender` | `RenderGuiEvent.Post` | `HudRenderCallback` / `HudElementRegistry` |
| `onMousePressed` / `Released` / `Scrolled` | `ScreenEvent.Mouse*.Pre` + `setCanceled` | `ScreenMouseEvents.allow*` + `return false` |
| `onMouseDragged` | `ScreenEvent.MouseDragged.Pre` | reconstructed — see below |
| `onKeyPressed` | `ScreenEvent.KeyPressed.Pre` + `setCanceled` | `ScreenKeyboardEvents.allowKeyPress` + `return false` |

A bridge per loader (`platform/neoforge/NeoForgeBridge`, `platform/fabric/FabricBridge`)
subscribes to its own events and forwards. Both are deliberately dumb: anything resembling
a decision belongs one level down, where the other loader gets it for free. Two thin
bridges were chosen over a cross-loader abstraction layer such as Architectury precisely
because the surface is this small — and because such a layer would be a runtime dependency
the player has to install, which this mod does not have.

Three places absorb a genuine asymmetry between the loaders.

**Player chat cannot be modified through the Fabric API.** `ClientReceiveMessageEvents`
has `MODIFY_GAME` for system messages but deliberately no `MODIFY_CHAT`: rewriting a
signed message would break its signature chain. The documented way round is to cancel
through `ALLOW_CHAT` and add the new message yourself, which is what `FabricChatSink`
does. The one thing the callback withholds is the `GuiMessageTag` — the "not secure" /
"modified" indicator vanilla draws beside the line — but it does not have to be
approximated: `ChatTrustLevel.evaluate` is public and takes exactly what the callback
provides, so the tag is the one vanilla would have attached (evaluated against the
*original* message, as vanilla does). This path is only taken for messages the mod
actually rewrote: `ClientHooks.onChatReceived` returns the **same instance** when nothing
matched, and the bridge leaves those to vanilla untouched. `ClientHooksTest` pins that
identity, because losing it would push every ordinary chat line through re-injection.

**Fabric has no drag event before 1.21.8.** `ScreenMouseEvents.allowMouseDrag` arrived in
1.21.8; dragging is how windows are moved and resized, so it is not optional. Rather than
ship a mixin for the three versions below that, `FabricBridge` reconstructs the drag: it
tracks whether a button is down (from the press/release hooks, which every version has)
and feeds the cursor position from the render hook. This is not a downgrade — vanilla
dispatches drags from the GLFW cursor callback, which the game polls once per frame, so a
real drag event fires at most once per frame either way. The reconstruction has the same
resolution, works identically on all seven versions, and keeps the mod mixin-free.

**The API had no loader-neutral extension point.** `MediaSourceRegistrationEvent` used to
extend NeoForge's `Event` and be posted on the mod bus; Fabric has no bus to post it on.
It is now a plain object handed to `MediaSourceProvider`s, which the loader discovers its
own way — a static registry on `LiasMediaPlayerApi` (both loaders) plus the
`liasmediaplayer:sources` entrypoint (Fabric). `PlaybackEvent` moved the same way, onto
`PlaybackEvents`. That is the breaking half of the API 2.0.0 bump.

Fabric API's own version churn is handled the way `gui/` handles vanilla's: one seam per
concern, each holding a single guard — `FabricHud`, `FabricScreens`, `FabricKeyMappings`,
`FabricChatSink`. Three of the four break at **26.1**, the same threshold as the vanilla
GUI rewrite. The exception is worth remembering: the screen mouse events folded their
loose `(x, y, button)` parameters into a `MouseButtonEvent` record at **1.21.11**, not
26.1 — the one Fabric threshold that does not line up with a vanilla one. The keyboard
events moved to a `KeyEvent` record at the same point and for the same reason, so
`allowKeyPress` carries the second guard at that threshold. NeoForge needs no guard
either time: its event objects kept `getMouseX()` / `getKeyCode()` alongside the new
record.

## How a link becomes media

Three chat rules rewrite incoming chat (all through one shared rewriter), and a single
overlay coordinator does all the drawing and input. Neither knows which loader delivered
the message or the mouse click.

### 1. Rewrite incoming chat

`ClientHooks.onChatReceived` runs the message through `ImageChatHandler`,
`VideoChatHandler` and `AudioChatHandler` in turn. Each hands the message to the
shared `ChatLinkRewriter` together with a small `LinkRewrite` rule describing what
it claims and how it styles a match. The rewriter walks the message
component-by-component (preserving inherited styles); for every URL matching
`https?://\S+` it asks the rule whether it `matches`, and if so replaces the URL
with the rule's `label` carrying the rule's `style`:

- **Images** (`MediaSources.isImage`): a direct image file or a Tenor share page.
  Replaced by a gold `[gif]` (Tenor) or `[picture]` label, and the URL is
  registered with `ImagePreviewCache.track` for lazy loading.
- **Videos** (`MediaSources.isVideo`): a direct video file, an HLS/DASH manifest,
  a YouTube link, or a Twitch stream. Replaced by an aqua, underlined `[video]` / `[youtube]` / `[twitch]` label.
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

- **On the chat screen** (`ClientHooks.onScreenRender` over a `ChatScreen`): every
  visible window is drawn back-to-front, each in its own depth band, with the text
  buffer flushed after each one so a front window fully occludes the one behind it
  (content *and* batched text like the seek time / volume pop-up). Then the
  "reveal hidden videos" button and finally the image hover preview
  (`ImageHoverPreview`) are drawn on top. Each window's default cascade position
  comes from its `anchorGroup()`, so images and videos fan out independently.
- **During gameplay** (`ClientHooks.onHudRender`, no screen open): the same windows are
  drawn on the HUD as **picture only** (no controls), so a clip keeps showing while
  you play.
- **Mouse input** (the four `ClientHooks.onMouse*` hooks) is
  tested **top-first**, so only the front-most window under the cursor reacts;
  each hook returns `true` when it consumed the input, which each bridge translates
  into its loader's way of saying "the screen must not see this";
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

On `ClientHooks.onDisconnect`, the image side (`ImageChatHandler`)
disposes pinned windows and clears `ImagePreviewCache`; the video side
(`VideoChatHandler`) disposes all players and clears `VideoThumbnailCache` /
`MediaTitleCache`;
the audio side (`AudioChatHandler`) disposes all audio bars.

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

The chat label is `[youtube]` for YouTube links, `[youtube playlist]` for a playlist
page and `[video]` otherwise.

### YouTube playlists (`source/YouTubePlaylistSource`, `media/YouTubePlaylistResolver`)

A `youtube.com/playlist?list=…` page is the one recognized link that is **not** a media
item, so it never reaches ffmpeg: clicking it calls `YouTubePlaylistResolver.loadAsync`,
which runs `yt-dlp --flat-playlist --print "%(playlist_title|)s\t%(url)s"` on the IO pool
(one index request — the individual videos are only resolved later, when each plays) and
hands the entries back on the main thread, capped at 500. They are then started with
`VideoPlayerManager.playAll` — or `AudioPlayerManager.playAll` when alt is held, matching
the modifier a single YouTube link uses. Every entry goes through the same `Urls.isHttp`
gate a chat link does. A `watch?v=…&list=…` link stays a single video (that is what the
link itself opens), which keeps the two YouTube sources disjoint.

The same expansion backs the playlist editor's **Add** box and the clipboard import, and
`MediaPlayerContext.playVideo`/`playAudio` route a playlist link through it too, so the
public API cannot hand a playlist page to a player as if it were a stream (it returns
`-1`, since the window does not exist yet).

### URL resolution (`media/MediaUrlResolver`)

Direct files and manifests are handed to ffmpeg unchanged. A YouTube page (via
`YouTubeSource.isYouTube`) is not a media file and there is no reliable pure-Java
extractor, so the resolver shells out to `yt-dlp`
(`yt-dlp -g -f "best[height<=720][acodec!=none][vcodec!=none]/best[height<=720]/best"`,
plus `--no-playlist --quiet --no-warnings`) and takes the first direct URL it prints.
It prefers a single progressive stream that already muxes audio + video. If `yt-dlp`
is missing or times out (25 s), the player fails with a clear message instead of
hanging. This always runs on a background thread.

### External tools (`tools/MediaBinaries`, `BinaryLocator`, `BinaryDownloader`)

FFmpeg is **not embedded** in the jar. Instead, the mod manages three external
command-line tools the same way: `yt-dlp` (YouTube resolution), and `ffmpeg` +
`ffprobe` (decoding/probing). `MediaBinaries.installAllAsync()` runs at launch on a
background daemon thread so the tools are usually ready before the first link is
clicked.

The work is split across three classes with distinct responsibilities:

- **`MediaBinaries`** is the public **facade**. It exposes the resolved paths
  (`ytDlp()`, `ffmpeg()`, `ffprobe()`), manages the per-tool result cache
  (`ConcurrentHashMap`), and enforces the once-per-session download guard. The rest
  of the mod only imports this class (and `FFmpegCli`).
- **`BinaryLocator`** handles **finding existing installations**. For each tool it
  builds an ordered, de-duplicated list of candidate paths by checking:
    1. an explicit override — the `-Dliasmediaplayer.<tool>=<path>` JVM argument, or
       the matching environment variable (`YT_DLP_PATH`/`YTDLP_PATH`, `FFMPEG_PATH`,
       `FFPROBE_PATH`);
    2. a copy this mod previously downloaded into `<gamedir>/liasmediaplayer/bin/`;
    3. every directory on `PATH`;
    4. common per-OS install locations (winget Links, scoop shims, Chocolatey, pip
       `Scripts`, Homebrew, `/usr/local/bin`, `~/.local/bin`, …);
    5. the bare command name (trusting the launcher's `PATH`).
- **`BinaryDownloader`** handles **downloading and installing** when `BinaryLocator`
  finds nothing. It supports two download shapes: a single executable (yt-dlp from
  the project's GitHub releases) and a per-platform archive (ffmpeg from BtbN's
  builds on Windows/Linux, evermeet.cx single-binary zips on macOS). Archives are
  unpacked with the JDK's zip support (or the system `tar` for `.tar.xz`), with
  zip-slip protection. It uses a shared `HttpClient` instance and writes through a
  temporary file with an atomic move so a failed download never leaves a corrupt
  binary.

The download is attempted **at most once per tool per game session**; failures are
logged with every location that was checked, so the usual fix is to add
`-Dliasmediaplayer.ffmpeg=...` (etc.) to the launch arguments.

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

### Decoding & playback (`video` package)

Each player (`VideoPlayer`) coordinates several single-responsibility components:

- **Process management** (`FFmpegSession`): Resolves the URL, probes it, computes a target size that fits within *
  *854×480** (never upscaled), and launches the ffmpeg video and audio processes.
- **Decode thread** (`VideoPlayer`): Reads `rgba` frames in order from the video pipe. Each frame is timestamped,
  wrapped in a `VideoFrame` record with a pooled direct `ByteBuffer`, and pushed onto a bounded queue (capacity **64**).
  The decode thread **blocks (back-pressure) while the queue is full**, so ffmpeg reads ahead and keeps the queue full —
  a **jitter buffer** that absorbs network instability.
- **Audio output** (`AudioOutput`): Runs an audio thread per session that reads PCM from the audio process and
  blocking-writes it to the `SourceDataLine` (whose blocking write paces playback); a stopped line back-pressures into a
  clean pause.
- **Render side** (`VideoRenderer`): The render/main thread path calls `prepareFrame()`, advancing to the queued frame
  whose timestamp is ≤ the playback clock. The direct `ByteBuffer` is copied straight into the `NativeImage` backing
  memory using `LWJGL MemoryUtil` (a zero-copy upload that bypasses Java arrays), and uploaded to a reused
  `DynamicTexture`. Draining the queue is therefore something only *drawing* does — so a **hidden** window would jam its
  own decode thread against a full queue and never reach the end of its track, and a queue in a hidden player would
  never advance. `MediaWindowOverlay.clientTick` calls `VideoPlayer.discardDueFrames()` for every hidden player, which
  drops the frames already due (recycling their buffers) at the clock's own pace, so the pipeline keeps running with
  nothing on screen.
- **Clock & sync** (`PlaybackClock`): When the video has audio, the line's `getMicrosecondPosition()` is the master
  clock so the picture follows the sound; otherwise a wall clock that only advances while playing is used. Video frames
  are shown once their timestamp is ≤ the
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

Two details follow from a seek being a *relaunch* rather than a jump:

- **Resuming always relaunches**, from the paused position. Un-pausing in place does not
  work: while paused nobody reads ffmpeg's pipes, the process blocks against a full one,
  and letting it continue afterwards leaves the picture frozen permanently. This was
  written as a "staleness" check with a half-second threshold, which every real pause
  exceeded — so it always relaunched and the in-place path was never exercised. Raising
  the threshold is what exposed it. `AudioPlayer` is *not* the same: sound recovers from
  an in-place resume, and it keeps its own `STALE_PAUSE_NANOS` (3 s) for the case the
  check was nominally about, a remote source dropping an idle connection.
- **The reported position is the target, not the clock,** for as long as a relaunch takes
  (`pendingSeekMicros`, in both engines). The clock only moves once ffmpeg is up, so a
  seek bar reading it directly jumped to the click, snapped back to where playback still
  was, and landed on the target a second later.
- **The relaunch ends when it delivers, not when it launches.** Launching ffmpeg takes a
  moment; its first frame takes about a second, and that second is the whole of what the
  viewer experiences. `isSeeking()` therefore stays true until the new session's first
  frame is on screen — `pumpLoop`'s first `line.write` on the audio side — rather than
  being cleared at the end of `performSeek`. It is cleared as a backstop when a session
  ends or fails, so a seek that produces nothing cannot leave the window loading forever.
- **Frames carry their session** (`VideoFrame.gen`). Everything the outgoing session had
  already decoded is still queued at timestamps *below* the position being sought to, so
  the ordinary path reads the whole backlog as due and flushes it to the screen as a
  burst of the old scene. While a seek is in flight `prepareFrame` switches to
  `VideoRenderer.showFirstFrameAfter(barrier, …)`, which drops anything from the barrier
  session or older (recycling its buffers — the pool is fixed-size) and holds the current
  picture until a genuinely new frame arrives. `discardDueFrames` does the same for a
  hidden player.
- **The wait is shown, not hidden.** A relaunch holds the last decoded frame on screen
  for about a second, and a held frame is indistinguishable from a crashed player, so
  `VideoWindow` draws a chip with `Glyphs.spinner` (wall-clock driven — the point is that
  no frames are arriving) over it while `isSeeking()`, and `AudioWindow` swaps its note
  glyph for the same spinner. Both cover the ordinary loading and buffering states too.

### Window, controls & queue (`gui/VideoWindow`)

The window is anchored bottom-right by default (so it never covers the left-aligned
chat link), scaled to about a third of the screen. Its control bar carries: a
play/pause button; a **next** button and a **queue** (playlist) button when
something is queued; a **loop** button (always shown — a lone video can repeat too)
and a **shuffle** toggle beside it while a queue exists; a **speaker/mute** button with a vertical **volume slider that
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
carries play/pause, **previous**, **next**, **loop**, **shuffle**, a speaker/mute toggle and a seek bar with an
elapsed `/` total read-out (a `+N` suffix shows how many tracks are queued). The mouse
wheel over the bar changes the (shared) volume.

The bar owns a shared `PlayQueue` (the same model the video window uses), which also
keeps the play **history**: `advance()` plays whatever `PlayQueue.next(current)` returns
and `previous()` re-queues the current URL at the front and replays the last history
entry. `AudioPlayerManager` is the registry — queue-into-front-most by default,
shift-click for a separate bar, `playAll(urls, shuffle[, repeat])` to start a whole
playlist, plus the transport helpers the keybinds call. When a track ends and
`next` has nothing to return, the overlay's tick closes the bar (exactly as it does for
finished videos).

### Looping and shuffle (`gui/PlayQueue`, `gui/RepeatMode`)

Both windows delegate "what plays next" to the queue, so the two modes are implemented
once:

- **`RepeatMode.ONE`** returns the current URL again; the queue is untouched.
- **`RepeatMode.ALL`** refills the queue from the history once the last entry has played
  — the finished track is pushed onto the history *before* the round is recycled, so it
  takes its place in the next round exactly once and no track is ever duplicated.
- **Shuffle is a sticky flag**, not a one-off reorder: turning it on reorders what is
  already queued, and every `ALL` round is **reshuffled** as it starts (rotating away a
  first pick that repeats the track just heard). This is why `playAll` records the flag on
  the window instead of only shuffling the list it is given.

The loop button cycles `OFF → ALL → ONE`, and both toggles are drawn in
`MediaWindow.BTN_ACTIVE` while on, so "active" never reads as "hovered".

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
(randomised, and left on for the bar). Play hands the URLs to `AudioPlayerManager.playAll`,
which opens a fresh bar playing the first track with the rest queued behind it. A **Loop**
toggle beside them decides whether that bar starts in `RepeatMode.ALL`. Entry names in the
list come from the shared `MediaTitleCache` (real YouTube titles, or file names).

Pasting a **YouTube playlist** link into the add box (or onto the clipboard **In** button)
expands it into its videos through `YouTubePlaylistResolver` instead of storing the page
link; the screen counts the in-flight expansions so it can say it is working, and a
clipboard import of a single playlist also takes its name from YouTube.

## Keybinds (`input/`)

`ModKeybinds` declares eleven `KeyMapping`s — play/pause, next, previous, volume up and
down, mute, hide/show all windows, close all windows, open playlists, open the options,
and play the link on the clipboard —
under a "Lia's Media Player" category, and exposes them as `all()`. Registering them with
the game is each loader bridge's job (`RegisterKeyMappingsEvent` on NeoForge,
`KeyBindingHelper`/`KeyMappingHelper` on Fabric), so adding a binding here is all that is
needed for both. 1.21.11 turned the category from a bare translation key into a registered
object; NeoForge wants modded ones declared before the mappings that use them, while
Fabric derives the grouping from the mapping itself and needs no registration.
They are **unbound by default** (so they can never clash with
a vanilla or other-mod key out of the box; the player assigns them in *Options →
Controls*). `KeybindHandler` polls them each client tick with `consumeClick()` and drives
the front-most audio bar (or opens `PlaylistScreen`); an unbound binding simply never
fires. A small `assets/liasmediaplayer/lang/{en_us,fr_fr}.json` provides the readable
names.

"Play from clipboard" reads `Minecraft.keyboardHandler.getClipboard()` and hands it to
`MediaWindowOverlay.play(url, audioOnly, newWindow)` — the same method a click on a chat
link routes through, so `Alt` (sound only) and `Shift` (its own window) mean exactly what
they mean in chat. Volume and mute act on `media.Volume`, the one shared level, rather
than on any particular player.

### Shortcuts over the chat screen (`gui/WindowShortcuts`)

The bindings above are global; a second, fixed set acts on the window stack while a
screen that hosts it is open, reached through `ClientHooks.onKeyPressed`. The table lives
in `WindowShortcuts.actionFor(key, control, shift, typing)`, a pure function so the whole
of it is unit-tested (`WindowShortcutsTest`), with `handle()` as the part that needs a
live window.

| Key | Action |
|---|---|
| `Space` | play / pause |
| `←` / `→` | seek ∓5 s (`Shift`: ∓30 s) |
| `↑` / `↓` | volume ±5% |
| `Ctrl+M` | mute | 
| `Ctrl+L` | cycle loop mode |
| `Ctrl+S` | toggle shuffle |
| `Ctrl+N` / `Ctrl+P` | next / previous track |
| `Ctrl+F` | theatre mode |

The split into two families is the whole design. These keys are pressed over the chat
screen, where the text field has the focus, so a bare letter cannot be claimed — the
first character of someone's message would be eaten. `Space` and the arrows are claimed
only while the chat field is **empty**, which is both the "not typing yet" state and the
state where none of them does anything to the field anyway; everything else sits behind
`Ctrl`, which the field only binds for `A`/`C`/`X`/`V`. `Escape` is deliberately left
alone. `gui/ChatInput` answers "is a message part-written?" by finding the `EditBox` in
`Screen.children()` — `ChatScreen.input` is `protected`, and the field is registered into
that list on every version. Whether `Ctrl` is held is asked of `gui/Keys`, not of the
event's modifier bits, so the shortcuts are `Cmd`-based on macOS like the rest of the mod.

Targets are chosen by capability, not simply by z-order: a transport key goes to the
front-most window that `hasTransport()`, so a pinned image over a playing video does not
swallow the space bar, and `Ctrl+F` goes to the front-most window that
`supportsTheater()`.

## Remembered window state (`gui/WindowStateStore`)

`<gamedir>/liasmediaplayer/windows.json` keeps where the windows were left — position,
size, and each player's queue panel, loop mode and shuffle. Same shape as
`PlaylistStore`: lazily loaded, written through a temp file and an atomic move, never
thrown from.

State is kept **per kind** (`image` / `video` / `audio`) rather than per URL, because what
is worth restoring is how someone arranges their screen, not the clip that happened to be
playing — and it bounds the file at three objects forever. Two details are load-bearing:

- The size is stored as the content's **width in pixels**, not the scale factor windows
  work in. A scale is relative to the source's own resolution, so restoring 0.5 from a
  1080p video onto a 360p one would give a quarter of the arranged box.
- That width is only converted back into a scale once `sourceSizeKnown()` is true. A video
  window exists before its player has decoded a frame and reports a 320×180 placeholder
  until then, so the width waits in `pendingWidth` rather than being divided by a
  stand-in.

`MediaWindowOverlay.clientTick()` collects one state per kind (the front-most window of
each wins) and offers it to the store, which rewrites the file only when the value it
holds actually changes — so a quiet tick costs nothing. Nothing is offered mid-drag, or
in theatre mode. On the way back, only a *lone* window of its kind takes the remembered
position: a second video player falls back to the cascade in `computeAnchor` instead of
landing exactly on the first.

## Windows, move & resize (`gui/MediaWindow`)

The shared base for the image, video and audio windows owns:

- **Geometry & chrome** — the box, padding, the top-right corner buttons (link,
  optional hide, close) and the bottom-right resize grip.
- **Move** — drag the window body; the first drag/resize "pins" the position so the
  window stops auto-anchoring and keeps its placement.
- **Resize** — drag the corner grip, or **`Ctrl`+mouse-wheel** to zoom; content is
  scaled between `MIN_CONTENT` (48 px) and 6× and always clamped so the whole box
  (with its control bar) stays on screen and the grip remains grabbable.
- **Magnetism** — while a window is dragged its edges and centre snap to the screen's
  edges and centre line and to those of every other visible window, within 6 px. The
  geometry is one pure function, `gui/Snap.axis(start, length, guides, threshold)`, run
  once per axis with the guides `MediaWindowOverlay` collects; three lines of the moving
  box are candidates for each guide (leading edge, trailing edge, centre), which is what
  makes one function do "flush against", "butted up to" and "centred on" without the
  caller saying which it meant. Holding `Shift` turns it off.
- **Theatre mode** — a double-click on the picture, or `Ctrl+F`, fills the screen with
  the window; the geometry it had is put aside and restored on the way out. Nothing is
  recomputed for it: `layout()` asks for `MAX_SCALE` and lets the existing on-screen cap
  cut it down to "as big as fits", and centres the box. The chrome (title bar, corner
  buttons, grip, control bar) disappears after 2 s of a still cursor and returns the
  instant it moves — a clean cut rather than a fade, because the chrome is drawn by a
  dozen `Glyphs` calls that take no alpha, and fading only the strips behind them would
  leave the glyphs floating at full strength. `VideoWindow` folds its queue panel away on
  the way in, since the panel docks beside the window and caps its width. `AudioWindow`
  opts out (`supportsTheater()` is false): a 14 px bar has no picture to enlarge.
  The idle timer is fed from `render()`, which is the only place the cursor position is
  reported on every version and both loaders — there is no mouse-move hook.
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

[Stonecutter](https://stonecutter.kikugie.dev) compiles the single `src/` tree into
fourteen jars — every supported Minecraft version times both mod loaders — with
**ModDevGradle** on the NeoForge side and **Fabric Loom** on the Fabric side. No
Shadow/shading and no bundled natives, so each produced jar is small. From the project
root:

```
./gradlew buildAll                   # builds all 14 targets
./gradlew buildNeoforge / buildFabric  # one loader, every Minecraft version
./gradlew :1.21.1-neoforge:build     # into versions/1.21.1-neoforge/build/libs/
./gradlew :1.21.1-fabric:runClient   # launches a dev client for that target
```

Subprojects are named `<minecraft-version>-<loader>`; the matrix is declared once, in
`settings.gradle.kts`. Loom is applied through `dev.kikugie.loom-back-compat`, which picks
`fabric-loom-remap` for the obfuscated versions and `fabric-loom` for 26+ (which ship
unobfuscated). Both loaders read the same per-version property table and each takes the
keys it needs. `buildAll` spans two Java toolchains *and* two modding toolchains, so it is
slow — prefer a single target while iterating.

### Supported versions

<!-- supported_versions -->
| Minecraft | NeoForge | Fabric API | Java |
|---|---|---|---|
| `1.21.1` *(primary)* | `21.1.230` | `0.116.15+1.21.1` | 21 |
| `1.21.4` | `21.4.157` | `0.119.4+1.21.4` | 21 |
| `1.21.5` | `21.5.98` | `0.128.2+1.21.5` | 21 |
| `1.21.8` | `21.8.54` | `0.136.1+1.21.8` | 21 |
| `1.21.11` | `21.11.45` | `0.141.6+1.21.11` | 21 |
| `26.1.2` | `26.1.2.97` | `0.155.2+26.1.2` | 25 |
| `26.2` | `26.2.0.64` | `0.158.0+26.2` | 25 |
<!-- /supported_versions -->

Generated by `./gradlew updateDocs` from `stonecutter.properties.toml`. Every row is
built for **both** loaders. The Java column is the toolchain that version compiles
against, taken from the Minecraft version manifest — 26.1 is where Mojang moved from
Java 21 to Java 25, so a full `buildAll` needs both installed (or lets Gradle provision
the missing one). The Fabric API column is the version built against; ModMenu is an
optional compile-only dependency, also pinned per version in the same file.

Install by dropping the built jar from `versions/<target>/build/libs/` into the
**client's** `mods/` folder, alongside NeoForge or Fabric Loader for your Minecraft
version from the table above — plus **Fabric API** on Fabric, without which the mod will
not load. It is a client-only mod: do not install it on a server, where it does nothing.

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
  External mods add sources through the API: `MediaPlayerAPI.registerSource()` or a
  `MediaSourceProvider`. See `API-DOCUMENTATION.md` for the developer guide.
- **Shared volume.** There is one level for everything in `media.Volume`. Both
  `VideoPlayer` and `AudioPlayer` read/write it and apply it via `Volume.apply`; don't
  reintroduce a per-engine volume field.
- **Audio vs. video engines.** They are siblings under the shared `media` layer
  (`MediaUrlResolver`, `MediaTitleCache`, `Volume`) and must not depend on each other.
  Put anything they both need in `media`, not in one engine. The audio engine is the
  simpler one (no frame queue / texture); its seek/pause model mirrors the video player's.
- **Shared queue.** `gui/PlayQueue` is the one queue model for both player windows; the
  video window adds a reorderable panel on top of it, the audio bar adds "previous". It
  also owns the history, the `RepeatMode` and the shuffle flag — put any new "what plays
  next" rule there rather than in a window, so both players get it.
- **Playlist pages are not media.** A YouTube playlist link must be expanded by
  `media/YouTubePlaylistResolver` before it reaches a player; nothing may hand it to
  ffmpeg or to `MediaUrlResolver` (which runs with `--no-playlist`).
- **Playlists.** `PlaylistStore` is the only thing that touches `playlists.json`; mutate
  a `Playlist` then call `PlaylistStore.save()`. The JSON schema is the `Playlist` field
  names (`name`, `urls`) — keep them stable or migrate.
- **Keybinds.** Add a binding by declaring a `KeyMapping` in `ModKeybinds`, adding it to
  `ModKeybinds.all()`, handling it in `KeybindHandler`, and adding its name to the lang
  files. Both loader bridges register whatever `all()` returns, so nothing loader-side
  needs touching. New bindings should stay unbound by default (`InputConstants.UNKNOWN`)
  to avoid clashes.
- **Loader-agnostic by default.** Nothing outside `com.lia.mediaplayer.platform` may
  import `net.neoforged` or `net.fabricmc` — that is what lets one source tree serve both
  loaders. When a feature needs a new moment from the game, add a hook to `ClientHooks`
  and wire it into **both** bridges rather than subscribing to a loader event from `gui`,
  `chat` or `input`. A whole file that is one loader's belongs in `platform/<loader>/`,
  which each buildscript excludes from the other's compilation; `//? if fabric` guards are
  for a few lines inside a *shared* file and should stay rare.
- **`ClientHooks.onChatReceived` must return its argument unchanged when nothing
  matched.** The Fabric bridge uses that identity to decide whether to leave a message to
  vanilla or cancel and re-inject it; a defensive copy would send every ordinary chat line
  through re-injection. `ClientHooksTest` pins it.
- **GUI & Internationalization.** Any new UI text elements must be internationalized using `Component.translatable()`
  and added to all supported language files (e.g., `assets/liasmediaplayer/lang/en_us.json` and `fr_fr.json`). Do not
  use hardcoded `Component.literal()` strings for UI text.
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
  update the URLs in `BinaryDownloader` and the unpack logic in the same class.
  Locating logic (PATH scanning, per-OS locations) lives in `BinaryLocator`;
  `MediaBinaries` is the facade that orchestrates both. If YouTube changes
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
