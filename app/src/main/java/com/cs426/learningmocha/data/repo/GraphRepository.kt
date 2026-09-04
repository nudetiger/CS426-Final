package com.cs426.learningmocha.data.repo

import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.SeedData
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node

enum class GraphEdgeType {
    /** An explicit `[[wiki-link]]` between two posts. */
    LINK,

    /** Two posts share at least one tag. */
    TAG,
}

data class GraphNode(
    val id: Long,
    val title: String,
    val status: LearningStatus,
    val favorite: Boolean,
    val degree: Int,
)

/** Endpoints are indices into [GraphSnapshot.nodes] so the layout can stay primitive-typed. */
data class GraphEdge(
    val from: Int,
    val to: Int,
    val type: GraphEdgeType,
)

/**
 * Immutable picture of the library at one moment.
 *
 * [candidateCount] is how many posts qualified before the node cap, so the UI can say out loud
 * that it is showing a subset instead of quietly dropping posts.
 */
data class GraphSnapshot(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val candidateCount: Int,
    val focusIndex: Int,
) {
    val truncated: Boolean get() = nodes.size < candidateCount
}

/**
 * Read-only projection of posts + links + tags into a graph.
 *
 * Uses existing DAO reads only and does all the shaping in memory: the library is small enough
 * that one pass over `nodes`, `links` and `post_tags` beats a bespoke recursive query.
 */
class GraphRepository(private val db: AppDatabase) {

    private val dao = db.nodeDao()
    private val knowledge = db.knowledgeDao()

    /**
     * @param focusPostId `0` for the whole library, otherwise that post plus its one-hop
     *   neighbourhood (incoming and outgoing links, and tag neighbours when [includeTagEdges])
     * @param includeTagEdges also connect posts that share a tag
     */
    suspend fun snapshot(focusPostId: Long, includeTagEdges: Boolean): GraphSnapshot {
        SeedData.ensureSeeded(db)
        val posts = dao.getPosts()
        if (posts.isEmpty()) {
            return GraphSnapshot(emptyList(), emptyList(), 0, NO_FOCUS)
        }
        val byId = posts.associateBy { it.id }

        // Links are directed in the database but the drawing is undirected, so a pair that
        // links both ways collapses to one edge. LINK always wins over TAG for the same pair.
        val edgeTypes = LinkedHashMap<PostPair, GraphEdgeType>()
        knowledge.allLinks().forEach { link ->
            val from = byId[link.fromPostId]
            val to = byId[link.toPostId]
            if (from == null || to == null || from.id == to.id) return@forEach
            edgeTypes[pairOf(from.id, to.id)] = GraphEdgeType.LINK
        }
        if (includeTagEdges) {
            tagPairs(byId.keys).forEach { pair ->
                if (!edgeTypes.containsKey(pair)) edgeTypes[pair] = GraphEdgeType.TAG
            }
        }

        val neighbours = HashMap<Long, MutableSet<Long>>()
        edgeTypes.keys.forEach { pair ->
            neighbours.getOrPut(pair.low) { mutableSetOf() }.add(pair.high)
            neighbours.getOrPut(pair.high) { mutableSetOf() }.add(pair.low)
        }

        val focus = if (focusPostId != 0L) byId[focusPostId] else null
        val candidates = if (focus == null) {
            posts
        } else {
            val keep = HashSet<Long>()
            keep.add(focus.id)
            neighbours[focus.id]?.let { keep.addAll(it) }
            posts.filter { it.id in keep }
        }

        val kept = capNodes(candidates, neighbours, focus)
        val indexById = HashMap<Long, Int>(kept.size)
        kept.forEachIndexed { index, node -> indexById[node.id] = index }

        val degree = IntArray(kept.size)
        val edges = ArrayList<GraphEdge>(edgeTypes.size)
        for ((pair, type) in edgeTypes) {
            val from = indexById[pair.low] ?: continue
            val to = indexById[pair.high] ?: continue
            edges.add(GraphEdge(from, to, type))
            degree[from]++
            degree[to]++
        }

        return GraphSnapshot(
            nodes = kept.mapIndexed { index, node ->
                GraphNode(
                    id = node.id,
                    title = node.title,
                    status = node.status,
                    favorite = node.favorite,
                    degree = degree[index],
                )
            },
            edges = edges,
            candidateCount = candidates.size,
            focusIndex = if (focus == null) NO_FOCUS else (indexById[focus.id] ?: NO_FOCUS),
        )
    }

    /** Keeps the most-connected posts (plus the focus, always) so the picture stays readable. */
    private fun capNodes(
        candidates: List<Node>,
        neighbours: Map<Long, Set<Long>>,
        focus: Node?,
    ): List<Node> {
        if (candidates.size <= MAX_NODES) return candidates
        val ranked = candidates.sortedWith(
            compareByDescending<Node> { neighbours[it.id]?.size ?: 0 }
                .thenByDescending { it.updatedAt }
                .thenBy { it.title.lowercase() },
        )
        val head = ranked.take(MAX_NODES)
        if (focus == null || head.any { it.id == focus.id }) return head
        return listOf(focus) + head.dropLast(1)
    }

    private suspend fun tagPairs(postIds: Set<Long>): List<PostPair> {
        val membersByTag = HashMap<Long, MutableSet<Long>>()
        knowledge.allPostTags().forEach { postTag ->
            if (postTag.postId in postIds) {
                membersByTag.getOrPut(postTag.tagId) { mutableSetOf() }.add(postTag.postId)
            }
        }
        val pairs = ArrayList<PostPair>()
        membersByTag.values.forEach { members ->
            if (members.size < 2) return@forEach
            val ids = members.sorted()
            if (ids.size <= TAG_CLIQUE_MAX) {
                for (i in ids.indices) {
                    for (j in i + 1 until ids.size) {
                        pairs.add(PostPair(ids[i], ids[j]))
                    }
                }
            } else {
                // A broad tag as a clique would add O(k^2) edges and bury the wiki-links under
                // a grey mesh. A ring still reads as "these belong together" for O(k) edges.
                for (i in ids.indices) {
                    pairs.add(pairOf(ids[i], ids[(i + 1) % ids.size]))
                }
            }
        }
        return pairs
    }

    private data class PostPair(val low: Long, val high: Long)

    private fun pairOf(a: Long, b: Long): PostPair =
        if (a <= b) PostPair(a, b) else PostPair(b, a)

    companion object {
        /** Past this the layout is a hairball and its cost grows quadratically. */
        const val MAX_NODES = 250

        private const val TAG_CLIQUE_MAX = 8

        const val NO_FOCUS = -1
    }
}
