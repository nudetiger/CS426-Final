# Learning Mocha — Implementation Plan

**Course:** CS426 Final Project · **Deadline:** 2026-09-05 23:59 (hard)
**Concept:** Local-first Android personal Wikipedia + learning tracker + knowledge graph + AI learning assistant.
**Design reference:** `CS426-Midterm` (CodeCup) — warm cream/brown coffee-shop aesthetic, Comfortaa type, calm DayNight chrome.

> Guiding rule: **every phase ends with a working, submittable app.** Features are layered in
> priority order so that if time runs out, the latest completed phase is still a coherent product.

---

## 1. Overall architecture

```
┌─────────────────────────────────────────────────────┐
│ Android app (source of truth, works fully offline)  │
│                                                     │
│  UI (XML Views) → ViewModel → Repository → Room     │
│                         │                           │
│                    AI Action Engine ── validates,   │
│                    previews, executes in one Room   │
│                    transaction after user approval  │
└──────────────┬──────────────────────────────────────┘
               │ HTTPS (only when user chats)
┌──────────────▼──────────────────────────────────────┐
│ Tiny backend gateway (Node + Express, ~150 lines)   │
│  POST /v1/chat  → holds DEEPSEEK_API_KEY, builds    │
│  request, passes through streaming, normalizes      │
│  errors. No database. No user content stored.       │
└──────────────┬──────────────────────────────────────┘
               │
        DeepSeek API (chat completions)
```

- The Android app is the **only** place knowledge lives.
- The backend is a **dumb key-holding proxy** with light prompt/response shaping.
- The app detects backend failure and keeps every non-AI feature fully usable.

## 2. Android architecture

Single-activity + Navigation Component, MVVM, repository pattern, unidirectional data flow:

```
ui/ (Fragment + Adapter + XML)  → observes StateFlow/LiveData
viewmodel/ (ViewModel)          → exposes state, calls repositories
data/
  local/ (Room: entities, DAOs, AppDatabase, FTS, seed)
  repo/  (TreeRepository, PostRepository, SearchRepository, AiRepository…)
  prefs/ (SettingsStore via SharedPreferences/DataStore)
ai/
  protocol/ (action DTOs, parser, validator)
  engine/   (ActionExecutor: Room transaction; ContextTools: KB queries for AI)
  chat/     (ChatRepository → backend API via Retrofit)
net/    (Retrofit/OkHttp setup, DTOs, offline detection)
backup/ (export/import)
util/   (Java helpers — see §3)
```

Rules: Fragments never touch Room directly; all DB work on `Dispatchers.IO`; all AI writes go
through the Action Engine, never straight to DAOs.

## 3. Java / Kotlin responsibility split

- **Kotlin (primary, ~85%)**: all Activities/Fragments, ViewModels, Room entities/DAOs,
  repositories, AI action engine, chat, navigation, coroutines/Flow.
- **Java (framework-free utilities, ~15%)**: `MarkdownLinkParser` (extracts `[[Post Title]]`
  links + YouTube URLs), `FtsQueryBuilder` (sanitizes user input into MATCH queries),
  `ExportJsonWriter`/`ImportJsonReader` (streaming backup), `TextDiff` (preview diffs).
  Pure-Java classes are easy to unit-test and give the required mixed-language codebase a clean,
  defensible boundary: *Android-facing = Kotlin, pure logic = Java.*

## 4. Local storage

**Room over SQLite — one database, no file-based post storage.** Simplest reliable option:
transactions, migrations, FTS, observable queries, easy single-file backup.

Storage decisions:

| Data | Where |
|---|---|
| Tree + posts | `nodes` table (single-table inheritance, see §5) — **implemented in Phase 1** |
| Search index | FTS4 virtual table via Room `@Fts4` entity (Room 2.6 has **no FTS5 annotation**; corrected from the first draft of this plan) |
| Internal links/backlinks | `links` edge table (backlink = reverse query, no duplication) |
| Settings | SharedPreferences (theme, backend URL, motion) |
| DB file itself | Backup = export to JSON (not raw file copy) for forward compatibility |

## 5. Data models

