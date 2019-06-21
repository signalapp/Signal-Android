package org.thoughtcrime.securesms.loki

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class FriendRequestView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {

    // region Components
    private val label by lazy {
        val result = TextView(context)
        result.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        result
    }
    // endregion

    // region Initialization
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    init {
        orientation = VERTICAL
        val topSpacer = View(context)
        topSpacer.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 12)
        addView(topSpacer)
        addView(label)
        updateUI()
    }
    // endregion

    // region Updating
    private fun updateUI() {
        label.text = "You've sent a friend request"
    }
    // endregion
}