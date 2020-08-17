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

    private val members = ArrayList<String>()
    private val lockedMembers = HashSet<String>()

    fun setMembers(members: Collection<String>) {
        this.members.clear()
        this.members.addAll(members)
        notifyDataSetChanged()
    }

    fun setLockedMembers(members: Collection<String>) {
        this.lockedMembers.clear()
        this.lockedMembers.addAll(members)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = members.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = UserView(context)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val member = members[position]

        val lockedMember = lockedMembers.contains(member)

        viewHolder.view.bind(Recipient.from(
                context,
                Address.fromSerialized(member), false),
                glide,
                (if (lockedMember) UserView.ActionIndicator.NONE else UserView.ActionIndicator.MENU))

        if (!lockedMember) {
            viewHolder.view.setOnClickListener { this.memberClickListener?.invoke(member) }
        }
    }

    class ViewHolder(val view: UserView) : RecyclerView.ViewHolder(view)
}