# YouTube search, streaming and download

Date: 2026-08-17
Status: approved for planning

## Problem

vibe.me plays the files already on the device. Getting files onto the device is somebody else's
job — currently yt-dlp on a desktop, then a cable. That is the gap this closes: search, play, and
save, without leaving the app.

The reference points are NewPipe, VidMate and mp3juices. What is being asked for is their core
loop — type a name, hear it immediately, keep it if you want it — on top of the local player that
already exists.

## What this costs before a line is written

Four consequences, none of them reversible by a later commit. They are listed first because they
are the actual price, and the code is the easy part.

**1. The app becomes GPLv3.** NewPipeExtractor is GPL-3.0-or-later. Linking it makes vibe.me a
derivative work, so vibe.me ships under GPLv3 or it is not distributable at all. The repo has no
LICENSE file today. Adding one is part of Phase 1, not an afterthought.

**2. The central claim in the README dies.** "No network permission at all — the app cannot phone
home because it was never given the ability to" becomes false the moment `INTERNET` is in the
manifest. The honest replacement is a narrower promise: no accounts, no telemetry, no ads, and
network traffic only to YouTube and only when you ask for it. The 2026-08-17 metadata-repair spec
lists "no INTERNET permission" as a non-goal and gives it as the *reason* that design is a manual
CSV round trip rather than an API call. That reasoning is now void; the CSV design still stands on
its own merits, but it is no longer forced.

**3. The APK roughly quadruples.** ~3 MB to ~12 MB. Breakdown below.

**4. This is permanent maintenance, not a feature.** YouTube changes shape and extraction breaks.
The only fix is bumping NewPipeExtractor and shipping again. Anyone who installed an old APK is
stuck with a broken Explore tab until they update. Google Play was never an option for this app and
now never will be.

These were raised and accepted before this spec was written.

## Goals

1. Search YouTube from inside the app and see results as ordinary track rows.
2. Tap a result and hear it now, streamed, through the existing player, notification and lockscreen.
3. Save a result as a real tagged file in `Music/vibe.me`, which then behaves like any other track.
4. Mixed queues: a downloaded track and a streamed one sit next to each other in one queue.
5. Fail legibly. Offline, extraction breakage and 403s produce a readable row with a retry, never a
   crash and never a silent empty list.

## Non-goals

- **No transcoding.** Downloads land as the source stream, `.m4a` (AAC) or `.opus`. media3 plays
  both and MediaStore indexes both. A bundled encoder would cost more APK than the entire rest of
  this feature and would re-encode a lossy source into a lossier one.
- **No video.** Audio streams only. This is a music player.
- **No playlists, channels, trending, related tracks, comments or subscriptions.** Search and a
  result list. The extractor offers far more; none of it is wanted here.
- **No account, no login, no cookies.** Anonymous extraction only.
- **No background or scheduled downloading.** Downloads run when queued, in a foreground service,
  visible in the shade.
- **No download resume across process death.** An interrupted download is discarded and re-queued
  by hand. Resume is a range-request plus a journal, and it is not worth it for 4 MB files.

## Architecture decision: one `Song`, not two models

Three options were weighed.

| | Approach | Verdict |
|---|---|---|
| A | Extend `Song` with two nullable fields | **Chosen** |
| B | Separate `RemoteTrack` and a second player path | Rejected |
| C | A `Playable` interface both implement | Rejected |

**B** was rejected because it duplicates NowPlaying, the mini player, the queue UI, the media
notification and the session callbacks, and it makes goal 4 impossible — a local and a remote track
could never share one queue.

**C** was rejected on cost. Every `List<Song>` signature across six UI files becomes
`List<Playable>`, for no user-visible gain over A. Correct on paper, worst return here.

**A** costs two fields that are null on 99% of rows. In exchange, everything downstream — favorites,
play counts, the queue, the notification, the sleep timer, the equalizer — works on remote tracks
without being told they exist.

## Phase 1 — Build, dependency, licence

### Coordinates

NewPipeExtractor is not on Maven Central under `net.newpipe`; that group publishes snapshots only.
JitPack is the real distribution channel, and it is needed for the TeamNewPipe nanojson fork
regardless.

