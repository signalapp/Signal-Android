package org.thoughtcrime.securesms.loki.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import kotlinx.android.synthetic.main.dialog_clear_all_data.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext

class ClearAllDataDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(context!!)
        val contentView = LayoutInflater.from(context!!).inflate(R.layout.dialog_clear_all_data, null)
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.clearAllDataButton.setOnClickListener { clearAllData() }
        builder.setView(contentView)
        val result = builder.create()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return result
    }

    private fun clearAllData() {
        ApplicationContext.getInstance(context).clearData()
    }
}