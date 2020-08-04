package org.thoughtcrime.securesms.loki.dialogs

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
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import org.thoughtcrime.securesms.loki.utilities.MnemonicUtilities
import org.thoughtcrime.securesms.loki.utilities.QRCodeUtilities
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.loki.api.SnodeAPI
import org.whispersystems.signalservice.loki.api.fileserver.FileServerAPI
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.DeviceLink
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.DeviceLinkingSession
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.DeviceLinkingSessionListener

class LinkDeviceMasterModeDialog : DialogFragment(), DeviceLinkingSessionListener {
    private val languageFileDirectory by lazy { MnemonicUtilities.getLanguageFileDirectory(context!!) }
    private lateinit var contentView: View
    private var deviceLink: DeviceLink? = null
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

    override fun requestUserAuthorization(deviceLink: DeviceLink) {
        if (deviceLink.type != DeviceLink.Type.REQUEST || deviceLink.masterPublicKey != TextSecurePreferences.getLocalNumber(context!!) || this.deviceLink != null) { return }
        Util.runOnMain {
            this.deviceLink = deviceLink
            contentView.qrCodeImageView.visibility = View.GONE
            val titleTextViewLayoutParams = contentView.titleTextView.layoutParams as LinearLayout.LayoutParams
            titleTextViewLayoutParams.topMargin = toPx(8, resources)
            contentView.titleTextView.layoutParams = titleTextViewLayoutParams
            contentView.titleTextView.text = resources.getString(R.string.dialog_link_device_master_mode_title_2)
            contentView.explanationTextView.text = resources.getString(R.string.dialog_link_device_master_mode_explanation_2)
            contentView.mnemonicTextView.visibility = View.VISIBLE
            contentView.mnemonicTextView.text = MnemonicUtilities.getFirst3Words(MnemonicCodec(languageFileDirectory), deviceLink.slavePublicKey)
            contentView.authorizeButton.visibility = View.VISIBLE
        }
    }

    private fun authorizeDeviceLink() {
        val deviceLink = this.deviceLink ?: return
        DeviceLinkingSession.shared.stopListeningForLinkingRequests()
        DeviceLinkingSession.shared.removeListener(this)
        Util.runOnMain {
            contentView.qrCodeImageViewContainer.visibility = View.GONE
            contentView.spinner.visibility = View.VISIBLE
            val titleTextViewLayoutParams = contentView.titleTextView.layoutParams as LinearLayout.LayoutParams
            titleTextViewLayoutParams.topMargin = toPx(24, resources)
            contentView.titleTextView.layoutParams = titleTextViewLayoutParams
            contentView.titleTextView.text = resources.getString(R.string.dialog_link_device_master_mode_title_3)
            contentView.explanationTextView.text = resources.getString(R.string.dialog_link_device_master_mode_explanation_3)
            contentView.mnemonicTextView.visibility = View.GONE
            contentView.buttonContainer.visibility = View.GONE
            contentView.cancelButton.visibility = View.GONE
            contentView.authorizeButton.visibility = View.GONE
        }
        FileServerAPI.shared.addDeviceLink(deviceLink).bind(SnodeAPI.sharedContext) {
            MultiDeviceProtocol.signAndSendDeviceLinkMessage(context!!, deviceLink)
        }.success {
            TextSecurePreferences.setMultiDevice(context!!, true)
        }.successUi {
            delegate?.onDeviceLinkRequestAuthorized()
            dismiss()
        }.fail {
            FileServerAPI.shared.removeDeviceLink(deviceLink) // If this fails we have a problem
            DatabaseFactory.getLokiPreKeyBundleDatabase(context!!).removePreKeyBundle(deviceLink.slavePublicKey)
        }.failUi {
            delegate?.onDeviceLinkAuthorizationFailed()
            dismiss()
        }
    }

    private fun onDeviceLinkCanceled() {
        DeviceLinkingSession.shared.stopListeningForLinkingRequests()
        DeviceLinkingSession.shared.removeListener(this)
        if (deviceLink != null) {
            DatabaseFactory.getLokiPreKeyBundleDatabase(context).removePreKeyBundle(deviceLink!!.slavePublicKey)
        }
        dismiss()
        delegate?.onDeviceLinkCanceled()
    }
}

interface LinkDeviceMasterModeDialogDelegate {

    fun onDeviceLinkRequestAuthorized()
    fun onDeviceLinkAuthorizationFailed()
    fun onDeviceLinkCanceled()
}
