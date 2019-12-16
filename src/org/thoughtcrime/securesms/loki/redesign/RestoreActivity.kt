package org.thoughtcrime.securesms.loki.redesign

import android.os.Bundle
import kotlinx.android.synthetic.main.activity_restore.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity

class RestoreActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restore)
    }

    override fun onResume() {
        super.onResume()
        mnemonicEditText.requestFocus()
    }
}