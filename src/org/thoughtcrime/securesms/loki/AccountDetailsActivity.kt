package org.thoughtcrime.securesms.loki

import android.os.Bundle
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.R

class AccountDetailsActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_details)
    }
}