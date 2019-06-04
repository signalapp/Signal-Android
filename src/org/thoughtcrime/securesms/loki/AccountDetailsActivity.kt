package org.thoughtcrime.securesms.loki

import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_account_details.*
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.R
import org.whispersystems.signalservice.api.crypto.ProfileCipher

class AccountDetailsActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_details)
        nextButton.setOnClickListener { continueIfPossible() }
    }

    private fun continueIfPossible() {
        val uncheckedName = nameEditText.text.toString()
        val name = if (uncheckedName.isNotEmpty()) { uncheckedName.trim() } else { null }
        if (name != null && name.toByteArray().size > ProfileCipher.NAME_PADDED_LENGTH) {
            return nameEditText.input.setError("Too Long")
        }
        startActivity(Intent(this, KeyPairActivity::class.java))
    }
}