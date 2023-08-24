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
import org.signal.libsignal.usernames.BaseUsernameException
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkSettingsState.ActiveTab
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.UsernameUtil
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import java.io.IOException

class UsernameLinkSettingsViewModel : ViewModel() {

  private val TAG = Log.tag(UsernameLinkSettingsViewModel::class.java)

  private val username: BehaviorSubject<String> = BehaviorSubject.createDefault(Recipient.self().username.get())

  private val _state = mutableStateOf(
    UsernameLinkSettingsState(
      activeTab = ActiveTab.Code,
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

  fun onTabSelected(tab: ActiveTab) {
    _state.value = _state.value.copy(
      activeTab = tab
    )
  }

  fun onQrCodeScanned(url: String) {
    _state.value = _state.value.copy(
      indeterminateProgress = true
    )

    disposable += Single
      .fromCallable {
        val username: String? = UsernameUtil.parseLink(url)

        if (username == null) {
          Log.w(TAG, "Failed to parse username from url")
          return@fromCallable QrScanResult.InvalidData
        }

        return@fromCallable try {
          val hashed: String = UsernameUtil.hashUsernameToBase64(username)
          val aci: ACI = ApplicationDependencies.getSignalServiceAccountManager().getAciByUsernameHash(hashed)
          QrScanResult.Success(Recipient.externalUsername(aci, username))
        } catch (e: BaseUsernameException) {
          Log.w(TAG, "Invalid username", e)
          QrScanResult.InvalidData
        } catch (e: NonSuccessfulResponseCodeException) {
          Log.w(TAG, "Non-successful response during username resolution", e)
          if (e.code == 404) {
            QrScanResult.NotFound(username)
          } else {
            QrScanResult.NetworkError
          }
        } catch (e: IOException) {
          Log.w(TAG, "Network error during username resolution", e)
          QrScanResult.NetworkError
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

  private fun generateQrCodeData(url: String): Single<QrCodeData> {
    return Single.fromCallable {
      QrCodeData.forData(url, 64)
    }
  }
}
