package org.thoughtcrime.securesms.loki.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_home.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.dialogs.PNModeBottomSheet
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupsProtocol
import org.thoughtcrime.securesms.loki.protocol.LokiSessionResetImplementation
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.loki.views.ConversationView
import org.thoughtcrime.securesms.loki.views.NewConversationButtonSetViewDelegate
import org.thoughtcrime.securesms.loki.views.SeedReminderViewDelegate
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.notifications.MessageNotifier
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.fileserver.LokiFileServerAPI
import org.whispersystems.signalservice.loki.protocol.friendrequests.FriendRequestProtocol
import org.whispersystems.signalservice.loki.protocol.mentions.MentionsManager
import org.whispersystems.signalservice.loki.protocol.meta.SessionMetaProtocol
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol
import org.whispersystems.signalservice.loki.protocol.sessionmanagement.SessionManagementProtocol
import org.whispersystems.signalservice.loki.protocol.syncmessages.SyncMessagesProtocol
import org.whispersystems.signalservice.loki.protocol.todo.LokiMessageFriendRequestStatus
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus
import kotlin.math.abs

class HomeActivity : PassphraseRequiredActionBarActivity, ConversationClickListener, SeedReminderViewDelegate, NewConversationButtonSetViewDelegate {
    private lateinit var glide: GlideRequests

