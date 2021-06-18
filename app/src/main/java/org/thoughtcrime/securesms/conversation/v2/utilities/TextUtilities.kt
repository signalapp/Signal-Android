package org.thoughtcrime.securesms.conversation.v2.utilities

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

object TextUtilities {

    fun getIntrinsicHeight(text: CharSequence, paint: TextPaint, width: Int): Int {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0.0f, 1.0f)
            .setIncludePad(false)
        val layout = builder.build()
        return layout.height
    }
}