package org.thoughtcrime.securesms.loki.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_create_private_chat.*
import kotlinx.android.synthetic.main.fragment_enter_public_key.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.DistributionTypes
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragmentDelegate
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.PublicKeyValidation


class CreatePrivateChatActivity : PassphraseRequiredActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = CreatePrivateChatActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Set content view
        setContentView(R.layout.activity_create_private_chat)
        // Set title
        supportActionBar!!.title = resources.getString(R.string.activity_create_private_chat_title)
        // Set up view pager
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_done, menu)
        return true
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.doneButton -> adapter.enterPublicKeyFragment.createPrivateChatIfPossible()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun handleQRCodeScanned(hexEncodedPublicKey: String) {
        createPrivateChatIfPossible(hexEncodedPublicKey)
    }

    fun createPrivateChatIfPossible(hexEncodedPublicKey: String) {
        if (!PublicKeyValidation.isValid(hexEncodedPublicKey)) { return Toast.makeText(this, R.string.invalid_session_id, Toast.LENGTH_SHORT).show() }
        val recipient = Recipient.from(this, Address.fromSerialized(hexEncodedPublicKey), false)
        val intent = Intent(this, ConversationActivity::class.java)
        intent.putExtra(ConversationActivity.ADDRESS_EXTRA, recipient.address)
        intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA))
        intent.setDataAndType(getIntent().data, getIntent().type)
        val existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient)
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread)
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, DistributionTypes.DEFAULT)
        startActivity(intent)
        finish()
    }
    // endregion
}

// region Adapter
private class CreatePrivateChatActivityAdapter(val activity: CreatePrivateChatActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {
    val enterPublicKeyFragment = EnterPublicKeyFragment()

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return when (index) {
            0 -> enterPublicKeyFragment
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = activity
                result.message = activity.resources.getString(R.string.activity_create_private_chat_scan_qr_code_explanation)
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence? {
        return when (index) {
            0 -> activity.resources.getString(R.string.activity_create_private_chat_enter_session_id_tab_title)
            1 -> activity.resources.getString(R.string.activity_create_private_chat_scan_qr_code_tab_title)
            else -> throw IllegalStateException()
        }
    }
}
// endregion

// region Enter Public Key Fragment
class EnterPublicKeyFragment : Fragment() {

    private val hexEncodedPublicKey: String
        get() {
            return TextSecurePreferences.getLocalNumber(requireContext())!!
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_enter_public_key, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        publicKeyEditText.imeOptions = EditorInfo.IME_ACTION_DONE or 16777216 // Always use incognito keyboard
        publicKeyEditText.setRawInputType(InputType.TYPE_CLASS_TEXT)
        publicKeyEditText.setOnEditorActionListener { v, actionID, _ ->
            if (actionID == EditorInfo.IME_ACTION_DONE) {
                val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                createPrivateChatIfPossible()
                true
            } else {
                false
            }
        }
        publicKeyTextView.text = hexEncodedPublicKey
        copyButton.setOnClickListener { copyPublicKey() }
        shareButton.setOnClickListener { sharePublicKey() }
        createPrivateChatButton.setOnClickListener { createPrivateChatIfPossible() }
    }

    private fun copyPublicKey() {
        val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Session ID", hexEncodedPublicKey)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun sharePublicKey() {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.putExtra(Intent.EXTRA_TEXT, hexEncodedPublicKey)
        intent.type = "text/plain"
        startActivity(intent)
    }

    fun createPrivateChatIfPossible() {
        val hexEncodedPublicKey = publicKeyEditText.text?.trim().toString() ?: ""
        (requireActivity() as CreatePrivateChatActivity).createPrivateChatIfPossible(hexEncodedPublicKey)
    }
}
// endregion
