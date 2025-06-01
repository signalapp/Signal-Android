/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.webrtc.audio

import android.app.Application
import android.content.pm.PackageManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.signal.core.util.logging.Log
import org.signal.ringrtc.AudioConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AudioDeviceConfigTest {
  companion object {
    private val REFERENCE_CONFIG = """
      [
        {
          "target": {
            "include": [
              "FP4"
            ]
          },
          "settings": {
            "oboe": true,
            "inVoiceComm": false
          },
          "final": true
        },
        {
          "target": {
            "custom": false,
            "include": [
              "Redmi Note 5",
              "FP2",
              "M1901F7*",
              "ASUS_I006D",
              "motorola one power",
              "FP3",
              "S22 FLIP",
              "Mi Note 10",
              "SM-S215DL",
              "T810S",
              "CPH2067"
            ]
          },
          "settings": {
            "softwareAec": true,
            "softwareNs": true
          }
        },
        {
          "target": {
            "custom": true,
            "include": [
              "ONEPLUS A5010",
              "POCO F1",
              "SM-A320FL",
              "ONEPLUS A3003",
              "ONEPLUS A5000",
              "SM-G900F",
              "SM-G800F"
            ]
          },
          "settings": {
            "softwareAec": true,
            "softwareNs": true
          }
        },
        {
          "target": {
            "custom": true,
            "exclude": [
              "Pixel 3",
              "SM-G973F"
            ]
          },
          "settings": {
            "oboe": true
          }
        }
      ]
    """.trimMargin()

    private val REFERENCE_CONFIG_COMPRESSED = """
      [{"target":{"include":["FP4"]},"settings":{"oboe":true,"inVoiceComm":false},"final":true},{"target":{"custom":false,"include":["Redmi Note 5","FP2","M1901F7*","ASUS_I006D","motorola one power","FP3","S22 FLIP","Mi Note 10","SM-S215DL","T810S","CPH2067"]},"settings":{"softwareAec":true,"softwareNs":true}},{"target":{"custom":true,"include":["ONEPLUS A5010","POCO F1","SM-A320FL","ONEPLUS A3003","ONEPLUS A5000","SM-G900F","SM-G800F"]},"settings":{"softwareAec":true,"softwareNs":true}},{"target":{"custom":true,"exclude":["Pixel 3","SM-G973F"]},"settings":{"oboe":true}}]
    """.trimMargin()

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Before
  fun setup() {
    mockkStatic(AppDependencies::class)
    mockkStatic(PackageManager::class)
    mockkObject(RemoteConfig)
    mockkStatic(AcousticEchoCanceler::class)
    mockkStatic(NoiseSuppressor::class)
  }

  @After
  fun tearDown() {
    mockkStatic(AppDependencies::class)
    unmockkStatic(PackageManager::class)
    unmockkObject(RemoteConfig)
    unmockkStatic(AcousticEchoCanceler::class)
    unmockkStatic(NoiseSuppressor::class)
  }

  private fun mockEnvironment(
    sdk: Int = 34,
    aec: Boolean = true,
    ns: Boolean = true,
    lowLatency: Boolean = true
  ) {
    ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)

    every { AcousticEchoCanceler.isAvailable() } returns aec
    every { NoiseSuppressor.isAvailable() } returns ns
    every {
      AppDependencies.application.applicationContext.packageManager
        .hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
    } returns lowLatency
  }

  private fun createAudioConfig(
    useOboe: Boolean = false,
    useSoftwareAec: Boolean = false,
    useSoftwareNs: Boolean = false,
    useInputLowLatency: Boolean = true,
    useInputVoiceComm: Boolean = true
  ): AudioConfig {
    return AudioConfig().apply {
      this.useOboe = useOboe
      this.useSoftwareAec = useSoftwareAec
      this.useSoftwareNs = useSoftwareNs
      this.useInputLowLatency = useInputLowLatency
      this.useInputVoiceComm = useInputVoiceComm
    }
  }

  @Test
  fun `empty config`() {
    mockEnvironment()

    every { RemoteConfig.callingAudioDeviceConfig } returns ""
    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())
  }

  @Test
  fun `invalid config`() {
    mockEnvironment()

    every { RemoteConfig.callingAudioDeviceConfig } returns "bad"
    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())
  }

  @Test
  fun `reference`() {
    mockEnvironment()

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())
  }

  @Test
  fun `targeted device`() {
    mockEnvironment()

    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "FP4")

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useOboe = true, useInputVoiceComm = false))
  }

  @Test
  fun `hardware aec block list`() {
    mockEnvironment()

    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Redmi Note 5")

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useSoftwareAec = true, useSoftwareNs = true))
  }

  @Test
  fun `hardware aec block list wildcard`() {
    mockEnvironment()

    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "M1901F7A")

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useSoftwareAec = true, useSoftwareNs = true))
  }

  @Test
  fun `custom hardware aec block list`() {
    mockEnvironment()

    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "ONEPLUS A5000")
    ReflectionHelpers.setStaticField(Build::class.java, "PRODUCT", "lorem,lineageos=1.0.0,ipsum")

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useOboe = true, useSoftwareAec = true, useSoftwareNs = true))
  }

  @Test
  fun `custom device`() {
    mockEnvironment()

    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Some Device")
    ReflectionHelpers.setStaticField(Build::class.java, "PRODUCT", "calyxos2.0")

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useOboe = true))
  }

  @Test
  fun `custom excluded device`() {
    mockEnvironment()

    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 3")
    ReflectionHelpers.setStaticField(Build::class.java, "DISPLAY", "special calyxos,2.0")

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())
  }

  @Test
  fun `overrides`() {
    mockEnvironment(aec = false, ns = false, lowLatency = false)

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useSoftwareAec = true, useSoftwareNs = true, useInputLowLatency = false))
  }

  @Test
  fun `flip all`() {
    mockEnvironment()

    every { RemoteConfig.callingAudioDeviceConfig } returns
      """
      [
        {
          "settings": {
            "oboe": true,
            "softwareAec": true,
            "softwareNs": true,
            "inLowLatency": false,
            "inVoiceComm": false
          }
        }
      ]
      """.trimMargin()

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useOboe = true, useSoftwareAec = true, useSoftwareNs = true, useInputLowLatency = false, useInputVoiceComm = false))
  }

  @Test
  fun `bail early`() {
    mockEnvironment()

    every { RemoteConfig.callingAudioDeviceConfig } returns
      """
      [
        {
          "final": true
        },
        {
          "settings": {
            "oboe": true,
            "softwareAec": true,
            "softwareNs": true,
            "inLowLatency": false,
            "inVoiceComm": false
          }
        }
      ]
      """.trimMargin()

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())
  }

  @Test
  fun `custom exclude wildcard`() {
    mockEnvironment()

    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "ABCDEFG")
    ReflectionHelpers.setStaticField(Build::class.java, "DISPLAY", "lineage")

    every { RemoteConfig.callingAudioDeviceConfig } returns
      """
      [
        {
          "target": {
            "custom": true,
            "exclude": ["ABC*"]
          },
          "settings": {
            "oboe": true
          }
        }
      ]
      """.trimMargin()

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())
  }

  @Test
  fun `ignore unknown`() {
    mockEnvironment()

    every { RemoteConfig.callingAudioDeviceConfig } returns
      """
      [
        {
          "unknownRule": {
            "unknown": true
          },
          "target": {
            "unknown": true
          },
          "settings": {
            "unknown": true
          }
        }
      ]
      """.trimMargin()

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())
  }

  @Test
  fun `invalid json`() {
    mockEnvironment()

    every { RemoteConfig.callingAudioDeviceConfig } returns
      """
      [
        {
          "final": {
            "unknown": true
          }
        }
      ]
      """.trimMargin()

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())
  }

  @Test
  fun `check compressed`() {
    mockEnvironment()

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG_COMPRESSED

    // Non-custom device.
    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())

    // Non-custom device requiring software aec.
    // Note: For legacy reasons, we assume that if software AEC is used, software NS should also be used.
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "T810S")
    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useSoftwareAec = true, useSoftwareNs = true))

    // Custom device.
    ReflectionHelpers.setStaticField(Build::class.java, "PRODUCT", "lorem,lineageos=1.0.0,ipsum")
    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useOboe = true))

    // Custom device requiring software aec.
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "ONEPLUS A5000")
    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useOboe = true, useSoftwareAec = true, useSoftwareNs = true))

    // Custom device using Java ADM.
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "SM-G973F")
    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig())

    // Device with specific settings, in this case custom or not.
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "FP4")
    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useOboe = true, useInputVoiceComm = false))
  }

  @Test
  fun `api override`() {
    mockEnvironment(sdk = 28)

    every { RemoteConfig.callingAudioDeviceConfig } returns REFERENCE_CONFIG_COMPRESSED

    assertThat(AudioDeviceConfig.computeConfig()).isEqualTo(createAudioConfig(useSoftwareAec = true, useSoftwareNs = true))
  }
}
