package org.thoughtcrime.securesms.loki.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.opengl.Visibility
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_user_details_bottom_sheet.*
import kotlinx.android.synthetic.main.view_conversation.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.SSKEnvironment
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.mms.GlideApp

public class UserDetailsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_user_details_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val publicKey = arguments?.getString("publicKey") ?: return dismiss()
        val recipient = Recipient.from(requireContext(), Address.fromSerialized(publicKey), false)
        profilePictureView.publicKey = publicKey
        profilePictureView.glide = GlideApp.with(this)
        profilePictureView.isLarge = true
        profilePictureView.update()
        nameTextViewContainer.visibility = View.VISIBLE
        nameTextViewContainer.setOnClickListener {
            nameTextViewContainer.visibility = View.INVISIBLE
            nameEditContainer.visibility = View.VISIBLE
            nameEditText.requestFocus()
        }
        btnCancelNickNameEdit.setOnClickListener {
            nameEditText.clearFocus()
            nameTextViewContainer.visibility = View.VISIBLE
            nameEditContainer.visibility = View.INVISIBLE
            nameEditText.text = null
        }
        btnSaveNickNameEdit.setOnClickListener {
            nameEditText.clearFocus()
            nameTextViewContainer.visibility = View.VISIBLE
            nameEditContainer.visibility = View.INVISIBLE
            var newNickName = null
            SSKEnvironment.shared.profileManager.setDisplayName(requireContext(), recipient, newNickName)
            nameTextView.text = SSKEnvironment.shared.profileManager.getDisplayName(requireContext(), recipient) ?: "Anonymous"
        }
        nameTextView.text = SSKEnvironment.shared.profileManager.getDisplayName(requireContext(), recipient) ?: "Anonymous"
        publicKeyTextView.text = publicKey
        copyButton.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Session ID", publicKey)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }
}