# Lia's Media Player API Documentation

Lia's Media Player exposes a public API that allows other NeoForge mods to seamlessly integrate with its media playback
capabilities.

The project uses a two-mods-in-one-JAR pattern. A single JAR file contains two separate NeoForge mods:

- `liasmediaplayer`: The main client-side mod.
- `liasmediaplayerapi`: The API mod, which provides the interfaces, events, and facade class for interacting with the
  media player.

Both mods appear as separate entries in the Minecraft Mods menu. Other mods should depend **only** on the
`liasmediaplayerapi` mod and import classes strictly from the `com.lia.mediaplayer.api` package.

## Getting Started

To use the API in your NeoForge 1.21.1 mod, add the mod JAR as a `compileOnly` dependency in your `build.gradle` (or via
a Maven repository if published).

In your `src/main/resources/META-INF/neoforge.mods.toml`, declare a required dependency on the API mod:

```toml
[[dependencies.yourmodid]]
    modId="liasmediaplayerapi"
    type="required"
    versionRange="[1.2.0,)"
    ordering="AFTER"
    side="CLIENT"
```

## Core Concepts

All interactions with the API go through the `com.lia.mediaplayer.api.IMediaPlayerAPI` interface, obtained via
`LiasMediaPlayerApi.getInstance()`, or through NeoForge events.

### Thread Safety

- **Media Queries** (`isSupported`, `kindOf`, volume getters/setters) are thread-safe and can be called from any thread.
- **Playback Control** (`playVideo`, `playAudio`, `togglePause`, etc.) must be called from the **main/render thread**.

## Use Cases and Examples

### 1. Registering Custom Media Sources

You can teach the media player how to handle new link formats (e.g., a custom music streaming service) by implementing
the `MediaSource` interface.

The recommended way to register a source is by listening to the `MediaSourceRegistrationEvent` on the **mod event bus**
during initialization:

```java
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import com.lia.mediaplayer.api.event.MediaSourceRegistrationEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("myaddon")
public class MyAddon {
    public MyAddon(IEventBus modBus) {
        modBus.addListener(this::onRegisterSources);
    }

    private void onRegisterSources(MediaSourceRegistrationEvent event) {
        event.register(new MyCustomAudioSource());
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

Alternatively, you can register a source at any time by calling
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

// Pin an image window
long imageId = LiasMediaPlayerApi.getInstance().showImage("https://example.com/image.png");
```

### 3. Listening to Playback Events

The mod fires `PlaybackEvent` on the **NeoForge event bus** (`NeoForge.EVENT_BUS`) whenever playback state changes. This
is extremely useful for addons that want to synchronize video or audio playback across a server.

```java
import com.lia.mediaplayer.api.event.PlaybackEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = "myaddon", bus = EventBusSubscriber.Bus.GAME)
public class SyncListener {

    @SubscribeEvent
    public static void onPlaybackChanged(PlaybackEvent event) {
        if (event.getPlayerKind() == PlaybackEvent.PlayerKind.VIDEO) {
            switch (event.getType()) {
                case STARTED -> System.out.println("Video started: " + event.getUrl());
                case PAUSED -> System.out.println("Video paused at " + event.getPositionMicros() + " us");
                case SEEKED -> System.out.println("Video seeked to " + event.getPositionMicros() + " us");
                case ENDED -> System.out.println("Video finished");
            }
        }
    }
}
```

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
the mod's Options menu (`ConfigScreen`).

First, create a `ConfigOption`. The API provides handy subclasses like `IntSliderOption`:

```java
import com.lia.mediaplayer.api.config.IntSliderOption;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

IntSliderOption myOption = new IntSliderOption(
    "myaddon:custom_limit", // Unique ID
    "My Custom Limit",      // Translation key / display name
    10,                     // Default value
    1,                      // Min value
    100                     // Max value
).withWarning("myaddon.custom_limit.warning"); // Optional red tooltip for sensitive settings

// Register it
LiasMediaPlayerApi.getInstance().registerConfigOption(myOption);
```

Once registered, your option will automatically appear in the Options menu. You can access its current value at any
time:

```java
int currentLimit = myOption.getValue();
```

## Class Reference

| Class                          | Description                                                                                       |
|--------------------------------|---------------------------------------------------------------------------------------------------|
| `LiasMediaPlayerApi`           | The API mod entrypoint. Provides `getInstance()` to retrieve the active `IMediaPlayerAPI`.        |
| `IMediaPlayerAPI`              | The main interface for controlling playback, volume, playlists, and registering sources/configs.  |
| `MediaSource`                  | Interface to implement to define a new recognized link format.                                    |
| `MediaKind`                    | Enum (`IMAGE`, `VIDEO`, `AUDIO`) returned by `MediaSource.kind()`.                                |
| `PlaybackState`                | Enum (`LOADING`, `PLAYING`, `PAUSED`, `ENDED`, `FAILED`) representing current player state.       |
| `MediaSourceRegistrationEvent` | Mod bus event fired during `FMLClientSetupEvent` to collect custom `MediaSource` implementations. |
| `PlaybackEvent`                | Game bus event fired on transport changes (started, paused, seeked, ended, etc.).                 |
| `ConfigOption<T>`              | Base class for an extensible configuration option.                                                |
| `IntSliderOption`              | A `ConfigOption` implementation for integer values controlled via a slider.                       |
