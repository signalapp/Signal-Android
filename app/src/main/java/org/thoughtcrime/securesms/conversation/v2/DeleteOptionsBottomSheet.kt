package org.thoughtcrime.securesms.conversation.v2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_conversation_bottom_sheet.*
import kotlinx.android.synthetic.main.fragment_delete_message_bottom_sheet.*
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.util.UiModeUtilities

class DeleteOptionsBottomSheet: BottomSheetDialogFragment(), View.OnClickListener {

    lateinit var recipient: Recipient

    var onDeleteForMeTapped: (() -> Unit?)? = null
    var onDeleteForEveryoneTapped: (() -> Unit)? = null
    var onCancelTapped: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_delete_message_bottom_sheet, container, false)
    }

    override fun onClick(v: View?) {
        when (v) {
            deleteForMeTextView -> onDeleteForMeTapped?.invoke()
            deleteForEveryoneTextView -> onDeleteForEveryoneTapped?.invoke()
            cancelTextView -> onCancelTapped?.invoke()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!this::recipient.isInitialized) { return dismiss() }
        if (!recipient.isGroupRecipient) {
            deleteForEveryoneTextView.text = resources.getString(R.string.delete_message_for_me_and_recipient, recipient.name)
        }
        deleteForMeTextView.setOnClickListener(this)
        deleteForEveryoneTextView.setOnClickListener(this)
        cancelTextView.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val isLightMode = UiModeUtilities.isDayUiMode(requireContext())
        window.setDimAmount(if (isLightMode) 0.1f else 0.75f)
    }
}