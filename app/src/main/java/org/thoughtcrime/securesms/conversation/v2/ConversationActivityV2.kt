package org.thoughtcrime.securesms.conversation.v2

import android.database.Cursor
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import kotlinx.android.synthetic.main.activity_conversation_v2_action_bar.*
import network.loki.messenger.R
import org.session.libsession.utilities.ExpirationUtil
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import org.thoughtcrime.securesms.mms.GlideApp

class ConversationActivityV2 : PassphraseRequiredActionBarActivity() {
    private var threadID: Long = -1

    private val adapter by lazy {
        val cursor = DatabaseFactory.getMmsSmsDatabase(this).getConversation(threadID)
        val adapter = ConversationAdapter(this, cursor) { handleLongPress(it) }
        adapter.setHasStableIds(true)
        adapter
    }

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
        conversationRecyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(this)
        layoutManager.reverseLayout = true
        layoutManager.stackFromEnd = true
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
        val touchHelperCallback = ConversationTouchHelperCallback(adapter, this) { reply(it) }
        val touchHelper = ItemTouchHelper(touchHelperCallback)
        touchHelper.attachToRecyclerView(conversationRecyclerView)
    }

    private fun setUpToolbar() {
        val actionBar = supportActionBar!!
        actionBar.setCustomView(R.layout.activity_conversation_v2_action_bar)
        actionBar.setDisplayShowCustomEnabled(true)
        conversationTitleView.text = thread.toShortString()
        profilePictureView.glide = glide
        profilePictureView.update(thread, threadID)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Prepare
        menu.clear()
        val isOpenGroup = thread.isOpenGroupRecipient
        // Base menu (options that should always be present)
        menuInflater.inflate(R.menu.menu_conversation, menu)
        // Expiring messages
        if (!isOpenGroup) {
            if (thread.expireMessages > 0) {
                menuInflater.inflate(R.menu.menu_conversation_expiration_on, menu)
                val item = menu.findItem(R.id.menu_expiring_messages)
                val actionView = item.actionView
                val iconView = actionView.findViewById<ImageView>(R.id.menu_badge_icon)
                val badgeView = actionView.findViewById<TextView>(R.id.expiration_badge)
                @ColorInt val color = resources.getColorWithID(R.color.text, theme)
                iconView.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
                badgeView.text = ExpirationUtil.getExpirationAbbreviatedDisplayValue(this, thread.expireMessages)
                actionView.setOnClickListener { onOptionsItemSelected(item) }
            } else {
                menuInflater.inflate(R.menu.menu_conversation_expiration_off, menu)
            }
        }
        // One-on-one chat menu (options that should only be present for one-on-one chats)
        if (thread.isContactRecipient) {
            if (thread.isBlocked) {
                menuInflater.inflate(R.menu.menu_conversation_unblock, menu)
            } else {
                menuInflater.inflate(R.menu.menu_conversation_block, menu)
            }
            menuInflater.inflate(R.menu.menu_conversation_copy_session_id, menu)
        }
        // Closed group menu (options that should only be present in closed groups)
        if (thread.isClosedGroupRecipient) {
            menuInflater.inflate(R.menu.menu_conversation_closed_group, menu)
        }
        // Open group menu
        if (isOpenGroup) {
            menuInflater.inflate(R.menu.menu_conversation_open_group, menu)
        }
        // Return
        return true
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // TODO: Implement
        return super.onOptionsItemSelected(item)
    }

    private fun reply(messagePosition: Int) {
        Log.d("Loki", "Reply to message at position: $messagePosition.")
    }

    private fun handleLongPress(messagePosition: Int) {
        val actionModeCallback = ConversationActionModeCallback()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            startActionMode(actionModeCallback, ActionMode.TYPE_PRIMARY)
        } else {
            startActionMode(actionModeCallback)
        }
    }
    // endregion
}