package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.UsernameUtil

class UsernameLinkSettingsViewModel : ViewModel() {

  private val username: BehaviorSubject<String> = BehaviorSubject.createDefault(Recipient.self().username.get())

  private val _state = mutableStateOf(
    UsernameLinkSettingsState(
      username = username.value!!,
      usernameLink = UsernameUtil.generateLink(username.value!!),
      qrCodeData = null,
      qrCodeColorScheme = SignalStore.misc().usernameQrCodeColorScheme
    )
  )

  val state: State<UsernameLinkSettingsState> = _state

  private val disposable: CompositeDisposable = CompositeDisposable()

  init {
    disposable += username
      .observeOn(Schedulers.io())
      .map { UsernameUtil.generateLink(it) }
      .flatMapSingle { generateQrCodeData(it) }
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

  fun onResume() {
    _state.value = _state.value.copy(
      qrCodeColorScheme = SignalStore.misc().usernameQrCodeColorScheme
    )
  }

  private fun generateQrCodeData(url: String): Single<QrCodeData> {
    return Single.fromCallable {
      QrCodeData.forData(url, 64)
    }
  }
}
