package org.thoughtcrime.securesms.loki.activities

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_link_device.*
import kotlinx.android.synthetic.main.fragment_enter_session_id.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragmentDelegate
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation

class LinkDeviceActivity : BaseActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = LinkDeviceActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set content view
        setContentView(R.layout.activity_link_device)
        // Set title
        supportActionBar!!.title = resources.getString(R.string.activity_link_device_title)
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
            Toast.makeText(this, R.string.invalid_session_id, Toast.LENGTH_SHORT).show()
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
                result.message = activity.resources.getString(R.string.activity_link_device_scan_qr_code_explanation)
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence? {
        return when (index) {
            0 -> activity.getString(R.string.activity_link_device_enter_session_id_tab_title)
            1 -> activity.getString(R.string.activity_link_device_scan_qr_code_tab_title)
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