```
nodes          id PK | parentId FK→nodes (null=root branch level) | type: BRANCH|FOLDER|POST
               | title | content (markdown, posts only) | status: NONE|READING|IN_PROGRESS|FINISHED
               | favorite: 0/1 | orderIndex | createdAt | updatedAt
links          id PK | fromPostId FK | toPostId FK | anchorText  (derived from markdown)
prerequisites  postId FK | requiresId FK  (PK = pair) — user-declared "read that first"
tags           id PK | name UNIQUE
post_tags      postId FK | tagId FK  (PK = pair)
dictionary     id PK | postId FK NULL (null = global) | term | definition | meaningVi
resources      id PK | postId FK | type: YOUTUBE|ARTICLE|BOOK|OTHER | title | url
posts_fts      FTS4(title, content) via @Fts4(contentEntity=Node) — Room auto-syncs
chat_sessions  id PK | title | createdAt        (local chat history)
chat_messages  id PK | sessionId FK | role | text | actionsJson NULL | status
```

Why one `nodes` table for branch/folder/post: identical tree operations (create/rename/move/
reorder/delete cascade) with zero joins; `content/status` are simply unused for containers.

## 6. Knowledge graph / link system

- Author writes `[[Spring Boot]]` in markdown. `MarkdownLinkParser` (Java) extracts targets on save.
- Save pipeline: parse links → resolve titles → node ids (create "ghost" placeholder posts
  optionally off; v1: unresolved links render as plain text + "not created yet" toast) →
  replace rows in `links` inside the same transaction as the post update.
- **Backlinks**: `SELECT … WHERE toPostId = :id` — always correct, never stored twice.
- **Related posts**: shared tags + shared outbound links (simple scoring query).
- Reader renders `[[…]]` as clickable spans → navigate to post.
- v1 graph visualization: **none** beyond backlinks/related lists (see §26 not-in-v1).

## 7. Content format

- Storage: **plain Markdown** + `[[wiki-links]]` extension + YouTube URL lines.
- Rendering: **Markwon** (offline, View-based) + linkify for `[[…]]` spans; YouTube shown as a
  tappable thumbnail card (opens in browser/YouTube app — no embedded player in v1).
- Editor: EditText (monospace-ish, source mode) + **Preview tab**; toolbar: bold, italic, heading,
  list, link, `[[…]]` picker (searches existing posts), dictionary term, save.
- Status chip (Reading / In Progress / Finished), favorite star, tag chips in reader header.

## 8. Search

- FTS4 MATCH across title/content (tags matched via normal join); ranked by `bm25`, snippets.
- Filters: type (post/branch), tag, status, favorites-only.
- Search screen doubles as **quick-open** (like Ctrl+K): title matches first.
- Also indexed: dictionary terms (searching "RAG" finds the entry and its post).
- Debounced input (300 ms), Flow-based results.

## 9. Dictionary

- Two scopes: **global** (`postId = null`) and **per-post**.
- Reader: bottom strip / "Terms" sheet lists terms found in the current post; tapping a term shows
  a small popup (EN definition + VI meaning) without leaving the page.
- Editor toolbar "Add term" creates an entry; AI can also propose entries.
- Dedicated Dictionary screen: alphabetical list, search, scope filter.

## 10. AI chat

- Chat tab: session list + message bubbles, markdown-rendered AI replies, typing indicator,
  offline banner when backend unreachable.
- Chat modes (selector): **Answer · Assist**. Answer is read-only by contract; Assist may
  propose changes, which the user reviews before anything is written. (These were four
  chips — Answer/Suggest/Modify/Organize — until the last three were merged: they all
  produce the same reviewable actions batch, so the split only asked the user to
  pre-classify their own sentence.)
  Mode is sent to the backend and shapes the system prompt + whether actions are allowed.
- Messages + sessions persisted locally (`chat_sessions/chat_messages`) so history survives restarts.

## 11. AI context / tool system

Never send the whole KB. Two-round **structured-request protocol** over plain chat completions
(model-independent — works with any chat API):

1. App sends: system prompt (persona + action schema docs + mode) + **compact KB index**
   (tree of titles/ids only, capped ~2–4 KB) + user message.
2. DeepSeek replies with JSON envelope:
   - `{"type":"answer","text":…}` — render as chat.
   - `{"type":"context_request","queries":[{op,args}…]}` — app executes **ContextTools** locally,
     appends results as a tool message, calls API again (max 3 rounds, then force answer).
   - `{"type":"actions","summary":…,"actions":[…]}` — goes to the Action Engine (§13).
