package org.thoughtcrime.securesms.loki.redesign.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_join_public_chat.*
import kotlinx.android.synthetic.main.fragment_enter_chat_url.*
import network.loki.messenger.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragmentDelegate
import org.thoughtcrime.securesms.loki.redesign.utilities.OpenGroupUtilities
import org.thoughtcrime.securesms.sms.MessageSender

class JoinPublicChatActivity : PassphraseRequiredActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = JoinPublicChatActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Set content view
        setContentView(R.layout.activity_join_public_chat)
        // Set title
        supportActionBar!!.title = "Join Open Group"
        // Set up view pager
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
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
    override fun handleQRCodeScanned(url: String) {
        joinPublicChatIfPossible(url)
    }

    fun joinPublicChatIfPossible(url: String) {
        if (!Patterns.WEB_URL.matcher(url).matches() || !url.startsWith("https://")) {
            return Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
        }
        showLoader()

        val channel: Long = 1
        OpenGroupUtilities.addGroup(this, url, channel).success {
            MessageSender.syncAllOpenGroups(this)
        }.successUi {
            finish()
        }.failUi {
            hideLoader()
            Toast.makeText(this, "Couldn't join channel", Toast.LENGTH_SHORT).show()
        }
    }
    // endregion
}

// region Adapter
private class JoinPublicChatActivityAdapter(val activity: JoinPublicChatActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return when (index) {
            0 -> EnterChatURLFragment()
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = activity
                result.message = "Scan the QR code of the open group you'd like to join"
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence? {
        return when (index) {
            0 -> "Open Group URL"
            1 -> "Scan QR Code"
            else -> throw IllegalStateException()
        }
    }
}
// endregion

// region Enter Chat URL Fragment
class EnterChatURLFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_enter_chat_url, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatURLEditText.imeOptions = chatURLEditText.imeOptions or 16777216 // Always use incognito keyboard
        joinPublicChatButton.setOnClickListener { joinPublicChatIfPossible() }
    }

    private fun joinPublicChatIfPossible() {
        val inputMethodManager = context!!.getSystemService(BaseActionBarActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(chatURLEditText.windowToken, 0)
        var chatURL = chatURLEditText.text.trim().toString().toLowerCase().replace("http://", "https://")
        if (!chatURL.toLowerCase().startsWith("https")) {
            chatURL = "https://$chatURL"
        }
        (activity!! as JoinPublicChatActivity).joinPublicChatIfPossible(chatURL)
    }
}
// endregion