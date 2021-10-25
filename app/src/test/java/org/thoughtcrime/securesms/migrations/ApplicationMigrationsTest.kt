package org.thoughtcrime.securesms.migrations

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class ApplicationMigrationsTest {
  @Test
  fun `ensure ApplicationMigration CURRENT_VERSION matches max version`() {
    val fields: Array<Field> = ApplicationMigrations.Version::class.java.declaredFields

    val maxField: Int? = fields.filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.java }
      .map { it.getInt(null) }
      .maxOrNull()

    assertEquals(ApplicationMigrations.CURRENT_VERSION, maxField)
  }
}
