# Lia's Media Player API Documentation

Lia's Media Player exposes a public API that allows other mods — on **NeoForge or Fabric** — to seamlessly integrate
with its media playback capabilities.

The API ships inside the same JAR as the mod, under the id `liasmediaplayerapi`:

- `liasmediaplayer`: The main client-side mod.
- `liasmediaplayerapi`: The API, which provides the interfaces, extension points, and facade class for interacting with
  the media player. On NeoForge it is a second `[[mods]]` entry and appears as its own line in the Mods menu; on Fabric
  it is a `provides` id, resolvable by dependants without a separate entry in the mod list.

Other mods should depend **only** on the `liasmediaplayerapi` id and import classes strictly from the
`com.lia.mediaplayer.api` package.

**Nothing in `com.lia.mediaplayer.api` imports a mod loader.** The same addon code compiles and runs on both

## Getting Started

Add the mod JAR as a `compileOnly` dependency in your build (or via a Maven repository if published), then declare the
dependency in your loader's metadata.

NeoForge — `src/main/resources/META-INF/neoforge.mods.toml`:

```toml
[[dependencies.yourmodid]]
    modId="liasmediaplayerapi"
    type="required"
    versionRange="[3.4.0,)"
    ordering="AFTER"
    side="CLIENT"
```

Fabric — `src/main/resources/fabric.mod.json`:

```json
"depends": {
  "liasmediaplayerapi": "*"
}
```

