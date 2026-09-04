You are the lead architect for a school final project. Design the full implementation plan for a **local-first Android personal learning/knowledge management app** called **Learning Mocha**.

## Design reference

An existing Android project is located at:

`C:\Workspace\Learning\Mobile\CS426-Midterm`

Use it as the **UI/UX and visual design reference**.

Learning Mocha should feel:

* Minimal
* Neutral
* Calm and soothing
* Warm, like a coffee shop
* Clean and comfortable for long reading sessions
* Low visual clutter

Reuse the useful visual language and interaction patterns where appropriate, but do not assume its architecture should be reused.

## Technology

* Android application
* Java + Kotlin
* All user data lives locally on the Android device.
* A small backend server exists only as a gateway to the DeepSeek API.
* The app must remain usable when the backend is unavailable.

## Core concept

Learning Mocha is a personal **Wikipedia + learning tracker + knowledge graph + AI learning assistant**.

The knowledge base is organized into:

* Branches
* Folders
* Subfolders
* Posts/articles

Example:

Programming
→ Backend
→ Java
→ Spring Boot
→ REST API

I can create, rename, move, reorder, and delete these items.

## Posts

Each post is a Wikipedia-like learning article containing:

* Title
* Markdown/rich content
* Internal links to other posts
* Backlinks
* Related posts
* External references and learning resources
* Embedded YouTube videos
* Tags
* Status such as Reading, In Progress, Finished
* Favorite
* Dictionary/glossary

Any term can link to another post.

Example:

"My project uses **Spring Boot**."

Clicking Spring Boot opens my Spring Boot post.

## Dictionary

Every post can contain difficult, unfamiliar, abbreviated, or domain-specific terms.

Example:

RAG
→ Retrieval-Augmented Generation
→ English explanation
→ Vietnamese meaning

Definitions should be accessible without disrupting reading.

Consider both page-specific definitions and a reusable global dictionary.

## AI learning assistant

Learning Mocha contains a chat interface connected to DeepSeek through the backend.

This is not just a chatbot. The AI should be able to **operate on the local knowledge base through structured actions**.

Examples:

> "Create a branch called Distributed Systems."

> "Under Distributed Systems, create posts for Consensus, Replication, Fault Tolerance and Raft."

> "Create a Spring Boot post and link it to my Java post."

> "Add a YouTube resource about JVM garbage collection to the JVM post."

> "Mark Spring Boot as Finished."

> "Look at my Backend branch and organize it better."

> "I want to learn Kubernetes. Based on what I already know, create a learning path for me."

The AI should be able to inspect relevant local knowledge, reason about it, and propose changes.

### AI capabilities

Design four levels of interaction:

1. **Answer**

   * Normal conversational questions.
   * No modifications.

2. **Suggest**

   * AI recommends posts, learning paths, links, resources, or dictionary terms.
   * User can choose which suggestions to apply.

3. **Modify**

   * AI proposes concrete knowledge-base changes.
   * Example: create/move/edit/link/tag posts.
   * User reviews the proposed changes before applying them.

4. **Organize**

   * AI analyzes an existing branch or collection and proposes restructuring.
   * Example: moving posts, creating folders, adding links, detecting duplicates, etc.
   * Changes must still be reviewable before execution.

## AI action system

The AI must **never directly manipulate the Android database**.

Design an application-level action protocol.

Conceptually:

AI → structured actions → Android validation → user review → local execution

Example actions:

* `create_branch`
* `create_folder`
* `create_post`
* `update_post`
* `move_post`
* `delete_post`
* `create_link`
* `remove_link`
* `add_tag`
* `remove_tag`
* `set_status`
* `set_favorite`
* `add_resource`
* `add_dictionary_entry`

Design the exact action schema, validation rules, error handling, transaction behavior, and confirmation UX.

The protocol should be model-independent so DeepSeek can potentially be replaced later.

## AI knowledge access

Do not send the entire knowledge base to DeepSeek on every request.

Design a tool/context mechanism that allows the AI to access only relevant local information.

Potential operations:

* Search posts
* Get a post
* List children of a branch
* Find backlinks
* Search dictionary
* Get tags
* Inspect related posts

The Android application should remain the source of truth.

Consider whether these operations should be implemented as explicit AI tools, structured requests, or another lightweight mechanism compatible with the DeepSeek API.

## Example AI workflow

For:

> "I want to learn Distributed Systems."

The AI should be able to:

