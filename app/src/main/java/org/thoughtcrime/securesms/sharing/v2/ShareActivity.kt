package org.thoughtcrime.securesms.sharing.v2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.Result
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFullScreenDialogFragment
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity.Companion.share
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.MultiShareDialogs
import org.thoughtcrime.securesms.sharing.MultiShareSender
import org.thoughtcrime.securesms.sharing.MultiShareSender.MultiShareSendResultCollection
import org.thoughtcrime.securesms.sharing.interstitial.ShareInterstitialActivity
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.LifecycleDisposable
import java.util.Optional

class ShareActivity : PassphraseRequiredActivity(), MultiselectForwardFragment.Callback {

  companion object {
    private val TAG = Log.tag(ShareActivity::class.java)
  }

  private val dynamicTheme = DynamicNoActionBarTheme()
  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var finishOnOkResultLauncher: ActivityResultLauncher<Intent>
  private lateinit var unresolvedShareData: UnresolvedShareData

  private val viewModel: ShareViewModel by viewModels {
    ShareViewModel.Factory(unresolvedShareData, ShareRepository(this))
  }

  private val directShareTarget: RecipientId?
    get() = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID).let { ConversationUtil.getRecipientId(it) }

  override fun onPreCreate() {
    super.onPreCreate()
    dynamicTheme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContentView(R.layout.share_activity_v2)

    val isIntentValid = getUnresolvedShareData().either(
      onSuccess = {
        unresolvedShareData = it
        true
      },
      onFailure = {
        handleIntentError(it)
        false
      }
    )

    if (!isIntentValid) {
      finish()
      return
    }

    finishOnOkResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (it.resultCode == Activity.RESULT_OK) {
        finish()
      }
    }

    lifecycleDisposable += viewModel.events.subscribe { shareEvent ->
      when (shareEvent) {
        is ShareEvent.OpenConversation -> openConversation(shareEvent)
        is ShareEvent.OpenMediaInterstitial -> openMediaInterstitial(shareEvent)
        is ShareEvent.OpenTextInterstitial -> openTextInterstitial(shareEvent)
        is ShareEvent.SendWithoutInterstitial -> sendWithoutInterstitial(shareEvent)
      }
    }

    lifecycleDisposable += viewModel.state.observeOn(AndroidSchedulers.mainThread()).subscribe { shareState ->
      when (shareState.loadState) {
        ShareState.ShareDataLoadState.Init -> Unit
        ShareState.ShareDataLoadState.Failed -> finish()
        is ShareState.ShareDataLoadState.Loaded -> {
          val directShareTarget = this.directShareTarget
          if (directShareTarget != null) {
            Log.d(TAG, "Encountered a direct share target. Opening conversation with resolved share data.")
            openConversation(
              ShareEvent.OpenConversation(
                shareState.loadState.resolvedShareData,
                ContactSearchKey.RecipientSearchKey(directShareTarget, false)
              )
            )
          } else {
            ensureFragment(shareState.loadState.resolvedShareData)
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  override fun onFinishForwardAction() = Unit

  override fun exitFlow() = Unit

  override fun onSearchInputFocused() = Unit

  override fun setResult(bundle: Bundle) {
    if (bundle.containsKey(MultiselectForwardFragment.RESULT_SENT)) {
      throw AssertionError("Should never happen.")
    }

    if (!bundle.containsKey(MultiselectForwardFragment.RESULT_SELECTION)) {
      throw AssertionError("Expected a recipient selection!")
    }

    val contactSearchKeys: List<ContactSearchKey.RecipientSearchKey> = bundle.getParcelableArrayList(MultiselectForwardFragment.RESULT_SELECTION)!!

    viewModel.onContactSelectionConfirmed(contactSearchKeys)
  }

  override fun getContainer(): ViewGroup = findViewById(R.id.container)

  override fun getDialogBackgroundColor(): Int = ContextCompat.getColor(this, R.color.signal_background_primary)

  private fun getUnresolvedShareData(): Result<UnresolvedShareData, IntentError> {
    return when {
      intent.action == Intent.ACTION_SEND_MULTIPLE && intent.hasExtra(Intent.EXTRA_TEXT) -> {
        intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.let { list ->
          val stringBuilder = SpannableStringBuilder()
          list.forEachIndexed { index, text ->
            stringBuilder.append(text)

            if (index != list.lastIndex) {
              stringBuilder.append("\n")
            }
          }

          Result.success(UnresolvedShareData.ExternalPrimitiveShare(stringBuilder))
        } ?: Result.failure(IntentError.SEND_MULTIPLE_TEXT)
      }
      intent.action == Intent.ACTION_SEND_MULTIPLE && intent.hasExtra(Intent.EXTRA_STREAM) -> {
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let {
          Result.success(UnresolvedShareData.ExternalMultiShare(it))
        } ?: Result.failure(IntentError.SEND_MULTIPLE_STREAM)
      }
      intent.action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_STREAM) -> {
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
          Result.success(UnresolvedShareData.ExternalSingleShare(it, intent.type))
        } ?: extractSingleExtraTextFromIntent(IntentError.SEND_STREAM)
      }
      intent.action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_TEXT) -> {
        extractSingleExtraTextFromIntent()
      }
      else -> null
    } ?: Result.failure(IntentError.UNKNOWN)
  }

  private fun extractSingleExtraTextFromIntent(fallbackError: IntentError = IntentError.UNKNOWN): Result<UnresolvedShareData, IntentError> {
    return if (intent.hasExtra(Intent.EXTRA_TEXT)) {
      intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let {
        Result.success(UnresolvedShareData.ExternalPrimitiveShare(it))
      } ?: Result.failure(IntentError.SEND_TEXT)
    } else {
      Result.failure(fallbackError)
    }
  }

  private fun ensureFragment(resolvedShareData: ResolvedShareData) {
    if (!supportFragmentManager.isStateSaved && supportFragmentManager.fragments.none { it is MultiselectForwardFullScreenDialogFragment }) {
      supportFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          MultiselectForwardFragment.create(
            MultiselectForwardFragmentArgs(
              canSendToNonPush = resolvedShareData.isMmsOrSmsSupported,
              multiShareArgs = listOf(resolvedShareData.toMultiShareArgs()),
              title = R.string.MultiselectForwardFragment__share_with,
              forceDisableAddMessage = true,
              forceSelectionOnly = true
            )
          )
        ).commitNow()
    }
  }

  private fun openConversation(shareEvent: ShareEvent.OpenConversation) {
    if (shareEvent.contact.isStory) {
      error("Can't open a conversation for a story!")
    }

    Log.d(TAG, "Opening conversation...")

    val multiShareArgs = shareEvent.getMultiShareArgs()
    val conversationIntentBuilder = ConversationIntents.createBuilder(this, shareEvent.contact.recipientId, -1L)
      .withDataUri(multiShareArgs.dataUri)
      .withDataType(multiShareArgs.dataType)
      .withMedia(multiShareArgs.media)
      .withDraftText(multiShareArgs.draftText)
      .withStickerLocator(multiShareArgs.stickerLocator)
      .asBorderless(multiShareArgs.isBorderless)
      .withShareDataTimestamp(System.currentTimeMillis())

    val mainActivityIntent = MainActivity.clearTop(this)
    finish()
    startActivities(arrayOf(mainActivityIntent, conversationIntentBuilder.build()))
  }

  private fun openMediaInterstitial(shareEvent: ShareEvent.OpenMediaInterstitial) {
    Log.d(TAG, "Opening media share interstitial...")

    val multiShareArgs = shareEvent.getMultiShareArgs()
    val media: MutableList<Media> = ArrayList(multiShareArgs.media)
    if (media.isEmpty() && multiShareArgs.dataUri != null) {
      media.add(
        Media(
          multiShareArgs.dataUri,
          multiShareArgs.dataType,
          0,
          0,
          0,
          0,
          0,
          false,
          false,
          Optional.empty(),
          Optional.empty(),
          Optional.empty()
        )
      )
    }

    val shareAsTextStory = multiShareArgs.allRecipientsAreStories() && media.isEmpty()

    val intent = share(
      this,
      MultiShareSender.getWorstTransportOption(this, multiShareArgs.recipientSearchKeys),
      media,
      multiShareArgs.recipientSearchKeys.toList(),
      multiShareArgs.draftText,
      shareAsTextStory
    )

    finishOnOkResultLauncher.launch(intent)
  }

  private fun openTextInterstitial(shareEvent: ShareEvent.OpenTextInterstitial) {
    Log.d(TAG, "Opening text share interstitial...")

    finishOnOkResultLauncher.launch(ShareInterstitialActivity.createIntent(this, shareEvent.getMultiShareArgs()))
  }

  private fun sendWithoutInterstitial(shareEvent: ShareEvent.SendWithoutInterstitial) {
    Log.d(TAG, "Sending without an interstitial...")

    MultiShareSender.send(shareEvent.getMultiShareArgs()) { results: MultiShareSendResultCollection? ->
      MultiShareDialogs.displayResultDialog(this, results!!) {
        finish()
      }
    }
  }

  private fun handleIntentError(intentError: IntentError) {
    val logEntry = when (intentError) {
      IntentError.SEND_MULTIPLE_TEXT -> "Failed to parse text array from intent for multi-share."
      IntentError.SEND_MULTIPLE_STREAM -> "Failed to parse stream array from intent for multi-share."
      IntentError.SEND_TEXT -> "Failed to parse text from intent for single-share."
      IntentError.SEND_STREAM -> "Failed to parse stream from intent for single-share."
      IntentError.UNKNOWN -> "Failed to parse unknown from intent."
    }

    Log.w(TAG, "$logEntry action: ${intent.action}, type: ${intent.type}")
    Toast.makeText(this, R.string.ShareActivity__could_not_get_share_data_from_intent, Toast.LENGTH_LONG).show()
  }

  /**
   * Represents an error with the intent when trying to extract the unresolved share data.
   */
  private enum class IntentError {
    SEND_MULTIPLE_TEXT,
    SEND_MULTIPLE_STREAM,
    SEND_TEXT,
    SEND_STREAM,
    UNKNOWN
  }
}