`settings.gradle.kts` gains `maven { url = uri("https://jitpack.io") }`.

```
implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
```

Pin the exact tag. Do not use a `+` range or a commit SNAPSHOT: a silent extractor bump is a silent
change to how the app behaves against a hostile remote.

### What comes with it

| Artifact | Version | Approx. |
|---|---|---|
| rhino + rhino-engine | 1.8.1 | ~2.5 MB |
| protobuf-javalite | 4.35.1 | ~900 KB |
| jsoup | 1.22.2 | ~450 KB |
| nanojson (TeamNewPipe fork) | pinned commit | ~30 KB |
| jsr305 | 3.0.2 | ~20 KB |
| extractor itself | v0.26.5 | ~1.5 MB |
| desugar_jdk_libs | 2.x | ~1 MB |

Rhino is there to execute YouTube's signature-decipher JavaScript, and the keep rules below stop R8
shrinking it, so budget it whole. Expect ~3 MB to ~12 MB installed.

### Desugaring

The extractor targets a Java 11 toolchain and uses `java.time`, which is API 26. minSdk is 24, so
core library desugaring is mandatory — NewPipe itself sets it. `app/build.gradle.kts` gains:

```kotlin
compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
```

plus `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")`.

### R8

The release build runs `isMinifyEnabled = true` and `isShrinkResources = true`, so missing keep
rules produce a debug build that works and a release APK that dies at runtime — the worst failure
shape available. Port NewPipe's rules verbatim into `proguard-rules.pro`:

```proguard
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
```

`javax.script` and `jdk.dynalink` do not exist on Android; rhino-engine references them and the
`-dontwarn` pairs are what keep R8 from failing the build over it.

### Manifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
                 android:maxSdkVersion="28" />
```

`WRITE_EXTERNAL_STORAGE` is capped at 28 because scoped storage takes over at 29 and the MediaStore
insert path needs no permission there.

### Licence

Add `LICENSE` (GPL-3.0), an SPDX header convention for new files, and a README section naming
NewPipeExtractor and its licence. Gate 1 for this phase: a signed release APK installs and the
existing local library still plays. The dependency is inert at this point — that is the check.

## Phase 2 — Model and queue correctness

### `data/Models.kt`

```kotlin
data class Song(
    ...,
    /** YouTube video id. Null means this is a local MediaStore file. */
    val streamKey: String? = null,
    /** Remote thumbnail. Null means use the MediaStore album-art endpoint. */
    val artUrl: String? = null,
) {
    val isRemote: Boolean get() = streamKey != null

    val uri: Uri get() =
        if (streamKey != null) "vibe://yt/$streamKey".toUri()
        else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    val artUri: Uri get() =
        artUrl?.toUri()
            ?: ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
}
```

The `vibe://yt/<id>` placeholder is load-bearing and is explained in Phase 5.

### Identity

MediaStore `_ID` is always positive, so remote ids take the negative half of the space and can never
collide with a local file:

```kotlin
// ponytail: hash, not a registry, because the id must survive a restart — favorites and play
// counts are persisted against it. ~65k distinct remote tracks before a 50% chance of one
// collision; swap for a persisted videoId->id table if that ever matters.
fun remoteId(videoId: String): Long = -((videoId.hashCode().toLong() and 0x7FFFFFFFL) + 1)
```

Stability matters more than collision-freedom: a sequential registry would be collision-proof but
would reassign ids on every launch, orphaning every favorite on a remote track.

### The queue bug this unblocks

`PlayerController.publish()` rebuilds the visible queue by looking every media id up in `songsById`,
which is populated only by `setLibrary()` from MediaStore. Any item not in the local library is
dropped from the queue the UI renders — the track would play while being invisible in the queue
sheet, and `PlayerUiState.current` would be null, so the mini player and NowPlaying would show
nothing.

Fix: `play()`, `addToQueue()` and `playNext()` register whatever they are handed.

```kotlin
private val extra = mutableMapOf<Long, Song>()
private fun remember(songs: List<Song>) { songs.forEach { extra[it.id] = it } }
```

