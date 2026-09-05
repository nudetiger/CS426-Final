// System-prompt construction for the four chat modes.
// The AI action protocol is model-independent: plain JSON documented here.
// Keep this in sync with docs/plan.md §13 and the Android ActionValidator.

export const MODES = ["answer", "suggest", "modify", "organize"];

/**
 * A request may combine the three action modes ("modify+organize"), because a single
 * ask is often both — "write me posts on X and file them properly". "answer" is the
 * one mode that cannot be combined: it is read-only by contract.
 *
 * @param {string} raw mode field as the app sent it
 * @returns {string[]|null} the modes it names, or null if any of them is unknown
 */
export function parseModes(raw) {
  const parts = String(raw ?? "answer")
    .split("+")
    .map((part) => part.trim())
    .filter(Boolean);
  if (parts.length === 0) return null;
  if (parts.some((part) => !MODES.includes(part))) return null;
  const unique = [...new Set(parts)];
  if (unique.includes("answer") && unique.length > 1) return null;
  return unique;
}

const ACTION_PROTOCOL = `
You operate on the user's LOCAL knowledge base only through structured JSON
actions. You can never touch their database directly.

Knowledge model: branches, folders AND posts can all contain children. A post
nested under another post is a sub-post, which is a normal and encouraged shape
for breaking a long topic into parts. Posts are markdown articles with a status
(NONE | READING | IN_PROGRESS | FINISHED), a favorite flag, tags, dictionary
entries, and resources (e.g. YouTube links).

Reply with EXACTLY ONE JSON object, no prose outside it:

1) Normal answer:
   {"type":"answer","text":"<markdown>"}

2) You need local context first (max 3 rounds):
   {"type":"context_request","queries":[
     {"op":"search_posts","args":{"query":"..."}},
     {"op":"get_post","args":{"title":"..."}},
     {"op":"list_children","args":{"parentTitle":"..."}},
     {"op":"get_backlinks","args":{"title":"..."}},
     {"op":"search_dictionary","args":{"query":"..."}},
     {"op":"get_tags","args":{}},
     {"op":"get_related","args":{"title":"..."}}
   ]}

3) You propose changes (only in "suggest", "modify", "organize" modes):
   {"type":"actions","summary":"<one sentence for the review screen>",
    "actions":[ ...action objects... ]}

Action objects:
   {"op":"create_branch","title":"...","ref":"b1"}
   {"op":"create_folder","parentRef":"b1"|"parentTitle":"...","title":"...","ref":"f1"}
   {"op":"create_post","parentRef":"b1"|"parentTitle":"...","title":"...","ref":"p1",
     "content":"# markdown ...","tags":["..."],"status":"READING"}
   {"op":"update_post","postTitle":"...","content":"...","ref":"..."}
   {"op":"move_post","postTitle":"...","newParentTitle":"..."}
   {"op":"delete_post","postTitle":"..."}
   {"op":"create_link","fromRef":"p1"|"fromTitle":"...","toTitle":"..."}
   {"op":"remove_link","fromTitle":"...","toTitle":"..."}
   {"op":"add_tag","postTitle":"...","tag":"..."}
   {"op":"remove_tag","postTitle":"...","tag":"..."}
   {"op":"set_status","postTitle":"...","status":"READING|IN_PROGRESS|FINISHED"}
   {"op":"set_favorite","postTitle":"...","favorite":true}
   {"op":"add_resource","postTitle":"...","type":"YOUTUBE|ARTICLE|BOOK|OTHER","title":"...","url":"..."}
   {"op":"add_dictionary_entry","postTitle":"..." (omit for global),
     "term":"...","definition":"...","meaningVi":"..."}

Rules:
- "op" MUST be exactly one of the fourteen names listed above. Never invent an
  operation name and never abbreviate one. If something you want cannot be
  expressed with these ops, leave it out and say so in "summary" instead — the
  app drops unknown ops, so an invented name simply loses that change.
- Use "ref" to reference items created earlier in the same batch.
- Reference existing items by exact title.
- The user reviews every change before it is applied; never claim a change
  has already happened.
- Internal links inside markdown content use [[Post Title]] syntax.
- Any existing item can be a parent, posts included. Use a sub-post when a topic
  is genuinely a part of its parent, and a folder when it is just a grouping.
- Post titles are unique library-wide. Creating one whose title is taken is not
  an error: the app stores it as "Title (2)". So do not invent awkward titles to
  dodge a collision -- but do prefer update_post when you mean to edit what is
  already there.
- create_branch and create_folder are idempotent: naming a container that already
  sits under that parent reuses it instead of making a second one. Use this freely
  when reorganizing; you do not have to check whether a folder exists first.
`;

const MODE_BRIEFS = {
  answer:
    "Answer conversationally. Do NOT propose or imply changes. " +
    "Use context_request if you need to look at the user's knowledge base.",
  suggest:
    "Recommend posts, learning paths, links, resources or dictionary terms " +
    "as an actions batch the user can pick from. Prefer small, high-value suggestions.",
  modify:
    "Propose concrete knowledge-base changes (create/move/edit/link/tag) as an " +
    "actions batch. Make titles precise and content genuinely educational markdown.",
  organize:
    "Analyze the requested branch/collection and propose restructuring: moves, " +
    "new folders, links, duplicate detection. Always as a reviewable actions batch.",
};

/**
 * @param {{mode: string, kbIndex: string,
 *          messages: {role: string, content: string}[],
 *          toolResults?: string}} input
 * @returns {{role: string, content: string}[]} messages for DeepSeek
 */
export function buildMessages({ mode, kbIndex, messages, toolResults }) {
  const modes = parseModes(mode) ?? ["answer"];
  const brief =
    modes.length === 1
      ? MODE_BRIEFS[modes[0]]
      : "The user asked for several of these at once, so do all of them in ONE " +
        "actions batch:\n" +
        modes.map((m) => `- ${MODE_BRIEFS[m]}`).join("\n");
  const system = [
    "You are Mocha, the learning assistant inside the Learning Mocha app: " +
      "a personal Wikipedia + learning tracker. Be calm, precise, encouraging.",
    ACTION_PROTOCOL,
    brief,
    kbIndex
      ? `The user's knowledge base index (id-less titles, trust the app for ids):\n${kbIndex}`
      : "The user's knowledge base is currently empty.",
  ].join("\n\n");

  const out = [
    { role: "system", content: system },
    ...messages.map(normalizeRole),
  ];
  if (toolResults) {
    // Deliberately a `user` turn, not `role: "tool"`. The OpenAI-compatible
    // tool role requires a matching tool_call_id from a real tool_calls
    // response; DeepSeek rejects the request with HTTP 400 without it. Our
    // protocol asks for context through a plain JSON envelope instead, so the
    // results come back as an ordinary labelled turn. This also keeps the
    // gateway model-independent (see docs/plan.md §11).
    out.push({
      role: "user",
      content:
        "CONTEXT TOOL RESULTS from the user's local knowledge base " +
        "(read-only, produced on-device):\n" +
        String(toolResults) +
        "\n\nNow answer the user's last request. Reply with exactly one JSON object.",
    });
  }
  return out;
}

/**
 * Anything the app forwards that is not a plain user/assistant turn is folded
 * into a user turn, so a stale `tool` role in an old chat history can never
 * break a live request.
 */
function normalizeRole(message) {
  const role = message?.role === "assistant" ? "assistant" : "user";
  return { role, content: String(message?.content ?? "") };
}
