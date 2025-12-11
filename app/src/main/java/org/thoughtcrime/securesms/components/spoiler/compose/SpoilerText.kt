package org.thoughtcrime.securesms.components.spoiler.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.thoughtcrime.securesms.components.spoiler.SpoilerPaint

/**
 * A Text composable that supports spoiler annotations with particle effects.
 *
 * Text ranges marked with [SPOILER_ANNOTATION_TAG] annotations will be rendered with
 * an animated particle effect until revealed by tapping.
 *
 * Example usage:
 * ```
 * val spoilerState = rememberSpoilerState()
 * val text = buildAnnotatedString {
 *   append("This is normal text. ")
 *   withAnnotation(SPOILER_ANNOTATION_TAG, "spoiler-1") {
 *     append("This is a spoiler!")
 *   }
 *   append(" More normal text.")
 * }
 * SpoilerText(
 *   text = text,
 *   spoilerState = spoilerState
 * )
 * ```
 *
 * @param text The annotated string with optional spoiler annotations
 * @param spoilerState State holder for managing revealed spoilers
 * @param modifier Modifier to be applied to the Text
 * @param color Color to apply to the text
 * @param fontSize Font size to apply to the text
 * @param textAlign Text alignment
 * @param textDecoration Text decoration
 * @param overflow How to handle text overflow
 * @param softWrap Whether the text should break at soft line breaks
 * @param maxLines Maximum number of lines
 * @param minLines Minimum number of lines
 * @param onTextLayout Callback for text layout results
 * @param style Text style to apply
 * @param inlineContent Map of inline content for the text
 */
@Composable
fun SpoilerText(
  text: AnnotatedString,
  spoilerState: SpoilerState,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  fontSize: TextUnit = TextUnit.Unspecified,
  textAlign: TextAlign? = null,
  textDecoration: TextDecoration? = null,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  maxLines: Int = Int.MAX_VALUE,
  minLines: Int = 1,
  onTextLayout: (TextLayoutResult) -> Unit = {},
  style: TextStyle = LocalTextStyle.current,
  inlineContent: Map<String, InlineTextContent> = mapOf()
) {
  var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
  var frameTime by remember { mutableLongStateOf(0L) }

  // Get all spoiler annotations
  val spoilerAnnotations = remember(text) {
    text.getStringAnnotations(SPOILER_ANNOTATION_TAG, 0, text.length)
  }

  // Check if there are any unrevealed spoilers
  val hasUnrevealedSpoilers = remember(spoilerAnnotations, spoilerState, frameTime) {
    spoilerAnnotations.any { !spoilerState.isRevealed(it.item) }
  }

  // Animate when there are unrevealed spoilers
  LaunchedEffect(hasUnrevealedSpoilers) {
    if (hasUnrevealedSpoilers) {
      while (true) {
        withFrameNanos { nanos ->
          SpoilerPaint.update()
          frameTime = nanos
        }
      }
    }
  }

  val originalTextColor = if (color != Color.Unspecified) {
    color
  } else if (style.color != Color.Unspecified) {
    style.color
  } else {
    LocalContentColor.current
  }

  val displayText = remember(text, spoilerAnnotations, spoilerState, frameTime) {
    AnnotatedString.Builder(text).apply {
      for (annotation in spoilerAnnotations) {
        if (!spoilerState.isRevealed(annotation.item)) {
          addStyle(
            style = androidx.compose.ui.text.SpanStyle(color = Color.Transparent),
            start = annotation.start,
            end = annotation.end
          )
        }
      }
    }.toAnnotatedString()
  }

  Text(
    text = displayText,
    modifier = modifier
      .drawSpoilers(
        spoilerState = spoilerState,
        annotatedString = text,
        textLayoutResult = textLayoutResult,
        textColor = originalTextColor
      )
      .pointerInput(text, spoilerState) {
        detectTapGestures { offset ->
          val layout = textLayoutResult ?: return@detectTapGestures
          val position = layout.getOffsetForPosition(offset)
          val spoiler = text.findSpoilerAt(position)
          if (spoiler != null && !spoilerState.isRevealed(spoiler.item)) {
            spoilerState.reveal(spoiler.item)
          }
        }
      },
    color = color,
    fontSize = fontSize,
    textAlign = textAlign,
    textDecoration = textDecoration,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
    onTextLayout = { result ->
      textLayoutResult = result
      onTextLayout(result)
    },
    style = style,
    inlineContent = inlineContent
  )
}

/**
 * A Text composable that supports spoiler annotations, using a simple string with builder.
 *
 * @param text The plain text to display
 * @param spoilerState State holder for managing revealed spoilers
 * @param modifier Modifier to be applied to the Text
 * @param color Color to apply to the text
 * @param fontSize Font size to apply to the text
 * @param textAlign Text alignment
 * @param textDecoration Text decoration
 * @param overflow How to handle text overflow
 * @param softWrap Whether the text should break at soft line breaks
 * @param maxLines Maximum number of lines
 * @param minLines Minimum number of lines
 * @param onTextLayout Callback for text layout results
 * @param style Text style to apply
 */
@Composable
fun SpoilerText(
  text: String,
  spoilerState: SpoilerState,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  fontSize: TextUnit = TextUnit.Unspecified,
  textAlign: TextAlign? = null,
  textDecoration: TextDecoration? = null,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  maxLines: Int = Int.MAX_VALUE,
  minLines: Int = 1,
  onTextLayout: (TextLayoutResult) -> Unit = {},
  style: TextStyle = LocalTextStyle.current
) {
  SpoilerText(
    text = AnnotatedString(text),
    spoilerState = spoilerState,
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    textAlign = textAlign,
    textDecoration = textDecoration,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
    onTextLayout = onTextLayout,
    style = style
  )
}
