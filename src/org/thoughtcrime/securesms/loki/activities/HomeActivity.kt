package org.thoughtcrime.securesms.loki.activities

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_home.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.jobs.MultiDeviceBlockedUpdateJob
import org.thoughtcrime.securesms.loki.api.PrepareAttachmentAudioExtrasJob
import org.thoughtcrime.securesms.loki.dialogs.ConversationOptionsBottomSheet
import org.thoughtcrime.securesms.loki.dialogs.LightThemeFeatureIntroBottomSheet
import org.thoughtcrime.securesms.loki.dialogs.MultiDeviceRemovalBottomSheet
import org.thoughtcrime.securesms.loki.dialogs.UserDetailsBottomSheet
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupsProtocol
import org.thoughtcrime.securesms.loki.protocol.SessionResetImplementation
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.loki.views.ConversationView
import org.thoughtcrime.securesms.loki.views.NewConversationButtonSetViewDelegate
import org.thoughtcrime.securesms.loki.views.SeedReminderViewDelegate
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.loki.api.fileserver.FileServerAPI
import org.whispersystems.signalservice.loki.protocol.mentions.MentionsManager
import org.whispersystems.signalservice.loki.protocol.meta.SessionMetaProtocol
import org.whispersystems.signalservice.loki.protocol.sessionmanagement.SessionManagementProtocol
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.MultiDeviceProtocol
import org.whispersystems.signalservice.loki.protocol.shelved.syncmessages.SyncMessagesProtocol
import org.whispersystems.signalservice.loki.utilities.toHexString
import java.io.IOException

class HomeActivity : PassphraseRequiredActionBarActivity, ConversationClickListener, SeedReminderViewDelegate, NewConversationButtonSetViewDelegate {
    private lateinit var glide: GlideRequests
    private var broadcastReceiver: BroadcastReceiver? = null

    private val publicKey: String
        get() {
            val masterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(this)
            val userPublicKey = TextSecurePreferences.getLocalNumber(this)
            return masterPublicKey ?: userPublicKey
        }

