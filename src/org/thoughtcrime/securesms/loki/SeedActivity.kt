package org.thoughtcrime.securesms.loki

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_seed.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.Analytics
import org.whispersystems.signalservice.loki.utilities.hexEncodedPublicKey
import java.io.File
import java.io.FileOutputStream

class SeedActivity : BaseActionBarActivity() {
    private lateinit var languageFileDirectory: File
    private var mode = Mode.Register
        set(newValue) { field = newValue; updateUI() }
    private var seed: ByteArray? = null
        set(newValue) { field = newValue; updateMnemonic() }
    private var mnemonic: String? = null
        set(newValue) { field = newValue; updateMnemonicTextView() }

    // region Types
    enum class Mode { Register, Restore }
    // endregion

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seed)
        setUpLanguageFileDirectory()
        updateSeed()
        copyButton.setOnClickListener { copy() }
        toggleModeButton.setOnClickListener { toggleMode() }
        registerOrRestoreButton.setOnClickListener { registerOrRestore() }
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

    // region Updating
    private fun updateSeed() {
        val seed = Curve25519.getInstance(Curve25519.BEST).generateSeed(16)
        try {
            IdentityKeyUtil.generateIdentityKeyPair(this, seed + seed)
        } catch (exception: Exception) {
            return updateSeed()
        }
        this.seed = seed
    }

    private fun updateUI() {
        seedExplanationTextView1.visibility = if (mode == Mode.Register) View.VISIBLE else View.GONE
        mnemonicTextView.visibility = if (mode == Mode.Register) View.VISIBLE else View.GONE
        copyButton.visibility = if (mode == Mode.Register) View.VISIBLE else View.GONE
        seedExplanationTextView2.visibility = if (mode == Mode.Restore) View.VISIBLE else View.GONE
        mnemonicEditText.visibility = if (mode == Mode.Restore) View.VISIBLE else View.GONE
        val toggleModeButtonTitleID = if (mode == Mode.Register) R.string.activity_key_pair_toggle_mode_button_title_1 else R.string.activity_key_pair_toggle_mode_button_title_2
        toggleModeButton.setText(toggleModeButtonTitleID)
        val registerOrRestoreButtonTitleID = if (mode == Mode.Register) R.string.activity_key_pair_register_or_restore_button_title_1 else R.string.activity_key_pair_register_or_restore_button_title_2
        registerOrRestoreButton.setText(registerOrRestoreButtonTitleID)
        if (mode == Mode.Restore) {
            mnemonicEditText.requestFocus()
        } else {
            mnemonicEditText.clearFocus()
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(mnemonicEditText.windowToken, 0)
        }
    }

    private fun updateMnemonic() {
        val hexEncodedSeed = Hex.toStringCondensed(seed)
        mnemonic = MnemonicCodec(languageFileDirectory).encode(hexEncodedSeed)
    }

    private fun updateMnemonicTextView() {
        mnemonicTextView.text = mnemonic!!
    }
    // endregion

    // region Interaction
    private fun copy() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Mnemonic", mnemonic)
        clipboard.primaryClip = clip
        Toast.makeText(this, R.string.activity_key_pair_mnemonic_copied_message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleMode() {
        mode = when (mode) {
            Mode.Register -> Mode.Restore
            Mode.Restore -> Mode.Register
        }
    }

    private fun registerOrRestore() {
        var seed: ByteArray
        when (mode) {
            Mode.Register -> seed = this.seed!!
            Mode.Restore -> {
                val mnemonic = mnemonicEditText.text.toString()
                try {
                    val hexEncodedSeed = MnemonicCodec(languageFileDirectory).decode(mnemonic)
                    seed = Hex.fromStringCondensed(hexEncodedSeed)
                } catch (e: Exception) {
                    val message = if (e is MnemonicCodec.DecodingError) e.description else MnemonicCodec.DecodingError.Generic.description
                    return Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        val hexEncodedSeed = Hex.toStringCondensed(seed)
        IdentityKeyUtil.save(this, IdentityKeyUtil.lokiSeedKey, hexEncodedSeed)
        if (seed.count() == 16) seed = seed + seed
        if (mode == Mode.Restore) {
            IdentityKeyUtil.generateIdentityKeyPair(this, seed)
        }
        val keyPair = IdentityKeyUtil.getIdentityKeyPair(this)
        val publicKey = keyPair.publicKey
        val hexEncodedPublicKey = keyPair.hexEncodedPublicKey
        val registrationID = KeyHelper.generateRegistrationId(false)
        TextSecurePreferences.setLocalRegistrationId(this, registrationID)
        DatabaseFactory.getIdentityDatabase(this).saveIdentity(Address.fromSerialized(hexEncodedPublicKey), publicKey,
            IdentityDatabase.VerifiedStatus.VERIFIED, true, System.currentTimeMillis(), true)
        TextSecurePreferences.setLocalNumber(this, hexEncodedPublicKey)
        when (mode) {
            Mode.Register -> Analytics.shared.track("Seed Created")
            Mode.Restore -> Analytics.shared.track("Seed Restored")
        }
        startActivity(Intent(this, AccountDetailsActivity::class.java))
        finish()
    }
    // endregion
}