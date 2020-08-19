package org.thoughtcrime.securesms.loki.dialogs

import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_conversation_bottom_sheet.*
import kotlinx.android.synthetic.main.fragment_device_list_bottom_sheet.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.recipients.Recipient

public class ConversationOptionsBottomSheet : BottomSheetDialogFragment() {
    lateinit var recipient: Recipient
    var onBlockOrUnblockTapped: (() -> Unit)? = null
    var onDeleteTapped: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_conversation_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!recipient.isGroupRecipient && !recipient.isLocalNumber) {
            val textID = if (recipient.isBlocked) R.string.RecipientPreferenceActivity_unblock else R.string.RecipientPreferenceActivity_block
            blockOrUnblockTextView.setText(textID)
            val iconID = if (recipient.isBlocked) R.drawable.ic_check_white_24dp else R.drawable.ic_block_white_24dp
            val icon = context!!.resources.getDrawable(iconID, context!!.theme)
            blockOrUnblockTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            blockOrUnblockTextView.setOnClickListener { onBlockOrUnblockTapped?.invoke() }
        } else {
            blockOrUnblockTextView.visibility = View.GONE
        }
        deleteTextView.setOnClickListener { onDeleteTapped?.invoke() }
    }
}