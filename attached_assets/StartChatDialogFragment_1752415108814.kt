package org.thoughtcrime.securesms.crypto.fips

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.thoughtcrime.securesms.R // Assuming R file is available for string resources
import org.thoughtcrime.securesms.util.MdmPolicyManager

/**
 * An MDM-aware DialogFragment that prompts the user to choose a chat mode,
 * but only if enterprise policy allows it.
 *
 * This dialog is a key component for enterprise deployments. It integrates with
 * the [MdmPolicyManager] to enforce the "FIPS-Only Mode" policy.
 *
 * If FIPS-Only Mode is enforced by an administrator:
 * - The dialog will not be shown to the user.
 * - It will immediately invoke the `onAttemptFipsSession()` callback, forcing
 * the creation of a FIPS-compliant chat.
 *
 * If no policy is enforced, it displays the choice dialog as normal.
 */
class StartChatDialogFragment : DialogFragment() {

    private var listener: StartChatDialogListener? = null

    interface StartChatDialogListener {
        fun onAttemptFipsSession()
        fun onStartStandardSession()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Ensure the hosting context implements the listener interface.
        try {
            listener = parentFragment as StartChatDialogListener?
                ?: context as StartChatDialogListener?
        } catch (e: ClassCastException) {
            throw ClassCastException(
                (parentFragment?.toString() ?: context.toString()) +
                    " must implement StartChatDialogListener"
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // *** MDM POLICY ENFORCEMENT ***
        // Before creating the dialog, check the MDM policy.
        if (MdmPolicyManager.isFipsOnlyMode()) {
            // Policy is enforced. Bypass the UI and trigger the FIPS session attempt directly.
            // We create a transparent, empty dialog that we immediately dismiss after
            // invoking the callback.
            listener?.onAttemptFipsSession()
            
            val dialog = super.onCreateDialog(savedInstanceState)
            dialog.setOnShowListener {
                dismiss()
            }
            return dialog
        }

        // If policy is not enforced, proceed with showing the choice dialog to the user.
        val recipientName = arguments?.getString(ARG_RECIPIENT_NAME) ?: "this contact"

        return AlertDialog.Builder(requireActivity())
            .setTitle("Start a New Chat")
            .setMessage("How would you like to chat with $recipientName?")
            .setPositiveButton("Attempt FIPS Secure Chat") { _, _ ->
                listener?.onAttemptFipsSession()
            }
            .setNegativeButton("Standard Chat") { _, _ ->
                listener?.onStartStandardSession()
            }
            .setNeutralButton("Cancel", null)
            .create()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        const val TAG = "StartChatDialogFragment"
        private const val ARG_RECIPIENT_NAME = "recipient_name"

        @JvmStatic
        fun newInstance(recipientName: String): StartChatDialogFragment {
            return StartChatDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RECIPIENT_NAME, recipientName)
                }
            }
        }
    }
}
