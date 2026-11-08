package org.signal.storage.keyvalue

class ProxyValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_PROXY_ENABLED = "proxy.enabled"
    private const val KEY_HOST = "proxy.host"
    private const val KEY_PORT = "proxy.port"
  }

  public override fun onFirstEverAppLaunch() = Unit

  override val keysToIncludeInBackup: List<String>
    get() = listOf(KEY_PROXY_ENABLED, KEY_HOST, KEY_PORT)

  fun enableProxy(host: String, port: Int) {
    require(host.isNotEmpty()) { "Empty host!" }

    store.beginWrite()
      .putBoolean(KEY_PROXY_ENABLED, true)
      .putString(KEY_HOST, host)
      .putInteger(KEY_PORT, port)
      .apply()
  }

  /**
   * Disables the proxy, but does not clear out the last-chosen host.
   */
  fun disableProxy() {
    putBoolean(KEY_PROXY_ENABLED, false)
  }

  /**
   * Clears out the last-chosen host and port. Does not change whether the proxy is enabled.
   */
  fun clearProxy() {
    store.beginWrite()
      .remove(KEY_HOST)
      .remove(KEY_PORT)
      .apply()
  }

  val isProxyEnabled: Boolean by booleanValue(KEY_PROXY_ENABLED, false)

  val proxyHost: String? by stringValue(KEY_HOST, null)

  val proxyPort: Int by integerValue(KEY_PORT, 0)
}
