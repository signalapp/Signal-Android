/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.Result
import org.signal.core.util.logging.Log
import org.signal.signallogin.R
import org.signal.signallogin.fonts.MonoTypeface
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.ceil

/**
 * Renders the keys that make up a Signal Login into a single-page PDF: a miniature of the credential card up top,
 * a title and explainer, then each key in a rounded block, with the recovery key broken into character groups.
 *
 * All dimensions are in PostScript points (1/72 inch), on a US Letter page, and are taken directly from the design.
 */
object SignalLoginPdfRenderer {

  private val TAG = Log.tag(SignalLoginPdfRenderer::class)

  /** The file name to prefill in the system save dialog. */
  fun suggestedFileName(context: Context): String {
    return context.getString(R.string.SignalLoginViewDetailsScreen__signal_login_pdf)
  }

  private const val PAGE_WIDTH = 612
  private const val PAGE_HEIGHT = 792

  private const val CARD_TOP = 120f
  private const val CARD_WIDTH = 98f
  private const val CARD_HEIGHT = 56f

  private const val TITLE_TOP_SPACING = 16f
  private const val TITLE_TEXT_SIZE = 18f
  private const val TITLE_LINE_HEIGHT = 24f
  private const val TITLE_LETTER_SPACING_EM = -0.014f

  private const val BODY_TOP_SPACING = 8f
  private const val BODY_WIDTH = 390f
  private const val BODY_TEXT_SIZE = 14f
  private const val BODY_LINE_HEIGHT = 22f
  private const val BODY_LETTER_SPACING_EM = -0.006f

  private const val HEADER_TOP_SPACING = 32f
  private const val HEADER_TEXT_SIZE = 14f
  private const val HEADER_LINE_HEIGHT = 22f
  private const val HEADER_LETTER_SPACING_EM = -0.006f
  private const val HEADER_BOTTOM_SPACING = 12f

  private const val BLOCK_WIDTH = 380f
  private const val BLOCK_CORNER_RADIUS = 18f
  private const val BLOCK_HORIZONTAL_PADDING = 24f
  private const val BLOCK_VERTICAL_PADDING = 16f

  private const val KEY_TEXT_SIZE = 14f
  private const val KEY_LETTER_SPACING_EM = 0.07f
  private const val ACCOUNT_KEY_LINE_HEIGHT = 20f
  private const val RECOVERY_KEY_LINE_HEIGHT = 24f

  private const val GROUPS_PER_ROW = 4

  private const val BLOCK_LEFT = (PAGE_WIDTH - BLOCK_WIDTH) / 2f
  private const val CONTENT_LEFT = BLOCK_LEFT + BLOCK_HORIZONTAL_PADDING
  private const val CONTENT_WIDTH = BLOCK_WIDTH - 2 * BLOCK_HORIZONTAL_PADDING

  /** The PDF is always rendered as if in light theme, so these are fixed rather than pulled from the theme. */
  private const val BLOCK_COLOR = 0xFFF4F4F5.toInt()
  private const val TEXT_COLOR = 0xFF000000.toInt()
  private const val BODY_TEXT_COLOR = 0xFF4D4D4D.toInt()

