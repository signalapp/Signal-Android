package org.thoughtcrime.securesms.loki.dialogs

import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_light_theme_feature_intro_bottom_sheet.*
import kotlinx.android.synthetic.main.fragment_open_group_suggestion_bottom_sheet.*
import network.loki.messenger.R

class LightThemeFeatureIntroBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(
                R.layout.fragment_light_theme_feature_intro_bottom_sheet,
                container,
                false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        okButton.setOnClickListener { dismiss() }
    }
}