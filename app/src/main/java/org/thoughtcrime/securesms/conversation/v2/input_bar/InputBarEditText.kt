package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.content.Context
import android.text.Layout
import android.text.StaticLayout
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatEditText
import org.thoughtcrime.securesms.loki.utilities.toPx
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class InputBarEditText : AppCompatEditText {
    var delegate: InputBarEditTextDelegate? = null

    private val snMinHeight = toPx(40.0f, resources)
    private val snMaxHeight = toPx(80.0f, resources)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onTextChanged(text: CharSequence, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        delegate?.inputBarEditTextContentChanged(text)
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0.0f, 1.0f)
            .setIncludePad(false)
        val layout = builder.build()
        val height = layout.height.toFloat()
        val constrainedHeight = min(max(height, snMinHeight), snMaxHeight)
        if (constrainedHeight.roundToInt() == this.height) { return }
        val layoutParams = this.layoutParams as? RelativeLayout.LayoutParams ?: return
        layoutParams.height = constrainedHeight.roundToInt()
        this.layoutParams = layoutParams
        delegate?.inputBarEditTextHeightChanged(constrainedHeight.roundToInt())
    }
}

interface InputBarEditTextDelegate {

    fun inputBarEditTextContentChanged(text: CharSequence)
    fun inputBarEditTextHeightChanged(newValue: Int)
}