(The Fabric `provides` id carries the mod's own version, not the API version, so pin it with `"*"` and check
compatibility through `ApiVersion` — see [Version and capabilities](#0-version-and-capabilities), which is the only
reliable way to ask on either loader.)

## Core Concepts

All interactions with the API go through the `com.lia.mediaplayer.api.IMediaPlayerAPI` interface, obtained via
`LiasMediaPlayerApi.getInstance()`, or through the two extension points on `LiasMediaPlayerApi` and `PlaybackEvents`.

### Thread Safety

- **Media Queries** (`isSupported`, `kindOf`, volume getters/setters) are thread-safe and can be called from any thread.
- **Playback Control** (`playVideo`, `playAudio`, `togglePause`, `play(MediaRequest)`, etc.) must be called from the
  **main/render thread**.
- **History** (`getHistory`, `getFavorites`, `isFavorite`, `setFavorite`, `clearHistory`) is thread-safe.
- **Tools** (`MediaTools`) are thread-safe; `probe` completes off the render thread, so hop back yourself before
  touching anything in the game.
- **Handles, queues, surfaces and windows** are **render thread only**, with one exception: a `MediaHandle`'s
  `id()`, `url()`, `kind()` and `isAlive()` are safe from anywhere.

Every method says which it is, in its own javadoc.

### Minecraft types in signatures

`Component`, `GuiGraphics` and `ResourceLocation` appear in this API. Minecraft renames them across versions —
`ResourceLocation` became `Identifier` at 1.21.11, `GuiGraphics` became `GuiGraphicsExtractor` at 26.1 — so those
signatures differ between the mod's build targets. In practice this costs you nothing, because your addon compiles
against one Minecraft version anyway; it is why the texture-shaped part of the API is kept small and concentrated in
`com.lia.mediaplayer.api.render`.

## Use Cases and Examples

### 0. Version and capabilities

Ask before you call. `Capability` names **every planned feature**, including ones that have not shipped yet — a constant
always resolves, so this is safe to compile against an older mod, unlike `Class.forName`.

```java
import com.lia.mediaplayer.api.ApiVersion;
import com.lia.mediaplayer.api.Capability;

if (ApiVersion.supports(Capability.PLACEMENT)) {
    api.play(MediaRequest.of(url).placement(Placement.anchored(Anchor.TOP_RIGHT, 4, 4)));
} else {
    api.playVideo(url);                     // the 2.0 path, still there
}

if (!ApiVersion.supports(Capability.HEADLESS_AUDIO)) {
    LOGGER.info("Needs Lia's Media Player API {} for the radio feature",
            Capability.HEADLESS_AUDIO.since());
}

ApiVersion.asString();                      // "3.0.0"
ApiVersion.atLeast(2, 2);                   // the blunt instrument
```

Know when the API is ready, instead of guessing at setup order:

```java
PlaybackEvents.register(event -> {
    if (event.getType() == PlaybackEvent.Type.LIFECYCLE_READY) {
        // The context is up, the config is loaded, and every addon-supplied source is
        // registered. Anything on the API is now safe to call.
    }
});
```

### 1. Registering Custom Media Sources

You can teach the media player how to handle new link formats (e.g., a custom music streaming service) by implementing
the `MediaSource` interface.

The recommended way is to implement `MediaSourceProvider` and register it from your mod's entry point. This is
identical on both loaders — the provider is called once during client setup, after every mod has been constructed:

```java
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import com.lia.mediaplayer.api.MediaSourceProvider;
import com.lia.mediaplayer.api.event.MediaSourceRegistrationEvent;
import net.minecraft.network.chat.Component;

public class MySources implements MediaSourceProvider {
    @Override
    public void registerSources(MediaSourceRegistrationEvent event) {
        event.register(new MyCustomAudioSource());
    }
}

// NeoForge
@Mod("myaddon")
public class MyAddon {
    public MyAddon(IEventBus modBus) {
        LiasMediaPlayerApi.registerSourceProvider(new MySources());
    }
}

// Fabric
public class MyAddon implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LiasMediaPlayerApi.registerSourceProvider(new MySources());
    }
}

class MyCustomAudioSource implements MediaSource {
    @Override
    public boolean matches(String url) {
        return url.startsWith("https://my-custom-service.com/track/");
    }

    @Override
    public MediaKind kind() {
        return MediaKind.AUDIO;
    }

    @Override
    public Component label(String url) {
        return Component.literal("[my service]").withStyle(style -> style.withColor(0xFF00FF));
    }
}
```

On **Fabric** you may instead declare the provider as a custom entrypoint, which frees you from caring whether your
initializer happens to run before Lia's Media Player's:

```json
"entrypoints": {
  "liasmediaplayer:sources": ["com.example.addon.MySources"]
}
```

Either way, you can also register a source at any time by calling
`LiasMediaPlayerApi.getInstance().registerSource(source)`.

### 2. Triggering Playback

You can programmatically trigger media playback from your mod. These methods return a `long` ID which uniquely
identifies the player window.

```java
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

// Enqueue a video in the front-most video player (or open a new one if none exists)
long videoId = LiasMediaPlayerApi.getInstance().playVideo("https://www.youtube.com/watch?v=...");

// Open a video in a brand-new, independent player window
long newVideoId = LiasMediaPlayerApi.getInstance().playVideoNewWindow("https://www.youtube.com/watch?v=...");

// Enqueue audio in the compact audio bar
long audioId = LiasMediaPlayerApi.getInstance().playAudio("https://example.com/sound.mp3");

// Play a full playlist of audio tracks (starts immediately, queues the rest)
long playlistId = LiasMediaPlayerApi.getInstance().playAudioAll(List.of("url1", "url2", "url3"), true /* shuffle */);

// A YouTube playlist page is expanded first (a background yt-dlp call), then played in
// a fresh window — so playVideo/playAudio accept one, but return -1 instead of an ID
// because the window does not exist yet.
LiasMediaPlayerApi.getInstance().playVideo("https://www.youtube.com/playlist?list=...");

// Pin an image window
long imageId = LiasMediaPlayerApi.getInstance().showImage("https://example.com/image.png");
```

### 3. Listening to Playback Events

The mod dispatches `PlaybackEvent` through `PlaybackEvents` whenever playback state changes. This is extremely useful
for addons that want to synchronize video or audio playback across a server. Registration is the same on both loaders —
there is no bus involved, because Fabric has none.

```java
import com.lia.mediaplayer.api.event.PlaybackEvent;
import com.lia.mediaplayer.api.event.PlaybackEvents;

public final class SyncListener {

    public static void install() {
        PlaybackEvents.register(event -> {
            if (event.getPlayerKind() != PlaybackEvent.PlayerKind.VIDEO) {
                return;
            }
            switch (event.getType()) {
                case STARTED -> System.out.println("Video started: " + event.getUrl());
                case PAUSED -> System.out.println("Video paused at " + event.getPositionMicros() + " us");
                case SEEKED -> System.out.println("Video seeked to " + event.getPositionMicros() + " us");
                case ENDED -> System.out.println("Video finished");
                default -> {
                }
            }
        });
    }
}
```

Listeners are called on the thread that caused the change — usually the render thread, but a decode thread for `ENDED`
and `FAILED`. Do not block in one.

`PlaybackEvents.unregister` exists, and a listener you forget leaks for the whole process: the dispatcher is static and
outlives every world reload. If you only care about one player, prefer `MediaHandle.addListener` — those listeners are
dropped with the handle.

**Since API 2.1.0/2.3.0** an event also carries `getHandle()` — the player it came from, so two windows playing two
things are finally tell-apart-able — and four more types joined the seven:

| Type | Carries | For |
|---|---|---|
| `QUEUE_CHANGED` | the handle; read the list through `getHandle().queue()` | Queue mirrors, "up next" HUDs |
| `METADATA_RESOLVED` | the url, and a duration if one came with it; no handle | Rich presence, now-playing exports |
| `LIFECYCLE_READY` | nothing | The moment the API is safe to call |
| `WORLD_LEFT` | nothing | Everything is closed and every handle you hold is now dead |

`PlayerKind` gained `IMAGE`; a pinned image reports `STARTED` and `STOPPED` and nothing else, having no clock.
`getPlayerKind()` and `getState()` are `null` on the three events that belong to no player, so check `getType()` first.

### 4. Controlling Playback Programmatically

You can act on the front-most active player using the API:

```java
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

// Toggles pause state for the active video
LiasMediaPlayerApi.getInstance().togglePauseVideo();

// Skips to the next track in the audio bar
LiasMediaPlayerApi.getInstance().nextAudio();

// Seeks the video to 50%
LiasMediaPlayerApi.getInstance().seekVideo(0.5);
```

You can also use the `long` ID returned by the playback methods to control a specific player directly, regardless of
whether it is front-most:

```java
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

long playerId = LiasMediaPlayerApi.getInstance().playVideo("https://www.youtube.com/watch?v=...");

// Toggle pause on this specific player
LiasMediaPlayerApi.getInstance().togglePause(playerId);

// Skip to the next track
LiasMediaPlayerApi.getInstance().next(playerId);

// Enqueue another video to this specific player
LiasMediaPlayerApi.getInstance().enqueueTo(playerId, "https://example.com/next.mp4");

// Hide the player window
LiasMediaPlayerApi.getInstance().setVisible(playerId, false);

// Close the player
LiasMediaPlayerApi.getInstance().close(playerId);
```

### 4b. Handles — the read side the `long` ids never had

A `long` id can only be written to: you can act on it, but you cannot read a state, a position, a title or a queue back
out of it, and nothing tells you when the window behind it goes away — which happens on its own, because both players
evict their oldest window once the configured cap is reached. A `MediaHandle` answers all of that.

```java
import com.lia.mediaplayer.api.MediaHandle;

MediaHandle handle = api.getHandle(playerId);           // null once it is gone
MediaHandle front  = api.getFrontMost(MediaKind.AUDIO); // what the "front-most" methods act on
List<MediaHandle> all = api.getHandles();               // back to front; a snapshot

if (handle != null && handle.isAlive()) {
    handle.title();            // resolved name, or the URL; never blocks
    handle.progress();         // 0..1
    handle.seekToFraction(0.5);
    handle.setVisible(false);  // a hidden player keeps playing
    handle.audio().ifPresent(a -> a.setGain(0.4f));   // this sound only; see 7b
}
```

Three of a handle's members are `Optional`, because not every handle has one: `window()` (a headless sound and an
off-screen surface have no geometry), `queue()` (a pinned image is one picture, not a playlist), and `audio()` (an
image makes no noise).

**A dead handle is inert, not fatal.** Once `isAlive()` is `false` — closed, evicted past the window cap, or dropped on
disconnect — every method is a no-op returning a neutral value, so you can hold one across a world change without
guarding each call.

Listen to *one* handle instead of filtering a global listener, and stop worrying about unregistering it: a handle's
listeners are dropped when the handle dies.

```java
handle.addListener(event -> {
    switch (event.getType()) {
        case ENDED -> onTrackFinished(event.getUrl());
        case QUEUE_CHANGED -> refreshMyHud(event.getHandle().queue().orElseThrow());
        default -> { }
    }
});
```

Every `PlaybackEvent` now carries `getHandle()`, so two windows playing two things are finally tell-apart-able in a
global listener too. It is `null` only on `LIFECYCLE_READY`, `WORLD_LEFT` and `METADATA_RESOLVED`, which belong to no
player.

### 4c. Saying where and how big — `MediaRequest`

One entry point covers all three kinds and every option there is. Every default reproduces what a chat click does, so a
bare `play(MediaRequest.of(url))` behaves exactly like one.

