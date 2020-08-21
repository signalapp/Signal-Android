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
    var onBlockTapped: (() -> Unit)? = null
    var onUnblockTapped: (() -> Unit)? = null
    var onDeleteTapped: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_conversation_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!recipient.isGroupRecipient && !recipient.isLocalNumber) {
            unblockTextView.visibility = if (recipient.isBlocked) View.VISIBLE else View.GONE
            blockTextView.visibility = if (recipient.isBlocked) View.GONE else View.VISIBLE

            blockTextView.setOnClickListener { onBlockTapped?.invoke() }
            unblockTextView.setOnClickListener { onUnblockTapped?.invoke() }
        }
        deleteTextView.setOnClickListener { onDeleteTapped?.invoke() }
    }
}