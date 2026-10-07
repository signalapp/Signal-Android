/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTables
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StorageUsageRepositoryTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `buckets main database, stickers, and other files`() {
    val databaseDirectory = context.getDatabasePath(SignalDatabase.DATABASE_NAME).parentFile!!.apply { mkdirs() }
    writeFile(File(databaseDirectory, SignalDatabase.DATABASE_NAME), 100)
    writeFile(File(databaseDirectory, "${SignalDatabase.DATABASE_NAME}-wal"), 20)
    writeFile(File(databaseDirectory, "signal-key-value.db"), 30)
    writeFile(File(databaseDirectory, "signal-jobmanager.db"), 10)

    val stickerDirectory = context.getDir(StickerTables.DIRECTORY, Context.MODE_PRIVATE)
    writeFile(File(stickerDirectory, "sticker-1"), 40)
    writeFile(File(File(stickerDirectory, "nested").apply { mkdirs() }, "sticker-2"), 5)

    writeFile(File(context.cacheDir, "glide-cache"), 70)

    val dataDirectorySize = context.dataDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    val usage = StorageUsageRepository(context).getStorageUsage()

    assertThat(usage.messages).isEqualTo(120)
    assertThat(usage.stickers).isEqualTo(45)
    assertThat(usage.other).isEqualTo(dataDirectorySize - 120 - 45)
    assertThat(usage.total).isEqualTo(dataDirectorySize)
  }

  private fun writeFile(file: File, size: Int) {
    file.writeBytes(ByteArray(size))
  }
}
