package org.thoughtcrime.securesms.loki.dialogs

import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_device_list_bottom_sheet.*
import network.loki.messenger.R

public class DeviceEditingOptionsBottomSheet : BottomSheetDialogFragment() {
    var onEditTapped: (() -> Unit)? = null
    var onUnlinkTapped: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_device_list_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editDisplayNameText.setOnClickListener { onEditTapped?.invoke() }
        unlinkDeviceText.setOnClickListener { onUnlinkTapped?.invoke() }
    }
}