# Metadata repair: parser, batch tag write, rename, CSV round trip

Date: 2026-08-17
Status: approved for planning

## Problem

A 242-file device library, downloaded with yt-dlp, has no usable tags. Every row reads
`artist=<unknown>`, `album=download`. The title is the original filename:
`Sia_-_Snowman(128k).m4a`.

`Display.kt` already repairs this **for display**, at query time. Nothing is written back, so
the files themselves stay untagged — every other app on the phone still sees `<unknown>`, and
vibe.me redoes the work on every library read.

Measured breakdown of the 242 files:

| Class | Count | Fix |
|---|---:|---|
| `Artist - Title (junk)` | 154 | Existing parser already handles it |
| `Title \| Artist \| Label` (pipe, arrives as `___`) | ~50 | New parser rule |
| Bare title, no artist present anywhere | ~18 | Manual lookup via CSV |
| Not music (ringtones, `viber_message`, dance clips) | ~10 | Exclude from library |
| Duplicate pairs (same track at two bitrates) | 5 pairs | Surface, let the user delete |

The manual-lookup residue is ~30 files, not 242. Build order follows from that: parser first,
CSV loop last, so the human only ever sees what no rule can crack.

## Goals

1. Write correct tags to the files themselves, in one batch, with one consent prompt.
2. Rename files on disk to `Artist - Title.ext`, reversibly.
3. Export the unfixable residue as CSV, let an external LLM fill it in, import it back.
4. Never lose data. Every destructive step is previewed, journalled, and undoable.

## Non-goals

- No network access. The app gains no INTERNET permission; the LLM round trip is manual,
  carried by the user through the share sheet. The "cannot phone home" property is preserved
  and is the reason this design is shaped as a file round trip rather than an API call.
- No online metadata lookup (MusicBrainz, Last.fm). That is what the CSV hand-off replaces.
- No album art fetching.

## Phase 1 — Pipe-separated artist rule

### The ordering bug

`tidyNames(rawTitle, rawArtist)` currently calls `tidyString` first, which runs
`MULTISPACE.replace(it, " ")`. A YouTube title `Title | Artist | Label` reaches the filesystem
as `Title___Artist___Label` (yt-dlp maps `|` and its surrounding spaces to underscores). By the
time `ARTIST_SPLIT` runs, that is `Title Artist Label` — one space, no boundary, information
already gone.

So segmentation must happen on the **raw** string, before any tidying.

### The two shapes are inverted

- Dash: `Artist - Title` — artist LEFT, title RIGHT. Already implemented.
- Pipe: `Title | Artist | Label` — title LEFT, artist SECOND.

Confusing them swaps the fields on ~50 tracks. The rules are separate and must be tested
against each other.

### Rule

In `tidyNames`, when the artist tag is unknown:

1. Split the raw title on a run of 3+ underscores, or a literal `|`, `｜`, or `•` with
   optional surrounding underscores/spaces.
2. If 2+ segments result, tidy each segment independently (`tidyString`, dropping trailing
   word noise).
3. Drop segments left without substance — `Official Lyrical Video` tidies to empty and is
   not an artist.
4. First surviving segment is the title, second is the artist. Ignore the third onward: it is
   the label, the film, or a description.
5. Apply the existing `length <= 40` plausibility guard to the artist segment.
6. If fewer than 2 segments survive, fall through to the existing dash rule unchanged.

Dash rule wins when both could apply — it is the stronger signal and covers the larger set.

### Tests

New cases in `DisplayTest`, drawn verbatim from the real library:

- `Ugh,_That_Look_Tho___Auric_Veil___Funny___Relatable_Song_About_Falling_for_a_Glance`
  → title `Ugh, That Look Tho`, artist `Auric Veil`
- `Farkanna_Hola___Official_Lyrical_Video___Prod._by_Tunna_Bell_Thapa_#shotoniphone`
  → title `Farkanna Hola`; noise segment dropped
- `Sajni__Song___Arijit_Singh,_Ram_Sampath___Laapataa_Ladies____Aamir_Khan_Productions`
  → title `Sajni (Song)`, artist `Arijit Singh, Ram Sampath`