  /**
   * Renders the credentials in [state] to a PDF and writes it to [uri].
   */
  suspend fun renderTo(context: Context, uri: Uri, state: SignalLoginViewDetailsState): Result<Unit, SignalLoginPdfError> {
    return withContext(Dispatchers.IO) {
      try {
        val bytes = render(context, state)
        val stream = context.contentResolver.openOutputStream(uri)
        if (stream == null) {
          Log.w(TAG, "Could not open an output stream for the chosen location.")
          Result.failure(SignalLoginPdfError.UnableToOpenDocument)
        } else {
          stream.use { it.write(bytes) }
          Result.success(Unit)
        }
      } catch (e: IOException) {
        Log.w(TAG, "Failed to write the Signal Login PDF.", e)
        Result.failure(SignalLoginPdfError.WriteFailed)
      } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to write the Signal Login PDF to the chosen location.", e)
        Result.failure(SignalLoginPdfError.NotAllowed)
      }
    }
  }

  fun render(context: Context, state: SignalLoginViewDetailsState): ByteArray {
    val document = PdfDocument()
    try {
      val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
      drawPage(context, page.canvas, state)
      document.finishPage(page)

      return ByteArrayOutputStream().use { stream ->
        document.writeTo(stream)
        stream.toByteArray()
      }
    } finally {
      document.close()
    }
  }

  private fun drawPage(context: Context, canvas: Canvas, state: SignalLoginViewDetailsState) {
    val titlePaint = textPaint(TITLE_TEXT_SIZE, TITLE_LETTER_SPACING_EM, TEXT_COLOR, semiBold = true)
    val bodyPaint = textPaint(BODY_TEXT_SIZE, BODY_LETTER_SPACING_EM, BODY_TEXT_COLOR, semiBold = false)
    val headerPaint = textPaint(HEADER_TEXT_SIZE, HEADER_LETTER_SPACING_EM, TEXT_COLOR, semiBold = true)
    val keyPaint = textPaint(KEY_TEXT_SIZE, KEY_LETTER_SPACING_EM, TEXT_COLOR, semiBold = false).apply {
      typeface = MonoTypeface.typeface(context)
    }

    var y = drawCard(context, canvas, top = CARD_TOP)

    y += TITLE_TOP_SPACING
    y += drawText(
      canvas = canvas,
      text = context.getString(R.string.SignalLoginPdf__your_signal_login),
      paint = titlePaint,
      left = (PAGE_WIDTH - BODY_WIDTH) / 2f,
      top = y,
      width = BODY_WIDTH,
      lineHeight = TITLE_LINE_HEIGHT,
      alignment = Layout.Alignment.ALIGN_CENTER
    )

    y += BODY_TOP_SPACING
    y += drawText(
      canvas = canvas,
      text = context.getString(R.string.SignalLoginPdf__store_this_in_a_safe_place),
      paint = bodyPaint,
      left = (PAGE_WIDTH - BODY_WIDTH) / 2f,
      top = y,
      width = BODY_WIDTH,
      lineHeight = BODY_LINE_HEIGHT,
      alignment = Layout.Alignment.ALIGN_CENTER
    )

    y = drawSectionHeader(canvas, headerPaint, context.getString(R.string.SignalLoginPdf__account_id), top = y)
    y = drawKeyBlock(canvas, top = y, rowCount = 1, lineHeight = ACCOUNT_KEY_LINE_HEIGHT) { contentTop ->
      drawText(
        canvas = canvas,
        text = state.accountKey,
        paint = keyPaint,
        left = CONTENT_LEFT,
        top = contentTop,
        width = CONTENT_WIDTH,
        lineHeight = ACCOUNT_KEY_LINE_HEIGHT,
        alignment = Layout.Alignment.ALIGN_CENTER
      )
    }

    val rows = state.recoveryKeyGroups.chunked(GROUPS_PER_ROW)
    y = drawSectionHeader(canvas, headerPaint, context.getString(R.string.SignalLoginPdf__recovery_key), top = y)
    drawKeyBlock(canvas, top = y, rowCount = rows.size, lineHeight = RECOVERY_KEY_LINE_HEIGHT) { contentTop ->
      drawRecoveryKeyGroups(canvas, keyPaint, rows, contentTop)
    }
  }

  /** Draws the miniature credential card artwork centered at the top of the page, returning the y position of its bottom edge. */
  private fun drawCard(context: Context, canvas: Canvas, top: Float): Float {
    val card = requireNotNull(ContextCompat.getDrawable(context, R.drawable.image_signal_login_card_x_small))
    val left = (PAGE_WIDTH - CARD_WIDTH) / 2f

    card.setBounds(left.toInt(), top.toInt(), (left + CARD_WIDTH).toInt(), (top + CARD_HEIGHT).toInt())
    card.draw(canvas)

    return top + CARD_HEIGHT
  }

  /** Draws a section header above a key block, returning the y position the block should start at. */
  private fun drawSectionHeader(canvas: Canvas, paint: TextPaint, text: String, top: Float): Float {
    val headerTop = top + HEADER_TOP_SPACING
    val height = drawText(
      canvas = canvas,
      text = text,
      paint = paint,
      left = CONTENT_LEFT,
      top = headerTop,
      width = CONTENT_WIDTH,
      lineHeight = HEADER_LINE_HEIGHT,
      alignment = Layout.Alignment.ALIGN_NORMAL
    )

    return headerTop + height + HEADER_BOTTOM_SPACING
  }

  /**
   * Draws a rounded block sized to hold [rowCount] rows of [lineHeight], invoking [drawContent] with the y position
   * its content starts at. Returns the y position of the block's bottom edge.
   */
  private fun drawKeyBlock(canvas: Canvas, top: Float, rowCount: Int, lineHeight: Float, drawContent: (Float) -> Unit): Float {
    val height = 2 * BLOCK_VERTICAL_PADDING + rowCount * lineHeight
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLOCK_COLOR }

    canvas.drawRoundRect(RectF(BLOCK_LEFT, top, BLOCK_LEFT + BLOCK_WIDTH, top + height), BLOCK_CORNER_RADIUS, BLOCK_CORNER_RADIUS, backgroundPaint)
    drawContent(top + BLOCK_VERTICAL_PADDING)

    return top + height
  }

  /** Draws the recovery key groups as evenly-spaced columns spanning the width of the block's content area. */
  private fun drawRecoveryKeyGroups(canvas: Canvas, paint: TextPaint, rows: List<List<String>>, top: Float) {
    val groupWidth = rows.flatten().maxOfOrNull { paint.measureText(it) } ?: 0f
    val columnSpacing = (CONTENT_WIDTH - groupWidth) / (GROUPS_PER_ROW - 1)

    rows.forEachIndexed { rowIndex, row ->
      row.forEachIndexed { columnIndex, group ->
        drawText(
          canvas = canvas,
          text = group,
          paint = paint,
          left = CONTENT_LEFT + columnIndex * columnSpacing,
          top = top + rowIndex * RECOVERY_KEY_LINE_HEIGHT,
          width = groupWidth,
          lineHeight = RECOVERY_KEY_LINE_HEIGHT,
          alignment = Layout.Alignment.ALIGN_NORMAL
        )
      }
    }
  }

  /**
   * Draws [text] wrapped to [width], with each line occupying [lineHeight] and its glyphs centered within that,
   * matching how the design lays text out. Returns the total height consumed.
   */
  private fun drawText(canvas: Canvas, text: String, paint: TextPaint, left: Float, top: Float, width: Float, lineHeight: Float, alignment: Layout.Alignment): Float {
    val glyphHeight = paint.fontMetrics.let { it.descent - it.ascent }
    val layout = StaticLayout.Builder
      .obtain(text, 0, text.length, paint, ceil(width).toInt())
      .setAlignment(alignment)
      .setIncludePad(false)
      .setLineSpacing(lineHeight - glyphHeight, 1f)
      .build()

    canvas.withTranslation(left, top + (lineHeight - glyphHeight) / 2f) {
      layout.draw(this)
    }

    return layout.lineCount * lineHeight
  }

  private fun textPaint(textSize: Float, letterSpacing: Float, color: Int, semiBold: Boolean): TextPaint {
    return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      this.typeface = if (semiBold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.SANS_SERIF
      this.textSize = textSize
      this.letterSpacing = letterSpacing
      this.color = color
    }
  }
}

/**
 * The ways saving the login PDF can fail, each carrying the message to show the user.
 */
sealed class SignalLoginPdfError(@StringRes val userMessageRes: Int) {
  /** The system could not provide an output stream for the chosen location. */
  data object UnableToOpenDocument : SignalLoginPdfError(R.string.SignalLoginViewDetailsScreen__unable_to_save_pdf)

  /** Writing the rendered bytes failed. */
  data object WriteFailed : SignalLoginPdfError(R.string.SignalLoginViewDetailsScreen__unable_to_save_pdf)

  /** The document provider rejected the write, e.g. a permission grant that has since been revoked. */
  data object NotAllowed : SignalLoginPdfError(R.string.SignalLoginViewDetailsScreen__cant_save_pdf_to_the_selected_location)
}
