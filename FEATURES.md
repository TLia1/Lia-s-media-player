# Lia's Media player

**For Minecraft <!-- minecraft_version -->1.21.1<!-- /minecraft_version --> · NeoForge · Client-side only**

Lia's Media player is a companion mod that can display medias that you can *interact* — right inside Minecraft, without ever leaving the game.

---

## What it does at a glance

When a link to an image or video appears in chat, the mod quietly replaces the raw URL with a short, colored label:

- A gold **[picture]** for images
- A gold **[gif]** for Tenor GIFs
- An aqua, underlined **video label** for videos and YouTube links
- A green, underlined **[audio]** label for audio files

---

## Pictures and GIFs

**Hover to preview.** Open your chat and move the mouse over any **[picture]** label. A preview of the image pops up right above your cursor — no clicking, no browser, no tabbing out. The image is downloaded the first time you hover and then kept ready, so it appears instantly after that. Animated GIFs play in the preview.

**Supported images.** Standard picture files work out of the box: PNG, JPG/JPEG, GIF, and BMP.

**Tenor GIFs.** The mod figures out the real animated GIF behind the tenor page automatically and shows it for you — these appear as a **[gif]** label.

**Pin it on screen.** Want to keep a picture up while you keep playing or chatting? Click to pin it as its own little window. Pinned images can be:

- **Dragged** anywhere on screen
- **Resized** by dragging the bottom-right corner, or by holding **Ctrl** and scrolling the mouse wheel to zoom
- **Hidden or closed** with the small corner buttons

Previews and pinned windows are automatically scaled down to fit comfortably on your screen, never blown up past their real size.

---

## Videos

Click a video label and the mod opens a real, working video player *inside Minecraft* — with sound.
Hovering over a video label provides a handy tooltip reminder of your options.
* **Alt-Click a video link** to play it as audio-only in the compact audio bar.

**What it can play:**

- **Direct video files** shared in chat: MP4, WEBM, MOV, MKV, M4V, AVI, FLV, OGV, and TS
- **Live/adaptive streams**: HLS (`.m3u8`) and DASH (`.mpd`) manifests
- **YouTube links**: normal `watch` links, `youtu.be` short links, and Shorts

**The player window** gives you full controls:

- **Play / pause**
- A **seek bar** with elapsed and total time, so you can scrub to any point
- A **speaker toggle** and a pop-up **volume slider**
- A **next** button when more than one video is lined up
- **Move, resize, and zoom** the window exactly like a pinned image

**A built-in queue.** Instead of cluttering your screen with a new window for every link, extra videos are added to the current player's queue. When one finishes (or you press next), the window automatically swaps to the next video in place. The queue panel shows each entry's thumbnail and its real video name (the actual YouTube title, or the file name), so you can tell what's coming up at a glance.

**In-Game Playlists GUI** (`/liasmediaplayer playlists`)
  - Create, manage, and rename multiple personal playlists.
  - Reorder tracks effortlessly (click the up/down arrows or use the swap buttons next to tracks).
  - One-click shuffle play.
  - Import playlists directly from your clipboard (lines of URLs) and export playlists to your clipboard for easy sharing.

**Keeps playing in the background.** Hide a player, and it keeps running — audio and all — so you can listen while you play. Close it with the **×** button when you're done. While no menu or chat screen is open, visible video windows stay drawn on your HUD so a clip keeps showing during normal gameplay.

**Everything just works.** Video playback relies on two small helper tools: `ffmpeg` (to decode video and sound) and `yt-dlp` (to play YouTube links). If you don't already have them, the mod quietly downloads the official copies into its own folder when the game starts — so you don't have to set anything up. If you *do* have them installed, the mod finds them automatically in the usual places.

---

## Audio

Click an **[audio]** label and the mod opens a small **audio bar** — just the track name and a row of controls — so you can listen without a big window in the way. Hovering over an audio label will provide a tooltip reminder to click it.

**What it can play:**

- **Direct audio files** shared in chat: MP3, WAV, OGG/OGA, FLAC, M4A, AAC, OPUS, WEBA, WMA and AIFF

**The bar gives you:**

- **Play / pause**, **previous** and **next**
- A **seek bar** with elapsed and total time
- A **speaker toggle**, plus scroll the mouse wheel over the bar to change the volume

**A built-in queue, just like videos.** Click more audio links, and they line up behind the current one; the bar plays them one after another and the time read-out shows how many are waiting (a little `+N`). The volume is shared with the video player, so one setting controls everything.

**Keeps playing in the background.** Hide the bar and the music keeps going; while no menu is open it stays on your HUD showing the track name. The same top-left button that brings hidden videos back works for hidden audio bars too.

---

## Playlists

Open the **Playlists** button in the top-left of your chat (or bind a key for it — see below) to manage saved playlists.

- **Create** a playlist and give it a name.
- **Add** tracks by pasting a link — a direct audio file *or* a YouTube video (only the sound is played).
- **Rename**, **reorder tracks** (with the up/down arrows), **remove tracks**, or **delete** a playlist.
- **Play** it in order, or **Shuffle** it for a random order.

Your playlists are **saved to disk**, so they're still there next time you play.

---

## Mod Options

The mod now features a configurable options menu where you can tailor its behavior to your liking.
- You can access it by clicking the **Options** button in the Playlists menu, or via the **Config** button in the game's Mods list.
- **Video Resolution:** Choose the maximum resolution for video playback (from 144p up to 720p).
- **Resource Limits:** Adjust the maximum number of pinned images, video players, audio players, cached entries, and GIF frames to optimize memory usage based on your computer's capabilities.
- All options are saved automatically and persist between sessions.

---

## Keybinds

The mod adds keys you can set in **Options → Controls → "Lia's Media Player"** to drive the players without opening chat:

- **Play / Pause**, **Next track** and **Previous track**: these apply to the active audio track, or if none is playing, the active video player.
- **Open playlists**

They're **unbound by default** so they never clash with your existing keys — just assign whatever you like.

## Chat Commands

You can also start playing media directly via chat commands without receiving a link first:
- `/show <type> <url> [newPlayer]`
  - `type`: Either `image`, `video`, or `audio` (with auto-completion). Note: A video URL can be played as `video` or `audio`, but you cannot play an audio URL as a video.
  - `url`: The link to the media.
  - `newPlayer` *(optional)*: When `true`, opens the media in a brand-new player window instead of adding it to the queue of an existing player. Defaults to `false`.

---

## Why you'll like it

- **No alt-tabbing.** Images and videos play where the conversation is happening.
- **Zero setup for most things.** Hover an image, and it just appears; click a video, and it just plays.
- **Stays out of your way.** Nothing shows up until media is actually shared, labels are compact, and everything is downloaded in the background so the game never stutters waiting on a link.
- **You're in control.** Drag, resize, pin, hide, queue, mute — arrange your media however you like, then close it all when you're done.

---

*Lia's Media Player is a client-side convenience mod. It only affects how shared image, video, and audio links appear in your own chat — it does not modify gameplay, the world, or other players.*
