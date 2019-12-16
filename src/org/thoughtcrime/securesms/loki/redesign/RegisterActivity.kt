package org.thoughtcrime.securesms.loki.redesign

import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_register.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity

class RegisterActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        registerButton.setOnClickListener { register() }
    }

    private fun register() {
        val intent = Intent(this, DisplayNameActivity::class.java)
        startActivity(intent)
    }
}