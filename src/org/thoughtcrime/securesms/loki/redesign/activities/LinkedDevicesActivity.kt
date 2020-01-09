package org.thoughtcrime.securesms.loki.redesign.activities

import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_linked_devices.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.dialogs.LinkDeviceMasterModeDialog
import org.thoughtcrime.securesms.loki.redesign.dialogs.LinkDeviceMasterModeDialogDelegate
import org.thoughtcrime.securesms.loki.signAndSendPairingAuthorisationMessage
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.loki.api.PairingAuthorisation

class LinkedDevicesActivity : PassphraseRequiredActionBarActivity, LinkDeviceMasterModeDialogDelegate {

    constructor() : super()

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_linked_devices)
        supportActionBar!!.title = "Linked Devices"
//        val homeAdapter = LinkedDevicesAdapter(this, cursor)
//        recyclerView.adapter = homeAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        linkDeviceButton.setOnClickListener { linkDevice() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_linked_devices, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.linkDeviceButton -> linkDevice()
            else -> { /* Do nothing */ }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun linkDevice() {
        val linkDeviceDialog = LinkDeviceMasterModeDialog()
        linkDeviceDialog.delegate = this
        linkDeviceDialog.show(supportFragmentManager, "Link Device Dialog")
    }

    override fun onDeviceLinkRequestAuthorized(authorization: PairingAuthorisation) {
        AsyncTask.execute {
            signAndSendPairingAuthorisationMessage(this, authorization)
            Util.runOnMain {

            }
        }
    }

    override fun onDeviceLinkCanceled() {
        // Do nothing
    }
}