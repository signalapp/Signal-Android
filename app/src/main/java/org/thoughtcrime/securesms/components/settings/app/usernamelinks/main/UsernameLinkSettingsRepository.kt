/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.withTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.ui.compose.drawQr
import org.signal.core.ui.permissions.Permissions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.toLink
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import java.io.ByteArrayOutputStream
import org.signal.core.ui.R as CoreUiR

/**
 * All of the storage, network and drawing work behind [UsernameLinkSettingsViewModel].
 */
object UsernameLinkSettingsRepository {

  fun getUsername(): String = SignalStore.account.username.orEmpty()

  /** The user's username link, or null if they don't have one yet. */
  fun getUsernameLink(): String? = SignalStore.account.usernameLink?.toLink()

  fun getColorScheme(): UsernameQrCodeColorScheme = SignalStore.misc.usernameQrCodeColorScheme

  fun hasCameraPermission(): Boolean = Permissions.hasAll(AppDependencies.application, Manifest.permission.CAMERA)

  suspend fun createQrCodeData(link: String): QrCodeData {
    return withContext(Dispatchers.Default) { QrCodeData.forData(link) }
  }

  fun toLink(components: UsernameLinkComponents): String = components.toLink()

  /** Replaces the user's username link with a new one, or makes their first. */
  suspend fun resetUsernameLink(): UsernameLinkResetResult = UsernameRepository.createOrResetUsernameLink().await()

  /** Looks up whoever [url], read from a QR code, belongs to. */
  suspend fun lookUpUsernameLink(url: String): QrScanResult = UsernameQrScanRepository.lookupUsernameUrl(url).await()

  /** Looks for a username QR code in the image at [uri], and whoever it belongs to. */
  suspend fun scanImage(uri: Uri): QrScanResult = UsernameQrScanRepository.scanImageUriForQrCode(AppDependencies.application, uri).await()

  /** Draws the user's QR code badge to an image and stores it for sharing, returning where it can be read from. */
  suspend fun createQrCodeShareImage(qrCodeData: QrCodeData, colorScheme: UsernameQrCodeColorScheme, username: String): Uri {
    return withContext(Dispatchers.Default) {
      val helpText = AppDependencies.application.getString(R.string.UsernameLinkSettings_scan_this_qr_code)
      val bitmap = drawQrCodeImage(qrCodeData, colorScheme, username, helpText)

      try {
        val bytes = ByteArrayOutputStream().use { stream ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
          stream.toByteArray()
        }

        AppDependencies.blobs
          .forData(bytes)
          .withMimeType("image/png")
          .withFileName("SignalUsernameQr.png")
          .createForSingleSessionInMemory()
      } finally {
        bitmap.recycle()
      }
    }
  }