3. ContextTool ops (read-only, Kotlin): `search_posts`, `get_post`, `list_children`,
   `get_backlinks`, `search_dictionary`, `get_tags`, `get_related`.

## 12. Backend / DeepSeek API

Node 24 + Express, single responsibility: hold the key and shape requests.

```
POST /v1/chat
  body: { mode, messages[], kbIndex, toolResults?, userProfile? }
  → builds DeepSeek chat.completions request (model from env, default deepseek-chat)
  → non-streaming in v1 (streaming via SSE only if time permits)
  → returns { reply: <json-envelope>, usage } ; normalizes 4xx/5xx to { error, retryable }
GET  /v1/health → { ok, model }   (app pings for offline banner)
```

Config via `.env` (`DEEPSEEK_API_KEY`, `MODEL`, `PORT`). No DB, no logging of message bodies,
no auth (local dev tool; emulator reaches it via `10.0.2.2`, device via LAN IP).

## 13. AI action protocol

JSON envelope validated **on device** before anything executes:

```json
{ "type": "actions",
  "summary": "Create Distributed Systems branch with 4 posts",
  "actions": [
    {"op":"create_branch","title":"Distributed Systems","ref":"b1"},
    {"op":"create_post","parentRef":"b1","title":"Raft","ref":"p1",
     "content":"# Raft\n…","tags":["consensus"],"status":"READING",
     "icon":"book","color":"amber","nextRef":"p2"},
    {"op":"create_link","fromRef":"p1","toTitle":"Consensus"},
    {"op":"set_status","postTitle":"Spring Boot","status":"FINISHED"},
    {"op":"add_dictionary_entry","postRef":"p1","term":"Raft",
     "definition":"…","meaningVi":"…"}
  ] }
```

Ops v1: `create_branch, create_folder, create_post, update_post, move_post, delete_post,
create_link, remove_link, add_tag, remove_tag, set_status, set_favorite, add_resource,
add_dictionary_entry`. `ref` lets actions reference not-yet-created items within the batch.

Validation rules: schema check → name/collision check → parent existence → cycle check on moves
→ length/limits caps. Any failure = whole batch rejected with a readable error list.
**Model-independent**: the schema is plain JSON documented in the system prompt; swapping DeepSeek
means changing one URL/key.

## 14. Action validation & transactions

```
AI envelope → ActionParser (Kotlin, Gson) → ActionValidator (against live DB snapshot)
→ PreviewScreen (user approves) → ActionExecutor
   Room @Transaction / withTransaction { resolve refs → apply ops in order →
   rebuild FTS rows → return UndoSnapshot }
→ success snackbar with Undo (restores snapshot; simple in-memory, per-batch)
```

- All-or-nothing: one Room transaction per batch; crash mid-apply = automatic rollback.
- Undo v1: before-images of touched rows kept in memory for the session.

## 15. AI change-review UX

`ReviewChangesActivity`: summary header ("AI proposes 6 changes") + grouped list —
each row: icon, human description ("Create post *Raft* under *Distributed Systems*"),
tap to expand (full generated markdown preview / before-after diff for edits).
Checkbox per change (deselect = skip; transaction skips rejected ops and re-validates).
Buttons: **Apply selected** / **Discard all**. Same screen serves every Assist batch,
learning-path and post-generation flows.

## 16. AI-generated learning paths

Signature feature. Flow: user says "I want to learn Distributed Systems" in Assist mode →
AI uses context tools to inspect what exists → proposes a tree (branches→posts with short
descriptions) as an action batch → Review screen shows it as an **indented tree preview** →
Apply → posts are created with AI skeleton content + inter-post links + dictionary terms.

## 17. AI-generated posts

"Teach me Raft" → AI returns `create_post` with full markdown (headings, `[[links]]`, YouTube
resource lines, tags, dictionary entries) → Review screen shows the rendered article preview
+ metadata cards → Approve / Edit before saving / Reject. Suggested location (branch path)
is shown and changeable via a folder picker.

## 18. UI / navigation

Single `MainActivity`, bottom nav (5 tabs), Navigation Component:

