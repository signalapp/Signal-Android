package org.thoughtcrime.securesms.mediasend.v2

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.animation.ArgbEvaluatorCompat
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil
import org.thoughtcrime.securesms.mediasend.CameraDisplay
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.mediasend.v2.review.MediaReviewFragment
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryPostCreationViewModel
import org.thoughtcrime.securesms.mediasend.v2.text.send.TextStoryPostSendRepository
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.visible

class MediaSelectionActivity :
  PassphraseRequiredActivity(),
  MediaReviewFragment.Callback,
  EmojiKeyboardPageFragment.Callback,
  EmojiEventListener,
  EmojiSearchFragment.Callback {

  private var animateInShadowLayerValueAnimator: ValueAnimator? = null
  private var animateInTextColorValueAnimator: ValueAnimator? = null
  private var animateOutShadowLayerValueAnimator: ValueAnimator? = null
  private var animateOutTextColorValueAnimator: ValueAnimator? = null

  lateinit var viewModel: MediaSelectionViewModel

  private val textViewModel: TextStoryPostCreationViewModel by viewModels(
    factoryProducer = {
      TextStoryPostCreationViewModel.Factory(TextStoryPostSendRepository())
    }
  )

  private val destination: MediaSelectionDestination
    get() = MediaSelectionDestination.fromBundle(requireNotNull(intent.getBundleExtra(DESTINATION)))

  private val isStory: Boolean
    get() = intent.getBooleanExtra(IS_STORY, false)

  private val shareToTextStory: Boolean
    get() = intent.getBooleanExtra(AS_TEXT_STORY, false)

  private val draftText: CharSequence?
    get() = intent.getCharSequenceExtra(MESSAGE)

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContentView(R.layout.media_selection_activity)

    FullscreenHelper.showSystemUI(window)
    WindowUtil.setNavigationBarColor(this, 0x01000000)
    WindowUtil.setStatusBarColor(window, Color.TRANSPARENT)

    val sendType: MessageSendType = requireNotNull(intent.getParcelableExtra(MESSAGE_SEND_TYPE))
    val initialMedia: List<Media> = intent.getParcelableArrayListExtra(MEDIA) ?: listOf()
    val message: CharSequence? = if (shareToTextStory) null else draftText
    val isReply: Boolean = intent.getBooleanExtra(IS_REPLY, false)
    val isAddToGroupStoryFlow: Boolean = intent.getBooleanExtra(IS_ADD_TO_GROUP_STORY_FLOW, false)

    val factory = MediaSelectionViewModel.Factory(destination, sendType, initialMedia, message, isReply, isStory, isAddToGroupStoryFlow, MediaSelectionRepository(this))
    viewModel = ViewModelProvider(this, factory)[MediaSelectionViewModel::class.java]

    val textStoryToggle: ConstraintLayout = findViewById(R.id.switch_widget)
    val cameraDisplay = CameraDisplay.getDisplay(this)

    textStoryToggle.updateLayoutParams<FrameLayout.LayoutParams> {
      bottomMargin = cameraDisplay.getToggleBottomMargin()
    }

    val cameraSelectedConstraintSet = ConstraintSet().apply {
      clone(textStoryToggle)
    }
    val textSelectedConstraintSet = ConstraintSet().apply {
      clone(this@MediaSelectionActivity, R.layout.media_selection_activity_text_selected_constraints)
    }

    val textSwitch: TextView = findViewById(R.id.text_switch)
    val cameraSwitch: TextView = findViewById(R.id.camera_switch)

    textSwitch.setOnClickListener {
      viewModel.sendCommand(HudCommand.GoToText)
    }

    cameraSwitch.setOnClickListener {
      viewModel.sendCommand(HudCommand.GoToCapture)
    }

    if (savedInstanceState == null) {
      if (shareToTextStory) {
        initializeTextStory()
      }

      cameraSwitch.isSelected = true

      val navHostFragment = NavHostFragment.create(R.navigation.media)

      supportFragmentManager
        .beginTransaction()
        .replace(R.id.fragment_container, navHostFragment, NAV_HOST_TAG)
        .commitNowAllowingStateLoss()

      navigateToStartDestination()
    } else {
      viewModel.onRestoreState(savedInstanceState)
      textViewModel.restoreFromInstanceState(savedInstanceState)
    }

    (supportFragmentManager.findFragmentByTag(NAV_HOST_TAG) as NavHostFragment).navController.addOnDestinationChangedListener { _, d, _ ->
      when (d.id) {
        R.id.mediaCaptureFragment -> {
          textStoryToggle.visible = canDisplayStorySwitch()

          animateTextStyling(cameraSwitch, textSwitch, 200)
          TransitionManager.beginDelayedTransition(textStoryToggle, AutoTransition().setDuration(200))
          cameraSelectedConstraintSet.applyTo(textStoryToggle)
        }
        R.id.textStoryPostCreationFragment -> {
          textStoryToggle.visible = canDisplayStorySwitch()

          animateTextStyling(textSwitch, cameraSwitch, 200)
          TransitionManager.beginDelayedTransition(textStoryToggle, AutoTransition().setDuration(200))
          textSelectedConstraintSet.applyTo(textStoryToggle)
        }
        else -> textStoryToggle.visible = false
      }
    }

    onBackPressedDispatcher.addCallback(OnBackPressed())
  }

  private fun animateTextStyling(selectedSwitch: TextView, unselectedSwitch: TextView, duration: Long) {
    val offTextColor = ContextCompat.getColor(this, R.color.signal_colorOnSurface)
    val onTextColor = ContextCompat.getColor(this, R.color.signal_colorSecondaryContainer)

    animateInShadowLayerValueAnimator?.cancel()
    animateInTextColorValueAnimator?.cancel()
    animateOutShadowLayerValueAnimator?.cancel()
    animateOutTextColorValueAnimator?.cancel()

    animateInShadowLayerValueAnimator = ValueAnimator.ofFloat(selectedSwitch.shadowRadius, 0f).apply {
      this.duration = duration
      addUpdateListener { selectedSwitch.setShadowLayer(it.animatedValue as Float, 0f, 0f, Color.BLACK) }
      start()
    }
    animateInTextColorValueAnimator = ValueAnimator.ofObject(ArgbEvaluatorCompat(), selectedSwitch.currentTextColor, onTextColor).apply {
      setEvaluator(ArgbEvaluatorCompat.getInstance())
      this.duration = duration
      addUpdateListener { selectedSwitch.setTextColor(it.animatedValue as Int) }
      start()
    }
    animateOutShadowLayerValueAnimator = ValueAnimator.ofFloat(unselectedSwitch.shadowRadius, 3f).apply {
      this.duration = duration
      addUpdateListener { unselectedSwitch.setShadowLayer(it.animatedValue as Float, 0f, 0f, Color.BLACK) }
      start()
    }
    animateOutTextColorValueAnimator = ValueAnimator.ofObject(ArgbEvaluatorCompat(), unselectedSwitch.currentTextColor, offTextColor).apply {
      setEvaluator(ArgbEvaluatorCompat.getInstance())
      this.duration = duration
      addUpdateListener { unselectedSwitch.setTextColor(it.animatedValue as Int) }
      start()
    }
  }

  private fun initializeTextStory() {
    val message = draftText?.toString() ?: return
    val firstLink = LinkPreviewUtil.findValidPreviewUrls(message).findFirst()
    val firstLinkUrl = firstLink.map { it.url }.orElse(null)

    val iterator = BreakIteratorCompat.getInstance()
    iterator.setText(message)
    val trimmedMessage = iterator.take(700).toString()

    if (firstLinkUrl == message) {
      textViewModel.setLinkPreview(firstLinkUrl)
    } else if (firstLinkUrl != null) {
      textViewModel.setLinkPreview(firstLinkUrl)
      textViewModel.setBody(trimmedMessage.replace(firstLinkUrl, "").trim())
    } else {
      textViewModel.setBody(trimmedMessage.trim())
    }
  }

  private fun canDisplayStorySwitch(): Boolean {
    return Stories.isFeatureEnabled() &&
      isCameraFirst() &&
      !viewModel.hasSelectedMedia() &&
      (destination == MediaSelectionDestination.ChooseAfterMediaSelection || destination is MediaSelectionDestination.SingleStory)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    viewModel.onSaveState(outState)
    textViewModel.saveToInstanceState(outState)
  }

  override fun onSentWithResult(mediaSendActivityResult: MediaSendActivityResult) {
    setResult(
      RESULT_OK,
      Intent().apply {
        putExtra(MediaSendActivityResult.EXTRA_RESULT, mediaSendActivityResult)
      }
    )

    finish()
    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom)
  }

  override fun onSentWithoutResult() {
    val intent = Intent()
    setResult(RESULT_OK, intent)

    finish()
    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom)
  }

  override fun onSendError(error: Throwable) {
    if (error is UntrustedRecords.UntrustedRecordsException) {
      Log.w(TAG, "Send failed due to untrusted identities.")
      SafetyNumberBottomSheet
        .forIdentityRecordsAndDestinations(error.untrustedRecords, error.destinations.toList())
        .show(supportFragmentManager)
    } else {
      setResult(RESULT_CANCELED)

      // TODO [alex] - Toast
      Log.w(TAG, "Failed to send message.", error)

      finish()
      overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom)
    }
  }

  override fun onNoMediaSelected() {
    Log.w(TAG, "No media selected. Exiting.")

    setResult(RESULT_CANCELED)
    finish()
    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom)
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
    viewModel.sendCommand(HudCommand.OpenEmojiSearch)
  }

  override fun onEmojiSelected(emoji: String?) {
    viewModel.sendCommand(HudCommand.EmojiInsert(emoji))
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) {
    viewModel.sendCommand(HudCommand.EmojiKeyEvent(keyEvent))
  }

  override fun closeEmojiSearch() {
    viewModel.sendCommand(HudCommand.CloseEmojiSearch)
  }

  private inner class OnBackPressed : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      val navController = Navigation.findNavController(this@MediaSelectionActivity, R.id.fragment_container)

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
      media: List<Media>,
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
      isAddToGroupStoryFlow: Boolean = false
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
      }
    }
  }
}
