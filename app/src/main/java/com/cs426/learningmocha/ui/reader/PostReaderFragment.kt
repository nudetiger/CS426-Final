package com.cs426.learningmocha.ui.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.cs426.learningmocha.MainActivity
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.ResourceType
import com.cs426.learningmocha.databinding.FragmentPostReaderBinding
import com.cs426.learningmocha.databinding.ItemResourceBinding
import com.cs426.learningmocha.ui.common.ChipBar
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.common.NodePalette
import com.cs426.learningmocha.ui.browse.OutlineRow
import com.cs426.learningmocha.ui.common.PostMarks
import com.cs426.learningmocha.ui.common.Readiness
import com.cs426.learningmocha.ui.common.StatusMeterBinder
import com.cs426.learningmocha.ui.common.WikiMarkdown
import com.cs426.learningmocha.ui.common.YouTubeThumbnails
import com.cs426.learningmocha.ui.common.labelRes
import com.cs426.learningmocha.ui.common.stripe
import com.cs426.learningmocha.ui.common.themeColor
import com.cs426.learningmocha.viewmodel.BranchSession
import com.cs426.learningmocha.viewmodel.PostReaderViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch

class PostReaderFragment : Fragment() {

    private var binding: FragmentPostReaderBinding? = null
    private val viewModel: PostReaderViewModel by viewModels()
    private var markwon: Markwon? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentPostReaderBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        markwon = buildMarkwon()

        // Reading comfort from Settings. Applied here rather than in the theme because the
        // values are per-user, and only long-form body text should follow them.
        val settings = (requireActivity().application as com.cs426.learningmocha.LearningMochaApp)
            .settings
        b.readerBody.textSize =
            resources.getDimension(R.dimen.reader_text) / resources.displayMetrics.scaledDensity *
            settings.readerTextScale
        b.readerBody.setLineSpacing(0f, settings.readerLineSpacing)

        b.readerBack.setOnClickListener { findNavController().popBackStack() }
        b.readerHome.setOnClickListener {
            // Home is the graph's start destination, so it is always the bottom of the stack:
            // one pop clears however many posts the trail collected on the way here.
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        bindNavOnScroll(b)
        b.readerFavorite.setOnClickListener { viewModel.toggleFavorite() }
        b.readerGraph.setOnClickListener {
            findNavController().navigate(
                R.id.action_global_graph,
                bundleOf("focusPostId" to viewModel.postId),
            )
        }
        b.readerEdit.setOnClickListener {
            findNavController().navigate(
                R.id.action_reader_to_editor,
                bundleOf(
                    "postId" to viewModel.postId,
                    "parentId" to -1L,
                ),
            )
        }
        b.readerBranchOutline.setOnClickListener { showOutline() }
        b.readerStatus.setOnClickListener { pickStatus() }
        b.readerDictionaryAll.setOnClickListener {
            findNavController().navigate(R.id.action_global_dictionary)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.readerContent,
                        state = state.listState,
                        emptyText = getString(R.string.reader_missing),
                        errorText = state.errorMessage,
                        offlineText = getString(R.string.state_offline),
                        onRetry = viewModel::retry,
                    )
                    val post = state.post
                    if (state.listState == ListState.CONTENT && post != null) {
                        b.readerTitle.text = post.title
                        b.readerFavorite.setImageResource(
                            if (post.favorite) R.drawable.ic_star else R.drawable.ic_star_border,
                        )
                        b.readerFavorite.contentDescription = getString(
                            if (post.favorite) {
                                R.string.reader_cd_favorite_remove
                            } else {
                                R.string.reader_cd_favorite_add
                            },
                        )
                        b.readerStatus.setText(post.status.labelRes())
                        // The same status colours Browse uses, so the button is recognisable
                        // as the thing that was amber in the list a tap ago.
                        b.readerStatus.setTextColor(
                            requireContext().themeColor(NodePalette.statusInk(post.status)),
                        )
                        b.readerStatus.backgroundTintList = ColorStateList.valueOf(
                            requireContext().themeColor(NodePalette.statusWash(post.status)),
                        )
                        b.readerStatus.contentDescription = getString(
                            R.string.reader_cd_status,
                            getString(post.status.labelRes()),
                        )
                        bindBreadcrumbs(b.readerPath, state.breadcrumbs)
                        bindPrerequisites(b, state.readiness)
                        bindBranch(b, state.branch)
                        ChipBar.bind(b.readerTags, state.tags.map { tag ->
                            tag.name to {
                                findNavController().navigate(
                                    R.id.action_global_tag,
                                    bundleOf("tagId" to tag.id),
                                )
                            }
                        })
                        val markdown = WikiMarkdown.rewrite(post.content.orEmpty(), state.titleToId)
                        markwon?.setMarkdown(b.readerBody, markdown)
                        b.readerTermsHeader.isVisible = state.terms.isNotEmpty()
                        ChipBar.bind(b.readerTerms, state.terms.map { term ->
                            term.term to { showTerm(term) }
                        })
                        bindResources(
                            b.readerResourcesHeader,
                            b.readerResources,
                            state.resources,
                        )
                        bindNodeRows(b.readerChildrenHeader, b.readerChildren, state.children)
                        bindNodeRows(b.readerBacklinksHeader, b.readerBacklinks, state.backlinks)
                        bindNodeRows(b.readerRelatedHeader, b.readerRelated, state.related)
                        val next = state.nextPost
                        b.readerNext.isVisible = next != null
                        if (next != null) {
                            b.readerNextTitle.text = next.title
                            b.readerNext.setOnClickListener {
                                findNavController().navigate(
                                    R.id.action_global_open_post,
                                    bundleOf("postId" to next.id),
                                )
                            }
                        } else {
                            b.readerNext.setOnClickListener(null)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        markwon = null
    }

    /**
     * Hands the tab bar back to the reader while the user is going down the page, and returns
     * it the moment they scroll up — the direction people already move in when they are looking
     * for a way out. Purely presentational: nothing here changes what is on the back stack.
     *
     * The threshold keeps a fling's overscroll wobble from flickering the bar, and the last
     * branch guarantees a post too short to reach it never strands the user without tabs.
     */
    private fun bindNavOnScroll(b: FragmentPostReaderBinding) {
        val slop = (SCROLL_SLOP_DP * resources.displayMetrics.density).toInt()
        b.readerContent.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val host = activity as? MainActivity ?: return@OnScrollChangeListener
                val delta = scrollY - oldScrollY
                when {
                    scrollY <= slop -> host.setBottomNavVisible(true)
                    delta > slop -> host.setBottomNavVisible(false)
                    delta < -slop -> host.setBottomNavVisible(true)
                }
            },
        )
    }

