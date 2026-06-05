package org.thoughtcrime.securesms.starred

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.components.recyclerview.SmoothScrollingLinearLayoutManager
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.colors.ColorizerV1
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ItemDecoration
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicy
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.StickyHeaderDecoration
import org.thoughtcrime.securesms.util.viewModel
import java.util.Locale

class StarredMessagesActivity : PassphraseRequiredActivity() {

  companion object {
    private const val EXTRA_THREAD_ID = "thread_id"
    const val NO_THREAD_ID = -1L

    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, StarredMessagesActivity::class.java)
    }

    @JvmStatic
    fun createIntent(context: Context, threadId: Long): Intent {
      return Intent(context, StarredMessagesActivity::class.java).apply {
        putExtra(EXTRA_THREAD_ID, threadId)
      }
    }
  }

  private val viewModel by viewModel {
    val threadId = intent.getLongExtra(EXTRA_THREAD_ID, NO_THREAD_ID)
    val effectiveThreadId = if (threadId == NO_THREAD_ID) null else threadId
    StarredMessagesViewModel(effectiveThreadId)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContent {
      SignalTheme {
        StarredMessagesScreen(
          viewModel = viewModel,
          onNavigateBack = { supportFinishAfterTransition() },
          onNavigateToMessage = ::navigateToMessage
        )
      }
    }
  }

  private fun navigateToMessage(messageRecord: MessageRecord) {
    lifecycleScope.launch {
      val (threadRecipient, startingPosition) = withContext(Dispatchers.IO) {
        val position = SignalDatabase.messages.getMessagePositionInConversation(messageRecord.threadId, messageRecord.dateReceived)
        val recipient = SignalDatabase.threads.getRecipientForThreadId(messageRecord.threadId)
        Pair(recipient, maxOf(0, position))
      }
      if (threadRecipient != null) {
        val intent = ConversationIntents.createBuilderSync(this@StarredMessagesActivity, threadRecipient.id, messageRecord.threadId)
          .withStartingPosition(startingPosition)
          .build()
        startActivity(intent)
        finish()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StarredMessagesScreen(
  viewModel: StarredMessagesViewModel,
  onNavigateBack: () -> Unit,
  onNavigateToMessage: (MessageRecord) -> Unit
) {
  val messages by viewModel.getMessages().collectAsStateWithLifecycle(initialValue = null)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  Scaffold(
    topBar = {
      Scaffolds.DefaultTopAppBar(
        title = stringResource(R.string.StarredMessagesActivity__starred_messages),
        titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = SignalIcons.ArrowStart.imageVector,
        navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
        onNavigationClick = onNavigateBack,
        scrollBehavior = scrollBehavior
      )
    },
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { padding ->
    Box(
      modifier = Modifier
        .padding(padding)
        .fillMaxSize()
    ) {
      when {
        messages == null -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator()
          }
        }

        messages.isNullOrEmpty() -> {
          EmptyState(modifier = Modifier.fillMaxSize())
        }

        else -> {
          StarredMessageList(
            messages = messages!!,
            onItemClick = onNavigateToMessage,
            onQuoteClick = onNavigateToMessage,
            onUnstarMessage = { messageId ->
              scope.launch {
                try {
                  viewModel.unstarMessage(messageId)
                } catch (e: Exception) {
                  Toast.makeText(context, "Failed to unstar message", Toast.LENGTH_SHORT).show()
                }
              }
            },
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }
}

@SuppressLint("WrongThread")
@Composable
private fun StarredMessageList(
  messages: List<ConversationMessage>,
  onItemClick: (MessageRecord) -> Unit,
  onQuoteClick: (MessageRecord) -> Unit,
  onUnstarMessage: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val onItemClickState = rememberUpdatedState(onItemClick)
  val onQuoteClickState = rememberUpdatedState(onQuoteClick)
  val onUnstarMessageState = rememberUpdatedState(onUnstarMessage)

  val adapter = remember {
    @Suppress("DEPRECATION")
    val colorizer = ColorizerV1()
    ConversationAdapter(
      context,
      lifecycleOwner,
      Glide.with(context),
      Locale.getDefault(),
      StarredMessageClickListener(
        onItemClick = { onItemClickState.value(it) },
        onQuoteClick = { onQuoteClickState.value(it) },
        onUnstarMessage = { onUnstarMessageState.value(it) },
        context = context
      ),
      false,
      colorizer
    ).apply {
      setCondensedMode(ConversationItemDisplayMode.Starred)
    }
  }

  AndroidView(
    factory = { ctx ->
      FrameLayout(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )

        val videoContainer = FrameLayout(ctx).apply {
          layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
        }
        addView(videoContainer)

        val recyclerView = RecyclerView(ctx).apply {
          layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          clipToPadding = false
          setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
          layoutManager = SmoothScrollingLinearLayoutManager(ctx, true)
          this.adapter = adapter
          itemAnimator = null
          doOnNextLayout {
            addItemDecoration(StickyHeaderDecoration(adapter, false, false, ConversationAdapter.HEADER_TYPE_INLINE_DATE))
          }
        }
        addView(recyclerView)

        initializeGiphyMp4(lifecycleOwner.lifecycle, videoContainer, recyclerView)
      }
    },
    update = {
      adapter.submitList(messages)
    },
    modifier = modifier
  )
}

private fun initializeGiphyMp4(lifecycle: Lifecycle, videoContainer: ViewGroup, list: RecyclerView) {
  val context = list.context
  val maxPlayback = GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInConversation()
  val holders = GiphyMp4ProjectionPlayerHolder.injectVideoViews(context, lifecycle, videoContainer, maxPlayback)
  val callback = GiphyMp4ProjectionRecycler(holders)

  GiphyMp4PlaybackController.attach(list, callback, maxPlayback)
  list.addItemDecoration(GiphyMp4ItemDecoration(callback), 0)
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      painter = painterResource(R.drawable.symbol_star_24),
      contentDescription = null,
      modifier = Modifier
        .size(48.dp)
        .alpha(0.5f),
      tint = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = stringResource(R.string.StarredMessagesFragment__no_starred_messages),
      style = MaterialTheme.typography.headlineMedium
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.StarredMessagesFragment__tap_and_hold_on_a_message_to_star_it),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.alpha(0.7f)
    )
  }
}

private class StarredMessageClickListener(
  private val onItemClick: (MessageRecord) -> Unit,
  private val onQuoteClick: (MessageRecord) -> Unit,
  private val onUnstarMessage: (Long) -> Unit,
  private val context: Context
) : ConversationAdapter.ItemClickListener {

  override fun onItemClick(item: MultiselectPart) {
    onItemClick(item.getMessageRecord())
  }

  override fun onItemLongClick(itemView: View, item: MultiselectPart) {
    val messageRecord = item.getMessageRecord()
    val items = mutableListOf<ActionItem>()

    items.add(
      ActionItem(R.drawable.symbol_star_outline_24, context.getString(R.string.conversation_selection__menu_unstar)) {
        onUnstarMessage(messageRecord.id)
      }
    )

    SignalContextMenu.Builder(itemView, itemView.rootView as ViewGroup)
      .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.START)
      .show(items)
  }

  override fun onQuoteClicked(messageRecord: MmsMessageRecord) {
    onQuoteClick(messageRecord)
  }

  override fun onLinkPreviewClicked(linkPreview: LinkPreview) = Unit
  override fun onQuotedIndicatorClicked(messageRecord: MessageRecord) = Unit
  override fun onMoreTextClicked(conversationRecipientId: RecipientId, messageId: Long, isMms: Boolean) = Unit
  override fun onStickerClicked(stickerLocator: StickerLocator) = Unit
  override fun onViewOnceMessageClicked(messageRecord: MmsMessageRecord) = Unit
  override fun onSharedContactDetailsClicked(contact: Contact, avatarTransitionView: View) = Unit
  override fun onAddToContactsClicked(contact: Contact) = Unit
  override fun onMessageSharedContactClicked(choices: MutableList<Recipient>) = Unit
  override fun onInviteSharedContactClicked(choices: MutableList<Recipient>) = Unit
  override fun onReactionClicked(multiselectPart: MultiselectPart, messageId: Long, isMms: Boolean) = Unit
  override fun onGroupMemberClicked(recipientId: RecipientId, groupId: GroupId) = Unit
  override fun onMessageWithErrorClicked(messageRecord: MessageRecord) = Unit
  override fun onMessageWithRecaptchaNeededClicked(messageRecord: MessageRecord) = Unit
  override fun onGroupMigrationLearnMoreClicked(membershipChange: GroupMigrationMembershipChange) = Unit
  override fun onChatSessionRefreshLearnMoreClicked() = Unit
  override fun onBadDecryptLearnMoreClicked(author: RecipientId) = Unit
  override fun onSafetyNumberLearnMoreClicked(recipient: Recipient) = Unit
  override fun onJoinGroupCallClicked() = Unit
  override fun onInviteFriendsToGroupClicked(groupId: GroupId.V2) = Unit
  override fun onEnableCallNotificationsClicked() = Unit
  override fun onCallToAction(action: String) = Unit
  override fun onDonateClicked() = Unit
  override fun onRecipientNameClicked(target: RecipientId) = Unit
  override fun onViewGiftBadgeClicked(messageRecord: MessageRecord) = Unit
  override fun onActivatePaymentsClicked() = Unit
  override fun onSendPaymentClicked(recipientId: RecipientId) = Unit
  override fun onEditedIndicatorClicked(conversationMessage: ConversationMessage) = Unit
  override fun onShowSafetyTips(forGroup: Boolean) = Unit
  override fun onReportSpamLearnMoreClicked() = Unit
  override fun onMessageRequestAcceptOptionsClicked() = Unit
  override fun onItemDoubleClick(item: MultiselectPart) = Unit
  override fun onToggleVote(poll: PollRecord, pollOption: PollOption, isChecked: Boolean?) = Unit
  override fun onViewResultsClicked(pollId: Long) = Unit
  override fun onIncomingIdentityMismatchClicked(recipientId: RecipientId) = Unit
  override fun onRegisterVoiceNoteCallbacks(onPlaybackStartObserver: Observer<VoiceNotePlaybackState>) = Unit
  override fun onUnregisterVoiceNoteCallbacks(onPlaybackStartObserver: Observer<VoiceNotePlaybackState>) = Unit
  override fun onVoiceNotePause(uri: Uri) = Unit
  override fun onVoiceNotePlay(uri: Uri, messageId: Long, position: Double) = Unit
  override fun onVoiceNoteSeekTo(uri: Uri, position: Double) = Unit
  override fun onVoiceNotePlaybackSpeedChanged(uri: Uri, speed: Float) = Unit
  override fun onPlayInlineContent(conversationMessage: ConversationMessage?) = Unit
  override fun onInMemoryMessageClicked(messageRecord: InMemoryMessageRecord) = Unit
  override fun onViewGroupDescriptionChange(groupId: GroupId?, description: String, isMessageRequestAccepted: Boolean) = Unit
  override fun onChangeNumberUpdateContact(recipient: Recipient) = Unit
  override fun onChangeProfileNameUpdateContact(recipient: Recipient) = Unit
  override fun onBlockJoinRequest(recipient: Recipient) = Unit
  override fun onInviteToSignalClicked() = Unit
  override fun onScheduledIndicatorClicked(view: View, conversationMessage: ConversationMessage) = Unit
  override fun onUrlClicked(url: String): Boolean = false
  override fun onGiftBadgeRevealed(messageRecord: MessageRecord) = Unit
  override fun goToMediaPreview(parent: ConversationItem, sharedElement: View, args: MediaIntentFactory.MediaPreviewArgs) = Unit
  override fun onShowGroupDescriptionClicked(groupName: String, description: String, shouldLinkifyWebLinks: Boolean) = Unit
  override fun onJoinCallLink(callLinkRootKey: CallLinkRootKey) = Unit
  override fun onPaymentTombstoneClicked() = Unit
  override fun onDisplayMediaNoLongerAvailableSheet() = Unit
  override fun onShowUnverifiedProfileSheet(forGroup: Boolean) = Unit
  override fun onUpdateSignalClicked() = Unit
  override fun onViewPollClicked(messageId: Long) = Unit
  override fun onViewPinnedMessage(messageId: Long) = Unit
  override fun onCollapseEvents(messageId: Long, itemView: View, collapsedSize: Int) = Unit
  override fun onExpandEvents(messageId: Long, itemView: View, collapsedSize: Int) = Unit
}
