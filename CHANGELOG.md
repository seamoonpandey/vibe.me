# Changelog

Every release, newest first. The APK always lives at
[releases/latest](https://github.com/seamoonpandey/vibe.me/releases/latest), so the download link
never has to be kept up to date — only the version beside it.

## 1.2 — 2026-09-21

**Search, stream and keep.** The app was a player for the files you already had. It can now go and
get one: an Explore tab searches YouTube, a result plays straight away as an ordinary track in the
same queue as everything else, and tapping the arrow on a row saves it into `Music/vibe.me` as a
properly tagged file the library picks up by itself.

The downloads in this release are the first ones that worked. Three separate faults, each of which
ended with a transfer that fetched perfectly and saved nothing:

- The fetch loop trusted the length the server declared, and Google Video answers `HEAD` with
  `Content-Length: 0`. Every chunked range request therefore ran past the end of the file until the
  host answered `416`, which threw after the whole thing had already been written. The loop is
  driven by bytes now — a short chunk is simply the end of the body — and a size is only ever used
  to fill a progress bar.
- YouTube's Opus streams carry more bitrate than its M4A ones, so they won every pick. MediaStore's
  audio table has no type for a WebM file and rejects the insert outright, so the better-sounding
  stream was the one that could never be saved. M4A wins whenever it exists; the Opus fallback is
  filed as ogg, which is the nearest type the table has.
- The insert wrote no `IS_MUSIC`, and the library filters on it, so a finished download could exist
  on disk and appear nowhere. Title, artist, album and `IS_MUSIC` are written up front, and the
  track is on Home the moment the download clears its pending flag.

**A Downloads page.** A fifth destination holding both halves of the job: what is transferring and
what has landed. Queued and running rows carry a progress bar, a cancel, and — on failure — the
reason it failed with a retry beside it. The bottom half is read back from `Music/vibe.me` rather
than bookkept, so the page cannot disagree with the library about whether something was saved.

**It tells you what happened.** A finished download raises a dialog naming the track, with a Done
button, and posts a notification for when you have left the app. A failure says why instead of
disappearing. The foreground "Downloading" notice is handed back when the queue empties, which it
never used to be.

**The honest cost:** the app can now use the network. It talks to YouTube and to nothing else, only
when you search, play a stream or save one, and still has no account, no telemetry and no ads. That
is a real change to the promise 1.0 made, and the README no longer claims otherwise.

Under it: the app is GPLv3, because it links NewPipeExtractor.

## 1.1 — 2026-08-17

**Tapping the media notification opens the app.** It controlled playback but went nowhere when you
tapped the card itself — the session had no activity attached. It does now, and it lands on the
player showing the track you tapped.

**Tapping the track that's already playing no longer restarts it.** It opens the player at the spot
you're at, which is what you meant.

**The seek bar is exact.** Position used to be read on a half-second timer, so the thumb moved in
visible steps and could be half a second stale under your finger. The player now hands out where
playback was and the moment that was true, and the bar interpolates against the frame clock — so it
moves at the refresh rate and is right to the millisecond. Scrubbing starts on the press instead of
after your finger has travelled the touch slop, so the thumb is under your finger from the first
frame and the spot you release on is the spot you saw.

**Switching themes is instant.** Everything repainted at once except the artwork accent, which
crossfaded along behind for 700ms. That trailing colour is why it read as a transition rather than
a choice.

**Titles are cleaner.** Video junk is matched by shape now instead of from a list of spellings, so
`Official HD Video`, `Official Lyrics Video`, `OFFICIAL VISUALIZER` and `Album Visualizer` all go
the way `Official Video` already did. *Video Games* and *The Lazy Song* keep their names.

**Motion.** Screens travel in the direction you moved, the player springs up, play and pause turn
into one another, artwork fades in, and the track that's playing has three bars moving on it so you
can find it without reading.

Under it: a position update no longer pushes new state through the entire screen twice a second.

## 1.0 — 2026-08-12

An offline music player for the audio already on your phone. No account, no network permission, no
ads.

- Library read straight from MediaStore, with sorting, grouping and forgiving search
- Filenames like `Sia_-_Snowman(128k)` cleaned up into readable titles and artists
- Generated covers for files with no embedded artwork
- Background playback with lockscreen, notification, headset, Bluetooth and Android Auto controls
- Playlists, favourites, queue reordering, sleep timer, equalizer, playback speed, skip silence
- Tag editing written back to the file
- Seven themes
