package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.loki.views.UserView
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

class EditClosedGroupMembersAdapter(
        private val context: Context,
        private val glide: GlideRequests,
        private val memberClickListener: ((String) -> Unit)? = null
) : RecyclerView.Adapter<EditClosedGroupMembersAdapter.ViewHolder>() {

    private val items = ArrayList<String>()

//    private val selectedItems = mutableSetOf<String>()

    fun setItems(items: Collection<String>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = UserView(context)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = items[position]
//        val isSelected = selectedItems.contains(item)
        viewHolder.view.bind(Recipient.from(context, Address.fromSerialized(item), false), false, glide, true)
        viewHolder.view.setOnClickListener { this.memberClickListener?.invoke(item) }
    }

    class ViewHolder(val view: UserView) : RecyclerView.ViewHolder(view)
}