```java
import com.lia.mediaplayer.api.MediaRequest;
import com.lia.mediaplayer.api.RepeatMode;
import com.lia.mediaplayer.api.window.*;

MediaHandle handle = api.play(MediaRequest.of("https://example.com/clip.mp4")
        .newWindow(true)                                        // else it queues into the front-most player
        .placement(Placement.anchored(Anchor.TOP_RIGHT, 4, 4))  // "4 px from the top-right corner"
        .sizing(Sizing.fractionOfScreen(0.25))
        .startAt(30_000_000L)                                   // 30 seconds in
        .autoplay(false)                                        // open paused on the first frame
        .repeat(RepeatMode.ALL)
        .title(Component.translatable("myaddon.cutscene.intro"))
        .chrome(WindowChromeOptions.bare())                     // picture only
        .closeWhenEnded(false));
```

`Placement` is `anchored`, `at`, `relative` (fractions of the free space, so it survives a resolution or GUI-scale
change) or `remembered` (the default — wherever the user left it). `Sizing` is `contentWidth`, `scale`, `fitWithin`,
`fractionOfScreen`, `auto` or `theater`. Both are re-resolved on every layout pass, and both are clamped: an
API-supplied coordinate goes through the same "you can always drag it back on screen" clamp a mouse drag does.

`WindowChromeOptions` decides which parts of the furniture exist — `full()`, `bare()` (no title bar, no controls, no
corner buttons, still movable) or `display()` (all of that, **and** clicks pass straight through to whatever is behind
it). Turning a part off removes both the thing drawn and the input that went with it. Note that a `display()` window can
only be closed by the addon that opened it.

Two things worth knowing:

- **Window options only apply when the request opens a window.** A request that queues into the front-most player (the
  default) leaves that window exactly as the user arranged it. Ask for `newWindow(true)` when the geometry is the point.
- **`persistGeometry` is off by default for API-opened windows.** `windows.json` is keyed by window *kind*, so without
  that default an addon parking a player in a corner would overwrite where the user likes their own video window to
  open — every session, invisibly.

`MediaRequest.of` **throws** `IllegalArgumentException` on anything that is not an absolute `http(s)` URL with a host.
That is not pedantry: the string ends up as an argument to a downloaded binary, and an addon handing over a `file:` URL
has a bug worth being told about. `ofAll` filters instead, and throws only if nothing usable is left.

The request is mutable and fluent, which makes it a poor thing to share — `copy()` before reusing a template, and
`withUrls(List)` to apply one to a different set of links.

Afterwards, drive the window through the handle:

```java
handle.window().ifPresent(window -> {
    window.setPlacement(Placement.relative(0.5, 0.9));
    window.setSizing(Sizing.contentWidth(480));
    window.setTheater(true);
    window.setInteractive(false);
    int[] box = { window.x(), window.y(), window.width(), window.height() };
});
```

### 4d. Reading and editing a queue

```java
import com.lia.mediaplayer.api.MediaQueue;
import com.lia.mediaplayer.api.QueueEntry;

handle.queue().ifPresent(queue -> {
    for (QueueEntry entry : queue.entries()) {     // an immutable snapshot of what is waiting
        System.out.println(entry.title().getString() + " — " + entry.url());
    }
    queue.add("https://example.com/next.mp3");
    queue.insert(0, "https://example.com/jump-the-line.mp3");
    queue.move(4, 0);
    queue.remove(2);
    queue.jumpTo(0);
    queue.setRepeat(RepeatMode.ALL);
    queue.setShuffle(true);
});
```

**The track playing now is not in the list.** `entries()` is what is *waiting*; `current()` is what is playing, and it
has no index. That is the mod's own model rather than a simplification of it — a player owns one track and a list of
what comes after, which is why `next()` both advances the player and shortens the queue.

`QueueEntry.durationMicros()` is `-1` for almost every queued entry, because nothing has opened those streams yet;
`hasThumbnail()` is a peek, so reading a queue never starts a download. Use `MediaTools.probe` on the one entry you
actually care about.

### 5. Accessing Playlists

The API provides methods to read and modify the user's saved playlists:

```java
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.IMediaPlayerAPI;

// List all playlists
for (IMediaPlayerAPI.PlaylistInfo info : LiasMediaPlayerApi.getInstance().getPlaylists()) {
    System.out.println("Playlist: " + info.name() + " has " + info.urls().size() + " tracks.");
}

// Create a playlist and add a track
LiasMediaPlayerApi.getInstance().createPlaylist("Server Radio");
LiasMediaPlayerApi.getInstance().addToPlaylist("Server Radio", "https://youtube.com/...");
```

Since API 2.1.0 the rest of the surface is there too:

```java
IMediaPlayerAPI.PlaylistInfo one = api.getPlaylist("Server Radio");   // null if there is no such playlist
api.renamePlaylist("Server Radio", "Lobby Radio");
api.removeFromPlaylist("Lobby Radio", "https://youtube.com/...");
api.reorderPlaylist("Lobby Radio", reorderedUrls);                    // must be a permutation of what it holds

MediaHandle radio = api.playPlaylist("Lobby Radio", true /* shuffle */);
MediaHandle framed = api.playPlaylist("Lobby Radio",                  // …or through a request template
        MediaRequest.of("https://example.com/placeholder.mp3")
                .newWindow(true)
                .repeat(RepeatMode.ALL)
                .chrome(WindowChromeOptions.bare()));
```

Names are how a playlist is addressed here, so two of them may not share one, and `renamePlaylist` returns `false`
rather than creating a duplicate. `reorderPlaylist` reorders and nothing else — it will not add or drop entries, because
that would be a second way to edit a playlist that skips the `http(s)` gate.

### 5b. History and favourites

```java
import com.lia.mediaplayer.api.HistoryItem;

for (HistoryItem item : api.getHistory(20)) {          // newest first; 0 means "all of it"
    System.out.println(item.url() + " " + item.kind() + " " + item.playedAtEpochMillis());
}
api.getFavorites();                                    // what the user kept; never evicted
api.isFavorite(url);
api.setFavorite(url, true);
api.clearHistory();                                    // keeps the favourites, like the button in the UI does
```

All of it is thread-safe. **This is personal data** — a record of what the user watched and listened to. It is
local-only and never leaves the machine on its own, and it is exposed deliberately; an addon that reads it should say
so, and a pack author should be able to see that it does.

### 5c. Probing a link, and the tools

```java
import com.lia.mediaplayer.api.tools.MediaTools;
import com.lia.mediaplayer.api.tools.MediaInfo;

MediaTools.isReady();          // are ffmpeg, ffprobe and yt-dlp all present?
MediaTools.whenReady()         // completes when the first-launch install finishes, either way
        .thenRun(() -> ...);
MediaTools.ytDlpVersion();     // "2025.08.11", or null

MediaTools.probe(url).thenAccept(info -> {
    if (info == null) {
        return;                // not http(s), tools missing, or ffprobe could not read it —
    }                          // an ordinary outcome for a pasted link, so it is not thrown
    int w = info.width();
    long micros = info.durationMicros();
});
```

