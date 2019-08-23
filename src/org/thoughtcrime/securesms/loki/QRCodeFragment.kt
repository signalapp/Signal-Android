package org.thoughtcrime.securesms.loki

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_qr_code.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationPreferencesActivity
import org.thoughtcrime.securesms.qr.QrCode
import org.thoughtcrime.securesms.util.TextSecurePreferences

class QRCodeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_qr_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val hexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
        val qrCode = QrCode.create(hexEncodedPublicKey)
        qrCodeImageView.setImageBitmap(qrCode)
    }

    override fun onResume() {
        super.onResume()
        val activity = activity as ApplicationPreferencesActivity
        activity.supportActionBar!!.setTitle(R.string.fragment_qr_code_title)
    }
}