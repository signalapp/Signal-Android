package org.thoughtcrime.securesms.loki.dialogs

import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_conversation_bottom_sheet.*
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient

public class ConversationOptionsBottomSheet : BottomSheetDialogFragment() {

    //FIXME AC: Supplying a recipient directly into the field from an activity
    // is not the best idea. It doesn't survive configuration change.
    // We should be dealing with IDs and all sorts of serializable data instead
    // if we want to use dialog fragments properly.
    lateinit var recipient: Recipient

    var onViewDetailsTapped: (() -> Unit?)? = null
    var onBlockTapped: (() -> Unit)? = null
    var onUnblockTapped: (() -> Unit)? = null
    var onDeleteTapped: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_conversation_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!this::recipient.isInitialized) { return dismiss() }
        if (!recipient.isGroupRecipient && !recipient.isLocalNumber) {
            detailsTextView.visibility = View.VISIBLE
            unblockTextView.visibility = if (recipient.isBlocked) View.VISIBLE else View.GONE
            blockTextView.visibility = if (recipient.isBlocked) View.GONE else View.VISIBLE
            detailsTextView.setOnClickListener { onViewDetailsTapped?.invoke() }
            blockTextView.setOnClickListener { onBlockTapped?.invoke() }
            unblockTextView.setOnClickListener { onUnblockTapped?.invoke() }
        } else {
            detailsTextView.visibility = View.GONE
        }
        deleteTextView.setOnClickListener { onDeleteTapped?.invoke() }
    }
}