**The security line, stated plainly:** this facade exposes named operations over a URL and nothing else. It never
exposes a process builder, never accepts caller-supplied ffmpeg arguments, and never accepts a local filesystem path.
The mod downloads and executes three binaries; an API that let an addon pass arbitrary arguments to one of them would be
a remote-code-execution vector wearing a media API's clothes. Every URL-taking method in this API applies the same
`http(s)`-with-a-host gate, in the API layer, not in your code. The trust model for the binaries themselves is
documented on the mod's `tools.BinaryDownloader`.

### 6. Registering Configuration Options

The API provides a way to register custom configuration options that are automatically saved, loaded, and rendered in
the mod's Options menu. The options will be grouped by the `group` parameter in the constructor.

First, create a `ConfigOption`. The API provides handy subclasses like `IntSliderOption`, `StepSliderOption` and `EnumOption`:

```java
import com.lia.mediaplayer.api.config.IntSliderOption;
import com.lia.mediaplayer.api.config.StepSliderOption;
import com.lia.mediaplayer.api.config.EnumOption;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

// Example of a slider for integer values
IntSliderOption myOption = new IntSliderOption(
    "myaddon:custom_limit", // Unique ID
    "myaddon",              // Group
    "My Custom Limit",      // Translation key / display name
    10,                     // Default value
    1,                      // Min value
    100                     // Max value
)
    .withDescription("myaddon.custom_limit.description") // Optional tooltip explaining the option
    .withWarning("myaddon.custom_limit.warning");       // Optional red tooltip for sensitive settings

// Example of a button that cycles through enum values
public enum MyEnum {
    LOW, MEDIUM, HIGH
}

EnumOption<MyEnum> myEnumOption = new EnumOption<>(
    "myaddon:quality",      // Unique ID
    "myaddon",              // Group
    "Quality",              // Translation key / display name
    MyEnum.MEDIUM           // Default value
);


// Register it
LiasMediaPlayerApi.getInstance().registerConfigOption(myOption);
LiasMediaPlayerApi.getInstance().registerConfigOption(myEnumOption);
```

Every option in the menu gets a reset button beside it, which puts it back to the default value the
constructor was given. `resetToDefault()` and `isDefault()` are on `ConfigOption<T>` if an addon
wants to do the same from its own code — they live there rather than at the call site because
`setValue` is typed on `T`, and a caller holding a `ConfigOption<?>` cannot hand its own default
back to it without an unchecked cast.

Once registered, your option will automatically appear in the Options menu under the specified group. You can access its current value at any
time:

```java
int currentLimit = myOption.getValue();
MyEnum currentQuality = myEnumOption.getValue();
```

### 7. Drawing media yourself — surfaces

This is the part that turns the mod from "a chat mod with an API" into a decoder you build with: a cinema screen in the
world, a TV on a block, an animated background in your own `Screen`, a trailer in a menu, album art beside a jukebox.

```java
import com.lia.mediaplayer.api.render.*;

MediaSurface screen = MediaSurfaces.video(url, SurfaceOptions.defaults()
        .withMaxSize(256, 144)      // a TV the size of a block does not need a 480p decode
        .withLoop(true));

// …every frame you draw it, in your BlockEntityRenderer / Screen / HUD callback:
screen.markWanted();
ResourceLocation texture = screen.texture();   // null while loading, on failure, and after the world unloads
if (texture != null) {
    // render it on your quad, with your own RenderType
}

// …when your renderer goes away:
screen.close();
```

Three factories: `MediaSurfaces.image(url)` (still or animated GIF), `MediaSurfaces.video(url, options)`, and
`MediaSurfaces.thumbnail(url, atSeconds)` — a single frame, one ffmpeg launch and then a still picture, which is what
makes it the right thing for a wall of many. **None of them ever returns `null` or throws**: a link the mod cannot play,
a mod that has not started, and a request past the cap all come back as a surface that never becomes ready, because a
block-entity renderer has nowhere sensible to catch an exception.

Four things to get right:

1. **Call `markWanted()` once per frame, and only for surfaces actually in view.** A video surface nobody marks stops
   decoding pictures — its sound keeps playing — and that is the whole of what makes a world full of screens
   affordable. Drawing through `MediaGraphics` (below) does it for you.
2. **Never cache what `texture()` returns.** Ask every frame.
3. **`close()` when your renderer goes away.** Surfaces are reference-counted: two callers asking for the same media
   share one decode, and it is disposed when the last of them lets go. Closing twice is harmless; so is closing after
   the world unloaded.
4. **Two caps apply**, both user-editable: how many surfaces may exist (16), and how many may be *decoding video* at
   once (3) — each of those is an ffmpeg process. Past either, a request is refused and logged rather than queued.

Two surfaces share a decode only when the URL **and the options** match, so keep your `SurfaceOptions` stable rather
than building a fresh one with a different cap each frame.

Everything is dropped when the player leaves the world, whoever is still holding it — a texture that outlives its world
is a leak for the rest of the session. After that `isReady()` is `false`, which is the same branch you already have for
"still loading".

For anything drawn in a GUI, let `MediaGraphics` do the blit. `GuiGraphics.blit` is one of the least stable methods in
the client API — the argument order moved at 1.21.2 and the render-type factory became a blaze3d pipeline at 1.21.6 —
and this is how you reach the mod's single guarded call site instead of carrying a copy of that guard:

```java
MediaGraphics.draw(graphics, surface, x, y, width, height);          // letterboxed, aspect preserved
MediaGraphics.drawStretched(graphics, surface, x, y, width, height); // fills the rect exactly
int[] rect = MediaGraphics.fit(surface, boxX, boxY, boxW, boxH);     // {x, y, w, h}, for laying out around it
```

And inside your own `Screen`, `MediaWidget` is a drop-in `AbstractWidget`:

```java
MediaWidget widget = MediaWidget.video(10, 30, 320, 180, url);
addRenderableWidget(widget);

widget.handle().ifPresent(h -> h.pause());   // transport, if you want buttons of your own

@Override
public void removed() {
    widget.close();                          // you must; a screen that vanishes silently leaves ffmpeg running
    super.removed();
}
```

The widget draws media and nothing else — no controls, no clicks. A screen that wants transport buttons draws its own
and drives them through `handle()`, in its own layout and its own style.

**In-world rendering stays your job.** The API hands you a texture id; you render it on your block or entity with your
own `RenderType`. Two things bite: draw distance (mark only what is in view), and GUI-versus-world texture filtering.

