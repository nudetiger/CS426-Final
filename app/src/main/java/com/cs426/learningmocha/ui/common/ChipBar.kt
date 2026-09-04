package com.cs426.learningmocha.ui.common

import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.cs426.learningmocha.R

object ChipBar {
    fun bind(container: LinearLayout, items: List<Pair<String, () -> Unit>>) {
        container.removeAllViews()
        container.isVisible = items.isNotEmpty()
        if (items.isEmpty()) return
        val inflater = LayoutInflater.from(container.context)
        for ((label, onClick) in items) {
            val chip = inflater.inflate(R.layout.item_chip, container, false) as TextView
            chip.text = label
            chip.setOnClickListener { onClick() }
            container.addView(chip)
        }
    }
}
