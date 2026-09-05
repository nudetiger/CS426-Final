// Learning Mocha backend — a deliberately tiny DeepSeek gateway.
//
// Responsibilities (and ONLY these):
//   1. Hold the DeepSeek API key (never ship it in the APK).
//   2. Build chat.completions requests (system prompt per chat mode).
//   3. Normalize errors so the Android app can stay offline-calm.
//   4. Relay DeepSeek's token stream to the app as Server-Sent Events.
//
// It has NO database and stores NO user content. The Android device is the
// source of truth; this process is a stateless proxy.

import "dotenv/config";
import express from "express";
import { buildMessages, parseModes } from "./prompts.js";

const PORT = Number(process.env.PORT ?? 8787);
const MODEL = process.env.MODEL ?? "deepseek-chat";
const DEEPSEEK_URL =
  process.env.DEEPSEEK_URL ?? "https://api.deepseek.com/chat/completions";
const API_KEY = process.env.DEEPSEEK_API_KEY;

const REQUEST_TIMEOUT_MS = 60_000;
const STREAM_TIMEOUT_MS = 120_000;
// Learning-path batches and generated articles are long; without an explicit
// cap DeepSeek stops at its default and hands back a truncated JSON envelope.
const MAX_TOKENS = Number(process.env.MAX_TOKENS ?? 8192);
// Under load the model sometimes answers with a long run of whitespace instead of
// prose. Caught here so the app shows a retryable error rather than an empty bubble.
const EMPTY_REPLY = "The assistant replied with nothing usable. Try again.";

const app = express();
app.use(express.json({ limit: "1mb" }));

// ---------------------------------------------------------------------------
// GET /v1/health — the app pings this to decide whether the AI tab is usable.
// `streaming` lets the client pick /v1/chat/stream without probing for a 404.
// ---------------------------------------------------------------------------
app.get("/v1/health", (_req, res) => {
  res.json({ ok: Boolean(API_KEY), model: MODEL, streaming: true });
});

/**
 * Shared request validation for both chat endpoints.
 * @returns {{error: string, status: number, retryable: boolean} | {payloadInput: object}}
 */
function validate(body) {
  if (!API_KEY) {
    return {
      status: 503,
      error: "DEEPSEEK_API_KEY is not set on the backend",
      retryable: false,
    };
  }
  const { mode = "answer", messages = [], kbIndex = "", toolResults } =
    body ?? {};
  if (parseModes(mode) === null) {
    return { status: 400, error: `Unknown mode "${mode}"`, retryable: false };
  }
  if (!Array.isArray(messages) || messages.length === 0) {
    return {
      status: 400,
      error: "messages must be a non-empty array",
      retryable: false,
    };
  }
  return { payloadInput: { mode, kbIndex, messages, toolResults } };
}