- Regression: every existing dash case still passes, unchanged.
- Regression: `Video Games`, `The Lazy Song`, `Audioslave`, `Official Secrets` still survive.

Pure functions, pure unit tests, no device needed. This phase ships alone.

## Phase 2 — Batch tag write

### Mechanism

`TagWriter` already handles the scoped-storage dance: copy to cache, edit with jaudiotagger,
stream back through `ContentResolver`, and return an `IntentSender` when consent is needed.
What it lacks is a batch path.

New `MetadataRepair` class in `data/`:

- Computes a `Change` per song: current tags vs. what `tidyNames`/`tidyAlbum` derive.
- Skips songs where nothing would change.
- Requests consent for **all** affected URIs at once via
  `MediaStore.createWriteRequest(resolver, uris)` — one system dialog, not 242.
- Applies sequentially off the main thread, reporting progress.
- Collects per-file failures rather than aborting the batch; a damaged header on one file must
  not stop the other 241.

### Preview

A screen listing every pending change as `old → new`, grouped by field, with a count and a
per-row checkbox. Nothing is written until the user confirms. This is the only thing standing
between a parser regression and 242 wrongly-tagged files, so it is not optional.

### What gets written

`TITLE`, `ARTIST`, `ALBUM`. `ALBUM_ARTIST`, `GENRE`, `YEAR`, `TRACK` are left alone in this
phase — the parser cannot derive them, and writing a guess is worse than writing nothing.

**Display sentinels must not reach the tags.** `tidyNames` falls back to the literal
`"Unknown artist"` and `tidyAlbum` to `"No album"`. Those exist to fill a row in the UI. Written
to disk they are worse than the empty tag they replace: every other app on the phone would then
show a real artist named "Unknown artist", and the emptiness that marks a song as *needing* repair
would be erased — making the CSV residue unidentifiable.

So `MetadataRepair` must not consume the display helpers directly. It needs the underlying result
plus a flag for whether a real value was found — either a `tidyNamesRaw` returning nulls that
`tidyNames` wraps with the sentinels, or a small `Derived(title, artist?, album?)` type. A song
with a null artist is written title-only, is not renamed, and is exported to the CSV.

This is the single most likely way this feature damages the library, and the preview screen will
not catch it — `"Unknown artist"` looks plausible in a preview row. It needs a unit test asserting
neither sentinel ever appears in a planned `Change`.

## Phase 3 — Rename on disk

Chosen explicitly over tags-only. It costs an undo path; the undo path is therefore in scope.

### Mechanism

Rename via `ContentResolver.update()` setting `MediaStore.MediaColumns.DISPLAY_NAME`. MediaStore
performs the filesystem rename itself. Consent piggybacks on the same `createWriteRequest`
batch as Phase 2.

**The row `_ID` survives a `DISPLAY_NAME` update.** This is what makes the feature safe:
`UserState.favorites`, `playCounts`, `lastPlayed`, `queue`, and `Playlist.songIds` are all
keyed on `_ID`, so a rename cannot orphan a playlist or lose a play count. `Song.path` is
recomputed on the next library read. Verify this assumption on-device before shipping the
phase — the whole safety argument rests on it.

### Target name

`Artist - Title.ext`, from the repaired tags.

Constraints, all enforced before any write:

- Strip `/` and NUL. Collapse whitespace.
- Cap at 255 **bytes**, not characters — several titles are Devanagari, where one character
  costs three bytes. Truncate on a character boundary.
- Collision within the batch, or with an existing file: append ` (2)`, ` (3)`.
- A song whose artist is still `Unknown artist` is not renamed. `Unknown artist - Jhol.m4a`
  is worse than what it started with.

### Undo

Before applying, write a journal to app-private storage (`repair-journal.json`) and flush it
**before** the first write, so a crash mid-batch still leaves a complete record of what was
attempted.

The journal covers **tags and renames together**, not renames alone. A batch is one user action
and has to reverse as one — undoing a rename while leaving wrong tags behind is a worse state
than either the before or the after. Each entry records the prior values:

```
{ id, oldName, newName, oldTags: {title, artist, album}, newTags: {...}, appliedAt }
```

