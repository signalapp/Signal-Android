package org.thoughtcrime.securesms.loki.redesign.activities

import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_linked_devices.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.devicelist.Device
import org.thoughtcrime.securesms.loki.redesign.dialogs.LinkDeviceMasterModeDialog
import org.thoughtcrime.securesms.loki.redesign.dialogs.LinkDeviceMasterModeDialogDelegate
import org.thoughtcrime.securesms.loki.signAndSendPairingAuthorisationMessage
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.loki.api.PairingAuthorisation

class LinkedDevicesActivity : PassphraseRequiredActionBarActivity, LoaderManager.LoaderCallbacks<List<Device>>, LinkDeviceMasterModeDialogDelegate {
    private val linkedDevicesAdapter = LinkedDevicesAdapter(this)
    private var devices = listOf<Device>()
        set(value) { field = value; linkedDevicesAdapter.devices = value }

    // region Lifecycle
    constructor() : super()

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_linked_devices)
        supportActionBar!!.title = "Linked Devices"
        recyclerView.adapter = linkedDevicesAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        linkDeviceButton.setOnClickListener { linkDevice() }
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_linked_devices, menu)
        return true
    }
    // endregion

    // region Updating
    override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<List<Device>> {
        return LinkedDevicesLoader(this)
    }

    override fun onLoadFinished(loader: Loader<List<Device>>, devices: List<Device>?) {
        update(devices ?: listOf())
    }

    override fun onLoaderReset(loader: Loader<List<Device>>) {
        update(listOf())
    }

    private fun update(devices: List<Device>) {
        this.devices = devices
        emptyStateContainer.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.linkDeviceButton -> linkDevice()
            else -> { /* Do nothing */ }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun linkDevice() {
        if (devices.isEmpty()) {
            val linkDeviceDialog = LinkDeviceMasterModeDialog()
            linkDeviceDialog.delegate = this
            linkDeviceDialog.show(supportFragmentManager, "Link Device Dialog")
        } else {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Multi Device Limit Reached")
            builder.setMessage("It's currently not allowed to link more than one device.")
            builder.setPositiveButton("OK", { dialog, _ -> dialog.dismiss() })
            builder.create().show()
        }
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
    // endregion
}