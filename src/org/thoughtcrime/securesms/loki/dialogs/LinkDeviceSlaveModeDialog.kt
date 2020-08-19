package org.thoughtcrime.securesms.loki.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.dialog_link_device_slave_mode.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.MnemonicUtilities
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.DeviceLink
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.DeviceLinkingSession
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.DeviceLinkingSessionListener

class LinkDeviceSlaveModeDialog : DialogFragment(), DeviceLinkingSessionListener {
    private val languageFileDirectory by lazy { MnemonicUtilities.getLanguageFileDirectory(context!!) }
    private lateinit var contentView: View
    private var deviceLink: DeviceLink? = null
    var delegate: LinkDeviceSlaveModeDialogDelegate? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(context!!)
        contentView = LayoutInflater.from(context!!).inflate(R.layout.dialog_link_device_slave_mode, null)
        val hexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
        contentView.mnemonicTextView.text = MnemonicUtilities.getFirst3Words(MnemonicCodec(languageFileDirectory), hexEncodedPublicKey)
        contentView.cancelButton.setOnClickListener { onDeviceLinkCanceled() }
        builder.setView(contentView)
        DeviceLinkingSession.shared.startListeningForLinkingRequests()
        DeviceLinkingSession.shared.addListener(this)
        val result = builder.create()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return result
    }

    override fun onDeviceLinkRequestAuthorized(deviceLink: DeviceLink) {
        if (deviceLink.type != DeviceLink.Type.AUTHORIZATION || deviceLink.slavePublicKey != TextSecurePreferences.getLocalNumber(context!!) || this.deviceLink != null) { return }
        Util.runOnMain {
            this.deviceLink = deviceLink
            DeviceLinkingSession.shared.stopListeningForLinkingRequests()
            DeviceLinkingSession.shared.removeListener(this)
            contentView.spinner.visibility = View.GONE
            val titleTextViewLayoutParams = contentView.titleTextView.layoutParams as LinearLayout.LayoutParams
            titleTextViewLayoutParams.topMargin = 0
            contentView.titleTextView.layoutParams = titleTextViewLayoutParams
            contentView.titleTextView.text = resources.getString(R.string.dialog_link_device_slave_mode_title_2)
            contentView.explanationTextView.text = resources.getString(R.string.dialog_link_device_slave_mode_explanation_2)
            contentView.mnemonicTextView.visibility = View.GONE
            contentView.cancelButton.visibility = View.GONE
            Handler().postDelayed({
                dismiss()
                delegate?.onDeviceLinkRequestAuthorized(deviceLink)
            }, 4000)
        }
    }

    private fun onDeviceLinkCanceled() {
        DeviceLinkingSession.shared.stopListeningForLinkingRequests()
        DeviceLinkingSession.shared.removeListener(this)
        dismiss()
        delegate?.onDeviceLinkCanceled()
    }
}

interface LinkDeviceSlaveModeDialogDelegate {

    fun onDeviceLinkRequestAuthorized(authorization: DeviceLink)
    fun onDeviceLinkCanceled()
}