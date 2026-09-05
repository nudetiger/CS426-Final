# Learning Mocha

A local-first Android personal learning & knowledge-management app:
**personal Wikipedia + learning tracker + knowledge graph + AI learning assistant.**
CS426 final project.

Everything you write stays on the device. The only thing that ever leaves it is the
text you send to the AI assistant, and the app remains fully usable with the AI
turned off or unreachable.

- 📋 Plan with phases: [`docs/plan.md`](docs/plan.md)
- 📜 Course requirements: [`docs/final_requirements.md`](docs/final_requirements.md)
- 💡 Concept: [`docs/idea.md`](docs/idea.md)
- 🧾 What was built, and by whom: [`report-work.md`](report-work.md) · [`report/report.pdf`](report/report.pdf)

## Group members

| Student ID | Full name       | Responsibility                                                                                                            |
| ---------- | --------------- | ------------------------------------------------------------------------------------------------------------------------- |
| 24125006   | Nguyen Anh Dung | Android app core: project setup, Room data layer & migrations, tree/Browse, reader & editor, FTS search, knowledge graph, backup/export/import, settings & theming, release build |
| 24125009   | Le Huu Hoa      | AI subsystem: Node/Express DeepSeek gateway, prompt & action protocol, context tools, action validator/executor, review-changes UX, SSE streaming, embedded media, tests & report  |

A per-feature breakdown of who did what is in the work-division table of
[`report/report.pdf`](report/report.pdf) (§9) and in [`report-work.md`](report-work.md).

Demo video: DEMO_VIDEO_LINK

The link itself is kept in one place, [`video/demo-link.txt`](video/demo-link.txt), and
`tools/package-submission.ps1` substitutes it into the line above when it builds the
submission archive — so the README and the video file can never disagree.

## Test account

**None required.** The app has no accounts, no login and no server-side identity by
design — the knowledge base is local to the device. The AI assistant needs the local
gateway from `backend/` to be running with a DeepSeek API key; every other feature
works without it.

## Repository layout

```
app/       Android application (Kotlin + Java, XML Views, Room, minSdk 24)
backend/   Tiny Node/Express gateway to the DeepSeek API (holds the key, stores nothing)
docs/      Concept, course requirements, implementation plan
report/    LaTeX sources and the built PDF report
tools/     package-submission.ps1, which builds the graded submission archive
video/     Link to the demo video
```

## Build & run (Android)

Prereqs: **JDK 17** (Android Studio's bundled JBR is fine), Android SDK with
platform 35 and build-tools 34+.

```powershell
.\gradlew.bat assembleDebug          # debug APK -> app\build\outputs\apk\debug\
.\gradlew.bat installDebug           # install on a connected device/emulator
.\gradlew.bat test                   # JVM unit tests
.\gradlew.bat connectedDebugAndroidTest   # instrumented Room migration tests
```

The instrumented tests install a debug-signed APK, so uninstall the app first if the
signed release build is already on the device — otherwise the signature clash surfaces
as a confusing `Process crashed` instead of an install error.

Gradle 8.9 cannot run on JDK 23+. If your default `java` is newer, point Gradle at a
JDK 17 by adding one line to your own `~/.gradle/gradle.properties` — Gradle never reads
`local.properties`, only the Android plugin does:

```properties
org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
```

`local.properties` (git-ignored) needs `sdk.dir=<path to your Android SDK>`; Android
Studio writes it for you on first open.

### Release APK

```powershell
.\gradlew.bat assembleRelease        # -> app\build\outputs\apk\release\
```

Signing is read from a git-ignored `keystore.properties` at the repository root
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without that file the
release build still succeeds and is left unsigned — the graded, signed APK ships in
`apk/app-release.apk` of the submission archive and installs on Android API 24+.

## Build & run (backend — only needed for the AI assistant)

```powershell
cd backend
npm install
copy .env.example .env               # then add your DEEPSEEK_API_KEY
npm run dev                          # http://localhost:8787
```

The app reaches the gateway from the emulator at `http://10.0.2.2:8787`; on a
physical device use your computer's LAN address and set it in the app under
**Settings → AI gateway**. `GET /v1/health` tells the app whether the assistant is
available, and the chat screen shows a calm offline banner when it is not.

Without the backend every non-AI feature keeps working — the app is local-first, not
offline-tolerant as an afterthought.

## What the app does

- **Knowledge base** — branches, folders and subfolders of Markdown posts you can
  create, rename, move, reorder (drag & drop) and delete.
- **Wiki links** — write `[[Spring Boot]]` in any post; the reader turns it into a tap
  target, and the target post shows the backlink.
- **Knowledge graph** — a force-directed view of your posts and the links between
  them, for the whole library or the neighbourhood of one post.
- **Learning metadata** — per-post status (Reading / In progress / Finished),
  favourites, tags, external references and YouTube resources that play in-app.
- **Dictionary** — global and per-post glossary entries (term, English definition,
  Vietnamese meaning) reachable without leaving the article.
- **Search** — SQLite FTS4 across titles and content, plus tags, dictionary terms and
  resources, with type/status/favourite filters.
- **Prerequisites** — say which posts come first, and every post shows how far through its
  prerequisites you are. Browse can sort and filter by what you are actually ready to read.
- **Branch reading** — open a branch and read it end to end, in an order that respects the
  prerequisites you set, with the structure a tap away.
- **AI assistant** — two modes (Answer · Assist). It reads your
  library only through explicit read-only context tools, streams its replies, and can
  never write to the database: it proposes a batch of structured actions that you
  review item by item before a single Room transaction applies them.
- **Backup** — export and import the whole library as one `.mocha.json` file through
  the system file picker, plus a weekly local reminder. Chat history is deliberately
  never exported.
