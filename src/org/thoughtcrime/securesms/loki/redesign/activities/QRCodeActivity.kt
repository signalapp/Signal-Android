package org.thoughtcrime.securesms.loki.redesign.activities

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_qr_code.*
import kotlinx.android.synthetic.main.fragment_view_my_qr_code.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.redesign.fragments.ScanQRCodeWrapperFragmentDelegate
import org.thoughtcrime.securesms.loki.redesign.utilities.QRCodeUtilities
import org.thoughtcrime.securesms.loki.toPx
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.FileProviderUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation
import java.io.File
import java.io.FileOutputStream

class QRCodeActivity : PassphraseRequiredActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = QRCodeActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Set content view
        setContentView(R.layout.activity_qr_code)
        // Set title
        supportActionBar!!.title = "QR Code"
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
private class QRCodeActivityAdapter(val activity: QRCodeActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return when (index) {
            0 -> ViewMyQRCodeFragment()
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = activity
                result.message = "Scan someone\'s QR code to start a conversation with them"
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence? {
        return when (index) {
            0 -> "View My QR Code"
            1 -> "Scan QR Code"
            else -> throw IllegalStateException()
        }
    }
}
// endregion

// region View My QR Code Fragment
class ViewMyQRCodeFragment : Fragment() {

    private val hexEncodedPublicKey: String
        get() {
            val masterHexEncodedPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context!!)
            val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context!!)
            return masterHexEncodedPublicKey ?: userHexEncodedPublicKey
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_view_my_qr_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val size = toPx(280, resources)
        val qrCode = QRCodeUtilities.encode(hexEncodedPublicKey, size, false, false)
        qrCodeImageView.setImageBitmap(qrCode)
//        val explanation = SpannableStringBuilder("This is your unique public QR code. Other users can scan this to start a conversation with you.")
//        explanation.setSpan(StyleSpan(Typeface.BOLD), 8, 34, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        explanationTextView.text = "This is your QR code. Other users can scan it to start a session with you."
        shareButton.setOnClickListener { shareQRCode() }
    }

    private fun shareQRCode() {
        fun proceed() {
            val directory = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES)
            val fileName = "$hexEncodedPublicKey.png"
            val file = File(directory, fileName)
            file.createNewFile()
            val fos = FileOutputStream(file)
            val size = toPx(280, resources)
            val qrCode = QRCodeUtilities.encode(hexEncodedPublicKey, size, false, false)
            qrCode.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(Intent.EXTRA_STREAM, FileProviderUtil.getUriFor(activity!!, file))
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.type = "image/png"
            startActivity(Intent.createChooser(intent, "Share QR Code"))
        }
        if (RxPermissions(this).isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            proceed()
        } else {
            @SuppressWarnings("unused")
            val unused = RxPermissions(this).request(Manifest.permission.WRITE_EXTERNAL_STORAGE).subscribe { isGranted ->
                if (isGranted) {
                    proceed()
                }
            }
        }
    }
}
// endregion