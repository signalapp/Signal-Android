package org.thoughtcrime.securesms.messages

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [31])
class NetworkConnectionListenerTest {

  @Test
  fun `default network capability changes notify listener`() {
    val unavailableEvents = mutableListOf<Boolean>()
    val listener = NetworkConnectionListener(
      context = ApplicationProvider.getApplicationContext(),
      onNetworkLost = { isNetworkUnavailable -> unavailableEvents += isNetworkUnavailable() },
      onProxySettingsChanged = {}
    )
    val callback = ReflectionHelpers.getField<ConnectivityManager.NetworkCallback>(listener, "networkChangedCallback")
    val network = mockk<Network>()

    callback.onCapabilitiesChanged(network, capabilities(hasInternet = true, validated = false))
    callback.onCapabilitiesChanged(network, capabilities(hasInternet = true, validated = false))
    callback.onCapabilitiesChanged(network, capabilities(hasInternet = true, validated = true))
    callback.onCapabilitiesChanged(network, capabilities(hasInternet = false, validated = false))

    assertThat(unavailableEvents).containsExactly(false, false, true)
  }

  private fun capabilities(hasInternet: Boolean, validated: Boolean): NetworkCapabilities {
    val capabilities = NetworkCapabilities()

    if (hasInternet) {
      capabilities.addCapabilityReflectively(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    if (validated) {
      capabilities.addCapabilityReflectively(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    return capabilities
  }

  private fun NetworkCapabilities.addCapabilityReflectively(capability: Int) {
    val method = NetworkCapabilities::class.java.getDeclaredMethod("addCapability", Int::class.javaPrimitiveType)
    method.isAccessible = true
    method.invoke(this, capability)
  }
}
