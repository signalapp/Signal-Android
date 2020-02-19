package org.thoughtcrime.securesms.loki.redesign.activities

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
import android.support.design.widget.Snackbar
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_home.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.getColorWithID
import org.thoughtcrime.securesms.loki.redesign.utilities.push
import org.thoughtcrime.securesms.loki.redesign.utilities.show
import org.thoughtcrime.securesms.loki.redesign.views.ConversationView
import org.thoughtcrime.securesms.loki.redesign.views.SeedReminderViewDelegate
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.notifications.MessageNotifier
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import kotlin.math.abs

class HomeActivity : PassphraseRequiredActionBarActivity, ConversationClickListener, SeedReminderViewDelegate {
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
                    MessageNotifier.updateNotification(this)
                }
            }
            deleteThreadAtCurrentPosition()
            while (archivedConversations.moveToNext()) {
                deleteThreadAtCurrentPosition()
            }
        }
        // Double check that the long poller is up
        (applicationContext as ApplicationContext).startLongPollingIfNeeded()
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
        createClosedGroupButton.setOnClickListener { createClosedGroup() }
        joinPublicChatButton.setOnClickListener { joinPublicChat() }
        // Set up seed reminder view
        val isMasterDevice = (TextSecurePreferences.getMasterHexEncodedPublicKey(this) == null)
        val hasViewedSeed = TextSecurePreferences.getHasViewedSeed(this)
        if (!hasViewedSeed && isMasterDevice) {
            val seedReminderViewTitle = SpannableString("You're almost finished! 80%")
            seedReminderViewTitle.setSpan(ForegroundColorSpan(resources.getColorWithID(R.color.accent, theme)), 24, 27, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            seedReminderView.title = seedReminderViewTitle
            seedReminderView.subtitle = "Secure your account by saving your recovery phrase"
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
        // This is a workaround for the fact that CursorRecyclerViewAdapter doesn't actually auto-update (even though it says it will)
        LoaderManager.getInstance(this).restartLoader(0, null, object : LoaderManager.LoaderCallbacks<Cursor> {

            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<Cursor> {
                return HomeLoader(this@HomeActivity)
            }

            override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
                homeAdapter.changeCursor(cursor)
            }

            override fun onLoaderReset(cursor: Loader<Cursor>) {
                homeAdapter.changeCursor(null)
            }
        })
        // Set up new conversation button
        newConversationButton.setOnClickListener { createPrivateChat() }
        // Set up typing observer
        ApplicationContext.getInstance(this).typingStatusRepository.typingThreads.observe(this, Observer<Set<Long>> { threadIDs ->
            val adapter = recyclerView.adapter as HomeAdapter
            adapter.typingThreadIDs = threadIDs ?: setOf()
        })
        // Set up public chats and RSS feeds if needed
        if (TextSecurePreferences.getLocalNumber(this) != null) {
            val application = ApplicationContext.getInstance(this)
            application.createDefaultPublicChatsIfNeeded()
            application.createRSSFeedsIfNeeded()
            application.lokiPublicChatManager.startPollersIfNeeded()
            application.startRSSFeedPollersIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        val isMasterDevice = (TextSecurePreferences.getMasterHexEncodedPublicKey(this) == null)
        val hasViewedSeed = TextSecurePreferences.getHasViewedSeed(this)
        if (hasViewedSeed || !isMasterDevice) {
            seedReminderView.visibility = View.GONE
        }
//        if (!TextSecurePreferences.getHasSeenOpenGroupSuggestionSheet(this)) {
//            val bottomSheet = OpenGroupSuggestionBottomSheet()
//            bottomSheet.onJoinTapped = {
//                TextSecurePreferences.setHasSeenOpenGroupSuggestionSheet(this)
//                bottomSheet.dismiss()
//                // TODO: Duplication of the code in JoinPublicChatActivity
//                val application = ApplicationContext.getInstance(this)
//                val channel: Long = 1
//                val displayName = TextSecurePreferences.getProfileName(this)
//                val lokiPublicChatAPI = application.lokiPublicChatAPI!!
//                val url = "https://chat.getsession.org"
//                application.lokiPublicChatManager.addChat(url, channel).successUi {
//                    lokiPublicChatAPI.getMessages(channel, url)
//                    lokiPublicChatAPI.setDisplayName(displayName, url)
//                    lokiPublicChatAPI.join(channel, url)
//                    val profileKey: ByteArray = ProfileKeyUtil.getProfileKey(this)
//                    val profileUrl: String? = TextSecurePreferences.getProfileAvatarUrl(this)
//                    lokiPublicChatAPI.setProfilePicture(url, profileKey, profileUrl)
//                }
//            }
//            bottomSheet.onDismissTapped = {
//                TextSecurePreferences.setHasSeenOpenGroupSuggestionSheet(this)
//                bottomSheet.dismiss()
//            }
//            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
//        }
    }
    // endregion

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
        intent.putExtra(ConversationActivity.ADDRESS_EXTRA, thread.recipient.getAddress())
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

    private fun createPrivateChat() {
        val intent = Intent(this, CreatePrivateChatActivity::class.java)
        show(intent)
    }

    private fun createClosedGroup() {
        val intent = Intent(this, CreateClosedGroupActivity::class.java)
        show(intent)
    }

    private fun joinPublicChat() {
        val intent = Intent(this, JoinPublicChatActivity::class.java)
        show(intent)
    }

    private class SwipeCallback(val activity: HomeActivity) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        @SuppressLint("StaticFieldLeak")
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val threadID = (viewHolder as HomeAdapter.ViewHolder).view.thread!!.threadId
            val recipient = (viewHolder as HomeAdapter.ViewHolder).view.thread!!.recipient
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

                        MessageNotifier.updateNotification(activity)
                    }
                }
            }

            val message = if (recipient.isGroupRecipient) R.string.activity_home_leave_group_title else R.string.activity_home_delete_conversation_title
            val alertDialogBuilder = AlertDialog.Builder(activity)
            alertDialogBuilder.setMessage(message)
            alertDialogBuilder.setPositiveButton(R.string.yes) { _, _ ->
                val isGroup = recipient.isGroupRecipient

                // If we are deleting a group and it's active
                // We need to send a leave message
                if (isGroup && DatabaseFactory.getGroupDatabase(activity).isActive(recipient.address.toGroupString())) {
                    if (!GroupUtil.leaveGroup(activity, recipient)) {
                        Toast.makeText(activity, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show()
                        clearView(activity.recyclerView, viewHolder)
                        return@setPositiveButton
                    }
                }

                // Archive and forcefully delete the conversation in 10 seconds
                threadDatabase.archiveConversation(threadID)
                val handler = Handler()
                handler.postDelayed(deleteThread, 10000)

                // Notify the user
                val snackbarText = if (isGroup) R.string.MessageRecord_left_group else R.string.activity_home_conversation_deleted
                val snackbar = Snackbar.make(activity.contentView, snackbarText, Snackbar.LENGTH_LONG)
                snackbar.show()
            }
            alertDialogBuilder.setNegativeButton(R.string.no) { _, _ ->
                clearView(activity.recyclerView, viewHolder)
            }
            alertDialogBuilder.show()
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