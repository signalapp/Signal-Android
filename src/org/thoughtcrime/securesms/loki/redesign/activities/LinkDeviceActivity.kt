package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_link_device.*
import kotlinx.android.synthetic.main.fragment_enter_session_id.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragmentDelegate
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation

class LinkDeviceActivity : BaseActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = LinkDeviceActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set content view
        setContentView(R.layout.activity_link_device)
        // Set title
        supportActionBar!!.title = "Link Device"
        // Set up view pager
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
    }
    // endregion

    // region Interaction
    override fun handleQRCodeScanned(hexEncodedPublicKey: String) {
        requestDeviceLinkIfPossible(hexEncodedPublicKey)
    }

    fun requestDeviceLinkIfPossible(hexEncodedPublicKey: String) {
        if (!PublicKeyValidation.isValid(hexEncodedPublicKey)) {
            Toast.makeText(this, "Invalid Session ID", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent()
            intent.putExtra("hexEncodedPublicKey", hexEncodedPublicKey)
            setResult(RESULT_OK, intent)
            finish()
        }
    }
    // endregion
}

// region Adapter
private class LinkDeviceActivityAdapter(val activity: LinkDeviceActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return when (index) {
            0 -> EnterSessionIDFragment()
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = activity
                result.message = "Navigate to \"Settings\" > \"Devices\" > \"Link a Device\" on your other device and then scan the QR code that comes up to start the linking process."
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

// region Enter Session ID Fragment
class EnterSessionIDFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_enter_session_id, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionIDEditText.imeOptions = sessionIDEditText.imeOptions or 16777216 // Always use incognito keyboard
        requestDeviceLinkButton.setOnClickListener { requestDeviceLinkIfPossible() }
    }

    private fun requestDeviceLinkIfPossible() {
        val inputMethodManager = context!!.getSystemService(BaseActionBarActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(sessionIDEditText.windowToken, 0)
        val hexEncodedPublicKey = sessionIDEditText.text.trim().toString().toLowerCase()
        (activity!! as LinkDeviceActivity).requestDeviceLinkIfPossible(hexEncodedPublicKey)
    }
}
// endregion