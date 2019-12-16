package org.thoughtcrime.securesms.loki.redesign

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import kotlinx.android.synthetic.main.activity_register.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.utilities.setUpActionBarSessionLogo


class RegisterActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        setUpActionBarSessionLogo()
        registerButton.setOnClickListener { register() }
        val termsExplanation = SpannableStringBuilder("By using this service, you agree to our Terms and Conditions and Privacy Statement")
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 40, 60, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 65, 82, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        legalButton.text = termsExplanation
    }

    private fun register() {
        val intent = Intent(this, DisplayNameActivity::class.java)
        startActivity(intent)
    }
}