- **Home / Knowledge Hub** — continue reading, recent, favorites strip, branch shortcuts.
- **Browse** — tree explorer (walk in/out, sort + filter, swipe a row to delete, breadcrumbs).
- **Search** — quick-open + filters.
- **AI** — chat list/conversation (+ mode selector).
- **Settings** — theme, backend URL, backup/export/import, about/privacy.

Stack destinations (nav graph): PostReader, PostEditor, Dictionary, TagDetail, Favorites,
ReviewChanges, ChatConversation. Drag-and-drop reorder in Browse (ItemTouchHelper);
dialogs/bottom sheets styled per design system. Every list has loading / empty / error /
offline states (graded criterion).

## 19. Design system (from CodeCup)

Reuse tokens, renamed `mocha_*`; Comfortaa bundled font; DayNight themes; 12dp cards / 16dp
feature cards; zebra list rows; low-dim bottom sheets; brown filter chips.

| Light token | Hex | Dark | Hex |
|---|---|---|---|
| `mocha_cream` (background) | `#FFF7F0` | | `#2A2420` |
| `mocha_cream_dark` (header band) | `#F3E6DA` | | `#1F1B18` |
| `mocha_brown` (primary) | `#6B4F3A` | | `#C9A68A` |
| `mocha_brown_deep` (primary variant) | `#4A3428` | | `#A8856B` |
| `mocha_sage` (secondary/success) | `#7A8F7A` | | `#9DB59D` |
| `mocha_surface` | `#FFFBF7` | | `#332C27` |
| `mocha_text_primary` | `#2C241C` | | `#F5EDE4` |
| `mocha_text_secondary` | `#6E6258` | | `#B8A99A` |
| `mocha_error` | `#B54A3C` | | `#D97B6C` |

Reader-specific: body text 16sp/1.5 line height, max content width ~560dp on tablets
(centered column), headings in brown, code blocks on `cream_dark`.

## 20. Backup / import / export

- **Export**: single `.mocha.json` (versioned envelope: nodes, links, tags, dictionary,
  resources, settings) via Storage Access Framework — Java `ExportJsonWriter` streams it.
- **Import**: pick file → validate version/schema → merge-or-replace dialog → transaction.
- Auto-backup reminder (weekly, local notification — also satisfies "device capability").

## 21. Offline behavior

- Everything except AI chat works with zero connectivity (local Room + local rendering).
- Chat screen: ping `/v1/health`; on failure show calm banner "AI unavailable — your library
  still works", queue disabled, retry button; last known answers remain readable.
- In-flight AI request fails → inline error bubble with retry, no crash, no data loss.

## 22. Privacy / security

- Leaves device: only chat messages + the context snippets the AI explicitly requested
  (shown subtly: "Shared with AI: 3 posts" chip per message).
- Never leaves: full DB, files, settings, anything the AI didn't query.
- API key lives **only** in backend `.env` (never in the APK / never in git).
- Report includes a privacy subsection (what/why/how) — cheap points, matches rubric.

## 23. Testing

Realistic scope for the timeline:
- **JVM unit tests** (fast, no emulator): `MarkdownLinkParser`, `FtsQueryBuilder`,
  ActionValidator (fake DAO), action batch executor ordering, export/import round-trip.
- **One instrumented smoke test**: DB migration + tree CRUD + FTS search (Room in-memory).
- Manual test matrix: fresh install → seed → create/rename/move/delete → link/backlink →
  search → AI answer/modify offline→online transition → export/import.

## 24. Performance

- Room indices on `parentId`, `title`, FTS for content; paged/limited tree queries (children only).
- Markwon rendering off main thread for large posts; RecyclerView DiffUtil everywhere.
- Cold-start: no work in `Application.onCreate` beyond DB lazy init.
- APK size: only needed Markwon artifacts; release build with R8 + resource shrinking.

## 25. Developer tooling

- `.cursor/rules` (or `AGENTS.md`) describing: architecture layers, DB schema, post format
  (`[[links]]`), AI action protocol, design tokens, "never write to DB outside repositories /
  never let AI touch DAOs" safety rules — so coding agents can modify the codebase safely.
- `backend/.env.example`, root `README.md` with build/run instructions (also a deliverable).
- Git from commit #1 (deliverable requires visible history).

## 26. Development phases

Time-boxed to the deadline (total ≈ 30 h including deliverables). Each phase = working app.

