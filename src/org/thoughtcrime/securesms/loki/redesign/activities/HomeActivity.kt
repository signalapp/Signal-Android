package org.thoughtcrime.securesms.loki.redesign.activities

import android.arch.lifecycle.Observer
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_home.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.redesign.utilities.push
import org.thoughtcrime.securesms.loki.redesign.views.ConversationView
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.TextSecurePreferences

class HomeActivity : PassphraseRequiredActionBarActivity, ConversationClickListener {
    private lateinit var glide: GlideRequests

    // region Lifecycle
    constructor() : super()

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Set content view
        setContentView(R.layout.activity_home)
        // Set title
        supportActionBar!!.title = "Messages"
        // Set up Glide
        glide = GlideApp.with(this)
        // Set up recycler view
        val cursor = DatabaseFactory.getThreadDatabase(this).conversationList
        val homeAdapter = HomeAdapter(this, cursor)
        homeAdapter.glide = glide
        homeAdapter.conversationClickListener = this
        recyclerView.adapter = homeAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        // Set up new conversation button
        newConversationButton.setOnClickListener { createPrivateChat() }
        // Set up typing observer
        ApplicationContext.getInstance(this).typingStatusRepository.typingThreads.observe(this, object : Observer<Set<Long>> {

            override fun onChanged(threadIDs: Set<Long>?) {
                val adapter = recyclerView.adapter as HomeAdapter
                adapter.typingThreadIDs = threadIDs ?: setOf()
            }
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.getItemId()
        when (id) {
            R.id.joinPublicChatItem -> joinPublicChat()
            else -> { /* Do nothing */ }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onConversationClick(view: ConversationView) {
        val thread = view.thread ?: return
        openConversation(thread)
    }

    override fun onLongConversationClick(view: ConversationView) {
        // TODO: Implement
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

    private fun createPrivateChat() {
        val intent = Intent(this, CreatePrivateChatActivity::class.java)
        startActivity(intent)
    }

    private fun joinPublicChat() {
        val intent = Intent(this, JoinPublicChatActivity::class.java)
        startActivity(intent)
    }
    // endregion
}