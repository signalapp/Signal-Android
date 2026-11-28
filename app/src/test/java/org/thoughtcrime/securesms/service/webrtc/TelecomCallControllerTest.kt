/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.app.Application
import android.os.ParcelUuid
import androidx.core.telecom.CallEndpointCompat
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
class TelecomCallControllerTest {

  private val earpiece = endpoint("Earpiece", CallEndpointCompat.TYPE_EARPIECE)
  private val speaker = endpoint("Speaker", CallEndpointCompat.TYPE_SPEAKER)

  @Test
  fun `Given two named bluetooth endpoints, when I key them, then I expect each to be offered separately`() {
    val airpods = endpoint("AirPods Pro", CallEndpointCompat.TYPE_BLUETOOTH)
    val buds = endpoint("Galaxy Buds+", CallEndpointCompat.TYPE_BLUETOOTH)
    val endpoints = listOf(earpiece, speaker, airpods, buds)

    assertThat(TelecomCallController.endpointKey(airpods, endpoints)).isNotEqualTo(TelecomCallController.endpointKey(buds, endpoints))
  }

  @Test
  fun `Given unnamed bluetooth endpoints, when I key them, then I expect a single bluetooth option`() {
    val first = endpoint("Bluetooth Device", CallEndpointCompat.TYPE_BLUETOOTH)
    val second = endpoint("Bluetooth Device", CallEndpointCompat.TYPE_BLUETOOTH)
    val endpoints = listOf(earpiece, speaker, first, second)

    assertThat(TelecomCallController.endpointKey(first, endpoints)).isEqualTo(TelecomCallController.endpointKey(second, endpoints))
  }

  @Test
  fun `Given non bluetooth endpoints, when I key them, then I expect them keyed by device type`() {
    val endpoints = listOf(earpiece, speaker)

    assertThat(TelecomCallController.endpointKey(earpiece, endpoints)).isEqualTo(TelecomCallController.endpointKey(endpoint("Earpiece", CallEndpointCompat.TYPE_EARPIECE), endpoints))
    assertThat(TelecomCallController.endpointKey(earpiece, endpoints)).isNotEqualTo(TelecomCallController.endpointKey(speaker, endpoints))
  }

  @Test
  fun `Given one unnamed bluetooth endpoint, when I name it, then I expect the product name`() {
    val endpoints = listOf(earpiece, speaker, endpoint("Bluetooth Device", CallEndpointCompat.TYPE_BLUETOOTH))

    assertThat(TelecomCallController.unnamedBluetoothProductName(endpoints) { listOf("AirPods Pro", "AirPods Pro") }).isEqualTo("AirPods Pro")
  }

  @Test
  fun `Given two unnamed bluetooth endpoints, when I name them, then I expect no product name`() {
    val endpoints = listOf(earpiece, speaker, endpoint("Bluetooth Device", CallEndpointCompat.TYPE_BLUETOOTH), endpoint("Bluetooth Device", CallEndpointCompat.TYPE_BLUETOOTH))

    assertThat(TelecomCallController.unnamedBluetoothProductName(endpoints) { listOf("AirPods Pro") }).isNull()
  }

  @Test
  fun `Given one unnamed bluetooth endpoint and several product names, when I name it, then I expect no product name`() {
    val endpoints = listOf(earpiece, speaker, endpoint("Bluetooth Device", CallEndpointCompat.TYPE_BLUETOOTH))

    assertThat(TelecomCallController.unnamedBluetoothProductName(endpoints) { listOf("AirPods Pro", "Galaxy Buds+") }).isNull()
  }

  @Test
  fun `Given a bluetooth endpoint, when a default speaker request runs, then I expect it to be skipped`() {
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.SPEAKER_PHONE, isDefault = true)

