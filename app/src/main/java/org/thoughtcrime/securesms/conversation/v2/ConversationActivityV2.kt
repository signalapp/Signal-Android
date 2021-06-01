package org.thoughtcrime.securesms.conversation.v2

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.mms.GlideApp

class ConversationActivityV2 : PassphraseRequiredActionBarActivity() {
    private var threadID: Long = -1

    private val thread by lazy {
        DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadID)!!
    }

    private val glide by lazy { GlideApp.with(this) }

    // region Settings
    companion object {
        const val THREAD_ID = "thread_id"
    }
    // endregion

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_conversation_v2)
        threadID = intent.getLongExtra(THREAD_ID, -1)
        setUpRecyclerView()
        setUpToolbar()
    }

    private fun setUpRecyclerView() {
        val cursor = DatabaseFactory.getMmsSmsDatabase(this).getConversation(threadID)
        val adapter = ConversationAdapter(this, cursor)
        adapter.setHasStableIds(true)
        conversationRecyclerView.adapter = adapter
        conversationRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setUpToolbar() {
        backButton.setOnClickListener { onBackPressed() }
        conversationTitleView.text = thread.toShortString()
        conversationSettingsButton.glide = glide
        conversationSettingsButton.update(thread, threadID)
        conversationSettingsButton.setOnClickListener { showConversationSettings() }
    }
    // endregion

    // region Interaction
    private fun showConversationSettings() {
        // TODO: Implement
    }
    // endregion
}