package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_landing.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.utilities.push
import org.thoughtcrime.securesms.loki.redesign.utilities.setUpActionBarSessionLogo

class LandingActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
        setUpActionBarSessionLogo()
        fakeChatView.startAnimating()
        registerButton.setOnClickListener { register() }
        restoreButton.setOnClickListener { restore() }
    }

    private fun register() {
        val intent = Intent(this, RegisterActivity::class.java)
        push(intent)
    }

    private fun restore() {
        val intent = Intent(this, RestoreActivity::class.java)
        push(intent)
    }
}