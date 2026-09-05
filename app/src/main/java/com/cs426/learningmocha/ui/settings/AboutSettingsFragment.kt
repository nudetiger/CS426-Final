package com.cs426.learningmocha.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.cs426.learningmocha.databinding.FragmentSettingsAboutBinding

/** What leaves the device, and what does not. Static text; no state to hold. */
class AboutSettingsFragment : Fragment() {

    private var binding: FragmentSettingsAboutBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentSettingsAboutBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.aboutBack?.setOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
