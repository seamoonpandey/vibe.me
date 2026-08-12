# vibe.me

A music player for the songs already sitting on your phone.

No account. No sign-in. No network permission at all — the app cannot phone home because it was
never given the ability to. No ads, no "discover weekly", no upsell, nothing that wants your
attention for its own reasons.

I got tired of not having a simple music app without ads. So I built one.

## Download

**[Download vibe.me 1.0 (APK, 3 MB)](https://github.com/seamoonpandey/vibe.me/releases/latest)**

Android 7.0 or newer. Your phone will warn you about installing outside the Play Store — that
warning is correct and you should read it, then allow it if you trust me. Or build it yourself
from source, which is the version of trust I would pick.

## What it's like to use

**It opens where you left it.** Same track, same spot in the queue, paused. Closing the app is not
an event you have to recover from.

**Your files probably have terrible names.** Mine do. Something like `Sia_-_Snowman(128k).mp3`
with no artist tag at all. The app shows that as **Snowman** by *Sia* — underscores gone, bitrate
suffix gone, `(Official Video)` gone, apostrophes that got mangled by some download tool put back,
and the artist taken from the filename when the tag simply isn't there.

Files without embedded artwork get a generated cover instead of a blank grey square. Same track,
same colours, every time — so the list is something you can scan rather than a wall of nothing.

**Search is forgiving.** Type `swok` and you get *Somewhere Only We Know*. Prefix, substring, or
just the right letters in the right order.

**Sorting that's actually there when you need it.** Title, artist, album, length, year, track
number, recently added, most played. Group into sections by album, artist, folder, year or first
letter. The current order is written out in words at the top of the list, one tap from being
changed — because on a library of untagged downloads, sort order *is* the structure.

**It keeps playing.** Playback lives in a service, not in the screen, so it survives you leaving
the app. Lockscreen, notification, headset buttons, Bluetooth, Android Auto — all of it works.
Gapless is always on. The notification's shuffle, repeat and favourite buttons show the truth
rather than a guess.

**Also:** a queue you can reorder, a sleep timer, playback speed, skip silence, an equalizer with
presets and bass boost, playlists, favourites. You can edit a track's tags and the change is
written back into the file. Share a track, set it as a ringtone, delete it.

**Seven themes**, five light and two dark, switching instantly. Each one has its own dog.

## Building it yourself

Needs JDK 17+ and the Android SDK.

```bash
./gradlew assembleDebug       # debug APK
./gradlew test                # unit tests
./gradlew assembleRelease     # R8 + resource shrinking
./gradlew installDebug        # onto a connected device
```

`minSdk 24`, `compileSdk 37`. The release APK is about 3 MB.

Release signing is read from `local.properties`, which is not in this repository. Without it
`assembleRelease` still works — the APK just comes out unsigned, which is the right outcome for
someone building their own copy.

## How it's put together

**MediaStore is the library.** Android already indexes every audio file on the device with title,
artist, album, duration, year, track number and date added. So the app never scans storage — one
query, on a background thread, done. Albums and artists are grouped from that same list in memory
rather than asked for separately.

That one decision removes the entire database layer. There is **no Room, no Hilt, no annotation
processing**. The only data the app owns is playlists, favourites, play counts, the saved queue
and settings — small enough to hold in memory, stored as a single serialized blob. Dependency
injection is one `object` with four fields.

```
data/       MediaStore queries, name cleanup, sorting, user state, tag writing
playback/   MediaSessionService, controller, effects, notification controls
ui/         Compose screens, theme palettes, components
```

Sorting, grouping, search and the name cleanup are pure functions with no Android types in them,
and they have unit tests that run on the JVM in about a second.

## About the battery

A music player is judged on what it costs while you aren't looking at it, so the background work
is kept honest:

- The service exists only while there is playback or a UI. It isn't started at launch and left
  resident.
- Position polling stops entirely when nothing is on screen to display it — the progress bar is
  the only reason to poll, and a locked screen has no progress bar.
- The crossfade and sleep-timer timers idle at one tick every two seconds and only speed up when
  one of them is actually armed and audio is running.
- The equalizer is touched when you move a knob, not every time a play count is written.
- Album-art colour extraction runs off the main thread, on a 64px thumbnail. A swatch does not get
  better from more pixels.

## Known limits

- **Crossfade is a fade, not an overlap.** One player cannot render two tracks at once. A real
  crossfade needs a second ExoPlayer and a session that reports one item while rendering another.
- **No home-screen widget.** Android Auto gets playback controls but not library browsing — that
  needs a `MediaLibraryService` browse tree.
- Equalizer and bass boost depend on the device having the hardware, and are quietly unavailable
  where it doesn't.
- Playlists can't be imported or exported as m3u yet.
