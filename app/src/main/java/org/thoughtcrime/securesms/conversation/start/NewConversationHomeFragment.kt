package org.thoughtcrime.securesms.conversation.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.FragmentNewConversationHomeBinding
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideApp
import javax.inject.Inject

@AndroidEntryPoint
class NewConversationHomeFragment : Fragment() {

    private lateinit var binding: FragmentNewConversationHomeBinding
    private val viewModel: NewConversationHomeViewModel by viewModels()

    @Inject
    lateinit var textSecurePreferences: TextSecurePreferences

    lateinit var delegate: NewConversationDelegate

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNewConversationHomeBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeButton.setOnClickListener { delegate.onDialogClosePressed() }
        binding.createPrivateChatButton.setOnClickListener { delegate.onNewMessageSelected() }
        binding.createClosedGroupButton.setOnClickListener { delegate.onCreateGroupSelected() }
        binding.joinCommunityButton.setOnClickListener { delegate.onJoinCommunitySelected() }
        val adapter = ContactListAdapter(requireContext(), GlideApp.with(requireContext())) {
            delegate.onContactSelected(it.address.serialize())
        }
        val unknownSectionTitle = getString(R.string.new_conversation_unknown_contacts_section_title)
        val recipients = viewModel.recipients.value?.filter { !it.isGroupRecipient && it.address.serialize() != textSecurePreferences.getLocalNumber()!! } ?: emptyList()
        val contactGroups = recipients.map {
            val sessionId = it.address.serialize()
            val contact = DatabaseComponent.get(requireContext()).sessionContactDatabase().getContactWithSessionID(sessionId)
            val displayName = contact?.displayName(Contact.ContactContext.REGULAR) ?: sessionId
            ContactListItem.Contact(it, displayName)
        }.sortedBy { it.displayName }
            .groupBy { if (PublicKeyValidation.isValid(it.displayName)) unknownSectionTitle else it.displayName.first().uppercase() }
            .toMutableMap()
        contactGroups.remove(unknownSectionTitle)?.let { contactGroups.put(unknownSectionTitle, it) }
        adapter.items = contactGroups.flatMap { entry -> listOf(ContactListItem.Header(entry.key)) + entry.value }
        binding.contactsRecyclerView.adapter = adapter
        val divider = ContextCompat.getDrawable(requireActivity(), R.drawable.conversation_menu_divider)!!.let {
            DividerItemDecoration(requireActivity(), RecyclerView.VERTICAL).apply {
                setDrawable(it)
            }
        }
        binding.contactsRecyclerView.addItemDecoration(divider)
    }
}