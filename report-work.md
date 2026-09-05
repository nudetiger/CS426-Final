# Learning Mocha — work report

CS426 final project. Local-first Android personal knowledge base with an AI learning assistant.

This file records what was built, by whom, how it was verified, and what is deliberately not
there. The formal deliverable is [`report/report.pdf`](report/report.pdf); this is the engineering
log that sits next to it.

- **Repository:** Android app (`app/`, Kotlin + Java, XML Views, Room, minSdk 24) and a stateless
  Node/Express gateway to DeepSeek (`backend/`).
- **Group:** 24125006 Nguyen Anh Dung · 24125009 Le Huu Hoa.

---

## 1. Deliverable status

| Deliverable | Status | Where |
| --- | --- | --- |
| Full source code | Done | repository root, `.git` history included |
| Installable release APK, API 24+ | Done — signed, install-tested on an emulator | `app/build/outputs/apk/release/app-release.apk` |
| Report PDF, 10–30 pages | Done | `report/report.pdf` |
| README with members, build instructions, test account | Done | [`README.md`](README.md) |
| Demo video link | **Outstanding — yours to record** | `video/demo-link.txt` |
| Submission archive | Built by script once the video link is in | `tools/package-submission.ps1` |

**The one thing left to do:** record the 5–10 minute demo video, upload it (unlisted YouTube or
Google Drive, shared so anyone with the link can view), and paste the URL into
`video/demo-link.txt`. `video/demo-link.txt` already contains a shot-by-shot running order that
matches the report. The packaging script now **refuses to build the archive** while that file still
holds the placeholder, so it cannot be shipped by accident.

Then run, from the repository root:

```powershell
.\gradlew.bat assembleRelease
powershell -ExecutionPolicy Bypass -File tools\package-submission.ps1
```

which produces `dist/24125006_24125009.zip` in the required layout, with a secret sweep that fails
loudly if a `.env`, a keystore or an API-key-shaped string ever reaches the staging directory.

---

## 2. What the app does

Branches, folders and subfolders of Markdown posts, with create, rename, move, drag-to-reorder and
cascading delete. Posts carry `[[wiki-links]]`, backlinks, related posts, tags, a learning status,
a favourite flag, external references, YouTube resources that play in-app, and dictionary entries in
two scopes (global and per-post). A force-directed knowledge graph draws the whole library or one
post's neighbourhood. FTS4 search covers titles, content, tags, dictionary terms and resources.

The AI assistant runs in four modes (Answer · Suggest · Modify · Organize). It reads the library
only through seven read-only context tools, and it **cannot write to the database at all**: it emits
a batch of structured actions, the device validates them against live data, the user reviews and
edits them item by item, and one Room transaction applies the approved subset atomically.

Everything except the text sent to the assistant stays on the device. There are no accounts, no
sync, and no analytics.

---

## 3. Work division

The split follows the seam in the architecture: one side owns the local knowledge base, the other
owns everything the assistant touches.

| Student ID | Name | Delivered |
| --- | --- | --- |
| 24125006 | Nguyen Anh Dung | Project scaffold, Gradle/AGP setup, mocha design system (tokens, DayNight themes, typography). Room schema and all migrations, the single-table node model, DAOs and repositories. Tree operations: create, rename, move with cycle checking, drag reorder, cascade delete. Browse, Home hub, reader and Markdown editor with preview. Wiki-link parsing, backlinks, related posts, rename propagation. FTS4 search with filters, tags, favourites, status, dictionary storage. Knowledge-graph screen and the force-directed layout. Backup export/import, settings, theming, backup reminder. Release signing, R8 rules, launcher icon, packaging script. |
| 24125009 | Le Huu Hoa | The Node/Express DeepSeek gateway: endpoint contracts, prompt construction, the four modes, error normalisation, SSE streaming. The model-independent action protocol (14 operations), its parser and the on-device validator. The seven read-only context tools and the multi-round context loop. The transactional action executor and undo snapshots. Chat UI with streamed replies and the privacy disclosure. The review-changes screen: per-action selection, tree preview, diffs, destination picker, edit-before-save, destructive confirmation. In-app YouTube playback, dictionary screen, unit and instrumented tests, the report and the demo video. |

Done together: the design decisions recorded in `docs/plan.md` (storage model, action protocol, and
the explicit list of what *not* to build in v1); a code-review pass at the end of every phase against
the rules in `AGENTS.md`; the final audit described in §5; and manual testing on the emulator.

---

## 4. How it was built — phase by phase

Each phase ends with a working, submittable app, and each one wrote its prep notes for the next into
`docs/plan.md`, so the reasoning is visible in the diff rather than only in the final state.

