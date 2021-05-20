package org.thoughtcrime.securesms.loki.dialogs

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import kotlinx.android.synthetic.main.dialog_file_server.view.*
import kotlinx.android.synthetic.main.dialog_seed.view.*
import network.loki.messenger.R
import org.session.libsession.utilities.IdentityKeyUtil
import org.thoughtcrime.securesms.loki.utilities.MnemonicUtilities
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.hexEncodedPrivateKey

class FileServerDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_file_server, null)
        contentView.okButton.setOnClickListener { dismiss() }
        builder.setView(contentView)
        val result = builder.create()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return result
    }
}