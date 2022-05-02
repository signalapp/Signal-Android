package org.thoughtcrime.securesms.sharing.v2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
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

  private val dynamicTheme = DynamicNoActionBarTheme()
  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var finishOnOkResultLauncher: ActivityResultLauncher<Intent>

  private val viewModel: ShareViewModel by viewModels {
    ShareViewModel.Factory(getUnresolvedShareData(), ShareRepository(this))
  }

  private val directShareTarget: RecipientId?
    get() = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID).let { ConversationUtil.getRecipientId(it) }

  override fun onPreCreate() {
    super.onPreCreate()
    dynamicTheme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContentView(R.layout.share_activity_v2)

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
            openConversation(
              ShareEvent.OpenConversation(
                shareState.loadState.resolvedShareData,
                ContactSearchKey.RecipientSearchKey.KnownRecipient(directShareTarget)
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

    val parcelizedKeys: List<ContactSearchKey.ParcelableRecipientSearchKey> = bundle.getParcelableArrayList(MultiselectForwardFragment.RESULT_SELECTION)!!
    val contactSearchKeys = parcelizedKeys.map { it.asRecipientSearchKey() }

    viewModel.onContactSelectionConfirmed(contactSearchKeys)
  }

  override fun getContainer(): ViewGroup = findViewById(R.id.container)

  override fun getDialogBackgroundColor(): Int = ContextCompat.getColor(this, R.color.signal_background_primary)

  private fun getUnresolvedShareData(): UnresolvedShareData {
    return when {
      intent.action == Intent.ACTION_SEND_MULTIPLE -> {
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let {
          UnresolvedShareData.ExternalMultiShare(it)
        } ?: error("ACTION_SEND_MULTIPLE with EXTRA_STREAM but the EXTRA_STREAM was null")
      }
      intent.action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_STREAM) -> {
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
          UnresolvedShareData.ExternalSingleShare(it, intent.type)
        } ?: error("ACTION_SEND with EXTRA_STREAM but the EXTRA_STREAM was null")
      }
      intent.action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_TEXT) -> {
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let {
          UnresolvedShareData.ExternalPrimitiveShare(it)
        } ?: error("ACTION_SEND with EXTRA_TEXT but the EXTRA_TEXT was null")
      }
      else -> null
    } ?: error("Intent Action: ${Intent.ACTION_SEND_MULTIPLE} could not be resolved with the given arguments.")
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

    val multiShareArgs = shareEvent.getMultiShareArgs()
    val conversationIntentBuilder = ConversationIntents.createBuilder(this, shareEvent.contact.recipientId, -1L)
      .withDataUri(multiShareArgs.dataUri)
      .withDataType(multiShareArgs.dataType)
      .withMedia(multiShareArgs.media)
      .withDraftText(multiShareArgs.draftText)
      .withStickerLocator(multiShareArgs.stickerLocator)
      .asBorderless(multiShareArgs.isBorderless)

    finish()
    startActivity(conversationIntentBuilder.build())
  }

  private fun openMediaInterstitial(shareEvent: ShareEvent.OpenMediaInterstitial) {
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
    finishOnOkResultLauncher.launch(ShareInterstitialActivity.createIntent(this, shareEvent.getMultiShareArgs()))
  }

  private fun sendWithoutInterstitial(shareEvent: ShareEvent.SendWithoutInterstitial) {
    MultiShareSender.send(shareEvent.getMultiShareArgs()) { results: MultiShareSendResultCollection? ->
      MultiShareDialogs.displayResultDialog(this, results!!) {
        finish()
      }
    }
  }
}
