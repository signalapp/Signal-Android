package org.thoughtcrime.securesms.conversation.v2

import android.Manifest
import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.*
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.DimenRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.annimon.stream.Stream
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import kotlinx.android.synthetic.main.activity_conversation_v2.view.*
import kotlinx.android.synthetic.main.activity_conversation_v2_action_bar.*
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.view_conversation.view.*
import kotlinx.android.synthetic.main.view_input_bar.view.*
import kotlinx.android.synthetic.main.view_input_bar_recording.*
import kotlinx.android.synthetic.main.view_input_bar_recording.view.*
import kotlinx.android.synthetic.main.view_visible_message.view.*
import network.loki.messenger.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.mentions.Mention
import org.session.libsession.messaging.mentions.MentionsManager
import org.session.libsession.messaging.messages.control.DataExtractionNotification
import org.session.libsession.messaging.messages.control.UnsendRequest
import org.session.libsession.messaging.messages.signal.OutgoingMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.messages.visible.OpenGroupInvitation
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.MediaTypes
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.concurrent.SimpleTask
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.recipients.RecipientModifiedListener
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.ListenableFuture
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.utilities.hexEncodedPrivateKey
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.audio.AudioRecorder
import org.thoughtcrime.securesms.contacts.SelectContactsActivity
import org.thoughtcrime.securesms.contacts.SelectContactsActivity.Companion.selectedContactsKey
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher
import org.thoughtcrime.securesms.conversation.v2.dialogs.*
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarButton
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarRecordingViewDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.mentions.MentionCandidatesView
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationActionModeCallback
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationActionModeCallbackDelegate
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationMenuHelper
import org.thoughtcrime.securesms.conversation.v2.messages.*
import org.thoughtcrime.securesms.conversation.v2.search.SearchBottomBar
import org.thoughtcrime.securesms.conversation.v2.search.SearchViewModel
import org.thoughtcrime.securesms.conversation.v2.utilities.*
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.database.DraftDatabase.Drafts
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.giph.ui.GiphyActivity
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel.LinkPreviewState
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivity
import org.thoughtcrime.securesms.mms.*
import org.thoughtcrime.securesms.notifications.MarkReadReceiver
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.util.*
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.*

// Some things that seemingly belong to the input bar (e.g. the voice message recording UI) are actually
// part of the conversation activity layout. This is just because it makes the layout a lot simpler. The
// price we pay is a bit of back and forth between the input bar and the conversation activity.

