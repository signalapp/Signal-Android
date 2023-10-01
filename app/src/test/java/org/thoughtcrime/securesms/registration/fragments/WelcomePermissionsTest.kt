/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.fragments

import android.Manifest
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Ignore("Causing OOM errors.")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WelcomePermissionsTest {
  @Test
  @Config(sdk = [33])
  fun givenApi33_whenIGetWelcomePermissions_thenIExpectPostNotifications() {
    val result = WelcomePermissions.getWelcomePermissions(true)

    assertTrue(Manifest.permission.POST_NOTIFICATIONS in result)
  }

  @Test
  @Config(sdk = [23, 26, 29])
  fun givenApiUnder33_whenIGetWelcomePermissions_thenIExpectNoPostNotifications() {
    val result = WelcomePermissions.getWelcomePermissions(true)

    assertFalse(Manifest.permission.POST_NOTIFICATIONS in result)
  }

  @Test
  @Config(sdk = [23, 26, 29, 33])
  fun givenAnyApi_whenIGetWelcomePermissions_thenIExpectContacts() {
    val result = WelcomePermissions.getWelcomePermissions(true)

    assertTrue(Manifest.permission.WRITE_CONTACTS in result)
    assertTrue(Manifest.permission.READ_CONTACTS in result)
  }

  @Test
  @Config(sdk = [23, 26, 29, 33])
  fun givenAnyApi_whenIGetWelcomePermissions_thenIExpectReadPhoneState() {
    val result = WelcomePermissions.getWelcomePermissions(true)

    assertTrue(Manifest.permission.READ_PHONE_STATE in result)
  }

  @Test
  @Config(sdk = [26, 29, 33])
  fun givenApi26Plus_whenIGetWelcomePermissions_thenIExpectReadPhoneNumbers() {
    val result = WelcomePermissions.getWelcomePermissions(true)

    assertTrue(Manifest.permission.READ_PHONE_NUMBERS in result)
  }

  @Test
  @Config(sdk = [23])
  fun givenApiUnder26_whenIGetWelcomePermissions_thenIExpectNoReadPhoneNumbers() {
    val result = WelcomePermissions.getWelcomePermissions(true)

    assertFalse(Manifest.permission.READ_PHONE_NUMBERS in result)
  }

  @Test
  @Config(sdk = [23, 26])
  fun givenApiUnder29_whenIGetWelcomePermissions_thenIExpectPhoneStorage() {
    val result = WelcomePermissions.getWelcomePermissions(true)

    assertTrue(Manifest.permission.WRITE_EXTERNAL_STORAGE in result)
    assertTrue(Manifest.permission.READ_EXTERNAL_STORAGE in result)
  }

  @Test
  @Config(sdk = [29, 33])
  fun givenApi29Plus_whenIGetWelcomePermissionsAndSelectionNotRequired_thenIExpectPhoneStorage() {
    val result = WelcomePermissions.getWelcomePermissions(false)

    assertTrue(Manifest.permission.WRITE_EXTERNAL_STORAGE in result)
    assertTrue(Manifest.permission.READ_EXTERNAL_STORAGE in result)
  }

  @Test
  @Config(sdk = [29, 33])
  fun givenApi29Plus_whenIGetWelcomePermissionsAndSelectionRequired_thenIExpectNoPhoneStorage() {
    val result = WelcomePermissions.getWelcomePermissions(true)

    assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in result)
    assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in result)
  }
}
