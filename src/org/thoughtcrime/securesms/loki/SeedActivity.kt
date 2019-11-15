package org.thoughtcrime.securesms.loki

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_seed.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.ConversationListActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.signalservice.loki.api.PairingAuthorisation
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.Analytics
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation
import org.whispersystems.signalservice.loki.utilities.hexEncodedPublicKey
import org.whispersystems.signalservice.loki.utilities.retryIfNeeded
import java.io.File
import java.io.FileOutputStream

class SeedActivity : BaseActionBarActivity(), DeviceLinkingDialogDelegate {
    private lateinit var languageFileDirectory: File
    private var mode = Mode.Register
        set(newValue) { field = newValue; updateUI() }
    private var seed: ByteArray? = null
        set(newValue) { field = newValue; updateMnemonic() }
    private var mnemonic: String? = null
        set(newValue) { field = newValue; updateMnemonicTextView() }

    // region Types
    enum class Mode { Register, Restore, Link }
    // endregion

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seed)
        setUpLanguageFileDirectory()
        mnemonicEditText.input.imeOptions = mnemonicEditText.input.imeOptions or 16777216 // Always use incognito keyboard for this
        updateSeed()
        copyButton.setOnClickListener { copy() }
        toggleRegisterModeButton.setOnClickListener { mode = Mode.Register }
        toggleRestoreModeButton.setOnClickListener { mode = Mode.Restore }
        toggleLinkModeButton.setOnClickListener { mode = Mode.Link }
        mainButton.setOnClickListener { handleMainButtonTapped() }
        Analytics.shared.track("Seed Screen Viewed")
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
        val registerModeVisibility = if (mode == Mode.Register) View.VISIBLE else View.GONE
        val restoreModeVisibility = if (mode == Mode.Restore) View.VISIBLE else View.GONE
        val linkModeVisibility = if (mode == Mode.Link) View.VISIBLE else View.GONE
        seedExplanationTextView1.visibility = registerModeVisibility
        mnemonicTextView.visibility = registerModeVisibility
        copyButton.visibility = registerModeVisibility
        seedExplanationTextView2.visibility = restoreModeVisibility
        mnemonicEditText.visibility = restoreModeVisibility
        linkExplanationTextView.visibility = linkModeVisibility
        publicKeyEditText.visibility = linkModeVisibility
        toggleRegisterModeButton.visibility = if (mode != Mode.Register) View.VISIBLE else View.GONE
        toggleRestoreModeButton.visibility = if (mode != Mode.Restore) View.VISIBLE else View.GONE
        toggleLinkModeButton.visibility = if (mode != Mode.Link) View.VISIBLE else View.GONE
        val mainButtonTitleID = when (mode) {
            Mode.Register -> R.string.activity_key_pair_main_button_title_1
            Mode.Restore -> R.string.activity_key_pair_main_button_title_2
            Mode.Link -> R.string.activity_key_pair_main_button_title_3
        }
        mainButton.setText(mainButtonTitleID)
        if (mode == Mode.Restore) {
            mnemonicEditText.requestFocus()
        } else {
            mnemonicEditText.clearFocus()
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(mnemonicEditText.windowToken, 0)
        }
        if (mode == Mode.Link) {
            publicKeyEditText.requestFocus()
        } else {
            publicKeyEditText.clearFocus()
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(publicKeyEditText.windowToken, 0)
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

    private fun handleMainButtonTapped() {
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
            Mode.Link -> {
                val hexEncodedPublicKey = publicKeyEditText.text.trim().toString()
                if (!PublicKeyValidation.isValid(hexEncodedPublicKey)) {
                    return Toast.makeText(this, "Invalid public key", Toast.LENGTH_SHORT).show()
                }
                seed = this.seed!!
            }
        }
        val hexEncodedSeed = Hex.toStringCondensed(seed)
        IdentityKeyUtil.save(this, IdentityKeyUtil.lokiSeedKey, hexEncodedSeed)
        if (seed.count() == 16) seed += seed
        if (mode == Mode.Restore) {
            IdentityKeyUtil.generateIdentityKeyPair(this, seed)
        }
        val keyPair = IdentityKeyUtil.getIdentityKeyPair(this)
        val userHexEncodedPublicKey = keyPair.hexEncodedPublicKey
        val registrationID = KeyHelper.generateRegistrationId(false)
        TextSecurePreferences.setLocalRegistrationId(this, registrationID)
        DatabaseFactory.getIdentityDatabase(this).saveIdentity(Address.fromSerialized(userHexEncodedPublicKey), keyPair.publicKey,
            IdentityDatabase.VerifiedStatus.VERIFIED, true, System.currentTimeMillis(), true)
        TextSecurePreferences.setLocalNumber(this, userHexEncodedPublicKey)
        when (mode) {
            Mode.Register -> Analytics.shared.track("Seed Created")
            Mode.Restore -> Analytics.shared.track("Seed Restored")
            Mode.Link -> Analytics.shared.track("Device Linking Attempted")
        }
        if (mode == Mode.Link) {
            TextSecurePreferences.setHasSeenWelcomeScreen(this, true)
            TextSecurePreferences.setPromptedPushRegistration(this, true)
            val masterHexEncodedPublicKey = publicKeyEditText.text.trim().toString()
            val authorisation = PairingAuthorisation(masterHexEncodedPublicKey, userHexEncodedPublicKey).sign(PairingAuthorisation.Type.REQUEST, keyPair.privateKey.serialize())
            if (authorisation == null) {
                Log.d("Loki", "Failed to sign pairing request.")
                resetForRegistration()
                return Toast.makeText(application, "Couldn't start device linking process.", Toast.LENGTH_SHORT).show()
            }
            val application = ApplicationContext.getInstance(this)
            application.startLongPollingIfNeeded()
            application.setUpP2PAPI()
            application.setUpStorageAPIIfNeeded()
            DeviceLinkingDialog.show(this, DeviceLinkingView.Mode.Slave, this)
            AsyncTask.execute {
                retryIfNeeded(8) {
                    sendPairingAuthorisationMessage(this@SeedActivity, authorisation.primaryDevicePublicKey, authorisation)
                }
            }
        } else {
            startActivity(Intent(this, DisplayNameActivity::class.java))
            finish()
        }
    }

    override fun handleDeviceLinkAuthorized(pairingAuthorisation: PairingAuthorisation) {
        Analytics.shared.track("Device Linked Successfully")
        if (pairingAuthorisation.secondaryDevicePublicKey == TextSecurePreferences.getLocalNumber(this)) {
            TextSecurePreferences.setMasterHexEncodedPublicKey(this, pairingAuthorisation.primaryDevicePublicKey)
        }
        startActivity(Intent(this, ConversationListActivity::class.java))
        finish()
    }

    override fun handleDeviceLinkingDialogDismissed() {
        resetForRegistration()
    }

    private fun resetForRegistration() {
        IdentityKeyUtil.delete(this, IdentityKeyUtil.lokiSeedKey)
        DatabaseFactory.getLokiPreKeyBundleDatabase(this).resetAllPreKeyBundleInfo()
        TextSecurePreferences.removeLocalNumber(this)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, false)
        TextSecurePreferences.setPromptedPushRegistration(this, false)
    }
    // endregion
}