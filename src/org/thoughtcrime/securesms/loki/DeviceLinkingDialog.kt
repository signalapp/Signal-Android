package org.thoughtcrime.securesms.loki

import android.content.Context
import android.support.v7.app.AlertDialog
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.loki.api.DeviceLinkingSession
import org.whispersystems.signalservice.loki.api.DeviceLinkingSessionListener
import org.whispersystems.signalservice.loki.api.LokiStorageAPI
import org.whispersystems.signalservice.loki.api.PairingAuthorisation
import org.whispersystems.signalservice.loki.utilities.retryIfNeeded

class DeviceLinkingDialog private constructor(private val context: Context, private val mode: DeviceLinkingView.Mode, private val delegate: DeviceLinkingDialogDelegate?) : DeviceLinkingViewDelegate, DeviceLinkingSessionListener {
    private lateinit var view: DeviceLinkingView
    private lateinit var dialog: AlertDialog

    private val userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(context).privateKey.serialize()

    companion object {

        fun show(context: Context, mode: DeviceLinkingView.Mode, delegate: DeviceLinkingDialogDelegate?): DeviceLinkingDialog {
            val dialog = DeviceLinkingDialog(context, mode, delegate)
            dialog.show()
            return dialog
        }
    }

    private fun show() {
        view = DeviceLinkingView(context, mode, this)
        dialog = AlertDialog.Builder(context).setView(view).show()
        view.dismiss = { dismiss() }
        startListening()
    }

    public fun dismiss() {
        stopListening()
        dialog.dismiss()
    }

    private fun startListening() {
        DeviceLinkingSession.shared.startListeningForLinkingRequests()
        DeviceLinkingSession.shared.addListener(this)
    }

    private fun stopListening() {
        DeviceLinkingSession.shared.stopListeningForLinkingRequests()
        DeviceLinkingSession.shared.removeListener(this)
    }

    override fun sendPairingAuthorizedMessage(pairing: PairingAuthorisation): Boolean {
        val signedAuthorisation = pairing.sign(PairingAuthorisation.Type.GRANT, userPrivateKey)
        if (signedAuthorisation == null || signedAuthorisation.type != PairingAuthorisation.Type.GRANT) {
            Log.d("Loki", "Failed to sign pairing authorization.")
            return false
        }
        retryIfNeeded(8) {
            sendPairingAuthorisationMessage(context, pairing.secondaryDevicePublicKey, signedAuthorisation).get()
        }.fail {
            Log.d("Loki", "Failed to send pairing authorization message to ${pairing.secondaryDevicePublicKey}.")
        }
        DatabaseFactory.getLokiAPIDatabase(context).insertOrUpdatePairingAuthorisation(signedAuthorisation)
        LokiStorageAPI.shared.updateUserDeviceMappings()
        return true
    }

    override fun handleDeviceLinkAuthorized(pairing: PairingAuthorisation) {
        delegate?.handleDeviceLinkAuthorized(pairing)
    }

    override fun handleDeviceLinkingDialogDismissed() {
        if (mode == DeviceLinkingView.Mode.Master && view.pairingAuthorisation != null) {
            val authorisation = view.pairingAuthorisation!!
            DatabaseFactory.getLokiPreKeyBundleDatabase(context).removePreKeyBundle(authorisation.secondaryDevicePublicKey)
        }
        delegate?.handleDeviceLinkingDialogDismissed()
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