package org.thoughtcrime.securesms.conversation.v2.input_bar

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities
import org.thoughtcrime.securesms.util.toPx
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class InputBarEditText : AppCompatEditText {
    private val screenWidth get() = Resources.getSystem().displayMetrics.widthPixels
    var delegate: InputBarEditTextDelegate? = null

    var showMediaControls: Boolean = true

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

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(editorInfo) ?: return null
        EditorInfoCompat.setContentMimeTypes(editorInfo,
            if (showMediaControls) arrayOf("image/png", "image/gif", "image/jpg") else null
        )

        val callback =
                InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, opts ->
                    val lacksPermission = (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
                    // read and display inputContentInfo asynchronously
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && lacksPermission) {
                        try {
                            inputContentInfo.requestPermission()
                        } catch (e: Exception) {
                            return@OnCommitContentListener false // return false if failed
                        }
                    }

                    inputContentInfo.contentUri

                    // read and display inputContentInfo asynchronously.
                    delegate?.commitInputContent(inputContentInfo.contentUri)

                    true  // return true if succeeded
                }
        return InputConnectionCompat.createWrapper(ic, editorInfo, callback)
    }

}

interface InputBarEditTextDelegate {

    fun inputBarEditTextContentChanged(text: CharSequence)
    fun inputBarEditTextHeightChanged(newValue: Int)
    fun commitInputContent(contentUri: Uri)
}