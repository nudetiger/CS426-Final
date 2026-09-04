package com.cs426.learningmocha.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.cs426.learningmocha.databinding.FragmentPlaceholderBinding

/**
 * Phase-0 stand-in for a top-level tab. Exists so the shell runs end-to-end;
 * each tab replaces its subclass with a real screen per docs/plan.md.
 */
abstract class PlaceholderFragment(
    @StringRes private val titleRes: Int,
    @StringRes private val subtitleRes: Int,
) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentPlaceholderBinding.inflate(inflater, container, false)
        binding.placeholderTitle.setText(titleRes)
        binding.placeholderSubtitle.setText(subtitleRes)
        return binding.root
    }
}
