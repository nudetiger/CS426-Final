# Learning Mocha — backend gateway

Deliberately tiny DeepSeek proxy. Holds the API key, builds prompts, normalizes
errors. **No database, no user content stored** — the Android app is the source
of truth and stays fully usable when this server is down.

## Run

```bash
npm install
cp .env.example .env   # then put your DeepSeek key in .env
npm run dev            # http://localhost:8787
```

From the Android emulator the app reaches it at `http://10.0.2.2:8787`;
from a physical device use your machine's LAN IP (settable in the app's
Settings, Phase 4).

## Endpoints

| Method | Path         | Purpose                                             |
| ------ | ------------ | --------------------------------------------------- |
| GET    | `/v1/health` | `{ ok, model }` — app-side offline detection        |
| POST   | `/v1/chat`   | `{ mode, messages, kbIndex, toolResults? }` → `{ reply, usage }` |

`reply` is a JSON envelope (`answer` | `context_request` | `actions`) — see
`prompts.js` and `docs/plan.md` §11–13 for the protocol.