### Phase 0 — Base scaffold ✅ (this session, ~1–2 h)
Repo + git init; Android module (Kotlin+Java, Views, AGP 8.7.3/Gradle 8.9, minSdk 24, target 35);
mocha theme/tokens imported from CodeCup; single activity + bottom nav with 5 placeholder tabs;
backend Express skeleton with `/v1/health`; `README.md`, `.gitignore`, `.env.example`.

### Phase 1 — Core knowledge base ✅ (~5 h) → *gradable MVP*
Room schema (`nodes`), tree CRUD (create/rename/move/reorder/delete cascade), Browse tab,
PostReader + PostEditor (markdown + preview), Home hub with recents. Seed sample branch
("Getting Started") on first launch.
*Checkpoint: app installs, 4+ connected screens, persistent data — rubric-safe.*

### Phase 2 — Knowledge features ✅ (~5 h)
`links` + `[[wiki-link]]` parser + backlinks + related; tags; favorites; status; dictionary
(global + per-post, reader popup); resources (YouTube cards); FTS4 search + filters.

**Prep notes from the Phase 1 implementation (reviewed 2026-09-04):**
- **DB migration 1 → 2 is required** for `links`, `tags`, `post_tags`, `dictionary`,
  `resources`, `posts_fts`. Write a real `Migration(1,2)` — *no destructive fallback*:
  data loss is a grading-critical defect. Flip `AppDatabase.exportSchema` to `true`
  (+ `room.schemaLocation`) so the migration can be tested.
- **Already in schema, only UI wiring left:** `status` (reader status chip + editor picker)
  and `favorite` (reader star toggle, Favorites screen, search filter).
- **Already in UI, only wiring left:** editor `[[ ]]` toolbar button and seed content already
  contain wiki-links → build `MarkdownLinkParser.java` (extract `[[title]]` + YouTube URLs),
  resolve on save inside the post transaction, render clickable spans in the reader.
- **FTS correction:** use `@Fts4` Room entity (`posts_fts`, `contentEntity = Node::class`) —
  Room keeps the index in sync automatically; no triggers to maintain.
- Keep `touch()`-based recency (powers Home "recent"); Phase 2 adds nothing there.
- `fragment_post_reader.xml` relies on Material's transitive ConstraintLayout — builds fine;
  leave it (ponytail), but don't add more ConstraintLayout screens without the explicit dep.

### Phase 3 — AI ✅ (~6 h)
Backend `/v1/chat`; chat UI + local sessions; context tools; action protocol + validator +
transaction executor; ReviewChanges screen; modes Answer/Assist;
learning-path generation; AI post generation with preview.

**Prep notes from the Phase 2 implementation (reviewed 2026-09-04):**
- **Chat persistence → DB v3**: add `chat_sessions`/`chat_messages` (plan §5) as
  `MIGRATION_2_3` following the `MIGRATION_1_2` pattern; schema export is already on.
- **Networking is ready**: Retrofit/OkHttp/Gson present since Phase 0. Base URL constant
  `http://10.0.2.2:8787` for emulator (Settings toggle lands in Phase 4).
- **Backend is protocol-complete** (`prompts.js` documents the full action schema) — it only
  needs `DEEPSEEK_API_KEY` in `backend/.env` to run. Test the app against a *down* backend
  too: the offline banner is graded UX.
- **ActionExecutor must reuse repositories** (`PostRepository.createPost/savePost`,
  `TreeRepository.move/rename/delete`) so `KnowledgeSync` reindexing (links/tags/YouTube)
  stays consistent — never call DAOs directly (AGENTS.md rule 2).
- **Context tools map 1:1 onto existing code**: `search_posts`→SearchRepository,
  `get_post`/`list_children`→NodeDao, `get_backlinks`→KnowledgeDao, `search_dictionary`→
  KnowledgeDao, `get_tags`→KnowledgeDao, `get_related`→PostRepository.related.
- **kbIndex builder**: indented title tree from `NodeDao.getAll()`, cap ~4 KB.
- **Reuse UI machinery**: ListState, ChipBar, RowAdapter, and `WikiMarkdown`/Markwon for
  rendering AI markdown in chat bubbles and the review screen.

### Phase 4 — Polish ✅ (~4 h)
Backup/export/import; settings (theme toggle, backend URL); all empty/error/offline states;
dark theme pass; tablet width pass; notification backup reminder; performance pass.

