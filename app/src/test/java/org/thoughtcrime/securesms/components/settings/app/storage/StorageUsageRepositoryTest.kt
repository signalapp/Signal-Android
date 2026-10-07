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
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
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

  private val writtenFiles = mutableListOf<File>()

  @After
  fun tearDown() {
    writtenFiles.forEach { it.delete() }
    context.getDir(StickerTables.DIRECTORY, Context.MODE_PRIVATE).deleteRecursively()
  }

  @Test
  fun `buckets main database, stickers, and other files`() = runTest {
    val databaseDirectory = context.getDatabasePath(SignalDatabase.DATABASE_NAME).parentFile!!.apply { mkdirs() }
    writeFile(File(databaseDirectory, SignalDatabase.DATABASE_NAME), 100)
    writeFile(File(databaseDirectory, "${SignalDatabase.DATABASE_NAME}-wal"), 20)
    writeFile(File(databaseDirectory, "other-1.db"), 30)
    writeFile(File(databaseDirectory, "other-2.db"), 10)

    val stickerDirectory = context.getDir(StickerTables.DIRECTORY, Context.MODE_PRIVATE)
    writeFile(File(stickerDirectory, "sticker-1"), 40)
    writeFile(File(File(stickerDirectory, "nested").apply { mkdirs() }, "sticker-2"), 5)

    writeFile(File(context.cacheDir, "glide-cache"), 70)

    val dataDirectorySize = context.dataDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    val repository = StorageUsageRepository(context, this)
    repository.refresh()
    advanceUntilIdle()

    val usage = requireNotNull(repository.usage.value)

    assertThat(usage.messages).isEqualTo(120)
    assertThat(usage.stickers).isEqualTo(45)
    assertThat(usage.other).isEqualTo(dataDirectorySize - 120 - 45)
    assertThat(usage.total).isEqualTo(dataDirectorySize)
  }

  @Test
  fun `refresh publishes usage and keeps the previous value until the next computation completes`() = runTest {
    val repository = StorageUsageRepository(context, this)

    assertThat(repository.usage.value).isNull()

    repository.refresh()
    advanceUntilIdle()

    val first = repository.usage.value
    assertThat(first).isNotNull()

    writeFile(File(context.cacheDir, "new-file"), 10)
    repository.refresh()

    assertThat(repository.usage.value).isEqualTo(first)

    advanceUntilIdle()

    assertThat(repository.usage.value!!.total).isEqualTo(first!!.total + 10)
  }

  private fun writeFile(file: File, size: Int) {
    file.writeBytes(ByteArray(size))
    writtenFiles += file
  }
}
