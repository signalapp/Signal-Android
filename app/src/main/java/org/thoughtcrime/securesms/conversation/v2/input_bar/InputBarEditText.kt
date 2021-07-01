package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.content.Context
import android.content.res.Resources
import android.text.Layout
import android.text.StaticLayout
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatEditText
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities
import org.thoughtcrime.securesms.loki.utilities.toPx
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class InputBarEditText : AppCompatEditText {
    private val screenWidth get() = Resources.getSystem().displayMetrics.widthPixels
    var delegate: InputBarEditTextDelegate? = null

    private val snMinHeight = toPx(40.0f, resources)
    private val snMaxHeight = toPx(80.0f, resources)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onTextChanged(text: CharSequence, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        delegate?.inputBarEditTextContentChanged(text)
        // Calculate the width manually to get it right even before layout has happened (i.e.
        // when restoring a draft). The 64 DP is the horizontal margin around the input bar
        // edit text.
        val width = (screenWidth - 2 * toPx(64.0f, resources)).roundToInt()
        if (width < 0) { return } // screenWidth initially evaluates to 0
        val height = TextUtilities.getIntrinsicHeight(text, paint, width).toFloat()
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