**Prep notes from the Phase 3 implementation (reviewed 2026-09-04):**
- **`data/prefs/` and `backup/` are still empty directories** — all of Phase 4's storage work
  is greenfield. `androidTest/` is empty too (see the migration test below).
- **Backend URL setting needs an indirection.** `LearningMochaApp.chatRepository` is a
  `by lazy` val holding one Retrofit instance built from `ApiClient.DEFAULT_BASE_URL`
  (`http://10.0.2.2:8787/`). A user-editable URL should be an OkHttp interceptor that rewrites
  the host from `SettingsStore` per request — swapping the lazy repository at runtime would
  strand in-flight calls and any collected Flow. Cleartext is already allowed in the manifest.
- **Backup scope decision: chat history is NOT exported.** The `.mocha.json` envelope covers
  `nodes, links, tags, post_tags, dictionary, resources` + settings; `chat_sessions`/
  `chat_messages` are session scratch, and exporting them would leak conversation text into a
  file the privacy section (§22) promises stays local. `posts_fts` is never exported — it is a
  content-backed FTS4 index; rebuild it after import.
- **Import must not write DAOs directly.** Go through `PostRepository`/`TreeRepository` (or
  re-run `KnowledgeSync` afterwards) so links/tags/YouTube reindexing stays consistent —
  same rule that governs `ActionExecutor` (AGENTS.md rule 2).
- ~~**Remaining list states**~~ — *corrected during Phase 4*: every list screen (Home, Browse,
  Search, Chat list, ChatConversation, Dictionary, Favorites, TagDetail, Reader, Editor,
  Review) already binds `ListStateBinder`. Nothing to do here.
- **Instrumented migration test is now the only untested critical path**: `room-testing` is
  already a declared `androidTestImplementation` dep, so a `MigrationTestHelper` run is cheap.
  *Note: only `2.json`/`3.json` exist — schema export was switched on in Phase 2, so there is
  no `1.json` and a v1 fixture has to be built by hand.*
- **Decide R8 keep rules before Phase 5 flips `minifyEnabled`.** Gson reflects over
  `Envelope`, `KbAction`, `ContextQuery` and the `net/` DTOs; without keep rules the AI
  protocol fails *silently* (fields deserialize to null → every envelope looks like a plain
  answer). This is the single most likely way the release APK differs from debug.
- **Notification reminder**: `POST_NOTIFICATIONS` is already in the manifest, but it is a
  runtime permission on API 33+ — request it, don't assume it.
- **Undo limits to document in the report** (§14 promised "before-images", these are the real
  semantics): the snapshot is in-memory, per-batch, single-level, and restores
  title/content/status/tags/favorite/parent for *touched* posts only. `delete_post` is
  deliberately not undoable — deletion cascades to children and resurrecting the subtree is
  out of scope. The review screen only offers Undo after an Apply.

**Deviations from this plan introduced in Phase 3 (plan text left as-is, code is the truth):**
- Context rounds are capped at 4 in `ChatRepository.complete`, not 3 (§11). Only the most
  recent round's tool results are forwarded; earlier rounds survive as assistant turns.
- `create_post` with no `status` defaults to `READING`, not `NONE`.
- `ActionValidator` resolves titles created *earlier in the same batch*, not just live DB rows.
  Required because `backend/prompts.js` tells the model to address posts by `postTitle`, so
  "create post → tag it → add a term to it" is the normal shape of a learning-path batch.
- SSE streaming stayed out of scope (Appendix E), as expected.

### Phase 5 — Deliverables (~4 h, **start no later than ~6 h before deadline**)

**Prep notes from the Phase 4 implementation (reviewed 2026-09-04):**
- **R8 is the biggest remaining risk.** `minifyEnabled` is still `false`. Gson reflects over
  `ai/protocol` (`Envelope`, `ContextQuery`, `KbAction`) and the `net/` DTOs; obfuscating their
  field names makes every AI reply deserialize to nulls, which the parser then treats as a plain
  answer — the failure is **silent**, not a crash. Either ship the release build with
  `minifyEnabled false` (safe, larger APK) or add keep rules for those two packages and re-test
  an actual AI round trip on the release APK. Do not flip it and assume debug behaviour holds.
- **Signing**: nothing is configured yet — no keystore, no `signingConfigs`. Budget time for
  generating one and keep it out of git.
