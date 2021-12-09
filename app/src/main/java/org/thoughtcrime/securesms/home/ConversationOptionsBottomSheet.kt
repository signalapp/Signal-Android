package org.thoughtcrime.securesms.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_conversation_bottom_sheet.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.util.UiModeUtilities

public class ConversationOptionsBottomSheet : BottomSheetDialogFragment(), View.OnClickListener {

    //FIXME AC: Supplying a threadRecord directly into the field from an activity
    // is not the best idea. It doesn't survive configuration change.
    // We should be dealing with IDs and all sorts of serializable data instead
    // if we want to use dialog fragments properly.
    lateinit var thread: ThreadRecord

    var onViewDetailsTapped: (() -> Unit?)? = null
    var onPinTapped: (() -> Unit)? = null
    var onUnpinTapped: (() -> Unit)? = null
    var onBlockTapped: (() -> Unit)? = null
    var onUnblockTapped: (() -> Unit)? = null
    var onDeleteTapped: (() -> Unit)? = null
    var onNotificationTapped: (() -> Unit)? = null
    var onSetMuteTapped: ((Boolean) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_conversation_bottom_sheet, container, false)
    }

    override fun onClick(v: View?) {
        when (v) {
            detailsTextView -> onViewDetailsTapped?.invoke()
            pinTextView -> onPinTapped?.invoke()
            unpinTextView -> onUnpinTapped?.invoke()
            blockTextView -> onBlockTapped?.invoke()
            unblockTextView -> onUnblockTapped?.invoke()
            deleteTextView -> onDeleteTapped?.invoke()
            notificationsTextView -> onNotificationTapped?.invoke()
            unMuteNotificationsTextView -> onSetMuteTapped?.invoke(false)
            muteNotificationsTextView -> onSetMuteTapped?.invoke(true)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!this::thread.isInitialized) { return dismiss() }
        val recipient = thread.recipient
        if (!recipient.isGroupRecipient && !recipient.isLocalNumber) {
            detailsTextView.visibility = View.VISIBLE
            unblockTextView.visibility = if (recipient.isBlocked) View.VISIBLE else View.GONE
            blockTextView.visibility = if (recipient.isBlocked) View.GONE else View.VISIBLE
            detailsTextView.setOnClickListener(this)
            blockTextView.setOnClickListener(this)
            unblockTextView.setOnClickListener(this)
        } else {
            detailsTextView.visibility = View.GONE
        }
        unMuteNotificationsTextView.isVisible = recipient.isMuted && !recipient.isLocalNumber
        muteNotificationsTextView.isVisible = !recipient.isMuted && !recipient.isLocalNumber
        unMuteNotificationsTextView.setOnClickListener(this)
        muteNotificationsTextView.setOnClickListener(this)
        notificationsTextView.isVisible = recipient.isGroupRecipient && !recipient.isMuted
        notificationsTextView.setOnClickListener(this)
        deleteTextView.setOnClickListener(this)
        pinTextView.isVisible = !thread.isPinned
        unpinTextView.isVisible = thread.isPinned
        pinTextView.setOnClickListener(this)
        unpinTextView.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val isLightMode = UiModeUtilities.isDayUiMode(requireContext())
        window.setDimAmount(if (isLightMode) 0.1f else 0.75f)
    }
}