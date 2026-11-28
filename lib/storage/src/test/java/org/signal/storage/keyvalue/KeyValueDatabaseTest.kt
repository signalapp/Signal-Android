/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.storage.database.DatabaseSecret

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class KeyValueDatabaseTest {

  private lateinit var openHelper: SupportSQLiteOpenHelper
  private lateinit var subject: KeyValueDatabase

  @Before
  fun setUp() {
    val application = ApplicationProvider.getApplicationContext<Application>()

    openHelper = FrameworkSQLiteOpenHelperFactory().create(
      SupportSQLiteOpenHelper.Configuration.builder(application)
        .name(null)
        .callback(object : SupportSQLiteOpenHelper.Callback(1) {
          override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(KeyValueDatabase.CREATE_TABLE)
          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })
        .build()
    )

    val backing = openHelper.writableDatabase
    subject = object : KeyValueDatabase(application, DatabaseSecret.fromBytes(ByteArray(32)), "test") {
      override val database: SupportSQLiteDatabase = backing
    }
  }

  @After
  fun tearDown() {
    openHelper.close()
  }

  @Test
  fun `getDataSet - empty database`() {
    assertThat(subject.getDataSet().values.isEmpty()).isTrue()
  }

  @Test
  fun `writeDataSet - round trips every type`() {
    val input = KeyValueDataSet().apply {
      putBlob("blob", byteArrayOf(1, 2, 3))
      putBoolean("boolean", true)
      putFloat("float", 1.5f)
      putInteger("integer", 42)
      putLong("long", Long.MAX_VALUE)
      putString("string", "spiderman")
    }

    subject.writeDataSet(input, emptyList())
    val output = subject.getDataSet()

    assertThat(output.getBlob("blob", null)).isNotNull().isEqualTo(byteArrayOf(1, 2, 3))
    assertThat(output.getBoolean("boolean", false)).isTrue()
    assertThat(output.getFloat("float", 0f)).isEqualTo(1.5f)
    assertThat(output.getInteger("integer", 0)).isEqualTo(42)
    assertThat(output.getLong("long", 0)).isEqualTo(Long.MAX_VALUE)
    assertThat(output.getString("string", null)).isEqualTo("spiderman")

    assertThat(output.getType("blob")).isEqualTo(ByteArray::class.java)
    assertThat(output.getType("boolean")).isEqualTo(Boolean::class.javaObjectType)
    assertThat(output.getType("float")).isEqualTo(Float::class.javaObjectType)
    assertThat(output.getType("integer")).isEqualTo(Int::class.javaObjectType)
    assertThat(output.getType("long")).isEqualTo(Long::class.javaObjectType)
    assertThat(output.getType("string")).isEqualTo(String::class.java)
  }

  @Test
  fun `writeDataSet - round trips false booleans`() {
    subject.writeDataSet(KeyValueDataSet().apply { putBoolean("key", false) }, emptyList())

    assertThat(subject.getDataSet().getBoolean("key", true)).isFalse()
  }

  @Test
  fun `writeDataSet - round trips null values`() {
    val input = KeyValueDataSet().apply {
      putBlob("blob", null)
      putString("string", null)
    }

    subject.writeDataSet(input, emptyList())
    val output = subject.getDataSet()

    assertThat(output.containsKey("blob")).isTrue()
    assertThat(output.getBlob("blob", byteArrayOf(1))).isNull()
    assertThat(output.containsKey("string")).isTrue()
    assertThat(output.getString("string", "default")).isNull()
  }

  @Test
  fun `writeDataSet - replaces existing values, including their type`() {
    subject.writeDataSet(KeyValueDataSet().apply { putInteger("key", 1) }, emptyList())
    subject.writeDataSet(KeyValueDataSet().apply { putString("key", "venom") }, emptyList())

    val output = subject.getDataSet()
    assertThat(output.getString("key", null)).isEqualTo("venom")
    assertThat(output.getType("key")).isEqualTo(String::class.java)
    assertThat(output.values.size).isEqualTo(1)
  }

  @Test
  fun `writeDataSet - removes keys`() {
    subject.writeDataSet(KeyValueDataSet().apply { putInteger("a", 1); putInteger("b", 2) }, emptyList())
    subject.writeDataSet(KeyValueDataSet().apply { putInteger("c", 3) }, listOf("a"))

    val output = subject.getDataSet()
    assertThat(output.containsKey("a")).isFalse()
    assertThat(output.getInteger("b", 0)).isEqualTo(2)
    assertThat(output.getInteger("c", 0)).isEqualTo(3)
  }

  @Test
  fun `writeDataSet - removing a missing key is a no-op`() {
    subject.writeDataSet(KeyValueDataSet().apply { putInteger("a", 1) }, listOf("missing"))

    assertThat(subject.getDataSet().getInteger("a", 0)).isEqualTo(1)
  }

  @Test
  fun `clear - removes everything`() {
    subject.writeDataSet(KeyValueDataSet().apply { putInteger("a", 1); putString("b", "spiderman") }, emptyList())

    subject.clear()

    assertThat(subject.getDataSet().values.isEmpty()).isTrue()
  }

  @Test
  fun `KeyValueStore - writes persist and are visible to a fresh store`() {
    val store = KeyValueStore(subject) { it.run() }
    store.beginWrite()
      .putLong("long", 7L)
      .putString("string", "spiderman")
      .commit()

    val freshStore = KeyValueStore(subject) { it.run() }
    assertThat(freshStore.getLong("long", 0)).isEqualTo(7L)
    assertThat(freshStore.getString("string", null)).isEqualTo("spiderman")
  }
}
