package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_create_private_chat.*
import kotlinx.android.synthetic.main.fragment_enter_public_key.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragmentDelegate
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation

class CreatePrivateChatActivity : PassphraseRequiredActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = CreatePrivateChatActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Set content view
        setContentView(R.layout.activity_create_private_chat)
        // Set title
        supportActionBar!!.title = "New Session"
        // Set up view pager
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
    }
    // endregion

    // region Interaction
    override fun handleQRCodeScanned(hexEncodedPublicKey: String) {
        createPrivateChatIfPossible(hexEncodedPublicKey)
    }

    fun createPrivateChatIfPossible(hexEncodedPublicKey: String) {
        if (!PublicKeyValidation.isValid(hexEncodedPublicKey)) { return Toast.makeText(this, "Invalid Session ID", Toast.LENGTH_SHORT).show() }
        val masterHexEncodedPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(this)
        val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this)
        val targetHexEncodedPublicKey = if (hexEncodedPublicKey == masterHexEncodedPublicKey) userHexEncodedPublicKey else hexEncodedPublicKey
        val recipient = Recipient.from(this, Address.fromSerialized(targetHexEncodedPublicKey), false)
        val intent = Intent(this, ConversationActivity::class.java)
        intent.putExtra(ConversationActivity.ADDRESS_EXTRA, recipient.address)
        intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA))
        intent.setDataAndType(getIntent().data, getIntent().type)
        val existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient)
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread)
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT)
        startActivity(intent)
        finish()
    }
    // endregion
}

// region Adapter
private class CreatePrivateChatActivityAdapter(val activity: CreatePrivateChatActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return when (index) {
            0 -> EnterPublicKeyFragment()
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = activity
                result.message = "Scan a user’s QR code to start a session. QR codes can be found by tapping the QR code icon in account settings."
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence? {
        return when (index) {
            0 -> "Enter Session ID"
            1 -> "Scan QR Code"
            else -> throw IllegalStateException()
        }
    }
}
// endregion

// region Enter Public Key Fragment
class EnterPublicKeyFragment : Fragment() {

    private val hexEncodedPublicKey: String
        get() {
            val masterHexEncodedPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context!!)
            val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context!!)
            return masterHexEncodedPublicKey ?: userHexEncodedPublicKey
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_enter_public_key, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        publicKeyTextView.imeOptions = publicKeyTextView.imeOptions or 16777216 // Always use incognito keyboard
        publicKeyTextView.text = hexEncodedPublicKey
        copyButton.setOnClickListener { copyPublicKey() }
        shareButton.setOnClickListener { sharePublicKey() }
        createPrivateChatButton.setOnClickListener { createPrivateChatIfPossible() }
    }

    private fun copyPublicKey() {
        val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Session ID", hexEncodedPublicKey)
        clipboard.primaryClip = clip
        Toast.makeText(context!!, R.string.activity_register_public_key_copied_message, Toast.LENGTH_SHORT).show()
    }

    private fun sharePublicKey() {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.putExtra(Intent.EXTRA_TEXT, hexEncodedPublicKey)
        intent.type = "text/plain"
        startActivity(intent)
    }

    private fun createPrivateChatIfPossible() {
        val hexEncodedPublicKey = publicKeyEditText.text.trim().toString()
        (activity!! as CreatePrivateChatActivity).createPrivateChatIfPossible(hexEncodedPublicKey)
    }
}
// endregion
