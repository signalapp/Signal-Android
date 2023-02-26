package org.thoughtcrime.securesms.mediasend.v2

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.getParcelableArrayListCompat
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.MessageStyler
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.mediasend.VideoEditorFragment
import org.thoughtcrime.securesms.mediasend.v2.review.AddMessageCharacterCount
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.livedata.Store
import java.util.Collections

/**
 * ViewModel which maintains the list of selected media and other shared values.
 */
class MediaSelectionViewModel(
  val destination: MediaSelectionDestination,
  sendType: MessageSendType,
  initialMedia: List<Media>,
  initialMessage: CharSequence?,
  val isReply: Boolean,
  isStory: Boolean,
  val isAddToGroupStoryFlow: Boolean,
  private val repository: MediaSelectionRepository,
  private val identityChangesSince: Long = System.currentTimeMillis()
) : ViewModel() {

  private val selectedMediaSubject: Subject<List<Media>> = BehaviorSubject.create()

  private val store: Store<MediaSelectionState> = Store(
    MediaSelectionState(
      sendType = sendType,
      message = initialMessage,
      isStory = isStory
    )
  )

  private val addAMessageUpdatePublisher = BehaviorProcessor.create<CharSequence>()

  val isContactSelectionRequired = destination == MediaSelectionDestination.ChooseAfterMediaSelection

  val state: LiveData<MediaSelectionState> = store.stateLiveData

  private val internalHudCommands = PublishSubject.create<HudCommand>()

  val mediaErrors: PublishSubject<MediaValidator.FilterError> = PublishSubject.create()
  val hudCommands: Observable<HudCommand> = internalHudCommands

  private val disposables = CompositeDisposable()

  fun watchAddAMessageCount(): Flowable<AddMessageCharacterCount> {
    return addAMessageUpdatePublisher
      .onBackpressureLatest()
      .map {
        val iterator = BreakIteratorCompat.getInstance()
        iterator.setText(it)
        AddMessageCharacterCount(iterator.countBreaks())
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun updateAddAMessageCount(input: CharSequence?) {
    addAMessageUpdatePublisher.onNext(input ?: "")
  }

  private val isMeteredDisposable: Disposable = repository.isMetered.subscribe { metered ->
    store.update {
      it.copy(
        isMeteredConnection = metered,
        isPreUploadEnabled = shouldPreUpload(metered, it.sendType.usesSmsTransport, it.recipient)
      )
    }
  }

  private var lastMediaDrag: Pair<Int, Int> = Pair(0, 0)

  init {
    val recipientSearchKey = destination.getRecipientSearchKey()
    if (recipientSearchKey != null) {
      store.update(Recipient.live(recipientSearchKey.recipientId).liveData) { r, s ->
        s.copy(
          recipient = r,
          isPreUploadEnabled = shouldPreUpload(s.isMeteredConnection, s.sendType.usesSmsTransport, r)
        )
      }
    }

    if (initialMedia.isNotEmpty()) {
      addMedia(initialMedia)
    }

    disposables += selectedMediaSubject.map { media ->
      Stories.MediaTransform.getSendRequirements(media)
    }.subscribeBy { requirements ->
      store.update {
        it.copy(storySendRequirements = requirements)
      }
    }
  }

  override fun onCleared() {
    isMeteredDisposable.dispose()
    disposables.clear()
  }

  fun kick() {
    store.update { it }
  }

  fun sendCommand(hudCommand: HudCommand) {
    internalHudCommands.onNext(hudCommand)
  }

  fun setTouchEnabled(isEnabled: Boolean) {
    store.update { it.copy(isTouchEnabled = isEnabled) }
  }

  fun setSuppressEmptyError(isSuppressed: Boolean) {
    store.update { it.copy(suppressEmptyError = isSuppressed) }
  }

  fun addMedia(media: Media) {
    addMedia(listOf(media))
  }

  fun isStory(): Boolean {
    return store.state.isStory
  }

  fun getStorySendRequirements(): Stories.MediaTransform.SendRequirements {
    return store.state.storySendRequirements
  }

  private fun addMedia(media: List<Media>) {
    val newSelectionList: List<Media> = linkedSetOf<Media>().apply {
      addAll(store.state.selectedMedia)
      addAll(media)
    }.toList()

    disposables.add(
      repository
        .populateAndFilterMedia(newSelectionList, getMediaConstraints(), store.state.maxSelection, store.state.isStory)
        .subscribe { filterResult ->
          if (filterResult.filteredMedia.isNotEmpty()) {
            store.update {
              it.copy(
                selectedMedia = filterResult.filteredMedia,
                focusedMedia = it.focusedMedia ?: filterResult.filteredMedia.first()
              )
            }

            selectedMediaSubject.onNext(filterResult.filteredMedia)

            val newMedia = filterResult.filteredMedia.toSet().intersect(media).toList()
            startUpload(newMedia)
          }

          if (filterResult.filterError != null) {
            mediaErrors.onNext(filterResult.filterError)
          }
        }
    )
  }

  fun swapMedia(originalStart: Int, end: Int): Boolean {
    var start: Int = originalStart

    if (lastMediaDrag.first == start && lastMediaDrag.second == end) {
      return true
    } else if (lastMediaDrag.first == start) {
      start = lastMediaDrag.second
    }

    val snapshot = store.state

    if (end >= snapshot.selectedMedia.size || end < 0 || start >= snapshot.selectedMedia.size || start < 0) {
      return false
    }

    lastMediaDrag = Pair(originalStart, end)

    val newMediaList = snapshot.selectedMedia.toMutableList()

    if (start < end) {
      for (i in start until end) {
        Collections.swap(newMediaList, i, i + 1)
      }
    } else {
      for (i in start downTo end + 1) {
        Collections.swap(newMediaList, i, i - 1)
      }
    }

    store.update {
      it.copy(
        selectedMedia = newMediaList
      )
    }

    return true
  }

  fun isValidMediaDragPosition(position: Int): Boolean {
    return position >= 0 && position < store.state.selectedMedia.size
  }

  fun onMediaDragFinished() {
    lastMediaDrag = Pair(0, 0)
  }

  fun removeMedia(media: Media) {
    val snapshot = store.state
    val newMediaList = snapshot.selectedMedia - media
    val oldFocusIndex = snapshot.selectedMedia.indexOf(media)
    val newFocus = when {
      newMediaList.isEmpty() -> null
      media == snapshot.focusedMedia -> newMediaList[Util.clamp(oldFocusIndex, 0, newMediaList.size - 1)]
      else -> snapshot.focusedMedia
    }

    store.update {
      it.copy(
        selectedMedia = newMediaList,
        focusedMedia = newFocus,
        editorStateMap = it.editorStateMap - media.uri,
        cameraFirstCapture = if (media == it.cameraFirstCapture) null else it.cameraFirstCapture
      )
    }

    if (newMediaList.isEmpty() && !store.state.suppressEmptyError) {
      mediaErrors.onNext(MediaValidator.FilterError.NoItems())
    }

    selectedMediaSubject.onNext(newMediaList)
    repository.deleteBlobs(listOf(media))

    cancelUpload(media)
  }

  fun addCameraFirstCapture(media: Media) {
    store.update { state ->
      state.copy(cameraFirstCapture = media)
    }
    addMedia(media)
  }

  fun removeCameraFirstCapture() {
    val cameraFirstCapture: Media? = store.state.cameraFirstCapture
    if (cameraFirstCapture != null) {
      setSuppressEmptyError(true)
      removeMedia(cameraFirstCapture)
    }
  }

  fun setFocusedMedia(media: Media) {
    store.update { it.copy(focusedMedia = media) }
  }

  fun setFocusedMedia(position: Int) {
    store.update {
      if (position >= it.selectedMedia.size) {
        it.copy(focusedMedia = null)
      } else {
        it.copy(focusedMedia = it.selectedMedia[position])
      }
    }
  }

  fun getMediaConstraints(): MediaConstraints {
    return if (store.state.sendType.usesSmsTransport) {
      MediaConstraints.getMmsMediaConstraints(store.state.sendType.simSubscriptionId ?: -1)
    } else {
      MediaConstraints.getPushMediaConstraints()
    }
  }

  fun setSentMediaQuality(sentMediaQuality: SentMediaQuality) {
    if (sentMediaQuality == store.state.quality) {
      return
    }

    store.update { it.copy(quality = sentMediaQuality, isPreUploadEnabled = false) }
    repository.uploadRepository.cancelAllUploads()
  }

  fun setMessage(text: CharSequence?) {
    store.update { it.copy(message = text) }
  }

  fun incrementViewOnceState() {
    store.update { it.copy(viewOnceToggleState = it.viewOnceToggleState.next()) }
  }

  fun getEditorState(uri: Uri): Any? {
    return store.state.editorStateMap[uri]
  }

  fun setEditorState(uri: Uri, state: Any) {
    store.update {
      it.copy(editorStateMap = it.editorStateMap + (uri to state))
    }
  }

  fun onVideoBeginEdit(uri: Uri) {
    cancelUpload(MediaBuilder.buildMedia(uri))
  }

  fun send(
    selectedContacts: List<ContactSearchKey.RecipientSearchKey> = emptyList(),
    scheduledDate: Long? = null
  ): Maybe<MediaSendActivityResult> = send(selectedContacts, scheduledDate ?: -1)

  fun send(
    selectedContacts: List<ContactSearchKey.RecipientSearchKey> = emptyList(),
    scheduledDate: Long
  ): Maybe<MediaSendActivityResult> {
    return UntrustedRecords.checkForBadIdentityRecords(selectedContacts.toSet(), identityChangesSince).andThen(
      repository.send(
        selectedMedia = store.state.selectedMedia,
        stateMap = store.state.editorStateMap,
        quality = store.state.quality,
        message = store.state.message,
        isSms = store.state.sendType.usesSmsTransport,
        isViewOnce = isViewOnceEnabled(),
        singleContact = destination.getRecipientSearchKey(),
        contacts = selectedContacts.ifEmpty { destination.getRecipientSearchKeyList() },
        mentions = MentionAnnotation.getMentionsFromAnnotations(store.state.message),
        bodyRanges = MessageStyler.getStyling(store.state.message),
        sendType = store.state.sendType,
        scheduledTime = scheduledDate
      )
    )
  }

  private fun isViewOnceEnabled(): Boolean {
    return !store.state.sendType.usesSmsTransport &&
      store.state.selectedMedia.size == 1 &&
      store.state.viewOnceToggleState == MediaSelectionState.ViewOnceToggleState.ONCE
  }

  private fun startUpload(media: List<Media>) {
    if (!store.state.isPreUploadEnabled) {
      return
    }

    val filteredPreUploadMedia = if (destination is MediaSelectionDestination.SingleRecipient || !Stories.isFeatureEnabled()) {
      media
    } else {
      media.filter { Stories.MediaTransform.canPreUploadMedia(it) }
    }

    repository.uploadRepository.startUpload(filteredPreUploadMedia, store.state.recipient)
  }

  private fun cancelUpload(media: Media) {
    repository.uploadRepository.cancelUpload(media)
  }

  private fun shouldPreUpload(metered: Boolean, isSms: Boolean, recipient: Recipient?): Boolean {
    return !metered && !isSms && !repository.isLocalSelfSend(recipient, isSms)
  }

  fun onSaveState(outState: Bundle) {
    val snapshot = store.state

    outState.putParcelableArrayList(STATE_SELECTION, ArrayList(snapshot.selectedMedia))
    outState.putParcelable(STATE_FOCUSED, snapshot.focusedMedia)
    outState.putInt(STATE_QUALITY, snapshot.quality.code)
    outState.putCharSequence(STATE_MESSAGE, snapshot.message)
    outState.putInt(STATE_VIEW_ONCE, snapshot.viewOnceToggleState.code)
    outState.putBoolean(STATE_TOUCH_ENABLED, snapshot.isTouchEnabled)
    outState.putBoolean(STATE_SENT, snapshot.isSent)
    outState.putParcelable(STATE_CAMERA_FIRST_CAPTURE, snapshot.cameraFirstCapture)

    val editorStates: List<Bundle> = store.state.editorStateMap.entries.map { it.toBundleStateEntry() }
    outState.putParcelableArrayList(STATE_EDITORS, ArrayList(editorStates))
  }

  fun hasSelectedMedia(): Boolean {
    return store.state.selectedMedia.isNotEmpty()
  }

  fun onRestoreState(savedInstanceState: Bundle) {
    val selection: List<Media> = savedInstanceState.getParcelableArrayListCompat(STATE_SELECTION, Media::class.java) ?: emptyList()
    val focused: Media? = savedInstanceState.getParcelableCompat(STATE_FOCUSED, Media::class.java)
    val quality: SentMediaQuality = SentMediaQuality.fromCode(savedInstanceState.getInt(STATE_QUALITY))
    val message: CharSequence? = savedInstanceState.getCharSequence(STATE_MESSAGE)
    val viewOnce: MediaSelectionState.ViewOnceToggleState = MediaSelectionState.ViewOnceToggleState.fromCode(savedInstanceState.getInt(STATE_VIEW_ONCE))
    val touchEnabled: Boolean = savedInstanceState.getBoolean(STATE_TOUCH_ENABLED)
    val sent: Boolean = savedInstanceState.getBoolean(STATE_SENT)
    val cameraFirstCapture: Media? = savedInstanceState.getParcelableCompat(STATE_CAMERA_FIRST_CAPTURE, Media::class.java)

    val editorStates: List<Bundle> = savedInstanceState.getParcelableArrayListCompat(STATE_EDITORS, Bundle::class.java) ?: emptyList()
    val editorStateMap = editorStates.associate { it.toAssociation() }

    selectedMediaSubject.onNext(selection)

    store.update { state ->
      state.copy(
        selectedMedia = selection,
        focusedMedia = focused,
        quality = quality,
        message = message,
        viewOnceToggleState = viewOnce,
        isTouchEnabled = touchEnabled,
        isSent = sent,
        cameraFirstCapture = cameraFirstCapture,
        editorStateMap = editorStateMap
      )
    }
  }

  private fun Bundle.toAssociation(): Pair<Uri, Any> {
    val key: Uri = requireNotNull(getParcelableCompat(BUNDLE_URI, Uri::class.java))

    val value: Any = if (getBoolean(BUNDLE_IS_IMAGE)) {
      ImageEditorFragment.Data(this)
    } else {
      VideoEditorFragment.Data.fromBundle(this)
    }

    return key to value
  }

  private fun Map.Entry<Uri, Any>.toBundleStateEntry(): Bundle {
    return when (val value = this.value) {
      is ImageEditorFragment.Data -> {
        value.bundle.apply {
          putParcelable(BUNDLE_URI, key)
          putBoolean(BUNDLE_IS_IMAGE, true)
        }
      }
      is VideoEditorFragment.Data -> {
        value.bundle.apply {
          putParcelable(BUNDLE_URI, key)
          putBoolean(BUNDLE_IS_IMAGE, false)
        }
      }
      else -> {
        throw IllegalStateException()
      }
    }
  }

  companion object {
    private const val STATE_PREFIX = "selection.view.model"

    private const val BUNDLE_URI = "$STATE_PREFIX.uri"
    private const val BUNDLE_IS_IMAGE = "$STATE_PREFIX.is_image"
    private const val STATE_SELECTION = "$STATE_PREFIX.selection"
    private const val STATE_FOCUSED = "$STATE_PREFIX.focused"
    private const val STATE_QUALITY = "$STATE_PREFIX.quality"
    private const val STATE_MESSAGE = "$STATE_PREFIX.message"
    private const val STATE_VIEW_ONCE = "$STATE_PREFIX.viewOnce"
    private const val STATE_TOUCH_ENABLED = "$STATE_PREFIX.touchEnabled"
    private const val STATE_SENT = "$STATE_PREFIX.sent"
    private const val STATE_CAMERA_FIRST_CAPTURE = "$STATE_PREFIX.camera_first_capture"
    private const val STATE_EDITORS = "$STATE_PREFIX.editors"
  }

  class Factory(
    private val destination: MediaSelectionDestination,
    private val sendType: MessageSendType,
    private val initialMedia: List<Media>,
    private val initialMessage: CharSequence?,
    private val isReply: Boolean,
    private val isStory: Boolean,
    private val isAddToGroupStoryFlow: Boolean,
    private val repository: MediaSelectionRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(MediaSelectionViewModel(destination, sendType, initialMedia, initialMessage, isReply, isStory, isAddToGroupStoryFlow, repository)))
    }
  }
}
