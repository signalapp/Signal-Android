package org.thoughtcrime.securesms.loki.redesign.activities

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_home.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.getColorWithID
import org.thoughtcrime.securesms.loki.redesign.utilities.push
import org.thoughtcrime.securesms.loki.redesign.views.ConversationView
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.TextSecurePreferences
import kotlin.math.abs

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
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

    private class SwipeCallback(val context: Context) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        @SuppressLint("StaticFieldLeak")
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // TODO: Implement
        }

        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dx: Float, dy: Float, actionState: Int, isCurrentlyActive: Boolean) {
            if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
                super.onChildDraw(c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive)
            } else {
                val itemView = viewHolder.itemView
                val alpha = 1.0f - abs(dx) / viewHolder.itemView.width.toFloat()
                if (dx < 0) {
                    val backgroundPaint = Paint()
                    backgroundPaint.color = context.resources.getColorWithID(R.color.destructive, context.theme)
                    c.drawRect(itemView.right.toFloat() - abs(dx), itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat(), backgroundPaint)
                    val icon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_trash_filled_32)
                    val iconPaint = Paint()
                    val left = itemView.right.toFloat() - abs(dx) + context.resources.getDimension(R.dimen.medium_spacing)
                    val top = itemView.top.toFloat() + (itemView.bottom.toFloat() - itemView.top.toFloat() - icon.height) / 2
                    c.drawBitmap(icon, left, top, iconPaint)
                }
                viewHolder.itemView.alpha = alpha
                viewHolder.itemView.translationX = dx
            }
        }
    }
    // endregion
}