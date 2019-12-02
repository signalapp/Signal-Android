package org.thoughtcrime.securesms.loki

import android.content.Context
import android.support.v7.app.AlertDialog
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.loki.api.DeviceLinkingSession
import org.whispersystems.signalservice.loki.api.DeviceLinkingSessionListener
import org.whispersystems.signalservice.loki.api.PairingAuthorisation

class DeviceLinkingDialog private constructor(private val context: Context, private val mode: DeviceLinkingView.Mode, private val delegate: DeviceLinkingDelegate?) : DeviceLinkingDelegate, DeviceLinkingSessionListener {
    private lateinit var view: DeviceLinkingView
    private lateinit var dialog: AlertDialog

    companion object {
        fun show(context: Context, mode: DeviceLinkingView.Mode, delegate: DeviceLinkingDelegate?): DeviceLinkingDialog {
            val dialog = DeviceLinkingDialog(context, mode, delegate)
            dialog.show()
            return dialog
        }
    }

    private fun show() {
        val delegate = DeviceLinkingDelegate.combine(this, this.delegate)
        view = DeviceLinkingView(context, mode, delegate)
        dialog = AlertDialog.Builder(context).setView(view).show()
        dialog.setCanceledOnTouchOutside(false)
        view.dismiss = { dismiss() }
        DeviceLinkingSession.shared.startListeningForLinkingRequests()
        DeviceLinkingSession.shared.addListener(this)
    }

    private fun dismiss() {
        DeviceLinkingSession.shared.stopListeningForLinkingRequests()
        DeviceLinkingSession.shared.removeListener(this)
        dialog.dismiss()
    }

    override fun handleDeviceLinkingDialogDismissed() {
        if (mode == DeviceLinkingView.Mode.Master && view.pairingAuthorisation != null) {
            val authorisation = view.pairingAuthorisation!!
            DatabaseFactory.getLokiPreKeyBundleDatabase(context).removePreKeyBundle(authorisation.secondaryDevicePublicKey)
        }
    }

    override fun requestUserAuthorization(authorisation: PairingAuthorisation) {
        Util.runOnMain {
            view.requestUserAuthorization(authorisation)
        }
        DeviceLinkingSession.shared.stopListeningForLinkingRequests()
    }

    override fun onDeviceLinkRequestAuthorized(authorisation: PairingAuthorisation) {
        Util.runOnMain {
            view.onDeviceLinkAuthorized(authorisation)
        }
        DeviceLinkingSession.shared.stopListeningForLinkingRequests()
    }
}