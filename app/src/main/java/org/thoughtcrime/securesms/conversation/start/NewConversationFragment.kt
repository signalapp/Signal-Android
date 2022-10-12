package org.thoughtcrime.securesms.conversation.start

import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.dms.NewMessageFragment
import org.thoughtcrime.securesms.groups.CreateGroupFragment
import org.thoughtcrime.securesms.groups.JoinCommunityFragment

@AndroidEntryPoint
class NewConversationFragment : BottomSheetDialogFragment(), NewConversationDelegate {

    private val defaultPeekHeight: Int by lazy { (Resources.getSystem().displayMetrics.heightPixels * 0.94).toInt() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_new_conversation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        replaceFragment(
            fragment = NewConversationHomeFragment().apply { delegate = this@NewConversationFragment },
            fragmentKey = NewConversationHomeFragment::class.java.simpleName
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), R.style.Theme_Session_BottomSheet)
        dialog.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            val parentLayout =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            parentLayout?.let { it ->
                val behaviour = BottomSheetBehavior.from(it)
                val layoutParams = it.layoutParams
                layoutParams.height = defaultPeekHeight
                it.layoutParams = layoutParams
                behaviour.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onNewMessageSelected() {
        replaceFragment(NewMessageFragment().apply { delegate = this@NewConversationFragment })
    }

    override fun onCreateGroupSelected() {
        replaceFragment(CreateGroupFragment().apply { delegate = this@NewConversationFragment })
    }

    override fun onJoinCommunitySelected() {
        replaceFragment(JoinCommunityFragment().apply { delegate = this@NewConversationFragment })
    }

    override fun onContactSelected(address: String) {
        val intent = Intent(requireContext(), ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.ADDRESS, Address.fromSerialized(address))
        requireContext().startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_from_bottom, R.anim.fade_scale_out)
    }

    override fun onDialogBackPressed() {
        childFragmentManager.popBackStack()
    }

    override fun onDialogClosePressed() {
        dismiss()
    }

    private fun replaceFragment(fragment: Fragment, fragmentKey: String? = null) {
        childFragmentManager.commit {
            setCustomAnimations(
                R.anim.slide_from_right,
                R.anim.fade_scale_out,
                0,
                R.anim.slide_to_right
            )
            replace(R.id.new_conversation_fragment_container, fragment)
            addToBackStack(fragmentKey)
        }
    }
}