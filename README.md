# vibe.me

An offline music player for Android. It plays the audio already on your phone — no account, no
sign-in, no network. Nothing leaves the device.

Got bored of not having a simple, easy music app with no ads. So I built one.

## What it does

**Library** — Songs, Albums and Artists, read straight from MediaStore. Sort by title, artist,
album, length, year, track number, recently added or most played, and group into sections by
album, artist, folder, year or first letter. Fuzzy search matches on prefix, substring or letters
in order, so `swok` finds *Somewhere Only We Know*.

**Readable names.** Most downloaded files are named like `Sia_-_Snowman(128k)` with no artist tag
at all. Those become **Snowman** by *Sia*: underscores and bitrate suffixes go, video-site noise
like `(Official Video)` goes, apostrophes lost to filename encoding come back, and when the tag is
missing the artist is taken from the filename. Files with no embedded artwork get a generated
cover, stable per track, so the list is scannable instead of a wall of blank squares.

**Playback** — a `MediaSessionService` owns one ExoPlayer, so audio survives leaving the app and
drives the media notification, lockscreen, headset buttons and Bluetooth. Gapless is always on.
Also a queue you can reorder, a sleep timer, a system equalizer with presets and bass boost,
playback speed, and skip silence.

**Notification** — transport controls plus shuffle, repeat and favourite, with icons that follow
live state. Tracks with no embedded art get the same generated cover the app uses.

**Playlists** — create, rename, reorder, delete. Favourites, plus derived lists for recently
added, most played and recently played.

**Editing** — change title, artist, album, genre, year and track number, written back to the file.
Share a track, set it as a ringtone, or delete it.

**Themes** — seven palettes, five light and two dark, switchable instantly. Plus a local profile:
a name, a picture, and counts taken from your own library.

## Building

Needs JDK 17+ and the Android SDK.

```bash
./gradlew assembleDebug       # debug APK
./gradlew test                # unit tests
./gradlew assembleRelease     # R8 + resource shrinking
./gradlew installDebug        # onto a connected device
```

`minSdk 24`, `compileSdk 37`. The release APK is about 2.9 MB.

## How it is put together

**MediaStore is the library.** The OS already indexes every audio file with title, artist, album,
duration, year, track number and date added, so the app never scans storage — one query, on a
background dispatcher. Albums and artists are grouped from that list in memory rather than
queried separately.

That decision removes the usual database layer: there is **no Room, no Hilt and no annotation
processing**. The only data the app owns is playlists, favourites, play counts, saved queue and
settings, which is small enough to hold in memory and is stored as one serialized blob in
DataStore. Dependency injection is a single `object`.

```
data/       MediaStore queries, name cleanup, sorting, user state, tag writing
playback/   MediaSessionService, controller, effects, notification controls
ui/         Compose screens, theme palettes, components
```

Sorting, grouping, search and the name cleanup are pure functions with no Android types, and are
covered by unit tests that run on the JVM.

## Known limits

- **Crossfade is a fade, not an overlap.** One player cannot render two tracks at once; a true
  crossfade needs a second ExoPlayer and a session that reports one item while rendering another.
- **No home-screen widget**, and Android Auto gets playback controls but not library browsing —
  that needs a `MediaLibraryService` browse tree.
- Equalizer and bass boost depend on device hardware and are silently unavailable where it is
  missing.
- Playlists are not importable or exportable as m3u.
