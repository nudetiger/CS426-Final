package com.cs426.learningmocha.ui.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.ResourceType
import com.cs426.learningmocha.databinding.FragmentPostReaderBinding
import com.cs426.learningmocha.databinding.ItemYoutubeBinding
import com.cs426.learningmocha.ui.common.ChipBar
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.common.WikiMarkdown
import com.cs426.learningmocha.ui.common.labelRes
import com.cs426.learningmocha.viewmodel.PostReaderViewModel
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

        b.readerBack.setOnClickListener { findNavController().popBackStack() }
        b.readerFavorite.setOnClickListener { viewModel.toggleFavorite() }
        b.readerEdit.setOnClickListener {
            findNavController().navigate(
                R.id.action_reader_to_editor,
                bundleOf(
                    "postId" to viewModel.postId,
                    "parentId" to -1L,
                ),
            )
        }
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
                        onRetry = { },
                    )
                    val post = state.post
                    if (state.listState == ListState.CONTENT && post != null) {
                        b.readerTitle.text = post.title
                        b.readerFavorite.setImageResource(
                            if (post.favorite) R.drawable.ic_star else R.drawable.ic_star_border,
                        )
                        b.readerStatus.setText(post.status.labelRes())
                        ChipBar.bind(b.readerTags, state.tags.map { tag ->
                            tag.name to {
                                findNavController().navigate(
                                    R.id.action_global_tag,
                                    bundleOf("tagId" to tag.id),
                                )
                            }
                        })
                        bindResources(b.readerResources, state.resources.map { it.url to it.type })
                        val markdown = WikiMarkdown.rewrite(post.content.orEmpty(), state.titleToId)
                        markwon?.setMarkdown(b.readerBody, markdown)
                        b.readerTermsHeader.isVisible = state.terms.isNotEmpty()
                        ChipBar.bind(b.readerTerms, state.terms.map { term ->
                            term.term to { showTerm(term) }
                        })
                        bindNodeRows(b.readerBacklinksHeader, b.readerBacklinks, state.backlinks)
                        bindNodeRows(b.readerRelatedHeader, b.readerRelated, state.related)
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

    private fun showTerm(entry: DictionaryEntry) {
        val body = buildString {
            append(entry.definition)
            if (entry.meaningVi.isNotBlank()) {
                append("\n\n")
                append(entry.meaningVi)
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(entry.term)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun bindResources(
        container: LinearLayout,
        items: List<Pair<String, ResourceType>>,
    ) {
        container.removeAllViews()
        container.isVisible = items.isNotEmpty()
        val inflater = layoutInflater
        for ((url, type) in items) {
            if (type != ResourceType.YOUTUBE) continue
            val card = ItemYoutubeBinding.inflate(inflater, container, false)
            card.youtubeUrl.text = url
            card.root.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            container.addView(card.root)
        }
    }

    private fun bindNodeRows(header: TextView, container: LinearLayout, nodes: List<Node>) {
        header.isVisible = nodes.isNotEmpty()
        container.removeAllViews()
        for (node in nodes) {
            val row = layoutInflater.inflate(R.layout.item_home_row, container, false)
            row.findViewById<TextView>(R.id.row_title).text = node.title
            row.findViewById<TextView>(R.id.row_caption).setText(R.string.browse_type_post)
            row.setOnClickListener {
                findNavController().navigate(
                    R.id.action_global_open_post,
                    bundleOf("postId" to node.id),
                )
            }
            container.addView(row)
        }
    }
}
