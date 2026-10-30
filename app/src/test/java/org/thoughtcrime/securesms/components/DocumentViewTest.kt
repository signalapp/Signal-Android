
package org.thoughtcrime.securesms.components

import android.app.Application
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentViewTest {

  companion object {
    private const val SHORT_FILENAME = "report.pdf"
    private const val LONG_FILENAME =
      "Premium RO configuration complete document and installation manual final revision.pdf"
    private const val VERY_LONG_FILENAME =
      "Premium_RO_configuration_complete_document_installation_manual_" +
        "final_revision_2026_customer_copy_extended_reference_material.pdf"

    private const val VIEW_WIDTH_DP = 210
  }

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val context: Application =
    ApplicationProvider.getApplicationContext()

  private lateinit var container: FrameLayout
  private lateinit var documentView: DocumentView
  private lateinit var fileNameView: TextView

  @Before
  fun setUp() {
    every {
      signalStore.internal.showArchiveStateHint
    } returns false

    documentView = DocumentView(context)
    fileNameView = documentView.findViewById(R.id.file_name)

    container = FrameLayout(context).apply {
      addView(
        documentView,
        FrameLayout.LayoutParams(
          dp(VIEW_WIDTH_DP),
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      )
    }
  }

  @Test
  fun shortFilenameRemainsOnOneLine() {
    documentView.setDocument(
      createSlide(SHORT_FILENAME),
      false,
      true
    )

    measureAndLayout()

    assertEquals(SHORT_FILENAME, fileNameView.text.toString())
    assertEquals(1, fileNameView.layout.lineCount)
    assertEquals(2, fileNameView.maxLines)
  }

  @Test
  fun longFilenameUsesAtMostTwoLinesInConversation() {
    documentView.setDocument(
      createSlide(LONG_FILENAME),
      false,
      true
    )

    measureAndLayout()

    val lineCount = fileNameView.layout.lineCount

    assertTrue(diagnostics(), lineCount > 1)
    assertTrue(diagnostics(), lineCount <= 2)
    assertEquals(2, fileNameView.maxLines)
  }

  @Test
  fun veryLongFilenameIsEllipsized() {
    documentView.setDocument(
      createSlide(VERY_LONG_FILENAME),
      false,
      true
    )

    measureAndLayout()

    val textLayout = fileNameView.layout

    assertTrue(diagnostics(), textLayout.lineCount in 1..2)

    val lastLine = textLayout.lineCount - 1

    assertTrue(
      diagnostics(),
      textLayout.getEllipsisCount(lastLine) > 0
    )

    assertEquals(
      TextUtils.TruncateAt.END,
      fileNameView.ellipsize
    )
  }

  @Test
  fun detailedModeDoesNotLimitFilenameToTwoLines() {
    documentView.setDocument(
      createSlide(VERY_LONG_FILENAME),
      false,
      false
    )

    measureAndLayout()

    assertEquals(Int.MAX_VALUE, fileNameView.maxLines)
    assertTrue(diagnostics(), fileNameView.layout.lineCount > 2)
    assertEquals(VERY_LONG_FILENAME, fileNameView.text.toString())
  }

  @Test
  fun switchingModesUpdatesFilenameLineLimit() {
    val slide = createSlide(VERY_LONG_FILENAME)

    documentView.setDocument(slide, false, true)
    measureAndLayout()

    assertEquals(2, fileNameView.maxLines)
    assertTrue(
      diagnostics(),
      fileNameView.layout.lineCount <= 2
    )

    documentView.setDocument(slide, false, false)
    measureAndLayout()

    assertEquals(Int.MAX_VALUE, fileNameView.maxLines)
    assertTrue(
      diagnostics(),
      fileNameView.layout.lineCount > 2
    )

    documentView.setDocument(slide, false, true)
    measureAndLayout()

    assertEquals(2, fileNameView.maxLines)
    assertTrue(
      diagnostics(),
      fileNameView.layout.lineCount <= 2
    )
  }

  private fun createSlide(filename: String): Slide {
    val attachment = mockk<Attachment>(relaxed = true)
    val slide = mockk<Slide>(relaxed = true)

    every { slide.asAttachment() } returns attachment
    every { slide.transferState } returns AttachmentTable.TRANSFER_PROGRESS_DONE
    every { slide.isPendingDownload } returns false
    every { slide.uri } returns null
    every { slide.fileName } returns Optional.of(filename)
    every { slide.caption } returns Optional.empty()
    every { slide.fileSize } returns 1024L
    every { slide.getFileType(any()) } returns Optional.of("pdf")

    return slide
  }

  private fun measureAndLayout() {
    val widthSpec = View.MeasureSpec.makeMeasureSpec(
      dp(VIEW_WIDTH_DP),
      View.MeasureSpec.EXACTLY
    )

    val heightSpec = View.MeasureSpec.makeMeasureSpec(
      0,
      View.MeasureSpec.UNSPECIFIED
    )

    container.measure(widthSpec, heightSpec)

    container.layout(
      0,
      0,
      container.measuredWidth,
      container.measuredHeight
    )
  }

  private fun dp(value: Int): Int {
    return (value * context.resources.displayMetrics.density).toInt()
  }

  private fun diagnostics(): String {
    val textLayout = fileNameView.layout
    val lineCount = textLayout?.lineCount ?: -1
    val ellipsisCount = if (textLayout != null && lineCount > 0) {
      textLayout.getEllipsisCount(lineCount - 1)
    } else {
      -1
    }

    return "DocumentView measurement: " +
      "containerWidth=${container.measuredWidth}, " +
      "documentWidth=${documentView.measuredWidth}, " +
      "filenameWidth=${fileNameView.measuredWidth}, " +
      "textWidth=${fileNameView.paint.measureText(fileNameView.text.toString())}, " +
      "lineCount=$lineCount, " +
      "maxLines=${fileNameView.maxLines}, " +
      "ellipsisCount=$ellipsisCount"
  }
}
