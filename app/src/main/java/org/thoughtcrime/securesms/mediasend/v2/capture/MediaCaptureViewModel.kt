package org.thoughtcrime.securesms.mediasend.v2.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registrationv3.data.QuickRegistrationRepository
import org.thoughtcrime.securesms.util.rx.RxStore
import java.io.FileDescriptor
import java.util.Optional
import java.util.concurrent.TimeUnit

class MediaCaptureViewModel(private val repository: MediaCaptureRepository) : ViewModel() {

  companion object {
    private val TAG = Log.tag(MediaCaptureViewModel::class.java)
  }

  private val store: RxStore<MediaCaptureState> = RxStore(MediaCaptureState())

  private val internalEvents: Subject<MediaCaptureEvent> = PublishSubject.create()
  private val qrData: Subject<String> = PublishSubject.create()

  val events: Observable<MediaCaptureEvent> = internalEvents.observeOn(AndroidSchedulers.mainThread())
  val disposables = CompositeDisposable()

  init {
    repository.getMostRecentItem { media ->
      store.update { state ->
        state.copy(mostRecentMedia = media)
      }
    }

    disposables += qrData
      .throttleFirst(5, TimeUnit.SECONDS)
      .filter { UsernameRepository.isValidLink(it) }
      .subscribeOn(Schedulers.io())
      .flatMapSingle { url ->
        UsernameRepository.fetchUsernameAndAciFromLink(url)
          .map { result ->
            when (result) {
              is UsernameRepository.UsernameLinkConversionResult.Success -> QrScanResult.Success(result.username.toString(), Recipient.externalUsername(result.aci, result.username.toString()))
              is UsernameRepository.UsernameLinkConversionResult.Invalid,
              is UsernameRepository.UsernameLinkConversionResult.NotFound,
              is UsernameRepository.UsernameLinkConversionResult.NetworkError -> QrScanResult.Failure
            }
          }
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { data ->
        if (data is QrScanResult.Success) {
          internalEvents.onNext(MediaCaptureEvent.UsernameScannedFromQrCode(data.recipient, data.username))
        } else {
          Log.w(TAG, "Failed to scan QR code.")
        }
      }

    disposables += qrData
      .throttleFirst(5, TimeUnit.SECONDS)
      .filter { it.startsWith("sgnl://linkdevice") }
      .subscribe { data ->
        internalEvents.onNext(MediaCaptureEvent.DeviceLinkScannedFromQrCode)
      }

    if (SignalStore.account.isRegistered) {
      disposables += qrData
        .throttleFirst(5, TimeUnit.SECONDS)
        .filter { it.startsWith("sgnl://rereg") && QuickRegistrationRepository.isValidReRegistrationQr(it) }
        .subscribe { data ->
          internalEvents.onNext(MediaCaptureEvent.ReregistrationScannedFromQrCode(data))
        }
    }
  }

  override fun onCleared() {
    store.dispose()
    disposables.dispose()
  }

  fun onImageCaptured(data: ByteArray, width: Int, height: Int) {
    repository.renderImageToMedia(data, width, height, this::onMediaRendered, this::onMediaRenderFailed)
  }

  fun onVideoCaptured(fd: FileDescriptor) {
    repository.renderVideoToMedia(fd, this::onMediaRendered, this::onMediaRenderFailed)
  }

  fun getMostRecentMedia(): Flowable<Optional<Media>> {
    return store.stateFlowable.map { Optional.ofNullable(it.mostRecentMedia) }
  }

  fun onQrCodeFound(data: String) {
    qrData.onNext(data)
  }

  private fun onMediaRendered(media: Media) {
    internalEvents.onNext(MediaCaptureEvent.MediaCaptureRendered(media))
  }

  private fun onMediaRenderFailed() {
    internalEvents.onNext(MediaCaptureEvent.MediaCaptureRenderFailed)
  }

  private sealed class QrScanResult {
    data class Success(val username: String, val recipient: Recipient) : QrScanResult()
    object Failure : QrScanResult()
  }

  class Factory(private val repository: MediaCaptureRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(MediaCaptureViewModel(repository)))
    }
  }
}