    private fun buildMarkwon(): Markwon {
        val defaults = LinkResolverDef()
        return Markwon.builder(requireContext())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, link ->
                        when {
                            link.startsWith(WikiMarkdown.POST_PREFIX) -> {
                                val id = link.removePrefix(WikiMarkdown.POST_PREFIX).toLongOrNull()
                                if (id != null) {
                                    findNavController().navigate(
                                        R.id.action_global_open_post,
                                        bundleOf("postId" to id),
                                    )
                                }
                            }
                            link.startsWith(WikiMarkdown.MISSING_PREFIX) -> {
                                val title = Uri.decode(link.removePrefix(WikiMarkdown.MISSING_PREFIX))
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.reader_link_missing, title),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            else -> defaults.resolve(view, link)
                        }
                    }
                }
            })
            .build()
    }

    private fun pickStatus() {
        val values = LearningStatus.entries.toTypedArray()
        val labels = values.map { getString(it.labelRes()) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reader_status_title)
            .setItems(labels) { _, which -> viewModel.setStatus(values[which]) }
            .show()
    }

    /**
     * A bottom sheet instead of a dialog: the definition appears without covering the paragraph
     * the term was read in, so looking a word up does not interrupt reading.
     */
    private fun showTerm(entry: DictionaryEntry) {
        val context = requireContext()
        val gutter = resources.getDimensionPixelSize(R.dimen.space_l)
        val gap = resources.getDimensionPixelSize(R.dimen.space_s)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(context.themeColor(R.attr.mochaCream))
            setPadding(gutter, gutter, gutter, gutter)
            addView(
                TextView(context).apply {
                    setTextAppearance(R.style.TextAppearance_Mocha_Title)
                    setTextColor(context.themeColor(R.attr.mochaBrown))
                    text = entry.term
                },
            )
            addView(
                TextView(context).apply {
                    setTextAppearance(R.style.TextAppearance_Mocha_Body)
                    text = entry.definition
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = gap },
            )
            if (entry.meaningVi.isNotBlank()) {
                addView(
                    TextView(context).apply {
                        setTextAppearance(R.style.TextAppearance_Mocha_Caption)
                        text = entry.meaningVi
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = gap },
                )
            }
        }
        BottomSheetDialog(context).apply {
            setContentView(content)
            show()
        }
    }

    /**
     * The branch strip. Prev and next *replace* the reader rather than stacking on it, so
     * walking twelve posts does not leave twelve entries to press Back through — Back still
     * means "leave this branch", from anywhere in it.
     */
    private fun bindBranch(b: FragmentPostReaderBinding, branch: BranchSession?) {
        b.readerBranch.isVisible = branch != null
        if (branch == null) return
        b.readerBranchTitle.text = getString(
            R.string.branch_position,
            branch.title,
            branch.position,
            branch.total,
        )
        // Disabled rather than hidden at the ends, so the two arrows do not shuffle sideways
        // as you move through the branch.
        b.readerBranchPrev.isEnabled = branch.previousId != null
        b.readerBranchPrev.alpha = if (branch.previousId != null) 1f else DISABLED_ALPHA
        b.readerBranchNext.isEnabled = branch.nextId != null
        b.readerBranchNext.alpha = if (branch.nextId != null) 1f else DISABLED_ALPHA
        b.readerBranchPrev.setOnClickListener { branch.previousId?.let(::openInBranch) }
        b.readerBranchNext.setOnClickListener { branch.nextId?.let(::openInBranch) }
    }

    private fun openInBranch(postId: Long) {
        findNavController().navigate(
            R.id.postReaderFragment,
            bundleOf("postId" to postId, "branchId" to viewModel.branchId),
            navOptions {
                popUpTo(R.id.postReaderFragment) { inclusive = true }
                anim {
                    enter = R.anim.nav_enter
                    exit = R.anim.nav_exit
                    popEnter = R.anim.nav_pop_enter
                    popExit = R.anim.nav_pop_exit
                }
            },
        )
    }

    /**
     * The branch's folders and posts, indented, with the post being read marked. This is the
     * "show me the actual structure" half of branch reading: the strip says where you are in
     * the sequence, this says where that is in the tree.
     */
    private fun showOutline() {
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = viewModel.outline()
            val context = requireContext()
            val gutter = resources.getDimensionPixelSize(R.dimen.space_m)
            val indent = resources.getDimensionPixelSize(R.dimen.space_l)
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(context.themeColor(R.attr.mochaCream))
                setPadding(0, gutter, 0, gutter)
            }
            val sheet = BottomSheetDialog(context)
            content.addView(
                TextView(context).apply {
                    setTextAppearance(R.style.TextAppearance_Mocha_Title)
                    setTextColor(context.themeColor(R.attr.mochaBrown))
                    setText(R.string.branch_outline)
                    setPadding(gutter, 0, gutter, gutter)
                },
            )
            if (rows.isEmpty()) {
                content.addView(
                    TextView(context).apply {
                        setTextAppearance(R.style.TextAppearance_Mocha_Body)
                        setText(R.string.branch_outline_empty)
                        setPadding(gutter, 0, gutter, 0)
                    },
                )
            }
            rows.forEachIndexed { index, entry -> content.addView(outlineRow(entry, index, indent, sheet)) }
            sheet.setContentView(
                androidx.core.widget.NestedScrollView(context).apply { addView(content) },
            )
            sheet.show()
        }
    }

    private fun outlineRow(
        entry: OutlineRow,
        index: Int,
        indent: Int,
        sheet: BottomSheetDialog,
    ): View {
        val row = layoutInflater.inflate(R.layout.item_home_row, null, false)
        row.stripe(index)
        row.setPadding(indent * entry.depth, row.paddingTop, 0, row.paddingBottom)
        val title = row.findViewById<TextView>(R.id.row_title)
        title.text = entry.node.title
        // The post you are on is the one thing the sheet has to answer at a glance.
        val current = entry.node.id == viewModel.postId
        title.setTextColor(
            requireContext().themeColor(
                if (current) R.attr.mochaBrown else R.attr.mochaTextPrimary,
            ),
        )
        row.findViewById<TextView>(R.id.row_caption)
            .setText(NodePalette.typeLabelRes(entry.node.type))
        PostMarks.paint(
            row.findViewById<ImageView>(R.id.row_icon),
            entry.node,
            fallback = NodePalette.typeInk(entry.node.type),
        )
        if (entry.node.type == NodeType.POST) {
            row.setOnClickListener {
                sheet.dismiss()
                if (!current) openInBranch(entry.node.id)
            }
        }
        return row
    }

    /**
     * The readiness card. Absent entirely on a post with no prerequisites, which is most of
     * them — an empty "Prerequisites: 0 of 0" would be noise on every article in the library.
     *
     * Being unready is never a lock. The rows say what is missing and the caption offers to
     * read on anyway: this is a learning tracker, not a course with gates in it.
     */
    private fun bindPrerequisites(b: FragmentPostReaderBinding, readiness: Readiness) {
        b.readerPrereq.isVisible = readiness.total > 0
        if (readiness.total == 0) return
        b.readerPrereqSummary.text = buildString {
            append(
                getString(R.string.reader_prereq_summary, readiness.started, readiness.total),
            )
            append(" · ")
            append(
                getString(
                    if (readiness.isReady) {
                        R.string.reader_prereq_ready
                    } else {
                        R.string.reader_prereq_blocked
                    },
                ),
            )
        }
        // Read-only here: tapping a share on a post's own bar has nothing to filter, and the
        // caption above already gives the count the meter would otherwise repeat.
        StatusMeterBinder.bind(b.readerPrereqMeter, readiness.stats, showSummary = false)
        bindNodeRows(b.readerPrereqHeader, b.readerPrereqList, readiness.required)
        // bindNodeRows hides its header when the list is empty; this one is never empty here,
        // and the card's own visibility already answers that question.
        b.readerPrereqHeader.isVisible = true
    }

    private fun bindBreadcrumbs(path: TextView, crumbs: List<Node>) {
        path.isVisible = crumbs.isNotEmpty()
        if (crumbs.isEmpty()) return
        path.text = crumbs.joinToString(getString(R.string.reader_path_separator)) { it.title }
    }

    private fun bindResources(
        header: TextView,
        container: LinearLayout,
        items: List<ResourceItem>,
    ) {
        header.isVisible = items.isNotEmpty()
        container.isVisible = items.isNotEmpty()
        container.removeAllViews()
        for (item in items) {
            val card = ItemResourceBinding.inflate(layoutInflater, container, false)
            val typeLabel = getString(resourceLabel(item.type))
            val title = item.title.ifBlank { typeLabel }
            card.resourceTitle.text = title
            card.resourceType.text = typeLabel
            // An inline YouTube URL has no stored title, so its card would say "YouTube" twice.
            card.resourceType.isVisible = !title.equals(typeLabel, ignoreCase = true)
            card.resourceUrl.text = hostOf(item.url)
            card.resourceIcon.setImageResource(
                if (item.type == ResourceType.YOUTUBE) R.drawable.ic_play else R.drawable.ic_post,
            )
            card.resourceIcon.contentDescription = typeLabel
            bindThumbnail(card, item)
            card.root.setOnClickListener { openResource(item, title) }
            container.addView(card.root)
        }
    }

    /**
     * Swaps the play glyph for the video's own poster frame, which is the only thing on the
     * card that says *which* video it is — the title of an inline link is usually just the URL.
     * The glyph stays as the fallback: no network, or a video with no thumbnail, leaves the
     * card exactly as it looked before rather than a grey rectangle.
     */
    private fun bindThumbnail(card: ItemResourceBinding, item: ResourceItem) {
        val videoId = if (item.type == ResourceType.YOUTUBE) {
            YouTubePlayerSheet.videoId(item.url)
        } else {
            null
        }
        if (videoId == null) {
            card.resourceThumbFrame.isVisible = false
            card.resourceIcon.isVisible = true
            return
        }
        card.resourceThumbFrame.isVisible = true
        card.resourceIcon.isVisible = false
        YouTubeThumbnails.into(
            card.resourceThumb,
            videoId,
            viewLifecycleOwner.lifecycleScope,
        ) {
            card.resourceThumbFrame.isVisible = false
            card.resourceIcon.isVisible = true
        }
    }

    /** YouTube plays inline; anything else — and any link with no video id — leaves the app. */
    private fun openResource(item: ResourceItem, title: String) {
        if (item.type == ResourceType.YOUTUBE) {
            val videoId = YouTubePlayerSheet.videoId(item.url)
            if (videoId != null) {
                YouTubePlayerSheet.show(parentFragmentManager, videoId, item.url, title)
                return
            }
        }
        openExternally(item.url)
    }

    private fun openExternally(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.reader_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hostOf(url: String): String {
        val host = Uri.parse(url).host?.removePrefix("www.")
        return if (host.isNullOrBlank()) url else host
    }

    private fun bindNodeRows(header: TextView, container: LinearLayout, nodes: List<Node>) {
        header.isVisible = nodes.isNotEmpty()
        container.removeAllViews()
        for ((index, node) in nodes.withIndex()) {
            val row = layoutInflater.inflate(R.layout.item_home_row, container, false)
            row.stripe(index)
            row.findViewById<TextView>(R.id.row_title).text = node.title
            row.findViewById<TextView>(R.id.row_caption).setText(R.string.browse_type_post)
            PostMarks.paint(
                row.findViewById<ImageView>(R.id.row_icon),
                node,
                fallback = NodePalette.statusInk(node.status),
            )
            row.setOnClickListener {
                findNavController().navigate(
                    R.id.action_global_open_post,
                    bundleOf("postId" to node.id),
                )
            }
            container.addView(row)
        }
    }

    private companion object {
        /** Ignore anything smaller: a fling's settle wobbles by a pixel or two either way. */
        const val SCROLL_SLOP_DP = 6f

        /** Greyed rather than hidden, so the arrows keep their places at the ends of a branch. */
        const val DISABLED_ALPHA = 0.35f
    }
}

@StringRes
private fun resourceLabel(type: ResourceType): Int = when (type) {
    ResourceType.YOUTUBE -> R.string.resource_type_youtube
    ResourceType.ARTICLE -> R.string.resource_type_article
    ResourceType.BOOK -> R.string.resource_type_book
    ResourceType.OTHER -> R.string.resource_type_other
}
