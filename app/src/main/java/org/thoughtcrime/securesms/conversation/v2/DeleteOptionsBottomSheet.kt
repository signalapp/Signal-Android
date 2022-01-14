package org.thoughtcrime.securesms.conversation.v2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.FragmentDeleteMessageBottomSheetBinding
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.util.UiModeUtilities
import javax.inject.Inject

@AndroidEntryPoint
class DeleteOptionsBottomSheet : BottomSheetDialogFragment(), View.OnClickListener {

    @Inject
    lateinit var contactDatabase: SessionContactDatabase

    lateinit var recipient: Recipient
    private lateinit var binding: FragmentDeleteMessageBottomSheetBinding
    val contact by lazy {
        val senderId = recipient.address.serialize()
        // this dialog won't show for open group contacts
        contactDatabase.getContactWithSessionID(senderId)
            ?.displayName(Contact.ContactContext.REGULAR)
    }

    var onDeleteForMeTapped: (() -> Unit?)? = null
    var onDeleteForEveryoneTapped: (() -> Unit)? = null
    var onCancelTapped: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDeleteMessageBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.deleteForMeTextView -> onDeleteForMeTapped?.invoke()
            binding.deleteForEveryoneTextView -> onDeleteForEveryoneTapped?.invoke()
            binding.cancelTextView -> onCancelTapped?.invoke()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!this::recipient.isInitialized) {
            return dismiss()
        }
        if (!recipient.isGroupRecipient && !contact.isNullOrEmpty()) {
            binding.deleteForEveryoneTextView.text =
                resources.getString(R.string.delete_message_for_me_and_recipient, contact)
        }
        binding.deleteForEveryoneTextView.isVisible = !recipient.isClosedGroupRecipient
        binding.deleteForMeTextView.setOnClickListener(this)
        binding.deleteForEveryoneTextView.setOnClickListener(this)
        binding.cancelTextView.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val isLightMode = UiModeUtilities.isDayUiMode(requireContext())
        window.setDimAmount(if (isLightMode) 0.1f else 0.75f)
    }
}