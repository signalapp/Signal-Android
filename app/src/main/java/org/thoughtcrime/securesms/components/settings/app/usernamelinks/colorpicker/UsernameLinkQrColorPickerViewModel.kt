package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.collections.immutable.toImmutableList
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.UsernameUtil

class UsernameLinkQrColorPickerViewModel : ViewModel() {

  private val username: String = Recipient.self().username.get()

  private val _state = mutableStateOf(
    UsernameLinkQrColorPickerState(
      username = username,
      qrCodeData = null,
      colorSchemes = UsernameQrCodeColorScheme.values().asList().toImmutableList(),
      selectedColorScheme = SignalStore.misc().usernameQrCodeColorScheme
    )
  )

  val state: State<UsernameLinkQrColorPickerState> = _state

  private val disposable: CompositeDisposable = CompositeDisposable()

  init {
    disposable += Single
      .fromCallable { QrCodeData.forData(UsernameUtil.generateLink(username), 64) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { qrData ->
        _state.value = _state.value.copy(
          qrCodeData = qrData
        )
      }
  }

  override fun onCleared() {
    disposable.clear()
  }

  fun onColorSelected(color: UsernameQrCodeColorScheme) {
    SignalStore.misc().usernameQrCodeColorScheme = color
    _state.value = _state.value.copy(
      selectedColorScheme = color
    )
  }
}
