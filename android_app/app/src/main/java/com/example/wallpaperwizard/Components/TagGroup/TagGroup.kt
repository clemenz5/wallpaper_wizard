package com.example.wallpaperwizard.Components.TagGroup

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.example.wallpaperwizard.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.stream.Collectors

class TagGroup(context: Context, attributeSet: AttributeSet?) :
    LinearLayout(context, attributeSet) {
    var tagGroup: ChipGroup
    lateinit var selectedChips: MutableList<Chip>
    lateinit var unselectedChips: MutableList<Chip>
    var tags = arrayOf<String>()
    var preferredTags = arrayOf<String>()
    var searchTag: String = ""

    init {
        inflate(context, R.layout.tag_group_layout, this)
        tagGroup = findViewById(R.id.chip_group)
        val addButton = findViewById<ImageButton>(R.id.tag_layout_add)
        val searchInput = findViewById<EditText>(R.id.tag_group_search_input)

        context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.TagGroup,
            0, 0
        ).apply {
            try {
                val editable = getBoolean(R.styleable.TagGroup_editable, false)
                if (!editable) {
                    addButton.visibility = GONE
                }
            } finally {
                recycle()
            }
        }

        createChips()

        tagGroup.setOnCheckedStateChangeListener(object : ChipGroup.OnCheckedStateChangeListener {
            override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {
                searchTag = ""
                searchInput.setText(searchTag)
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

        addButton.setOnClickListener{
            if(searchInput.text.isNotEmpty()){
                val view = Chip(
                    context,
                    null,
                    com.google.android.material.R.style.Widget_MaterialComponents_Chip_Filter
                )
                view.text = searchInput.text
                view.id = ViewCompat.generateViewId()
                view.isCheckable = true
                view.isClickable = true
                view.isChecked = true
                selectedChips.add(view)
                searchInput.setText("")
                populateChips()

            }

        }

        searchInput.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun afterTextChanged(p0: Editable?) {
                searchTag = p0.toString()
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
        selectedChips.stream().filter{chip -> chip.text.contains(searchTag)}.forEach{chip -> tagGroup.addView(chip)}
        unselectedChips.stream().filter{chip -> chip.text.contains(searchTag)}.forEach{chip -> tagGroup.addView(chip)}
    }
}