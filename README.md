# vibe.me

A music player for the songs already sitting on your phone.

No account. No sign-in. No ads, no "discover weekly", no upsell, nothing that wants your attention
for its own reasons.

The network is used for one thing: the Explore tab, where you can search YouTube, play a result
immediately and save it to your phone. Nothing is uploaded, there is no telemetry, and no request
goes anywhere but YouTube. If you never open Explore, the app makes no network calls at all.

I got tired of not having a simple music app without ads. So I built one.

## Download

**[Download vibe.me 1.3 (APK, 4 MB)](https://github.com/seamoonpandey/vibe.me/releases/latest)**

Android 7.0 or newer. Your phone will warn you about installing outside the Play Store — that
warning is correct and you should read it, then allow it if you trust me. Or build it yourself
from source, which is the version of trust I would pick.

Every release, with the notes that went with it, is in the [changelog](CHANGELOG.md).

## What it's like to use

**It opens where you left it.** Same track, same spot in the queue, paused. Closing the app is not
an event you have to recover from.

**It can go and get music.** Explore searches YouTube's music vertical — so no vlogs, no reaction
videos, none of the hour-long "mixes" — and the results are ordinary track rows. Tap one and it
streams straight away, into the same queue as your own files: a downloaded track and a streamed one
sit next to each other and you queue them the same way. Tap the arrow on a row and it saves into
`Music/vibe.me` as a tagged file with the cover art embedded, and it is in your library before you
have finished looking for it.

**Downloads stay visible.** A Downloads tab holds both halves of the job: what is transferring,
with a progress bar and a cancel, above everything that has already landed. The bottom half is read
back from the files themselves rather than bookkept, so it cannot disagree with your library about
whether something was saved. A finished download says so; a failed one says why and offers a retry.

**Your files probably have terrible names.** Mine do. Something like `Sia_-_Snowman(128k).mp3`
with no artist tag at all. The app shows that as **Snowman** by *Sia* — underscores gone, bitrate
suffix gone, apostrophes that got mangled by some download tool put back, and the artist taken
from the filename when the tag simply isn't there.

The video junk is matched by shape rather than from a list, because a real library outruns any
list: `Official Video`, `Official HD Video`, `Official Lyrics Video`, `OFFICIAL VISUALIZER` and
`Album Visualizer` are all the same thing wearing different clothes. A qualifier is required in
front, so *Video Games* and *The Lazy Song* keep their names.

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
rather than a guess, and tapping the card itself opens the player on the track it is showing.

**The seek bar is exact.** Position isn't polled on a timer — the player hands out where playback
was and the moment that was true, and the bar interpolates it against the frame clock. So it moves
at the refresh rate instead of stepping twice a second, and it is right to the millisecond in
between. Scrubbing starts on the press, not after your finger has travelled the touch slop, so the
thumb is under your finger from the first frame and the position you release on is the one you saw.

**Tapping the track that's already playing** opens the player at the spot you're at. It does not
start the song over.

**Also:** a queue you can reorder, a sleep timer, playback speed, skip silence, an equalizer with
presets and bass boost, playlists, favourites. You can edit a track's tags and the change is
written back into the file. Share a track, set it as a ringtone, delete it.

**Seven themes**, five light and two dark. Switching is a hard cut on the next frame — no colour
trailing along behind the rest of the page. Each one has its own dog.

## Building it yourself

Needs JDK 17+ and the Android SDK.

```bash
./gradlew assembleDebug       # debug APK
./gradlew test                # unit tests
./gradlew assembleRelease     # R8 + resource shrinking
./gradlew installDebug        # onto a connected device
```

`minSdk 24`, `compileSdk 37`. The release APK is about 4 MB.

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
data/         MediaStore queries, name cleanup, sorting, user state, tag writing
data/remote/  the only code that touches a network: extractor, downloads, thumbnails, tokens
playback/     MediaSessionService, controller, effects, notification controls
ui/           Compose screens, theme palettes, components
```

Everything network-facing is behind `data/remote/`, and the downloader talks to `HttpURLConnection`
directly — forty lines — rather than adding a second HTTP stack to an APK that only ever needs one.

Sorting, grouping, search and the name cleanup are pure functions with no Android types in them,
and they have unit tests that run on the JVM in about a second.

## About the battery

A music player is judged on what it costs while you aren't looking at it, so the background work
is kept honest:

- The service exists only while there is playback or a UI. It isn't started at launch and left
  resident.
- Position isn't polled at all. It is an anchor the UI interpolates against the frame clock, so
  there is nothing ticking; a slow beat re-anchors it, and only while something is on screen to
  show it. That also means a position update no longer pushes new state through the whole screen
  twice a second for a number one bar cares about.
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
- **An interrupted download starts over.** There is no resume across the app being killed: the
  partial file is discarded and the track is re-queued by hand. For 4 MB files, a range-request
  journal costs more than it saves.
- **Downloads run one at a time.** Two transfers on one phone share one radio and finish no sooner
  than they would in sequence, so they are queued rather than raced.
- **Explore breaks when YouTube changes.** Extraction is maintenance, not a feature: the fix is a
  new build against a newer extractor, and an old APK keeps failing until it gets one.

## Licence

GPLv3, because the app links [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor),
which is GPL-3.0-or-later — that makes vibe.me a derivative work and it ships under the same
licence. The proof-of-origin token code in `data/remote/potoken/` is ported from NewPipe, and each
of those files carries its attribution. See [LICENSE](LICENSE).