A video surface's sound plays through the mod's single volume unless you place it: `surface.playback()` gives you a
`MediaHandle`, and its `audio()` is the same `AudioControls` everything else has — see the next section.

### 7b. Sound with no window — headless audio, the mixer, and 2.5D placement

A speaker block, an ambience loop, a radio in a vehicle, a cutscene's soundtrack: none of those wants a media bar on
screen. `MediaAudio` plays them with the mod's engine and none of its interface.

```java
import com.lia.mediaplayer.api.audio.*;

MediaHandle radio = MediaAudio.play(url, AudioOptions.defaults()
        .withPlacement(AudioPlacement.world(speakerCentre, 24))   // fades to silence 24 blocks out
        .withChannel(AudioChannel.AMBIENT)
        .withLoop(true)
        .withFade(1000, 1000));                                   // fade in, and fade out on close

// ...when the block is broken:
radio.close();          // rides the gain down over that second, then stops
```

It hands back an ordinary `MediaHandle`, so pause, seek, `state()`, `isAlive()` and per-handle listeners all work the
way they do for a window. Its `window()` and `queue()` are empty — it has neither — and it is `null` when the mod is not
up, the link is not `http(s)`, or the cap is reached.

Three things the API will not do for you:

1. **You own the lifetime.** Nothing on screen can stop a headless sound. A non-looping track retires itself when it
   ends or fails, and everything is dropped when the player leaves the world; anything else is `close()`. An addon that
   never calls it has left an ffmpeg process running.
2. **There is a cap, and it is small** (4 by default, user-editable). Each headless sound is an ffmpeg process and an
   audio line, exactly like a track in the audio bar. One sound for the nearest speaker is a design this supports; one
   sound per speaker block in a world of them is not — past the cap a request is refused and logged, and `play` answers
   `null`.
3. **`pauseWithGame` defaults to `true`.** A speaker in the world should not keep playing over a paused single-player
   game. Set it `false` for music that is meant to carry on.

#### Placing a sound — and what "2.5D" honestly means

`AudioPlacement` has three cases: `world(Vec3, radius)` for a fixed point, `entity(Entity, radius)` for one that
follows something, and `screen()` for "not in the world at all" (the same thing a `null` placement does). It applies to
**any** handle, not just a headless one — a video surface's soundtrack and even a window's track take the same call:

```java
handle.audio().ifPresent(a -> a.setPlacement(AudioPlacement.entity(minecart, 16)));
```

What you get is **distance attenuation** (a linear ramp from full volume at the source to silence at the radius,
recomputed once per client tick from the *camera*, so third person and spectator behave like the rest of the game's
sound) and **stereo panning** from the horizontal angle between where you are looking and where the sound is.

What you do **not** get, and should not design around: no HRTF, no elevation, no occlusion by blocks, no reverb, no
Doppler. A sound directly ahead and a sound directly behind are panned identically — that ambiguity is inherent to
stereo panning, not a bug to work around. Some audio devices expose no pan control at all, and there a placed sound is
attenuated but centred; `AudioControls.pan()` tells you what is actually being applied. Real 3D would mean feeding
Minecraft's own OpenAL engine, which does not take arbitrary streamed PCM — a separate project, deliberately not
attempted. Check `Capability.POSITIONAL_AUDIO` before assuming any of it is there.

An `entity(...)` placement whose entity is removed or unloaded goes **silent** until it comes back, rather than
snapping to 2D full volume in the middle of your head. A `world(...)` placement has no dimension: the mod cannot tell
which world your `Vec3` belongs to, so mute or close it yourself when the player leaves.

#### The gain chain, and which knob to turn

```
effective = masterVolume  x  channelGain(channel)  x  handleGain  x  distanceAttenuation
```

| Factor | Who owns it | How to reach it |
|---|---|---|
| `masterVolume` | **the user** — their slider, their music | `MediaAudio.mixer().setMasterVolume` (or `IMediaPlayerAPI.setVolume`) |
| `channelGain` | shared between addons, for ducking | `MediaAudio.mixer().setChannelGain(channel, g)` |
| `handleGain` | one sound | `handle.audio().get().setGain(g)` / `fadeTo(g, millis)` |
| `distanceAttenuation` | derived from the placement | `setPlacement(...)` |

Turn **your own sound** down with `setGain`. Turn **a category** down with a channel — that is what lets a cutscene
addon quiet the jukebox addon's radio without knowing it exists, and put it back afterwards. Reach for
`setMasterVolume` only when your addon *is* the volume control: it is the user's setting, it is what their music is
playing at, and nothing puts it back for you.

`AudioChannel` is a closed set of five — `MEDIA` (the default), `VOICE`, `AMBIENT`, `UI`, `ADDON` — deliberately, so
two addons that have never heard of each other can still agree on what to duck. Channel gains are **not persisted**:
every channel is back at `1.0` on the next game start, so an addon that dies mid-fade cannot leave someone quiet
forever. They are *not* Minecraft's `SoundSource` categories, and the in-game sliders other than **Master** do not
apply to what this mod plays.

```java
MediaMixer mixer = MediaAudio.mixer();      // never null, even before the mod has loaded
mixer.setChannelGain(AudioChannel.MEDIA, 0.2f);   // duck the radio for a cutscene
// ...afterwards
mixer.setChannelGain(AudioChannel.MEDIA, 1.0f);   // or mixer.resetChannelGains()
```

`fadeTo(target, millis)` is a linear ramp in **client ticks**, not a per-sample envelope: it lands within a tick of
where you asked, and a second call starts from wherever the first had got to, so two fades in quick succession do not
jump. Setting a gain directly cancels a fade in flight. `AudioOptions.fadeOutMillis` is the one that also *stops*
things — it rides the last of a track down, and `close()` honours it before disposing.

`AudioControls` is render-thread only like the rest of a handle, except `MediaAudio.mixer()`, which is thread-safe.

### 8. Teaching the mod to play *your* service

`MediaSource` answers "what is this link?". Two more extension points answer the rest, and both register the same way on
both loaders:

```java
import com.lia.mediaplayer.api.source.*;

// How do I open it? Asked before the mod's own resolution; first non-null answer wins.
LiasMediaPlayerApi.registerResolver(new MediaResolver() {
    @Override public boolean handles(String url) { return url.startsWith("https://my-cdn.example/"); }
    @Override public String resolve(String url) throws IOException {
        return signedStreamUrlFor(url);          // must be an absolute http(s) URL
    }
});

// What is it called? Without this, a custom source shows a raw URL everywhere.
LiasMediaPlayerApi.registerMetadataProvider(new MediaMetadataProvider() {
    @Override public boolean handles(String url) { return url.startsWith("https://my-cdn.example/"); }
    @Override public CompletableFuture<MediaMetadata> fetch(String url) {
        return CompletableFuture.supplyAsync(() -> MediaMetadata.ofTitle(
                Component.literal(lookUpTitle(url))), myExecutor);
    }
});
```

