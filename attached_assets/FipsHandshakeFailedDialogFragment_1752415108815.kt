package org.thoughtcrime.securesms.crypto.fips

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.thoughtcrime.securesms.R // Assuming R file is available for string resources

/**
 * A DialogFragment that displays a prominent, non-dismissible warning to the user
 * when an attempt to establish a FIPS-compliant session has failed.
 *
 * This is a critical security and UX component. It ensures that the user is
 * explicitly notified that their conversation, which they intended to be FIPS-secure,
 * has fallen back to a standard Signal session.
 *
 * The dialog is intentionally non-cancellable to force the user to acknowledge
 * the change in security context before proceeding.
 */
class FipsHandshakeFailedDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make the dialog non-cancellable. The user must explicitly press "OK".
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val recipientName = arguments?.getString(ARG_RECIPIENT_NAME) ?: "this contact"

        // Use AlertDialog.Builder to construct the warning dialog.
        // In a real app, strings would be from R.string resources.
        return AlertDialog.Builder(requireActivity())
            .setTitle("FIPS Session Failed")
            .setMessage(
                "Could not establish a FIPS-compliant session with $recipientName, likely because they are not using a FIPS-capable client.\n\n" +
                "This conversation will proceed as a standard, end-to-end encrypted Signal chat."
            )
            .setPositiveButton("OK, I Understand") { dialog, _ ->
                // Simply dismiss the dialog. The hosting activity will handle
                // ensuring the UI is in the standard theme.
                dialog.dismiss()
            }
            .create()
    }

    companion object {
        const val TAG = "FipsHandshakeFailedDialogFragment"
        private const val ARG_RECIPIENT_NAME = "recipient_name"

        /**
         * Creates a new instance of the warning dialog.
         *
         * @param recipientName The name of the contact for whom the handshake failed.
         * @return A new instance of FipsHandshakeFailedDialogFragment.
         */
        @JvmStatic
        fun newInstance(recipientName: String): FipsHandshakeFailedDialogFragment {
            return FipsHandshakeFailedDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RECIPIENT_NAME, recipientName)
                }
            }
        }
    }
}
