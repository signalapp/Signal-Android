package org.thoughtcrime.securesms.conversation.v2.utilities

import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.widget.TextView
import androidx.core.text.getSpans
import androidx.core.text.toSpannable

object TextUtilities {

    fun getIntrinsicHeight(text: CharSequence, paint: TextPaint, width: Int): Int {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0.0f, 1.0f)
            .setIncludePad(false)
        val layout = builder.build()
        return layout.height
    }

    fun TextView.getIntersectedModalSpans(event: MotionEvent): List<ModalURLSpan> {
        val xInt = event.rawX.toInt()
        val yInt = event.rawY.toInt()
        val hitRect = Rect(xInt, yInt, xInt, yInt)
        return getIntersectedModalSpans(hitRect)
    }

    fun TextView.getIntersectedModalSpans(hitRect: Rect): List<ModalURLSpan> {
        val textLayout = layout ?: return emptyList()
        val lineRect = Rect()
        val bodyTextRect = Rect()
        getGlobalVisibleRect(bodyTextRect)
        val textSpannable = text.toSpannable()
        return (0 until textLayout.lineCount).flatMap { line ->
            textLayout.getLineBounds(line, lineRect)
            lineRect.offset(bodyTextRect.left + totalPaddingLeft, bodyTextRect.top + totalPaddingTop)
            if ((Rect(lineRect)).contains(hitRect)) {
                // calculate the url span intersected with (if any)
                val off = textLayout.getOffsetForHorizontal(line, hitRect.left.toFloat()) // left and right will be the same
                textSpannable.getSpans<ModalURLSpan>(off, off).toList()
            } else {
                emptyList()
            }
        }
    }

}