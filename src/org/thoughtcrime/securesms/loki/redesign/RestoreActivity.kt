package org.thoughtcrime.securesms.loki.redesign

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import kotlinx.android.synthetic.main.activity_restore.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.utilities.setUpActionBarSessionLogo

class RestoreActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        setContentView(R.layout.activity_restore)
        val termsExplanation = SpannableStringBuilder("By using this service, you agree to our Terms and Conditions and Privacy Statement")
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 40, 60, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 65, 82, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        legalButton.text = termsExplanation
    }

    override fun onResume() {
        super.onResume()
        mnemonicEditText.requestFocus()
    }
}