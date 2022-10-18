package org.thoughtcrime.securesms.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.SpannableString
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityHomeBinding
import network.loki.messenger.databinding.ViewMessageRequestBannerBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.ProfilePictureModifiedEvent
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.ThreadUtils
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.MuteDialog
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.start.NewConversationFragment
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.utilities.NotificationUtils
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter
import org.thoughtcrime.securesms.home.search.GlobalSearchInputLayout
import org.thoughtcrime.securesms.home.search.GlobalSearchViewModel
import org.thoughtcrime.securesms.messagerequests.MessageRequestsActivity
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.onboarding.SeedActivity
import org.thoughtcrime.securesms.onboarding.SeedReminderViewDelegate
import org.thoughtcrime.securesms.preferences.SettingsActivity
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.IP2Country
import org.thoughtcrime.securesms.util.disableClipping
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.show
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : PassphraseRequiredActionBarActivity(),
    ConversationClickListener,
    SeedReminderViewDelegate,
    GlobalSearchInputLayout.GlobalSearchInputLayoutListener {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var glide: GlideRequests
    private var broadcastReceiver: BroadcastReceiver? = null

    @Inject lateinit var threadDb: ThreadDatabase
    @Inject lateinit var mmsSmsDatabase: MmsSmsDatabase
    @Inject lateinit var recipientDatabase: RecipientDatabase
    @Inject lateinit var groupDatabase: GroupDatabase
    @Inject lateinit var textSecurePreferences: TextSecurePreferences

    private val globalSearchViewModel by viewModels<GlobalSearchViewModel>()
    private val homeViewModel by viewModels<HomeViewModel>()

    private val publicKey: String
        get() = textSecurePreferences.getLocalNumber()!!

    private val homeAdapter: HomeAdapter by lazy {
        HomeAdapter(context = this, listener = this)
    }

    private val globalSearchAdapter = GlobalSearchAdapter { model ->
        when (model) {
            is GlobalSearchAdapter.Model.Message -> {
                val threadId = model.messageResult.threadId
                val timestamp = model.messageResult.receivedTimestampMs
                val author = model.messageResult.messageRecipient.address

                val intent = Intent(this, ConversationActivityV2::class.java)
                intent.putExtra(ConversationActivityV2.THREAD_ID, threadId)
                intent.putExtra(ConversationActivityV2.SCROLL_MESSAGE_ID, timestamp)
                intent.putExtra(ConversationActivityV2.SCROLL_MESSAGE_AUTHOR, author)
                push(intent)
            }
            is GlobalSearchAdapter.Model.SavedMessages -> {
                val intent = Intent(this, ConversationActivityV2::class.java)
                intent.putExtra(ConversationActivityV2.ADDRESS, Address.fromSerialized(model.currentUserPublicKey))
                push(intent)
            }
            is GlobalSearchAdapter.Model.Contact -> {
                val address = model.contact.sessionID

                val intent = Intent(this, ConversationActivityV2::class.java)
                intent.putExtra(ConversationActivityV2.ADDRESS, Address.fromSerialized(address))
                push(intent)
            }
            is GlobalSearchAdapter.Model.GroupConversation -> {
                val groupAddress = Address.fromSerialized(model.groupRecord.encodedId)
                val threadId = threadDb.getThreadIdIfExistsFor(Recipient.from(this, groupAddress, false))
                if (threadId >= 0) {
                    val intent = Intent(this, ConversationActivityV2::class.java)
                    intent.putExtra(ConversationActivityV2.THREAD_ID, threadId)
                    push(intent)
                }
            }
            else -> {
                Log.d("Loki", "callback with model: $model")
            }
        }
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Set content view
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Set custom toolbar
        setSupportActionBar(binding.toolbar)
        // Set up Glide
        glide = GlideApp.with(this)
        // Set up toolbar buttons
        binding.profileButton.root.glide = glide
        binding.profileButton.root.setOnClickListener { openSettings() }
        binding.searchViewContainer.setOnClickListener {
            binding.globalSearchInputLayout.requestFocus()
        }
        binding.sessionToolbar.disableClipping()
        // Set up seed reminder view
        val hasViewedSeed = textSecurePreferences.getHasViewedSeed()
        if (!hasViewedSeed) {
            binding.seedReminderView.isVisible = true
            binding.seedReminderView.title = SpannableString("You're almost finished! 80%") // Intentionally not yet translated
            binding.seedReminderView.subtitle = resources.getString(R.string.view_seed_reminder_subtitle_1)
            binding.seedReminderView.setProgress(80, false)
            binding.seedReminderView.delegate = this@HomeActivity
        } else {
            binding.seedReminderView.isVisible = false
        }
        setupMessageRequestsBanner()
        // Set up recycler view
        binding.globalSearchInputLayout.listener = this
        homeAdapter.setHasStableIds(true)
        homeAdapter.glide = glide
        binding.recyclerView.adapter = homeAdapter
        binding.globalSearchRecycler.adapter = globalSearchAdapter

        // Set up empty state view
        binding.createNewPrivateChatButton.setOnClickListener { showNewConversation() }
        IP2Country.configureIfNeeded(this@HomeActivity)
        startObservingUpdates()

        // Set up new conversation button
        binding.newConversationButton.setOnClickListener { showNewConversation() }
        // Observe blocked contacts changed events
        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                binding.recyclerView.adapter!!.notifyDataSetChanged()
            }
        }
        this.broadcastReceiver = broadcastReceiver
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter("blockedContactsChanged"))

        lifecycleScope.launchWhenStarted {
            launch(Dispatchers.IO) {
                // Double check that the long poller is up
                (applicationContext as ApplicationContext).startPollingIfNeeded()
                // update things based on TextSecurePrefs (profile info etc)
                // Set up remaining components if needed
                val application = ApplicationContext.getInstance(this@HomeActivity)
                application.registerForFCMIfNeeded(false)
                if (textSecurePreferences.getLocalNumber() != null) {
                    OpenGroupManager.startPolling()
                    JobQueue.shared.resumePendingJobs()
                }
                // Set up typing observer
                withContext(Dispatchers.Main) {
                    updateProfileButton()
                    TextSecurePreferences.events.filter { it == TextSecurePreferences.PROFILE_NAME_PREF }.collect {
                        updateProfileButton()
                    }
                }
            }
            // monitor the global search VM query
            launch {
                binding.globalSearchInputLayout.query
                        .onEach(globalSearchViewModel::postQuery)
                        .collect()
            }
            // Get group results and display them
            launch {
                globalSearchViewModel.result.collect { result ->
                    val currentUserPublicKey = publicKey
                    val contactAndGroupList = result.contacts.map { GlobalSearchAdapter.Model.Contact(it) } +
                            result.threads.map { GlobalSearchAdapter.Model.GroupConversation(it) }

                    val contactResults = contactAndGroupList.toMutableList()

                    if (contactResults.isEmpty()) {
                        contactResults.add(GlobalSearchAdapter.Model.SavedMessages(currentUserPublicKey))
                    }

                    val userIndex = contactResults.indexOfFirst { it is GlobalSearchAdapter.Model.Contact && it.contact.sessionID == currentUserPublicKey }
                    if (userIndex >= 0) {
                        contactResults[userIndex] = GlobalSearchAdapter.Model.SavedMessages(currentUserPublicKey)
                    }

                    if (contactResults.isNotEmpty()) {
                        contactResults.add(0, GlobalSearchAdapter.Model.Header(R.string.global_search_contacts_groups))
                    }

                    val unreadThreadMap = result.messages
                            .groupBy { it.threadId }.keys
                            .map { it to mmsSmsDatabase.getUnreadCount(it) }
                            .toMap()

                    val messageResults: MutableList<GlobalSearchAdapter.Model> = result.messages
                            .map { messageResult ->
                                GlobalSearchAdapter.Model.Message(
                                        messageResult,
                                        unreadThreadMap[messageResult.threadId] ?: 0
                                )
                            }.toMutableList()

                    if (messageResults.isNotEmpty()) {
                        messageResults.add(0, GlobalSearchAdapter.Model.Header(R.string.global_search_messages))
                    }

                    val newData = contactResults + messageResults

                    globalSearchAdapter.setNewData(result.query, newData)
                }
            }
        }
        EventBus.getDefault().register(this@HomeActivity)
    }

    override fun onInputFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            setSearchShown(true)
        } else {
            setSearchShown(!binding.globalSearchInputLayout.query.value.isNullOrEmpty())
        }
    }

    private fun setSearchShown(isShown: Boolean) {
        binding.searchToolbar.isVisible = isShown
        binding.sessionToolbar.isVisible = !isShown
        binding.recyclerView.isVisible = !isShown
        binding.emptyStateContainer.isVisible = (binding.recyclerView.adapter as HomeAdapter).itemCount == 0 && binding.recyclerView.isVisible
        binding.seedReminderView.isVisible = !TextSecurePreferences.getHasViewedSeed(this) && !isShown
        binding.globalSearchRecycler.isVisible = isShown
        binding.newConversationButton.isVisible = !isShown
    }

    private fun setupMessageRequestsBanner() {
        val messageRequestCount = threadDb.unapprovedConversationCount
        // Set up message requests
        if (messageRequestCount > 0 && !textSecurePreferences.hasHiddenMessageRequests()) {
            with(ViewMessageRequestBannerBinding.inflate(layoutInflater)) {
                unreadCountTextView.text = messageRequestCount.toString()
                timestampTextView.text = DateUtils.getDisplayFormattedTimeSpanString(
                    this@HomeActivity,
                    Locale.getDefault(),
                    threadDb.latestUnapprovedConversationTimestamp
                )
                root.setOnClickListener { showMessageRequests() }
                root.setOnLongClickListener { hideMessageRequests(); true }
                root.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                val hadHeader = homeAdapter.hasHeaderView()
                homeAdapter.header = root
                if (hadHeader) homeAdapter.notifyItemChanged(0)
                else homeAdapter.notifyItemInserted(0)
            }
        } else {
            val hadHeader = homeAdapter.hasHeaderView()
            homeAdapter.header = null
            if (hadHeader) {
                homeAdapter.notifyItemRemoved(0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ApplicationContext.getInstance(this).messageNotifier.setHomeScreenVisible(true)
        if (textSecurePreferences.getLocalNumber() == null) { return; } // This can be the case after a secondary device is auto-cleared
        IdentityKeyUtil.checkUpdate(this)
        binding.profileButton.root.recycle() // clear cached image before update tje profilePictureView
        binding.profileButton.root.update()
        if (textSecurePreferences.getHasViewedSeed()) {
            binding.seedReminderView.isVisible = false
        }
        if (textSecurePreferences.getConfigurationMessageSynced()) {
            lifecycleScope.launch(Dispatchers.IO) {
                ConfigurationMessageUtilities.syncConfigurationIfNeeded(this@HomeActivity)
            }
        }

        // If the theme hasn't changed then start observing updates again (if it does change then we
        // will recreate the activity resulting in it responding to changes multiple times)
        if (currentThemeState == textSecurePreferences.themeState() && !homeViewModel.getObservable(this).hasActiveObservers()) {
            startObservingUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        ApplicationContext.getInstance(this).messageNotifier.setHomeScreenVisible(false)

        homeViewModel.getObservable(this).removeObservers(this)
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
    private fun startObservingUpdates() {
        homeViewModel.getObservable(this).observe(this) { newData ->
            val manager = binding.recyclerView.layoutManager as LinearLayoutManager
            val firstPos = manager.findFirstCompletelyVisibleItemPosition()
            val offsetTop = if(firstPos >= 0) {
                manager.findViewByPosition(firstPos)?.let { view ->
                    manager.getDecoratedTop(view) - manager.getTopDecorationHeight(view)
                } ?: 0
            } else 0
            homeAdapter.data = newData
            if(firstPos >= 0) { manager.scrollToPositionWithOffset(firstPos, offsetTop) }
            setupMessageRequestsBanner()
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        val threadCount = (binding.recyclerView.adapter)!!.itemCount
        binding.emptyStateContainer.isVisible = threadCount == 0 && binding.recyclerView.isVisible
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUpdateProfileEvent(event: ProfilePictureModifiedEvent) {
        if (event.recipient.isLocalNumber) {
            updateProfileButton()
        } else {
            homeViewModel.tryUpdateChannel()
        }
    }

    private fun updateProfileButton() {
        binding.profileButton.root.publicKey = publicKey
        binding.profileButton.root.displayName = textSecurePreferences.getProfileName()
        binding.profileButton.root.recycle()
        binding.profileButton.root.update()
    }
    // endregion

    // region Interaction
    override fun onBackPressed() {
        if (binding.globalSearchRecycler.isVisible) {
            binding.globalSearchInputLayout.clearSearch(true)
            return
        }
        super.onBackPressed()
    }

    override fun handleSeedReminderViewContinueButtonTapped() {
        val intent = Intent(this, SeedActivity::class.java)
        show(intent)
    }

    override fun onConversationClick(thread: ThreadRecord) {
        val intent = Intent(this, ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.THREAD_ID, thread.threadId)
        push(intent)
    }

    override fun onLongConversationClick(thread: ThreadRecord) {
        val bottomSheet = ConversationOptionsBottomSheet(this)
        bottomSheet.thread = thread
        bottomSheet.onViewDetailsTapped = {
            bottomSheet.dismiss()
            val userDetailsBottomSheet = UserDetailsBottomSheet()
            val bundle = bundleOf(
                    UserDetailsBottomSheet.ARGUMENT_PUBLIC_KEY to thread.recipient.address.toString(),
                    UserDetailsBottomSheet.ARGUMENT_THREAD_ID to thread.threadId
            )
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
        bottomSheet.onSetMuteTapped = { muted ->
            bottomSheet.dismiss()
            setConversationMuted(thread, muted)
        }
        bottomSheet.onNotificationTapped = {
            bottomSheet.dismiss()
            NotificationUtils.showNotifyDialog(this, thread.recipient) { notifyType ->
                setNotifyType(thread, notifyType)
            }
        }
        bottomSheet.onPinTapped = {
            bottomSheet.dismiss()
            setConversationPinned(thread.threadId, true)
        }
        bottomSheet.onUnpinTapped = {
            bottomSheet.dismiss()
            setConversationPinned(thread.threadId, false)
        }
        bottomSheet.onMarkAllAsReadTapped = {
            bottomSheet.dismiss()
            markAllAsRead(thread)
        }
        bottomSheet.show(supportFragmentManager, bottomSheet.tag)
    }

    private fun blockConversation(thread: ThreadRecord) {
        AlertDialog.Builder(this)
                .setTitle(R.string.RecipientPreferenceActivity_block_this_contact_question)
                .setMessage(R.string.RecipientPreferenceActivity_you_will_no_longer_receive_messages_and_calls_from_this_contact)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.RecipientPreferenceActivity_block) { dialog, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        recipientDatabase.setBlocked(thread.recipient, true)
                        withContext(Dispatchers.Main) {
                            binding.recyclerView.adapter!!.notifyDataSetChanged()
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
                    lifecycleScope.launch(Dispatchers.IO) {
                        recipientDatabase.setBlocked(thread.recipient, false)
                        withContext(Dispatchers.Main) {
                            binding.recyclerView.adapter!!.notifyDataSetChanged()
                            dialog.dismiss()
                        }
                    }
                }.show()
    }

    private fun setConversationMuted(thread: ThreadRecord, isMuted: Boolean) {
        if (!isMuted) {
            lifecycleScope.launch(Dispatchers.IO) {
                recipientDatabase.setMuted(thread.recipient, 0)
                withContext(Dispatchers.Main) {
                    binding.recyclerView.adapter!!.notifyDataSetChanged()
                }
            }
        } else {
            MuteDialog.show(this) { until: Long ->
                lifecycleScope.launch(Dispatchers.IO) {
                    recipientDatabase.setMuted(thread.recipient, until)
                    withContext(Dispatchers.Main) {
                        binding.recyclerView.adapter!!.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun setNotifyType(thread: ThreadRecord, newNotifyType: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            recipientDatabase.setNotifyType(thread.recipient, newNotifyType)
            withContext(Dispatchers.Main) {
                binding.recyclerView.adapter!!.notifyDataSetChanged()
            }
        }
    }

    private fun setConversationPinned(threadId: Long, pinned: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            threadDb.setPinned(threadId, pinned)
            homeViewModel.tryUpdateChannel()
        }
    }

    private fun markAllAsRead(thread: ThreadRecord) {
        ThreadUtils.queue {
            threadDb.markAllAsRead(thread.threadId, thread.recipient.isOpenGroupRecipient)
        }
    }

    private fun deleteConversation(thread: ThreadRecord) {
        val threadID = thread.threadId
        val recipient = thread.recipient
        val message = if (recipient.isGroupRecipient) {
            val group = groupDatabase.getGroup(recipient.address.toString()).orNull()
            if (group != null && group.admins.map { it.toString() }.contains(textSecurePreferences.getLocalNumber())) {
                "Because you are the creator of this group it will be deleted for everyone. This cannot be undone."
            } else {
                resources.getString(R.string.activity_home_leave_group_dialog_message)
            }
        } else {
            resources.getString(R.string.activity_home_delete_conversation_dialog_message)
        }
        val dialog = AlertDialog.Builder(this)
        dialog.setMessage(message)
        dialog.setPositiveButton(R.string.yes) { _, _ ->
            lifecycleScope.launch(Dispatchers.Main) {
                val context = this@HomeActivity as Context
                // Cancel any outstanding jobs
                DatabaseComponent.get(context).sessionJobDatabase().cancelPendingMessageSendJobs(threadID)
                // Send a leave group message if this is an active closed group
                if (recipient.address.isClosedGroup && DatabaseComponent.get(context).groupDatabase().isActive(recipient.address.toGroupString())) {
                    var isClosedGroup: Boolean
                    var groupPublicKey: String?
                    try {
                        groupPublicKey = GroupUtil.doubleDecodeGroupID(recipient.address.toString()).toHexString()
                        isClosedGroup = DatabaseComponent.get(context).lokiAPIDatabase().isClosedGroup(groupPublicKey)
                    } catch (e: IOException) {
                        groupPublicKey = null
                        isClosedGroup = false
                    }
                    if (isClosedGroup) {
                        MessageSender.explicitLeave(groupPublicKey!!, false)
                    }
                }
                // Delete the conversation
                val v2OpenGroup = DatabaseComponent.get(this@HomeActivity).lokiThreadDatabase().getOpenGroupChat(threadID)
                if (v2OpenGroup != null) {
                    OpenGroupManager.delete(v2OpenGroup.server, v2OpenGroup.room, this@HomeActivity)
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        threadDb.deleteConversation(threadID)
                    }
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

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        show(intent, isForResult = true)
    }

    private fun showMessageRequests() {
        val intent = Intent(this, MessageRequestsActivity::class.java)
        push(intent)
    }

    private fun hideMessageRequests() {
        AlertDialog.Builder(this)
            .setMessage("Hide message requests?")
            .setPositiveButton(R.string.yes) { _, _ ->
                textSecurePreferences.setHasHiddenMessageRequests()
                setupMessageRequestsBanner()
                homeViewModel.tryUpdateChannel()
            }
            .setNegativeButton(R.string.no) { _, _ ->
                // Do nothing
            }
            .create().show()
    }

    private fun showNewConversation() {
        NewConversationFragment().show(supportFragmentManager, "NewConversationFragment")
    }

    // endregion
}
