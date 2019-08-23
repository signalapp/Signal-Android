package org.thoughtcrime.securesms.loki

import android.annotation.TargetApi
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import network.loki.messenger.R
import org.thoughtcrime.securesms.components.camera.CameraView
import org.thoughtcrime.securesms.qr.ScanListener
import org.thoughtcrime.securesms.qr.ScanningThread
import org.thoughtcrime.securesms.util.ViewUtil

class ScanQRCodeFragment : Fragment() {

    private var container: ViewGroup? = null
    private var overlay: LinearLayout? = null
    private var scannerView: CameraView? = null
    private var scanningThread: ScanningThread? = null
    private var scanListener: ScanListener? = null

    override fun onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View? {
        this.container = ViewUtil.inflate(inflater, viewGroup!!, R.layout.fragment_scan_qr_code)
        this.overlay = ViewUtil.findById(this.container!!, R.id.overlay)
        this.scannerView = ViewUtil.findById(this.container!!, R.id.cameraView)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            this.overlay!!.orientation = LinearLayout.HORIZONTAL
        } else {
            this.overlay!!.orientation = LinearLayout.VERTICAL
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.container!!.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    v.removeOnLayoutChangeListener(this)

                    val reveal = ViewAnimationUtils.createCircularReveal(v, right, bottom, 0f, Math.hypot(right.toDouble(), bottom.toDouble()).toInt().toFloat())
                    reveal.interpolator = DecelerateInterpolator(2f)
                    reveal.duration = 800
                    reveal.start()
                }
            })
        }

        return this.container
    }

    override fun onResume() {
        super.onResume()
        this.scanningThread = ScanningThread()
        this.scanningThread!!.setScanListener(scanListener)
        this.scannerView!!.onResume()
        this.scannerView!!.setPreviewCallback(scanningThread!!)
        this.scanningThread!!.start()
        val activity = activity as NewConversationActivity
        activity.supportActionBar!!.setTitle(R.string.fragment_scan_qr_code_title)
    }

    override fun onPause() {
        super.onPause()
        this.scannerView!!.onPause()
        this.scanningThread!!.stopScanning()
    }

    override fun onConfigurationChanged(newConfiguration: Configuration?) {
        super.onConfigurationChanged(newConfiguration)

        this.scannerView!!.onPause()

        if (newConfiguration!!.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            overlay!!.orientation = LinearLayout.HORIZONTAL
        } else {
            overlay!!.orientation = LinearLayout.VERTICAL
        }

        this.scannerView!!.onResume()
        this.scannerView!!.setPreviewCallback(scanningThread!!)
    }

    fun setScanListener(scanListener: ScanListener) {
        this.scanListener = scanListener

        if (this.scanningThread != null) {
            this.scanningThread!!.setScanListener(scanListener)
        }
    }


}