`publish()` resolves `songsById[id] ?: extra[id]`. This is a latent correctness fix independent of
this feature — any song reaching the player from outside a library read hits it today.

Gate 2: a unit test that a hand-built `Song` with a `streamKey` survives a round trip through the
queue-resolution logic, and `remoteId` is negative, stable and distinct across a sample of ids.

## Phase 3 — The YouTube source

`data/remote/YouTube.kt`, roughly 140 lines.

```kotlin
object YouTube {
    fun init()                                        // NewPipe.init(downloader, localization)
    suspend fun search(query: String): List<Song>
    suspend fun audioStreamUrl(videoId: String): String
}
```

Everything on `Dispatchers.IO`.

**Downloader.** NewPipeExtractor requires an implementation of its abstract
`org.schabi.newpipe.extractor.downloader.Downloader`. Implement it over `HttpURLConnection` — about
40 lines. OkHttp is not added; it would be a second HTTP stack for no gain.

**Search** uses `ServiceList.YouTube.getSearchExtractor(query, listOf("music_songs"), "")`, taking
only `StreamInfoItem` results and ignoring channels and playlists. Each becomes a `Song` with
`id = remoteId(videoId)`, `durationMs` from the item, `artUrl` from the thumbnail list, and
`album = ""`.

**Titles go through the existing repair pipeline.** `tidyNames(rawTitle, uploaderName)` in
`Display.kt` was built for exactly this input — `Sia - Snowman (Official Video)` is the shape it
already parses, splitting artist from title and stripping video noise by shape. Reusing it means
search results and the local library read identically, and a track looks the same before and after
it is downloaded. This is the single largest piece of leverage in the design and it costs one
function call.

**Stream selection** takes `StreamInfo.audioStreams`, keeps m4a and opus, and picks the highest
`averageBitrate`. No format preference beyond that — whichever is better wins.

Gate 3: `search("snowman")` returns non-empty on a device, and the first row's title and artist are
split correctly.

## Phase 4 — PoToken

Since 2024 YouTube gates most audio stream URLs behind a proof-of-origin token. Without one, streams
return 403 — intermittently at first, then broadly. The extractor defines the
`PoTokenProvider` interface and `PoTokenResult`, but supplies no implementation: generating a token
means running YouTube's BotGuard JavaScript, which needs a browser.

NewPipe's own implementation is five files, ~700 lines of Kotlin, driving a headless `WebView`:
`PoTokenWebView`, `PoTokenGenerator`, `PoTokenProviderImpl`, `JavaScriptUtil`, `PoTokenException`.
Port it. It is GPLv3, which is already the licence as of Phase 1, so this is permitted provided the
attribution is kept.

Consequences worth stating plainly:

- The app now instantiates a `WebView` and loads Google-authored JavaScript into it. That is a much
  larger trust surface than an HTTP client, and it is the single least defensible part of this
  design against the app's original premise.
- The WebView is created lazily on first Explore use and destroyed when the tab is left, so a user
  who never opens Explore never loads it.
- Tokens are cached in memory and regenerated on 403.

**This phase is the highest-risk one in the spec and the most likely to be the reason the feature
does not work.** It should be built and verified before Phase 5 depends on it, and if it cannot be
made to work, the honest outcome is to stop — a download button that 403s is worse than no tab.

Gate 4: a 403 that reproduces without a provider stops reproducing with one, on a real device.

## Phase 5 — Streaming playback

### The expiry problem

Extracted googlevideo URLs carry an expiry, typically around six hours, and are bound to the client
that requested them. A URL cannot be put in the queue, because the queue is persisted across
restarts and a user's queue routinely outlives the URL in it.

### The fix

The queue holds only the stable `vibe://yt/<videoId>` placeholder. A media3
`ResolvingDataSource.Resolver` swaps it for a live URL at the moment the loader opens the stream:

```kotlin
// playback/RemoteResolver.kt
ResolvingDataSource.Resolver { spec ->
    if (spec.uri.scheme != "vibe") spec
    else spec.withUri(YouTube.audioStreamUrl(spec.uri.lastPathSegment!!).toUri())
}
```

