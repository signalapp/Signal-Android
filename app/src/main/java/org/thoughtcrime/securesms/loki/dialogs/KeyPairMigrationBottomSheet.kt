package org.thoughtcrime.securesms.loki.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_key_pair_migration_bottom_sheet.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext

class KeyPairMigrationBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_key_pair_migration_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        upgradeNowButton.setOnClickListener { upgradeNow() }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener {
            val d = dialog as BottomSheetDialog
            val bottomSheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)!!
            BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
            BottomSheetBehavior.from(bottomSheet).isHideable = false
        }
        isCancelable = false
        return dialog
    }

    private fun upgradeNow() {
        val applicationContext = requireContext().applicationContext as ApplicationContext
        applicationContext.clearAllData(true)
    }
}