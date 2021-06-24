package org.thoughtcrime.securesms.loki.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_seed.view.*
import network.loki.messenger.R
import org.session.libsession.utilities.IdentityKeyUtil
import org.thoughtcrime.securesms.loki.utilities.MnemonicUtilities
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.hexEncodedPrivateKey
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

class SeedDialog : BaseDialog() {

    private val seed by lazy {
        var hexEncodedSeed = IdentityKeyUtil.retrieve(requireContext(), IdentityKeyUtil.LOKI_SEED)
        if (hexEncodedSeed == null) {
            hexEncodedSeed = IdentityKeyUtil.getIdentityKeyPair(requireContext()).hexEncodedPrivateKey // Legacy account
        }
        val loadFileContents: (String) -> String = { fileName ->
            MnemonicUtilities.loadFileContents(requireContext(), fileName)
        }
        MnemonicCodec(loadFileContents).encode(hexEncodedSeed!!, MnemonicCodec.Language.Configuration.english)
    }

    override fun setContentView(builder: AlertDialog.Builder) {
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_seed, null)
        contentView.seedTextView.text = seed
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.copyButton.setOnClickListener { copySeed() }
        builder.setView(contentView)
    }

    private fun copySeed() {
        val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Seed", seed)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        dismiss()
    }
}