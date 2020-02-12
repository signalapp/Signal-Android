package org.thoughtcrime.securesms.loki.redesign.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.dialog_link_device_master_mode.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.redesign.utilities.MnemonicUtilities
import org.thoughtcrime.securesms.loki.redesign.utilities.QRCodeUtilities
import org.thoughtcrime.securesms.loki.toPx
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.loki.api.DeviceLink
import org.whispersystems.signalservice.loki.api.DeviceLinkingSession
import org.whispersystems.signalservice.loki.api.DeviceLinkingSessionListener
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec

class LinkDeviceMasterModeDialog : DialogFragment(), DeviceLinkingSessionListener {
    private val languageFileDirectory by lazy { MnemonicUtilities.getLanguageFileDirectory(context!!) }
    private lateinit var contentView: View
    private var authorization: DeviceLink? = null
    var delegate: LinkDeviceMasterModeDialogDelegate? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(context!!)
        contentView = LayoutInflater.from(context!!).inflate(R.layout.dialog_link_device_master_mode, null)
        val size = toPx(128, resources)
        val hexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context!!)
        val qrCode = QRCodeUtilities.encode(hexEncodedPublicKey, size, false, false)
        contentView.qrCodeImageView.setImageBitmap(qrCode)
        contentView.cancelButton.setOnClickListener { onDeviceLinkCanceled() }
        contentView.authorizeButton.setOnClickListener { authorizeDeviceLink() }
        builder.setView(contentView)
        DeviceLinkingSession.shared.startListeningForLinkingRequests() // FIXME: This flag is named poorly as it's actually also used for authorizations
        DeviceLinkingSession.shared.addListener(this)
        val result = builder.create()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return result
    }

    override fun requestUserAuthorization(authorization: DeviceLink) {
        if (authorization.type != DeviceLink.Type.REQUEST || authorization.masterHexEncodedPublicKey != TextSecurePreferences.getLocalNumber(context!!) || this.authorization != null) { return }
        Util.runOnMain {
            this.authorization = authorization
            contentView.qrCodeImageView.visibility = View.GONE
            val titleTextViewLayoutParams = contentView.titleTextView.layoutParams as LinearLayout.LayoutParams
            titleTextViewLayoutParams.topMargin = toPx(8, resources)
            contentView.titleTextView.layoutParams = titleTextViewLayoutParams
            contentView.titleTextView.text = "Linking Request Received"
            contentView.explanationTextView.text = "Please check that the words below match those shown on your other device"
            contentView.mnemonicTextView.visibility = View.VISIBLE
            contentView.mnemonicTextView.text = MnemonicUtilities.getFirst3Words(MnemonicCodec(languageFileDirectory), authorization.slaveHexEncodedPublicKey)
            contentView.authorizeButton.visibility = View.VISIBLE
        }
    }

    private fun authorizeDeviceLink() {
        val authorization = this.authorization ?: return
        delegate?.onDeviceLinkRequestAuthorized(authorization)
        DeviceLinkingSession.shared.stopListeningForLinkingRequests()
        DeviceLinkingSession.shared.removeListener(this)
        dismiss()
    }

    private fun onDeviceLinkCanceled() {
        DeviceLinkingSession.shared.stopListeningForLinkingRequests()
        DeviceLinkingSession.shared.removeListener(this)
        if (authorization != null) {
            DatabaseFactory.getLokiPreKeyBundleDatabase(context).removePreKeyBundle(authorization!!.slaveHexEncodedPublicKey)
        }
        dismiss()
        delegate?.onDeviceLinkCanceled()
    }
}

interface LinkDeviceMasterModeDialogDelegate {

    fun onDeviceLinkRequestAuthorized(authorization: DeviceLink)
    fun onDeviceLinkCanceled()
}