| Phase | Contents |
| --- | --- |
| 0 | Project base: Android shell (Kotlin + Views, mocha theme, five tabs), Express gateway skeleton, plan |
| 1 | Core knowledge base: Room `nodes` tree, Browse CRUD/drag/swipe, reader + editor (Markwon), Home hub, seed data |
| 2 | Knowledge features: links/backlinks/related, tags, favourites, status, dictionary, YouTube resources, FTS4 search; DB v2 migration |
| 3 | AI assistant: chat sessions, context tools, action protocol, validator, executor, review screen |
| 4 | Polish: backup/export/import, settings, theming, backup reminder, empty/error/offline states |
| 5 | Knowledge graph, SSE streaming, in-app YouTube player, tags screen, release signing, R8 rules, report, packaging |

Three features that the plan had explicitly ruled *out* of version 1 were built in phase 5 once the
graded core was safe: the knowledge graph, streamed AI replies, and in-app video playback.

---

## 5. Final review pass

Before submission the codebase was audited against the concept document (`docs/idea.md`) and the
grading criteria, with every finding re-checked against the code before anything was changed. The
audit found problems that testing the happy path never would.

### Found by running the app

Asked in Modify mode to build a Distributed Systems learning path, the model returned seven
operations — one of them using an operation name that does not exist in the protocol. The validator
rejected it correctly, but because every row arrived ticked, that one invented name **disabled Apply
for the whole batch**: six good changes were unreachable because of one bad line.

Unsupported operations now arrive unticked with a plain explanation on the row, so a model that
improvises costs the user that single change. The gateway prompt was also tightened to state that the
`op` field must be one of the fourteen names and that inventing one simply loses the change.

### Fixed

| Area | Problem | Fix |
| --- | --- | --- |
| AI review | One invalid action disabled Apply for the entire batch | Unsupported ops arrive unticked with an inline explanation |
| AI executor | `update_post` carrying tags **replaced** the post's whole tag set, silently dropping the user's own tags | Tags are merged, not replaced |
| AI review | Proposed tags and status were applied but never displayed | Both now shown on the action row |
| Backup | Merge-importing the same file twice created duplicate post titles — and titles are how wiki-links and AI actions address posts | A colliding **post** gets a distinct title (`… (imported)`), and incoming bodies are rewritten so the imported set links to its own copies. Container titles are not de-duplicated: nothing addresses a branch or folder by title |
| Tags | Removing a post's last tag left the tag behind forever, listed as "0 posts" and reported to the assistant | Empty tags are purged on tag change and on delete |
| Architecture | `TagsViewModel` read a DAO directly — the only place breaking the layering rule — and counted posts one query per tag | Goes through the repository with a single grouped query |
| Privacy | The in-app privacy text did not mention that a titles-only outline of the library is sent with every AI request | Wording corrected |
| Settings | A gateway address typed without a scheme was stored, silently ignored, and reported unreachable forever | Validated on save, with a visible rejection |
| UI state | Two error-state Retry buttons did nothing; several lists lost scroll position on rotation | Retry re-subscribes; adapters defer state restoration |
| Protocol | A reference to a branch or folder could be used where a post was required; a reference with stray whitespace passed validation then failed inside the transaction | Both rejected at validation |
| Design system | One raw `fontFamily` in a layout | Moved into a `TextAppearance.Mocha.Code` token |
| Graph | Node labels sat flush against the window edge and read as clipped, and two labels could overlap into an unreadable smear | Inset to the viewport padding; labels now draw best-connected first and one that would land on a label already placed is dropped |
| Packaging | The script would ship a placeholder demo link with only a warning | It now fails |

### Second pass — a lint run over the finished code

The audit above was read against the source. Running Android Lint afterwards found what reading
does not: two errors and a set of grammar defects that only appear at a particular count.

| Area | Problem | Fix |
| --- | --- | --- |
| Wording | Eight count strings were written with a bare `%d`, so a library of one read "Exported 1 posts", "1 posts · 1 connections", "1 of 1 changes selected" | Graph, settings and review counts are `<plurals>`; the two-quantity graph captions are assembled from a posts plural and a connections plural instead of one fixed sentence |
| Accessibility | `GraphView` recognises taps through a `GestureDetector`, so no click ever reached View's own machinery and TalkBack could not announce one | `performClick()` is overridden and called from the tap handler |
| Lint noise | Both lint *errors* were guarded false positives — the notification permission is checked in `canPost()` one call away, and the WebView's height is measured to 16:9 at runtime — and the chat bubbles' one-sided gutter is deliberate | Suppressed at the exact site with the reason written next to it, so a real regression in either place is visible again |
| Packaging | The requirements want the demo link in `README.md` too, which meant pasting the same URL in two places | `video/demo-link.txt` stays the single source of truth and the packaging script substitutes it into the staged README, failing loudly if the placeholder is gone |
| Dead code | Four strings left behind by later renames (`review_undone`, `browse_create`, `action_move`, `dictionary_empty`) | Removed |

