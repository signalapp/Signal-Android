package org.thoughtcrime.securesms.loki.redesign.activities

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
import org.thoughtcrime.securesms.loki.redesign.dialogs.LinkDeviceSlaveModeDialog
import org.thoughtcrime.securesms.loki.redesign.dialogs.LinkDeviceSlaveModeDialogDelegate
import org.thoughtcrime.securesms.loki.redesign.utilities.push
import org.thoughtcrime.securesms.loki.redesign.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.loki.redesign.utilities.show
import org.thoughtcrime.securesms.loki.sendDeviceLinkMessage
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECKeyPair
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.signalservice.loki.api.DeviceLink
import org.whispersystems.signalservice.loki.utilities.hexEncodedPublicKey
import org.whispersystems.signalservice.loki.utilities.retryIfNeeded

class LandingActivity : BaseActionBarActivity(), LinkDeviceSlaveModeDialogDelegate {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
        setUpActionBarSessionLogo()
        fakeChatView.startAnimating()
        registerButton.setOnClickListener { register() }
        restoreButton.setOnClickListener { restore() }
        linkButton.setOnClickListener { linkDevice() }
        if (TextSecurePreferences.databaseResetFromUnpair(this)) {
            Toast.makeText(this, "Your device was unlinked successfully", Toast.LENGTH_LONG).show()
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
        fun generateKeyPair() {
            val seedCandidate = Curve25519.getInstance(Curve25519.BEST).generateSeed(16)
            try {
                keyPair = Curve.generateKeyPair(seedCandidate + seedCandidate) // Validate the seed
            } catch (exception: Exception) {
                return generateKeyPair()
            }
            seed = seedCandidate
        }
        generateKeyPair()
        IdentityKeyUtil.save(this, IdentityKeyUtil.lokiSeedKey, Hex.toStringCondensed(seed))
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
            return Toast.makeText(application, "Couldn't link device.", Toast.LENGTH_LONG).show()
        }
        val application = ApplicationContext.getInstance(this)
        application.startPollingIfNeeded()
        application.setUpP2PAPI()
        application.setUpStorageAPIIfNeeded()
        val linkDeviceDialog = LinkDeviceSlaveModeDialog()
        linkDeviceDialog.delegate = this
        linkDeviceDialog.show(supportFragmentManager, "Link Device Dialog")
        AsyncTask.execute {
            retryIfNeeded(8) {
                sendDeviceLinkMessage(this@LandingActivity, deviceLink.masterHexEncodedPublicKey, deviceLink)
            }
        }
    }

    override fun onDeviceLinkRequestAuthorized(deviceLink: DeviceLink) {
        TextSecurePreferences.setMasterHexEncodedPublicKey(this, deviceLink.masterHexEncodedPublicKey)
        val intent = Intent(this, HomeActivity::class.java)
        show(intent)
        finish()
    }

    override fun onDeviceLinkCanceled() {
        reset()
    }

    private fun reset() {
        IdentityKeyUtil.delete(this, IdentityKeyUtil.lokiSeedKey)
        TextSecurePreferences.removeLocalNumber(this)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, false)
        TextSecurePreferences.setPromptedPushRegistration(this, false)
    }
}