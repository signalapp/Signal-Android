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
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeState
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkSettingsState.ActiveTab
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.util.UsernameUtil
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import java.util.Optional

class UsernameLinkSettingsViewModel : ViewModel() {

  private val TAG = Log.tag(UsernameLinkSettingsViewModel::class.java)

  private val _state = mutableStateOf(
    UsernameLinkSettingsState(
      activeTab = ActiveTab.Code,
      username = SignalStore.account().username!!,
      usernameLinkState = SignalStore.account().usernameLink?.let { UsernameLinkState.Present(UsernameUtil.generateLink(it)) } ?: UsernameLinkState.NotSet,
      qrCodeState = QrCodeState.Loading,
      qrCodeColorScheme = SignalStore.misc().usernameQrCodeColorScheme
    )
  )
  val state: State<UsernameLinkSettingsState> = _state

  private val disposable: CompositeDisposable = CompositeDisposable()
  private val usernameLink: BehaviorSubject<Optional<UsernameLinkComponents>> = BehaviorSubject.createDefault(Optional.ofNullable(SignalStore.account().usernameLink))
  private val usernameRepo: UsernameRepository = UsernameRepository()

  init {
    disposable += usernameLink
      .observeOn(Schedulers.io())
      .map { link -> link.map { UsernameUtil.generateLink(it) } }
      .flatMapSingle { generateQrCodeData(it) }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { qrData ->
        _state.value = _state.value.copy(
          qrCodeState = if (qrData.isPresent) QrCodeState.Present(qrData.get()) else QrCodeState.NotSet
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

    disposable += usernameRepo.createOrResetUsernameLink()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { result ->
        val components: Optional<UsernameLinkComponents> = when (result) {
          is UsernameLinkResetResult.Success -> Optional.of(result.components)
          is UsernameLinkResetResult.NetworkError -> Optional.empty()
          else -> { usernameLink.value ?: Optional.empty() }
        }

        _state.value = _state.value.copy(
          usernameLinkState = if (components.isPresent) {
            val link = UsernameUtil.generateLink(components.get())
            UsernameLinkState.Present(link)
          } else {
            UsernameLinkState.NotSet
          },
          usernameLinkResetResult = result,
          qrCodeState = if (components.isPresent && previousQrValue != null) {
            QrCodeState.Present(previousQrValue)
          } else {
            QrCodeState.NotSet
          }
        )
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

    disposable += usernameRepo.convertLinkToUsernameAndAci(url)
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
}
