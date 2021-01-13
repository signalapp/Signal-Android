package org.thoughtcrime.securesms.loki.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_key_pair_migration_success_bottom_sheet.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.TextSecurePreferences

class KeyPairMigrationSuccessBottomSheet : BottomSheetDialogFragment() {

    private val sessionID by lazy {
        TextSecurePreferences.getLocalNumber(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_key_pair_migration_success_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionIDTextView.text = sessionID
        copyButton.setOnClickListener { copySessionID() }
        okButton.setOnClickListener { dismiss() }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // Expand the bottom sheet by default
        dialog.setOnShowListener {
            val d = dialog as BottomSheetDialog
            val bottomSheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            BottomSheetBehavior.from(bottomSheet!!).setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        return dialog
    }

    private fun copySessionID() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Session ID", sessionID)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}