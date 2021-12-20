package org.thoughtcrime.securesms.mediasend.v2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.TransportOption
import org.thoughtcrime.securesms.TransportOptions
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.mediasend.v2.review.MediaReviewFragment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class MediaSelectionActivity :
  PassphraseRequiredActivity(),
  MediaReviewFragment.Callback,
  EmojiKeyboardPageFragment.Callback,
  EmojiEventListener,
  EmojiSearchFragment.Callback {

  lateinit var viewModel: MediaSelectionViewModel

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContentView(R.layout.fragment_container)

    val transportOption: TransportOption = requireNotNull(intent.getParcelableExtra(TRANSPORT_OPTION))
    val initialMedia: List<Media> = intent.getParcelableArrayListExtra(MEDIA) ?: listOf()
    val destination: MediaSelectionDestination = MediaSelectionDestination.fromBundle(requireNotNull(intent.getBundleExtra(DESTINATION)))
    val message: CharSequence? = intent.getCharSequenceExtra(MESSAGE)
    val isReply: Boolean = intent.getBooleanExtra(IS_REPLY, false)

    val factory = MediaSelectionViewModel.Factory(destination, transportOption, initialMedia, message, isReply, MediaSelectionRepository(this))
    viewModel = ViewModelProvider(this, factory)[MediaSelectionViewModel::class.java]

    if (savedInstanceState == null) {
      val navHostFragment = NavHostFragment.create(R.navigation.media)

      supportFragmentManager
        .beginTransaction()
        .replace(R.id.fragment_container, navHostFragment, NAV_HOST_TAG)
        .commitNowAllowingStateLoss()

      navigateToStartDestination()
    } else {
      viewModel.onRestoreState(savedInstanceState)
    }

    onBackPressedDispatcher.addCallback(OnBackPressed())
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
    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom)
  }

  override fun onSentWithoutResult() {
    val intent = Intent()
    setResult(RESULT_OK, intent)

    finish()
    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom)
  }

  override fun onSendError(error: Throwable) {
    setResult(RESULT_CANCELED)

    // TODO [alex] - Toast
    Log.w(TAG, "Failed to send message.", error)

    finish()
    overridePendingTransition(R.anim.stationary, R.anim.camera_slide_to_bottom)
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
      if (!navController.popBackStack()) {
        finish()
      }
    }
  }

  companion object {
    private val TAG = Log.tag(MediaSelectionActivity::class.java)

    private const val NAV_HOST_TAG = "NAV_HOST"

    private const val START_ACTION = "start.action"
    private const val TRANSPORT_OPTION = "transport.option"
    private const val MEDIA = "media"
    private const val MESSAGE = "message"
    private const val DESTINATION = "destination"
    private const val IS_REPLY = "is_reply"

    @JvmStatic
    fun camera(context: Context): Intent {
      return buildIntent(
        context = context,
        startAction = R.id.action_directly_to_mediaCaptureFragment
      )
    }

    @JvmStatic
    fun camera(
      context: Context,
      transportOption: TransportOption,
      recipientId: RecipientId,
      isReply: Boolean
    ): Intent {
      return buildIntent(
        context = context,
        startAction = R.id.action_directly_to_mediaCaptureFragment,
        transportOption = transportOption,
        destination = MediaSelectionDestination.SingleRecipient(recipientId),
        isReply = isReply
      )
    }

    @JvmStatic
    fun gallery(
      context: Context,
      transportOption: TransportOption,
      media: List<Media>,
      recipientId: RecipientId,
      message: CharSequence?,
      isReply: Boolean
    ): Intent {
      return buildIntent(
        context = context,
        startAction = R.id.action_directly_to_mediaGalleryFragment,
        transportOption = transportOption,
        media = media,
        destination = MediaSelectionDestination.SingleRecipient(recipientId),
        message = message,
        isReply = isReply
      )
    }

    @JvmStatic
    fun editor(
      context: Context,
      transportOption: TransportOption,
      media: List<Media>,
      recipientId: RecipientId,
      message: CharSequence?
    ): Intent {
      return buildIntent(
        context = context,
        transportOption = transportOption,
        media = media,
        destination = MediaSelectionDestination.SingleRecipient(recipientId),
        message = message
      )
    }

    @JvmStatic
    fun share(
      context: Context,
      transportOption: TransportOption,
      media: List<Media>,
      recipientIds: List<RecipientId>,
      message: CharSequence?
    ): Intent {
      return buildIntent(
        context = context,
        transportOption = transportOption,
        media = media,
        destination = MediaSelectionDestination.MultipleRecipients(recipientIds),
        message = message
      )
    }

    private fun buildIntent(
      context: Context,
      startAction: Int = -1,
      transportOption: TransportOption = TransportOptions.getPushTransportOption(context),
      media: List<Media> = listOf(),
      destination: MediaSelectionDestination = MediaSelectionDestination.ChooseAfterMediaSelection,
      message: CharSequence? = null,
      isReply: Boolean = false
    ): Intent {
      return Intent(context, MediaSelectionActivity::class.java).apply {
        putExtra(START_ACTION, startAction)
        putExtra(TRANSPORT_OPTION, transportOption)
        putParcelableArrayListExtra(MEDIA, ArrayList(media))
        putExtra(MESSAGE, message)
        putExtra(DESTINATION, destination.toBundle())
        putExtra(IS_REPLY, isReply)
      }
    }
  }
}
