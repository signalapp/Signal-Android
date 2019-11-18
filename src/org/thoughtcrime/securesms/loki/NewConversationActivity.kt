package org.thoughtcrime.securesms.loki

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.qr.ScanListener
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.utilities.Analytics
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation

class NewConversationActivity : PassphraseRequiredActionBarActivity(), ScanListener {
    private val dynamicTheme = DynamicTheme()

    override fun onPreCreate() {
        dynamicTheme.onCreate(this)
    }

    override fun onCreate(bundle: Bundle?, isReady: Boolean) {
        supportActionBar!!.setTitle(R.string.fragment_new_conversation_title)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        val fragment = NewConversationFragment()
        initFragment(android.R.id.content, fragment, null)
    }

    public override fun onResume() {
        super.onResume()
        dynamicTheme.onResume(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun scanQRCode() {
        Permissions.with(this)
            .request(Manifest.permission.CAMERA)
            .ifNecessary()
            .withPermanentDenialDialog(getString(R.string.fragment_scan_qr_code_camera_permission_dialog_message))
            .onAllGranted {
                val fragment = ScanQRCodeFragment()
                fragment.scanListener = this
                supportFragmentManager.beginTransaction().replace(android.R.id.content, fragment).addToBackStack(null).commitAllowingStateLoss()
            }
            .onAnyDenied { Toast.makeText(this, R.string.fragment_scan_qr_code_camera_permission_dialog_message, Toast.LENGTH_SHORT).show() }
            .execute()
    }

    override fun onQrDataFound(hexEncodedPublicKey: String) {
        Analytics.shared.track("QR Code Scanned")
        startNewConversationIfPossible(hexEncodedPublicKey)
    }

    fun startNewConversationIfPossible(hexEncodedPublicKey: String) {
        if (!PublicKeyValidation.isValid(hexEncodedPublicKey)) { return Toast.makeText(this, R.string.fragment_new_conversation_invalid_public_key_message, Toast.LENGTH_SHORT).show() }
        val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this)
        // If we try to contact our master device then redirect to note to self
        val contactPublicKey = if (TextSecurePreferences.getMasterHexEncodedPublicKey(this) == hexEncodedPublicKey) userHexEncodedPublicKey else hexEncodedPublicKey
        val contact = Recipient.from(this, Address.fromSerialized(contactPublicKey), true)
        val intent = Intent(this, ConversationActivity::class.java)
        intent.putExtra(ConversationActivity.ADDRESS_EXTRA, contact.address)
        intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA))
        intent.setDataAndType(getIntent().data, getIntent().type)
        val existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(contact)
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread)
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT)
        Analytics.shared.track("New Conversation Started")
        startActivity(intent)
        finish()
    }
}