1. Inspect existing knowledge.
2. Determine what I already know.
3. Identify missing prerequisite topics.
4. Propose a learning tree.
5. Generate proposed posts.
6. Generate internal links.
7. Generate dictionary entries.
8. Present a change preview.
9. Apply everything locally after approval.

Example result:

Distributed Systems
├── Fundamentals
│   ├── Architecture
│   ├── Communication
│   └── Failure Models
├── Consistency
│   ├── Replication
│   └── Consistency Models
└── Consensus
├── Leader Election
└── Raft

This **AI-generated learning path** should be considered a potentially important signature feature of the application.

## AI-generated posts

The AI should also be able to generate complete learning articles.

For example:

> "Create a post teaching me Raft. I understand basic distributed systems but don't know consensus algorithms."

The app should show a preview before saving:

* Generated article
* Internal links
* References
* Dictionary entries
* Tags
* Suggested location

The user can approve, edit, or reject it.

## Local-first storage

Everything except DeepSeek communication is local.

Design the best storage architecture for:

* Posts
* Branch/folder hierarchy
* Internal links
* Backlinks
* Tags
* Favorites
* Status
* Dictionary entries
* Resources
* Search index
* Metadata

Evaluate Room/SQLite, files, and hybrid approaches.

Prioritize:

* Simplicity
* Reliability
* Offline operation
* Easy backup/export
* Easy migration
* Maintainability

## Search and navigation

Provide fast local search across:

* Titles
* Content
* Tags
* Dictionary terms
* References

Consider:

* Breadcrumbs
* Backlinks
* Related pages
* Recent pages
* Favorites
* Tags
* Lightweight knowledge graph visualization

## Main UI

Design the Android navigation and major screens:

* Home / Knowledge Hub
* Branch/folder browser
* Post reader
* Post editor
* Search
* Favorites
* Tags
* Dictionary
* AI Chat
* AI change preview/review
* Settings
* Backup / Import / Export

Use drag-and-drop where it meaningfully improves organization.

Follow the visual style of `CS426-Midterm`.

## Backend

Keep the backend intentionally tiny:

Android App
→ Backend
→ DeepSeek API
→ Backend
→ Android App

The backend should primarily handle:

* DeepSeek authentication
* API requests
* Prompt/context construction if appropriate
* Streaming
* Structured responses
* Error handling
* Model abstraction

Do not unnecessarily store the user's knowledge base on the server.

## Privacy

The knowledge base is local.

Clearly identify:

* What information is sent to DeepSeek
* How context is selected
* What never leaves the device
* How API credentials are protected
* What happens when the backend is unavailable

## Development tooling

Cursor and Claude Code are **development tools, not features of the Android application**.

They may be used while developing Learning Mocha.

If useful, design a developer skill/specification that helps Cursor or Claude Code understand:

* The project architecture
* Database schema
* Post format
* AI action protocol
* UI conventions
* How to safely modify the codebase

Do not treat Cursor/Claude Code as dependencies of the finished application.

## School final project

The architecture should be realistic for a student final project while demonstrating meaningful engineering:

* Clean Android architecture
* Java/Kotlin
* Local database
* Offline-first design
* Knowledge graph
* Search
* AI integration
* Structured AI actions
* API design
* Testing
* Privacy considerations
* Non-trivial Android UI

Avoid unnecessary:

* Microservices
* Cloud databases
* Authentication systems
* Complex DevOps
* Over-engineered abstractions

## What I want from your response

Create a **complete implementation plan, not code**.

Cover:

1. Overall architecture
2. Android architecture
3. Java/Kotlin responsibility split
4. Local storage
5. Data models
6. Knowledge graph/link system
7. Content format
8. Search
9. Dictionary
10. AI chat
11. AI context/tool system
12. DeepSeek/backend API
13. AI action protocol
14. Action validation and transactions
15. AI change-review UX
16. AI-generated learning paths
17. AI-generated posts
18. UI/navigation
19. Design system based on CS426-Midterm
20. Backup/import/export
21. Offline behavior
22. Privacy/security
23. Testing
24. Performance
25. Developer tooling
26. Development phases

At the end provide:

* Recommended final architecture
* Repository/project tree
* Core data models
* Core API endpoints
* AI action schema
* AI interaction flow
* Main screens
* Design system
* Development roadmap from empty repository to final-project-ready application
* A clear list of what should NOT be built in v1

Favor the simplest architecture that satisfies the requirements. Make practical decisions rather than presenting endless alternatives.

Do not write implementation code. Produce a plan detailed enough that another coding agent can implement the project.
