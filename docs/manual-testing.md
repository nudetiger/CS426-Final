# Manual testing guide

A human walkthrough of Learning Mocha. Tests are ordered by importance — do
them top to bottom and stop at the first thing that breaks badly.

For every step: **Do** = what you tap. **Expect** = what you should see.
If what you see differs, note the test number and jump to
[How to report a problem](#how-to-report-a-problem) at the bottom.

---

## 0. Setup (5 minutes)

**Do**

1. Open a terminal in the project root and run `.\gradlew.bat installDebug`
   with an emulator or phone connected.
2. If the app was already installed from a release APK, uninstall it first.
3. For the AI tests (§5) only, open a second terminal:
   ```
   cd backend
   npm install
   copy .env.example .env      # then paste your DEEPSEEK_API_KEY into it
   npm run dev
   ```
   It should print `Learning Mocha backend on http://localhost:8787`.
4. Open `http://localhost:8787/v1/health` in a browser.

**Expect**

- Build says `BUILD SUCCESSFUL`, app icon appears on the device.
- The health URL returns JSON saying the gateway is ok.

If you have already used the app before, some tests below assume a **fresh
install**. To reset: uninstall the app, then `installDebug` again.

---

## 1. First launch — does it even open? (CRITICAL)

**Test 1.1 — cold start**

- Do: tap the app icon.
- Expect: Home screen opens within a couple of seconds. No crash, no white
  screen. A greeting at the top ("Good morning" etc.), and a line like
  "5 posts in your library".

**Test 1.2 — seed content exists**

- Do: look at the Home screen sections.
- Expect: a **Branches** section containing "Getting Started". "Recent" shows
  posts like "Welcome to Learning Mocha" and "How this library works".

**Test 1.3 — bottom tabs**

- Do: tap each of the 5 bottom tabs in order: Home, Browse, Search, Chat,
  Settings. Then tap back through them.
- Expect: each screen opens, no crash. Tapping a tab you are already on does
  nothing bad.

**Test 1.4 — open a post**

- Do: on Home, tap "Welcome to Learning Mocha".
- Expect: reader screen with the title, formatted text (headings are bigger,
  bullets are bullets — not raw `#` and `-` characters), a back arrow, a star
  icon, a status button, and a graph icon.

---

## 2. Browse — the tree (CRITICAL)

Go to the **Browse** tab.

**Test 2.1 — navigate in and out**

- Do: tap "Getting Started". Then tap a folder inside it. Then press the
  system Back button twice.
- Expect: each tap goes one level deeper. The breadcrumb row at the top grows
  ("Library / Getting Started / ..."). Back goes one level up each time, not
  out of the app.

**Test 2.2 — breadcrumb tap**

- Do: go 2 levels deep, then tap "Library" in the breadcrumb.
- Expect: you jump straight back to the top level.

**Test 2.3 — create a branch**

- Do: at the top level (breadcrumb shows only "Library"), tap the **+** FAB.
  In the dialog pick **Branch**, type `Test Branch`, tap Create.
- Expect: dialog closes, "Test Branch" appears in the list.

**Test 2.4 — create a folder**

- Do: open "Test Branch". Tap **+**, pick **Folder**, type `Test Folder`,
  Create.
- Expect: "Test Folder" appears inside Test Branch.

**Test 2.5 — invalid nesting is refused**

- Do: at the top level, tap **+** and try to create a **Folder** (not a
  branch) named `Bad Folder`.
- Expect: a snackbar error at the bottom saying a folder cannot live at the
  root — and no new row appears. Only branches are allowed at the top.

**Test 2.6 — create a post**

- Do: open "Test Folder". Tap **+**, pick **Post**, tap Create.
- Expect: the editor opens (not a new row in the list). This is intentional —
  posts are created by writing them.
- Then: type title `Test Post`, type body `Hello **world**`, tap Save.
- Expect: snackbar "Saved", and the reader opens showing **world** in bold.

**Test 2.7 — rename**

- Do: Browse → find "Test Folder" → tap the **⋮** menu on its row → Rename →
  change to `Renamed Folder` → Rename.
- Expect: the row title changes immediately.

**Test 2.8 — move**

- Do: **⋮** on "Renamed Folder" → Move → pick a different branch from the
  list.
- Expect: the folder disappears from here and is now inside the branch you
  picked (go check).

**Test 2.9 — reorder by dragging**

- Do: in a list with 3+ items, press and hold a row, drag it to a different
  position, release. Then leave Browse (go to Home) and come back.
- Expect: the row stays in the new position after coming back. The order is
  saved, not just visual.

**Test 2.10 — swipe to delete**

- Do: swipe a row from right to left.
- Expect: a confirm dialog appears. Tap **Cancel** → the row springs back
  intact. Swipe again, tap **Delete** → the row is gone.

**Test 2.11 — delete cascades**

- Do: delete "Test Branch" (which still contains things).
- Expect: the confirm dialog warns about contents. After deleting, the branch
  and everything inside it is gone — check Search for `Test Post`, it should
  not be found.

---

## 3. Editor and reader (CRITICAL)

Open any post → tap the **edit** (pencil) button. The header says "Edit post"
(or "New post" when you came from Browse → + → Post).

The editor is laid out top to bottom as: header (back arrow, "Edit post",
**Save**) → a scrolling form with **Title**, the four status chips, **Tags**,
and a **References** section → a formatting toolbar reading
**Bold · Italic · Heading · List · Link · Wiki link · Term** → the
**Source | Preview** tabs → the body text area. The toolbar scrolls
sideways, so on a narrow screen swipe it left to reach **Term**.

**Test 3.1 — formatting toolbar**

- Do: in the body, type `hello`, select it, tap **Bold**. Then select another
  word and tap **Italic**. Then put the cursor on a new line and tap
  **Heading**, and on another line tap **List**.
- Expect: text becomes `**hello**`, `*word*`, the line gets `## ` in front,
  another line gets `- ` in front.

**Test 3.2 — preview tab**

- Do: tap the **Preview** tab (next to **Source**, just above the body).
- Expect: the raw markdown is replaced by rendered text — bold is bold, `##`
  is a big heading, `-` lines are bullets. Tap **Source** to go back and the
  raw text is still there and editable.

**Test 3.3 — wiki-link picker**

- Do: put the cursor in the body, tap **Wiki link** in the toolbar.
- Expect: a dialog lists other post titles. Pick one → `[[That Title]]` is
  inserted at the cursor.
- Note: if you had text selected instead, it just wraps the selection in
  `[[ ]]` without showing the dialog. Same if the library has no other posts.

**Test 3.4 — wiki-link works in the reader**

- Do: Save. In the reader, tap the `That Title` link in the text.
- Expect: it opens that post.

**Test 3.5 — broken wiki-link**

- Do: edit the post, type `[[No Such Post Xyz]]`, save, tap that link in the
  reader.
- Expect: a short toast saying that post does not exist. No crash, no
  navigation.

**Test 3.6 — backlinks**

- Do: open the post you linked *to* in 3.3 and scroll to the bottom.
- Expect: a "Backlinks" section listing the post that links to it. Tap it →
  it opens.

**Test 3.7 — plain markdown link**

- Do: select a word in the body and tap **Link** in the toolbar.
- Expect: it becomes `[word](https://)` — type the real URL between the
  brackets, save, and the reader shows it as a tappable link that opens the
  browser. (This is a normal web link; **Wiki link** is the one that points at
  another post.)

**Test 3.8 — tags**

- Do: in the editor, type `kotlin, testing` in the **Tags** field and save.
- Expect: the reader shows two tag chips. Tap `kotlin` → a screen listing all
  posts with that tag.

**Test 3.9 — status**

- Do: in the reader, tap the status button (says "Reading" or "None"). Pick
  **Finished**.
- Expect: the button label changes right away. Leave the post and come back —
  still "Finished". Reopen the editor → the **Finished** chip is the one
  selected.

**Test 3.10 — favorite**

- Do: tap the star icon in the reader.
- Expect: it fills in. Go to Home → the post now appears under Favorites.
  Tap the star again → it empties and drops off the Favorites list.

**Test 3.11 — dictionary term**

- Do: in the editor toolbar tap **Term** (the last button — scroll the
  toolbar sideways if you cannot see it). Enter term `Coroutine`, definition
  `A suspendable computation`, Vietnamese `Luồng treo được`. Save the dialog,
  then **Save** the post.
- Expect: a snackbar "Term will be saved with the post". The reader then
  shows a "Terms" section with a `Coroutine` chip. Tap it → a bottom sheet
  slides up with the definition and the Vietnamese line. It covers only the bottom part
  of the screen, not the whole post.
- Do: leave the term field blank and try to save the dialog.
- Expect: a snackbar says the term is required; nothing is added.

**Test 3.12 — add a reference (article)**

- Do: in the editor, scroll the form up to the **References** section (below
  the Tags field). It says "No references yet." Tap the small **Add** button
  on the right of that header.
- Expect: a dialog with a name field, a URL field, and four kind options
  (Article / YouTube / Book / Other).
- Do: pick **Article**, name `Kotlin docs`, URL
  `https://kotlinlang.org/docs/home.html` → Save → **Save** the post.
- Expect: a snackbar "Saved with the post" — the reference only really lands
  when you save the post, not when you close the dialog. A chip appears in
  the References section, and the reader shows a
  "References" card. Tap the card → your browser opens that page.
- Do: back in the editor, tap the chip itself.
- Expect: a snackbar showing the full URL.
- Do: tap the **×** on the chip.
- Expect: the chip is removed and a snackbar confirms it.

**Test 3.13 — URL is required**

- Do: References → **Add** → fill in only the name, leave URL blank → Save.
- Expect: a snackbar saying the URL is required; no chip is added.

**Test 3.14 — YouTube plays inside the app**

- Do: References → **Add** → kind **YouTube**, name `Demo`, URL
  `https://www.youtube.com/watch?v=dQw4w9WgXcQ` → Save → **Save** the post.
- Expect: a reader card with a play icon. Tap it → a bottom sheet opens and
  the video plays **inside the app**, roughly 16:9, not in the YouTube app.
  There is an "Open in YouTube" button that does leave the app.

**Test 3.15 — inline YouTube link is auto-detected**

- Do: paste a bare YouTube URL into the post **body** and save.
- Expect: it also shows up as a reference card in the reader, without you
  adding anything in the References section. These derived cards have no
  **×** on their chip — the way to remove one is to delete the URL from the
  body text.

**Test 3.15b — YouTube cards show the video's thumbnail**

- Do: with the device online, look at the reference cards from 3.14 and 3.15.
- Expect: each YouTube card shows that video's own poster frame with a play
  badge on it, not the generic play glyph. Two different videos show two
  different frames.
- Do: turn off wifi and mobile data, force-stop the app, reopen the post.
- Expect: the frames still appear (they are cached on disk). Now clear the
  app's cache in Android Settings and reopen while still offline — the cards
  fall back to the plain play glyph rather than showing an empty grey box.

**Test 3.16 — discard guard**

- Do: edit a post, type something, then press system Back without saving.
- Expect: a dialog "Discard changes?". **Keep editing** returns you to your
  text intact. **Discard** leaves and the change is gone.

---

## 4. Search (HIGH)

Go to the **Search** tab.

**Test 4.1 — empty state**

- Do: just look, without typing.
- Expect: a message like "Type to search your library." Not an error, not a
  spinner stuck forever.

**Test 4.2 — full-text search**

- Do: type a word you know is in the *body* of a post (not the title), e.g.
  `markdown`.
- Expect: results appear as you type, including posts whose body contains the
  word.

**Test 4.3 — prefix search**

- Do: type just `mark`.
- Expect: it still matches `markdown` — prefix matching is on.

**Test 4.4 — no matches**

- Do: type `zzzqqq`.
- Expect: "No matches." No crash.

**Test 4.5 — special characters do not crash**

- Do: type `"`, then `*`, then `foo AND`, then `(`.
- Expect: no crash at any point. Empty or odd results are fine; a crash is
  not.

**Test 4.6 — filters**

- Do: type a broad query, then tap the **Posts** chip. Then tap **Branches**.
- Expect: Posts and Branches are mutually exclusive — turning one on turns
  the other off. Results narrow accordingly.
- Do: tap **Favorites**.
- Expect: only starred posts remain.
- Do: tap **Reading**, then **Finished**.
- Expect: also mutually exclusive; results match the status you set in 3.8.

**Test 4.7 — result types open correctly**

- Do: search for your dictionary term `Coroutine`, and for part of a
  reference URL like `kotlinlang`.
- Expect: the term result opens the post it belongs to (or the Dictionary if
  it is a global term); the reference result opens the post that cites it.

**Test 4.8 — branch result jumps to Browse**

- Do: search a branch name and tap the result.
- Expect: the app switches to the Browse tab, already opened inside that
  branch.

---

## 5. AI assistant (HIGH — the biggest feature)

The backend from §0 must be running. On an **emulator** the default address
`http://10.0.2.2:8787/` works. On a **real phone**, go to Settings → AI
gateway, type `http://<your-computer-LAN-IP>:8787/`, tap Save, then
**Test connection**.

**Test 5.0 — connection**

- Do: Settings → AI gateway → **Test connection**.
- Expect: "Connected to the gateway." If it cannot reach it, the rest of §5
  will not work — fix this first (backend running? same Wi-Fi? firewall?).

**Test 5.1 — offline banner (do this first, with the backend STOPPED)**

- Do: stop the backend (Ctrl+C). Open the **Chat** tab.
- Expect: a calm banner saying AI is unavailable and your library still
  works. Browse, Search and the reader all still work normally. Restart the
  backend and tap **Retry** on the banner → the banner disappears.

**Test 5.2 — create a conversation**

- Do: Chat tab → tap the **+** FAB.
- Expect: an empty conversation opens with 2 mode chips: **Answer** and
  **Assist**. Answer is selected.
- Expect: each chip carries its own colour — green Answer, blue Assist — on the
  outline and the label even when unselected. Selecting one fills it with the
  same colour, and it is the colour the replies sent in that mode are drawn in.
- Expect: they are mutually exclusive. Tapping Assist unselects Answer; tapping
  the chip that is already selected leaves it selected rather than clearing it.

**Test 5.3 — Answer mode, plain question**

- Mode: **Answer**
- Prompt: `In two sentences, what is a Kotlin coroutine?`
- Expect: the reply **streams in word by word** with a "typing…" indicator,
  then settles into a formatted bubble. No "Review changes" button — Answer
  mode must never propose changes.

**Test 5.4 — Answer mode reads your library**

- Mode: **Answer**
- Prompt: `What posts do I have in my library, and what is the Welcome post about?`
- Expect: it names your actual post titles. Under the reply, a small line
  like "Shared 1 note with the AI" — that means it used a context tool to
  read a post. This is the proof it can read your library.

**Test 5.5 — Assist mode proposes changes (does NOT apply them)**

- Mode: **Assist**
- Prompt: `Create a branch called "Kotlin Basics" with three posts: "Variables and Types", "Null Safety", and "Coroutines 101". Give each one a short markdown article and tag them "kotlin".`
- Expect:
  1. It streams, then the bubble ends with a **Review changes** button.
  2. **Nothing has been created yet.** Switch to Browse and confirm
     "Kotlin Basics" does NOT exist.
  3. Tap **Review changes** → a list of roughly 10 rows: one create_branch,
     three create_post (indented under it), several add_tag. Each has a
     checkbox, all ticked. A one-line summary at the top and a count like
     "10 of 10 selected".

**Test 5.6 — review screen: preview and edit**

- Do: tap a create_post row.
- Expect: a dialog previewing the generated markdown, rendered.
- Do: tap the **⋮** on that row → **Edit** → change the title to
  `Coroutines Intro` → Save.
- Expect: the row's title updates in the list.

**Test 5.7 — review screen: change location**

- Do: **⋮** on a post row → **Change location** → pick a different branch or
  folder → Save.
- Expect: the row's indent / parent label updates.

**Test 5.8 — review screen: unselect**

- Do: untick one post row.
- Expect: the count drops ("9 of 10 selected"). Untick everything → the
  **Apply selected** button greys out.

**Test 5.9 — apply**

- Do: re-tick everything, tap **Apply selected**.
- Expect: a snackbar "Applied N changes" with an **Undo** action, and you are
  returned to the conversation. The bubble's button now says **Applied** and
  is no longer tappable.
- Do: go to Browse.
- Expect: "Kotlin Basics" exists with the three posts inside, each with real
  content and the `kotlin` tag. Open one — it renders.

**Test 5.10 — undo**

- Do: run 5.5 and 5.9 again with a different branch name, and this time tap
  **Undo** on the snackbar before it disappears.
- Expect: a second snackbar confirms the undo, and Browse no longer shows the
  new branch. Note: undo cannot bring back things a *delete* action removed —
  if the batch deleted anything, the message says so.

**Test 5.11 — discard**

- Do: propose another batch, open Review, tap **Discard all**.
- Expect: a snackbar confirms, you go back to the chat, the button now says
  **Discarded**, and nothing changed in Browse.

**Test 5.12 — destructive confirm**

- Mode: **Assist**
- Prompt: `Delete the post "Coroutines 101" from my library.`
- Expect: the review screen shows a warning that the batch deletes something.
  Tapping **Apply selected** shows an extra confirmation dialog that names
  the post by title. Cancel → nothing happens. You can confirm it if you
  want; the post should then be gone.

**Test 5.13 — Assist recommends**

- Mode: **Assist**
- Prompt: `Suggest 3 dictionary terms and 2 YouTube resources for my Kotlin posts.`
- Expect: a reviewable batch of add_dictionary_entry / add_resource rows.
  Apply them, then open the post's reader screen: the terms appear as chips
  and the resources as cards.

**Test 5.14 — Assist reorganizes**

- Mode: **Assist**
- Prompt: `Look at my library and reorganize it: group related posts into folders and add wiki-links between posts that belong together.`
- Expect: a batch of create_folder / move_post / create_link rows. Apply,
  then check Browse reflects the new structure and the reader shows new links
  and backlinks.

**Test 5.14b — one Assist batch covers a two-part ask**

- Mode: **Assist**
- Prompt: `Write me two short posts on B-trees and B+ trees, and file them under
  a new folder called "Indexes".`
- Expect: a **single** review batch holding both create_post rows and the
  create_folder row — not two replies, and not half the request. This is the
  case the old Modify/Organize split could only do by sending twice.

**Test 5.15 — validation catches bad plans**

- Mode: **Assist**
- Prompt: `Create a post titled "Welcome to Learning Mocha".` (a title that
  already exists)
- Expect: the review screen shows a red error line like
  `Action 1: title "Welcome to Learning Mocha" already exists`, and **Apply
  selected** is disabled until you untick that row.

**Test 5.16 — streaming survives leaving the screen**

- Do: send a long prompt, e.g. `Write a detailed 800-word article about
  Android Room.` While it is still streaming, press Back to the chat list,
  wait 5 seconds, then reopen the conversation.
- Expect: the reply is still arriving or already finished — not lost, not
  duplicated.

**Test 5.17 — error retry**

- Do: send a message, then immediately kill the backend (Ctrl+C) so the
  request fails.
- Expect: an error bubble with a **Retry** button. Restart the backend, tap
  Retry → it works.

**Test 5.18 — delete a conversation**

- Do: Chat tab → long-press a conversation row.
- Expect: a delete confirm dialog. Delete → the row is gone, the app does not
  crash, and other conversations are untouched.

---

## 6. Backup: export and import (HIGH — data loss risk)

**Test 6.1 — export**

- Do: Settings → **Export library**. The system file picker opens with a name
  like `learning-mocha-2026-09-05.mocha.json`. Save it to Downloads.
- Expect: snackbar "Exported N posts". The "Last export" line updates to
  today's date and time.

**Test 6.2 — the file is real**

- Do: open the file (Files app, or `adb pull`).
- Expect: readable JSON containing your post titles and content. It contains
  **no chat messages** — chat history is deliberately never exported.

**Test 6.3 — import as merge**

- Do: delete one post from Browse. Then Settings → **Import library** → pick
  the file → choose **Merge**.
- Expect: a dialog first tells you how many posts the backup holds. After
  merging, snackbar "Imported N posts", and the deleted post is back —
  *without* wiping anything you created after the export.

**Test 6.4 — import as replace**

- Do: create a throwaway post `Should Vanish`. Then import the same file and
  choose **Replace**.
- Expect: when it finishes, `Should Vanish` is gone and the library matches
  the backup exactly.

**Test 6.5 — bad file**

- Do: import some random non-backup file (any `.json` or text file).
- Expect: a clear "Import failed: ..." snackbar. **No crash, and your library
  is untouched** — verify Browse still has everything.

**Test 6.6 — cancel does nothing**

- Do: tap Export, then back out of the file picker without saving. Same for
  Import.
- Expect: nothing happens, no error, no change to "Last export".

---

## 7. Knowledge graph (MEDIUM)

**Test 7.1 — whole-library graph**

- Do: Home → the **Graph** shortcut button.
- Expect: a canvas of dots joined by lines. Dot colour reflects learning
  status, size reflects how many links it has. A caption says how many posts
  and links there are.

**Test 7.2 — gestures**

- Do: drag with one finger; pinch with two.
- Expect: the graph pans and zooms smoothly. Labels appear once you are
  zoomed in enough.

**Test 7.3 — select and open**

- Do: single-tap a dot.
- Expect: a card at the bottom with the post title and its link count, plus
  an **Open** button. Tap Open → the post opens.
- Do: double-tap a dot.
- Expect: it opens the post directly.
- Do: tap empty background.
- Expect: the selection card disappears.

**Test 7.4 — tag edges**

- Do: tap the **Tags** chip, then back to **Links**.
- Expect: with Tags on, more lines appear (posts sharing a tag are joined)
  and the caption count changes.

**Test 7.5 — focused graph**

- Do: open a post → tap the graph icon in the reader.
- Expect: only that post's neighbourhood is drawn, centred on it. A chip
  appears to widen to the whole library — tap it and the full graph loads.

**Test 7.6 — isolated post**

- Do: create a post with no links and open its focused graph.
- Expect: a friendly empty message or a single dot — not a crash and not an
  endless spinner.

**Test 7.7 — rotation**

- Do: rotate the device while the graph is open.
- Expect: the graph survives; it does not reset to a blank screen.

---

## 8. Dictionary, tags, favorites (MEDIUM)

**Test 8.1 — dictionary list**

- Do: Home → **Dictionary** shortcut.
- Expect: a list of every term, with definition and Vietnamese meaning.

**Test 8.2 — add / edit / delete a term**

- Do: tap the **+** FAB, add term `Room`, definition `Android's SQLite ORM`,
  Save.
- Expect: it appears in the list.
- Do: tap the row → edit the definition → Save.
- Expect: the row updates.
- Do: **⋮** on the row → Delete.
- Expect: it disappears with an **Undo** snackbar. Tap Undo → it comes back.

**Test 8.3 — dictionary filters**

- Do: tap **Global**, then **Per post**, then **All**.
- Expect: Global shows terms not attached to a post (like `Room` above), Per
  post shows terms added from the editor (like `Coroutine`), All shows both.
- Do: type in the search field.
- Expect: the list narrows as you type.

**Test 8.4 — tags index**

- Do: Home → **Tags** shortcut.
- Expect: every tag, alphabetical, with a post count. Tap one → the posts
  carrying it. Tap a post → it opens.

**Test 8.5 — tags stay fresh**

- Do: add a new tag to a post in the editor, save, then open the Tags screen.
- Expect: the new tag is listed immediately, not missing until restart.

**Test 8.6 — favorites**

- Do: Home → **Favorites** shortcut (or tap the Favorites header).
- Expect: exactly the posts you starred. Unstar one in the reader, come back
  → it is gone from the list.

---

## 9. Settings, theme, polish (MEDIUM)

**Test 9.1 — theme**

- Do: Settings → Appearance. Six theme cards, each showing its own colours.
- Do: tap **Mocha Dark**.
- Expect: the whole app turns dark immediately, including screens you visit
  afterwards. No unreadable dark-on-dark text anywhere — check Home, Browse,
  Reader, Chat, Graph.
- Do: pick **Mocha Light**, then **Mocha**, and change the system theme.
- Expect: the app follows each time.

**Test 9.2 — named palettes**

- Do: pick **Rosé Pine**, then **Catppuccin**, then **Nord**.
- Expect: each repaints the whole app — background, header, status bar, list
  accents, chat bubbles, the status meter. The card you picked is the one with the
  accent border and the tick. Walk Home, Browse, Reader and Chat after each and
  check nothing kept the old palette's brown.
- Expect: all three stay dark even if the phone is in light mode.

**Test 9.3 — theme survives restart**

- Do: set Rosé Pine, force-stop the app (or swipe it away), reopen.
- Expect: it opens in Rosé Pine, with no light flash on the first frame.

**Test 9.4 — gateway URL validation**

- Do: Settings → AI gateway → type `not a url` → Save.
- Expect: a snackbar saying it cannot be used, and the field goes back to the
  previous valid address.
- Do: type `192.168.1.5:8787` (no `http://`) → Save.
- Expect: it is accepted and shown back as `http://192.168.1.5:8787/`.
- Do: tap **Reset**.
- Expect: back to `http://10.0.2.2:8787/`.

**Test 9.5 — backup reminder toggle**

- Do: Settings → toggle **Remind me weekly** on. Android 13+ asks for
  notification permission — allow it.
- Expect: the switch stays on. If you deny the permission instead, the switch
  goes back off rather than lying to you.

**Test 9.6 — privacy text**

- Do: scroll to the bottom of Settings.
- Expect: a privacy paragraph explaining that data stays on the device and
  what is sent to the AI. Read it — it should match what you observed in §5.

---

## 9b. Navigation, prerequisites and branch reading (HIGH)

**Test 9b.1 — home button escapes a deep trail**

- Do: open any post, then follow six or seven `[[wiki-links]]` in a row.
- Do: tap the **⌂** button beside Back in the header.
- Expect: you land on Home in one tap. Pressing Back from Home does not walk
  back into the trail — the whole chain was cleared, not hidden.

**Test 9b.2 — the tab bar hides while reading**

- Do: open a long post. Scroll down.
- Expect: the five tabs slide away and the text keeps its place — the words you
  were reading do not jump.
- Do: scroll up a little.
- Expect: the tabs slide back. They are also back whenever you are near the top.
- Do: leave the post while the bar is hidden.
- Expect: Browse (or wherever you land) has its tabs.

**Test 9b.3 — the editor and review screen keep their tabs hidden**

- Do: open a post → **Edit**. Then from a chat, open a **Review changes** screen.
- Expect: no tab bar on either. These are save-or-discard screens, and a stray
  tab tap mid-edit would lose the draft.

**Test 9b.4 — declare a prerequisite**

- Do: create or open a post, tap **Edit**, tap **Prerequisites**.
- Expect: a multi-select list of every other post. The post you are editing is
  not in it.
- Do: tick two, **Save**, then **Save** the post.
- Do: open that post in the reader.
- Expect: a **Prerequisites** card above the tags, with a status bar in the same
  colours Browse uses over a folder, "N of 2 started", and both posts listed.
- Do: tap one of the listed rows.
- Expect: it opens that post.

**Test 9b.5 — the bar tracks status**

- Do: from the prerequisite card, open one prerequisite and set it to
  **Reading**. Go back.
- Expect: the bar moved and the caption counts one more started. When every
  prerequisite has been started, the caption says you are ready.
- Note: being unready never blocks the post. The card says what is missing and
  you can read on regardless.

**Test 9b.6 — no card without prerequisites**

- Do: open a post that has none.
- Expect: no Prerequisites card at all — not an empty "0 of 0".

**Test 9b.7 — a loop is refused, the rest is kept**

- Do: make A require B. Then edit B and tick both A and some third post C.
- Expect: Save succeeds, a message says one prerequisite was dropped as a loop,
  and B ends up requiring C only.

**Test 9b.8 — sort and filter by readiness**

- Do: Browse → **Sort** → **Ready to read**.
- Expect: posts you can pick up now come first; blocked ones follow, the closest
  to ready first.
- Do: Browse → **Filter** → turn on **Ready to read** → Apply.
- Expect: blocked posts vanish, the Filter button shows a count, and folders and
  branches stay visible so you can still walk the tree.

**Test 9b.9 — read a branch end to end**

- Do: Browse → the **⋮** menu on a branch or folder → **Read this branch**.
- Expect: the first post of that branch opens with a strip under the header
  reading "Branch · 1 of N", and prev/next arrows.
- Do: tap next repeatedly to the end.
- Expect: the position counts up; the next arrow greys out at the last post and
  the prev arrow at the first.
- Do: press **Back** from the middle of the branch.
- Expect: you leave the branch entirely and return to Browse — *not* one post
  back. Twelve posts read must not mean twelve Backs.

**Test 9b.10 — the branch respects prerequisites and chains**

- Do: inside a branch, make a later post a prerequisite of an earlier one, then
  start the branch again.
- Expect: the prerequisite is read first.
- Do: on a branch whose posts are chained with **Next post**, start the branch.
- Expect: the reading order follows the chain, not the alphabet.

**Test 9b.11 — branch structure sheet**

- Do: during a branch read, tap the folder icon in the strip.
- Expect: a sheet showing the branch's folders and posts, indented, with the
  post you are on highlighted.
- Do: tap another post in the sheet.
- Expect: it opens, still inside the branch, and the strip's position updates.

**Test 9b.12 — following a link leaves the branch**

- Do: during a branch read, tap a `[[wiki-link]]` in the body.
- Expect: that post opens with **no** branch strip — you left the session. Back
  returns you to where you were in the branch.

**Test 9b.13 — an empty branch says so**

- Do: create an empty folder, then **Read this branch** on it.
- Expect: a message saying there is nothing to read. Nothing opens.

**Test 9b.14 — swipe a chat away**

- Do: AI tab → swipe a chat row to the left, slowly.
- Expect: a red panel grows behind the row and a bin fades in once you are past
  about a quarter of the width.
- Do: let go before roughly half way.
- Expect: the row springs back, nothing is deleted.
- Do: swipe past half and let go.
- Expect: a confirmation dialog. **Cancel** — the row comes back and the chat is
  still there. Swipe again and confirm — it is gone.
- Do: tap outside the dialog instead of cancelling.
- Expect: same as cancel; the row must not be left swiped off screen.
- Do: long-press a chat row.
- Expect: the same delete dialog. Both routes still work.

**Test 9b.15 — Browse swipes the same way**

- Do: swipe a Browse row left.
- Expect: the identical red panel and bin, the same confirmation. The gesture
  means one thing in both places.

---

## 10. Robustness (do last)

**Test 10.1 — rotation everywhere**

- Do: rotate the device on each screen: Home, Browse (2 levels deep), Reader,
  Editor (mid-typing), Search (with a query), Chat conversation, Review
  changes, Graph, Settings.
- Expect: no crash. Text you typed in the editor and the search query survive
  the rotation.

**Test 10.2 — back button chain**

- Do: from Home, go Post → Editor → Save → Graph → back, back, back…
- Expect: you end up back at Home one step at a time, and only then does Back
  leave the app. No screen appears twice in a row.

**Test 10.3 — empty library**

- Do: delete every branch (or import an empty backup).
- Expect: Home, Browse, Search and Graph all show polite empty states — not
  spinners, not crashes. The **+** FAB still works to start again.

**Test 10.4 — long text**

- Do: create a post with a very long title (200+ characters) and a very long
  body (paste several pages).
- Expect: it saves, the reader scrolls smoothly, the title ellipsizes rather
  than breaking the layout.

**Test 10.5 — airplane mode**

- Do: turn on airplane mode, then use Browse, Search, Reader, Editor, Graph
  and Export.
- Expect: everything works exactly as before. Only the Chat tab shows its
  offline banner.

**Test 10.6 — tablet / large screen**

- Do: run on a tablet emulator or resize the window.
- Expect: content stays readable and centred with sensible margins — not one
  column of text stretched across the whole width.

---

## How to report a problem

When something differs from the Expect, send me:

1. **The test number**, e.g. "Test 5.9 failed".
2. **What you did** — the exact taps, and for AI tests the exact prompt and
   which mode chip was selected.
3. **What actually happened** — plainly ("the Apply button stayed greyed
   out", "the app closed", "the branch appeared but the posts inside were
   empty"). A screenshot or screen recording helps a lot.
4. **The log**, if the app crashed or misbehaved. Capture it like this:

   ```powershell
   adb logcat -c                      # clear first
   # ...now reproduce the problem in the app...
   adb logcat -d > crash.txt          # dump everything
   ```

   Or filter to just this app:

   ```powershell
   adb logcat -d --pid=$(adb shell pidof -s com.cs426.learningmocha) > crash.txt
   ```

   For AI problems, also copy what the **backend terminal** printed at that
   moment — that shows what DeepSeek actually returned.

The most useful reports are the boring ones: exact steps, what you saw, and
the log. I can usually find the bug from those three alone.
