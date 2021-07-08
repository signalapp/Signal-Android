package org.thoughtcrime.securesms.loki.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import kotlinx.android.synthetic.main.activity_create_private_chat.*
import kotlinx.android.synthetic.main.fragment_enter_public_key.*
import network.loki.messenger.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.DistributionTypes
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity

import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragmentDelegate

class CreatePrivateChatActivity : PassphraseRequiredActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = CreatePrivateChatActivityAdapter(this)
    private var isKeyboardShowing = false
        set(value) {
            val hasChanged = (field != value)
            field = value
            if (hasChanged) {
                adapter.isKeyboardShowing = value
            }
        }

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
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {

            override fun onGlobalLayout() {
                val diff = rootLayout.rootView.height - rootLayout.height
                val displayMetrics = this@CreatePrivateChatActivity.resources.displayMetrics
                val estimatedKeyboardHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200.0f, displayMetrics)
                this@CreatePrivateChatActivity.isKeyboardShowing = (diff > estimatedKeyboardHeight)
            }
        })
    }
    // endregion

    // region Updating
    private fun showLoader() {
        loader.visibility = View.VISIBLE
        loader.animate().setDuration(150).alpha(1.0f).start()
    }

    private fun hideLoader() {
        loader.animate().setDuration(150).alpha(0.0f).setListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                loader.visibility = View.GONE
            }
        })
    }
    // endregion

    // region Interaction
    override fun handleQRCodeScanned(hexEncodedPublicKey: String) {
        createPrivateChatIfPossible(hexEncodedPublicKey)
    }

    fun createPrivateChatIfPossible(onsNameOrPublicKey: String) {
        if (PublicKeyValidation.isValid(onsNameOrPublicKey)) {
            createPrivateChat(onsNameOrPublicKey)
        } else {
            // This could be an ONS name
            showLoader()
            SnodeAPI.getSessionIDFor(onsNameOrPublicKey).successUi { hexEncodedPublicKey ->
                hideLoader()
                this.createPrivateChat(hexEncodedPublicKey)
            }.failUi { exception ->
                hideLoader()
                var message = resources.getString(R.string.fragment_enter_public_key_error_message)
                exception.localizedMessage?.let {
                    message = it
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createPrivateChat(hexEncodedPublicKey: String) {
        val recipient = Recipient.from(this, Address.fromSerialized(hexEncodedPublicKey), false)
        val intent = Intent(this, ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.ADDRESS, recipient.address)
        intent.setDataAndType(getIntent().data, getIntent().type)
        val existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient)
        intent.putExtra(ConversationActivityV2.THREAD_ID, existingThread)
        startActivity(intent)
        finish()
    }
    // endregion
}

// region Adapter
private class CreatePrivateChatActivityAdapter(val activity: CreatePrivateChatActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {
    val enterPublicKeyFragment = EnterPublicKeyFragment()
    var isKeyboardShowing = false
        set(value) { field = value; enterPublicKeyFragment.isKeyboardShowing = isKeyboardShowing }

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
    var isKeyboardShowing = false
        set(value) { field = value; handleIsKeyboardShowingChanged() }

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

    private fun handleIsKeyboardShowingChanged() {
        optionalContentContainer.isVisible = !isKeyboardShowing
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

    private fun createPrivateChatIfPossible() {
        val hexEncodedPublicKey = publicKeyEditText.text?.trim().toString()
        val activity = requireActivity() as CreatePrivateChatActivity
        activity.createPrivateChatIfPossible(hexEncodedPublicKey)
    }
}
// endregion
