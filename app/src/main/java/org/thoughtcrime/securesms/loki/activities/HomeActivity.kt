package org.thoughtcrime.securesms.loki.activities

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.mentions.MentionsManager
import org.session.libsession.messaging.open_groups.OpenGroupAPI
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.utilities.*
import org.session.libsignal.utilities.toHexString
import org.session.libsignal.utilities.ThreadUtils
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.api.OpenGroupManager
import org.thoughtcrime.securesms.loki.dialogs.*
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.loki.views.ConversationView
import org.thoughtcrime.securesms.loki.views.NewConversationButtonSetViewDelegate
import org.thoughtcrime.securesms.loki.views.SeedReminderViewDelegate
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import java.io.IOException

class HomeActivity : PassphraseRequiredActionBarActivity(),
        ConversationClickListener,
        SeedReminderViewDelegate,
        NewConversationButtonSetViewDelegate {

    private lateinit var glide: GlideRequests
    private var broadcastReceiver: BroadcastReceiver? = null

    private val publicKey: String
        get() {
            return TextSecurePreferences.getLocalNumber(this)!!
        }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
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
        updateProfileButton()
        profileButton.setOnClickListener { openSettings() }
        pathStatusViewContainer.disableClipping()
        pathStatusViewContainer.setOnClickListener { showPath() }
        // Set up seed reminder view
        val hasViewedSeed = TextSecurePreferences.getHasViewedSeed(this)
        if (!hasViewedSeed) {
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
        homeAdapter.setHasStableIds(true)
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
        val userPublicKey = TextSecurePreferences.getLocalNumber(this)
        if (userPublicKey != null) {
            MentionsManager.configureIfNeeded(userPublicKey, userDB)
            OpenGroupManager.startPolling()
            JobQueue.shared.resumePendingJobs()
        }
        IP2Country.configureIfNeeded(this)
        application.registerForFCMIfNeeded(false)
        // Observe blocked contacts changed events
        val broadcastReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                recyclerView.adapter!!.notifyDataSetChanged()
            }
        }
        this.broadcastReceiver = broadcastReceiver
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter("blockedContactsChanged"))
        lifecycleScope.launch {
            // update things based on TextSecurePrefs (profile info etc)
            TextSecurePreferences.events.filter { it == TextSecurePreferences.PROFILE_NAME_PREF }.collect {
                updateProfileButton()
            }
        }
        EventBus.getDefault().register(this@HomeActivity)
    }

    override fun onResume() {
        super.onResume()
        if (TextSecurePreferences.getLocalNumber(this) == null) {
            return; } // This can be the case after a secondary device is auto-cleared
        profileButton.recycle() // clear cached image before update tje profilePictureView
        profileButton.update()
        val hasViewedSeed = TextSecurePreferences.getHasViewedSeed(this)
        if (hasViewedSeed) {
            seedReminderView.visibility = View.GONE
        }
        showFileServerInstabilityNotificationIfNeeded()
        if (TextSecurePreferences.getConfigurationMessageSynced(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                MultiDeviceProtocol.syncConfigurationIfNeeded(this@HomeActivity)
            }
        }
    }

    private fun showFileServerInstabilityNotificationIfNeeded() {
        val hasSeenNotification = TextSecurePreferences.hasSeenFileServerInstabilityNotification(this)
        if (hasSeenNotification) { return }
        FileServerDialog().show(supportFragmentManager, "File Server Dialog")
        TextSecurePreferences.setHasSeenFileServerInstabilityNotification(this)
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
        EventBus.getDefault().unregister(this)
    }
    // endregion

    // region Updating
    private fun updateEmptyState() {
        val threadCount = (recyclerView.adapter as HomeAdapter).itemCount
        emptyStateContainer.visibility = if (threadCount == 0) View.VISIBLE else View.GONE
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUpdateProfileEvent(event: ProfilePictureModifiedEvent) {
        if (event.recipient.isLocalNumber) {
            updateProfileButton()
        }
    }

    private fun updateProfileButton() {
        profileButton.publicKey = publicKey
        profileButton.displayName = TextSecurePreferences.getProfileName(this)
        profileButton.recycle()
        profileButton.update()
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
            bundle.putString("publicKey", thread.recipient.address.toString())
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
                    ThreadUtils.queue {
                        DatabaseFactory.getRecipientDatabase(this).setBlocked(thread.recipient, true)
                        Util.runOnMain {
                            recyclerView.adapter!!.notifyDataSetChanged()
                            dialog.dismiss()
                        }
                    }
                }.show()
    }

    private fun unblockConversation(thread: ThreadRecord) {
        AlertDialog.Builder(this)
                .setTitle(R.string.RecipientPreferenceActivity_unblock_this_contact_question)
                .setMessage(R.string.RecipientPreferenceActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.RecipientPreferenceActivity_unblock) { dialog, _ ->
                    ThreadUtils.queue {
                        DatabaseFactory.getRecipientDatabase(this).setBlocked(thread.recipient, false)
                        Util.runOnMain {
                            recyclerView.adapter!!.notifyDataSetChanged()
                            dialog.dismiss()
                        }
                    }
                }.show()
    }

    private fun deleteConversation(thread: ThreadRecord) {
        val threadID = thread.threadId
        val recipient = thread.recipient
        val threadDB = DatabaseFactory.getThreadDatabase(this)
        val message: String
        if (recipient.isGroupRecipient) {
            val group = DatabaseFactory.getGroupDatabase(this).getGroup(recipient.address.toString()).orNull()
            if (group != null && group.admins.map { it.toString() }.contains(TextSecurePreferences.getLocalNumber(this))) {
                message = "Because you are the creator of this group it will be deleted for everyone. This cannot be undone."
            } else {
                message = resources.getString(R.string.activity_home_leave_group_dialog_message)
            }
        } else {
            message = resources.getString(R.string.activity_home_delete_conversation_dialog_message)
        }
        val dialog = AlertDialog.Builder(this)
        dialog.setMessage(message)
        dialog.setPositiveButton(R.string.yes) { _, _ ->
            lifecycleScope.launch(Dispatchers.Main) {
                val context = this@HomeActivity as Context
                // Cancel any outstanding jobs
                DatabaseFactory.getSessionJobDatabase(context).cancelPendingMessageSendJobs(threadID)
                // Send a leave group message if this is an active closed group
                if (recipient.address.isClosedGroup && DatabaseFactory.getGroupDatabase(context).isActive(recipient.address.toGroupString())) {
                    var isClosedGroup: Boolean
                    var groupPublicKey: String?
                    try {
                        groupPublicKey = GroupUtil.doubleDecodeGroupID(recipient.address.toString()).toHexString()
                        isClosedGroup = DatabaseFactory.getLokiAPIDatabase(context).isClosedGroup(groupPublicKey)
                    } catch (e: IOException) {
                        groupPublicKey = null
                        isClosedGroup = false
                    }
                    if (isClosedGroup) {
                        MessageSender.explicitLeave(groupPublicKey!!, false)
                    } else {
                        Toast.makeText(context, R.string.activity_home_leaving_group_failed_message, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }
                // Delete the conversation
                val v1OpenGroup = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
                val v2OpenGroup = DatabaseFactory.getLokiThreadDatabase(context).getOpenGroupChat(threadID)
                if (v1OpenGroup != null) {
                    val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
                    apiDB.removeLastMessageServerID(v1OpenGroup.channel, v1OpenGroup.server)
                    apiDB.removeLastDeletionServerID(v1OpenGroup.channel, v1OpenGroup.server)
                    apiDB.clearOpenGroupProfilePictureURL(v1OpenGroup.channel, v1OpenGroup.server)
                    OpenGroupAPI.leave(v1OpenGroup.channel, v1OpenGroup.server)
                    // FIXME: No longer supported so let's remove this code
                } else if (v2OpenGroup != null) {
                    val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
                    apiDB.removeLastMessageServerID(v2OpenGroup.room, v2OpenGroup.server)
                    apiDB.removeLastDeletionServerID(v2OpenGroup.room, v2OpenGroup.server)
                    OpenGroupManager.delete(v2OpenGroup.server, v2OpenGroup.room, this@HomeActivity)
                } else {
                    threadDB.deleteConversation(threadID)
                }
                // Update the badge count
                ApplicationContext.getInstance(context).messageNotifier.updateNotification(context)
                // Notify the user
                val toastMessage = if (recipient.isGroupRecipient) R.string.MessageRecord_left_group else R.string.activity_home_conversation_deleted_message
                Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            }
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
        show(intent, isForResult = true)
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