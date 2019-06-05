package org.thoughtcrime.securesms.loki

import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_key_pair.*
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.ConversationListActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import java.io.File
import java.io.FileOutputStream

class KeyPairActivity : BaseActionBarActivity() {
    private lateinit var languageFileDirectory: File
    private var keyPair: IdentityKeyPair? = null
        set(newValue) { field = newValue; updateMnemonic() }
    private var mnemonic: String? = null
        set(newValue) { field = newValue; updateMnemonicTextView() }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_key_pair)
        setUpLanguageFileDirectory()
        updateKeyPair()
        nextButton.setOnClickListener { register() }
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
    private fun updateKeyPair() {
        IdentityKeyUtil.generateIdentityKeys(this)
        keyPair = IdentityKeyUtil.getIdentityKeyPair(this)
    }

    private fun updateMnemonic() {
        mnemonic = MnemonicCodec(languageFileDirectory).encode(keyPair!!.hexEncodedPrivateKey)
    }

    private fun updateMnemonicTextView() {
        mnemonicTextView.text = mnemonic!!
    }
    // endregion

    // region Interaction
    private fun register() {
        val publicKey = keyPair!!.publicKey
        val hexEncodedPublicKey = publicKey.fingerprint
        DatabaseFactory.getIdentityDatabase(this).saveIdentity(Address.fromSerialized(hexEncodedPublicKey), publicKey,
                IdentityDatabase.VerifiedStatus.VERIFIED, true, System.currentTimeMillis(), true)
        TextSecurePreferences.setLocalNumber(this, hexEncodedPublicKey)
        TextSecurePreferences.setProfileName(this, "User McUserFace") // TODO: For debugging purposes
        startActivity(Intent(this, ConversationListActivity::class.java))
    }
    // endregion
}