`MediaResolver.resolve` is called off the render thread and may block — that is where the work goes. It must return an
absolute `http(s)` URL; anything else is discarded with a warning, because what you return becomes an argument to a
downloaded binary. Returning `null` falls through to the next resolver and finally to the mod's own path. An
`IOException` stops the resolution and is reported (a signing service being down is a failure to report, not a link to
hand to ffmpeg unsigned); any other exception is logged and falls through.

`MediaMetadataProvider.handles` runs on the render thread and must be cheap; `fetch` is called there too and must not
block in it — do the work on your own executor. A future that completes with `null` or exceptionally simply falls back
to the mod's own guess. When a title lands, the mod posts `METADATA_RESOLVED` with the URL.

### 9. Saying no — interceptors and `PlayOrigin`

Everything above tells you what the mod *did*. `MediaInterceptor` is the other half: a chance to intervene *before*
something happens, which is what a party, moderation or parental-control addon actually needs.

```java
import com.lia.mediaplayer.api.policy.*;

LiasMediaPlayerApi.registerInterceptor(new MediaInterceptor() {
    @Override
    public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
        if (origin == PlayOrigin.API || origin == PlayOrigin.RESTORE) {
            return request;                                   // not our business
        }
        if (!partySettings.allows(request.url())) {
            return null;                                      // cancelled
        }
        return request.copy().sizing(Sizing.fractionOfScreen(0.25));   // or rewritten
    }

    @Override
    public boolean allowChatLink(String url, String sender, MediaKind kind) {
        return !mutedPlayers.contains(sender);
    }

    @Override
    public Component decorateLabel(String url, MediaKind kind, Component label) {
        return Component.empty().append(label).append(Component.literal(" ✔"));
    }
});
```

Three rules, and all three are promises:

- **The first veto wins**, and a rewrite is threaded through the interceptors after it — so two of them compose rather
  than the last one deciding.
- **An interceptor that throws abstains.** It is logged, and the mod carries on. If you want to fail closed, return a
  veto; do not throw.
- **`PlayOrigin` is the point.** `CHAT_CLICK`, `COMMAND`, `KEYBIND`, `PLAYLIST`, `HISTORY`, `API`, `RESTORE`. An addon
  almost always wants to gate what other people put in front of the player, not its own calls.

`beforePlay` runs on the render thread. `allowChatLink` and `decorateLabel` run once per link per incoming message, in
the client's network-to-render handoff — do no I/O in either.

**What this is not.** Nothing here hides a chat message or edits what somebody said. A vetoed link stays in the message,
still says what it says, and is still copyable; it simply is not turned into a label this mod will play. That is the
same line the built-in link filters draw, and it is deliberate.

One limitation worth knowing: the 2.0 `long`-id methods (`playVideo`, `playAudio`, `showImage`, …) take a URL and hand
back an id, so only the **URL** of a rewritten request can be honoured there. A placement or a chrome you also set has
nowhere to go and is not applied. `play(MediaRequest)` is the entry point that honours everything.

Use `LiasMediaPlayerApi.unregisterInterceptor(...)` when your policy goes away — it is the one registry here with a way
out, because a moderation addon turning itself off must be able to stop being asked.

### 10. Your own button on a window, your own keys, your own palette

Three small extension points that share a shape: register once from your entry point, and the mod draws or polls it.

```java
import com.lia.mediaplayer.api.window.*;
import com.lia.mediaplayer.api.input.*;
import com.lia.mediaplayer.api.theme.*;

// A button in every media window's corner row, beside the heart and the copy button.
LiasMediaPlayerApi.registerWindowAction(new WindowAction() {
    @Override public String id()          { return "myaddon:share"; }
    @Override public Component tooltip()  { return Component.translatable("action.myaddon.share"); }
    @Override public ActionIcon icon()    { return ActionIcon.EXTERNAL_LINK; }
    @Override public boolean appliesTo(MediaHandle handle) { return handle.kind() != MediaKind.IMAGE; }
    @Override public void onClick(MediaHandle handle)      { party.share(handle.url()); }
});

// A key binding the user rebinds in Options → Controls, polled by the mod once a tick.
MediaKeybinds.register(myMapping, minecraft -> party.toggle());

// A fixed shortcut over the chat screen. Ctrl, not a bare letter — the API enforces it.
MediaKeybinds.registerWindowShortcut(InputConstants.KEY_J, MediaKeybinds.MOD_CONTROL,
        frontMost -> { if (frontMost == null) return false; party.share(frontMost.url()); return true; });

// A palette. Partial: name the roles you care about, the rest keep the mod's dark values.
MediaThemes.register(MediaTheme.builder("myaddon:sunset", Component.translatable("theme.myaddon.sunset"))
        .set(ThemeRole.WINDOW_BG, 0xD0221018)
        .set(ThemeRole.FILL, 0xFFFF8A50)
        .set(ThemeRole.BORDER_FOCUSED, 0xFFFF8A50)
        .build());
```

**Window actions.** `ActionIcon` is a closed enum rather than a sprite id: the mod's icons are drawn from primitives, at
the one size the row uses, in whatever colour the hover state calls for — there is no id to hand out, and naming one
from the list is what keeps your button looking like the mod's own in every theme. At most **three** addon buttons are
drawn per window, in registration order, and only on windows whose chrome has buttons at all. `appliesTo` is asked every
frame: make it a field read.

**Key bindings.** `MediaKeybinds.register` has to be called from your mod constructor or client initializer — both
loader bridges collect the mod's binding list at one fixed moment during startup, and a mapping handed over after that
is never given to the game. Declare it **unbound** (`InputConstants.UNKNOWN`), as the mod's own are.

**Window shortcuts.** These act on the front-most player while the chat screen is open, and they are matched *after* the
mod's own table, so you cannot shadow a built-in key. Return `false` when there was nothing to do — swallowing a key
over a text field is how a shortcut becomes a bug report. Registering an unmodified letter or digit throws.

**Themes.** The id is what the user's `config.json` stores, so it has to be stable and namespaced; the mod's own
`dark` / `contrast` / `minecraft` are refused. Colours are `0xAARRGGBB`, and alpha matters — the window and panel
backgrounds are deliberately translucent. A theme whose addon is uninstalled leaves the setting alone and draws the dark
palette meanwhile, so the choice comes back with the addon.

