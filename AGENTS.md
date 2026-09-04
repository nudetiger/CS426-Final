# AGENTS.md — guidance for AI coding agents working on Learning Mocha

Read `docs/plan.md` first. It is the source of truth for architecture and phases.

## Non-negotiable rules

1. **Local-first**: all knowledge data lives in Room (`data/local`). Never add cloud
   storage, accounts, or analytics SDKs.
2. **AI never touches the database.** All AI-originated writes go through
   `ai/engine/ActionExecutor` (validate → user review → single Room transaction).
3. **Layers**: Fragment → ViewModel → Repository → DAO. No DB/network calls in UI code.
4. **Language split**: Kotlin for Android-facing code; Java only for framework-free
   utilities in `util/` (parsers, export/import, diff).
5. **Design tokens**: always use `@color/mocha_*`, `@dimen/*`, `@font/comfortaa` and the
   `TextAppearance.Mocha.*` styles. Never hardcode hex colors or px sizes in layouts.
6. The backend (`backend/`) stays stateless: no database, no storing user content,
   key only via `.env` (never committed, never in the APK).
7. Every list screen needs loading / empty / error / offline states.
8. minSdk is 24 — check API levels before using newer APIs.

## Key conventions

- Post content = Markdown + `[[Post Title]]` wiki-links (parsed by
  `util/MarkdownLinkParser.java`).
- Tree model: single `nodes` table (`BRANCH | FOLDER | POST`, `parentId`, `orderIndex`).
- Search = FTS5 table kept in sync on write.
- AI protocol envelopes and action ops: see `backend/prompts.js` and plan §11–13;
  keep both sides in sync when changing the schema.
- Phases: implement in the order of `docs/plan.md` §26; don't start Phase N+1
  features before Phase N works end-to-end.

## Build / verify

```powershell
.\gradlew.bat assembleDebug   # must pass before committing
.\gradlew.bat test            # JVM unit tests
```
