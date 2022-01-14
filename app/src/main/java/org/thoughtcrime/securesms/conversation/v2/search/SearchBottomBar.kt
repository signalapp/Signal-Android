package org.thoughtcrime.securesms.conversation.v2.search

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewSearchBottomBarBinding


class SearchBottomBar : LinearLayout {
    private lateinit var binding: ViewSearchBottomBarBinding
    private var eventListener: EventListener? = null

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    fun initialize() {
        binding = ViewSearchBottomBarBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun setData(position: Int, count: Int) = with(binding) {
        searchProgressWheel.visibility = GONE
        searchUp.setOnClickListener { v: View? ->
            if (eventListener != null) {
                eventListener!!.onSearchMoveUpPressed()
            }
        }
        searchDown.setOnClickListener { v: View? ->
            if (eventListener != null) {
                eventListener!!.onSearchMoveDownPressed()
            }
        }
        if (count > 0) {
            searchPosition.text = resources.getString(R.string.ConversationActivity_search_position, position + 1, count)
        } else {
            searchPosition.text = ""
        }
        setViewEnabled(searchUp, position < count - 1)
        setViewEnabled(searchDown, position > 0)
    }

    fun showLoading() {
        binding.searchProgressWheel.visibility = VISIBLE
    }

    private fun setViewEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.25f
    }

    fun setEventListener(eventListener: EventListener?) {
        this.eventListener = eventListener
    }

    interface EventListener {
        fun onSearchMoveUpPressed()
        fun onSearchMoveDownPressed()
    }
}