package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeState
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.drawQr
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkSettingsState.ActiveTab
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.toLink
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.NetworkUtil
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import java.util.Optional

class UsernameLinkSettingsViewModel : ViewModel() {

  private val TAG = Log.tag(UsernameLinkSettingsViewModel::class.java)

  private val _state = mutableStateOf(
    UsernameLinkSettingsState(
      activeTab = ActiveTab.Code,
      username = SignalStore.account().username!!,
      usernameLinkState = SignalStore.account().usernameLink?.let { UsernameLinkState.Present(it.toLink()) } ?: UsernameLinkState.NotSet,
      qrCodeState = QrCodeState.Loading,
      qrCodeColorScheme = SignalStore.misc().usernameQrCodeColorScheme
    )
  )
  val state: State<UsernameLinkSettingsState> = _state

  private val disposable: CompositeDisposable = CompositeDisposable()
  private val usernameLink: BehaviorSubject<Optional<UsernameLinkComponents>> = BehaviorSubject.createDefault(Optional.ofNullable(SignalStore.account().usernameLink))

  init {
    disposable += usernameLink
      .observeOn(Schedulers.io())
      .map { link -> link.map { it.toLink() } }
      .flatMapSingle { generateQrCodeData(it) }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { qrData ->
        _state.value = _state.value.copy(
          qrCodeState = if (qrData.isPresent) QrCodeState.Present(qrData.get()) else QrCodeState.NotSet
        )
      }

    if (SignalStore.account().usernameLink == null) {
      onUsernameLinkReset()
    }
  }

  override fun onCleared() {
    disposable.clear()
  }

  fun onResume() {
    _state.value = _state.value.copy(
      qrCodeColorScheme = SignalStore.misc().usernameQrCodeColorScheme
    )
  }

  fun onTabSelected(tab: ActiveTab) {
    _state.value = _state.value.copy(
      activeTab = tab
    )
  }

  fun onUsernameLinkReset() {
    if (!NetworkUtil.isConnected(ApplicationDependencies.getApplication())) {
      _state.value = _state.value.copy(
        usernameLinkResetResult = UsernameLinkResetResult.NetworkUnavailable
      )
      return
    }

    val currentValue = _state.value
    val previousQrValue: QrCodeData? = if (currentValue.qrCodeState is QrCodeState.Present) {
      currentValue.qrCodeState.data
    } else {
      null
    }

    _state.value = _state.value.copy(
      usernameLinkState = UsernameLinkState.Resetting,
      qrCodeState = QrCodeState.Loading
    )

    disposable += UsernameRepository.createOrResetUsernameLink()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { result ->
        val components: Optional<UsernameLinkComponents> = when (result) {
          is UsernameLinkResetResult.Success -> Optional.of(result.components)
          is UsernameLinkResetResult.NetworkError -> Optional.empty()
          else -> { usernameLink.value ?: Optional.empty() }
        }

        _state.value = _state.value.copy(
          usernameLinkState = if (components.isPresent) {
            val link = components.get().toLink()
            UsernameLinkState.Present(link)
          } else {
            UsernameLinkState.NotSet
          },
          usernameLinkResetResult = result,
          qrCodeState = if (!components.isPresent && previousQrValue != null) {
            QrCodeState.Present(previousQrValue)
          } else {
            QrCodeState.NotSet
          }
        )

        if (components.isPresent) {
          usernameLink.onNext(components)
        }
      }
  }

  fun onUsernameLinkResetResultHandled() {
    _state.value = _state.value.copy(
      usernameLinkResetResult = null
    )
  }

