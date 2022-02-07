package org.thoughtcrime.securesms.home.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewGlobalSearchHeaderBinding
import network.loki.messenger.databinding.ViewGlobalSearchResultBinding
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.search.model.MessageResult
import java.security.InvalidParameterException
import org.session.libsession.messaging.contacts.Contact as ContactModel

class GlobalSearchAdapter (private val modelCallback: (Model)->Unit): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val HEADER_VIEW_TYPE = 0
        const val CONTENT_VIEW_TYPE = 1
    }

    private var data: List<Model> = listOf()
    private var query: String? = null

    fun setNewData(query: String, newData: List<Model>) {
        val diffResult = DiffUtil.calculateDiff(GlobalSearchDiff(this.query, query, data, newData))
        this.query = query
        data = newData
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int =
        if (data[position] is Model.Header) HEADER_VIEW_TYPE else CONTENT_VIEW_TYPE

    override fun getItemCount(): Int = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == HEADER_VIEW_TYPE) {
            HeaderView(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.view_global_search_header, parent, false)
            )
        } else {
            ContentView(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.view_global_search_result, parent, false)
            , modelCallback)
        }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val newUpdateQuery: String? = payloads.firstOrNull { it is String } as String?
        if (newUpdateQuery != null && holder is ContentView) {
            holder.bindPayload(newUpdateQuery, data[position])
            return
        }
        if (holder is HeaderView) {
            holder.bind(data[position] as Model.Header)
        } else if (holder is ContentView) {
            holder.bind(query.orEmpty(), data[position])
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder,position, mutableListOf())
    }

    class HeaderView(view: View) : RecyclerView.ViewHolder(view) {

        val binding = ViewGlobalSearchHeaderBinding.bind(view)

        fun bind(header: Model.Header) {
            binding.searchHeader.setText(header.title)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ContentView) {
            holder.binding.searchResultProfilePicture.recycle()
        }
    }

    class ContentView(view: View, private val modelCallback: (Model) -> Unit) : RecyclerView.ViewHolder(view) {

        val binding = ViewGlobalSearchResultBinding.bind(view).apply {
            searchResultProfilePicture.glide = GlideApp.with(root)
        }

        fun bindPayload(newQuery: String, model: Model) {
            bindQuery(newQuery, model)
        }

        fun bind(query: String, model: Model) {
            binding.searchResultProfilePicture.recycle()
            when (model) {
                is Model.GroupConversation -> bindModel(query, model)
                is Model.Contact -> bindModel(query, model)
                is Model.Message -> bindModel(query, model)
                is Model.SavedMessages -> bindModel(model)
                is Model.Header -> throw InvalidParameterException("Can't display Model.Header as ContentView")
            }
            binding.root.setOnClickListener { modelCallback(model) }
        }

    }

    data class MessageModel(
            val threadRecipient: Recipient,
            val messageRecipient: Recipient,
            val messageSnippet: String
    )

    sealed class Model {
        data class Header(@StringRes val title: Int) : Model()
        data class SavedMessages(val currentUserPublicKey: String): Model()
        data class Contact(val contact: ContactModel) : Model()
        data class GroupConversation(val groupRecord: GroupRecord) : Model()
        data class Message(val messageResult: MessageResult, val unread: Int) : Model()
    }

}