    private val hexEncodedPublicKey: String
        get() {
            val masterHexEncodedPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(this)
            val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this)
            return masterHexEncodedPublicKey ?: userHexEncodedPublicKey
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
        profileButton.hexEncodedPublicKey = hexEncodedPublicKey
        profileButton.update()
        profileButton.setOnClickListener { openSettings() }
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
        ItemTouchHelper(SwipeCallback(this)).attachToRecyclerView(recyclerView)
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
        val userPublicKey = TextSecurePreferences.getLocalNumber(this)
        val sessionResetImpl = LokiSessionResetImplementation(this)
        if (userPublicKey != null) {
            FriendRequestProtocol.configureIfNeeded(apiDB, userPublicKey)
            MentionsManager.configureIfNeeded(userPublicKey, threadDB, userDB)
            SessionMetaProtocol.configureIfNeeded(apiDB, userPublicKey)
            SyncMessagesProtocol.configureIfNeeded(apiDB, userPublicKey)
            application.lokiPublicChatManager.startPollersIfNeeded()
        }
        SessionManagementProtocol.configureIfNeeded(sessionResetImpl, threadDB, application)
        MultiDeviceProtocol.configureIfNeeded(apiDB)
        IP2Country.configureIfNeeded(this)
        // Preload device links to make message sending quicker
        val publicKeys = ContactUtilities.getAllContacts(this).filter { contact ->
            !contact.recipient.isGroupRecipient && contact.isFriend && !contact.isOurDevice && !contact.isSlave
        }.map {
            it.recipient.address.toPhoneString()
        }.toSet()
        LokiFileServerAPI.shared.getDeviceLinks(publicKeys)
        // TODO: Temporary hack to unbork existing clients
        val allContacts = DatabaseFactory.getRecipientDatabase(this).allAddresses.map {
            MultiDeviceProtocol.shared.getMasterDevice(it.serialize()) ?: it.serialize()
        }.toSet()
        val lokiMessageDB = DatabaseFactory.getLokiMessageDatabase(this)
        for (contact in allContacts) {
            val slaveDeviceHasPendingFR = MultiDeviceProtocol.shared.getSlaveDevices(contact).any {
                val slaveDeviceThreadID = DatabaseFactory.getThreadDatabase(this).getThreadIdFor(recipient(this, it))
                DatabaseFactory.getLokiThreadDatabase(this).getFriendRequestStatus(slaveDeviceThreadID) == LokiThreadFriendRequestStatus.REQUEST_RECEIVED
            }
            val masterDeviceThreadID = DatabaseFactory.getThreadDatabase(this).getThreadIdFor(recipient(this, contact))
            val masterDeviceHasNoPendingFR = (DatabaseFactory.getLokiThreadDatabase(this).getFriendRequestStatus(masterDeviceThreadID) == LokiThreadFriendRequestStatus.NONE)
            if (slaveDeviceHasPendingFR && masterDeviceHasNoPendingFR) {
                val lastMessageID = org.thoughtcrime.securesms.loki.protocol.FriendRequestProtocol.getLastMessageID(this, masterDeviceThreadID)
                if (lastMessageID != null) {
                    val lastMessageFRStatus = lokiMessageDB.getFriendRequestStatus(lastMessageID)
                    if (lastMessageFRStatus != LokiMessageFriendRequestStatus.REQUEST_PENDING) {
                        lokiMessageDB.setFriendRequestStatus(lastMessageID, LokiMessageFriendRequestStatus.REQUEST_PENDING)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        profileButton.update()
        val isMasterDevice = (TextSecurePreferences.getMasterHexEncodedPublicKey(this) == null)
        val hasViewedSeed = TextSecurePreferences.getHasViewedSeed(this)
        if (hasViewedSeed || !isMasterDevice) {
            seedReminderView.visibility = View.GONE
        }
        if (!TextSecurePreferences.hasSeenPNModeSheet(this)) {
            val bottomSheet = PNModeBottomSheet()
            bottomSheet.onConfirmTapped = { isUsingFCM ->
                TextSecurePreferences.setHasSeenPNModeSheet(this, true)
                TextSecurePreferences.setIsUsingFCM(this, isUsingFCM)
                ApplicationContext.getInstance(this).registerForFCMIfNeeded(true)
                bottomSheet.dismiss()
            }
            bottomSheet.onSkipTapped = {
                TextSecurePreferences.setHasSeenPNModeSheet(this, true)
                bottomSheet.dismiss()
            }
            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == CreateClosedGroupActivity.createNewPrivateChatResultCode) {
            createNewPrivateChat()
        }
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
        // Do nothing
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

    private class SwipeCallback(val activity: HomeActivity) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        @SuppressLint("StaticFieldLeak")
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            viewHolder as HomeAdapter.ViewHolder
            val threadID = viewHolder.view.thread!!.threadId
            val recipient = viewHolder.view.thread!!.recipient
            val threadDatabase = DatabaseFactory.getThreadDatabase(activity)
            val deleteThread = object : Runnable {

                override fun run() {
                    AsyncTask.execute {
                        val publicChat = DatabaseFactory.getLokiThreadDatabase(activity).getPublicChat(threadID)
                        if (publicChat != null) {
                            val apiDatabase = DatabaseFactory.getLokiAPIDatabase(activity)
                            apiDatabase.removeLastMessageServerID(publicChat.channel, publicChat.server)
                            apiDatabase.removeLastDeletionServerID(publicChat.channel, publicChat.server)
                            ApplicationContext.getInstance(activity).lokiPublicChatAPI!!.leave(publicChat.channel, publicChat.server)
                        }
                        threadDatabase.deleteConversation(threadID)
                        ApplicationContext.getInstance(activity).messageNotifier.updateNotification(activity)
                    }
                }
            }
            val dialogMessage = if (recipient.isGroupRecipient) R.string.activity_home_leave_group_dialog_message else R.string.activity_home_delete_conversation_dialog_message
            val dialog = AlertDialog.Builder(activity)
            dialog.setMessage(dialogMessage)
            dialog.setPositiveButton(R.string.yes) { _, _ ->
                val isClosedGroup = recipient.address.isClosedGroup
                // Send a leave group message if this is an active closed group
                if (isClosedGroup && DatabaseFactory.getGroupDatabase(activity).isActive(recipient.address.toGroupString())) {
                    if (!ClosedGroupsProtocol.leaveGroup(activity, recipient)) {
                        Toast.makeText(activity, R.string.activity_home_leaving_group_failed_message, Toast.LENGTH_LONG).show()
                        clearView(activity.recyclerView, viewHolder)
                        return@setPositiveButton
                    }
                }
                // Archive the conversation and then delete it after 10 seconds (the case where the
                // app was closed before the conversation could be deleted is handled in onCreate)
                threadDatabase.archiveConversation(threadID)
                val delay = if (isClosedGroup) 10000L else 1000L
                val handler = Handler()
                handler.postDelayed(deleteThread, delay)
                // Notify the user
                val toastMessage = if (recipient.isGroupRecipient) R.string.MessageRecord_left_group else R.string.activity_home_conversation_deleted_message
                Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG).show()
            }
            dialog.setNegativeButton(R.string.no) { _, _ ->
                clearView(activity.recyclerView, viewHolder)
            }
            dialog.create().show()
        }

        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dx: Float, dy: Float, actionState: Int, isCurrentlyActive: Boolean) {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dx < 0) {
                val itemView = viewHolder.itemView
                animate(viewHolder, dx)
                val backgroundPaint = Paint()
                backgroundPaint.color = activity.resources.getColorWithID(R.color.destructive, activity.theme)
                c.drawRect(itemView.right.toFloat() - abs(dx), itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat(), backgroundPaint)
                val icon = BitmapFactory.decodeResource(activity.resources, R.drawable.ic_trash_filled_32)
                val iconPaint = Paint()
                val left = itemView.right.toFloat() - abs(dx) + activity.resources.getDimension(R.dimen.medium_spacing)
                val top = itemView.top.toFloat() + (itemView.bottom.toFloat() - itemView.top.toFloat() - icon.height) / 2
                c.drawBitmap(icon, left, top, iconPaint)
            } else {
                super.onChildDraw(c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive)
            }
        }

        private fun animate(viewHolder: RecyclerView.ViewHolder, dx: Float) {
            val alpha = 1.0f - abs(dx) / viewHolder.itemView.width.toFloat()
            viewHolder.itemView.alpha = alpha
            viewHolder.itemView.translationX = dx
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = 1.0f
            viewHolder.itemView.translationX = 0.0f
        }
    }
    // endregion
}