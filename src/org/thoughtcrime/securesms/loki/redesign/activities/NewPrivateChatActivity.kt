package org.thoughtcrime.securesms.loki.redesign.activities

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_new_private_chat.*
import kotlinx.android.synthetic.main.fragment_enter_public_key.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity

class NewPrivateChatActivity : BaseActionBarActivity() {
    private val adapter = Adapter(supportFragmentManager)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set content view
        setContentView(R.layout.activity_new_private_chat)
        // Set title
        supportActionBar!!.title = "New Conversation"
        // Set up view pager
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
    }
    // endregion
}

// region Adapter
private class Adapter(manager: FragmentManager) : FragmentPagerAdapter(manager) {

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return EnterPublicKeyFragment()
    }

    override fun getPageTitle(index: Int): CharSequence? {
        return when (index) {
            0 -> "Enter Public Key"
            1 -> "Scan QR Code"
            else -> throw IllegalStateException()
        }
    }
}
// endregion

class EnterPublicKeyFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_enter_public_key, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        separatorView.title = "Test"
    }
}