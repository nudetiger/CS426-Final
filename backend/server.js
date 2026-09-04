// Learning Mocha backend — a deliberately tiny DeepSeek gateway.
//
// Responsibilities (and ONLY these):
//   1. Hold the DeepSeek API key (never ship it in the APK).
//   2. Build chat.completions requests (system prompt per chat mode).
//   3. Normalize errors so the Android app can stay offline-calm.
//
// It has NO database and stores NO user content. The Android device is the
// source of truth; this process is a stateless proxy.

import "dotenv/config";
import express from "express";
import { buildMessages, MODES } from "./prompts.js";

const PORT = Number(process.env.PORT ?? 8787);
const MODEL = process.env.MODEL ?? "deepseek-chat";
const DEEPSEEK_URL =
  process.env.DEEPSEEK_URL ?? "https://api.deepseek.com/chat/completions";
const API_KEY = process.env.DEEPSEEK_API_KEY;

const app = express();
app.use(express.json({ limit: "1mb" }));

// ---------------------------------------------------------------------------
// GET /v1/health — the app pings this to decide whether the AI tab is usable.
// ---------------------------------------------------------------------------
app.get("/v1/health", (_req, res) => {
  res.json({ ok: Boolean(API_KEY), model: MODEL });
});

// ---------------------------------------------------------------------------
// POST /v1/chat
// body: {
//   mode: "answer" | "suggest" | "modify" | "organize",
//   messages: [{ role: "user" | "assistant" | "tool", content: string }, ...],
//   kbIndex: string,            // compact title/id tree, capped by the app
//   toolResults?: string        // JSON results of context tools (round 2+)
// }
// response: { reply: <model text: a JSON envelope or markdown answer>, usage }
// ---------------------------------------------------------------------------
app.post("/v1/chat", async (req, res) => {
  if (!API_KEY) {
    return res.status(503).json({
      error: "DEEPSEEK_API_KEY is not set on the backend",
      retryable: false,
    });
  }

  const { mode = "answer", messages = [], kbIndex = "", toolResults } =
    req.body ?? {};

  if (!MODES.includes(mode)) {
    return res
      .status(400)
      .json({ error: `Unknown mode "${mode}"`, retryable: false });
  }
  if (!Array.isArray(messages) || messages.length === 0) {
    return res
      .status(400)
      .json({ error: "messages must be a non-empty array", retryable: false });
  }

  const payload = {
    model: MODEL,
    stream: false, // SSE streaming is a stretch goal (docs/plan.md Appendix E)
    response_format: { type: "json_object" },
    messages: buildMessages({ mode, kbIndex, messages, toolResults }),
  };

  try {
    const upstream = await fetch(DEEPSEEK_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${API_KEY}`,
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(60_000),
    });

    const data = await upstream.json().catch(() => null);

    if (!upstream.ok) {
      return res.status(upstream.status === 429 ? 429 : 502).json({
        error: data?.error?.message ?? `DeepSeek returned ${upstream.status}`,
        retryable: upstream.status === 429 || upstream.status >= 500,
      });
    }

    const reply = data?.choices?.[0]?.message?.content ?? "";
    res.json({ reply, usage: data?.usage ?? null });
  } catch (err) {
    const isTimeout = err?.name === "TimeoutError";
    res.status(isTimeout ? 504 : 502).json({
      error: isTimeout ? "DeepSeek timed out" : "Failed to reach DeepSeek",
      retryable: true,
    });
  }
});

app.listen(PORT, () => {
  console.log(
    `Learning Mocha backend on http://localhost:${PORT} (model: ${MODEL}, key: ${
      API_KEY ? "set" : "MISSING — /v1/chat will 503"
    })`,
  );
});
