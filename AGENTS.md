# AGENTS.md — guidance for AI coding agents working on Learning Mocha

Read `docs/plan.md` first. It is the source of truth for architecture and phases.

## Project skills — use them

This repo ships agent skills in `.agents/skills/`, `.cursor/skills/`, and `.claude/skills/`
(same set, mirrored). **Before starting a task, check whether a skill covers it and follow
that skill's instructions** (read its `SKILL.md`). Don't cargo-cult a skill onto a task it
doesn't fit — the ponytail rule (simplest working solution) still governs.

| When the task involves… | Read skill(s) |
|---|---|
| Building, running, installing, emulator, screenshots, SDK lookup | `android-cli` |
| Adding/fixing tests, test infrastructure | `testing-setup` |
| UI screens, themes, dark mode, styling, accessibility | `ui-styling`, `design-system`, `frontend-checklist-global` |
| System-bar / IME insets, edge-to-edge (targetSdk 35) | `edge-to-edge` |
| Adapting to tablets / screen sizes (rubric criterion) | `adaptive` |
| Writing or editing prose (report, seed content, README) | `humanizer`, `no-ai-slop` |
| Reviewing a diff before commit | `ponytail-review` (see also `.cursor/rules/ponytail.mdc`) |

Skills not listed (Compose/TV/wear/billing/migration skills, etc.) don't apply to this
XML-Views phone app — ignore them unless the stack changes.

## Non-negotiable rules

1. **Local-first**: all knowledge data lives in Room (`data/local`). Never add cloud
   storage, accounts, or analytics SDKs.
2. **AI never touches the database.** All AI-originated writes go through
   `ai/engine/ActionExecutor` (validate → user review → single Room transaction).
3. **Layers**: Fragment → ViewModel → Repository → DAO. No DB/network calls in UI code.
4. **Language split**: Kotlin for Android-facing code; Java only for framework-free
   utilities in `util/` (parsers, export/import, diff).
5. **Design tokens**: colours are **theme attributes**, not colour resources — write
   `?attr/mochaBrown` in layouts and drawables, `R.attr.mochaBrown` + `Context.themeColor()`
   in code (`res/values/attrs_theme.xml` lists them all). `@color/mocha_*` is now only the
   default Mocha palette's *values*, wired up in `Theme.Mocha.Base`; referencing it directly
   breaks Rose Pine, Catppuccin and Nord, which swap the attributes and nothing else. Use
   `@dimen/*`, `@font/comfortaa` and the `TextAppearance.Mocha.*` styles the same way.
   Never hardcode hex colours or px sizes in layouts.
6. The backend (`backend/`) stays stateless: no database, no storing user content,
   key only via `.env` (never committed, never in the APK).
7. Every list screen needs loading / empty / error / offline states.
8. minSdk is 24 — check API levels before using newer APIs.

## Key conventions

- Post content = Markdown + `[[Post Title]]` wiki-links (parsed by
  `util/MarkdownLinkParser.java`).
- Tree model: single `nodes` table (`BRANCH | FOLDER | POST`, `parentId`, `orderIndex`).
  **Any node can be a parent, posts included** — a post under a post is a sub-post, and Browse
  walks into it. Only cycles are refused (`util/TreeRules`).
- Creating never fails on a taken title: a duplicate post is numbered by `util/TitleDedup`
  ("Raft (2)"), and `TreeRepository.createContainer` reuses a container that is already under
  that parent instead of making a second one. Renaming still refuses a taken title.
- Browse has no manual reordering; `orderIndex` survives only as a tiebreaker for legacy rows.
  Order comes from `ui/browse/BrowseQuery` (sort + filter). Swipe a row left to delete it —
  `ui/common/SwipeToDelete` draws the reveal and Browse and the chat list share it.
- **Prerequisites** (`prerequisites` table, DB v6): a user-declared "read that first" edge
  between two posts. Deliberately *not* in `links`, which `KnowledgeSync.reindex` rebuilds from
  markdown on every save and would therefore wipe. Direct only — never walked transitively.
  Cycles are refused by `util/PrereqRules` at the repository, the validator and the executor.
  `ui/common/Readiness` turns the edges into the reader's progress bar and Browse's
  "ready to read" sort and filter; it reuses `SubtreeStats` so the meter is the same widget.
- **Branch reading**: "Read this branch" opens the first post with `branchId` set as a nav
  argument. `ui/browse/BranchReading` decides the order — tree order as the baseline, pulled
  earlier by prerequisite and `nextPostId` edges *inside* that branch, cycle-safe. Prev/next
  replace the reader destination rather than stacking it, so Back always leaves the branch.
- Search = `posts_fts`, an **FTS4** table (`@Fts4(contentEntity = Node::class)` — Room 2.6 has
  no FTS5 annotation). It is content-backed, so Room's triggers keep it in sync automatically;
  never write to it by hand.
- Backup = `.mocha.json` via `util/ExportJsonWriter` + `ImportJsonReader` (pure Java) and
  `backup/BackupRepository` (Room). Restore always reassigns ids and rewrites foreign keys;
  a prerequisite edge is dropped unless *both* ends came through the file. Chat history is
  deliberately never exported. `BackupSnapshot` is `@JvmOverloads` so the pure-Java round-trip
  tests keep compiling as fields are added.
- User preferences live in `data/prefs/SettingsStore` (SharedPreferences), read synchronously
  in `Application.onCreate` to apply the theme before the first inflate.
- AI protocol envelopes and action ops (sixteen): see `backend/prompts.js` and plan §11–13;
  keep both sides in sync when changing the schema.
- Chat has **two** modes, `answer` and `assist` (`ui/chat/ChatModes`). It had four; the three
  action modes all produced the same reviewable batch, so they merged. `ChatModes.parse` folds
  the legacy values, which is why no migration was needed — and why an unrecognised mode must
  keep falling back to `answer`, never `assist`.
- Phases: implement in the order of `docs/plan.md` §26; don't start Phase N+1
  features before Phase N works end-to-end.

## Build / verify

```powershell
.\gradlew.bat assembleDebug   # must pass before committing
.\gradlew.bat test            # JVM unit tests
```
