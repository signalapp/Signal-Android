package org.thoughtcrime.securesms.database

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.single
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.testing.JdbcSqliteDatabase

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MegaphoneDatabaseTest {

  private lateinit var jdbcDatabase: JdbcSqliteDatabase
  private lateinit var db: MegaphoneDatabase

  @Before
  fun setUp() {
    jdbcDatabase = JdbcSqliteDatabase.createInMemory()
    jdbcDatabase.execSQL(MegaphoneDatabase.CREATE_TABLE)

    val backing: SupportSQLiteDatabase = jdbcDatabase
    db = object : MegaphoneDatabase(
      ApplicationProvider.getApplicationContext<Application>(),
      DatabaseSecret(ByteArray(32))
    ) {
      override val database: SupportSQLiteDatabase = backing
    }
  }

  @After
  fun tearDown() {
    jdbcDatabase.close()
  }

  @Test
  fun `insert adds events`() {
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL, Megaphones.Event.NOTIFICATIONS))

    assertThat(db.getAllAndDeleteMissing().map { it.event })
      .containsExactlyInAnyOrder(Megaphones.Event.PINS_FOR_ALL, Megaphones.Event.NOTIFICATIONS)
  }

  @Test
  fun `insert ignores duplicate events`() {
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL))
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL))

    assertThat(db.getAllAndDeleteMissing()).hasSize(1)
  }

  @Test
  fun `markFirstVisible sets firstVisible on the matching event only`() {
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL, Megaphones.Event.NOTIFICATIONS))

    db.markFirstVisible(Megaphones.Event.PINS_FOR_ALL, 12345L)

    val records = db.getAllAndDeleteMissing().associateBy { it.event }
    assertThat(records[Megaphones.Event.PINS_FOR_ALL]!!.firstVisible).isEqualTo(12345L)
    assertThat(records[Megaphones.Event.NOTIFICATIONS]!!.firstVisible).isEqualTo(0L)
  }

  @Test
  fun `markLastVisible sets lastVisible`() {
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL))

    db.markLastVisible(Megaphones.Event.PINS_FOR_ALL, 67890L)

    assertThat(db.getAllAndDeleteMissing()).single().transform { it.lastVisible }.isEqualTo(67890L)
  }

  @Test
  fun `markInteractedWith updates interaction fields and clears lastVisible`() {
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL))
    db.markLastVisible(Megaphones.Event.PINS_FOR_ALL, 67890L)

    db.markInteractedWith(Megaphones.Event.PINS_FOR_ALL, interactionCount = 3, lastInteractionTimestamp = 99999L)

    val record = db.getAllAndDeleteMissing().single()
    assertThat(record.interactionCount).isEqualTo(3)
    assertThat(record.lastInteractionTime).isEqualTo(99999L)
    assertThat(record.lastVisible).isEqualTo(0L)
  }

  @Test
  fun `markInteractedWith does not affect firstVisible`() {
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL))
    db.markFirstVisible(Megaphones.Event.PINS_FOR_ALL, 12345L)

    db.markInteractedWith(Megaphones.Event.PINS_FOR_ALL, interactionCount = 1, lastInteractionTimestamp = 99999L)

    assertThat(db.getAllAndDeleteMissing()).single().transform { it.firstVisible }.isEqualTo(12345L)
  }

  @Test
  fun `markFinished sets finished`() {
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL))

    db.markFinished(Megaphones.Event.PINS_FOR_ALL)

    assertThat(db.getAllAndDeleteMissing()).single().transform { it.finished }.isTrue()
  }

  @Test
  fun `delete removes only the targeted event`() {
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL, Megaphones.Event.NOTIFICATIONS))

    db.delete(Megaphones.Event.PINS_FOR_ALL)

    assertThat(db.getAllAndDeleteMissing().map { it.event })
      .containsExactlyInAnyOrder(Megaphones.Event.NOTIFICATIONS)
  }

  @Test
  fun `freshly inserted record has zeroed counters and unfinished state`() {
    db.insert(listOf(Megaphones.Event.PINS_FOR_ALL))

    val record = db.getAllAndDeleteMissing().single()
    assertThat(record.interactionCount).isEqualTo(0)
    assertThat(record.lastInteractionTime).isEqualTo(0L)
    assertThat(record.firstVisible).isEqualTo(0L)
    assertThat(record.lastVisible).isEqualTo(0L)
    assertThat(record.finished).isFalse()
  }
}