function upstreamRequest(payloadInput, { stream }) {
  return fetch(DEEPSEEK_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${API_KEY}`,
    },
    body: JSON.stringify({
      model: MODEL,
      stream,
      max_tokens: MAX_TOKENS,
      response_format: { type: "json_object" },
      messages: buildMessages(payloadInput),
    }),
    signal: AbortSignal.timeout(stream ? STREAM_TIMEOUT_MS : REQUEST_TIMEOUT_MS),
  });
}

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
  const check = validate(req.body);
  if (check.error) {
    return res
      .status(check.status)
      .json({ error: check.error, retryable: check.retryable });
  }

  try {
    const upstream = await upstreamRequest(check.payloadInput, {
      stream: false,
    });
    const data = await upstream.json().catch(() => null);

    if (!upstream.ok) {
      return res.status(upstream.status === 429 ? 429 : 502).json({
        error: data?.error?.message ?? `DeepSeek returned ${upstream.status}`,
        retryable: upstream.status === 429 || upstream.status >= 500,
      });
    }

    // A json_object reply that hit the token cap is not parseable JSON. Report
    // it as a retryable error instead of letting the app render raw braces.
    if (data?.choices?.[0]?.finish_reason === "length") {
      return res.status(502).json({
        error: "The reply was cut off before it finished. Try asking for less at once.",
        retryable: true,
      });
    }

    const reply = data?.choices?.[0]?.message?.content ?? "";
    if (!reply.trim()) {
      return res.status(502).json({ error: EMPTY_REPLY, retryable: true });
    }
    res.json({ reply, usage: data?.usage ?? null });
  } catch (err) {
    const isTimeout = err?.name === "TimeoutError";
    res.status(isTimeout ? 504 : 502).json({
      error: isTimeout ? "DeepSeek timed out" : "Failed to reach DeepSeek",
      retryable: true,
    });
  }
});

// ---------------------------------------------------------------------------
// POST /v1/chat/stream — same body as /v1/chat, answered as Server-Sent Events.
//
// Frames (one JSON object per `data:` line):
//   {"delta":"<text fragment>"}                     zero or more, in order
//   {"done":true,"reply":"<full text>","usage":…}   exactly once on success
//   {"error":"…","retryable":true|false}            instead of `done` on failure
//
// The full reply is repeated in the terminal frame so the client parses the
// action envelope from one authoritative string instead of re-joining deltas.
// Errors are reported inside the stream (HTTP 200) once headers are flushed —
// the client treats a stream that ends without `done` as a retryable failure.
// ---------------------------------------------------------------------------
app.post("/v1/chat/stream", async (req, res) => {
  const check = validate(req.body);
  if (check.error) {
    return res
      .status(check.status)
      .json({ error: check.error, retryable: check.retryable });
  }

  res.writeHead(200, {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache, no-transform",
    Connection: "keep-alive",
    "X-Accel-Buffering": "no",
  });
  const send = (obj) => res.write(`data: ${JSON.stringify(obj)}\n\n`);
  // Flush the headers immediately so the client can show its "thinking" state
  // before the first token arrives. SSE comments are ignored by parsers.
  res.write(": open\n\n");

  // Watch the RESPONSE, not the request: express has already consumed the
  // request body, so `req` emits "close" straight away and listening there
  // would abort the relay before the first token.
  let closed = false;
  res.on("close", () => {
    closed = true;
  });

  try {
    const upstream = await upstreamRequest(check.payloadInput, { stream: true });

    if (!upstream.ok) {
      const detail = await upstream.text().catch(() => "");
      let message = `DeepSeek returned ${upstream.status}`;
      try {
        message = JSON.parse(detail)?.error?.message ?? message;
      } catch {
        /* keep the status-based message */
      }
      send({
        error: message,
        retryable: upstream.status === 429 || upstream.status >= 500,
      });
      return res.end();
    }

    let buffer = "";
    let reply = "";
    let usage = null;
    let truncated = false;
    const decoder = new TextDecoder();

    for await (const chunk of upstream.body) {
      if (closed) break;
      buffer += decoder.decode(chunk, { stream: true });
      // SSE frames are separated by a blank line; keep the trailing partial.
      const frames = buffer.split("\n\n");
      buffer = frames.pop() ?? "";
      for (const frame of frames) {
        for (const line of frame.split("\n")) {
          if (!line.startsWith("data:")) continue;
          const data = line.slice(5).trim();
          if (!data || data === "[DONE]") continue;
          let parsed;
          try {
            parsed = JSON.parse(data);
          } catch {
            continue; // ignore keep-alive comments / malformed frames
          }
          const delta = parsed?.choices?.[0]?.delta?.content;
          if (typeof delta === "string" && delta.length > 0) {
            reply += delta;
            send({ delta });
          }
          if (parsed?.usage) usage = parsed.usage;
          if (parsed?.choices?.[0]?.finish_reason === "length") truncated = true;
        }
      }
    }

    if (!closed) {
      if (truncated) {
        send({
          error: "The reply was cut off before it finished. Try asking for less at once.",
          retryable: true,
        });
      } else if (!reply.trim()) {
        send({ error: EMPTY_REPLY, retryable: true });
      } else {
        send({ done: true, reply, usage });
      }
      res.end();
    }
  } catch (err) {
    if (closed) return;
    const isTimeout = err?.name === "TimeoutError";
    send({
      error: isTimeout ? "DeepSeek timed out" : "Failed to reach DeepSeek",
      retryable: true,
    });
    res.end();
  }
});

app.listen(PORT, () => {
  console.log(
    `Learning Mocha backend on http://localhost:${PORT} (model: ${MODEL}, key: ${
      API_KEY ? "set" : "MISSING — /v1/chat will 503"
    })`,
  );
});
