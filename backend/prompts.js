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
entries, an icon/color mark, an optional next-post pointer, and resources
(e.g. YouTube links).

Reply with EXACTLY ONE JSON object, no prose outside it:

1) Normal answer (use this in "answer" mode — never emit "actions" there):
   {"type":"answer","text":"<markdown>",
    "suggestMode":"suggest"|"modify"|"organize"|"suggest+modify"|"modify+organize"|... }

   suggestMode is optional. Set it when the user's last message actually wants
   library changes (create posts, a learning path, reorganize, recommendations
   to apply). The app will then offer to switch. Pick the smallest combination
   that covers the ask: create/edit posts → modify; file/move/structure →
   organize; recommend what to learn/read → suggest. Combine with "+" in that
   order: suggest, then modify, then organize. Do not set suggestMode for a
   plain question.

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
     "content":"# markdown ...","tags":["..."],"status":"READING",
     "icon":"book","color":"amber","nextTitle":"Letter B"|"nextRef":"p2"}
   {"op":"update_post","postTitle":"...","content":"...","icon":"...","color":"...",
     "nextTitle":"...","nextRef":"..."}
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
- icon must be one of: page, book, folder, branch, star, tag, chat, graph, coffee,
  play, palette, search. color must be one of: brown, sage, amber, blue, green,
  gold, sky, violet, rose. Pick a distinct pair per post in a batch so they are
  easy to tell apart in lists. Omit both to keep the default type glyph.
- nextTitle / nextRef chain a learning sequence (alphabet → A → B → C). Point each
  post at the next one. nextRef may name a post created later in the same batch.
  Omit on a one-off article.
`;

const MODE_BRIEFS = {
  answer:
    "Answer conversationally. Do NOT emit type:actions. If the user asked you to " +
    "create, edit, file, or recommend library changes, write a short answer and set " +
    "suggestMode so the app can offer a switch. Use context_request if you need the KB.",
  suggest:
    "Recommend posts, learning paths, links, resources or dictionary terms " +
    "as an actions batch the user can pick from. Prefer small, high-value suggestions.",
  modify:
    "Propose concrete knowledge-base changes (create/move/edit/link/tag) as an " +
    "actions batch. Make titles precise and content genuinely educational markdown. " +
    "When teaching a sequence, emit several create_post actions chained with nextRef.",
  organize:
    "Analyze the requested branch/collection and propose restructuring: moves, " +
    "new folders, links, duplicate detection. Always as a reviewable actions batch.",
};

const POST_CRAFT = `
When you create or rewrite a post:
- Write a real article, not a stub. Headings, short paragraphs, a few examples.
- Link related posts with [[Exact Title]] wiki-links, including other posts in this batch.
- Add 2–5 specific tags, useful dictionary entries, and a resource when one exists.
- Give each post an icon and color from the catalogs above.
- For sequential topics (alphabet, a course, numbered lessons), create the series in
  one batch and set nextRef on each post toward the next lesson. Put them under one
  parent (a post or folder). End the last lesson with no next pointer.
- Match reading level to the learner profile when one is provided (age, name, tone).
`;

const PERSONALITY_BRIEFS = {
  warm: "Warm, calm, encouraging. Coffee-shop tutor energy. Never condescending.",
  tutor: "Socratic tutor. Ask a short check, then explain. Prefer structured lessons.",
  concise: "Short sentences. No filler. Lead with the answer, then one example.",
  witty: "Light humour, still precise. Never mock the learner or the subject.",
  strict: "Direct, high standards. Correct mistakes plainly. Skip pep talk.",
};

function personalityBrief(userProfile) {
  const raw = String(userProfile ?? "");
  const match = /Mocha personality:\s*([a-z]+)/i.exec(raw);
  const key = (match?.[1] ?? "warm").toLowerCase();
  return PERSONALITY_BRIEFS[key] ?? PERSONALITY_BRIEFS.warm;
}

/**
 * @param {{mode: string, kbIndex: string,
 *          messages: {role: string, content: string}[],
 *          toolResults?: string, userProfile?: string}} input
 * @returns {{role: string, content: string}[]} messages for DeepSeek
 */
export function buildMessages({ mode, kbIndex, messages, toolResults, userProfile }) {
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
    personalityBrief(userProfile),
    userProfile ? String(userProfile) : null,
    ACTION_PROTOCOL,
    POST_CRAFT,
    brief,
    kbIndex
      ? `The user's knowledge base index (id-less titles, trust the app for ids):\n${kbIndex}`
      : "The user's knowledge base is currently empty.",
  ]
    .filter(Boolean)
    .join("\n\n");

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
