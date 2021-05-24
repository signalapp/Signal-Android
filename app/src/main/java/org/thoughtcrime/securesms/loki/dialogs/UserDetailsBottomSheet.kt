package org.thoughtcrime.securesms.loki.dialogs

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_user_details_bottom_sheet.*
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.SSKEnvironment
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.mms.GlideApp

class UserDetailsBottomSheet : BottomSheetDialogFragment() {

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
            nameEditTextContainer.visibility = View.VISIBLE
            nicknameEditText.text = null
            nicknameEditText.requestFocus()
            showSoftKeyboard()
        }
        cancelNicknameEditingButton.setOnClickListener {
            nicknameEditText.clearFocus()
            hideSoftKeyboard()
            nameTextViewContainer.visibility = View.VISIBLE
            nameEditTextContainer.visibility = View.INVISIBLE
        }
        saveNicknameButton.setOnClickListener {
            saveNickName(recipient)
        }
        nicknameEditText.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    saveNickName(recipient)
                    return@setOnEditorActionListener true
                }
                else -> return@setOnEditorActionListener false
            }
        }
        nameTextView.text = recipient.name ?: publicKey // Uses the Contact API internally
        publicKeyTextView.text = publicKey
        copyButton.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Session ID", publicKey)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    fun saveNickName(recipient: Recipient) {
        nicknameEditText.clearFocus()
        hideSoftKeyboard()
        nameTextViewContainer.visibility = View.VISIBLE
        nameEditTextContainer.visibility = View.INVISIBLE
        var newNickName: String? = null
        if (nicknameEditText.text.isNotEmpty()) {
            newNickName = nicknameEditText.text.toString()
        }
        val publicKey = recipient.address.serialize()
        val contactDB = DatabaseFactory.getSessionContactDatabase(context)
        val contact = contactDB.getContactWithSessionID(publicKey) ?: Contact(publicKey)
        contact.nickname = newNickName
        contactDB.setContact(contact)
        nameTextView.text = recipient.name ?: publicKey // Uses the Contact API internally
    }

    @SuppressLint("ServiceCast")
    fun showSoftKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(nicknameEditText, 0)
    }

    fun hideSoftKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(nicknameEditText.windowToken, 0)
    }
}