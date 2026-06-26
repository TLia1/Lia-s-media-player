# Lia's Media Player API Documentation

Lia's Media Player exposes a public API that allows other NeoForge mods to seamlessly integrate with its media playback capabilities.

The project uses a two-mods-in-one-JAR pattern. A single JAR file contains two separate NeoForge mods:
- `liasmediaplayer`: The main client-side mod.
- `liasmediaplayerapi`: The API mod, which provides the interfaces, events, and facade class for interacting with the media player.

Both mods appear as separate entries in the Minecraft Mods menu. Other mods should depend **only** on the `liasmediaplayerapi` mod and import classes strictly from the `com.lia.mediaplayer.api` package.

## Getting Started

To use the API in your NeoForge 1.21.1 mod, add the mod JAR as a `compileOnly` dependency in your `build.gradle` (or via a Maven repository if published). 

In your `src/main/resources/META-INF/neoforge.mods.toml`, declare a required dependency on the API mod:

```toml
[[dependencies.yourmodid]]
    modId="liasmediaplayerapi"
    type="required"
    versionRange="[1.0.0,)"
    ordering="AFTER"
    side="CLIENT"
```

## Core Concepts

All interactions with the API go through the `com.lia.mediaplayer.api.MediaPlayerAPI` facade class, or through NeoForge events.

### Thread Safety
- **Media Queries** (`isSupported`, `kindOf`, volume getters/setters) are thread-safe and can be called from any thread.
- **Playback Control** (`playVideo`, `playAudio`, `togglePause`, etc.) must be called from the **main/render thread**.

## Use Cases and Examples

### 1. Registering Custom Media Sources

You can teach the media player how to handle new link formats (e.g., a custom music streaming service) by implementing the `MediaSource` interface. 

The recommended way to register a source is by listening to the `MediaSourceRegistrationEvent` on the **mod event bus** during initialization:

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

Alternatively, you can register a source at any time by calling `MediaPlayerAPI.registerSource(source)`.

### 2. Triggering Playback

You can programmatically trigger media playback from your mod.

```java
import com.lia.mediaplayer.api.MediaPlayerAPI;

// Enqueue a video in the front-most video player (or open a new one if none exists)
MediaPlayerAPI.playVideo("https://www.youtube.com/watch?v=...");

// Open a video in a brand-new, independent player window
MediaPlayerAPI.playVideoNewWindow("https://www.youtube.com/watch?v=...");

// Enqueue audio in the compact audio bar
MediaPlayerAPI.playAudio("https://example.com/sound.mp3");

// Play a full playlist of audio tracks (starts immediately, queues the rest)
MediaPlayerAPI.playAudioAll(List.of("url1", "url2", "url3"), true /* shuffle */);

// Pin an image window
MediaPlayerAPI.showImage("https://example.com/image.png");
```

### 3. Listening to Playback Events

The mod fires `PlaybackEvent` on the **NeoForge event bus** (`NeoForge.EVENT_BUS`) whenever playback state changes. This is extremely useful for addons that want to synchronize video or audio playback across a server.

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
import com.lia.mediaplayer.api.MediaPlayerAPI;

// Toggles pause state for the active video
MediaPlayerAPI.togglePauseVideo();

// Skips to the next track in the audio bar
MediaPlayerAPI.nextAudio();

// Seeks the video to 50%
MediaPlayerAPI.seekVideo(0.5);
```

### 5. Accessing Playlists

The API provides methods to read and modify the user's saved playlists:

```java
import com.lia.mediaplayer.api.MediaPlayerAPI;

// List all playlists
for (MediaPlayerAPI.PlaylistInfo info : MediaPlayerAPI.getPlaylists()) {
    System.out.println("Playlist: " + info.name() + " has " + info.urls().size() + " tracks.");
}

// Create a playlist and add a track
MediaPlayerAPI.createPlaylist("Server Radio");
MediaPlayerAPI.addToPlaylist("Server Radio", "https://youtube.com/...");
```

### 6. Memory Management

You can integrate your own large memory objects (like custom caches) with Lia's Media Player's memory manager. When the game runs low on RAM, the memory manager will ask your components to release memory based on their priority.

```java
import com.lia.mediaplayer.media.MemoryMonitor;
import com.lia.mediaplayer.api.MemoryReleasable;

public class MyCustomCache implements MemoryReleasable {
    public MyCustomCache() {
        // Register the releasable to receive memory alerts
        MemoryMonitor.register(this);
    }

    @Override
    public int getReleasePriority() {
        return 20; // Lower values are asked to free memory first
    }

    @Override
    public boolean releaseMemory(boolean isCritical) {
        if (myMap.isEmpty()) return false;
        
        myMap.clear();
        // Return true if resources were actually freed
        return true; 
    }
}
```

## Class Reference

| Class                          | Description                                                                                       |
|--------------------------------|---------------------------------------------------------------------------------------------------|
| `MediaPlayerAPI`               | The main static facade for controlling playback, volume, playlists, and registering sources.      |
| `MediaSource`                  | Interface to implement to define a new recognized link format.                                    |
| `MediaKind`                    | Enum (`IMAGE`, `VIDEO`, `AUDIO`) returned by `MediaSource.kind()`.                                |
| `PlaybackState`                | Enum (`LOADING`, `PLAYING`, `PAUSED`, `ENDED`, `FAILED`) representing current player state.       |
| `MemoryReleasable`             | Interface to implement to respond to low memory situations and release resources.                 |
| `MediaSourceRegistrationEvent` | Mod bus event fired during `FMLClientSetupEvent` to collect custom `MediaSource` implementations. |
| `PlaybackEvent`                | Game bus event fired on transport changes (started, paused, seeked, ended, etc.).                 |
