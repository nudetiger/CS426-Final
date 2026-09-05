package com.cs426.learningmocha.ui.settings

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.FragmentSettingsAppearanceBinding
import com.cs426.learningmocha.databinding.ItemThemeCardBinding
import com.cs426.learningmocha.ui.common.AppTheme
import com.cs426.learningmocha.viewmodel.SettingsUiState
import com.cs426.learningmocha.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Theme, reading comfort and list colour. */
class AppearanceSettingsFragment : Fragment() {

    private var binding: FragmentSettingsAppearanceBinding? = null
    private val viewModel: SettingsViewModel by viewModels()

    /** The theme cards, in enum order, so render() only has to move the selection. */
    private var themeCards: List<Pair<AppTheme, ItemThemeCardBinding>> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentSettingsAppearanceBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.appearanceBack.setOnClickListener { findNavController().popBackStack() }

        buildThemeCards(b.settingsThemeGrid)
        // fromUser only: the programmatic writes in render() would otherwise loop back in as
        // user edits and fight whatever the slider is being dragged to.
        b.settingsTextSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setReaderTextScale(value)
        }
        b.settingsLineSpacing.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setReaderLineSpacing(value)
        }
        b.settingsReadingReset.setOnClickListener { viewModel.resetReading() }
        b.settingsColorful.setOnCheckedChangeListener { button, checked ->
            if (button.isPressed) viewModel.setColorfulLists(checked)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        themeCards = emptyList()
        binding = null
    }

    /**
     * Fills the two-column grid with one card per theme, each painted in its own colours.
     * The swatch is why this screen stopped being a list of radio buttons: a name cannot say
     * what Rose Pine looks like, and the user is choosing a look, not a word.
     */
    private fun buildThemeCards(grid: GridLayout) {
        val gap = resources.getDimensionPixelSize(R.dimen.space_s)
        themeCards = AppTheme.entries.map { theme ->
            val card = ItemThemeCardBinding.inflate(layoutInflater, grid, false)
            card.themeName.setText(theme.labelRes)
            card.themeCaption.setText(theme.captionRes)
            card.themeSwatch.backgroundTintList = tint(theme.previewBackground)
            card.themeSwatchChrome.backgroundTintList = tint(theme.previewChrome)
            card.themeDotOne.backgroundTintList = tint(theme.previewAccentOne)
            card.themeDotTwo.backgroundTintList = tint(theme.previewAccentTwo)
            card.themeDotThree.backgroundTintList = tint(theme.previewAccentThree)
            card.themeCard.setOnClickListener { pickTheme(theme) }
            // Width 0 + column weight is what makes two cards share the row evenly; the
            // GridLayout itself only knows there are two columns.
            grid.addView(
                card.root,
                GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(gap / 2, gap / 2, gap / 2, gap / 2)
                },
            )
            theme to card
        }
    }

    private fun tint(colorRes: Int) =
        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))

    /**
     * Applying a theme recreates the Activity, so the state write has to land in the store
     * first — which [SettingsViewModel.setTheme] does — and nothing may be scheduled after.
     */
    private fun pickTheme(theme: AppTheme) {
        if (theme == viewModel.uiState.value.theme) return
        viewModel.setTheme(theme)
        theme.applyTo(requireActivity())
    }

    private fun render(state: SettingsUiState) {
        val b = binding ?: return
        for ((theme, card) in themeCards) {
            val selected = theme == state.theme
            card.themeCard.isSelected = selected
            card.themeCheck.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            card.themeCard.contentDescription = if (selected) {
                getString(R.string.cd_theme_selected) + ", " + getString(theme.labelRes)
            } else {
                getString(theme.labelRes)
            }
        }
        if (b.settingsTextSize.value != state.readerTextScale) {
            b.settingsTextSize.value = state.readerTextScale
        }
        if (b.settingsLineSpacing.value != state.readerLineSpacing) {
            b.settingsLineSpacing.value = state.readerLineSpacing
        }
        if (b.settingsColorful.isChecked != state.colorfulLists) {
            b.settingsColorful.isChecked = state.colorfulLists
        }

        b.settingsTextSizeLabel.text = getString(
            R.string.settings_text_size_value,
            (state.readerTextScale * 100).roundToInt(),
        )
        b.settingsLineSpacingLabel.text = getString(
            R.string.settings_line_spacing_value,
            state.readerLineSpacing,
        )
        // The sample is the control: it takes the exact size and spacing a post body will.
        val base = resources.getDimension(R.dimen.reader_text) / resources.displayMetrics.scaledDensity
        b.settingsReadingSample.textSize = base * state.readerTextScale
        b.settingsReadingSample.setLineSpacing(0f, state.readerLineSpacing)
    }
}
