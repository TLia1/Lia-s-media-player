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

**Nothing in `com.lia.mediaplayer.api` imports a mod loader.** The same addon code compiles and runs on both — see
[Migrating to API 2.0.0](#migrating-to-api-200) if you wrote against API 1.x.

## Getting Started

Add the mod JAR as a `compileOnly` dependency in your build (or via a Maven repository if published), then declare the
dependency in your loader's metadata.

NeoForge — `src/main/resources/META-INF/neoforge.mods.toml`:

```toml
[[dependencies.yourmodid]]
    modId="liasmediaplayerapi"
    type="required"
    versionRange="[2.0.0,)"
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
compatibility through the API classes you use.)

## Migrating to API 2.0.0

API 2.0.0 removed every NeoForge type from the `com.lia.mediaplayer.api` package so that one addon can target both
loaders. Two things changed, both mechanical:

| API 1.x | API 2.0.0 |
|---|---|
| `MediaSourceRegistrationEvent extends Event implements IModBusEvent`, listened to on the mod bus | Plain object, handed to a `MediaSourceProvider` you register with `LiasMediaPlayerApi.registerSourceProvider(...)` |
| `PlaybackEvent extends Event`, listened to on `NeoForge.EVENT_BUS` | Plain object, dispatched to a `PlaybackListener` you register with `PlaybackEvents.register(...)` |

`MediaSource`, `MediaKind`, `PlaybackState`, `IMediaPlayerAPI` and `ConfigOption` are unchanged.

## Core Concepts

All interactions with the API go through the `com.lia.mediaplayer.api.IMediaPlayerAPI` interface, obtained via
`LiasMediaPlayerApi.getInstance()`, or through the two extension points on `LiasMediaPlayerApi` and `PlaybackEvents`.

### Thread Safety

- **Media Queries** (`isSupported`, `kindOf`, volume getters/setters) are thread-safe and can be called from any thread.
- **Playback Control** (`playVideo`, `playAudio`, `togglePause`, etc.) must be called from the **main/render thread**.

## Use Cases and Examples

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
).withWarning("myaddon.custom_limit.warning"); // Optional red tooltip for sensitive settings

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

Once registered, your option will automatically appear in the Options menu under the specified group. You can access its current value at any
time:

```java
int currentLimit = myOption.getValue();
MyEnum currentQuality = myEnumOption.getValue();
```

## Class Reference

| Class                          | Description                                                                                       |
|--------------------------------|---------------------------------------------------------------------------------------------------|
| `LiasMediaPlayerApi`           | The API front door. `getInstance()` retrieves the active `IMediaPlayerAPI`; `registerSourceProvider()` is the loader-neutral extension point. |
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