class ConversationActivityV2 : PassphraseRequiredActionBarActivity(), InputBarDelegate,
        InputBarRecordingViewDelegate, AttachmentManager.AttachmentListener, ActivityDispatcher,
        ConversationActionModeCallbackDelegate, VisibleMessageContentViewDelegate, RecipientModifiedListener,
        SearchBottomBar.EventListener, VoiceMessageViewDelegate {
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private var linkPreviewViewModel: LinkPreviewViewModel? = null
    private var threadID: Long = -1
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
    var searchViewModel: SearchViewModel? = null
    var searchViewItem: MenuItem? = null

    private val isScrolledToBottom: Boolean
        get() {
            val position = layoutManager.findFirstCompletelyVisibleItemPosition()
            return position == 0
        }

    private val layoutManager: LinearLayoutManager
        get() { return conversationRecyclerView.layoutManager as LinearLayoutManager }

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
        val cursor = DatabaseFactory.getMmsSmsDatabase(this).getConversation(threadID)
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
            glide
        )
        adapter.visibleMessageContentViewDelegate = this
        adapter
    }

    private val thread by lazy {
        DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadID)!!
    }

    private val glide by lazy { GlideApp.with(this) }
    private val lockViewHitMargin by lazy { toPx(40, resources) }
    private val gifButton by lazy { InputBarButton(this, R.drawable.ic_gif_white_24dp, hasOpaqueBackground = true, isGIFButton = true) }
    private val documentButton by lazy { InputBarButton(this, R.drawable.ic_document_small_dark, hasOpaqueBackground = true) }
    private val libraryButton by lazy { InputBarButton(this, R.drawable.ic_baseline_photo_library_24, hasOpaqueBackground = true) }
    private val cameraButton by lazy { InputBarButton(this, R.drawable.ic_baseline_photo_camera_24, hasOpaqueBackground = true) }

    // region Settings
    companion object {
        // Extras
        const val THREAD_ID = "thread_id"
        const val ADDRESS = "address"
        // Request codes
        const val PICK_DOCUMENT = 2
        const val TAKE_PHOTO = 7
        const val PICK_GIF = 10
        const val PICK_FROM_LIBRARY = 12
        const val INVITE_CONTACTS = 124

        //flag
        const val IS_UNSEND_REQUESTS_ENABLED = false
    }
    // endregion

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_conversation_v2)
        var threadID = intent.getLongExtra(THREAD_ID, -1L)
        if (threadID == -1L) {
            val address = intent.getParcelableExtra<Address>(ADDRESS) ?: return finish()
            val recipient = Recipient.from(this, address, false)
            threadID = DatabaseFactory.getThreadDatabase(this).getOrCreateThreadIdFor(recipient)
        }
        this.threadID = threadID
        val thread = DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadID)
        if (thread == null) {
            Toast.makeText(this, "This thread has been deleted.", Toast.LENGTH_LONG).show()
            return finish()
        }
        setUpRecyclerView()
        setUpToolBar()
        setUpInputBar()
        setUpLinkPreviewObserver()
        restoreDraftIfNeeded()
        addOpenGroupGuidelinesIfNeeded()
        scrollToBottomButton.setOnClickListener {
            val layoutManager = conversationRecyclerView.layoutManager ?: return@setOnClickListener
            if (layoutManager.isSmoothScrolling) {
                conversationRecyclerView.scrollToPosition(0)
            } else {
                conversationRecyclerView.smoothScrollToPosition(0)
            }
        }
        unreadCount = DatabaseFactory.getMmsSmsDatabase(this).getUnreadCount(threadID)
        updateUnreadCountIndicator()
        setUpTypingObserver()
        setUpRecipientObserver()
        updateSubtitle()
        getLatestOpenGroupInfoIfNeeded()
        setUpBlockedBanner()
        searchBottomBar.setEventListener(this)
        setUpSearchResultObserver()
        scrollToFirstUnreadMessageIfNeeded()
        showOrHideInputIfNeeded()
        if (this.thread.isOpenGroupRecipient) {
            val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID)
            if (openGroup == null) {
                Toast.makeText(this, "This thread has been deleted.", Toast.LENGTH_LONG).show()
                return finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ApplicationContext.getInstance(this).messageNotifier.setVisibleThread(threadID)
        markAllAsRead()
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
        conversationRecyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, true)
        conversationRecyclerView.layoutManager = layoutManager
        // Workaround for the fact that CursorRecyclerViewAdapter doesn't auto-update automatically (even though it says it will)
        LoaderManager.getInstance(this).restartLoader(0, null, object : LoaderManager.LoaderCallbacks<Cursor> {

            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<Cursor> {
                return ConversationLoader(threadID, this@ConversationActivityV2)
            }

            override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
                adapter.changeCursor(cursor)
            }

            override fun onLoaderReset(cursor: Loader<Cursor>) {
                adapter.changeCursor(null)
            }
        })
        conversationRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                handleRecyclerViewScrolled()
            }
        })
    }

    private fun setUpToolBar() {
        val actionBar = supportActionBar!!
        actionBar.setCustomView(R.layout.activity_conversation_v2_action_bar)
        actionBar.setDisplayShowCustomEnabled(true)
        conversationTitleView.text = thread.toShortString()
        @DimenRes val sizeID: Int
        if (thread.isClosedGroupRecipient) {
            sizeID = R.dimen.medium_profile_picture_size
        } else {
            sizeID = R.dimen.small_profile_picture_size
        }
        val size = resources.getDimension(sizeID).roundToInt()
        profilePictureView.layoutParams = LinearLayout.LayoutParams(size, size)
        profilePictureView.glide = glide
        MentionManagerUtilities.populateUserPublicKeyCacheIfNeeded(threadID, this)
        profilePictureView.update(thread, threadID)
    }

    private fun setUpInputBar() {
        inputBar.delegate = this
        inputBarRecordingView.delegate = this
        // GIF button
        gifButtonContainer.addView(gifButton)
        gifButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        gifButton.onUp = { showGIFPicker() }
        gifButton.snIsEnabled = false
        // Document button
        documentButtonContainer.addView(documentButton)
        documentButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        documentButton.onUp = { showDocumentPicker() }
        documentButton.snIsEnabled = false
        // Library button
        libraryButtonContainer.addView(libraryButton)
        libraryButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        libraryButton.onUp = { pickFromLibrary() }
        libraryButton.snIsEnabled = false
        // Camera button
        cameraButtonContainer.addView(cameraButton)
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
                startActivityForResult(MediaSendActivity.buildEditorIntent(this, listOf( media ), thread, ""), ConversationActivityV2.PICK_FROM_LIBRARY)
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
            inputBar.text = dataTextExtra.toString()
        } else {
            val draftDB = DatabaseFactory.getDraftDatabase(this)
            val drafts = draftDB.getDrafts(threadID)
            draftDB.clearDrafts(threadID)
            val text = drafts.find { it.type == DraftDatabase.Draft.TEXT }?.value ?: return
            inputBar.text = text
        }
    }

    private fun addOpenGroupGuidelinesIfNeeded() {
        val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID) ?: return
        val isOxenHostedOpenGroup = openGroup.room == "session" || openGroup.room == "oxen"
            || openGroup.room == "lokinet" || openGroup.room == "crypto"
        if (!isOxenHostedOpenGroup) { return }
        openGroupGuidelinesView.visibility = View.VISIBLE
        val recyclerViewLayoutParams = conversationRecyclerView.layoutParams as RelativeLayout.LayoutParams
        recyclerViewLayoutParams.topMargin = toPx(57, resources) // The height of the open group guidelines view is hardcoded to this
        conversationRecyclerView.layoutParams = recyclerViewLayoutParams
    }

    private fun setUpTypingObserver() {
        ApplicationContext.getInstance(this).typingStatusRepository.getTypists(threadID).observe(this) { state ->
            val recipients = if (state != null) state.typists else listOf()
            // FIXME: Also checking isScrolledToBottom is a quick fix for an issue where the
            //        typing indicator overlays the recycler view when scrolled up
            typingIndicatorViewContainer.isVisible = recipients.isNotEmpty() && isScrolledToBottom
            typingIndicatorViewContainer.setTypists(recipients)
            inputBarHeightChanged(inputBar.height)
        }
        if (TextSecurePreferences.isTypingIndicatorsEnabled(this)) {
            inputBar.inputBarEditText.addTextChangedListener(object : SimpleTextWatcher() {

                override fun onTextChanged(text: String?) {
                    ApplicationContext.getInstance(this@ConversationActivityV2).typingStatusSender.onTypingStarted(threadID)
                }
            })
        }
    }

    private fun setUpRecipientObserver() {
        thread.addListener(this)
    }

    private fun getLatestOpenGroupInfoIfNeeded() {
        val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID) ?: return
        OpenGroupAPIV2.getMemberCount(openGroup.room, openGroup.server).successUi { updateSubtitle() }
    }

    private fun setUpBlockedBanner() {
        if (thread.isGroupRecipient) { return }
        val contactDB = DatabaseFactory.getSessionContactDatabase(this)
        val sessionID = thread.address.toString()
        val contact = contactDB.getContactWithSessionID(sessionID)
        val name = contact?.displayName(Contact.ContactContext.REGULAR) ?: sessionID
        blockedBannerTextView.text = resources.getString(R.string.activity_conversation_blocked_banner_text, name)
        blockedBanner.isVisible = thread.isBlocked
        blockedBanner.setOnClickListener { unblock() }
    }

    private fun setUpLinkPreviewObserver() {
        val linkPreviewViewModel = ViewModelProviders.of(this, LinkPreviewViewModel.Factory(LinkPreviewRepository(this)))[LinkPreviewViewModel::class.java]
        this.linkPreviewViewModel = linkPreviewViewModel
        if (!TextSecurePreferences.isLinkPreviewsEnabled(this)) {
            linkPreviewViewModel.onUserCancel(); return
        }
        linkPreviewViewModel.linkPreviewState.observe(this, { previewState: LinkPreviewState? ->
            if (previewState == null) return@observe
            if (previewState.isLoading) {
                inputBar.draftLinkPreview()
            } else if (previewState.linkPreview.isPresent) {
                inputBar.updateLinkPreviewDraft(glide, previewState.linkPreview.get())
            } else {
                inputBar.cancelLinkPreviewDraft()
            }
        })
    }

    private fun scrollToFirstUnreadMessageIfNeeded() {
        val lastSeenTimestamp = DatabaseFactory.getThreadDatabase(this).getLastSeenAndHasSent(threadID).first()
        val lastSeenItemPosition = adapter.findLastSeenItemPosition(lastSeenTimestamp) ?: return
        if (lastSeenItemPosition <= 3) { return }
        conversationRecyclerView.scrollToPosition(lastSeenItemPosition)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        ConversationMenuHelper.onPrepareOptionsMenu(menu, menuInflater, thread, threadID, this) { onOptionsItemSelected(it) }
        super.onPrepareOptionsMenu(menu)
        return true
    }

    override fun onDestroy() {
        saveDraft()
        super.onDestroy()
    }
    // endregion

    // region Animation & Updating
    override fun onModified(recipient: Recipient) {
        runOnUiThread {
            if (thread.isContactRecipient) {
                blockedBanner.isVisible = thread.isBlocked
            }
            updateSubtitle()
            showOrHideInputIfNeeded()
        }
    }

    private fun showOrHideInputIfNeeded() {
        if (thread.isClosedGroupRecipient) {
            val group = DatabaseFactory.getGroupDatabase(this).getGroup(thread.address.toGroupString()).orNull()
            val isActive = (group?.isActive == true)
            inputBar.showInput = isActive
        } else {
            inputBar.showInput = true
        }
    }

    private fun markAllAsRead() {
        val messages = DatabaseFactory.getThreadDatabase(this).setRead(threadID, true)
        if (thread.isGroupRecipient) {
            for (message in messages) {
                MarkReadReceiver.scheduleDeletion(this, message.expirationInfo)
            }
        } else {
            MarkReadReceiver.process(this, messages)
        }
        ApplicationContext.getInstance(this).messageNotifier.updateNotification(this, false, 0)
    }

    override fun inputBarHeightChanged(newValue: Int) {
        @Suppress("NAME_SHADOWING") val newValue = max(newValue, resources.getDimension(R.dimen.input_bar_height).roundToInt())
        // 36 DP is the exact height of the typing indicator view. It's also exactly 18 * 2, and 18 is the large message
        // corner radius. This makes 36 DP look "correct" in the context of other messages on the screen.
        val typingIndicatorHeight = if (typingIndicatorViewContainer.isVisible) toPx(36, resources) else 0
        // Recycler view
        val recyclerViewLayoutParams = conversationRecyclerView.layoutParams as RelativeLayout.LayoutParams
        recyclerViewLayoutParams.bottomMargin = newValue + typingIndicatorHeight
        conversationRecyclerView.layoutParams = recyclerViewLayoutParams
        // Additional content container
        val additionalContentContainerLayoutParams = additionalContentContainer.layoutParams as RelativeLayout.LayoutParams
        additionalContentContainerLayoutParams.bottomMargin = newValue
        additionalContentContainer.layoutParams = additionalContentContainerLayoutParams
        // Attachment options
        val attachmentButtonHeight = inputBar.attachmentsButtonContainer.height
        val bottomMargin = (newValue - inputBar.additionalContentHeight - attachmentButtonHeight) / 2
        val margin = toPx(8, resources)
        val attachmentOptionsContainerLayoutParams = attachmentOptionsContainer.layoutParams as RelativeLayout.LayoutParams
        attachmentOptionsContainerLayoutParams.bottomMargin = bottomMargin + attachmentButtonHeight + margin
        attachmentOptionsContainer.layoutParams = attachmentOptionsContainerLayoutParams
        // Scroll to bottom button
        val scrollToBottomButtonLayoutParams = scrollToBottomButton.layoutParams as RelativeLayout.LayoutParams
        scrollToBottomButtonLayoutParams.bottomMargin = newValue + additionalContentContainer.height + toPx(12, resources)
        scrollToBottomButton.layoutParams = scrollToBottomButtonLayoutParams
    }

    override fun inputBarEditTextContentChanged(newContent: CharSequence) {
        if (TextSecurePreferences.isLinkPreviewsEnabled(this)) {
            linkPreviewViewModel?.onTextChanged(this, inputBar.text, 0, 0)
        }
        showOrHideMentionCandidatesIfNeeded(newContent)
        if (LinkPreviewUtil.findWhitelistedUrls(newContent.toString()).isNotEmpty()
            && !TextSecurePreferences.isLinkPreviewsEnabled(this) && !TextSecurePreferences.hasSeenLinkPreviewSuggestionDialog(this)) {
            LinkPreviewDialog {
                setUpLinkPreviewObserver()
                linkPreviewViewModel?.onEnabled()
                linkPreviewViewModel?.onTextChanged(this, inputBar.text, 0, 0)
            }.show(supportFragmentManager, "Link Preview Dialog")
            TextSecurePreferences.setHasSeenLinkPreviewSuggestionDialog(this)
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
            additionalContentContainer.removeAllViews()
            val view = MentionCandidatesView(this)
            view.glide = glide
            view.onCandidateSelected = { handleMentionSelected(it) }
            additionalContentContainer.addView(view)
            val candidates = MentionsManager.getMentionCandidates(query, threadID, thread.isOpenGroupRecipient)
            this.mentionCandidatesView = view
            view.show(candidates, threadID)
            view.alpha = 0.0f
            val animation = ValueAnimator.ofObject(FloatEvaluator(), view.alpha, 1.0f)
            animation.duration = 250L
            animation.addUpdateListener { animator ->
                view.alpha = animator.animatedValue as Float
            }
            animation.start()
        } else {
            val candidates = MentionsManager.getMentionCandidates(query, threadID, thread.isOpenGroupRecipient)
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
                if (animator.animatedFraction == 1.0f) { additionalContentContainer.removeAllViews() }
            }
            animation.start()
        }
        isShowingMentionCandidatesView = false
    }

    override fun toggleAttachmentOptions() {
        val targetAlpha = if (isShowingAttachmentOptions) 0.0f else 1.0f
        val allButtonContainers = listOf( cameraButtonContainer, libraryButtonContainer, documentButtonContainer, gifButtonContainer)
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
        inputBarRecordingView.show()
        inputBar.alpha = 0.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            inputBar.alpha = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun expandVoiceMessageLockView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), lockView.scaleX, 1.10f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            lockView.scaleX = animator.animatedValue as Float
            lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun collapseVoiceMessageLockView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), lockView.scaleX, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            lockView.scaleX = animator.animatedValue as Float
            lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun hideVoiceMessageUI() {
        val chevronImageView = inputBarRecordingView.inputBarChevronImageView
        val slideToCancelTextView = inputBarRecordingView.inputBarSlideToCancelTextView
        listOf( chevronImageView, slideToCancelTextView ).forEach { view ->
            val animation = ValueAnimator.ofObject(FloatEvaluator(), view.translationX, 0.0f)
            animation.duration = 250L
            animation.addUpdateListener { animator ->
                view.translationX = animator.animatedValue as Float
            }
            animation.start()
        }
        inputBarRecordingView.hide()
    }

    override fun handleVoiceMessageUIHidden() {
        inputBar.alpha = 1.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            inputBar.alpha = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun handleRecyclerViewScrolled() {
        val alpha = if (!isScrolledToBottom) 1.0f else 0.0f
        // FIXME: Checking isScrolledToBottom is a quick fix for an issue where the
        //        typing indicator overlays the recycler view when scrolled up
        val wasTypingIndicatorVisibleBefore = typingIndicatorViewContainer.isVisible
        typingIndicatorViewContainer.isVisible = wasTypingIndicatorVisibleBefore && isScrolledToBottom
        val isTypingIndicatorVisibleAfter = typingIndicatorViewContainer.isVisible
        if (isTypingIndicatorVisibleAfter != wasTypingIndicatorVisibleBefore) {
            inputBarHeightChanged(inputBar.height)
        }
        scrollToBottomButton.alpha = alpha
        unreadCount = min(unreadCount, layoutManager.findFirstVisibleItemPosition())
        updateUnreadCountIndicator()
    }

    private fun updateUnreadCountIndicator() {
        val formattedUnreadCount = if (unreadCount < 100) unreadCount.toString() else "99+"
        unreadCountTextView.text = formattedUnreadCount
        val textSize = if (unreadCount < 100) 12.0f else 9.0f
        unreadCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
        unreadCountTextView.setTypeface(Typeface.DEFAULT, if (unreadCount < 100) Typeface.BOLD else Typeface.NORMAL)
        unreadCountIndicator.isVisible = (unreadCount != 0)
    }

    private fun updateSubtitle() {
        muteIconImageView.isVisible = thread.isMuted
        conversationSubtitleView.isVisible = true
        if (thread.isMuted) {
            if (thread.mutedUntil != Long.MAX_VALUE) {
                conversationSubtitleView.text = getString(R.string.ConversationActivity_muted_until_date, DateUtils.getFormattedDateTime(thread.mutedUntil, "EEE, MMM d, yyyy HH:mm", Locale.getDefault()))
            } else {
                conversationSubtitleView.text = getString(R.string.ConversationActivity_muted_forever)
            }
        } else if (thread.isGroupRecipient) {
            val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID)
            if (openGroup != null) {
                val userCount = DatabaseFactory.getLokiAPIDatabase(this).getUserCount(openGroup.room, openGroup.server) ?: 0
                conversationSubtitleView.text = getString(R.string.ConversationActivity_member_count, userCount)
            } else {
                conversationSubtitleView.isVisible = false
            }
        } else {
            conversationSubtitleView.isVisible = false
        }
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return false
        }
        return ConversationMenuHelper.onOptionItemSelected(this, item, thread)
    }

    // `position` is the adapter position; not the visual position
    private fun handlePress(message: MessageRecord, position: Int, view: VisibleMessageView, event: MotionEvent) {
        val actionMode = this.actionMode
        if (actionMode != null) {
            adapter.toggleSelection(message, position)
            val actionModeCallback = ConversationActionModeCallback(adapter, threadID, this)
            actionModeCallback.delegate = this
            actionModeCallback.updateActionModeMenu(actionMode.menu)
            if (adapter.selectedItems.isEmpty()) {
                actionMode.finish()
                this.actionMode = null
            }
        } else {
            // NOTE:
            // We have to use onContentClick (rather than a click listener directly on
            // the view) so as to not interfere with all the other gestures. Do not add
            // onClickListeners directly to message content views.
            view.onContentClick(event)
        }
    }

    // `position` is the adapter position; not the visual position
    private fun handleSwipeToReply(message: MessageRecord, position: Int) {
        inputBar.draftQuote(thread, message, glide)
    }

    // `position` is the adapter position; not the visual position
    private fun handleLongPress(message: MessageRecord, position: Int) {
        val actionMode = this.actionMode
        val actionModeCallback = ConversationActionModeCallback(adapter, threadID, this)
        actionModeCallback.delegate = this
        searchViewItem?.collapseActionView()
        if (actionMode == null) { // Nothing should be selected if this is the case
            adapter.toggleSelection(message, position)
            this.actionMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                startActionMode(actionModeCallback, ActionMode.TYPE_PRIMARY)
            } else {
                startActionMode(actionModeCallback)
            }
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
        val chevronImageView = inputBarRecordingView.inputBarChevronImageView
        val slideToCancelTextView = inputBarRecordingView.inputBarSlideToCancelTextView
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
            inputBarRecordingView.lock()
        } else {
            val recordButtonOverlay = inputBarRecordingView.recordButtonOverlay
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
        lockView.getLocationOnScreen(lockViewLocation)
        val hitRect = Rect(lockViewLocation[0] - lockViewHitMargin, 0,
            lockViewLocation[0] + lockView.width + lockViewHitMargin, lockViewLocation[1] + lockView.height)
        return hitRect.contains(x, y)
    }

    private fun unblock() {
        if (!thread.isContactRecipient) { return }
        DatabaseFactory.getRecipientDatabase(this).setBlocked(thread, false)
    }

    private fun handleMentionSelected(mention: Mention) {
        if (currentMentionStartIndex == -1) { return }
        mentions.add(mention)
        val previousText = inputBar.text
        val newText = previousText.substring(0, currentMentionStartIndex) + "@" + mention.displayName + " "
        inputBar.text = newText
        inputBar.inputBarEditText.setSelection(newText.length)
        currentMentionStartIndex = -1
        hideMentionCandidates()
        this.previousText = newText
    }

    override fun scrollToMessageIfPossible(timestamp: Long) {
        val lastSeenItemPosition = adapter.getItemPositionForTimestamp(timestamp) ?: return
        conversationRecyclerView.scrollToPosition(lastSeenItemPosition)
    }

    override fun playVoiceMessageAtIndexIfPossible(indexInAdapter: Int) {
        if (indexInAdapter < 0 || indexInAdapter >= adapter.itemCount) { return }
        val viewHolder = conversationRecyclerView.findViewHolderForAdapterPosition(indexInAdapter) as? ConversationAdapter.VisibleMessageViewHolder
        val nextVisibleMessageView = viewHolder?.view ?: return
        nextVisibleMessageView.messageContentView.mainContainer.children.forEach { view ->
            if (view is VoiceMessageView) {
                return@forEach view.togglePlayback()
            }
        }
    }

    override fun sendMessage() {
        if (thread.isContactRecipient && thread.isBlocked) {
            BlockedDialog(thread).show(supportFragmentManager, "Blocked Dialog")
            return
        }
        if (inputBar.linkPreview != null || inputBar.quote != null) {
            sendAttachments(listOf(), getMessageBody(), inputBar.quote, inputBar.linkPreview)
        } else {
            sendTextOnlyMessage()
        }
    }

    override fun commitInputContent(contentUri: Uri) {
        val media = Media(contentUri, MediaUtil.getMimeType(this, contentUri)!!, 0, 0, 0, 0, Optional.absent(), Optional.absent())
        startActivityForResult(MediaSendActivity.buildEditorIntent(this, listOf( media ), thread, getMessageBody()), ConversationActivityV2.PICK_FROM_LIBRARY)
    }

    private fun sendTextOnlyMessage(hasPermissionToSendSeed: Boolean = false) {
        val text = getMessageBody()
        val userPublicKey = TextSecurePreferences.getLocalNumber(this)
        val isNoteToSelf = (thread.isContactRecipient && thread.address.toString() == userPublicKey)
        if (text.contains(seed) && !isNoteToSelf && !hasPermissionToSendSeed) {
            val dialog = SendSeedDialog { sendTextOnlyMessage(true) }
            return dialog.show(supportFragmentManager, "Send Seed Dialog")
        }
        // Create the message
        val message = VisibleMessage()
        message.sentTimestamp = System.currentTimeMillis()
        message.text = text
        val outgoingTextMessage = OutgoingTextMessage.from(message, thread)
        // Clear the input bar
        inputBar.text = ""
        inputBar.cancelQuoteDraft()
        inputBar.cancelLinkPreviewDraft()
        // Clear mentions
        previousText = ""
        currentMentionStartIndex = -1
        mentions.clear()
        // Put the message in the database
        message.id = DatabaseFactory.getSmsDatabase(this).insertMessageOutbox(threadID, outgoingTextMessage, false, message.sentTimestamp!!) { }
        // Send it
        MessageSender.send(message, thread.address)
        // Send a typing stopped message
        ApplicationContext.getInstance(this).typingStatusSender.onTypingStopped(threadID)
    }

    private fun sendAttachments(attachments: List<Attachment>, body: String?, quotedMessage: MessageRecord? = null, linkPreview: LinkPreview? = null) {
        // Create the message
        val message = VisibleMessage()
        message.sentTimestamp = System.currentTimeMillis()
        message.text = body
        val quote = quotedMessage?.let {
            val quotedAttachments = (it as? MmsMessageRecord)?.slideDeck?.asAttachments() ?: listOf()
            val sender = if (it.isOutgoing) fromSerialized(TextSecurePreferences.getLocalNumber(this)!!) else it.individualRecipient.address
            QuoteModel(it.dateSent, sender, it.body, false, quotedAttachments)
        }
        val outgoingTextMessage = OutgoingMediaMessage.from(message, thread, attachments, quote, linkPreview)
        // Clear the input bar
        inputBar.text = ""
        inputBar.cancelQuoteDraft()
        inputBar.cancelLinkPreviewDraft()
        // Clear mentions
        previousText = ""
        currentMentionStartIndex = -1
        mentions.clear()
        // Reset the attachment manager
        attachmentManager.clear()
        // Reset attachments button if needed
        if (isShowingAttachmentOptions) { toggleAttachmentOptions() }
        // Put the message in the database
        message.id = DatabaseFactory.getMmsDatabase(this).insertMessageOutbox(outgoingTextMessage, threadID, false) { }
        // Send it
        MessageSender.send(message, thread.address, attachments, quote, linkPreview)
        // Send a typing stopped message
        ApplicationContext.getInstance(this).typingStatusSender.onTypingStopped(threadID)
    }

    private fun showGIFPicker() {
        val hasSeenGIFMetaDataWarning: Boolean = TextSecurePreferences.hasSeenGIFMetaDataWarning(this)
        if (!hasSeenGIFMetaDataWarning) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Search GIFs?")
            builder.setMessage("You will not have full metadata protection when sending GIFs.")
            builder.setPositiveButton("OK") { dialog: DialogInterface, which: Int ->
                TextSecurePreferences.setHasSeenGIFMetaDataWarning(this)
                AttachmentManager.selectGif(this, ConversationActivityV2.PICK_GIF)
                dialog.dismiss()
            }
            builder.setNegativeButton(
                "Cancel"
            ) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
            builder.create().show()
        } else {
            AttachmentManager.selectGif(this, ConversationActivityV2.PICK_GIF)
        }
    }

    private fun showDocumentPicker() {
        AttachmentManager.selectDocument(this, ConversationActivityV2.PICK_DOCUMENT)
    }

    private fun pickFromLibrary() {
        AttachmentManager.selectGallery(this, ConversationActivityV2.PICK_FROM_LIBRARY, thread, inputBar.text.trim())
    }

    private fun showCamera() {
        attachmentManager.capturePhoto(this, ConversationActivityV2.TAKE_PHOTO, thread);
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
                if (!thread.isOpenGroupRecipient) { return }
                val extras = intent?.extras ?: return
                if (!intent.hasExtra(SelectContactsActivity.selectedContactsKey)) { return }
                val selectedContacts = extras.getStringArray(selectedContactsKey)!!
                val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID)
                for (contact in selectedContacts) {
                    val recipient = Recipient.from(this, fromSerialized(contact), true)
                    val message = VisibleMessage()
                    message.sentTimestamp = System.currentTimeMillis()
                    val openGroupInvitation = OpenGroupInvitation()
                    openGroupInvitation.name = openGroup!!.name
                    openGroupInvitation.url = openGroup!!.joinURL
                    message.openGroupInvitation = openGroupInvitation
                    val outgoingTextMessage = OutgoingTextMessage.fromOpenGroupInvitation(openGroupInvitation, recipient, message.sentTimestamp)
                    DatabaseFactory.getSmsDatabase(this).insertMessageOutbox(-1, outgoingTextMessage, message.sentTimestamp!!)
                    MessageSender.send(message, recipient.address)
                }
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

    private fun buildUnsendRequest(message: MessageRecord): UnsendRequest? {
        if (this.thread.isOpenGroupRecipient) return null
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        messageDataProvider.getServerHashForMessage(message.id) ?: return null
        val unsendRequest = UnsendRequest()
        if (message.isOutgoing) {
            unsendRequest.author = TextSecurePreferences.getLocalNumber(this)
        } else {
            unsendRequest.author = message.individualRecipient.address.contactIdentifier()
        }
        unsendRequest.timestamp = message.timestamp

        return unsendRequest
    }

    private fun deleteLocally(message: MessageRecord) {
        buildUnsendRequest(message)?.let { unsendRequest ->
            TextSecurePreferences.getLocalNumber(this@ConversationActivityV2)?.let {
                MessageSender.send(unsendRequest, Address.fromSerialized(it))
            }
        }
        MessagingModuleConfiguration.shared.messageDataProvider.deleteMessage(message.id, !message.isMms)
    }

    private fun deleteForEveryone(message: MessageRecord) {
        buildUnsendRequest(message)?.let { unsendRequest ->
            MessageSender.send(unsendRequest, thread.address)
        }
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val messageDB = DatabaseFactory.getLokiMessageDatabase(this@ConversationActivityV2)
        val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID)
        if (openGroup != null) {
            messageDB.getServerID(message.id, !message.isMms)?.let { messageServerID ->
                OpenGroupAPIV2.deleteMessage(messageServerID, openGroup.room, openGroup.server)
                    .success {
                        messageDataProvider.deleteMessage(message.id, !message.isMms)
                    }.failUi { error ->
                        Toast.makeText(this@ConversationActivityV2, "Couldn't delete message due to error: $error", Toast.LENGTH_LONG).show()
                    }
            }
        } else {
            messageDataProvider.deleteMessage(message.id, !message.isMms)
            messageDataProvider.getServerHashForMessage(message.id)?.let { serverHash ->
                var publicKey = thread.address.serialize()
                if (thread.isClosedGroupRecipient) { publicKey = GroupUtil.doubleDecodeGroupID(publicKey).toHexString() }
                SnodeAPI.deleteMessage(publicKey, listOf(serverHash))
                    .failUi { error ->
                        Toast.makeText(this@ConversationActivityV2, "Couldn't delete message due to error: $error", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    // Remove this after the unsend request is enabled
    fun deleteMessagesWithoutUnsendRequest(messages: Set<MessageRecord>) {
        val messageCount = messages.size
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val messageDB = DatabaseFactory.getLokiMessageDatabase(this@ConversationActivityV2)
        val builder = AlertDialog.Builder(this)
        builder.setTitle(resources.getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messageCount, messageCount))
        builder.setMessage(resources.getQuantityString(R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages, messageCount, messageCount))
        builder.setCancelable(true)
        val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID)
        builder.setPositiveButton(R.string.delete) { _, _ ->
            if (openGroup != null) {
                val messageServerIDs = mutableMapOf<Long, MessageRecord>()
                for (message in messages) {
                    val messageServerID = messageDB.getServerID(message.id, !message.isMms) ?: continue
                    messageServerIDs[messageServerID] = message
                }
                for ((messageServerID, message) in messageServerIDs) {
                    OpenGroupAPIV2.deleteMessage(messageServerID, openGroup.room, openGroup.server)
                        .success {
                            messageDataProvider.deleteMessage(message.id, !message.isMms)
                        }.failUi { error ->
                            Toast.makeText(this@ConversationActivityV2, "Couldn't delete message due to error: $error", Toast.LENGTH_LONG).show()
                        }
                }
            } else {
                for (message in messages) {
                    if (message.isMms) {
                        DatabaseFactory.getMmsDatabase(this@ConversationActivityV2).deleteMessage(message.id)
                    } else {
                        DatabaseFactory.getSmsDatabase(this@ConversationActivityV2).deleteMessage(message.id)
                    }
                }
            }
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
        val allHasHash = messages.all { DatabaseFactory.getLokiMessageDatabase(this@ConversationActivityV2).getMessageServerHash(it.id) != null }
        if (thread.isOpenGroupRecipient) {
            val messageCount = messages.size
            val builder = AlertDialog.Builder(this)
            builder.setTitle(resources.getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messageCount, messageCount))
            builder.setMessage(resources.getQuantityString(R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages, messageCount, messageCount))
            builder.setCancelable(true)
            builder.setPositiveButton(R.string.delete) { _, _ ->
                for (message in messages) {
                    this.deleteForEveryone(message)
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
            bottomSheet.recipient = thread
            bottomSheet.onDeleteForMeTapped = {
                for (message in messages) {
                    this.deleteLocally(message)
                }
                bottomSheet.dismiss()
                endActionMode()
            }
            bottomSheet.onDeleteForEveryoneTapped = {
                for (message in messages) {
                    this.deleteForEveryone(message)
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
                    this.deleteLocally(message)
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
        val sessionID = messages.first().individualRecipient.address.toString()
        builder.setTitle(R.string.ConversationFragment_ban_selected_user)
        builder.setMessage("This will ban the selected user from this room. It won't ban them from other rooms.")
        builder.setCancelable(true)
        val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID)!!
        builder.setPositiveButton(R.string.ban) { _, _ ->
            OpenGroupAPIV2.ban(sessionID, openGroup.room, openGroup.server).successUi {
                Toast.makeText(this@ConversationActivityV2, "Successfully banned user", Toast.LENGTH_LONG).show()
            }.failUi { error ->
                Toast.makeText(this@ConversationActivityV2, "Couldn't ban user due to error: $error", Toast.LENGTH_LONG).show()
            }
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
        val sessionID = messages.first().individualRecipient.address.toString()
        builder.setTitle(R.string.ConversationFragment_ban_selected_user)
        builder.setMessage("This will ban the selected user from this room and delete all messages sent by them. It won't ban them from other rooms or delete the messages they sent there.")
        builder.setCancelable(true)
        val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID)!!
        builder.setPositiveButton(R.string.ban) { _, _ ->
            OpenGroupAPIV2.banAndDeleteAll(sessionID, openGroup.room, openGroup.server).successUi {
                Toast.makeText(this@ConversationActivityV2, "Successfully banned user and deleted all their messages", Toast.LENGTH_LONG).show()
            }.failUi { error ->
                Toast.makeText(this@ConversationActivityV2, "Couldn't execute request due to error: $error", Toast.LENGTH_LONG).show()
            }
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
            val body = MentionUtilities.highlightMentions(message.body, threadID, this)
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
        messages.forEach { messageRecord ->
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
        inputBar.draftQuote(thread, messages.first(), glide)
        endActionMode()
    }

    private fun sendMediaSavedNotification() {
        if (thread.isGroupRecipient) { return }
        val timestamp = System.currentTimeMillis()
        val kind = DataExtractionNotification.Kind.MediaSaved(timestamp)
        val message = DataExtractionNotification(kind)
        MessageSender.send(message, thread.address)
    }

    private fun endActionMode() {
        actionMode?.finish()
        actionMode = null
    }
    // endregion

    // region General
    private fun getMessageBody(): String {
        var result = inputBar.inputBarEditText.text?.trim() ?: ""
        for (mention in mentions) {
            try {
                val startIndex = result.indexOf("@" + mention.displayName)
                val endIndex = startIndex + mention.displayName.count() + 1 // + 1 to include the "@"
                result = result.substring(0, startIndex) + "@" + mention.publicKey + result.substring(endIndex)
            } catch (exception: Exception) {
                Log.d("Loki", "Failed to process mention due to error: $exception")
            }
        }
        return result.toString()
    }

    private fun saveDraft() {
        val text = inputBar?.text?.trim() ?: return
        if (text.isEmpty()) { return }
        val drafts = Drafts()
        drafts.add(DraftDatabase.Draft(DraftDatabase.Draft.TEXT, text))
        val draftDB = DatabaseFactory.getDraftDatabase(this)
        draftDB.insertDrafts(threadID, drafts)
    }
    // endregion

    // region Search
    private fun setUpSearchResultObserver() {
        val searchViewModel = ViewModelProvider(this).get(SearchViewModel::class.java)
        this.searchViewModel = searchViewModel
        searchViewModel.searchResults.observe(this, Observer { result: SearchViewModel.SearchResult? ->
            if (result == null) return@Observer
            if (result.getResults().isNotEmpty()) {
                result.getResults()[result.position]?.let {
                    jumpToMessage(it.messageRecipient.address, it.receivedTimestampMs, Runnable { searchViewModel.onMissingResult() })
                }
            }
            this.searchBottomBar.setData(result.position, result.getResults().size)
        })
    }

    fun onSearchQueryUpdated(query: String?) {
        adapter.onSearchQueryUpdated(query)
    }

    override fun onSearchMoveUpPressed() {
        this.searchViewModel?.onMoveUp()
    }

    override fun onSearchMoveDownPressed() {
        this.searchViewModel?.onMoveDown()
    }

    private fun jumpToMessage(author: Address, timestamp: Long, onMessageNotFound: Runnable?) {
        SimpleTask.run(lifecycle, {
            DatabaseFactory.getMmsSmsDatabase(this).getMessagePositionInConversation(threadID, timestamp, author)
        }) { p: Int -> moveToMessagePosition(p, onMessageNotFound) }
    }

    private fun moveToMessagePosition(position: Int, onMessageNotFound: Runnable?) {
        if (position >= 0) {
            conversationRecyclerView.scrollToPosition(position)
        } else {
            onMessageNotFound?.run()
        }
    }
    // endregion
}