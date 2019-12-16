package org.thoughtcrime.securesms.loki.redesign

import android.os.Bundle
import kotlinx.android.synthetic.main.activity_display_name_v2.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity

class DisplayNameActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_name_v2)
    }

    override fun onResume() {
        super.onResume()
        displayNameEditText.requestFocus()
    }
}