package org.thoughtcrime.securesms.conversation.v2

import android.Manifest
import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.DimenRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.annimon.stream.Stream
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityConversationV2ActionBarBinding
import network.loki.messenger.databinding.ActivityConversationV2Binding
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.mentions.Mention
import org.session.libsession.messaging.mentions.MentionsManager
import org.session.libsession.messaging.messages.control.DataExtractionNotification
import org.session.libsession.messaging.messages.signal.OutgoingMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.MediaTypes
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.concurrent.SimpleTask
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.recipients.RecipientModifiedListener
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.ListenableFuture
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.utilities.hexEncodedPrivateKey
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.audio.AudioRecorder
import org.thoughtcrime.securesms.contacts.SelectContactsActivity.Companion.selectedContactsKey
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher
import org.thoughtcrime.securesms.conversation.v2.dialogs.BlockedDialog
import org.thoughtcrime.securesms.conversation.v2.dialogs.LinkPreviewDialog
import org.thoughtcrime.securesms.conversation.v2.dialogs.SendSeedDialog
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarButton
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarRecordingViewDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.mentions.MentionCandidatesView
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationActionModeCallback
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationActionModeCallbackDelegate
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationMenuHelper
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageContentViewDelegate
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageView
import org.thoughtcrime.securesms.conversation.v2.messages.VoiceMessageViewDelegate
import org.thoughtcrime.securesms.conversation.v2.search.SearchBottomBar
import org.thoughtcrime.securesms.conversation.v2.search.SearchViewModel
import org.thoughtcrime.securesms.conversation.v2.utilities.AttachmentManager
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionManagerUtilities
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities
import org.thoughtcrime.securesms.conversation.v2.utilities.ResendMessageUtilities
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.LokiAPIDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.giph.ui.GiphyActivity
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel.LinkPreviewState
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivity
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.mms.GifSlide
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.util.ActivityDispatcher
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.SaveAttachmentTask
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.toPx
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Some things that seemingly belong to the input bar (e.g. the voice message recording UI) are actually
// part of the conversation activity layout. This is just because it makes the layout a lot simpler. The
// price we pay is a bit of back and forth between the input bar and the conversation activity.
@AndroidEntryPoint
class ConversationActivityV2 : PassphraseRequiredActionBarActivity(), InputBarDelegate,
        InputBarRecordingViewDelegate, AttachmentManager.AttachmentListener, ActivityDispatcher,
        ConversationActionModeCallbackDelegate, VisibleMessageContentViewDelegate, RecipientModifiedListener,
        SearchBottomBar.EventListener, VoiceMessageViewDelegate {

    private lateinit var binding: ActivityConversationV2Binding
    private lateinit var actionBarBinding: ActivityConversationV2ActionBarBinding

    @Inject lateinit var textSecurePreferences: TextSecurePreferences
    @Inject lateinit var threadDb: ThreadDatabase
    @Inject lateinit var mmsSmsDb: MmsSmsDatabase
    @Inject lateinit var lokiThreadDb: LokiThreadDatabase
    @Inject lateinit var sessionContactDb: SessionContactDatabase
    @Inject lateinit var groupDb: GroupDatabase
    @Inject lateinit var lokiApiDb: LokiAPIDatabase
    @Inject lateinit var smsDb: SmsDatabase
    @Inject lateinit var mmsDb: MmsDatabase
    @Inject lateinit var lokiMessageDb: LokiMessageDatabase
    @Inject lateinit var viewModelFactory: ConversationViewModel.AssistedFactory

    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private val linkPreviewViewModel: LinkPreviewViewModel by lazy {
        ViewModelProvider(this, LinkPreviewViewModel.Factory(LinkPreviewRepository(this)))
            .get(LinkPreviewViewModel::class.java)
    }
    private val viewModel: ConversationViewModel by viewModels {
        var threadId = intent.getLongExtra(THREAD_ID, -1L)
        if (threadId == -1L) {
            intent.getParcelableExtra<Address>(ADDRESS)?.let { address ->
                val recipient = Recipient.from(this, address, false)
                threadId = threadDb.getOrCreateThreadIdFor(recipient)
            } ?: finish()
        }
        viewModelFactory.create(threadId)
    }
    private var actionMode: ActionMode? = null
    private var unreadCount = 0
    // Attachments
    private val audioRecorder = AudioRecorder(this)
    private val stopAudioHandler = Handler(Looper.getMainLooper())
    private val stopVoiceMessageRecordingTask = Runnable { sendVoiceMessage() }
    private val attachmentManager by lazy { AttachmentManager(this, this) }
    private var isLockViewExpanded = false
    private var isShowingAttachmentOptions = false
    // Mentions
    private val mentions = mutableListOf<Mention>()
    private var mentionCandidatesView: MentionCandidatesView? = null
    private var previousText: CharSequence = ""
    private var currentMentionStartIndex = -1
    private var isShowingMentionCandidatesView = false
    // Search
    val searchViewModel: SearchViewModel by viewModels()
    var searchViewItem: MenuItem? = null

    private val isScrolledToBottom: Boolean
        get() {
            val position = layoutManager.findFirstCompletelyVisibleItemPosition()
            return position == 0
        }

    private val layoutManager: LinearLayoutManager
        get() { return binding.conversationRecyclerView.layoutManager as LinearLayoutManager }

    private val seed by lazy {
        var hexEncodedSeed = IdentityKeyUtil.retrieve(this, IdentityKeyUtil.LOKI_SEED)
        if (hexEncodedSeed == null) {
            hexEncodedSeed = IdentityKeyUtil.getIdentityKeyPair(this).hexEncodedPrivateKey // Legacy account
        }
        val loadFileContents: (String) -> String = { fileName ->
            MnemonicUtilities.loadFileContents(this, fileName)
        }
        MnemonicCodec(loadFileContents).encode(hexEncodedSeed!!, MnemonicCodec.Language.Configuration.english)
    }

    private val adapter by lazy {
        val cursor = mmsSmsDb.getConversation(viewModel.threadId)
        val adapter = ConversationAdapter(
            this,
            cursor,
            onItemPress = { message, position, view, event ->
                handlePress(message, position, view, event)
            },
            onItemSwipeToReply = { message, position ->
                handleSwipeToReply(message, position)
            },
            onItemLongPress = { message, position ->
                handleLongPress(message, position)
            },
            glide,
            onDeselect = { message, position ->
                actionMode?.let {
                    onDeselect(message, position, it)
                }
            }
        )
        adapter.visibleMessageContentViewDelegate = this
        adapter
    }

    private val glide by lazy { GlideApp.with(this) }
    private val lockViewHitMargin by lazy { toPx(40, resources) }
    private val gifButton by lazy { InputBarButton(this, R.drawable.ic_gif_white_24dp, hasOpaqueBackground = true, isGIFButton = true) }
    private val documentButton by lazy { InputBarButton(this, R.drawable.ic_document_small_dark, hasOpaqueBackground = true) }
    private val libraryButton by lazy { InputBarButton(this, R.drawable.ic_baseline_photo_library_24, hasOpaqueBackground = true) }
    private val cameraButton by lazy { InputBarButton(this, R.drawable.ic_baseline_photo_camera_24, hasOpaqueBackground = true) }
    private val messageToScrollTimestamp = AtomicLong(-1)
    private val messageToScrollAuthor = AtomicReference<Address?>(null)

    // region Settings
    companion object {
        // Extras
        const val THREAD_ID = "thread_id"
        const val ADDRESS = "address"
        const val SCROLL_MESSAGE_ID = "scroll_message_id"
        const val SCROLL_MESSAGE_AUTHOR = "scroll_message_author"
        // Request codes
        const val PICK_DOCUMENT = 2
        const val TAKE_PHOTO = 7
        const val PICK_GIF = 10
        const val PICK_FROM_LIBRARY = 12
        const val INVITE_CONTACTS = 124

        //flag
        const val IS_UNSEND_REQUESTS_ENABLED = true
    }
    // endregion

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivityConversationV2Binding.inflate(layoutInflater)
        setContentView(binding.root)
        // messageIdToScroll
        messageToScrollTimestamp.set(intent.getLongExtra(SCROLL_MESSAGE_ID, -1))
        messageToScrollAuthor.set(intent.getParcelableExtra(SCROLL_MESSAGE_AUTHOR))
        val thread = threadDb.getRecipientForThreadId(viewModel.threadId)
        if (thread == null) {
            Toast.makeText(this, "This thread has been deleted.", Toast.LENGTH_LONG).show()
            return finish()
        }
        setUpRecyclerView()
        setUpToolBar()
        setUpInputBar()
        setUpLinkPreviewObserver()
        restoreDraftIfNeeded()
        setUpUiStateObserver()
        binding.scrollToBottomButton.setOnClickListener {
            val layoutManager = binding.conversationRecyclerView.layoutManager ?: return@setOnClickListener
            if (layoutManager.isSmoothScrolling) {
                binding.conversationRecyclerView.scrollToPosition(0)
            } else {
                binding.conversationRecyclerView.smoothScrollToPosition(0)
            }
        }
        unreadCount = mmsSmsDb.getUnreadCount(viewModel.threadId)
        updateUnreadCountIndicator()
        setUpTypingObserver()
        setUpRecipientObserver()
        updateSubtitle()
        getLatestOpenGroupInfoIfNeeded()
        setUpBlockedBanner()
        binding.searchBottomBar.setEventListener(this)
        setUpSearchResultObserver()
        scrollToFirstUnreadMessageIfNeeded()
        showOrHideInputIfNeeded()
        if (viewModel.recipient.isOpenGroupRecipient) {
            val openGroup = lokiThreadDb.getOpenGroupChat(viewModel.threadId)
            if (openGroup == null) {
                Toast.makeText(this, "This thread has been deleted.", Toast.LENGTH_LONG).show()
                return finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ApplicationContext.getInstance(this).messageNotifier.setVisibleThread(viewModel.threadId)
        threadDb.markAllAsRead(viewModel.threadId, viewModel.recipient.isOpenGroupRecipient)
    }

    override fun onPause() {
        super.onPause()
        ApplicationContext.getInstance(this).messageNotifier.setVisibleThread(-1)
    }

    override fun getSystemService(name: String): Any? {
        if (name == ActivityDispatcher.SERVICE) {
            return this
        }
        return super.getSystemService(name)
    }

    override fun dispatchIntent(body: (Context) -> Intent?) {
        val intent = body(this) ?: return
        push(intent, false)
    }

    override fun showDialog(baseDialog: BaseDialog, tag: String?) {
        baseDialog.show(supportFragmentManager, tag)
    }

    private fun setUpRecyclerView() {
        binding.conversationRecyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, true)
        binding.conversationRecyclerView.layoutManager = layoutManager
        // Workaround for the fact that CursorRecyclerViewAdapter doesn't auto-update automatically (even though it says it will)
        LoaderManager.getInstance(this).restartLoader(0, null, object : LoaderManager.LoaderCallbacks<Cursor> {

            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<Cursor> {
                return ConversationLoader(viewModel.threadId, this@ConversationActivityV2)
            }

            override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
                adapter.changeCursor(cursor)
                if (cursor != null) {
                    val messageTimestamp = messageToScrollTimestamp.getAndSet(-1)
                    val author = messageToScrollAuthor.getAndSet(null)
                    if (author != null && messageTimestamp >= 0) {
                        jumpToMessage(author, messageTimestamp, null)
                    }
                }
            }

            override fun onLoaderReset(cursor: Loader<Cursor>) {
                adapter.changeCursor(null)
            }
        })
        binding.conversationRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                handleRecyclerViewScrolled()
            }
        })
    }

    private fun setUpToolBar() {
        val actionBar = supportActionBar!!
        actionBarBinding = ActivityConversationV2ActionBarBinding.inflate(layoutInflater)
        actionBar.title = ""
        actionBar.customView = actionBarBinding.root
        actionBar.setDisplayShowCustomEnabled(true)
        actionBarBinding.conversationTitleView.text = viewModel.recipient.toShortString()
        @DimenRes val sizeID: Int = if (viewModel.recipient.isClosedGroupRecipient) {
            R.dimen.medium_profile_picture_size
        } else {
            R.dimen.small_profile_picture_size
        }
        val size = resources.getDimension(sizeID).roundToInt()
        actionBarBinding.profilePictureView.layoutParams = LinearLayout.LayoutParams(size, size)
        actionBarBinding.profilePictureView.glide = glide
        MentionManagerUtilities.populateUserPublicKeyCacheIfNeeded(viewModel.threadId, this)
        actionBarBinding.profilePictureView.update(viewModel.recipient)
    }

    private fun setUpInputBar() {
        binding.inputBar.delegate = this
        binding.inputBarRecordingView.delegate = this
        // GIF button
        binding.gifButtonContainer.addView(gifButton)
        gifButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        gifButton.onUp = { showGIFPicker() }
        gifButton.snIsEnabled = false
        // Document button
        binding.documentButtonContainer.addView(documentButton)
        documentButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        documentButton.onUp = { showDocumentPicker() }
        documentButton.snIsEnabled = false
        // Library button
        binding.libraryButtonContainer.addView(libraryButton)
        libraryButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        libraryButton.onUp = { pickFromLibrary() }
        libraryButton.snIsEnabled = false
        // Camera button
        binding.cameraButtonContainer.addView(cameraButton)
        cameraButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        cameraButton.onUp = { showCamera() }
        cameraButton.snIsEnabled = false
    }

    private fun restoreDraftIfNeeded() {
        val mediaURI = intent.data
        val mediaType = AttachmentManager.MediaType.from(intent.type)
        if (mediaURI != null && mediaType != null) {
            if (AttachmentManager.MediaType.IMAGE == mediaType || AttachmentManager.MediaType.GIF == mediaType || AttachmentManager.MediaType.VIDEO == mediaType) {
                val media = Media(mediaURI, MediaUtil.getMimeType(this, mediaURI)!!, 0, 0, 0, 0, Optional.absent(), Optional.absent())
                startActivityForResult(MediaSendActivity.buildEditorIntent(this, listOf( media ), viewModel.recipient, ""), PICK_FROM_LIBRARY)
                return
            } else {
                prepMediaForSending(mediaURI, mediaType).addListener(object : ListenableFuture.Listener<Boolean> {

                    override fun onSuccess(result: Boolean?) {
                        sendAttachments(attachmentManager.buildSlideDeck().asAttachments(), null)
                    }

                    override fun onFailure(e: ExecutionException?) {
                        Toast.makeText(this@ConversationActivityV2, R.string.activity_conversation_attachment_prep_failed, Toast.LENGTH_LONG).show()
                    }
                })
                return
            }
        } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            val dataTextExtra = intent.getCharSequenceExtra(Intent.EXTRA_TEXT) ?: ""
            binding.inputBar.text = dataTextExtra.toString()
        } else {
            viewModel.getDraft()?.let { text ->
                binding.inputBar.text = text
            }
        }
    }

    private fun addOpenGroupGuidelinesIfNeeded(isOxenHostedOpenGroup: Boolean) {
        if (!isOxenHostedOpenGroup) { return }
        binding.openGroupGuidelinesView.visibility = View.VISIBLE
        val recyclerViewLayoutParams = binding.conversationRecyclerView.layoutParams as RelativeLayout.LayoutParams
        recyclerViewLayoutParams.topMargin = toPx(57, resources) // The height of the open group guidelines view is hardcoded to this
        binding.conversationRecyclerView.layoutParams = recyclerViewLayoutParams
    }

    private fun setUpTypingObserver() {
        ApplicationContext.getInstance(this).typingStatusRepository.getTypists(viewModel.threadId).observe(this) { state ->
            val recipients = if (state != null) state.typists else listOf()
            // FIXME: Also checking isScrolledToBottom is a quick fix for an issue where the
            //        typing indicator overlays the recycler view when scrolled up
            binding.typingIndicatorViewContainer.isVisible = recipients.isNotEmpty() && isScrolledToBottom
            binding.typingIndicatorViewContainer.setTypists(recipients)
            inputBarHeightChanged(binding.inputBar.height)
        }
        if (textSecurePreferences.isTypingIndicatorsEnabled()) {
            binding.inputBar.addTextChangedListener(object : SimpleTextWatcher() {

                override fun onTextChanged(text: String?) {
                    ApplicationContext.getInstance(this@ConversationActivityV2).typingStatusSender.onTypingStarted(viewModel.threadId)
                }
            })
        }
    }

    private fun setUpRecipientObserver() {
        viewModel.recipient.addListener(this)
    }

    private fun getLatestOpenGroupInfoIfNeeded() {
        val openGroup = lokiThreadDb.getOpenGroupChat(viewModel.threadId) ?: return
        OpenGroupAPIV2.getMemberCount(openGroup.room, openGroup.server).successUi { updateSubtitle() }
    }

    private fun setUpBlockedBanner() {
        if (viewModel.recipient.isGroupRecipient) { return }
        val sessionID = viewModel.recipient.address.toString()
        val contact = sessionContactDb.getContactWithSessionID(sessionID)
        val name = contact?.displayName(Contact.ContactContext.REGULAR) ?: sessionID
        binding.blockedBannerTextView.text = resources.getString(R.string.activity_conversation_blocked_banner_text, name)
        binding.blockedBanner.isVisible = viewModel.recipient.isBlocked
        binding.blockedBanner.setOnClickListener { viewModel.unblock() }
    }

    private fun setUpLinkPreviewObserver() {
        if (!textSecurePreferences.isLinkPreviewsEnabled()) {
            linkPreviewViewModel.onUserCancel(); return
        }
        linkPreviewViewModel.linkPreviewState.observe(this) { previewState: LinkPreviewState? ->
            if (previewState == null) return@observe
            when {
                previewState.isLoading -> {
                    binding.inputBar.draftLinkPreview()
                }
                previewState.linkPreview.isPresent -> {
                    binding.inputBar.updateLinkPreviewDraft(glide, previewState.linkPreview.get())
                }
                else -> {
                    binding.inputBar.cancelLinkPreviewDraft()
                }
            }
        }
    }

    private fun setUpUiStateObserver() {
        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect { uiState ->
                uiState.uiMessages.firstOrNull()?.let {
                    Toast.makeText(this@ConversationActivityV2, it.message, Toast.LENGTH_LONG).show()
                    viewModel.messageShown(it.id)
                }
                addOpenGroupGuidelinesIfNeeded(uiState.isOxenHostedOpenGroup)
            }
        }
    }

    private fun scrollToFirstUnreadMessageIfNeeded() {
        val lastSeenTimestamp = threadDb.getLastSeenAndHasSent(viewModel.threadId).first()
        val lastSeenItemPosition = adapter.findLastSeenItemPosition(lastSeenTimestamp) ?: return
        if (lastSeenItemPosition <= 3) { return }
        binding.conversationRecyclerView.scrollToPosition(lastSeenItemPosition)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        ConversationMenuHelper.onPrepareOptionsMenu(menu, menuInflater, viewModel.recipient, viewModel.threadId, this) { onOptionsItemSelected(it) }
        super.onPrepareOptionsMenu(menu)
        return true
    }

    override fun onDestroy() {
        viewModel.saveDraft(binding.inputBar.text.trim())
        super.onDestroy()
    }
    // endregion

    // region Animation & Updating
    override fun onModified(recipient: Recipient) {
        runOnUiThread {
            if (viewModel.recipient.isContactRecipient) {
                binding.blockedBanner.isVisible = viewModel.recipient.isBlocked
            }
            updateSubtitle()
            showOrHideInputIfNeeded()
            actionBarBinding.profilePictureView.update(recipient)
        }
    }

    private fun showOrHideInputIfNeeded() {
        if (viewModel.recipient.isClosedGroupRecipient) {
            val group = groupDb.getGroup(viewModel.recipient.address.toGroupString()).orNull()
            val isActive = (group?.isActive == true)
            binding.inputBar.showInput = isActive
        } else {
            binding.inputBar.showInput = true
        }
    }

    override fun inputBarHeightChanged(newValue: Int) {
    }

    override fun inputBarEditTextContentChanged(newContent: CharSequence) {
        if (textSecurePreferences.isLinkPreviewsEnabled()) {
            linkPreviewViewModel.onTextChanged(this, binding.inputBar.text, 0, 0)
        }
        showOrHideMentionCandidatesIfNeeded(newContent)
        if (LinkPreviewUtil.findWhitelistedUrls(newContent.toString()).isNotEmpty()
            && !textSecurePreferences.isLinkPreviewsEnabled() && !textSecurePreferences.hasSeenLinkPreviewSuggestionDialog()) {
            LinkPreviewDialog {
                setUpLinkPreviewObserver()
                linkPreviewViewModel.onEnabled()
                linkPreviewViewModel.onTextChanged(this, binding.inputBar.text, 0, 0)
            }.show(supportFragmentManager, "Link Preview Dialog")
            textSecurePreferences.setHasSeenLinkPreviewSuggestionDialog()
        }
    }

    private fun showOrHideMentionCandidatesIfNeeded(text: CharSequence) {
        if (text.length < previousText.length) {
            currentMentionStartIndex = -1
            hideMentionCandidates()
            val mentionsToRemove = mentions.filter { !text.contains(it.displayName) }
            mentions.removeAll(mentionsToRemove)
        }
        if (text.isNotEmpty()) {
            val lastCharIndex = text.lastIndex
            val lastChar = text[lastCharIndex]
            // Check if there is whitespace before the '@' or the '@' is the first character
            val isCharacterBeforeLastWhiteSpaceOrStartOfLine: Boolean
            if (text.length == 1) {
                isCharacterBeforeLastWhiteSpaceOrStartOfLine = true // Start of line
            } else {
                val charBeforeLast = text[lastCharIndex - 1]
                isCharacterBeforeLastWhiteSpaceOrStartOfLine = Character.isWhitespace(charBeforeLast)
            }
            if (lastChar == '@' && isCharacterBeforeLastWhiteSpaceOrStartOfLine) {
                currentMentionStartIndex = lastCharIndex
                showOrUpdateMentionCandidatesIfNeeded()
            } else if (Character.isWhitespace(lastChar) || lastChar == '@') { // the lastCharacter == "@" is to check for @@
                currentMentionStartIndex = -1
                hideMentionCandidates()
            } else if (currentMentionStartIndex != -1) {
                val query = text.substring(currentMentionStartIndex + 1) // + 1 to get rid of the "@"
                showOrUpdateMentionCandidatesIfNeeded(query)
            }
        }
        previousText = text
    }

    private fun showOrUpdateMentionCandidatesIfNeeded(query: String = "") {
        if (!isShowingMentionCandidatesView) {
            binding.additionalContentContainer.removeAllViews()
            val view = MentionCandidatesView(this)
            view.glide = glide
            view.onCandidateSelected = { handleMentionSelected(it) }
            binding.additionalContentContainer.addView(view)
            val candidates = MentionsManager.getMentionCandidates(query, viewModel.threadId, viewModel.recipient.isOpenGroupRecipient)
            this.mentionCandidatesView = view
            view.show(candidates, viewModel.threadId)
            view.alpha = 0.0f
            val animation = ValueAnimator.ofObject(FloatEvaluator(), view.alpha, 1.0f)
            animation.duration = 250L
            animation.addUpdateListener { animator ->
                view.alpha = animator.animatedValue as Float
            }
            animation.start()
        } else {
            val candidates = MentionsManager.getMentionCandidates(query, viewModel.threadId, viewModel.recipient.isOpenGroupRecipient)
            this.mentionCandidatesView!!.setMentionCandidates(candidates)
        }
        isShowingMentionCandidatesView = true
    }

    private fun hideMentionCandidates() {
        if (isShowingMentionCandidatesView) {
            val mentionCandidatesView = mentionCandidatesView ?: return
            val animation = ValueAnimator.ofObject(FloatEvaluator(), mentionCandidatesView.alpha, 0.0f)
            animation.duration = 250L
            animation.addUpdateListener { animator ->
                mentionCandidatesView.alpha = animator.animatedValue as Float
                if (animator.animatedFraction == 1.0f) { binding.additionalContentContainer.removeAllViews() }
            }
            animation.start()
        }
        isShowingMentionCandidatesView = false
    }

    override fun toggleAttachmentOptions() {
        val targetAlpha = if (isShowingAttachmentOptions) 0.0f else 1.0f
        val allButtonContainers = listOf( binding.cameraButtonContainer, binding.libraryButtonContainer, binding.documentButtonContainer, binding.gifButtonContainer)
        val isReversed = isShowingAttachmentOptions // Run the animation in reverse
        val count = allButtonContainers.size
        allButtonContainers.indices.forEach { index ->
            val view = allButtonContainers[index]
            val animation = ValueAnimator.ofObject(FloatEvaluator(), view.alpha, targetAlpha)
            animation.duration = 250L
            animation.startDelay = if (isReversed) 50L * (count - index.toLong()) else 50L * index.toLong()
            animation.addUpdateListener { animator ->
                view.alpha = animator.animatedValue as Float
            }
            animation.start()
        }
        isShowingAttachmentOptions = !isShowingAttachmentOptions
        val allButtons = listOf( cameraButton, libraryButton, documentButton, gifButton )
        allButtons.forEach { it.snIsEnabled = isShowingAttachmentOptions }
    }

    override fun showVoiceMessageUI() {
        binding.inputBarRecordingView.show()
        binding.inputBar.alpha = 0.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            binding.inputBar.alpha = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun expandVoiceMessageLockView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), binding.inputBarRecordingView.lockView.scaleX, 1.10f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            binding.inputBarRecordingView.lockView.scaleX = animator.animatedValue as Float
            binding.inputBarRecordingView.lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun collapseVoiceMessageLockView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), binding.inputBarRecordingView.lockView.scaleX, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            binding.inputBarRecordingView.lockView.scaleX = animator.animatedValue as Float
            binding.inputBarRecordingView.lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun hideVoiceMessageUI() {
        val chevronImageView = binding.inputBarRecordingView.chevronImageView
        val slideToCancelTextView = binding.inputBarRecordingView.slideToCancelTextView
        listOf( chevronImageView, slideToCancelTextView ).forEach { view ->
            val animation = ValueAnimator.ofObject(FloatEvaluator(), view.translationX, 0.0f)
            animation.duration = 250L
            animation.addUpdateListener { animator ->
                view.translationX = animator.animatedValue as Float
            }
            animation.start()
        }
        binding.inputBarRecordingView.hide()
    }

    override fun handleVoiceMessageUIHidden() {
        binding.inputBar.alpha = 1.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            binding.inputBar.alpha = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun handleRecyclerViewScrolled() {
        // FIXME: Checking isScrolledToBottom is a quick fix for an issue where the
        //        typing indicator overlays the recycler view when scrolled up
        val wasTypingIndicatorVisibleBefore = binding.typingIndicatorViewContainer.isVisible
        binding.typingIndicatorViewContainer.isVisible = wasTypingIndicatorVisibleBefore && isScrolledToBottom
        val isTypingIndicatorVisibleAfter = binding.typingIndicatorViewContainer.isVisible
        if (isTypingIndicatorVisibleAfter != wasTypingIndicatorVisibleBefore) {
            inputBarHeightChanged(binding.inputBar.height)
        }
        binding.scrollToBottomButton.isVisible = !isScrolledToBottom
        unreadCount = min(unreadCount, layoutManager.findFirstVisibleItemPosition())
        updateUnreadCountIndicator()
    }

    private fun updateUnreadCountIndicator() {
        val formattedUnreadCount = if (unreadCount < 10000) unreadCount.toString() else "9999+"
        binding.unreadCountTextView.text = formattedUnreadCount
        val textSize = if (unreadCount < 10000) 12.0f else 9.0f
        binding.unreadCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
        binding.unreadCountTextView.setTypeface(Typeface.DEFAULT, if (unreadCount < 100) Typeface.BOLD else Typeface.NORMAL)
        binding.unreadCountIndicator.isVisible = (unreadCount != 0)
    }

    private fun updateSubtitle() {
        actionBarBinding.muteIconImageView.isVisible = viewModel.recipient.isMuted
        actionBarBinding.conversationSubtitleView.isVisible = true
        if (viewModel.recipient.isMuted) {
            if (viewModel.recipient.mutedUntil != Long.MAX_VALUE) {
                actionBarBinding.conversationSubtitleView.text = getString(R.string.ConversationActivity_muted_until_date, DateUtils.getFormattedDateTime(viewModel.recipient.mutedUntil, "EEE, MMM d, yyyy HH:mm", Locale.getDefault()))
            } else {
                actionBarBinding.conversationSubtitleView.text = getString(R.string.ConversationActivity_muted_forever)
            }
        } else if (viewModel.recipient.isGroupRecipient) {
            val openGroup = lokiThreadDb.getOpenGroupChat(viewModel.threadId)
            if (openGroup != null) {
                val userCount = lokiApiDb.getUserCount(openGroup.room, openGroup.server) ?: 0
                actionBarBinding.conversationSubtitleView.text = getString(R.string.ConversationActivity_member_count, userCount)
            } else {
                actionBarBinding.conversationSubtitleView.isVisible = false
            }
        } else {
            actionBarBinding.conversationSubtitleView.isVisible = false
        }
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return false
        }
        return ConversationMenuHelper.onOptionItemSelected(this, item, viewModel.recipient)
    }

    // `position` is the adapter position; not the visual position
    private fun handlePress(message: MessageRecord, position: Int, view: VisibleMessageView, event: MotionEvent) {
        val actionMode = this.actionMode
        if (actionMode != null) {
            onDeselect(message, position, actionMode)
        } else {
            // NOTE:
            // We have to use onContentClick (rather than a click listener directly on
            // the view) so as to not interfere with all the other gestures. Do not add
            // onClickListeners directly to message content views.
            view.onContentClick(event)
        }
    }

    private fun onDeselect(message: MessageRecord, position: Int, actionMode: ActionMode) {
        adapter.toggleSelection(message, position)
        val actionModeCallback = ConversationActionModeCallback(adapter, viewModel.threadId, this)
        actionModeCallback.delegate = this
        actionModeCallback.updateActionModeMenu(actionMode.menu)
        if (adapter.selectedItems.isEmpty()) {
            actionMode.finish()
            this.actionMode = null
        }
    }

    // `position` is the adapter position; not the visual position
    private fun handleSwipeToReply(message: MessageRecord, position: Int) {
        binding.inputBar.draftQuote(viewModel.recipient, message, glide)
    }

    // `position` is the adapter position; not the visual position
    private fun handleLongPress(message: MessageRecord, position: Int) {
        val actionMode = this.actionMode
        val actionModeCallback = ConversationActionModeCallback(adapter, viewModel.threadId, this)
        actionModeCallback.delegate = this
        searchViewItem?.collapseActionView()
        if (actionMode == null) { // Nothing should be selected if this is the case
            adapter.toggleSelection(message, position)
            this.actionMode = startActionMode(actionModeCallback, ActionMode.TYPE_PRIMARY)
        } else {
            adapter.toggleSelection(message, position)
            actionModeCallback.updateActionModeMenu(actionMode.menu)
            if (adapter.selectedItems.isEmpty()) {
                actionMode.finish()
                this.actionMode = null
            }
        }
    }

    override fun onMicrophoneButtonMove(event: MotionEvent) {
        val rawX = event.rawX
        val chevronImageView = binding.inputBarRecordingView.chevronImageView
        val slideToCancelTextView = binding.inputBarRecordingView.slideToCancelTextView
        if (rawX < screenWidth / 2) {
            val translationX = rawX - screenWidth / 2
            val sign = -1.0f
            val chevronDamping = 4.0f
            val labelDamping = 3.0f
            val chevronX = (chevronDamping * (sqrt(abs(translationX)) / sqrt(chevronDamping))) * sign
            val labelX = (labelDamping * (sqrt(abs(translationX)) / sqrt(labelDamping))) * sign
            chevronImageView.translationX = chevronX
            slideToCancelTextView.translationX = labelX
        } else {
            chevronImageView.translationX = 0.0f
            slideToCancelTextView.translationX = 0.0f
        }
        if (isValidLockViewLocation(event.rawX.roundToInt(), event.rawY.roundToInt())) {
            if (!isLockViewExpanded) {
                expandVoiceMessageLockView()
                isLockViewExpanded = true
            }
        } else {
            if (isLockViewExpanded) {
                collapseVoiceMessageLockView()
                isLockViewExpanded = false
            }
        }
    }

    override fun onMicrophoneButtonCancel(event: MotionEvent) {
        hideVoiceMessageUI()
    }

    override fun onMicrophoneButtonUp(event: MotionEvent) {
        val x = event.rawX.roundToInt()
        val y = event.rawY.roundToInt()
        if (isValidLockViewLocation(x, y)) {
            binding.inputBarRecordingView.lock()
        } else {
            val recordButtonOverlay = binding.inputBarRecordingView.recordButtonOverlay
            val location = IntArray(2) { 0 }
            recordButtonOverlay.getLocationOnScreen(location)
            val hitRect = Rect(location[0], location[1], location[0] + recordButtonOverlay.width, location[1] + recordButtonOverlay.height)
            if (hitRect.contains(x, y)) {
                sendVoiceMessage()
            } else {
                cancelVoiceMessage()
            }
        }
    }

    private fun isValidLockViewLocation(x: Int, y: Int): Boolean {
        // We can be anywhere above the lock view and a bit to the side of it (at most `lockViewHitMargin`
        // to the side)
        val lockViewLocation = IntArray(2) { 0 }
        binding.inputBarRecordingView.lockView.getLocationOnScreen(lockViewLocation)
        val hitRect = Rect(lockViewLocation[0] - lockViewHitMargin, 0,
            lockViewLocation[0] + binding.inputBarRecordingView.lockView.width + lockViewHitMargin, lockViewLocation[1] + binding.inputBarRecordingView.lockView.height)
        return hitRect.contains(x, y)
    }

    private fun handleMentionSelected(mention: Mention) {
        if (currentMentionStartIndex == -1) { return }
        mentions.add(mention)
        val previousText = binding.inputBar.text
        val newText = previousText.substring(0, currentMentionStartIndex) + "@" + mention.displayName + " "
        binding.inputBar.text = newText
        binding.inputBar.setSelection(newText.length)
        currentMentionStartIndex = -1
        hideMentionCandidates()
        this.previousText = newText
    }

    override fun scrollToMessageIfPossible(timestamp: Long) {
        val lastSeenItemPosition = adapter.getItemPositionForTimestamp(timestamp) ?: return
        binding.conversationRecyclerView.scrollToPosition(lastSeenItemPosition)
    }

    override fun playVoiceMessageAtIndexIfPossible(indexInAdapter: Int) {
        if (indexInAdapter < 0 || indexInAdapter >= adapter.itemCount) { return }
        val viewHolder = binding.conversationRecyclerView.findViewHolderForAdapterPosition(indexInAdapter) as? ConversationAdapter.VisibleMessageViewHolder
        viewHolder?.view?.playVoiceMessage()
    }

    override fun sendMessage() {
        if (viewModel.recipient.isContactRecipient && viewModel.recipient.isBlocked) {
            BlockedDialog(viewModel.recipient).show(supportFragmentManager, "Blocked Dialog")
            return
        }
        if (binding.inputBar.linkPreview != null || binding.inputBar.quote != null) {
            sendAttachments(listOf(), getMessageBody(), binding.inputBar.quote, binding.inputBar.linkPreview)
        } else {
            sendTextOnlyMessage()
        }
    }

    override fun commitInputContent(contentUri: Uri) {
        val media = Media(contentUri, MediaUtil.getMimeType(this, contentUri)!!, 0, 0, 0, 0, Optional.absent(), Optional.absent())
        startActivityForResult(MediaSendActivity.buildEditorIntent(this, listOf( media ), viewModel.recipient, getMessageBody()), PICK_FROM_LIBRARY)
    }

    private fun sendTextOnlyMessage(hasPermissionToSendSeed: Boolean = false) {
        val text = getMessageBody()
        val userPublicKey = textSecurePreferences.getLocalNumber()
        val isNoteToSelf = (viewModel.recipient.isContactRecipient && viewModel.recipient.address.toString() == userPublicKey)
        if (text.contains(seed) && !isNoteToSelf && !hasPermissionToSendSeed) {
            val dialog = SendSeedDialog { sendTextOnlyMessage(true) }
            return dialog.show(supportFragmentManager, "Send Seed Dialog")
        }
        // Create the message
        val message = VisibleMessage()
        message.sentTimestamp = System.currentTimeMillis()
        message.text = text
        val outgoingTextMessage = OutgoingTextMessage.from(message, viewModel.recipient)
        // Clear the input bar
        binding.inputBar.text = ""
        binding.inputBar.cancelQuoteDraft()
        binding.inputBar.cancelLinkPreviewDraft()
        // Clear mentions
        previousText = ""
        currentMentionStartIndex = -1
        mentions.clear()
        // Put the message in the database
        message.id = smsDb.insertMessageOutbox(viewModel.threadId, outgoingTextMessage, false, message.sentTimestamp!!) { }
        // Send it
        MessageSender.send(message, viewModel.recipient.address)
        // Send a typing stopped message
        ApplicationContext.getInstance(this).typingStatusSender.onTypingStopped(viewModel.threadId)
    }

    private fun sendAttachments(attachments: List<Attachment>, body: String?, quotedMessage: MessageRecord? = null, linkPreview: LinkPreview? = null) {
        // Create the message
        val message = VisibleMessage()
        message.sentTimestamp = System.currentTimeMillis()
        message.text = body
        val quote = quotedMessage?.let {
            val quotedAttachments = (it as? MmsMessageRecord)?.slideDeck?.asAttachments() ?: listOf()
            val sender = if (it.isOutgoing) fromSerialized(textSecurePreferences.getLocalNumber()!!) else it.individualRecipient.address
            QuoteModel(it.dateSent, sender, it.body, false, quotedAttachments)
        }
        val outgoingTextMessage = OutgoingMediaMessage.from(message, viewModel.recipient, attachments, quote, linkPreview)
        // Clear the input bar
        binding.inputBar.text = ""
        binding.inputBar.cancelQuoteDraft()
        binding.inputBar.cancelLinkPreviewDraft()
        // Clear mentions
        previousText = ""
        currentMentionStartIndex = -1
        mentions.clear()
        // Reset the attachment manager
        attachmentManager.clear()
        // Reset attachments button if needed
        if (isShowingAttachmentOptions) { toggleAttachmentOptions() }
        // Put the message in the database
        message.id = mmsDb.insertMessageOutbox(outgoingTextMessage, viewModel.threadId, false) { }
        // Send it
        MessageSender.send(message, viewModel.recipient.address, attachments, quote, linkPreview)
        // Send a typing stopped message
        ApplicationContext.getInstance(this).typingStatusSender.onTypingStopped(viewModel.threadId)
    }

    private fun showGIFPicker() {
        val hasSeenGIFMetaDataWarning: Boolean = textSecurePreferences.hasSeenGIFMetaDataWarning()
        if (!hasSeenGIFMetaDataWarning) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Search GIFs?")
            builder.setMessage("You will not have full metadata protection when sending GIFs.")
            builder.setPositiveButton("OK") { dialog: DialogInterface, _: Int ->
                textSecurePreferences.setHasSeenGIFMetaDataWarning()
                AttachmentManager.selectGif(this, PICK_GIF)
                dialog.dismiss()
            }
            builder.setNegativeButton(
                "Cancel"
            ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            builder.create().show()
        } else {
            AttachmentManager.selectGif(this, PICK_GIF)
        }
    }

    private fun showDocumentPicker() {
        AttachmentManager.selectDocument(this, PICK_DOCUMENT)
    }

    private fun pickFromLibrary() {
        AttachmentManager.selectGallery(this, PICK_FROM_LIBRARY, viewModel.recipient, binding.inputBar.text.trim())
    }

    private fun showCamera() {
        attachmentManager.capturePhoto(this, TAKE_PHOTO, viewModel.recipient);
    }

    override fun onAttachmentChanged() {
        // Do nothing
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        val mediaPreppedListener = object : ListenableFuture.Listener<Boolean> {

            override fun onSuccess(result: Boolean?) {
                sendAttachments(attachmentManager.buildSlideDeck().asAttachments(), null)
            }

            override fun onFailure(e: ExecutionException?) {
                Toast.makeText(this@ConversationActivityV2, R.string.activity_conversation_attachment_prep_failed, Toast.LENGTH_LONG).show()
            }
        }
        when (requestCode) {
            PICK_DOCUMENT -> {
                val uri = intent?.data ?: return
                prepMediaForSending(uri, AttachmentManager.MediaType.DOCUMENT).addListener(mediaPreppedListener)
            }
            PICK_GIF -> {
                intent ?: return
                val uri = intent.data ?: return
                val type = AttachmentManager.MediaType.GIF
                val width = intent.getIntExtra(GiphyActivity.EXTRA_WIDTH, 0)
                val height = intent.getIntExtra(GiphyActivity.EXTRA_HEIGHT, 0)
                prepMediaForSending(uri, type, width, height).addListener(mediaPreppedListener)
            }
            PICK_FROM_LIBRARY,
            TAKE_PHOTO -> {
                intent ?: return
                val body = intent.getStringExtra(MediaSendActivity.EXTRA_MESSAGE)
                val media = intent.getParcelableArrayListExtra<Media>(MediaSendActivity.EXTRA_MEDIA) ?: return
                val slideDeck = SlideDeck()
                for (item in media) {
                    when {
                        MediaUtil.isVideoType(item.mimeType) -> {
                            slideDeck.addSlide(VideoSlide(this, item.uri, 0, item.caption.orNull()))
                        }
                        MediaUtil.isGif(item.mimeType) -> {
                            slideDeck.addSlide(GifSlide(this, item.uri, 0, item.width, item.height, item.caption.orNull()))
                        }
                        MediaUtil.isImageType(item.mimeType) -> {
                            slideDeck.addSlide(ImageSlide(this, item.uri, 0, item.width, item.height, item.caption.orNull()))
                        }
                        else -> {
                            Log.d("Loki", "Asked to send an unexpected media type: '" + item.mimeType + "'. Skipping.")
                        }
                    }
                }
                sendAttachments(slideDeck.asAttachments(), body)
            }
            INVITE_CONTACTS -> {
                if (!viewModel.recipient.isOpenGroupRecipient) { return }
                val extras = intent?.extras ?: return
                if (!intent.hasExtra(selectedContactsKey)) { return }
                val selectedContacts = extras.getStringArray(selectedContactsKey)!!
                val recipients = selectedContacts.map { contact ->
                    Recipient.from(this, fromSerialized(contact), true)
                }
                viewModel.inviteContacts(recipients)
            }
        }
    }

    private fun prepMediaForSending(uri: Uri, type: AttachmentManager.MediaType): ListenableFuture<Boolean> {
        return prepMediaForSending(uri, type, null, null)
    }

    private fun prepMediaForSending(uri: Uri, type: AttachmentManager.MediaType, width: Int?, height: Int?): ListenableFuture<Boolean> {
        return attachmentManager.setMedia(glide, uri, type, MediaConstraints.getPushMediaConstraints(), width ?: 0, height ?: 0)
    }

    override fun startRecordingVoiceMessage() {
        if (Permissions.hasAll(this, Manifest.permission.RECORD_AUDIO)) {
            showVoiceMessageUI()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            audioRecorder.startRecording()
            stopAudioHandler.postDelayed(stopVoiceMessageRecordingTask, 60000) // Limit voice messages to 1 minute each
        } else {
            Permissions.with(this)
                .request(Manifest.permission.RECORD_AUDIO)
                .withRationaleDialog(getString(R.string.ConversationActivity_to_send_audio_messages_allow_signal_access_to_your_microphone), R.drawable.ic_baseline_mic_48)
                .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_requires_the_microphone_permission_in_order_to_send_audio_messages))
                .execute()
        }
    }

    override fun sendVoiceMessage() {
        hideVoiceMessageUI()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val future = audioRecorder.stopRecording()
        stopAudioHandler.removeCallbacks(stopVoiceMessageRecordingTask)
        future.addListener(object : ListenableFuture.Listener<Pair<Uri, Long>> {

            override fun onSuccess(result: Pair<Uri, Long>) {
                val audioSlide = AudioSlide(this@ConversationActivityV2, result.first, result.second, MediaTypes.AUDIO_AAC, true)
                val slideDeck = SlideDeck()
                slideDeck.addSlide(audioSlide)
                sendAttachments(slideDeck.asAttachments(), null)
            }

            override fun onFailure(e: ExecutionException) {
                Toast.makeText(this@ConversationActivityV2, R.string.ConversationActivity_unable_to_record_audio, Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun cancelVoiceMessage() {
        hideVoiceMessageUI()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioRecorder.stopRecording()
        stopAudioHandler.removeCallbacks(stopVoiceMessageRecordingTask)
    }

    // Remove this after the unsend request is enabled
    fun deleteMessagesWithoutUnsendRequest(messages: Set<MessageRecord>) {
        val messageCount = messages.size
        val builder = AlertDialog.Builder(this)
        builder.setTitle(resources.getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messageCount, messageCount))
        builder.setMessage(resources.getQuantityString(R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages, messageCount, messageCount))
        builder.setCancelable(true)
        builder.setPositiveButton(R.string.delete) { _, _ ->
            viewModel.deleteMessagesWithoutUnsendRequest(messages)
            endActionMode()
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
            endActionMode()
        }
        builder.show()
    }

    override fun deleteMessages(messages: Set<MessageRecord>) {
        if (!IS_UNSEND_REQUESTS_ENABLED) {
            deleteMessagesWithoutUnsendRequest(messages)
            return
        }
        val allSentByCurrentUser = messages.all { it.isOutgoing }
        val allHasHash = messages.all { lokiMessageDb.getMessageServerHash(it.id) != null }
        if (viewModel.recipient.isOpenGroupRecipient) {
            val messageCount = messages.size
            val builder = AlertDialog.Builder(this)
            builder.setTitle(resources.getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messageCount, messageCount))
            builder.setMessage(resources.getQuantityString(R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages, messageCount, messageCount))
            builder.setCancelable(true)
            builder.setPositiveButton(R.string.delete) { _, _ ->
                for (message in messages) {
                    viewModel.deleteForEveryone(message)
                }
                endActionMode()
            }
            builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                endActionMode()
            }
            builder.show()
        } else if (allSentByCurrentUser && allHasHash) {
            val bottomSheet = DeleteOptionsBottomSheet()
            bottomSheet.recipient = viewModel.recipient
            bottomSheet.onDeleteForMeTapped = {
                for (message in messages) {
                    viewModel.deleteLocally(message)
                }
                bottomSheet.dismiss()
                endActionMode()
            }
            bottomSheet.onDeleteForEveryoneTapped = {
                for (message in messages) {
                    viewModel.deleteForEveryone(message)
                }
                bottomSheet.dismiss()
                endActionMode()
            }
            bottomSheet.onCancelTapped = {
                bottomSheet.dismiss()
                endActionMode()
            }
            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
        } else {
            val messageCount = messages.size
            val builder = AlertDialog.Builder(this)
            builder.setTitle(resources.getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messageCount, messageCount))
            builder.setMessage(resources.getQuantityString(R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages, messageCount, messageCount))
            builder.setCancelable(true)
            builder.setPositiveButton(R.string.delete) { _, _ ->
                for (message in messages) {
                    viewModel.deleteLocally(message)
                }
                endActionMode()
            }
            builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                endActionMode()
            }
            builder.show()
        }
    }

    override fun banUser(messages: Set<MessageRecord>) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.ConversationFragment_ban_selected_user)
        builder.setMessage("This will ban the selected user from this room. It won't ban them from other rooms.")
        builder.setCancelable(true)
        builder.setPositiveButton(R.string.ban) { _, _ ->
            viewModel.banUser(messages.first().individualRecipient)
            endActionMode()
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
            endActionMode()
        }
        builder.show()
    }

    override fun banAndDeleteAll(messages: Set<MessageRecord>) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.ConversationFragment_ban_selected_user)
        builder.setMessage("This will ban the selected user from this room and delete all messages sent by them. It won't ban them from other rooms or delete the messages they sent there.")
        builder.setCancelable(true)
        builder.setPositiveButton(R.string.ban) { _, _ ->
            viewModel.banAndDeleteAll(messages.first().individualRecipient)
            endActionMode()
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
            endActionMode()
        }
        builder.show()
    }

    override fun copyMessages(messages: Set<MessageRecord>) {
        val sortedMessages = messages.sortedBy { it.dateSent }
        val messageSize = sortedMessages.size
        val builder = StringBuilder()
        val messageIterator = sortedMessages.iterator()
        while (messageIterator.hasNext()) {
            val message = messageIterator.next()
            val body = MentionUtilities.highlightMentions(message.body, viewModel.threadId, this)
            if (TextUtils.isEmpty(body)) { continue }
            if (messageSize > 1) {
                val formattedTimestamp = DateUtils.getDisplayFormattedTimeSpanString(this, Locale.getDefault(), message.timestamp)
                builder.append("$formattedTimestamp: ")
            }
            builder.append(body)
            if (messageIterator.hasNext()) {
                builder.append('\n')
            }
        }
        if (builder.isNotEmpty() && builder[builder.length - 1] == '\n') {
            builder.deleteCharAt(builder.length - 1)
        }
        val result = builder.toString()
        if (TextUtils.isEmpty(result)) { return }
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("Message Content", result))
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        endActionMode()
    }

    override fun copySessionID(messages: Set<MessageRecord>) {
        val sessionID = messages.first().individualRecipient.address.toString()
        val clip = ClipData.newPlainText("Session ID", sessionID)
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        endActionMode()
    }

    override fun resendMessage(messages: Set<MessageRecord>) {
        messages.iterator().forEach { messageRecord ->
            ResendMessageUtilities.resend(messageRecord)
        }
        endActionMode()
    }

    override fun showMessageDetail(messages: Set<MessageRecord>) {
        val message = messages.first()
        val intent = Intent(this, MessageDetailActivity::class.java)
        intent.putExtra(MessageDetailActivity.MESSAGE_TIMESTAMP, message.timestamp)
        push(intent)
        endActionMode()
    }

    override fun saveAttachment(messages: Set<MessageRecord>) {
        val message = messages.first() as MmsMessageRecord
        SaveAttachmentTask.showWarningDialog(this, { _, _ ->
            Permissions.with(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .maxSdkVersion(Build.VERSION_CODES.P)
                .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                .onAnyDenied {
                    endActionMode()
                    Toast.makeText(this@ConversationActivityV2, R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show()
                }
                .onAllGranted {
                    endActionMode()
                    val attachments: List<SaveAttachmentTask.Attachment?> = Stream.of(message.slideDeck.slides)
                        .filter { s: Slide -> s.uri != null && (s.hasImage() || s.hasVideo() || s.hasAudio() || s.hasDocument()) }
                        .map { s: Slide -> SaveAttachmentTask.Attachment(s.uri!!, s.contentType, message.dateReceived, s.fileName.orNull()) }
                        .toList()
                    if (attachments.isNotEmpty()) {
                        val saveTask = SaveAttachmentTask(this)
                        saveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, *attachments.toTypedArray())
                        if (!message.isOutgoing) {
                            sendMediaSavedNotification()
                        }
                        return@onAllGranted
                    }
                    Toast.makeText(this,
                        resources.getQuantityString(R.plurals.ConversationFragment_error_while_saving_attachments_to_sd_card, 1),
                        Toast.LENGTH_LONG).show()
                }
                .execute()
        })
    }

    override fun reply(messages: Set<MessageRecord>) {
        binding.inputBar.draftQuote(viewModel.recipient, messages.first(), glide)
        endActionMode()
    }

    private fun sendMediaSavedNotification() {
        if (viewModel.recipient.isGroupRecipient) { return }
        val timestamp = System.currentTimeMillis()
        val kind = DataExtractionNotification.Kind.MediaSaved(timestamp)
        val message = DataExtractionNotification(kind)
        MessageSender.send(message, viewModel.recipient.address)
    }

    private fun endActionMode() {
        actionMode?.finish()
        actionMode = null
    }
    // endregion

    // region General
    private fun getMessageBody(): String {
        var result = binding.inputBar.text.trim()
        for (mention in mentions) {
            try {
                val startIndex = result.indexOf("@" + mention.displayName)
                val endIndex = startIndex + mention.displayName.count() + 1 // + 1 to include the "@"
                result = result.substring(0, startIndex) + "@" + mention.publicKey + result.substring(endIndex)
            } catch (exception: Exception) {
                Log.d("Loki", "Failed to process mention due to error: $exception")
            }
        }
        return result
    }
    // endregion

    // region Search
    private fun setUpSearchResultObserver() {
        searchViewModel.searchResults.observe(this, Observer { result: SearchViewModel.SearchResult? ->
            if (result == null) return@Observer
            if (result.getResults().isNotEmpty()) {
                result.getResults()[result.position]?.let {
                    jumpToMessage(it.messageRecipient.address, it.receivedTimestampMs, Runnable { searchViewModel.onMissingResult() })
                }
            }
            binding.searchBottomBar.setData(result.position, result.getResults().size)
        })
    }

    fun onSearchOpened() {
        searchViewModel.onSearchOpened()
        binding.searchBottomBar.visibility = View.VISIBLE
        binding.searchBottomBar.setData(0, 0)
        binding.inputBar.visibility = View.GONE
    }

    fun onSearchClosed() {
        searchViewModel.onSearchClosed()
        binding.searchBottomBar.visibility = View.GONE
        binding.inputBar.visibility = View.VISIBLE
        adapter.onSearchQueryUpdated(null)
        invalidateOptionsMenu()
    }

    fun onSearchQueryUpdated(query: String) {
        searchViewModel.onQueryUpdated(query, viewModel.threadId)
        binding.searchBottomBar.showLoading()
        adapter.onSearchQueryUpdated(query)
    }

    override fun onSearchMoveUpPressed() {
        this.searchViewModel.onMoveUp()
    }

    override fun onSearchMoveDownPressed() {
        this.searchViewModel.onMoveDown()
    }

    private fun jumpToMessage(author: Address, timestamp: Long, onMessageNotFound: Runnable?) {
        SimpleTask.run(lifecycle, {
            mmsSmsDb.getMessagePositionInConversation(viewModel.threadId, timestamp, author)
        }) { p: Int -> moveToMessagePosition(p, onMessageNotFound) }
    }

    private fun moveToMessagePosition(position: Int, onMessageNotFound: Runnable?) {
        if (position >= 0) {
            binding.conversationRecyclerView.scrollToPosition(position)
        } else {
            onMessageNotFound?.run()
        }
    }
    // endregion
}