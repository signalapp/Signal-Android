package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import org.thoughtcrime.securesms.loki.redesign.views.UserView
import org.thoughtcrime.securesms.mms.GlideRequests

class CreateClosedGroupAdapter(private val context: Context) : RecyclerView.Adapter<CreateClosedGroupAdapter.ViewHolder>() {
    lateinit var glide: GlideRequests
    val selectedMembers = mutableSetOf<String>()
    var members = listOf<String>()
        set(value) { field = value; notifyDataSetChanged() }
    var memberClickListener: MemberClickListener? = null

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
        viewHolder.view.setOnClickListener { memberClickListener?.onMemberClick(member) }
        val isSelected = selectedMembers.contains(member)
        viewHolder.view.bind(member, isSelected, glide)
    }

    fun onMemberClick(member: String) {
        if (selectedMembers.contains(member)) {
            selectedMembers.remove(member)
        } else {
            selectedMembers.add(member)
        }
        val index = members.indexOf(member)
        notifyItemChanged(index)
    }
}

interface MemberClickListener {

    fun onMemberClick(member: String)
}