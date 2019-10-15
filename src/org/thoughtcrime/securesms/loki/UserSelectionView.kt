package org.thoughtcrime.securesms.loki

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import nl.komponents.kovenant.combine.Tuple2
import org.thoughtcrime.securesms.database.DatabaseFactory

class UserSelectionView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : ListView(context, attrs, defStyleAttr) {
    private var users = listOf<Tuple2<String, String>>()
        set(newValue) { field = newValue; userSelectionViewAdapter.users = newValue }
    var publicChatServer: String? = null
        set(newValue) { field = newValue; userSelectionViewAdapter.publicChatServer = publicChatServer }
    var publicChatChannel: Long? = null
        set(newValue) { field = newValue; userSelectionViewAdapter.publicChatChannel = publicChatChannel }
    var onUserSelected: ((Tuple2<String, String>) -> Unit)? = null

    private val userSelectionViewAdapter by lazy { Adapter(context) }

    private class Adapter(private val context: Context) : BaseAdapter() {
        var users = listOf<Tuple2<String, String>>()
            set(newValue) { field = newValue; notifyDataSetChanged() }
        var publicChatServer: String? = null
        var publicChatChannel: Long? = null

        override fun getCount(): Int {
            return users.count()
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItem(position: Int): Tuple2<String, String> {
            return users[position]
        }

        override fun getView(position: Int, cellToBeReused: View?, parent: ViewGroup): View {
            val cell = cellToBeReused as UserSelectionViewCell? ?: UserSelectionViewCell.inflate(LayoutInflater.from(context), parent)
            val user = getItem(position)
            cell.user = user
            cell.publicChatServer = publicChatServer
            cell.publicChatChannel = publicChatChannel
            return cell
        }
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    init {
        adapter = userSelectionViewAdapter
        userSelectionViewAdapter.users = users
        setOnItemClickListener { _, _, position, _ ->
            onUserSelected?.invoke(users[position])
        }
    }

    fun show(users: List<Tuple2<String, String>>, threadID: Long) {
        val publicChat = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
        if (publicChat != null) {
            publicChatServer = publicChat.server
            publicChatChannel = publicChat.channel
        }
        this.users = users
        val layoutParams = this.layoutParams as ViewGroup.LayoutParams
        layoutParams.height = toPx(6 + Math.min(users.count(), 4) * 52, resources)
        this.layoutParams = layoutParams
    }

    fun hide() {
        val layoutParams = this.layoutParams as ViewGroup.LayoutParams
        layoutParams.height = 0
        this.layoutParams = layoutParams
    }
}