### 11. Watch-together — sync hooks

**This mod ships no protocol and no server side**, and is not going to: it is client-only. What it ships is the pair of
hooks an addon that owns its own network channel needs.

```java
import com.lia.mediaplayer.api.sync.*;

// Sending: every local transport action, for you to broadcast.
MediaSync.setAdapter(action -> myChannel.sendToParty(new PlaybackPacket(action)));

// Receiving:
void onPacket(PlaybackPacket packet) {
    MediaSync.control().apply(new SyncAction(myLocalHandleId, packet.url(),
            packet.type(), packet.positionMicros(), packet.wallClockMillis()));
}
```

`SyncAction` is deliberately flat — no Minecraft types, no handle, nothing that only means something in one process — so
it is the thing you serialize. `wallClockMillis` is what makes the correction possible at the other end:
`projectedPositionMicros()` adds however long the packet spent in flight, so a `PLAY` resumes where the sender *is*, not
where they were when they pressed the key.

**The echo is broken for you.** Actions the mod applies through `SyncControl.apply` do not come back out of your
adapter. Without that, a remote pause would pause locally, be broadcast, be applied by the sender, and the two clients
would ping-pong forever.

Two more things on `SyncControl`:

- **`driftCorrect(handleId, targetMicros, toleranceMicros)`** converges instead of jumping. A video's audio line is the
  master clock and its decoder is back-pressured by a bounded frame queue, so a small correction is made *inside* that
  clock — the picture slides into place over a few ticks and nothing is torn down. Call it once a tick. Past about two
  seconds it gives up and seeks, and an audio track always seeks (its line *is* its position, and skewing that would
  mean resampling). Give audio a wide tolerance — a second is not a bad figure for music.
- **`setLocked(handleId, locked, reason)`** holds the local user off the transport: the control bar, the seek bar, the
  keyboard shortcuts and the key bindings all decline, and the window says why. It governs *hands*, not the player —
  `apply` and an addon holding a `MediaHandle` still work, which is what "the host controls this" actually means. Closing
  and hiding the window stay allowed whatever you set; a viewer who cannot get out of a video files that as a crash.

There is one adapter, not a list. Two mods both claiming to be the sync authority for a session is a bug, and the second
`setAdapter` logs that it replaced the first rather than quietly doubling every packet.

### 12. Formats the mod does not know, and reading a picture back

```java
import com.lia.mediaplayer.api.image.*;

LiasMediaPlayerApi.registerImageDecoder(new ImageDecoder() {
    @Override public boolean supports(byte[] header) { return isWebp(header); }
    @Override public DecodedImage decode(byte[] data) throws IOException {
        WebpFrames frames = MyWebp.read(data);              // off the render thread
        return new DecodedImage(frames.width(), frames.height(), frames.argb(), frames.delaysMs());
    }
});
```

Registered decoders are asked **before** the built-in ones, in registration order, and the first that claims the bytes
wins — so this is the way in for WebP and APNG, and it covers every picture the mod loads: a chat preview, a pinned
image, an image surface. Frames must be **fully composited** `0xAARRGGBB` arrays, one complete picture each; disposal
methods and frame offsets are your problem, not the mod's. `decode` runs on the IO pool: do no GL work. A decoder that
claims a picture and then throws does not fall through — it said the picture was its — but one that answers `null` does.

Reading pixels back is the other half, and it is opt-in:

```java
try (MediaSurface art = MediaSurfaces.image(albumArtUrl, /* keepPixels = */ true)) {
    art.pixels().ifPresent(pixels -> myGui.setAccent(pixels.averageColor()));
}
```

`pixels()` is empty unless the surface was asked for with `keepPixels`, and empty for a video or a thumbnail — keeping
every frame of a video readable would double what a decode costs for a question nobody asks. It hands back a **fresh
copy** each call rather than the native buffer the texture was uploaded from, so call it once and keep what you get.
`keepPixels` is part of the decode-sharing key, like a video's options are. `averageColor()` ignores fully transparent
pixels, so a mostly-transparent logo does not come back as the colour of nothing.

### 13. Playlists as files, a way into your screen, and the counters

```java
import com.lia.mediaplayer.api.diag.*;
import com.lia.mediaplayer.api.screen.*;

// m3u, in both directions.
String text = api.exportM3u("Road trip");                  // null for an unknown playlist
PlaylistInfo imported = api.importM3u("From VLC", clipboardText);   // null if nothing usable

// A button into your own screen, from the playlist and history screens.
LiasMediaPlayerApi.registerScreenTab(new MediaScreenTab() {
    @Override public String id()       { return "myaddon:featured"; }
    @Override public Component title() { return Component.translatable("screen.myaddon.featured"); }
    @Override public Screen open(Screen parent) { return new FeaturedScreen(parent); }
});

// The counters, and the failures.
MediaPlayerStats stats = MediaDiagnostics.stats();
MediaPlayerLog.addSink(entry -> myOverlay.show(entry.message()));
MediaPlayerLog.recent().forEach(entry -> log.info("{} failed: {}", entry.url(), entry.cause()));
```

**m3u.** Import passes every line through the same `http(s)` gate every other way into a playlist uses — an m3u file is
a list of *paths*, and that is exactly the shape that would otherwise hand ffmpeg a `file:` URL. `#EXTINF` titles are
read and discarded: the mod resolves its own, and a stored one goes stale the first time a video is renamed. Export
writes whatever titles the mod has already resolved and probes nothing.

**Screen tabs** are buttons in the library screens' footer rather than a tab strip — those two screens are not a tabbed
pair, and the roadmap says so. Send the player back to the `parent` you were handed when your screen closes, or Escape
drops them into the world from the middle of the library. At most three are shown.

**Diagnostics.** `MediaPlayerStats` is a snapshot: window counts, live and decoding surfaces, cache sizes, whether the
binaries are ready. `MediaPlayerLog` gives you playback failures already classified — the readable line and the hint the
mod's own error panel shows, plus a stable `cause` id to match on and the raw stderr for a bug report. It keeps a short
backlog (32 entries, cleared when the world unloads) because the screen that wants to show a failure is very often
opened *after* it happened. Remember `removeSink`: the dispatcher is static and lives for the process.

## Class Reference

