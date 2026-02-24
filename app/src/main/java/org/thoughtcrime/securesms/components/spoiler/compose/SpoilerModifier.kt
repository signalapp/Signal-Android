package org.thoughtcrime.securesms.components.spoiler.compose

import android.graphics.Paint
import android.graphics.PorterDuff
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import org.thoughtcrime.securesms.components.spoiler.SpoilerPaint

/**
 * Annotation tag used to mark spoiler text ranges in an AnnotatedString.
 */
const val SPOILER_ANNOTATION_TAG = "spoiler"

/**
 * Modifier that draws the spoiler effect over specific text regions.
 *
 * @param spoilerState State holder for revealed spoilers
 * @param annotatedString The text with spoiler annotations
 * @param textLayoutResult The result of text layout measurement
 * @param textColor Color to tint the particles
 */
fun Modifier.drawSpoilers(
  spoilerState: SpoilerState,
  annotatedString: AnnotatedString,
  textLayoutResult: TextLayoutResult?,
  textColor: Color
): Modifier = this.then(
  Modifier.drawWithCache {
    val paint = Paint()
    val colorFilter = android.graphics.PorterDuffColorFilter(
      textColor.toArgb(),
      PorterDuff.Mode.SRC_IN
    )

    onDrawWithContent {
      drawContent()

      val layout = textLayoutResult ?: return@onDrawWithContent

      // Get all spoiler annotations
      val spoilerAnnotations = annotatedString.getStringAnnotations(
        tag = SPOILER_ANNOTATION_TAG,
        start = 0,
        end = annotatedString.length
      )

      if (spoilerAnnotations.isEmpty()) {
        return@onDrawWithContent
      }

      val shader = SpoilerPaint.shader

      drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas

        // Update paint properties for this draw
        if (shader != null) {
          paint.shader = shader
          paint.colorFilter = colorFilter
        } else {
          paint.shader = null
          paint.color = android.graphics.Color.TRANSPARENT
        }

        for (annotation in spoilerAnnotations) {
          if (spoilerState.isRevealed(annotation.item)) {
            continue
          }

          val start = annotation.start
          val end = annotation.end

          if (start >= end) {
            continue
          }

          val startLine = layout.getLineForOffset(start)
          val endLine = layout.getLineForOffset(end)

          if (startLine == endLine) {
            val left = layout.getHorizontalPosition(start, true)
            val right = layout.getHorizontalPosition(end, true)
            val top = layout.getLineTop(startLine)
            val bottom = layout.getLineBottom(startLine)

            nativeCanvas.drawRect(
              left.coerceAtMost(right),
              top,
              left.coerceAtLeast(right),
              bottom,
              paint
            )
          } else {
            val firstLineLeft = layout.getHorizontalPosition(start, true)
            val firstLineRight = if (layout.getParagraphDirection(startLine) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl) {
              layout.getLineLeft(startLine)
            } else {
              layout.getLineRight(startLine)
            }
            nativeCanvas.drawRect(
              firstLineLeft.coerceAtMost(firstLineRight),
              layout.getLineTop(startLine),
              firstLineLeft.coerceAtLeast(firstLineRight),
              layout.getLineBottom(startLine),
              paint
            )

            for (line in startLine + 1 until endLine) {
              nativeCanvas.drawRect(
                layout.getLineLeft(line),
                layout.getLineTop(line),
                layout.getLineRight(line),
                layout.getLineBottom(line),
                paint
              )
            }

            val lastLineLeft = if (layout.getParagraphDirection(endLine) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl) {
              layout.getLineRight(endLine)
            } else {
              layout.getLineLeft(endLine)
            }
            val lastLineRight = layout.getHorizontalPosition(end, true)
            nativeCanvas.drawRect(
              lastLineLeft.coerceAtMost(lastLineRight),
              layout.getLineTop(endLine),
              lastLineLeft.coerceAtLeast(lastLineRight),
              layout.getLineBottom(endLine),
              paint
            )
          }
        }
      }
    }
  }
)

/**
 * Find which spoiler annotation (if any) contains the given offset in the text.
 */
fun AnnotatedString.findSpoilerAt(offset: Int): AnnotatedString.Range<String>? {
  return getStringAnnotations(SPOILER_ANNOTATION_TAG, offset, offset).firstOrNull()
}
