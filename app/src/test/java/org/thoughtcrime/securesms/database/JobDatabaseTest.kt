import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.database.SqlCipherDeletingErrorHandler
import org.thoughtcrime.securesms.dependencies.AppDependencies

class JobDatabaseTest {

  private lateinit var mockApplication: Application
  private lateinit var mockDatabase: SQLiteDatabase
  private lateinit var sqlCipherDeletingErrorHandler: SqlCipherDeletingErrorHandler
  private lateinit var databaseSecret: DatabaseSecret

  @Before
  fun setUp() {
    mockApplication = mockk(relaxed = true)

    // Set _application field in AppDependencies using reflection
    val applicationField = AppDependencies::class.java.getDeclaredField("_application")
    applicationField.isAccessible = true
    applicationField.set(null, mockApplication)

    databaseSecret = mockk(relaxed = true)
    mockDatabase = mockk(relaxed = true)
    every { mockDatabase.rawQuery(any(), any()) } returns mockk()
    sqlCipherDeletingErrorHandler = spyk(SqlCipherDeletingErrorHandler("signal-jobmanager.db"))
    every { mockApplication.deleteDatabase("signal-jobmanager.db") } returns true
  }

  @After
  fun tearDown() {
    unmockkObject(AppDependencies)
  }

  @Test
  fun `onCorruption deletes database on corruption event`() {
    sqlCipherDeletingErrorHandler.onCorruption(mockDatabase, "Database corrupted!")
    verify { mockApplication.deleteDatabase("signal-jobmanager.db") }
  }

  @Test
  fun `JobDatabase initializes with SqlCipherDeletingErrorHandler`() {
    val jobDatabase = JobDatabase(mockApplication, databaseSecret)

    // Use reflection to access the private errorHandler field
    val errorHandlerField = SQLiteOpenHelper::class.java.getDeclaredField("mErrorHandler")
    errorHandlerField.isAccessible = true
    val errorHandler = errorHandlerField.get(jobDatabase)
    assert(errorHandler is SqlCipherDeletingErrorHandler) {
      "Expected SqlCipherDeletingErrorHandler, but got ${errorHandler?.javaClass?.name}"
    }
  }
}
