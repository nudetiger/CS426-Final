# Learning Mocha — backend gateway

Deliberately tiny DeepSeek proxy. Holds the API key, builds prompts, normalizes
errors, relays the token stream. **No database, no user content stored** — the
Android app is the source of truth and stays fully usable when this server is
down.

## Run

```bash
npm install
cp .env.example .env   # then put your DeepSeek key in .env
npm run dev            # http://localhost:8787
```

From the Android emulator the app reaches it at `http://10.0.2.2:8787`;
from a physical device use your machine's LAN IP (settable in the app under
Settings → AI gateway).

## Endpoints

| Method | Path              | Purpose                                                          |
| ------ | ----------------- | ---------------------------------------------------------------- |
| GET    | `/v1/health`      | `{ ok, model, streaming }` — app-side offline detection          |
| POST   | `/v1/chat`        | `{ mode, messages, kbIndex, toolResults? }` → `{ reply, usage }` |
| POST   | `/v1/chat/stream` | same body, answered as Server-Sent Events (see below)            |

`reply` is a JSON envelope (`answer` | `context_request` | `actions`) — see
`prompts.js` and `docs/plan.md` §11–13 for the protocol.

### Streaming frames

`/v1/chat/stream` writes one JSON object per SSE `data:` line:

```
data: {"delta":"<text fragment>"}                    // zero or more, in order
data: {"done":true,"reply":"<full text>","usage":{}} // once, on success
data: {"error":"…","retryable":true|false}           // instead of done, on failure
```

The complete reply is repeated in the terminal frame so the client parses the
action envelope from one authoritative string instead of re-joining deltas. A
stream that ends without a `done` frame is treated by the app as a retryable
failure, and the app falls back to the non-streaming `/v1/chat` endpoint when
`/v1/health` does not advertise `streaming: true`.

## Privacy

What leaves the device: the chat messages the user typed, the compact knowledge-base
index (titles only), and the results of the context-tool queries the model asked
for. Nothing is written to disk here — no request logging of message bodies, no
database. The API key exists only in `.env`, which is git-ignored and never
shipped inside the APK.