  fun onQrCodeScanned(url: String) {
    _state.value = _state.value.copy(
      indeterminateProgress = true
    )

    disposable += UsernameRepository.fetchUsernameAndAciFromLink(url)
      .map { result ->
        when (result) {
          is UsernameRepository.UsernameLinkConversionResult.Success -> QrScanResult.Success(Recipient.externalUsername(result.aci, result.username.toString()))
          is UsernameRepository.UsernameLinkConversionResult.Invalid -> QrScanResult.InvalidData
          is UsernameRepository.UsernameLinkConversionResult.NotFound -> QrScanResult.NotFound(result.username?.toString())
          is UsernameRepository.UsernameLinkConversionResult.NetworkError -> QrScanResult.NetworkError
        }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { result ->
        _state.value = _state.value.copy(
          qrScanResult = result,
          indeterminateProgress = false
        )
      }
  }

  fun onQrResultHandled() {
    _state.value = _state.value.copy(
      qrScanResult = null
    )
  }

  private fun generateQrCodeData(url: Optional<String>): Single<Optional<QrCodeData>> {
    return Single.fromCallable {
      url.map { QrCodeData.forData(it, 64) }
    }
  }

  /**
   * Fun fact: there's no way to draw a composable to a bitmap. You'd think there would be, but there isn't. You can "screenshot" it if it's 100% on-screen,
   * but if it's partially offscreen you're SOL. So, we get to go through the fun process of re-drawing the QR badge to an image for sharing ourselves.
   *
   * Sizes were picked arbitrarily.
   *
   * I hate this as much as you do.
   */
  fun generateQrCodeImage(): Bitmap? {
    val state: UsernameLinkSettingsState = _state.value

    if (state.qrCodeState !is QrCodeState.Present) {
      Log.w(TAG, "Invalid state to generate QR code! ${state.qrCodeState.javaClass.simpleName}")
      return null
    }

    val qrCodeData: QrCodeData = state.qrCodeState.data

    val width = 480
    val height = 525
    val qrSize = 300f
    val qrPadding = 25f
    val borderSizeX = 64f
    val borderSizeY = 52f

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.TRANSPARENT)
    }

    val androidCanvas = android.graphics.Canvas(bitmap)
    val composeCanvas = Canvas(androidCanvas)
    val canvasDrawScope = CanvasDrawScope()

    // Draw the background
    androidCanvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 30f, 30f, Paint().apply { color = state.qrCodeColorScheme.borderColor.toArgb() })
    androidCanvas.drawRoundRect(borderSizeX, borderSizeY, borderSizeX + qrSize + qrPadding * 2, borderSizeY + qrSize + qrPadding * 2, 15f, 15f, Paint().apply { color = Color.WHITE })
    androidCanvas.drawRoundRect(
      borderSizeX,
      borderSizeY,
      borderSizeX + qrSize + qrPadding * 2,
      borderSizeY + qrSize + qrPadding * 2,
      15f,
      15f,
      Paint().apply {
        color = state.qrCodeColorScheme.outlineColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 4f
      }
    )

    // Draw the QR code
    composeCanvas.translate((width / 2) - (qrSize / 2), 80f)
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
        foregroundColor = state.qrCodeColorScheme.foregroundColor,
        backgroundColor = state.qrCodeColorScheme.borderColor,
        deadzonePercent = 0.35f,
        logo = null
      )
    }
    composeCanvas.translate(-90f, -80f)

    // Draw the signal logo -- unfortunately can't have the normal QR code drawing handle it because it requires a composable ImageBitmap
    BitmapFactory.decodeResource(ApplicationDependencies.getApplication().resources, R.drawable.qrcode_logo).also { logoBitmap ->
      val tintedPaint = Paint().apply {
        colorFilter = PorterDuffColorFilter(state.qrCodeColorScheme.foregroundColor.toArgb(), PorterDuff.Mode.SRC_IN)
      }
      val sourceRect = Rect(0, 0, logoBitmap.width, logoBitmap.height)
      val destRect = RectF(210f, 200f, 270f, 260f)
      androidCanvas.drawBitmap(logoBitmap, sourceRect, destRect, tintedPaint)
    }

    // Draw the text
    val textPaint = Paint().apply {
      color = state.qrCodeColorScheme.textColor.toArgb()
      textSize = 34f
      typeface = if (Build.VERSION.SDK_INT < 26) {
        Typeface.DEFAULT_BOLD
      } else {
        Typeface.Builder("")
          .setFallback("sans-serif")
          .setWeight(600)
          .build()
      }
    }
    val textBounds = Rect()
    textPaint.getTextBounds(state.username, 0, state.username.length, textBounds)

    androidCanvas.drawText(state.username, (width / 2f) - (textBounds.width() / 2f), 465f, textPaint)

    return bitmap
  }
}
