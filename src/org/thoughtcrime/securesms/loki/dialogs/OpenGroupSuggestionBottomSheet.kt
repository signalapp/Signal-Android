package org.thoughtcrime.securesms.loki.dialogs

import android.os.Bundle
import android.support.design.widget.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_open_group_suggestion_bottom_sheet.*
import network.loki.messenger.R

class OpenGroupSuggestionBottomSheet : BottomSheetDialogFragment() {
    var onJoinTapped: (() -> Unit)? = null
    var onDismissTapped: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.SessionBottomSheetDialogTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_open_group_suggestion_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        joinButton.setOnClickListener { onJoinTapped?.invoke() }
        dismissButton.setOnClickListener { onDismissTapped?.invoke() }
    }
}