package org.thoughtcrime.securesms.loki.dialogs

import android.os.Bundle
import android.support.design.widget.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_group_edit_bottom_sheet.*
import network.loki.messenger.R

public class GroupEditingOptionsBottomSheet : BottomSheetDialogFragment() {
    var onRemoveTapped: (() -> Unit)? = null
//    var onAdminTapped: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_group_edit_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        removeFromGroup.setOnClickListener { onRemoveTapped?.invoke() }
//        makeAdministrator.setOnClickListener { onAdminTapped?.invoke() }
    }
}