Blocking network here is correct: `resolveDataSpec` runs on media3's loader thread, never the main
thread. A track parked in the queue overnight resolves fresh when it is reached, and a queue
restored on a cold start needs no revalidation pass.

Resolved URLs are cached in memory for five hours, keyed by video id, so seeking and re-preparing
the same track do not re-hit YouTube.

### Wiring

In `PlaybackService.onCreate()`:

```kotlin
ExoPlayer.Builder(this)
    .setMediaSourceFactory(
        DefaultMediaSourceFactory(
            ResolvingDataSource.Factory(DefaultDataSource.Factory(this), RemoteResolver),
        ),
    )
```

**And `setWakeMode(C.WAKE_MODE_LOCAL)` becomes `C.WAKE_MODE_NETWORK`.** The existing comment
explains the choice — *"a network flag is not wanted here, since every file is local"* — and that
premise no longer holds. Without the change, a dozing device drops wifi mid-stream and playback
stalls. The comment gets rewritten to say why the flag is now warranted, so the next reader does not
undo it.

Gate 5: a streamed track plays, survives the screen locking, appears correctly on the lockscreen,
and can be seeked. A queue containing one local and one remote track plays through the boundary in
both directions.

## Phase 6 — Download

`data/remote/Downloads.kt` and `playback/DownloadService.kt`.

**Service.** Foreground, `android:foregroundServiceType="dataSync"`, its own notification channel
showing the current title and a progress bar. Separate from `PlaybackService`: a download must
survive playback stopping, and a foreground service that is sometimes about audio and sometimes
about a file transfer is two services wearing one name.

**Queue.** A `Channel<Song>` consumed serially, one download at a time. Parallel downloads on a
phone contend for the same radio and finish no sooner.

**Path.** One code path for every API level:

1. Resolve the stream URL (Phase 3), read it to a temp file in `cacheDir`.
2. Tag the temp file with **jaudiotagger, which is already a dependency** — title, artist, album,
   plus the thumbnail fetched and embedded as cover art. jaudiotagger needs a real seekable `File`,
   which is exactly why the temp step exists rather than streaming straight into MediaStore.
3. Copy into MediaStore: on API 29+, insert into `Audio.Media` with
   `RELATIVE_PATH = Music/vibe.me` and `IS_PENDING = 1`, write, then clear the pending flag. On 24
   to 28, write to `Environment.DIRECTORY_MUSIC/vibe.me` and notify the scanner.
4. Delete the temp file, in a `finally`.

**Appearing in the library is free.** `MediaStoreLibrary` already registers a `ContentObserver`, so
the new file triggers a reload with no call from the download path. Nothing new to wire.

**State.** `MutableStateFlow<Map<String, DownloadState>>` keyed by video id, exposed through `Deps`,
where `DownloadState` is `Queued | Running(fraction) | Failed(reason) | Done`. The Explore row reads
it directly.

**Failure.** Partial files are deleted, never left half-written for MediaStore to index. A failed
download shows the reason on its row with a retry.

Gate 6: a downloaded file appears in Home without any manual refresh, carries correct tags and
embedded art when inspected by a second app, and plays offline with the network off.

## Phase 7 — The Explore tab

`ui/ExploreScreen.kt`, roughly 200 lines.

Home / **Explore** / Playlists / Profile. `Screen.Explore` joins the sealed interface and counts as
top-level.

- A search field with a 400 ms debounce. Empty query shows an empty state, not a spinner.
- Results reuse the existing `SongRow` from `Components.kt`, so an Explore row and a Home row are
  the same object visually.
- Tap plays — the track goes into the queue as a normal `Song` and streams.
- A trailing download control per row, showing queued / progress ring / done / retry from the
  Phase 6 flow. A track already in the local library shows as already-owned rather than offering a
  second copy.
- Error rows are explicit and named: no connection, extraction failed, blocked. NewPipeExtractor
  throws `ExtractionException`, `ReCaptchaException` and plain `IOException` freely, and each maps
  to a different sentence. A silent empty list is not an acceptable rendering of any of them.

