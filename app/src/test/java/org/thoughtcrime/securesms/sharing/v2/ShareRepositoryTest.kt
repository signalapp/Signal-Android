/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.sharing.v2

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Verifies that [ShareRepository] resolves external share data gracefully when the sending app
 * has not granted access to a content URI (e.g. AOSP Contacts sharing a vCard without
 * `FLAG_GRANT_READ_URI_PERMISSION`), rather than throwing or failing silently without user feedback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ShareRepositoryTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  private val vCardUri: Uri = Uri.parse("content://com.android.contacts/contacts/as_vcard/42")

  @Test
  fun givenSecurityExceptionFromResolver_whenResolveSingleShare_thenReturnAccessDeniedFailure() {
    val resolver = mockk<ContentResolver>(relaxed = true)
    every { resolver.query(any(), any(), any(), any(), any()) } throws SecurityException("No URI grant")
    every { resolver.openInputStream(any()) } throws SecurityException("No URI grant")
    every { resolver.getType(any()) } throws SecurityException("No URI grant")

    val repository = repositoryWith(resolver)
    val result = repository.resolve(unresolvedSingleShare()).blockingGet()

    assertThat(result).isEqualTo(ResolvedShareData.Failure(ShareError.ACCESS_DENIED))
  }

  @Test
  fun givenIOExceptionFromResolver_whenResolveSingleShare_thenReturnUnknownFailure() {
    val resolver = mockk<ContentResolver>(relaxed = true)
    every { resolver.query(any(), any(), any(), any(), any()) } throws SecurityException("No URI grant")
    every { resolver.openInputStream(any()) } throws IOException("Failed to open")
    every { resolver.getType(any()) } throws SecurityException("No URI grant")

    val repository = repositoryWith(resolver)
    val result = repository.resolve(unresolvedSingleShare()).blockingGet()

    assertThat(result).isEqualTo(ResolvedShareData.Failure(ShareError.UNKNOWN))
  }

  @Test
  fun givenReadableVCardUri_whenResolveSingleShare_thenResolveExternalUri() {
    val resolver = mockk<ContentResolver>(relaxed = true)
    val cursor = mockk<Cursor>(relaxed = true)
    every { cursor.moveToFirst() } returns true
    every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 0
    every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 1
    every { cursor.getColumnIndexOrThrow(OpenableColumns.SIZE) } returns 0
    every { cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME) } returns 1
    every { cursor.getLong(0) } returns 2048L
    every { cursor.getString(1) } returns "contact.vcf"
    every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
    every { resolver.getType(any()) } returns "text/x-vcard"
    every { resolver.openInputStream(any()) } returns ByteArrayInputStream(ByteArray(64))
    every { AppDependencies.blobs.forData(any(), any()) } returns mockk(relaxed = true)

    val repository = repositoryWith(resolver)
    val result = repository.resolve(unresolvedSingleShare()).blockingGet()

    assertThat(result).isInstanceOf(ResolvedShareData.ExternalUri::class)
    val externalUri = result as ResolvedShareData.ExternalUri
    assertThat(externalUri.mimeType).isEqualTo("text/x-vcard")
    assertThat(externalUri.text).isNull()
  }

  private fun repositoryWith(resolver: ContentResolver): ShareRepository {
    val context = mockk<Context>(relaxed = true)
    every { context.applicationContext } returns context
    every { context.packageName } returns "org.thoughtcrime.securesms"
    every { context.contentResolver } returns resolver
    return ShareRepository(context)
  }

  private fun unresolvedSingleShare(): UnresolvedShareData.ExternalSingleShare {
    return UnresolvedShareData.ExternalSingleShare(vCardUri, "text/x-vcard", null, false)
  }
}
