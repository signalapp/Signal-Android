package org.thoughtcrime.securesms.mediasend.v2

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.models.media.Media
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.getWindowBreakpoint
import org.signal.core.util.Debouncer
import org.signal.core.util.OVERRIDE_TRANSITION_CLOSE_COMPAT
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.logging.Log
import org.signal.core.util.overrideActivityTransitionCompat
import org.signal.emoji.EmojiEventListener
import org.signal.mediasend.MediaSendRoute
import org.signal.mediasend.MediaValidator
import org.signal.mediasend.screens.capture.MediaCaptureBottomBar
import org.signal.mediasend.screens.capture.MediaCaptureScreenEvents
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardEvent
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardEventViewModel
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.mediasend.v2.review.MediaReviewFragment
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryPostCreationFragment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class MediaSelectionActivity :
  PassphraseRequiredActivity(),
  MediaReviewFragment.Callback,
  TextStoryPostCreationFragment.Callback,
  EmojiKeyboardPageFragment.Callback,
  EmojiEventListener,
  EmojiSearchFragment.Callback {

  private var selectedCaptureScreen: MediaSendRoute.Capture by mutableStateOf(MediaSendRoute.Capture.Camera)

  private var isOnCaptureScreen: Boolean by mutableStateOf(false)

  lateinit var viewModel: MediaSelectionViewModel

  private val lifecycleDisposable = LifecycleDisposable()

  private val addMessageCommandViewModel: EmojiKeyboardEventViewModel by viewModels()

  private val destination: MediaSelectionDestination
    get() = MediaSelectionDestination.fromBundle(requireNotNull(intent.getBundleExtra(DESTINATION)))

  override val textStoryDestinations: Set<ContactSearchKey.RecipientSearchKey>
    get() = (destination.getRecipientSearchKeyList() + destination.getRecipientSearchKey()).filterNotNull().toSet()

  override val isAddToGroupStoryFlow: Boolean
    get() = intent.getBooleanExtra(IS_ADD_TO_GROUP_STORY_FLOW, false)

  override val textStoryDraftText: CharSequence?
    get() = if (shareToTextStory) draftText else null

  private val isStory: Boolean
    get() = intent.getBooleanExtra(IS_STORY, false)

  private val shareToTextStory: Boolean
    get() = intent.getBooleanExtra(AS_TEXT_STORY, false)

  private val draftText: CharSequence?
    get() = intent.getCharSequenceExtra(MESSAGE)

  private val debouncer = Debouncer(200)

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onPreCreate() {
    val sendType: MessageSendType = requireNotNull(intent.getParcelableExtraCompat(MESSAGE_SEND_TYPE, MessageSendType::class.java))
    val initialMedia: List<Media> = intent.getParcelableArrayListExtraCompat(MEDIA, Media::class.java) ?: listOf()
    val message: CharSequence? = if (shareToTextStory) null else draftText
    val isReply: Boolean = intent.getBooleanExtra(IS_REPLY, false)
    val isAddToGroupStoryFlow: Boolean = intent.getBooleanExtra(IS_ADD_TO_GROUP_STORY_FLOW, false)

    val factory = MediaSelectionViewModel.Factory(destination, sendType, initialMedia, message, isReply, isStory, isAddToGroupStoryFlow, MediaSelectionRepository(this))
    viewModel = ViewModelProvider(this, factory)[MediaSelectionViewModel::class.java]
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContentView(R.layout.media_selection_activity)

    if (resources.getWindowBreakpoint() !is WindowBreakpoint.Small) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    val toggleBar: ComposeView = findViewById(R.id.toggle_bar)
    toggleBar.setContent {
      val state by viewModel.state.observeAsState()

      val canDisplayStorySwitch = remember(state?.selectedMedia) {
        state?.selectedMedia?.let { canDisplayStorySwitch(it) } ?: false
      }

      val canDisplayMediaPreview = remember(state?.selectedMedia) {
        state?.selectedMedia?.let { canDisplayMediaPreview(it) } ?: false
      }

      SignalTheme {
        if (isOnCaptureScreen) {
          MediaCaptureBottomBar(
            canDisplayToggleSwitch = canDisplayStorySwitch,
            canDisplayMediaBar = canDisplayMediaPreview,
            selectedCaptureScreen = selectedCaptureScreen,
            selectedMedia = state?.selectedMedia ?: emptyList(),
            onEvent = { event ->
              when (event) {
                MediaCaptureScreenEvents.ShowCamera -> debouncer.publish { popTextStoryPostCreationFragment() }
                MediaCaptureScreenEvents.ShowTextStory -> viewModel.sendCommand(HudCommand.GoToText)
                MediaCaptureScreenEvents.NextClicked -> viewModel.sendCommand(HudCommand.GoToReview)
                is MediaCaptureScreenEvents.Camera,
                is MediaCaptureScreenEvents.ParentStateChanged,
                is MediaCaptureScreenEvents.SelectedCaptureScreenChanged -> Unit
              }
            },
            modifier = Modifier.navigationBarsPadding()
          )
        }
      }
    }

    if (savedInstanceState == null) {
      val navHostFragment = NavHostFragment.create(R.navigation.media)

      supportFragmentManager
        .beginTransaction()
        .replace(R.id.fragment_container, navHostFragment, NAV_HOST_TAG)
        .commitNowAllowingStateLoss()

      navigateToStartDestination()
    } else {
      viewModel.onRestoreState(this, savedInstanceState)
    }

    (supportFragmentManager.findFragmentByTag(NAV_HOST_TAG) as NavHostFragment).navController.addOnDestinationChangedListener { _, d, _ ->
      when (d.id) {
        R.id.mediaCaptureFragment -> {
          selectedCaptureScreen = MediaSendRoute.Capture.Camera
          isOnCaptureScreen = true
          requestedOrientation = if (resources.getWindowBreakpoint() is WindowBreakpoint.Small) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
          } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          }
        }

        R.id.textStoryPostCreationFragment -> {
          selectedCaptureScreen = MediaSendRoute.Capture.TextStory
          isOnCaptureScreen = true
          requestedOrientation = if (resources.getWindowBreakpoint() is WindowBreakpoint.Small) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
          } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          }
        }

        else -> {
          isOnCaptureScreen = false
          requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
      }

      // Hard-cut rotation while capturing (like Pixel Camera) instead of the system's smooth rotate.
      window.attributes = window.attributes.apply {
        rotationAnimation = if (isOnCaptureScreen) {
          WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
        } else {
          WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE
        }
      }
    }

    lifecycleDisposable.bindTo(this)
    lifecycleDisposable += viewModel.mediaErrors
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(this::handleError)

    lifecycleDisposable += viewModel.videoTrimmedEvents
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { Toast.makeText(this, R.string.MediaReviewFragment__video_trimmed_to_fit, Toast.LENGTH_SHORT).show() }

    onBackPressedDispatcher.addCallback(OnBackPressed())

    if (savedInstanceState == null && intent.getBooleanExtra(IS_FOR_QUICK_RESTORE, false)) {
      QuickRestoreInfoDialog.show(supportFragmentManager)
    }
  }

  private fun handleError(error: MediaValidator.FilterError) {
    when (error) {
      MediaValidator.FilterError.None -> return
      is MediaValidator.FilterError.ItemTooLarge -> Toast.makeText(this, R.string.MediaReviewFragment__one_or_more_items_were_too_large, Toast.LENGTH_SHORT).show()
      is MediaValidator.FilterError.ItemInvalidType -> Toast.makeText(this, R.string.MediaReviewFragment__one_or_more_items_were_invalid, Toast.LENGTH_SHORT).show()
      MediaValidator.FilterError.TooManyItems -> Toast.makeText(this, R.string.MediaReviewFragment__too_many_items_selected, Toast.LENGTH_SHORT).show()
      is MediaValidator.FilterError.NoItems -> {
        error.cause?.let { handleError(it) }
        onNoMediaSelected()
      }
    }

    viewModel.clearMediaErrors()
  }

  private fun popTextStoryPostCreationFragment() {
    val navController = findNavController(R.id.fragment_container)
    if (navController.currentDestination?.id == R.id.textStoryPostCreationFragment) {
      navController.popBackStack()
    }
  }

  private fun canDisplayMediaPreview(selectedMedia: List<Media>): Boolean {
    return Stories.isFeatureEnabled() &&
      isCameraFirst() &&
      selectedMedia.isNotEmpty() &&
      (destination == MediaSelectionDestination.ChooseAfterMediaSelection || destination is MediaSelectionDestination.SingleStory)
  }

  private fun canDisplayStorySwitch(selectedMedia: List<Media>): Boolean {
    return Stories.isFeatureEnabled() &&
      isCameraFirst() &&
      selectedMedia.isEmpty() &&
      (destination == MediaSelectionDestination.ChooseAfterMediaSelection || destination is MediaSelectionDestination.SingleStory)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    viewModel.onSaveState(outState)
  }

  override fun onSentWithResult(mediaSendActivityResult: MediaSendActivityResult) {
    setResult(
      RESULT_OK,
      Intent().apply {
        putExtra(MediaSendActivityResult.EXTRA_RESULT, mediaSendActivityResult)
      }
    )

    finish()
    overrideActivityTransitionCompat(OVERRIDE_TRANSITION_CLOSE_COMPAT, R.anim.stationary, R.anim.camera_slide_to_bottom)
  }

  override fun onSentWithoutResult() {
    val intent = Intent()
    setResult(RESULT_OK, intent)

    finish()
    overrideActivityTransitionCompat(OVERRIDE_TRANSITION_CLOSE_COMPAT, R.anim.stationary, R.anim.camera_slide_to_bottom)
  }

  override fun onSendError(error: Throwable) {
    setResult(RESULT_CANCELED)

    // TODO [alex] - Toast
    Log.w(TAG, "Failed to send message.", error)

    finish()
    overrideActivityTransitionCompat(OVERRIDE_TRANSITION_CLOSE_COMPAT, R.anim.stationary, R.anim.camera_slide_to_bottom)
  }

  override fun onNoMediaSelected() {
    Log.w(TAG, "No media selected. Exiting.")

    setResult(RESULT_CANCELED)
    finish()
    overrideActivityTransitionCompat(OVERRIDE_TRANSITION_CLOSE_COMPAT, R.anim.stationary, R.anim.camera_slide_to_bottom)
  }

  override fun onPopFromReview() {
    if (isCameraFirst()) {
      viewModel.removeCameraFirstCapture()
    }

    if (!navigateToStartDestination()) {
      finish()
    }
  }

  private fun navigateToStartDestination(navHostFragment: NavHostFragment? = null): Boolean {
    val hostFragment: NavHostFragment = navHostFragment ?: supportFragmentManager.findFragmentByTag(NAV_HOST_TAG) as NavHostFragment

    val startDestination: Int = intent.getIntExtra(START_ACTION, -1)
    return if (startDestination > 0) {
      hostFragment.navController.safeNavigate(
        startDestination,
        Bundle().apply {
          putBoolean("first", true)
        }
      )

      true
    } else {
      false
    }
  }

  private fun isCameraFirst(): Boolean = intent.getIntExtra(START_ACTION, -1) == R.id.action_directly_to_mediaCaptureFragment

  override fun openEmojiSearch() {
    addMessageCommandViewModel.onEvent(EmojiKeyboardEvent.OpenEmojiSearch)
  }

  override fun onEmojiSelected(emoji: String?) {
    addMessageCommandViewModel.onEvent(EmojiKeyboardEvent.EmojiInsert(emoji))
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) {
    addMessageCommandViewModel.onEvent(EmojiKeyboardEvent.EmojiKeyEvent(keyEvent))
  }

  override fun closeEmojiSearch() {
    addMessageCommandViewModel.onEvent(EmojiKeyboardEvent.CloseEmojiSearch)
  }

  private inner class OnBackPressed : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      val navController = this@MediaSelectionActivity.findNavController(R.id.fragment_container)

      if (shareToTextStory && navController.currentDestination?.id == R.id.textStoryPostCreationFragment) {
        finish()
      }

      if (!navController.popBackStack()) {
        finish()
      }
    }
  }

  companion object {
    private val TAG = Log.tag(MediaSelectionActivity::class.java)

    private const val NAV_HOST_TAG = "NAV_HOST"

    private const val START_ACTION = "start.action"
    private const val MESSAGE_SEND_TYPE = "message.send.type"
    private const val MEDIA = "media"
    private const val MESSAGE = "message"
    private const val DESTINATION = "destination"
    private const val IS_REPLY = "is_reply"
    private const val IS_STORY = "is_story"
    private const val AS_TEXT_STORY = "as_text_story"
    private const val IS_ADD_TO_GROUP_STORY_FLOW = "is_add_to_group_story_flow"
    private const val IS_FOR_QUICK_RESTORE = "is_for_quick_restore"

    @JvmStatic
    fun camera(context: Context): Intent {
      return camera(context, false)
    }

    @JvmStatic
    fun camera(context: Context, isStory: Boolean): Intent {
      return buildIntent(
        context = context,
        startAction = R.id.action_directly_to_mediaCaptureFragment,
        isStory = isStory
      )
    }

    fun cameraForQuickRestore(context: Context): Intent {
      return buildIntent(
        context = context,
        startAction = R.id.action_directly_to_mediaCaptureFragment,
        isForQuickRestore = true
      )
    }

    fun addToGroupStory(
      context: Context,
      recipientId: RecipientId
    ): Intent {
      return buildIntent(
        context = context,
        startAction = R.id.action_directly_to_mediaCaptureFragment,
        isStory = true,
        isAddToGroupStoryFlow = true,
        destination = MediaSelectionDestination.SingleStory(recipientId)
      )
    }

    @JvmStatic
    fun camera(
      context: Context,
      messageSendType: MessageSendType,
      recipientId: RecipientId,
      isReply: Boolean
    ): Intent {
      return buildIntent(
        context = context,
        startAction = R.id.action_directly_to_mediaCaptureFragment,
        messageSendType = messageSendType,
        destination = MediaSelectionDestination.SingleRecipient(recipientId),
        isReply = isReply
      )
    }

    @JvmStatic
    fun gallery(
      context: Context,
      messageSendType: MessageSendType,
      media: List<Media>,
      recipientId: RecipientId,
      message: CharSequence?,
      isReply: Boolean
    ): Intent {
      return buildIntent(
        context = context,
        startAction = R.id.action_directly_to_mediaGalleryFragment,
        messageSendType = messageSendType,
        media = media,
        destination = MediaSelectionDestination.SingleRecipient(recipientId),
        message = message,
        isReply = isReply
      )
    }

    @JvmStatic
    fun editor(
      context: Context,
      messageSendType: MessageSendType,
      media: List<Media>,
      recipientId: RecipientId,
      message: CharSequence?
    ): Intent {
      return buildIntent(
        context = context,
        messageSendType = messageSendType,
        media = media,
        destination = MediaSelectionDestination.SingleRecipient(recipientId),
        message = message
      )
    }

    @JvmStatic
    fun editor(
      context: Context,
      media: List<Media>
    ): Intent {
      return buildIntent(
        context = context,
        media = media
      )
    }

    @JvmStatic
    fun share(
      context: Context,
      messageSendType: MessageSendType,
      media: List<Media>,
      recipientSearchKeys: List<ContactSearchKey.RecipientSearchKey>,
      message: CharSequence?,
      asTextStory: Boolean
    ): Intent {
      return buildIntent(
        context = context,
        messageSendType = messageSendType,
        media = media,
        destination = MediaSelectionDestination.MultipleRecipients(recipientSearchKeys),
        message = message,
        asTextStory = asTextStory,
        startAction = if (asTextStory) R.id.action_directly_to_textPostCreationFragment else -1,
        isStory = recipientSearchKeys.any { it.isStory }
      )
    }

    private fun buildIntent(
      context: Context,
      startAction: Int = -1,
      messageSendType: MessageSendType = MessageSendType.SignalMessageSendType,
      media: List<Media> = listOf(),
      destination: MediaSelectionDestination = MediaSelectionDestination.ChooseAfterMediaSelection,
      message: CharSequence? = null,
      isReply: Boolean = false,
      isStory: Boolean = false,
      asTextStory: Boolean = false,
      isAddToGroupStoryFlow: Boolean = false,
      isForQuickRestore: Boolean = false
    ): Intent {
      return Intent(context, MediaSelectionActivity::class.java).apply {
        putExtra(START_ACTION, startAction)
        putExtra(MESSAGE_SEND_TYPE, messageSendType)
        putParcelableArrayListExtra(MEDIA, ArrayList(media))
        putExtra(MESSAGE, message)
        putExtra(DESTINATION, destination.toBundle())
        putExtra(IS_REPLY, isReply)
        putExtra(IS_STORY, isStory)
        putExtra(AS_TEXT_STORY, asTextStory)
        putExtra(IS_ADD_TO_GROUP_STORY_FLOW, isAddToGroupStoryFlow)
        putExtra(IS_FOR_QUICK_RESTORE, isForQuickRestore)
      }
    }
  }
}
