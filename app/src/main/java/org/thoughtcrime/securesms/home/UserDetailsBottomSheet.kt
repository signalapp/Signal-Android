package org.thoughtcrime.securesms.home

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.FragmentUserDetailsBottomSheetBinding
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.UiModeUtilities
import javax.inject.Inject

@AndroidEntryPoint
class UserDetailsBottomSheet: BottomSheetDialogFragment() {

    @Inject lateinit var threadDb: ThreadDatabase

    private lateinit var binding: FragmentUserDetailsBottomSheetBinding
    companion object {
        const val ARGUMENT_PUBLIC_KEY = "publicKey"
        const val ARGUMENT_THREAD_ID = "threadId"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val wrappedContext = ContextThemeWrapper(requireActivity(), requireActivity().theme)
        val themedInflater = inflater.cloneInContext(wrappedContext)
        binding = FragmentUserDetailsBottomSheetBinding.inflate(themedInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val publicKey = arguments?.getString(ARGUMENT_PUBLIC_KEY) ?: return dismiss()
        val threadID = arguments?.getLong(ARGUMENT_THREAD_ID) ?: return dismiss()
        val recipient = Recipient.from(requireContext(), Address.fromSerialized(publicKey), false)
        val threadRecipient = threadDb.getRecipientForThreadId(threadID) ?: return dismiss()
        with(binding) {
            profilePictureView.root.publicKey = publicKey
            profilePictureView.root.glide = GlideApp.with(this@UserDetailsBottomSheet)
            profilePictureView.root.isLarge = true
            profilePictureView.root.update(recipient)
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

            publicKeyTextView.isVisible = !threadRecipient.isOpenGroupRecipient && !threadRecipient.isOpenGroupInboxRecipient
            messageButton.isVisible = !threadRecipient.isOpenGroupRecipient || IdPrefix.fromValue(publicKey) == IdPrefix.BLINDED
            publicKeyTextView.text = publicKey
            publicKeyTextView.setOnLongClickListener {
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Session ID", publicKey)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT)
                    .show()
                true
            }
            messageButton.setOnClickListener {
                val threadId = MessagingModuleConfiguration.shared.storage.getThreadId(recipient)
                val intent = Intent(
                    context,
                    ConversationActivityV2::class.java
                )
                intent.putExtra(ConversationActivityV2.ADDRESS, recipient.address)
                intent.putExtra(ConversationActivityV2.THREAD_ID, threadId ?: -1)
                intent.putExtra(ConversationActivityV2.FROM_GROUP_THREAD_ID, threadID)
                startActivity(intent)
                dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val isLightMode = UiModeUtilities.isDayUiMode(requireContext())
        window.setDimAmount(if (isLightMode) 0.1f else 0.75f)
    }

    fun saveNickName(recipient: Recipient) = with(binding) {
        nicknameEditText.clearFocus()
        hideSoftKeyboard()
        nameTextViewContainer.visibility = View.VISIBLE
        nameEditTextContainer.visibility = View.INVISIBLE
        var newNickName: String? = null
        if (nicknameEditText.text.isNotEmpty()) {
            newNickName = nicknameEditText.text.toString()
        }
        val publicKey = recipient.address.serialize()
        val contactDB = DatabaseComponent.get(requireContext()).sessionContactDatabase()
        val contact = contactDB.getContactWithSessionID(publicKey) ?: Contact(publicKey)
        contact.nickname = newNickName
        contactDB.setContact(contact)
        nameTextView.text = recipient.name ?: publicKey // Uses the Contact API internally
    }

    @SuppressLint("ServiceCast")
    fun showSoftKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.nicknameEditText, 0)
    }

    fun hideSoftKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.nicknameEditText.windowToken, 0)
    }
}