  /**
   * Composables can't be drawn to a bitmap unless they're entirely on screen, so the QR badge is drawn again by hand
   * for sharing. Sizes were picked arbitrarily.
   */
  private fun drawQrCodeImage(qrCodeData: QrCodeData, colorScheme: UsernameQrCodeColorScheme, username: String, helpText: String): Bitmap {
    val scaleFactor = 2
    val width = 424 * scaleFactor
    val height = 576 * scaleFactor
    val backgroundPadHorizontal = 64f * scaleFactor
    val backgroundPadVertical = 80f * scaleFactor
    val qrBorderWidth = width - (backgroundPadHorizontal * 2)
    val qrBorderHeight = 324f * scaleFactor
    val qrBorderRadius = 30f * scaleFactor
    val qrSize = 184f * scaleFactor
    val qrPadding = 16f * scaleFactor
    val borderSizeX = 40f * scaleFactor
    val borderSizeY = 32f * scaleFactor
    val helpTextHorizontalPad = 72 * scaleFactor
    val helpTextVerticalPad = 444f * scaleFactor
    val helpTextSize = 14f * scaleFactor
    val usernameVerticalPad = 348f * scaleFactor
    val usernameTextSize = 20f * scaleFactor

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.TRANSPARENT)
    }

    val androidCanvas = android.graphics.Canvas(bitmap)
    val composeCanvas = Canvas(androidCanvas)
    val canvasDrawScope = CanvasDrawScope()

    // Background
    androidCanvas.drawColor(colorScheme.backgroundColor.toArgb())

    // QR Border
    androidCanvas.withTranslation(x = backgroundPadHorizontal, y = backgroundPadVertical) {
      drawRoundRect(0f, 0f, qrBorderWidth, qrBorderHeight, qrBorderRadius, qrBorderRadius, Paint().apply { color = colorScheme.borderColor.toArgb() })

      drawRoundRect(borderSizeX, borderSizeY, borderSizeX + qrSize + qrPadding * 2, borderSizeY + qrSize + qrPadding * 2, 15f, 15f, Paint().apply { color = Color.WHITE })
      drawRoundRect(
        borderSizeX,
        borderSizeY,
        borderSizeX + qrSize + qrPadding * 2,
        borderSizeY + qrSize + qrPadding * 2,
        15f * scaleFactor,
        15f * scaleFactor,
        Paint().apply {
          color = colorScheme.outlineColor.toArgb()
          style = Paint.Style.STROKE
          strokeWidth = 4f
        }
      )

      // Draw the QR code
      composeCanvas.withSave {
        composeCanvas.translate((qrBorderWidth / 2) - (qrSize / 2), borderSizeY + qrPadding)

        canvasDrawScope.draw(
          density = object : Density {
            override val density: Float = 1f
            override val fontScale: Float = 1f
          },
          layoutDirection = LayoutDirection.Ltr,
          canvas = composeCanvas,
          size = Size(qrSize, qrSize)
        ) {
          drawQr(
            data = qrCodeData,
            foregroundColor = colorScheme.foregroundColor,
            backgroundColor = colorScheme.borderColor,
            deadzonePercent = 0.35f,
            logo = null
          )
        }
      }
    }

    // Draw the signal logo -- unfortunately can't have the normal QR code drawing handle it because it requires a composable ImageBitmap
    BitmapFactory.decodeResource(AppDependencies.application.resources, CoreUiR.drawable.qrcode_logo).also { logoBitmap ->
      val tintedPaint = Paint().apply {
        colorFilter = PorterDuffColorFilter(colorScheme.foregroundColor.toArgb(), PorterDuff.Mode.SRC_IN)
      }
      val sourceRect = Rect(0, 0, logoBitmap.width, logoBitmap.height)

      val logoSize = 36f * scaleFactor
      val destLeft = (width / 2f) - (logoSize / 2f)
      val destTop = destLeft - (10f * scaleFactor) + (logoSize / 2f)
      val destRect = RectF(destLeft, destTop, destLeft + logoSize, destTop + logoSize)
      androidCanvas.drawBitmap(logoBitmap, sourceRect, destRect, tintedPaint)
    }

    // Draw the username
    val usernamePaint = TextPaint().apply {
      color = colorScheme.textColor.toArgb()
      textSize = usernameTextSize
      typeface = if (Build.VERSION.SDK_INT < 26) {
        Typeface.DEFAULT_BOLD
      } else {
        Typeface.Builder("")
          .setFallback("sans-serif")
          .setWeight(600)
          .build()
      }
    }

    val usernameMaxWidth = qrBorderWidth - borderSizeX * 2f
    val usernameLayout = StaticLayout(username, usernamePaint, usernameMaxWidth.toInt(), Layout.Alignment.ALIGN_CENTER, 1f, 0f, true)
    val usernameVerticalOffset = when (usernameLayout.lineCount) {
      1 -> 0f
      2 -> usernameTextSize / 2f
      else -> usernameTextSize
    }

    androidCanvas.withTranslation(x = backgroundPadHorizontal + borderSizeX, y = usernameVerticalPad - usernameVerticalOffset) {
      usernameLayout.draw(this)
    }

    // Draw the help text
    val helpTextPaint = TextPaint().apply {
      isAntiAlias = true
      color = 0xFF3C3C43.toInt()
      textSize = helpTextSize
      typeface = if (Build.VERSION.SDK_INT < 26) {
        Typeface.DEFAULT
      } else {
        Typeface.Builder("")
          .setFallback("sans-serif")
          .setWeight(400)
          .build()
      }
    }

    val maxWidth = width - helpTextHorizontalPad * 2
    val helpTextLayout = StaticLayout(helpText, helpTextPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1f, 0f, true)

    androidCanvas.withTranslation(x = helpTextHorizontalPad.toFloat(), y = helpTextVerticalPad) {
      helpTextLayout.draw(androidCanvas)
    }

    return bitmap
  }
}
