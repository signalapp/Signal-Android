package org.thoughtcrime.securesms.loki

import android.content.Context
import android.support.v7.app.AlertDialog
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.logging.Log
import org.whispersystems.signalservice.loki.api.LokiDeviceLinkingSession
import org.whispersystems.signalservice.loki.api.LokiDeviceLinkingSessionListener
import org.whispersystems.signalservice.loki.api.LokiPairingAuthorisation
import org.whispersystems.signalservice.loki.api.LokiStorageAPI

class DeviceLinkingDialog private constructor(private val context: Context, private val mode: DeviceLinkingView.Mode, private val delegate: DeviceLinkingDialogDelegate? = null): DeviceLinkingViewDelegate, LokiDeviceLinkingSessionListener {
    private lateinit var view: DeviceLinkingView

    private val userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(context).privateKey.serialize()

    companion object {
        fun show(context: Context, mode: DeviceLinkingView.Mode) { show(context, mode, null) }
        fun show(context: Context, mode: DeviceLinkingView.Mode, delegate: DeviceLinkingDialogDelegate?) {
            val dialog = DeviceLinkingDialog(context, mode, delegate)
            dialog.show()
        }
    }

    private fun show() {
        view = DeviceLinkingView(context, mode, this)
        val dialog = AlertDialog.Builder(context).setView(view).show()
        view.dismiss = {
            this.stopListening()
            dialog.dismiss()
        }

        this.startListening()
    }

    // region Private functions
    private fun startListening() {
        LokiDeviceLinkingSession.shared.startListeningForLinkingRequests()
        LokiDeviceLinkingSession.shared.addListener(this)
    }

    private fun stopListening() {
        LokiDeviceLinkingSession.shared.stopListeningForLinkingRequests()
        LokiDeviceLinkingSession.shared.removeListener(this)
    }
    // endregion

    // region Dialog View Delegate
    override fun authorise(pairing: LokiPairingAuthorisation): Boolean {
        val signedAuthorisation = pairing.sign(LokiPairingAuthorisation.Type.GRANT, userPrivateKey)
        if (signedAuthorisation == null) {
            Log.e("Loki", "Failed to sign grant authorisation")
            return false
        }

        // Send authorisation message
        sendAuthorisationMessage(context, pairing.secondaryDevicePubKey, signedAuthorisation)

        // Add the auth to the database
        DatabaseFactory.getLokiAPIDatabase(context).insertOrUpdatePairingAuthorisation(signedAuthorisation)

        // Update the api
        LokiStorageAPI.shared?.updateOurDeviceMappings()

        return true
    }

    override fun handleDeviceLinkAuthorized() {
        delegate?.handleDeviceLinkAuthorized()
    }

    override fun handleDeviceLinkingDialogDismissed() {
        // If we cancelled while we were listening for requests on main device, we need to remove any pre key bundles
        if (mode == DeviceLinkingView.Mode.Master && view.pairingAuthorisation != null) {
            val authorisation = view.pairingAuthorisation!!
            // Remove pre key bundle from the requesting device
            DatabaseFactory.getLokiPreKeyBundleDatabase(context).removePreKeyBundle(authorisation.secondaryDevicePubKey)
        }

        delegate?.handleDeviceLinkingDialogDismissed()
    }
    // endregion

    // region Loki Device Session Listener
    override fun onDeviceLinkingRequestReceived(authorisation: LokiPairingAuthorisation) {
        view.requestUserAuthorization(authorisation)

        // Stop listening to any more requests
        LokiDeviceLinkingSession.shared.stopListeningForLinkingRequests()
    }

    override fun onDeviceLinkRequestAccepted(authorisation: LokiPairingAuthorisation) {
        view.onDeviceLinkAuthorized(authorisation)

        // Stop listening to any more requests
        LokiDeviceLinkingSession.shared.stopListeningForLinkingRequests()
    }
    // endregion
}