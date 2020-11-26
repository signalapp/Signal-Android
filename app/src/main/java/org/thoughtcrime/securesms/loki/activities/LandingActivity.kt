package org.thoughtcrime.securesms.loki.activities

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_landing.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.loki.dialogs.LinkDeviceSlaveModeDialog
import org.thoughtcrime.securesms.loki.dialogs.LinkDeviceSlaveModeDialogDelegate
import org.thoughtcrime.securesms.loki.protocol.SessionResetImplementation
import org.thoughtcrime.securesms.loki.protocol.shelved.MultiDeviceProtocol
import org.thoughtcrime.securesms.loki.utilities.push
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.loki.utilities.show
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.curve25519.Curve25519
import org.session.libsignal.libsignal.ecc.Curve
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.libsignal.util.KeyHelper
import org.session.libsignal.service.loki.protocol.mentions.MentionsManager
import org.session.libsignal.service.loki.protocol.meta.SessionMetaProtocol
import org.session.libsignal.service.loki.protocol.shelved.multidevice.DeviceLink
import org.session.libsignal.service.loki.protocol.sessionmanagement.SessionManagementProtocol
import org.session.libsignal.service.loki.protocol.shelved.syncmessages.SyncMessagesProtocol
import org.session.libsignal.service.loki.utilities.hexEncodedPublicKey
import org.session.libsignal.service.loki.utilities.retryIfNeeded
import java.lang.UnsupportedOperationException

class LandingActivity : BaseActionBarActivity(), LinkDeviceSlaveModeDialogDelegate {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
        setUpActionBarSessionLogo(true)
        fakeChatView.startAnimating()
        registerButton.setOnClickListener { register() }
        restoreButton.setOnClickListener { restore() }
        restoreBackupButton.setOnClickListener {
            val intent = Intent(this, BackupRestoreActivity::class.java)
            push(intent)
        }
//        linkButton.setOnClickListener { linkDevice() }
        if (TextSecurePreferences.getWasUnlinked(this)) {
            Toast.makeText(this, R.string.activity_landing_device_unlinked_dialog_title, Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode != RESULT_OK) { return }
        val hexEncodedPublicKey = data!!.getStringExtra("hexEncodedPublicKey")
        requestDeviceLink(hexEncodedPublicKey)
    }

    private fun register() {
        val intent = Intent(this, RegisterActivity::class.java)
        push(intent)
    }

    private fun restore() {
        val intent = Intent(this, RestoreActivity::class.java)
        push(intent)
    }

    private fun linkDevice() {
        val intent = Intent(this, LinkDeviceActivity::class.java)
        show(intent, true)
    }

    private fun requestDeviceLink(hexEncodedPublicKey: String) {
        var seed: ByteArray? = null
        var keyPair: ECKeyPair? = null

        //FIXME AC: Previously we used the modified version of the Signal's Curve25519 lib to generate the seed and key pair.
        // If you need to restore this logic you should probably fork and patch the lib to support that method as well.
        // https://github.com/signalapp/curve25519-java
        fun generateKeyPair() {
            throw UnsupportedOperationException("Generating device link key pair is not supported at the moment.")
//            val seedCandidate = Curve25519.getInstance(Curve25519.BEST).generateSeed(16)
//            try {
//                keyPair = Curve.generateKeyPair(seedCandidate + seedCandidate) // Validate the seed
//            } catch (exception: Exception) {
//                return generateKeyPair()
//            }
//            seed = seedCandidate
        }
        generateKeyPair()
        IdentityKeyUtil.save(this, IdentityKeyUtil.LOKI_SEED, Hex.toStringCondensed(seed))
        IdentityKeyUtil.save(this, IdentityKeyUtil.IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(keyPair!!.publicKey.serialize()))
        IdentityKeyUtil.save(this, IdentityKeyUtil.IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(keyPair!!.privateKey.serialize()))
        val userHexEncodedPublicKey = keyPair!!.hexEncodedPublicKey
        val registrationID = KeyHelper.generateRegistrationId(false)
        TextSecurePreferences.setLocalRegistrationId(this, registrationID)
        DatabaseFactory.getIdentityDatabase(this).saveIdentity(Address.fromSerialized(userHexEncodedPublicKey),
                IdentityKeyUtil.getIdentityKeyPair(this).publicKey, IdentityDatabase.VerifiedStatus.VERIFIED,
                true, System.currentTimeMillis(), true)
        TextSecurePreferences.setLocalNumber(this, userHexEncodedPublicKey)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, true)
        TextSecurePreferences.setPromptedPushRegistration(this, true)
        val deviceLink = DeviceLink(hexEncodedPublicKey, userHexEncodedPublicKey).sign(DeviceLink.Type.REQUEST, keyPair!!.privateKey.serialize())
        if (deviceLink == null) {
            Log.d("Loki", "Failed to sign device link request.")
            reset()
            return Toast.makeText(application, R.string.device_linking_failed, Toast.LENGTH_LONG).show()
        }
        val application = ApplicationContext.getInstance(this)
        application.startPollingIfNeeded()
        val apiDB = DatabaseFactory.getLokiAPIDatabase(this)
        val threadDB = DatabaseFactory.getLokiThreadDatabase(this)
        val userDB = DatabaseFactory.getLokiUserDatabase(this)
        val sskDatabase = DatabaseFactory.getSSKDatabase(this)
        val userPublicKey = TextSecurePreferences.getLocalNumber(this)
        val sessionResetImpl = SessionResetImplementation(this)
        MentionsManager.configureIfNeeded(userPublicKey, threadDB, userDB)
        SessionMetaProtocol.configureIfNeeded(apiDB, userPublicKey)
        org.session.libsignal.service.loki.protocol.shelved.multidevice.MultiDeviceProtocol.configureIfNeeded(apiDB)
        SessionManagementProtocol.configureIfNeeded(sessionResetImpl, sskDatabase, application)
        SyncMessagesProtocol.configureIfNeeded(apiDB, userPublicKey)
        application.setUpP2PAPIIfNeeded()
        application.setUpStorageAPIIfNeeded()
        val linkDeviceDialog = LinkDeviceSlaveModeDialog()
        linkDeviceDialog.delegate = this
        linkDeviceDialog.show(supportFragmentManager, "Link Device Dialog")
        AsyncTask.execute {
            retryIfNeeded(8) {
                MultiDeviceProtocol.sendDeviceLinkMessage(this@LandingActivity, deviceLink.masterPublicKey, deviceLink)
            }
        }
    }

    override fun onDeviceLinkRequestAuthorized(deviceLink: DeviceLink) {
        TextSecurePreferences.setMasterHexEncodedPublicKey(this, deviceLink.masterPublicKey)
        val intent = Intent(this, HomeActivity::class.java)
        show(intent)
        finish()
    }

    override fun onDeviceLinkCanceled() {
        reset()
    }

    private fun reset() {
        IdentityKeyUtil.delete(this, IdentityKeyUtil.LOKI_SEED)
        TextSecurePreferences.removeLocalNumber(this)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, false)
        TextSecurePreferences.setPromptedPushRegistration(this, false)
        val application = ApplicationContext.getInstance(this)
        application.stopPolling()
    }
}