| Class                          | Description                                                                                       |
|--------------------------------|---------------------------------------------------------------------------------------------------|
| `LiasMediaPlayerApi`           | The API front door. `getInstance()` retrieves the active `IMediaPlayerAPI`; `registerSourceProvider()` / `registerResolver()` / `registerMetadataProvider()` are the loader-neutral extension points. |
| `ApiVersion`                   | The version of the API surface, and `supports(Capability)` — the reliable way to ask "does this build have X". |
| `Capability`                   | Every feature the API has or plans to have, each carrying the version it lands in. Constants are never removed, so one always resolves. |
| `MediaHandle`                  | A live media instance: state, position, title, transport, per-handle listeners, `audio()`, and `isAlive()`. Returned by `play(MediaRequest)` and `getHandle(long)`. |
| `MediaRequest`                 | Everything you can say about how something should be played, in one fluent object. |
| `MediaQueue` / `QueueEntry`    | What a player will play next, and the edits the queue panel already performs on it. Reached through `MediaHandle.queue()`. |
| `RepeatMode`                   | `OFF` / `ALL` / `ONE`. Moved here from the mod's `gui` package in API 2.3.0. |
| `HistoryItem`                  | One line of the local play history. Personal data — see §5b. |
| `IMediaPlayerAPI`              | The main interface for controlling playback, volume, playlists, and registering sources/configs.  |
| `MediaSource`                  | Interface to implement to define a new recognized link format.                                    |
| `MediaSourceProvider`          | Interface an addon implements to contribute `MediaSource`s. Registered via `LiasMediaPlayerApi` on either loader, or the `liasmediaplayer:sources` entrypoint on Fabric. |
| `MediaKind`                    | Enum (`IMAGE`, `VIDEO`, `AUDIO`) returned by `MediaSource.kind()`.                                |
| `PlaybackState`                | Enum (`LOADING`, `PLAYING`, `PAUSED`, `ENDED`, `FAILED`) representing current player state.       |
| `MediaSourceRegistrationEvent` | Passed to every `MediaSourceProvider` during client setup, to collect custom `MediaSource` implementations. |
| `PlaybackEvent`                | Describes a transport change (started, paused, seeked, ended, etc.).                              |
| `PlaybackEvents`               | The dispatcher for `PlaybackEvent`: `register()` / `unregister()` a `PlaybackListener`.           |
| `PlaybackListener`             | Functional interface receiving `PlaybackEvent`s.                                                  |
| `ConfigOption<T>`              | Base class for an extensible configuration option.                                                |
| `IntSliderOption`              | A `ConfigOption` implementation for integer values controlled via a slider.                       |
| `StepSliderOption<T>`          | A `ConfigOption` implementation for values selected from a predefined list of steps.              |
| `EnumOption<E>`                | A `ConfigOption` implementation for enum values controlled via a button.                          |
| `window.Anchor` / `Placement`  | Where a window sits — anchored, absolute, relative to the screen, or wherever the user left it.   |
| `window.Sizing`                | How big it is — a content width, a scale, a fit, a fraction of the screen, auto, or theatre.      |
| `window.WindowChromeOptions`   | Which parts of the window furniture exist. `full()`, `bare()`, `display()`.                       |
| `window.MediaWindowHandle`     | The window half of a handle: geometry, placement, sizing, theatre, chrome, geometry persistence.  |
| `render.MediaSurface`          | Media decoded into a texture you draw yourself. Reference-counted; `close()` your acquisition.    |
| `render.MediaSurfaces`         | Where a surface comes from: `image`, `video`, `thumbnail`. Never returns `null`, never throws.    |
| `render.SurfaceOptions`        | Resolution cap, loop and autoplay for a video surface. Part of the decode-sharing key.            |
| `render.MediaGraphics`         | Drawing a surface into a rectangle, without re-deriving the mod's `blit` version guards.          |
| `render.MediaWidget`           | A drop-in `AbstractWidget` showing media inside your own `Screen`. Close it when the screen goes. |
| `audio.MediaAudio`             | Sound with no window: `play(url, options)`, and `mixer()`. The front door for everything below.     |
| `audio.AudioOptions`           | How a headless sound starts: gain, loop, start point, channel, placement, fades, pause-with-game.  |
| `audio.AudioPlacement`         | Where a sound is: `world(Vec3, r)`, `entity(Entity, r)`, `screen()`. 2.5D — attenuation and pan.   |
| `audio.AudioChannel`           | `MEDIA` / `VOICE` / `AMBIENT` / `UI` / `ADDON` — the category a sound is ducked by.                |
| `audio.AudioControls`          | One sound's own gain, channel, placement and fade. Reached through `MediaHandle.audio()`.          |
| `audio.MediaMixer`             | The master level and the five channel gains. Thread-safe. Reached through `MediaAudio.mixer()`.    |
| `tools.MediaTools` / `MediaInfo` | Probing a URL and asking after the downloaded binaries. Named operations only — see §5c.        |
| `source.MediaResolver`         | Turns your own links into something ffmpeg can open.                                              |
| `source.MediaMetadataProvider` / `MediaMetadata` | Supplies titles (and durations, thumbnails, authors) for your own links.         |
| `policy.MediaInterceptor`      | Veto or rewrite a play request, veto a chat link, decorate a label. First veto wins. |
| `policy.PlayOrigin`            | Who asked: `CHAT_CLICK`, `COMMAND`, `KEYBIND`, `PLAYLIST`, `HISTORY`, `API`, `RESTORE`. |
| `window.WindowAction` / `ActionIcon` | Your own button in a window's corner row, drawn with one of the mod's icons. Three per window. |
| `input.MediaKeybinds`          | Register a `KeyMapping` the mod collects and polls, or a fixed shortcut over the chat screen. |
| `input.WindowShortcutAction`   | What such a shortcut does. Return `false` when there was nothing to do.            |
| `theme.MediaTheme` / `MediaThemes` / `ThemeRole` | A palette of your own, as the roles you override. Partial by design. |
| `sync.MediaSync`               | The watch-together front door: `setAdapter(...)` to send, `control()` to receive.  |
| `sync.PlaybackSyncAdapter`     | Told about every local transport action, for you to broadcast. One adapter, not a list. |
| `sync.SyncAction`              | One action, flat and primitive — the thing you put on the wire. `projectedPositionMicros()`. |
| `sync.SyncControl`             | `apply`, `setLocked`, `driftCorrect`. Render thread.                              |
| `image.ImageDecoder` / `DecodedImage` | A picture format the mod does not know. Asked before the built-ins; runs on the IO pool. |
| `render.SurfacePixels`         | A copy of a surface's decoded pixels, and `averageColor()`. Opt in with `MediaSurfaces.image(url, true)`. |
| `screen.MediaScreenTab`        | A button into your own screen, from the playlist and history screens.             |
| `diag.MediaDiagnostics` / `MediaPlayerStats` | What the mod is holding right now: windows, surfaces, caches, binaries. |
| `diag.MediaPlayerLog` / `MediaLogEntry` | Playback failures, classified, as they happen and as a short backlog.  |
