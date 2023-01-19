package com.example.wallpaperwizard.Components.TagGroup

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.example.wallpaperwizard.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.stream.Collectors

class TagGroup(context: Context, attributeSet: AttributeSet?) :
    LinearLayout(context, attributeSet) {
    var tagGroup: ChipGroup
    lateinit var selectedChips: List<Chip>
    lateinit var unselectedChips: List<Chip>
    var tags = arrayOf<String>()
    var preferredTags = arrayOf<String>()

    init {
        inflate(context, R.layout.tag_group_layout, this)
        tagGroup = findViewById(R.id.chip_group)

        context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.TagGroup,
            0, 0
        ).apply {
            try {
                var editable = getBoolean(R.styleable.TagGroup_editable, false)
            } finally {
                recycle()
            }
        }

        createChips()

        tagGroup.setOnCheckedStateChangeListener(object : ChipGroup.OnCheckedStateChangeListener {
            override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {

                var newSelectedChips: MutableList<Chip> = mutableListOf()
                var newUnselectedChips: MutableList<Chip> = mutableListOf()
                for (chip in selectedChips) {
                    if (chip.id in checkedIds) {
                        newSelectedChips.add(chip)
                    } else {
                        newUnselectedChips.add(chip)
                    }
                }
                for (chip in unselectedChips) {
                    if (chip.id in checkedIds) {
                        newSelectedChips.add(chip)
                    } else {
                        newUnselectedChips.add(chip)
                    }
                }
                selectedChips = newSelectedChips
                unselectedChips = newUnselectedChips
                populateChips()
            }

        })
    }

    fun setTags(tags: Array<String>, preferredTags: Array<String>) {
        this.tags = tags
        this.preferredTags = preferredTags
        createChips()
        populateChips()
    }

    fun getSelectedTags(): Array<String> {
        return selectedChips.stream().map { chip -> chip.text.toString() }.collect(Collectors.toList()).toTypedArray()
    }

    private fun createChips() {
        val selectedChips: MutableList<Chip> = mutableListOf()
        val unselectedChips: MutableList<Chip> = mutableListOf()
        for (tag in tags) {
            val view = Chip(
                context,
                null,
                com.google.android.material.R.style.Widget_MaterialComponents_Chip_Filter
            )
            view.text = tag
            view.id = ViewCompat.generateViewId()
            view.isCheckable = true
            view.isClickable = true

            if (tag in preferredTags) {
                view.isChecked = true
                selectedChips.add(view)
            } else {
                unselectedChips.add(view)
            }
        }
        this.selectedChips = selectedChips
        this.unselectedChips = unselectedChips
    }

    private fun populateChips() {
        tagGroup.removeAllViews()
        for (chip in selectedChips) {
            tagGroup.addView(chip)
        }
        for (chip in unselectedChips) {
            tagGroup.addView(chip)
        }
    }
}