package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.loki.views.UserView
import org.thoughtcrime.securesms.mms.GlideRequests
import org.session.libsession.utilities.recipients.Recipient

class SelectContactsAdapter(private val context: Context, private val glide: GlideRequests) : RecyclerView.Adapter<SelectContactsAdapter.ViewHolder>() {
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

    override fun onBindViewHolder(viewHolder: ViewHolder,
                                  position: Int,
                                  payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            // Because these updates can be batched,
            // there can be multiple payloads for a single bind
            when (payloads[0]) {
                Payload.MEMBER_CLICKED -> {
                    val member = members[position]
                    val isSelected = selectedMembers.contains(member)
                    viewHolder.view.toggleCheckbox(isSelected)
                }
            }
        } else {
            // When payload list is empty,
            // or we don't have logic to handle a given type,
            // default to full bind:
            this.onBindViewHolder(viewHolder, position)
        }
    }

    private fun onMemberClick(member: String) {
        if (selectedMembers.contains(member)) {
            selectedMembers.remove(member)
        } else {
            selectedMembers.add(member)
        }
        val index = members.indexOf(member)
        notifyItemChanged(index, Payload.MEMBER_CLICKED)
    }

    // define below the different events used to notify the adapter
    enum class Payload {
        MEMBER_CLICKED
    }
}
