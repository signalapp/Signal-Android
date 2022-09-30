package org.thoughtcrime.securesms.dms

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.FragmentNewMessageBinding
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.conversation.start.NewConversationDelegate
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

@AndroidEntryPoint
class NewMessageFragment : Fragment() {

    private lateinit var binding: FragmentNewMessageBinding

    lateinit var delegate: NewConversationDelegate

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNewMessageBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { delegate.onDialogBackPressed() }
        binding.closeButton.setOnClickListener { delegate.onDialogClosePressed() }
        val onsOrPkDelegate = { onsNameOrPublicKey: String -> createPrivateChatIfPossible(onsNameOrPublicKey)}
        val adapter = NewMessageFragmentAdapter(
            parentFragment = this,
            enterPublicKeyDelegate = onsOrPkDelegate,
            scanPublicKeyDelegate = onsOrPkDelegate
        )
        binding.viewPager.adapter = adapter
        val mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.activity_create_private_chat_enter_session_id_tab_title)
                1 -> getString(R.string.activity_create_private_chat_scan_qr_code_tab_title)
                else -> throw IllegalStateException()
            }
        }
        mediator.attach()
    }

    private fun createPrivateChatIfPossible(onsNameOrPublicKey: String) {
        if (PublicKeyValidation.isValid(onsNameOrPublicKey)) {
            createPrivateChat(onsNameOrPublicKey)
        } else {
            // This could be an ONS name
            showLoader()
            SnodeAPI.getSessionID(onsNameOrPublicKey).successUi { hexEncodedPublicKey ->
                hideLoader()
                createPrivateChat(hexEncodedPublicKey)
            }.failUi { exception ->
                hideLoader()
                var message = getString(R.string.fragment_enter_public_key_error_message)
                exception.localizedMessage?.let {
                    message = it
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createPrivateChat(hexEncodedPublicKey: String) {
        val recipient = Recipient.from(requireContext(), Address.fromSerialized(hexEncodedPublicKey), false)
        val intent = Intent(requireContext(), ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.ADDRESS, recipient.address)
        intent.setDataAndType(requireActivity().intent.data, requireActivity().intent.type)
        val existingThread = DatabaseComponent.get(requireContext()).threadDatabase().getThreadIdIfExistsFor(recipient)
        intent.putExtra(ConversationActivityV2.THREAD_ID, existingThread)
        requireContext().startActivity(intent)
        delegate.onDialogClosePressed()
    }

    private fun showLoader() {
        binding.loader.visibility = View.VISIBLE
        binding.loader.animate().setDuration(150).alpha(1.0f).start()
    }

    private fun hideLoader() {
        binding.loader.animate().setDuration(150).alpha(0.0f).setListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                binding.loader.visibility = View.GONE
            }
        })
    }
}