Gate 7: search, stream, download and the four error states all render on a device, including with
aeroplane mode on.

## Phase 8 — Extract navigation

`MainActivity.kt` is 754 lines against the project's 500-line limit. Adding a fourth destination
makes it worse, so the `Screen` graph, `backTargetFor`, the `AnimatedContent` routing block and the
`NavigationBar` move to `ui/Navigation.kt`. Pure extraction, no behaviour change, no new
abstraction — done because this feature is editing that file anyway.

This is deliberately last: it produces a large no-op diff, and putting it after the behavioural work
keeps it from hiding a real change inside a move.

## Testing

Matching what is already there: plain JVM JUnit, no instrumentation, no network.

| Test | Asserts |
|---|---|
| `RemoteIdTest` | `remoteId` is always negative, stable across calls, distinct across a sample, and never collides with a plausible MediaStore id |
| `StreamPickTest` | Best-audio selection picks the highest-bitrate m4a/opus from a fake stream list, and returns null rather than throwing when the list has no audio |
| `RemoteResolverTest` | A `vibe://` spec is rewritten, any other scheme passes through untouched, and a missing path segment fails rather than resolving to garbage |
| `DisplayTest` (extend) | Real YouTube title shapes — `Artist - Title (Official Video)`, `Artist - Title [Lyrics]`, topic-channel uploader names — split correctly through the existing `tidyNames` |

Everything network-facing is verified on a device against the phase gates above, because a mocked
YouTube tests the mock.

## Risks

| Risk | Shape | Response |
|---|---|---|
| PoToken cannot be made to work | Streams 403, feature is dead | Phase 4 is gated before anything depends on it. If it fails, stop and say so. |
| Extraction breaks upstream | Explore tab stops working on shipped APKs | Pin the version, expect to bump it, accept it as ongoing cost |
| R8 strips rhino | Debug works, release crashes | Keep rules in Phase 1; verify against a **release** APK, not debug |
| Desugaring misconfigured | `NoClassDefFoundError` on API 24-25 only | Test on an API 24 emulator specifically |
| Synthetic id collision | Two remote tracks share favorites | ~65k tracks before it is likely; documented ceiling and upgrade path in the code |
| Download corrupts on interruption | A truncated file indexed as music | `IS_PENDING` until complete; delete partials in `finally` |
| Legal | ToS and copyright | Raised, understood, accepted by the author before this spec |

## Files

**New**

```
app/src/main/kotlin/me/vibe/data/remote/YouTube.kt
app/src/main/kotlin/me/vibe/data/remote/Downloads.kt
app/src/main/kotlin/me/vibe/playback/RemoteResolver.kt
app/src/main/kotlin/me/vibe/playback/DownloadService.kt
app/src/main/kotlin/me/vibe/playback/potoken/       (5 files, ported)
app/src/main/kotlin/me/vibe/ui/ExploreScreen.kt
app/src/main/kotlin/me/vibe/ui/Navigation.kt
app/src/test/kotlin/me/vibe/RemoteIdTest.kt
app/src/test/kotlin/me/vibe/StreamPickTest.kt
app/src/test/kotlin/me/vibe/RemoteResolverTest.kt
LICENSE
```

**Edited**

```
app/src/main/kotlin/me/vibe/data/Models.kt          two fields, uri and artUri
app/src/main/kotlin/me/vibe/App.kt                  NewPipe.init, two singletons
app/src/main/kotlin/me/vibe/playback/PlayerController.kt   register handed songs
app/src/main/kotlin/me/vibe/playback/PlaybackService.kt    resolving source, wake mode
app/src/main/kotlin/me/vibe/MainActivity.kt         shrinks; routing moves out
app/src/main/AndroidManifest.xml                    permissions, download service
app/build.gradle.kts                                dep, desugaring, Java 11
settings.gradle.kts                                 JitPack
app/proguard-rules.pro                              rhino, protobuf, timeago
README.md                                           the network claim, honestly
```

## Build order

Phases run in order. 1 through 4 are the risky half and each has a gate that can stop the project
before the expensive half begins. 5 through 8 are ordinary work once 4 holds.
