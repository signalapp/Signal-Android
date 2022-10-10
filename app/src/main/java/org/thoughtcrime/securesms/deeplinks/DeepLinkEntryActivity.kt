package org.thoughtcrime.securesms.deeplinks

import android.os.Bundle
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity

class DeepLinkEntryActivity : PassphraseRequiredActivity() {
    override fun onCreate(savedInstanceState: Bundle, ready: Boolean) {
        val intent = MainActivity.clearTop(this)
        val data = getIntent().data
        intent.data = data
        startActivity(intent)
    }
}