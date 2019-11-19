package org.thoughtcrime.securesms.loki

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import org.thoughtcrime.securesms.*
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.DynamicLanguage
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.LokiStorageAPI

class LinkedDevicesActivity : PassphraseRequiredActionBarActivity() {

  companion object {
    private val TAG = DeviceActivity::class.java.simpleName
  }

  private val dynamicTheme = DynamicTheme()
  private val dynamicLanguage = DynamicLanguage()
  private lateinit var deviceListFragment: DeviceListFragment

  public override fun onPreCreate() {
    dynamicTheme.onCreate(this)
    dynamicLanguage.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setTitle(R.string.AndroidManifest__linked_devices)
    this.deviceListFragment = DeviceListFragment()
    this.deviceListFragment.setAddDeviceButtonListener {
      // TODO: Hook up add device
    }
    this.deviceListFragment.setHandleDisconnectDevice { devicePublicKey ->
      // Purge the device pairing from our database
      val ourPublicKey = TextSecurePreferences.getLocalNumber(this)
      val database = DatabaseFactory.getLokiAPIDatabase(this)
      database.removePairingAuthorisation(ourPublicKey, devicePublicKey)
      // Update mapping on the file server
      LokiStorageAPI.shared.updateUserDeviceMappings()
      // Send a background message to let the device know that it has been revoked
      MessageSender.sendBackgroundMessage(this, devicePublicKey)
      // Refresh the list
      refresh()
      Toast.makeText(this, R.string.DeviceListActivity_unlinked_device, Toast.LENGTH_LONG).show()
      return@setHandleDisconnectDevice null
    }
    initFragment(android.R.id.content, deviceListFragment, dynamicLanguage.currentLocale)
    refresh()
  }

  private fun refresh() {
    val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this)
    val isDeviceLinkingEnabled = DatabaseFactory.getLokiAPIDatabase(this).getPairingAuthorisations(userHexEncodedPublicKey).isEmpty()
    this.deviceListFragment.setAddDeviceButtonVisible(isDeviceLinkingEnabled)
    this.deviceListFragment.refresh()
  }

  public override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
    dynamicLanguage.onResume(this)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return false
  }
}
