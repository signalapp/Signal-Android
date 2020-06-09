package org.thoughtcrime.securesms.loki.activities

import android.R
import android.app.PendingIntent.getActivity
import android.content.Context
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import org.thoughtcrime.securesms.DeviceListItem
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.loki.dialogs.DeviceEditingOptionsBottomSheet
import org.thoughtcrime.securesms.loki.dialogs.GroupEditingOptionsBottomSheet
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.loki.views.UserView
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

class EditClosedGroupAdapter(private val context: Context) : RecyclerView.Adapter<EditClosedGroupAdapter.ViewHolder>() {
    lateinit var glide: GlideRequests
    val selectedMembers = mutableSetOf<String>()
    var members = listOf<String>()
        set(value) {
            field = value; notifyDataSetChanged()
        }
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
        viewHolder.view.bind(Recipient.from(context, Address.fromSerialized(member), false), isSelected, glide)
    }
}
