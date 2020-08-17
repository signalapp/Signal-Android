package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.loki.views.UserView
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

class SelectContactsAdapter(
        private val context: Context,
        private val glide: GlideRequests)
    : RecyclerView.Adapter<SelectContactsAdapter.ViewHolder>() {

    val selectedMembers = mutableSetOf<String>()

    var members = listOf<String>()
        set(value) { field = value; notifyDataSetChanged() }

    class ViewHolder(val view: UserView) : RecyclerView.ViewHolder(view)

    override fun getItemCount(): Int {
        return members.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = UserView(context)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val member = members[position]
        viewHolder.view.setOnClickListener { onMemberClick(member) }
        val isSelected = selectedMembers.contains(member)
        viewHolder.view.bind(Recipient.from(
                context,
                Address.fromSerialized(member), false),
                glide,
                UserView.ActionIndicator.Tick,
                isSelected)
    }

    private fun onMemberClick(member: String) {
        if (selectedMembers.contains(member)) {
            selectedMembers.remove(member)
        } else {
            selectedMembers.add(member)
        }
        val index = members.indexOf(member)
        notifyItemChanged(index)
    }
}