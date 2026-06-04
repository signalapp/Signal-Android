/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.io.File

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AvatarPickerStorageTest_cleanOrphans {

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun givenFileOnDiskAndInDatabase_whenCleanOrphans_thenNothingIsDeleted() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val filename = createDiskFile("avatar_matched.jpg").name
    val savedAvatar = insertPhotoAvatar(filename)

    AvatarPickerStorage.cleanOrphans(context)

    assertThat(diskFileExists(filename)).isTrue()
    assertThat(dbRowExists(savedAvatar)).isTrue()
  }

  @Test
  fun givenFileOnDiskOnly_whenCleanOrphans_thenFileIsDeleted() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val filename = createDiskFile("avatar_diskonly.jpg").name

    AvatarPickerStorage.cleanOrphans(context)

    assertThat(diskFileExists(filename)).isFalse()
  }

  @Test
  fun givenInDatabaseOnly_whenCleanOrphans_thenDatabaseRowIsDeleted() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val savedAvatar = insertPhotoAvatar("avatar_dbonly.jpg")

    AvatarPickerStorage.cleanOrphans(context)

    assertThat(dbRowExists(savedAvatar)).isFalse()
  }

  @Test
  fun givenOrphanFileAndOrphanDbRow_whenCleanOrphans_thenBothDeleted() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val diskOnlyFilename = createDiskFile("avatar_diskorphan.jpg").name
    val dbOnlyAvatar = insertPhotoAvatar("avatar_dborphan.jpg")

    AvatarPickerStorage.cleanOrphans(context)

    assertThat(diskFileExists(diskOnlyFilename)).isFalse()
    assertThat(dbRowExists(dbOnlyAvatar)).isFalse()
  }

  @Test
  fun givenTextAvatarAndOrphanFile_whenCleanOrphans_thenOrphanFileDeletedAndTextAvatarUntouched() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val textAvatar = SignalDatabase.avatarPicker.saveAvatarForSelf(
      Avatar.Text("AB", Avatars.colors[0], Avatar.DatabaseId.NotSet)
    )
    val diskOnlyFilename = createDiskFile("avatar_orphan_with_text.jpg").name

    AvatarPickerStorage.cleanOrphans(context)

    assertThat(diskFileExists(diskOnlyFilename)).isFalse()
    assertThat(dbRowExists(textAvatar)).isTrue()
  }

  @Test
  fun givenNoFilesAndNoDatabase_whenCleanOrphans_thenNothingExplodes() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    AvatarPickerStorage.cleanOrphans(context)
  }

  // region helpers

  private fun createDiskFile(name: String): File {
    val dir = ApplicationProvider.getApplicationContext<Application>().getDir("avatar_picker", android.content.Context.MODE_PRIVATE)
    return File(dir, name).also { it.createNewFile() }
  }

  private fun insertPhotoAvatar(filename: String): Avatar {
    val uri = PartAuthority.getAvatarPickerUri(filename)
    return SignalDatabase.avatarPicker.saveAvatarForSelf(
      Avatar.Photo(uri, 0L, Avatar.DatabaseId.NotSet)
    )
  }

  private fun diskFileExists(filename: String): Boolean {
    val dir = ApplicationProvider.getApplicationContext<Application>().getDir("avatar_picker", android.content.Context.MODE_PRIVATE)
    return File(dir, filename).exists()
  }

  private fun dbRowExists(avatar: Avatar): Boolean {
    val id = (avatar.databaseId as? Avatar.DatabaseId.Saved)?.id ?: return false
    return SignalDatabase.avatarPicker.getAllAvatars().any {
      (it.databaseId as? Avatar.DatabaseId.Saved)?.id == id
    }
  }

  // endregion
}
