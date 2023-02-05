package org.thoughtcrime.securesms.registration.secondary

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.loaders.DeviceListLoader
import org.thoughtcrime.securesms.devicelist.protos.DeviceName
import java.nio.charset.Charset

class DeviceNameCipherTest {

  @Test
  fun encryptDeviceName() {
    val deviceName = "xXxCoolDeviceNamexXx"
    val identityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()

    val encryptedDeviceName = DeviceNameCipher.encryptDeviceName(deviceName.toByteArray(Charset.forName("UTF-8")), identityKeyPair)

    val plaintext = DeviceListLoader.decryptName(DeviceName.ADAPTER.decode(encryptedDeviceName), identityKeyPair)

    assertThat(String(plaintext, Charset.forName("UTF-8")), `is`(deviceName))
  }
}
