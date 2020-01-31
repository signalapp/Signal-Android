package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_restore.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.loki.redesign.utilities.push
import org.thoughtcrime.securesms.loki.redesign.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.hexEncodedPublicKey
import java.io.File
import java.io.FileOutputStream

class RestoreActivity : BaseActionBarActivity() {
    private lateinit var languageFileDirectory: File

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpLanguageFileDirectory()
        setUpActionBarSessionLogo()
        setContentView(R.layout.activity_restore)
        mnemonicEditText.imeOptions = mnemonicEditText.imeOptions or 16777216 // Always use incognito keyboard
        restoreButton.setOnClickListener { restore() }
        val termsExplanation = SpannableStringBuilder("By using this service, you agree to our Terms and Conditions and Privacy Statement")
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 40, 60, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 65, 82, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsButton.text = termsExplanation
        termsButton.setOnClickListener { showTerms() }
    }
    // endregion

    // region General
    private fun setUpLanguageFileDirectory() {
        val languages = listOf( "english", "japanese", "portuguese", "spanish" )
        val directory = File(applicationInfo.dataDir)
        for (language in languages) {
            val fileName = "$language.txt"
            if (directory.list().contains(fileName)) { continue }
            val inputStream = assets.open("mnemonic/$fileName")
            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            val buffer = ByteArray(1024)
            while (true) {
                val count = inputStream.read(buffer)
                if (count < 0) { break }
                outputStream.write(buffer, 0, count)
            }
            inputStream.close()
            outputStream.close()
        }
        languageFileDirectory = directory
    }
    // endregion

    // region Interaction
    private fun restore() {
        val mnemonic = mnemonicEditText.text.toString()
        try {
            val hexEncodedSeed = MnemonicCodec(languageFileDirectory).decode(mnemonic)
            var seed = Hex.fromStringCondensed(hexEncodedSeed)
            IdentityKeyUtil.save(this, IdentityKeyUtil.lokiSeedKey, Hex.toStringCondensed(seed))
            if (seed.size == 16) { seed = seed + seed }
            val keyPair = Curve.generateKeyPair(seed)
            IdentityKeyUtil.save(this, IdentityKeyUtil.IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(keyPair.publicKey.serialize()))
            IdentityKeyUtil.save(this, IdentityKeyUtil.IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(keyPair.privateKey.serialize()))
            val userHexEncodedPublicKey = keyPair.hexEncodedPublicKey
            val registrationID = KeyHelper.generateRegistrationId(false)
            TextSecurePreferences.setLocalRegistrationId(this, registrationID)
            DatabaseFactory.getIdentityDatabase(this).saveIdentity(Address.fromSerialized(userHexEncodedPublicKey),
                    IdentityKeyUtil.getIdentityKeyPair(this).publicKey, IdentityDatabase.VerifiedStatus.VERIFIED,
                    true, System.currentTimeMillis(), true)
            TextSecurePreferences.setLocalNumber(this, userHexEncodedPublicKey)
            TextSecurePreferences.setRestorationTime(this, System.currentTimeMillis())
            TextSecurePreferences.setHasViewedSeed(this, true)
            val intent = Intent(this, DisplayNameActivity::class.java)
            push(intent)
        } catch (e: Exception) {
            val message = if (e is MnemonicCodec.DecodingError) e.description else MnemonicCodec.DecodingError.Generic.description
            return Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTerms() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/loki-project/loki-messenger-android/blob/master/privacy-policy.md"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open link", Toast.LENGTH_SHORT).show()
        }
    }
    // endregion
}