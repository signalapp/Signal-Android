package org.thoughtcrime.securesms.onboarding

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityRecoveryPhraseRestoreBinding
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo

class RecoveryPhraseRestoreActivity : BaseActionBarActivity() {
    private lateinit var binding: ActivityRecoveryPhraseRestoreBinding
    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        TextSecurePreferences.apply {
            setHasViewedSeed(this@RecoveryPhraseRestoreActivity, true)
            setConfigurationMessageSynced(this@RecoveryPhraseRestoreActivity, false)
            setRestorationTime(this@RecoveryPhraseRestoreActivity, System.currentTimeMillis())
            setLastProfileUpdateTime(this@RecoveryPhraseRestoreActivity, System.currentTimeMillis())
        }
        binding = ActivityRecoveryPhraseRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mnemonicEditText.imeOptions = binding.mnemonicEditText.imeOptions or 16777216 // Always use incognito keyboard
        binding.restoreButton.setOnClickListener { restore() }
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
        binding.termsTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.termsTextView.text = termsExplanation
    }
    // endregion

    // region Interaction
    private fun restore() {
        val mnemonic = binding.mnemonicEditText.text.toString()
        try {
            val loadFileContents: (String) -> String = { fileName ->
                MnemonicUtilities.loadFileContents(this, fileName)
            }
            val hexEncodedSeed = MnemonicCodec(loadFileContents).decode(mnemonic)
            val seed = Hex.fromStringCondensed(hexEncodedSeed)
            val keyPairGenerationResult = KeyPairUtilities.generate(seed)
            val x25519KeyPair = keyPairGenerationResult.x25519KeyPair
            KeyPairUtilities.store(this, seed, keyPairGenerationResult.ed25519KeyPair, x25519KeyPair)
            val userHexEncodedPublicKey = x25519KeyPair.hexEncodedPublicKey
            val registrationID = KeyHelper.generateRegistrationId(false)
            TextSecurePreferences.setLocalRegistrationId(this, registrationID)
            TextSecurePreferences.setLocalNumber(this, userHexEncodedPublicKey)
            val intent = Intent(this, DisplayNameActivity::class.java)
            push(intent)
        } catch (e: Exception) {
            val message = if (e is MnemonicCodec.DecodingError) e.description else MnemonicCodec.DecodingError.Generic.description
            return Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
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