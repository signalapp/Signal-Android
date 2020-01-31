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
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_linked_devices.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.devicelist.Device
import org.thoughtcrime.securesms.loki.redesign.views.DeviceEditingOptionsBottomSheet
import org.thoughtcrime.securesms.loki.redesign.dialogs.EditDeviceNameDialog
import org.thoughtcrime.securesms.loki.redesign.dialogs.EditDeviceNameDialogDelegate
import org.thoughtcrime.securesms.loki.redesign.dialogs.LinkDeviceMasterModeDialog
import org.thoughtcrime.securesms.loki.redesign.dialogs.LinkDeviceMasterModeDialogDelegate
import org.thoughtcrime.securesms.loki.signAndSendPairingAuthorisationMessage
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.loki.api.LokiStorageAPI
import org.whispersystems.signalservice.loki.api.PairingAuthorisation

class LinkedDevicesActivity : PassphraseRequiredActionBarActivity, LoaderManager.LoaderCallbacks<List<Device>>, DeviceClickListener, EditDeviceNameDialogDelegate, LinkDeviceMasterModeDialogDelegate {
    private var devices = listOf<Device>()
        set(value) { field = value; linkedDevicesAdapter.devices = value }

    private val linkedDevicesAdapter by lazy {
        val result = LinkedDevicesAdapter(this)
        result.deviceClickListener = this
        result
    }

    // region Lifecycle
    constructor() : super()

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_linked_devices)
        supportActionBar!!.title = "Devices"
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

    override fun handleDeviceNameChanged(device: Device) {
        LoaderManager.getInstance(this).restartLoader(0, null, this)
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when(id) {
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

    override fun onDeviceClick(device: Device) {
        val bottomSheet = DeviceEditingOptionsBottomSheet()
        bottomSheet.onEditTapped = {
            bottomSheet.dismiss()
            val editDeviceNameDialog = EditDeviceNameDialog()
            editDeviceNameDialog.device = device
            editDeviceNameDialog.delegate = this
            editDeviceNameDialog.show(supportFragmentManager, "Edit Device Name Dialog")
        }
        bottomSheet.onUnlinkTapped = {
            bottomSheet.dismiss()
            unlinkDevice(device.id)
        }
        bottomSheet.show(supportFragmentManager, bottomSheet.tag)
    }

    private fun unlinkDevice(slaveDeviceHexEncodedPublicKey: String) {
        val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this)
        val database = DatabaseFactory.getLokiAPIDatabase(this)
        database.removePairingAuthorisation(userHexEncodedPublicKey, slaveDeviceHexEncodedPublicKey)
        LokiStorageAPI.shared.updateUserDeviceMappings().success {
            MessageSender.sendUnpairRequest(this, slaveDeviceHexEncodedPublicKey)
        }
        LoaderManager.getInstance(this).restartLoader(0, null, this)
        Toast.makeText(this, "Your device was unlinked successfully", Toast.LENGTH_LONG).show()
    }

    override fun onDeviceLinkRequestAuthorized(authorization: PairingAuthorisation) {
        AsyncTask.execute {
            signAndSendPairingAuthorisationMessage(this, authorization)
            Util.runOnMain {
                LoaderManager.getInstance(this).restartLoader(0, null, this)
            }
        }
    }

    override fun onDeviceLinkCanceled() {
        // Do nothing
    }
    // endregion
}