- **Verified working on the emulator** (Pixel 8, API 36) during Phase 4, so the demo video can
  rely on these: export → SAF file → import/merge (3 posts became 6, tree and FTS index intact),
  theme switching, and the weekly backup notification.
- **Report material already written down**: privacy wording lives in `settings_privacy_body`
  (strings.xml) and matches §22; undo limitations are listed in the Phase 4 prep notes above;
  the Phase 3 notes record where the code diverges from this plan.
- **Test story for the report**: 64 JVM unit tests (protocol validation/parsing, KB index,
  backup round trip, tree rules, markdown/FTS helpers) plus 2 instrumented Room migration
  tests covering 1→3 and 2→3 with real rows. Commands: `gradlew test`,
  `gradlew connectedDebugAndroidTest`.
- **Known gaps, worth stating honestly in the self-assessment** rather than hiding: no tablet
  hardware pass (only `values-sw600dp` gutters, unverified on a real tablet), `delete_post` is
  not undoable, and AI chat needs the local backend running with a DeepSeek key.

Signed release APK (API 24+, install-tested on emulator/device); `report.pdf` (10–30 pages:
topic, users, architecture, tech, setup, work-division, self-assessment); 5–10 min demo video
(all members speak) uploaded + `demo-link.txt`; assemble zip
`<id1>_<id2>_…/{README.md, src/ (with .git), apk/, report/, video/}`; submit form; save
confirmation email.

---

## Appendix A — Recommended final architecture

Kotlin-first MVVM Android app (XML Views, Room, Navigation, Markwon, Retrofit) + pure-Java
utilities + single-file-ish Node/Express DeepSeek gateway. Local-first, offline-capable,
AI writes only through validated, reviewable, transactional action batches.

## Appendix B — Repository tree

```
CS426-Final/
├── README.md  ├─ docs/{idea,final_requirements,plan}.md  ├─ .gitignore
├── settings.gradle ├─ build.gradle ├─ gradle.properties ├─ gradlew(.bat) ├─ gradle/wrapper/
├── app/
│   ├─ build.gradle ├─ proguard-rules.pro
│   └─ src/main/
│       ├─ AndroidManifest.xml
│       ├─ java/com/cs426/learningmocha/
│       │   ├─ LearningMochaApp.kt ├─ MainActivity.kt
│       │   ├─ ui/{home,browse,search,chat,settings,reader,editor,review,dictionary}/…
│       │   ├─ viewmodel/…
│       │   ├─ data/{local/{entity,dao,AppDatabase,Seed},repo,prefs}/…
│       │   ├─ ai/{protocol,engine,chat}/…
│       │   ├─ net/{ApiClient, dto}/…
│       │   ├─ backup/…
│       │   └─ util/{MarkdownLinkParser.java, FtsQueryBuilder.java,
│       │              ExportJsonWriter.java, ImportJsonReader.java, TextDiff.java}
│       └─ res/{layout, values, values-night, font, drawable, menu, navigation}/…
└── backend/
    ├─ package.json ├─ server.js ├─ prompts.js ├─ .env.example └─ README.md
```

## Appendix C — Core API endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/health` | liveness + model name (offline detection) |
| POST | `/v1/chat` | `{mode, messages, kbIndex, toolResults?}` → `{reply}` envelope |

## Appendix D — AI interaction flow

```
user message → app: build kbIndex → POST /v1/chat → DeepSeek
   ← answer              → render chat bubble
   ← context_request     → run ContextTools locally → resend with toolResults (≤3 rounds)
   ← actions (Assist) → validate → ReviewChanges screen
        → user approves → Room transaction → snackbar + Undo
        → user rejects  → logged in chat as "discarded", nothing touched
```

## Appendix E — NOT in v1 (explicitly)

- Full knowledge-graph force-directed visualization (backlinks/related lists cover the need)
- Streaming AI responses (SSE) — nice-to-have only if Phase 3 finishes early
- Embedded YouTube player (thumbnail → external open instead)
- Accounts, sync, cloud backup, multi-device
- WYSIWYG editor (markdown source + preview instead)
- Ghost/auto-creation of unresolved `[[links]]`
- Rich attachment/image embedding in posts
- Spaced repetition / flashcards
- Backend auth/multi-user, server-side storage of any kind
