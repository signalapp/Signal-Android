package org.thoughtcrime.securesms.loki

import android.os.Bundle
import kotlinx.android.synthetic.main.activity_key_pair.*
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
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
        val hexEncodedPrivateKey = ("67982927" + "aaaabbbb" + "8926bfgd" + "0bfbba33" + "67982927" + "aaaabbbb" + "8926bfgd" + "0bfbba33").toUpperCase() // Hex.toString(keyPair!!.privateKey.serialize())
        mnemonic = MnemonicCodec(languageFileDirectory).encode(hexEncodedPrivateKey)
    }

    private fun updateMnemonicTextView() {
        mnemonicTextView.text = mnemonic!!
    }
    // endregion
}