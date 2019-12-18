package org.thoughtcrime.securesms.loki.redesign.activities

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_join_public_chat.*
import kotlinx.android.synthetic.main.fragment_enter_chat_url.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragmentDelegate

class JoinPublicChatActivity : BaseActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = JoinPublicChatActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set content view
        setContentView(R.layout.activity_join_public_chat)
        // Set title
        supportActionBar!!.title = "Join Public Chat"
        // Set up view pager
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
    }
    // endregion

    // region Interaction
    override fun handleQRCodeScanned(url: String) {
        joinPublicChatIfPossible(url)
    }

    fun joinPublicChatIfPossible(url: String) {
        // TODO: Implement
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
                result.message = "Scan the QR code of the public chat you'd like to join"
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence? {
        return when (index) {
            0 -> "Enter Chat URL"
            1 -> "Scan QR Code"
            else -> throw IllegalStateException()
        }
    }
}
// endregion

// region Enter Public Key Fragment
class EnterChatURLFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_enter_chat_url, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        joinPublicChatButton.setOnClickListener { joinPublicChatIfPossible() }
    }

    private fun joinPublicChatIfPossible() {
        val chatURL = chatURLEditText.text.trim().toString()
        (activity!! as JoinPublicChatActivity).joinPublicChatIfPossible(chatURL)
    }
}
// endregion