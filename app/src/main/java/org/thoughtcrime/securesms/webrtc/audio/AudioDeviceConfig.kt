/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.webrtc.audio

import android.content.pm.PackageManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.util.logging.Log
import org.signal.ringrtc.AudioConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.IOException

/**
 * Remote config for audio devices to allow for more targeted configurations
 */
object AudioDeviceConfig {
  private val TAG = Log.tag(AudioDeviceConfig::class.java)

  private val CUSTOM_ROMS = "(lineage|calyxos)".toRegex(RegexOption.IGNORE_CASE)

  private var currentConfig: AudioConfig? = null

  @Synchronized
  fun getCurrentConfig(): AudioConfig {
    if (currentConfig == null) {
      currentConfig = computeConfig()
    }
    return currentConfig!!
  }

  @Synchronized
  fun refresh() {
    currentConfig = computeConfig()
  }

  /**
   * Defines rules that can be filtered and applied to a specific device based on the provided criteria
   * @param target the targets to apply the rule to; can be specific devices and/or device classes
   * @param settings the audio settings that the rule will set
   * @param final forces an exit from the rule matching if true
   */
  data class Rule(
    @JsonProperty("target") val target: Target = Target(),
    @JsonProperty("settings") val settings: Settings = Settings(),
    @JsonProperty("final") val isFinal: Boolean = false
  )

  /**
   * Defines the target devices for which a given rule should be applied
   * @param include a list of device models to include; wildcard suffixes are supported
   * @param exclude a list of device models to exclude; wildcard suffixes are supported
   * @param custom whether or not a custom (non-AOSP) ROM should be considered
   */
  data class Target(
    @JsonProperty("include") val include: List<String> = emptyList(),
    @JsonProperty("exclude") val exclude: List<String> = emptyList(),
    @JsonProperty("custom") val isCustomRom: Boolean? = null
  )

  /**
   * Defines the audio settings a rule can override, if specified
   * @param oboe if true, use the Oboe ADM instead of the Java ADM
   * @param softwareAec if true, use software AEC instead of the platform provided AEC
   * @param softwareNs if true, use software NS instead of the platform provided NS
   * @param inLowLatency if true, use low latency setting for input
   * @param inVoiceComm if true, use voice communications setting for input
   */
  data class Settings(
    @JsonProperty("oboe") val oboe: Boolean? = null,
    @JsonProperty("softwareAec") val softwareAec: Boolean? = null,
    @JsonProperty("softwareNs") val softwareNs: Boolean? = null,
    @JsonProperty("inLowLatency") val inLowLatency: Boolean? = null,
    @JsonProperty("inVoiceComm") val inVoiceComm: Boolean? = null
  )

  @VisibleForTesting
  fun computeConfig(): AudioConfig {
    // Initialize the config with the default.
    val config = AudioConfig()

    val serialized = RemoteConfig.callingAudioDeviceConfig
    if (serialized.isBlank()) {
      Log.w(TAG, "No RemoteConfig.callingAudioDeviceConfig found! Using default.")
      return applyOverrides(config)
    }

    val rules: List<Rule> = try {
      JsonUtils.fromJsonArray(serialized, Rule::class.java)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to parse callingAudioDeviceConfig json! Using default. " + e.message)
      return applyOverrides(config)
    }

    for (rule in rules) {
      if (matchesDevice(rule.target)) {
        applySettings(rule.settings, config)

        if (rule.isFinal) {
          return applyOverrides(config)
        }
      }
    }

    return applyOverrides(config)
  }

  // Check if the target should apply to the device the code is running on.
  private fun matchesDevice(target: Target): Boolean {
    // Make sure the device is in the include list, if the list is present.
    if (target.include.isNotEmpty() &&
      !target.include.any { matchesModel(it) }
    ) {
      return false
    }

    // If the device is in the exclude list, don't match it.
    if (target.exclude.isNotEmpty() &&
      target.exclude.any { matchesModel(it) }
    ) {
      return false
    }

    // Check if the device needs to be a custom ROM or not, if the constraint is present.
    if (target.isCustomRom != null &&
      target.isCustomRom != isCustomRom()
    ) {
      return false
    }

    return true
  }

  private fun matchesModel(model: String): Boolean {
    return if (model.endsWith("*")) {
      Build.MODEL.startsWith(model.substring(0, model.length - 1))
    } else {
      Build.MODEL.equals(model, ignoreCase = true)
    }
  }

  private fun isCustomRom(): Boolean {
    return Build.PRODUCT.contains(CUSTOM_ROMS) ||
      Build.DISPLAY.contains(CUSTOM_ROMS) ||
      Build.HOST.contains(CUSTOM_ROMS)
  }

  private fun applySettings(settings: Settings, config: AudioConfig) {
    settings.oboe?.let { config.useOboe = it }
    settings.softwareAec?.let { config.useSoftwareAec = it }
    settings.softwareNs?.let { config.useSoftwareNs = it }
    settings.inLowLatency?.let { config.useInputLowLatency = it }
    settings.inVoiceComm?.let { config.useInputVoiceComm = it }
  }

  private fun applyOverrides(config: AudioConfig): AudioConfig {
    if (!isCustomRom() && Build.VERSION.SDK_INT < 29) {
      Log.w(TAG, "Device is less than API level 29, forcing software AEC/NS!")
      config.useSoftwareAec = true
      config.useSoftwareNs = true
    }

    if (!config.useSoftwareAec && !AcousticEchoCanceler.isAvailable()) {
      Log.w(TAG, "Device does not implement AcousticEchoCanceler, overriding config!")
      config.useSoftwareAec = true
    }

    if (!config.useSoftwareNs && !NoiseSuppressor.isAvailable()) {
      Log.w(TAG, "Device does not implement NoiseSuppressor, overriding config!")
      config.useSoftwareNs = true
    }

    val context = AppDependencies.application.applicationContext

    if (config.useInputLowLatency &&
      !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
    ) {
      Log.w(TAG, "Device does not implement FEATURE_AUDIO_LOW_LATENCY, overriding config!")
      config.useInputLowLatency = false
    }

    return config
  }
}
