package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_input_bar.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.toDp
import org.thoughtcrime.securesms.loki.utilities.toPx
import kotlin.math.max

class InputBar : LinearLayout, InputBarEditTextDelegate {
    var delegate: InputBarDelegate? = null

    private val attachmentsButton by lazy { InputBarButton(context, R.drawable.ic_plus_24) }
    private val microphoneButton by lazy { InputBarButton(context, R.drawable.ic_microphone) }
    private val sendButton by lazy { InputBarButton(context, R.drawable.ic_arrow_up, true) }

    // region Lifecycle
    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        LayoutInflater.from(context).inflate(R.layout.view_input_bar, this)
        attachmentsButtonContainer.addView(attachmentsButton)
        attachmentsButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        attachmentsButton.setOnClickListener {  }
        microphoneOrSendButtonContainer.addView(microphoneButton)
        microphoneButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        microphoneButton.setOnClickListener {  }
        microphoneOrSendButtonContainer.addView(sendButton)
        sendButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        sendButton.setOnClickListener {  }
        sendButton.isVisible = false
        inputBarEditText.imeOptions = inputBarEditText.imeOptions or 16777216 // Always use incognito keyboard
        inputBarEditText.delegate = this
    }
    // endregion

    // region Updating
    override fun inputBarEditTextContentChanged(text: CharSequence) {
        sendButton.isVisible = text.isNotEmpty()
        microphoneButton.isVisible = text.isEmpty()
    }

    override fun inputBarEditTextHeightChanged(newValue: Int) {
        val vMargin = toDp(4, resources)
        val layoutParams = inputBarLinearLayout.layoutParams as LayoutParams
        val newHeight = max(newValue + 2 * vMargin, toPx(56, resources))
        layoutParams.height = newHeight
        inputBarLinearLayout.layoutParams = layoutParams
        delegate?.inputBarHeightChanged(newHeight)
    }
    // endregion
}

interface InputBarDelegate {

    fun inputBarHeightChanged(newValue: Int)
}