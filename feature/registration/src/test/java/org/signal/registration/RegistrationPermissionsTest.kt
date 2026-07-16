/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RegistrationPermissionsTest {

  @Test
  @Config(sdk = [34])
  fun `API 34 requests notifications, contacts, and phone but not legacy storage`() {
    val permissions = RegistrationPermissions.getRequiredPermissions(isModernBackupDirectorySelectionRequired = true)

    assertThat(permissions.contains(Manifest.permission.POST_NOTIFICATIONS)).isTrue()
    assertThat(permissions.contains(Manifest.permission.READ_CONTACTS)).isTrue()
    assertThat(permissions.contains(Manifest.permission.WRITE_CONTACTS)).isTrue()
    assertThat(permissions.contains(Manifest.permission.READ_PHONE_STATE)).isTrue()
    assertThat(permissions.contains(Manifest.permission.READ_PHONE_NUMBERS)).isTrue()
    assertThat(permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE)).isFalse()
    assertThat(permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)).isFalse()
  }

  @Test
  @Config(sdk = [34])
  fun `API 34 keeps legacy storage when a backup location does not require user selection`() {
    val permissions = RegistrationPermissions.getRequiredPermissions(isModernBackupDirectorySelectionRequired = false)

    assertThat(permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE)).isTrue()
    assertThat(permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)).isTrue()
  }

  @Test
  @Config(sdk = [28])
  fun `API 28 requests legacy storage but not notifications`() {
    val permissions = RegistrationPermissions.getRequiredPermissions(isModernBackupDirectorySelectionRequired = true)

    assertThat(permissions.contains(Manifest.permission.POST_NOTIFICATIONS)).isFalse()
    assertThat(permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE)).isTrue()
    assertThat(permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)).isTrue()
  }

  @Test
  @Config(sdk = [25])
  fun `API 25 does not request read phone numbers`() {
    val permissions = RegistrationPermissions.getRequiredPermissions(isModernBackupDirectorySelectionRequired = true)

    assertThat(permissions.contains(Manifest.permission.READ_PHONE_STATE)).isTrue()
    assertThat(permissions.contains(Manifest.permission.READ_PHONE_NUMBERS)).isFalse()
  }

  @Test
  @Config(sdk = [34])
  fun `hasAllRequiredPermissions is false when nothing is granted`() {
    val context = ApplicationProvider.getApplicationContext<Application>()

    assertThat(RegistrationPermissions.hasAllRequiredPermissions(context)).isFalse()
  }

  @Test
  @Config(sdk = [34])
  fun `hasAllRequiredPermissions is true once every required permission is granted`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(context).grantPermissions(*RegistrationPermissions.getRequiredPermissions(context).toTypedArray())

    assertThat(RegistrationPermissions.hasAllRequiredPermissions(context)).isTrue()
  }
}
