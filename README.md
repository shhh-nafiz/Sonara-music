# Sonara – local music player (Spotify-style)

Kotlin + Jetpack Compose + Media3 (ExoPlayer). Plays the music stored on your phone.

## Build on GitHub (no Android Studio needed)
Push this folder to a GitHub repo, open the Actions tab > "Build APK" > Run workflow. When it finishes, download `Sonara-apk` from the run page, unzip it and install `app-release.apk` on your phone.

## Build locally
1. Install Android Studio (Koala or newer). File > Open > select this folder.
2. Let Gradle sync (needs internet once; it downloads Gradle 8.7 and the libraries).
3. Plug in your phone (USB debugging on) and press Run, or Build > Build APK(s).
4. Android 8.0+ (API 26+). Allow the music + notification permissions on first launch.

## Features
- Reads every song from phone storage (MediaStore), shows embedded cover art
- Spotify-like layout: Home, Search, Library (Songs/Albums/Artists/Playlists/Favorites), mini player, full Now Playing screen, queue
- Heart (favorite) button on the player, mini player, notification, and a home-screen widget
- No-repeat shuffle: the queue is a permutation of all songs; a new permutation is built only after the last one starts
- Repeat off/all/one, gapless playback, sleep timer, swipe cover to skip
- Live audio visualizer (Bars, Mirror, Wave, Circle) driven by the real audio; tap it to change style
- 10-band equalizer (31 Hz–16 kHz, ±12 dB), preamp, bass boost, 20 presets, auto-headroom + soft limiter
- Highest quality: float (32-bit) output pipeline, FLAC/WAV/ALAC/MP3/AAC/OGG/Opus, bit-perfect when EQ is off (16-bit)
- Custom accent + background colors (swatches + RGB sliders), adaptive cover-tinted player background
- Home-screen widget: add via long-press home screen > Widgets > Sonara

## Library control
- Settings > Manage library: pick storage (internal / SD card), tick/untick folders, music-only filter, minimum clip length
- Blacklist: hides a song everywhere and never plays it (song menu > Blacklist, or Manage > Blacklist)
- Ignore in shuffle: song stays in the library but shuffle skips it until you un-ignore it (song menu, or Manage > Ignored, search to find it)
- WhatsApp / Telegram / recorder / ringtone folders are excluded automatically on first run

## Motion & playback extras
- Player is a draggable sheet: swipe the mini player up / the player's top bar down, it follows your finger and springs into place
- Animated page transitions, animated list changes, animated song title/cover changes
- Settings > Playback: fade between songs (0-12 s), playback speed 0.5x-2x, skip silence
- Library: sort songs (title / artist / recent / length / album) and browse by folder

## Latest additions
- Loudness normalization (ReplayGain tags), skips unplayable files automatically
- Drag-to-reorder queue, A-Z fast scroller, accent color that follows the album cover
- Resumes your queue and position after the app is closed

## Lyrics
Time-synced lyrics from LRCLIB (free, open database). Matched by title, artist, album and song length, downloaded automatically when you're online and cached on the phone. Tap the lyrics button in the player; tap a line to jump to it.
