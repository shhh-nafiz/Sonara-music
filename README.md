# Sonara – local music player (Spotify-style)

Kotlin + Jetpack Compose + Media3 (ExoPlayer). Plays the music stored on your phone.

## Build
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
