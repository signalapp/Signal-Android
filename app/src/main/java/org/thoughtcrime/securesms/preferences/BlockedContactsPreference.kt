package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

class BlockedContactsPreference @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null) : PreferenceCategory(context, attributeSet), View.OnClickListener {

    override fun onClick(v: View?) {
        if (v is BlockedContactsLayout) {
            val intent = Intent(context, BlockedContactsActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val itemView = holder.itemView
        itemView.setOnClickListener(this)
    }

}