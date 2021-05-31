package org.thoughtcrime.securesms.conversation.v2

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.DatabaseFactory

class ConversationActivityV2 : PassphraseRequiredActionBarActivity() {
    private var threadID: Long = -1

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
    }

    private fun setUpRecyclerView() {
        val cursor = DatabaseFactory.getMmsSmsDatabase(this).getConversation(threadID)
        val adapter = ConversationAdapter(this, cursor)
        adapter.setHasStableIds(true)
        conversationRecyclerView.adapter = adapter
        conversationRecyclerView.layoutManager = LinearLayoutManager(this)
    }
    // endregion
}