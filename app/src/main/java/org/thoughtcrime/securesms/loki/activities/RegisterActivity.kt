package org.thoughtcrime.securesms.loki.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast
import com.goterl.lazysodium.utils.KeyPair
import kotlinx.android.synthetic.main.activity_register.*
import network.loki.messenger.R
import org.session.libsession.utilities.KeyPairUtilities
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.utilities.push
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo
import java.util.*

class RegisterActivity : BaseActionBarActivity() {
    private var seed: ByteArray? = null
    private var ed25519KeyPair: KeyPair? = null
    private var x25519KeyPair: ECKeyPair? = null
        set(value) { field = value; updatePublicKeyTextView() }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        setUpActionBarSessionLogo()
        TextSecurePreferences.apply {
            setHasViewedSeed(this@RegisterActivity, false)
            setConfigurationMessageSynced(this@RegisterActivity, true)
            setRestorationTime(this@RegisterActivity, 0)
            setLastProfileUpdateTime(this@RegisterActivity, System.currentTimeMillis())
        }
        registerButton.setOnClickListener { register() }
        copyButton.setOnClickListener { copyPublicKey() }
        val termsExplanation = SpannableStringBuilder("By using this service, you agree to our Terms of Service and Privacy Policy")
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 40, 56, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(object : ClickableSpan() {

            override fun onClick(widget: View) {
                openURL("https://getsession.org/terms-of-service/")
            }
        }, 40, 56, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 61, 75, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(object : ClickableSpan() {

            override fun onClick(widget: View) {
                openURL("https://getsession.org/privacy-policy/")
            }
        }, 61, 75, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsTextView.movementMethod = LinkMovementMethod.getInstance()
        termsTextView.text = termsExplanation
        updateKeyPair()
    }
    // endregion

    // region Updating
    private fun updateKeyPair() {
        val keyPairGenerationResult = KeyPairUtilities.generate()
        seed = keyPairGenerationResult.seed
        ed25519KeyPair = keyPairGenerationResult.ed25519KeyPair
        x25519KeyPair = keyPairGenerationResult.x25519KeyPair
    }

    private fun updatePublicKeyTextView() {
        val hexEncodedPublicKey = x25519KeyPair!!.hexEncodedPublicKey
        val characterCount = hexEncodedPublicKey.count()
        var count = 0
        val limit = 32
        fun animate() {
            val numberOfIndexesToShuffle = 32 - count
            val indexesToShuffle = (0 until characterCount).shuffled().subList(0, numberOfIndexesToShuffle)
            var mangledHexEncodedPublicKey = hexEncodedPublicKey
            for (index in indexesToShuffle) {
                try {
                    mangledHexEncodedPublicKey = mangledHexEncodedPublicKey.substring(0, index) + "0123456789abcdef__".random() + mangledHexEncodedPublicKey.substring(index + 1, mangledHexEncodedPublicKey.count())
                } catch (exception: Exception) {
                    // Do nothing
                }
            }
            count += 1
            if (count < limit) {
                publicKeyTextView.text = mangledHexEncodedPublicKey
                Handler().postDelayed({
                    animate()
                }, 32)
            } else {
                publicKeyTextView.text = hexEncodedPublicKey
            }
        }
        animate()
    }
    // endregion

    // region Interaction
    private fun register() {
        KeyPairUtilities.store(this, seed!!, ed25519KeyPair!!, x25519KeyPair!!)
        val userHexEncodedPublicKey = x25519KeyPair!!.hexEncodedPublicKey
        val registrationID = KeyHelper.generateRegistrationId(false)
        TextSecurePreferences.setLocalRegistrationId(this, registrationID)
        TextSecurePreferences.setLocalNumber(this, userHexEncodedPublicKey)
        TextSecurePreferences.setRestorationTime(this, 0)
        TextSecurePreferences.setHasViewedSeed(this, false)
        val intent = Intent(this, DisplayNameActivity::class.java)
        push(intent)
    }

    private fun copyPublicKey() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Session ID", x25519KeyPair!!.hexEncodedPublicKey)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun openURL(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
    }
    // endregion
}