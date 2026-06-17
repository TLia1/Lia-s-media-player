# Lia's Media player

**For Minecraft 1.21.1 · NeoForge · Client-side only**

Lia's Media player is a companion mod that links into things you can actually *see and watch* — right inside Minecraft, without ever leaving the game.

---

## What it does at a glance

When a link to an image or video appears in chat, the mod quietly replaces the raw URL with a short, colored label:

- A gold **[picture]** for images
- A gold **[gif]** for Tenor GIFs
- An aqua, underlined **video label** for videos and YouTube links

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

**Keeps playing in the background.** Hide a player and it keeps running — audio and all — so you can listen while you play. Close it with the **×** button when you're done. While no menu or chat screen is open, visible video windows stay drawn on your HUD so a clip keeps showing during normal gameplay.

**Everything just works.** Video playback relies on two small helper tools: `ffmpeg` (to decode video and sound) and `yt-dlp` (to play YouTube links). If you don't already have them, the mod quietly downloads the official copies into its own folder when the game starts — so you don't have to set anything up. If you *do* have them installed, the mod finds them automatically in the usual places.

---

## Why you'll like it

- **No alt-tabbing.** Images and videos play where the conversation is happening.
- **Zero setup for most things.** Hover an image and it just appears; click a video and it just plays.
- **Stays out of your way.** Nothing shows up until media is actually shared, labels are compact, and everything is downloaded in the background so the game never stutters waiting on a link.
- **You're in control.** Drag, resize, pin, hide, queue, mute — arrange your media however you like, then close it all when you're done.

---

*Lia's Utils Client is a client-side convenience mod. It only affects how shared image and video links appear in your own chat — it does not modify gameplay, the world, or other players.*