    assertThat(TelecomCallController.shouldSkipDefaultEndpoint(command, setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER_PHONE, AudioDevice.BLUETOOTH))).isTrue()
  }

  @Test
  fun `Given a wired headset endpoint, when a default speaker request runs, then I expect it to be skipped`() {
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.SPEAKER_PHONE, isDefault = true)

    assertThat(TelecomCallController.shouldSkipDefaultEndpoint(command, setOf(AudioDevice.SPEAKER_PHONE, AudioDevice.WIRED_HEADSET))).isTrue()
  }

  @Test
  fun `Given no headset endpoint, when a default speaker request runs, then I expect it to be applied`() {
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.SPEAKER_PHONE, isDefault = true)

    assertThat(TelecomCallController.shouldSkipDefaultEndpoint(command, setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER_PHONE))).isFalse()
  }

  @Test
  fun `Given no endpoints yet, when a default speaker request runs, then I expect it not to be skipped`() {
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.SPEAKER_PHONE, isDefault = true)

    assertThat(TelecomCallController.shouldSkipDefaultEndpoint(command, emptySet())).isFalse()
  }

  @Test
  fun `Given a bluetooth endpoint, when the user picks the speaker, then I expect it to be applied`() {
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.SPEAKER_PHONE)

    assertThat(TelecomCallController.shouldSkipDefaultEndpoint(command, setOf(AudioDevice.SPEAKER_PHONE, AudioDevice.BLUETOOTH))).isFalse()
  }

  @Test
  fun `Given a bluetooth endpoint, when a default bluetooth request runs, then I expect it to be applied`() {
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.BLUETOOTH, isDefault = true)

    assertThat(TelecomCallController.shouldSkipDefaultEndpoint(command, setOf(AudioDevice.SPEAKER_PHONE, AudioDevice.BLUETOOTH))).isFalse()
  }

  @Test
  fun `Given no endpoints yet, when I resolve an endpoint, then I expect it to be held`() {
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.SPEAKER_PHONE)

    assertThat(TelecomCallController.resolveEndpoint(command, emptyList())).isEqualTo(TelecomCallController.EndpointResolution.Hold)
  }

  @Test
  fun `Given a bluetooth endpoint, when I resolve a default speaker request, then I expect it to be skipped`() {
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.SPEAKER_PHONE, isDefault = true)
    val endpoints = listOf(earpiece, speaker, endpoint("AirPods Pro", CallEndpointCompat.TYPE_BLUETOOTH))

    assertThat(TelecomCallController.resolveEndpoint(command, endpoints)).isEqualTo(TelecomCallController.EndpointResolution.Skip)
  }

  @Test
  fun `Given a matching identifier, when I resolve an endpoint, then I expect that endpoint`() {
    val airpods = endpoint("AirPods Pro", CallEndpointCompat.TYPE_BLUETOOTH)
    val buds = endpoint("Galaxy Buds+", CallEndpointCompat.TYPE_BLUETOOTH)
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.BLUETOOTH, identifier = buds.identifier)

    assertThat(TelecomCallController.resolveEndpoint(command, listOf(earpiece, airpods, buds))).isEqualTo(TelecomCallController.EndpointResolution.Request(buds))
  }

  @Test
  fun `Given no matching identifier, when I resolve an endpoint, then I expect the first endpoint of that type`() {
    val airpods = endpoint("AirPods Pro", CallEndpointCompat.TYPE_BLUETOOTH)
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.BLUETOOTH, identifier = ParcelUuid(UUID.randomUUID()))

    assertThat(TelecomCallController.resolveEndpoint(command, listOf(earpiece, airpods))).isEqualTo(TelecomCallController.EndpointResolution.Request(airpods))
  }

  @Test
  fun `Given no endpoint of the requested type, when I resolve an endpoint, then I expect not found`() {
    val command = TelecomCommand.ChangeEndpoint(AudioDevice.WIRED_HEADSET)

    assertThat(TelecomCallController.resolveEndpoint(command, listOf(earpiece, speaker))).isEqualTo(TelecomCallController.EndpointResolution.NotFound)
  }

  private fun endpoint(name: String, type: Int): CallEndpointCompat {
    return CallEndpointCompat(name, type, ParcelUuid(UUID.randomUUID()))
  }
}
