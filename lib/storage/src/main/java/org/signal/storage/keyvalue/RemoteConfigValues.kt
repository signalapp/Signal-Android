package org.signal.storage.keyvalue

import org.signal.storage.protos.RemoteConfigOverrides

class RemoteConfigValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val CURRENT_CONFIG = "remote_config"
    private const val PENDING_CONFIG = "pending_remote_config"
    private const val LAST_FETCH_TIME = "remote_config_last_fetch_time"
    private const val ETAG = "etag"
    private const val OVERRIDES = "internal.remote_config_overrides"
  }

  public override fun onFirstEverAppLaunch() = Unit

  override val keysToIncludeInBackup: List<String>
    get() = emptyList()

  var currentConfig: String? by stringValue(CURRENT_CONFIG, null)

  var pendingConfig: String?
    get() = getString(PENDING_CONFIG, currentConfig)
    set(value) = putString(PENDING_CONFIG, value)

  var lastFetchTime: Long by longValue(LAST_FETCH_TIME, 0)

  var eTag: String? by stringValue(ETAG, "")

  private var overridesProto: RemoteConfigOverrides by protoValue(OVERRIDES, RemoteConfigOverrides(), RemoteConfigOverrides.ADAPTER)

  /**
   * Locally-overridden remote config values, keyed by config key. Only internal users can set these, but they
   * aren't gated on the internal user flag, because they're read while remote config is initializing.
   */
  var overrides: Map<String, String>
    get() = overridesProto.valueByKey
    set(value) {
      overridesProto = RemoteConfigOverrides(valueByKey = value)
    }
}
