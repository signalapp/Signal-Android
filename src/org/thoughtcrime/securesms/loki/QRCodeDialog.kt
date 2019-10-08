package org.thoughtcrime.securesms.loki

import android.content.Context
import android.support.v7.app.AlertDialog
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_qr_code.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.qr.QrCode
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences

object QRCodeDialog {

    fun show(context: Context) {
        val view = QRCodeView(context)
        val dialog = AlertDialog.Builder(context).setView(view).show()
        view.onCancel = { dialog.dismiss() }
    }
}

class QRCodeView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
    var onCancel: (() -> Unit)? = null

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    init {
        inflate(context, R.layout.view_qr_code, this)
        val hexEncodedPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(getContext()) ?: TextSecurePreferences.getLocalNumber(context)
        val displayMetrics = DisplayMetrics()
        ServiceUtil.getWindowManager(context).defaultDisplay.getMetrics(displayMetrics)
        val size = displayMetrics.widthPixels - 2 * toPx(96, resources)
        val qrCode = QrCode.create(hexEncodedPublicKey, size)
        qrCodeImageView.setImageBitmap(qrCode)
        cancelButton.setOnClickListener { onCancel?.invoke() }
    }
}