Lint now reports **0 errors**. The 59 remaining warnings are deliberate and were left alone:
available dependency upgrades (frozen for the submission), the documented cleartext base config
the gateway needs, overdraw from the mocha background painted under a themed window, and unused
entries in the dimension and text-appearance scales, which are a design token set rather than
dead code.

### Confirmed sound

The audit also checked, and found genuinely solid: the AI write path (one call site, one Room
transaction, no reachable partial apply); the validator's modelling of the batch's own future state;
the hand-written migrations with no destructive fallback and instrumented tests against exported
schemas; content-backed FTS4 that cannot desync; coroutine and lifecycle hygiene across all fragments;
state restoration in the editor, Browse and the graph; runtime notification permission handling on
API 33+; the signed release build with no debuggable flag and only `INTERNET` and `POST_NOTIFICATIONS`;
and no secret anywhere in git history.

---

## 6. Verification

- **Unit tests — 125 across 15 classes, all passing** (JVM, no device): the framework-free Java
  utilities in `util/` and the AI protocol objects — action parsing, validation and labelling, the KB
  index, SSE framing, the streaming answer extractor, backup round trip, force layout, tree rules, the
  Markdown link parser, the FTS query builder, gateway-URL normalisation, and import title
  de-duplication. Run with `.\gradlew.bat test`.
- **Android Lint — 0 errors, 59 warnings**, all of the warnings reviewed and deliberate (see the
  second-pass table in §5). Run with `.\gradlew.bat lintDebug`.
- **Instrumented tests — 2, both passing on a Pixel emulator (API 36):** Room migration tests using
  `MigrationTestHelper` against the exported schemas in `app/schemas/`, covering the 1→3 and 2→3 paths
  with real rows. Run with `.\gradlew.bat connectedDebugAndroidTest`. Note: uninstall the app first if
  a *release*-signed build is on the device — the tests install a debug-signed APK, and the signature
  clash reports as a confusing "process crashed".
- **Manual, on a Pixel emulator at API 36, using the signed release APK:** first-launch seeding, tree
  CRUD and drag reorder, reader with wiki-links and backlinks, editor, FTS search, the knowledge
  graph, dictionary, favourites, theme switching, the export/import round trip, the backup reminder,
  and a **live DeepSeek round trip** through the local gateway — an Answer-mode question, then a
  Modify-mode learning path reviewed and applied into the tree. The offline path was verified by
  stopping the gateway: Settings reports "Could not reach the gateway. Your library still works.",
  the chat screen shows its banner with a Retry, and every other feature is unaffected.

Not covered: no Espresso/UI instrumentation tests, no automated test of the AI round trip (it needs a
live key), and no pass on real tablet hardware.

---

## 7. Known limits

Stated plainly, because the report is graded partly on an honest self-assessment.

- Posts are Markdown text only — no images, attachments or rich-text editing.
- YouTube plays from a resource card in a bottom sheet, not embedded in the article flow.
- Drag and drop reorders siblings; moving between containers is a picker dialog.
- The review screen edits titles and content; proposed tags and status are shown but not editable.
- The assistant can delete posts, never branches or folders — deletion cascades and cannot be undone.
- Organize mode's duplicate detection rests on the model; there is no on-device similarity check.
- Undo after an AI batch is in-memory, single-level, per-batch, and never resurrects deleted posts.
- Tablet support is three dimension overrides above 600 dp, not a two-pane layout, and was never
  verified on real tablet hardware.
- R8 is deliberately off in the release build: Gson reflects over the AI protocol classes, and a wrong
  keep rule there fails *silently*. The keep rules are written and correct, but APK size is not graded
  and a silently broken assistant would be.
- The assistant needs `backend/` running with a DeepSeek key. Everything else works without it.

---

## 8. Repository notes

- `AGENTS.md` holds the non-negotiable rules: local-first, AI writes only through `ActionExecutor`,
  strict Fragment → ViewModel → Repository → DAO layering, Kotlin for Android-facing code and Java for
  framework-free utilities, design tokens only in layouts.
- `docs/plan.md` is the implementation plan and the source of truth for architecture and phases.
  Where the shipped code diverges from it, the code is the truth; §11 of the report lists the
  divergences that matter.
- `backend/.env` holds a live DeepSeek key. It is git-ignored and never enters the archive, and the
  packaging script fails if an API-key-shaped string reaches the staged source. **Rotate the key**
  before sharing the machine or the repository.