    // region Lifecycle
    constructor() : super()

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Process any outstanding deletes
        val threadDatabase = DatabaseFactory.getThreadDatabase(this)
        val archivedConversationCount = threadDatabase.archivedConversationListCount
        if (archivedConversationCount > 0) {
            val archivedConversations = threadDatabase.archivedConversationList
            archivedConversations.moveToFirst()
            fun deleteThreadAtCurrentPosition() {
                val threadID = archivedConversations.getLong(archivedConversations.getColumnIndex(ThreadDatabase.ID))
                AsyncTask.execute {
                    threadDatabase.deleteConversation(threadID)
                    (applicationContext as ApplicationContext).messageNotifier.updateNotification(this)
                }
            }
            deleteThreadAtCurrentPosition()
            while (archivedConversations.moveToNext()) {
                deleteThreadAtCurrentPosition()
            }
        }
        // Double check that the long poller is up
        (applicationContext as ApplicationContext).startPollingIfNeeded()
        // Set content view
        setContentView(R.layout.activity_home)
        // Set custom toolbar
        setSupportActionBar(toolbar)
        // Set up Glide
        glide = GlideApp.with(this)
        // Set up toolbar buttons
        profileButton.glide = glide
        profileButton.publicKey = publicKey
        profileButton.displayName = TextSecurePreferences.getProfileName(this)
        profileButton.update()
        profileButton.setOnClickListener { openSettings() }
        pathStatusViewContainer.disableClipping()
        pathStatusViewContainer.setOnClickListener { showPath() }
        // Set up seed reminder view
        val isMasterDevice = (TextSecurePreferences.getMasterHexEncodedPublicKey(this) == null)
        val hasViewedSeed = TextSecurePreferences.getHasViewedSeed(this)
        if (!hasViewedSeed && isMasterDevice) {
            val seedReminderViewTitle = SpannableString("You're almost finished! 80%") // Intentionally not yet translated
            seedReminderViewTitle.setSpan(ForegroundColorSpan(resources.getColorWithID(R.color.accent, theme)), 24, 27, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            seedReminderView.title = seedReminderViewTitle
            seedReminderView.subtitle = resources.getString(R.string.view_seed_reminder_subtitle_1)
            seedReminderView.setProgress(80, false)
            seedReminderView.delegate = this
        } else {
            seedReminderView.visibility = View.GONE
        }
        // Set up recycler view
        val cursor = DatabaseFactory.getThreadDatabase(this).conversationList
        val homeAdapter = HomeAdapter(this, cursor)
        homeAdapter.glide = glide
        homeAdapter.conversationClickListener = this
        recyclerView.adapter = homeAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        // Set up empty state view
        createNewPrivateChatButton.setOnClickListener { createNewPrivateChat() }
        // This is a workaround for the fact that CursorRecyclerViewAdapter doesn't actually auto-update (even though it says it will)
        LoaderManager.getInstance(this).restartLoader(0, null, object : LoaderManager.LoaderCallbacks<Cursor> {

            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<Cursor> {
                return HomeLoader(this@HomeActivity)
            }

            override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
                homeAdapter.changeCursor(cursor)
                updateEmptyState()
            }

            override fun onLoaderReset(cursor: Loader<Cursor>) {
                homeAdapter.changeCursor(null)
            }
        })
        // Set up gradient view
        val gradientViewLayoutParams = gradientView.layoutParams as RelativeLayout.LayoutParams
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        gradientViewLayoutParams.topMargin = (0.15 * height.toFloat()).toInt()
        // Set up new conversation button set
        newConversationButtonSet.delegate = this
        // Set up typing observer
        ApplicationContext.getInstance(this).typingStatusRepository.typingThreads.observe(this, Observer<Set<Long>> { threadIDs ->
            val adapter = recyclerView.adapter as HomeAdapter
            adapter.typingThreadIDs = threadIDs ?: setOf()
        })
        // Set up remaining components if needed
        val application = ApplicationContext.getInstance(this)
        val apiDB = DatabaseFactory.getLokiAPIDatabase(this)
        val threadDB = DatabaseFactory.getLokiThreadDatabase(this)
        val userDB = DatabaseFactory.getLokiUserDatabase(this)
        val sskDatabase = DatabaseFactory.getSSKDatabase(this)
        val userPublicKey = TextSecurePreferences.getLocalNumber(this)
        val sessionResetImpl = SessionResetImplementation(this)
        if (userPublicKey != null) {
            MentionsManager.configureIfNeeded(userPublicKey, threadDB, userDB)
            SessionMetaProtocol.configureIfNeeded(apiDB, userPublicKey)
            SyncMessagesProtocol.configureIfNeeded(apiDB, userPublicKey)
            application.publicChatManager.startPollersIfNeeded()
        }
        SessionManagementProtocol.configureIfNeeded(sessionResetImpl, sskDatabase, application)
        MultiDeviceProtocol.configureIfNeeded(apiDB)
        IP2Country.configureIfNeeded(this)
        application.registerForFCMIfNeeded(false)
        // Preload device links to make message sending quicker
        val publicKeys = ContactUtilities.getAllContacts(this).filter { contact ->
            !contact.recipient.isGroupRecipient && !contact.isOurDevice && !contact.isSlave
        }.map {
            it.recipient.address.toPhoneString()
        }.toSet()
        FileServerAPI.shared.getDeviceLinks(publicKeys)
        // Observe blocked contacts changed events
        val broadcastReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                recyclerView.adapter!!.notifyDataSetChanged()
            }
        }
        this.broadcastReceiver = broadcastReceiver
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter("blockedContactsChanged"))
        // Clear all data if this is a secondary device
        if (TextSecurePreferences.getMasterHexEncodedPublicKey(this) != null) {
            TextSecurePreferences.setWasUnlinked(this, true)
            ApplicationContext.getInstance(this).clearData()
        }
    }

    override fun onResume() {
        super.onResume()
        if (TextSecurePreferences.getLocalNumber(this) == null) { return; } // This can be the case after a secondary device is auto-cleared
        profileButton.update()
        val isMasterDevice = (TextSecurePreferences.getMasterHexEncodedPublicKey(this) == null)
        val hasViewedSeed = TextSecurePreferences.getHasViewedSeed(this)
        if (hasViewedSeed || !isMasterDevice) {
            seedReminderView.visibility = View.GONE
        }

        // Multi device removal sheet
        if (!TextSecurePreferences.getHasSeenMultiDeviceRemovalSheet(this)) {
            TextSecurePreferences.setHasSeenMultiDeviceRemovalSheet(this)
            val userPublicKey = TextSecurePreferences.getLocalNumber(this)
            val deviceLinks = DatabaseFactory.getLokiAPIDatabase(this).getDeviceLinks(userPublicKey)
            if (deviceLinks.isNotEmpty()) {
                val bottomSheet = MultiDeviceRemovalBottomSheet()
                bottomSheet.onOKTapped = {
                    bottomSheet.dismiss()
                }
                bottomSheet.onLinkTapped = {
                    bottomSheet.dismiss()
                    val url = "https://getsession.org/faq"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
                return
            }
        }

        // Light theme introduction sheet
        if (!TextSecurePreferences.hasSeenLightThemeIntroSheet(this) &&
                UiModeUtilities.isDayUiMode(this)) {
            TextSecurePreferences.setHasSeenLightThemeIntroSheet(this)
            val bottomSheet = LightThemeFeatureIntroBottomSheet()
            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
            return
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == CreateClosedGroupActivity.closedGroupCreatedResultCode) {
            createNewPrivateChat()
        }
    }

    override fun onDestroy() {
        val broadcastReceiver = this.broadcastReceiver
        if (broadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        }
        super.onDestroy()
    }
    // endregion

    // region Updating
    private fun updateEmptyState() {
        val threadCount = (recyclerView.adapter as HomeAdapter).itemCount
        emptyStateContainer.visibility = if (threadCount == 0) View.VISIBLE else View.GONE
    }
    // endregion

    // region Interaction
    override fun handleSeedReminderViewContinueButtonTapped() {
        val intent = Intent(this, SeedActivity::class.java)
        show(intent)
    }

    override fun onConversationClick(view: ConversationView) {
        val thread = view.thread ?: return
        openConversation(thread)
    }

    override fun onLongConversationClick(view: ConversationView) {
        val thread = view.thread ?: return
        val bottomSheet = ConversationOptionsBottomSheet()
        bottomSheet.recipient = thread.recipient
        bottomSheet.onViewDetailsTapped = {
            bottomSheet.dismiss()
            val userDetailsBottomSheet = UserDetailsBottomSheet()
            val bundle = Bundle()
            bundle.putString("publicKey", thread.recipient.address.toPhoneString())
            userDetailsBottomSheet.arguments = bundle
            userDetailsBottomSheet.show(supportFragmentManager, userDetailsBottomSheet.tag)
        }
        bottomSheet.onBlockTapped = {
            bottomSheet.dismiss()
            if (!thread.recipient.isBlocked) {
                blockConversation(thread)
            }
        }
        bottomSheet.onUnblockTapped = {
            bottomSheet.dismiss()
            if (thread.recipient.isBlocked) {
                unblockConversation(thread)
            }
        }
        bottomSheet.onDeleteTapped = {
            bottomSheet.dismiss()
            deleteConversation(thread)
        }
        bottomSheet.show(supportFragmentManager, bottomSheet.tag)
    }

    private fun blockConversation(thread: ThreadRecord) {
        AlertDialog.Builder(this)
            .setTitle(R.string.RecipientPreferenceActivity_block_this_contact_question)
            .setMessage(R.string.RecipientPreferenceActivity_you_will_no_longer_receive_messages_and_calls_from_this_contact)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.RecipientPreferenceActivity_block) { dialog, _ ->
                Thread {
                    DatabaseFactory.getRecipientDatabase(this).setBlocked(thread.recipient, true)
                    ApplicationContext.getInstance(this).jobManager.add(MultiDeviceBlockedUpdateJob())
                    Util.runOnMain {
                        recyclerView.adapter!!.notifyDataSetChanged()
                        dialog.dismiss()
                    }
                }.start()
            }.show()
    }

    private fun unblockConversation(thread: ThreadRecord) {
        AlertDialog.Builder(this)
            .setTitle(R.string.RecipientPreferenceActivity_unblock_this_contact_question)
            .setMessage(R.string.RecipientPreferenceActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.RecipientPreferenceActivity_unblock) { dialog, _ ->
                Thread {
                    DatabaseFactory.getRecipientDatabase(this).setBlocked(thread.recipient, false)
                    ApplicationContext.getInstance(this).jobManager.add(MultiDeviceBlockedUpdateJob())
                    Util.runOnMain {
                        recyclerView.adapter!!.notifyDataSetChanged()
                        dialog.dismiss()
                    }
                }.start()
            }.show()
    }

    private fun deleteConversation(thread: ThreadRecord) {
        val threadID = thread.threadId
        val recipient = thread.recipient
        val threadDB = DatabaseFactory.getThreadDatabase(this)
        val deleteThread = Runnable {
            AsyncTask.execute {
                val publicChat = DatabaseFactory.getLokiThreadDatabase(this@HomeActivity).getPublicChat(threadID)
                if (publicChat != null) {
                    val apiDB = DatabaseFactory.getLokiAPIDatabase(this@HomeActivity)
                    apiDB.removeLastMessageServerID(publicChat.channel, publicChat.server)
                    apiDB.removeLastDeletionServerID(publicChat.channel, publicChat.server)
                    apiDB.clearOpenGroupProfilePictureURL(publicChat.channel, publicChat.server)
                    ApplicationContext.getInstance(this@HomeActivity).publicChatAPI!!.leave(publicChat.channel, publicChat.server)
                }
                threadDB.deleteConversation(threadID)
                ApplicationContext.getInstance(this@HomeActivity).messageNotifier.updateNotification(this@HomeActivity)
            }
        }
        val dialogMessage = if (recipient.isGroupRecipient) R.string.activity_home_leave_group_dialog_message else R.string.activity_home_delete_conversation_dialog_message
        val dialog = AlertDialog.Builder(this)
        dialog.setMessage(dialogMessage)
        dialog.setPositiveButton(R.string.yes) { _, _ ->
            val isClosedGroup = recipient.address.isClosedGroup
            // Send a leave group message if this is an active closed group
            if (isClosedGroup && DatabaseFactory.getGroupDatabase(this).isActive(recipient.address.toGroupString())) {
                var isSSKBasedClosedGroup: Boolean
                var groupPublicKey: String?
                try {
                    groupPublicKey = ClosedGroupsProtocol.doubleDecodeGroupID(recipient.address.toString()).toHexString()
                    isSSKBasedClosedGroup = DatabaseFactory.getSSKDatabase(this).isSSKBasedClosedGroup(groupPublicKey)
                } catch (e: IOException) {
                    groupPublicKey = null
                    isSSKBasedClosedGroup = false
                }
                if (isSSKBasedClosedGroup) {
                    ClosedGroupsProtocol.leave(this, groupPublicKey!!)
                } else if (!ClosedGroupsProtocol.leaveLegacyGroup(this, recipient)) {
                    Toast.makeText(this, R.string.activity_home_leaving_group_failed_message, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
            }
            // Archive the conversation and then delete it after 10 seconds (the case where the
            // app was closed before the conversation could be deleted is handled in onCreate)
            threadDB.archiveConversation(threadID)
            val delay = if (isClosedGroup) 10000L else 1000L
            val handler = Handler()
            handler.postDelayed(deleteThread, delay)
            // Notify the user
            val toastMessage = if (recipient.isGroupRecipient) R.string.MessageRecord_left_group else R.string.activity_home_conversation_deleted_message
            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
        }
        dialog.setNegativeButton(R.string.no) { _, _ ->
            // Do nothing
        }
        dialog.create().show()
    }

    private fun openConversation(thread: ThreadRecord) {
        val intent = Intent(this, ConversationActivity::class.java)
        intent.putExtra(ConversationActivity.ADDRESS_EXTRA, thread.recipient.address)
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, thread.threadId)
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, thread.distributionType)
        intent.putExtra(ConversationActivity.TIMING_EXTRA, System.currentTimeMillis())
        intent.putExtra(ConversationActivity.LAST_SEEN_EXTRA, thread.lastSeen)
        intent.putExtra(ConversationActivity.STARTING_POSITION_EXTRA, -1)
        push(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        show(intent)
    }

    private fun showPath() {
        val intent = Intent(this, PathActivity::class.java)
        show(intent)
    }

    override fun createNewPrivateChat() {
        val intent = Intent(this, CreatePrivateChatActivity::class.java)
        show(intent)
    }

    override fun createNewClosedGroup() {
        val intent = Intent(this, CreateClosedGroupActivity::class.java)
        show(intent, true)
    }

    override fun joinOpenGroup() {
        val intent = Intent(this, JoinPublicChatActivity::class.java)
        show(intent)
    }
    // endregion
}