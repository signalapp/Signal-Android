package org.thoughtcrime.securesms.conversation.v2

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.Window
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.lifecycleScope
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.launch
import org.signal.core.util.ConfigurationUtil
import org.signal.core.util.Debouncer
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayRepository
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsNavHostFragment
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.jobs.ConversationShortcutUpdateJob
import org.thoughtcrime.securesms.main.MainNavigationChatDetailRouter
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.messagedetails.MessageDetailsFragment
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import java.util.concurrent.TimeUnit

/**
 * Wrapper activity for ConversationFragment.
 */
open class ConversationActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner, GooglePayComponent, MainNavigationChatDetailRouter {

  companion object {
    private val TAG = tag(ConversationActivity::class.java)
    private const val MESSAGE_DETAILS_FRAGMENT_TAG = "MessageDetailsFragment"
  }

  private val theme = DynamicNoActionBarTheme()
  private val transitionDebouncer: Debouncer = Debouncer(150, TimeUnit.MILLISECONDS)

  override val voiceNoteMediaController = VoiceNoteMediaController(this, true)

  override val googlePayRepository: GooglePayRepository by lazy { GooglePayRepository(this) }
  override val googlePayResultPublisher: Subject<GooglePayComponent.GooglePayResult> = PublishSubject.create()

  private val motionEventRelay: MotionEventRelay by viewModels()
  private val shareDataTimestampViewModel: ShareDataTimestampViewModel by viewModels()

  override fun onPreCreate() {
    theme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    if (!ActivityCompat.isLaunchedFromBubble(this)) {
      startActivity(
        MainActivity.clearTop(this).apply {
          action = ConversationIntents.ACTION
          putExtras(intent)
        }
      )

      if (!ConversationIntents.isConversationIntent(intent)) {
        ConversationShortcutUpdateJob.enqueue()
      }

      finish()
      return
    }

    enableSavedStateHandles()
    supportPostponeEnterTransition()
    transitionDebouncer.publish { supportStartPostponedEnterTransition() }
    window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

    shareDataTimestampViewModel.setTimestampFromActivityCreation(savedInstanceState, intent)
    setContentView(R.layout.fragment_container)

    if (savedInstanceState == null) {
      replaceFragment()
    }
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
  }

  override fun onStop() {
    super.onStop()
    if (isChangingConfigurations) {
      Log.i(TAG, "Conversation recreating due to configuration change")
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    transitionDebouncer.clear()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    // Note: We utilize this instead of 'replaceFragment' because there seems to be a bug
    // in constraint-layout which mixes up insets when replacing the fragment via onNewIntent.
    finish()
    startActivity(intent)
  }

  @Suppress("DEPRECATION")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    googlePayResultPublisher.onNext(GooglePayComponent.GooglePayResult(requestCode, resultCode, data))
  }

  override fun onConfigurationChanged(newConfiguration: Configuration) {
    super.onConfigurationChanged(newConfiguration)
    if (ConfigurationUtil.isUiModeChanged(resources.configuration, newConfiguration)) {
      recreate()
    }
  }

  private fun replaceFragment() {
    val fragment = ConversationFragment().apply {
      arguments = if (ConversationIntents.isBubbleIntentUri(intent.data)) {
        ConversationIntents.createParentFragmentArguments(intent)
      } else {
        intent.extras
      }
    }

    supportFragmentManager
      .beginTransaction()
      .replace(R.id.fragment_container, fragment)
      .disallowAddToBackStack()
      .commitNowAllowingStateLoss()
  }

  override fun exitDetailLocation() {
    if (!supportFragmentManager.popBackStackImmediate()) {
      finish()
    }
  }

  override fun goToChatDetail(location: MainNavigationDetailLocation.Chats) {
    when (location) {
      is MainNavigationDetailLocation.Chats.ConversationSettings -> {
        lifecycleScope.launch {
          val args = ConversationSettingsNavHostFragment.createArgs(location.recipientId)
          supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, ConversationSettingsNavHostFragment::class.java, args)
            .addToBackStack(null)
            .commit()
        }
      }

      is MainNavigationDetailLocation.Chats.MessageDetails -> {
        MessageDetailsFragment.create(location.messageId, location.recipientId)
          .show(supportFragmentManager, MESSAGE_DETAILS_FRAGMENT_TAG)
      }
    }
  }

  override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
    return motionEventRelay.offer(ev) || super.dispatchTouchEvent(ev)
  }
}
