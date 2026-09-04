# Learning Mocha

A local-first Android personal learning & knowledge-management app:
**personal Wikipedia + learning tracker + knowledge graph + AI learning assistant.**
CS426 final project.

- 📋 Plan with phases: [`docs/plan.md`](docs/plan.md)
- 📜 Course requirements: [`docs/final_requirements.md`](docs/final_requirements.md)
- 💡 Concept: [`docs/idea.md`](docs/idea.md)

## Group members

| Student ID | Full name | Responsibility |
| ---------- | --------- | -------------- |
| _TODO_     | _TODO_    | _TODO_         |

Demo video: _TODO (link goes to `video/demo-link.txt` at submission)_

## Repository layout

```
app/       Android application (Kotlin + Java, XML Views, Room, minSdk 24)
backend/   Tiny Node/Express gateway to the DeepSeek API (holds the key, stores nothing)
docs/      Concept, course requirements, implementation plan
```

## Build & run (Android)

Prereqs: JDK 17 (Android Studio JBR is used automatically), Android SDK.

```powershell
.\gradlew.bat assembleDebug          # debug APK -> app\build\outputs\apk\debug\
.\gradlew.bat installDebug           # install on connected device/emulator
```

Release APK (Phase 5): `.\gradlew.bat assembleRelease` → submit as
`apk/app-release.apk` (runs on API 24+).

## Build & run (backend — only needed for the AI chat feature)

```powershell
cd backend
npm install
copy .env.example .env               # add your DEEPSEEK_API_KEY
npm run dev                          # http://localhost:8787
```

The app reaches the backend from the emulator at `http://10.0.2.2:8787`.
Without the backend every non-AI feature keeps working (local-first).

## Test account

No login required — the app has no authentication by design.
