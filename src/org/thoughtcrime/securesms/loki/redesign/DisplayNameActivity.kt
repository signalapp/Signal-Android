package org.thoughtcrime.securesms.loki.redesign

import android.os.Bundle
import kotlinx.android.synthetic.main.activity_display_name_v2.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.utilities.setUpActionBarSessionLogo

class DisplayNameActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        setContentView(R.layout.activity_display_name_v2)
    }

    override fun onResume() {
        super.onResume()
        displayNameEditText.requestFocus()
    }
}