Capturing `oldTags` costs one extra `TagWriter.read()` per file during planning, which the
preview screen already needs in order to show `old → new`. No additional work.

An "Undo last repair" action in settings reverses the journal newest-first, restoring both name
and tags. One journal, one level of undo — enough to recover from a bad batch, and not a version
control system.

## Phase 4 — CSV round trip

For the ~30 files no rule can crack.

### Export

Share-sheet export of a CSV. UTF-8 **with BOM** — Excel and Google Sheets mis-decode Devanagari
without it, and this library is full of it. RFC 4180 quoting.

Columns:

| Column | Role |
|---|---|
| `id` | MediaStore `_ID`. The join key. Never edited. |
| `file` | Current filename. Context for the LLM, and a checksum on import. Never edited. |
| `title`, `artist`, `album`, `year` | Pre-filled with the parser's best guess. Edited. |
| `action` | `tag`, `skip`, or `hide`. Defaults to `tag`. |

Pre-filling matters: the LLM corrects rather than invents, and the user can see at a glance
which rows the parser already got right.

Default export scope is "only tracks still missing an artist" — the ~30. Exporting all 242 is
available but not the default.

`action=hide` is how the ringtones and `viber_message.mp3` leave the library. It adds
`UserState.hidden: List<Long>`, filtered out at the `MediaStoreLibrary` boundary.

### Import

The CSV comes back from an LLM. It is untrusted input and gets validated as such:

- Reject the file whole if headers are missing or malformed. Partial application of a
  structurally broken file is worse than none.
- Per row, drop with a reported reason: unknown `id`; `file` not matching that id's current
  name (the library changed under the CSV — stale, do not guess); `action` not in the enum;
  `year` not 4 digits; any field over 500 chars; embedded NUL or newline.
- Report a summary before applying: N rows applied, M skipped and why.
- Then route into the same Phase 2/3 preview and journal. Import has no privileged path to
  the filesystem — it produces the same `Change` list the parser does.

## Data flow

```
MediaStore ──► MediaStoreLibrary ──► tidyNames/tidyAlbum ──► Song (display)
                                              │
                                              ▼
                                    MetadataRepair.plan()
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
              CSV export              Preview screen              CSV import
            (residue only)          (old → new, per row)        (validated, merged)
                                              │
                                    user confirms
                                              │
                                    createWriteRequest (one dialog)
                                              │
                              ┌───────────────┴───────────────┐
                              ▼                               ▼
                       TagWriter.write()            DISPLAY_NAME update
                                                     (journalled first)
```

## Files

| File | Change |
|---|---|
| `data/Display.kt` | Pipe segmentation rule |
| `test/DisplayTest.kt` | New cases + regressions |
| `data/MetadataRepair.kt` | New. Plan/apply/journal/undo |
| `data/MetadataCsv.kt` | New. Export, parse, validate |
| `test/MetadataCsvTest.kt` | New. Validation and rejection cases |
| `data/TagWriter.kt` | Batch consent path |
| `data/UserData.kt` | `hidden: List<Long>` |
| `data/MediaStoreLibrary.kt` | Filter hidden |
| `ui/SettingsScreen.kt` | Entry points |
| `ui/MetadataScreen.kt` | New. Preview and confirm |

Under the 500-line cap per file; `MetadataRepair.kt` is the one at risk and splits if it grows.

## Risks

| Risk | Mitigation |
|---|---|
| Display sentinels written as real tags | Separate derivation from display; unit test asserts neither string reaches a `Change` |
| Parser regression mis-tags the library | Preview before write; snapshot tests on real filenames |
| Rename orphans playlists/queue | `_ID` is stable across rename; verified on-device before ship |
| Bad CSV scrambles filenames | Validation, preview, journal, one-level undo |
| Devanagari filename exceeds FS limit | Byte-capped truncation on a character boundary |
| Batch dies mid-way | Journal flushed before first write; per-file failures collected, not fatal |

## Order

Phase 1 → 2 → 3 → 4. Each ships independently and is useful on its own. Phase 1 alone fixes
roughly 85% of the library's display; Phase 2 makes it permanent.
