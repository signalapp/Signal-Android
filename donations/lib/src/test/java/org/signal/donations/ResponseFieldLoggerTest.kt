package org.signal.donations

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Before
import org.junit.Test
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.Logger

class ResponseFieldLoggerTest {

  @Before
  fun setUp() {
    Log.initialize(object : Logger() {
      override fun v(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) = Unit
      override fun d(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) = Unit
      override fun i(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) = Unit
      override fun w(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) = println(message)
      override fun e(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) = Unit
      override fun flush() = Unit
    })
  }

  @Test
  fun `Given a null, when I logFields, then I expect no crash`() {
    ResponseFieldLogger.logFields(ObjectMapper(), null)
  }

  @Test
  fun `Given empty, when I logFields, then I expect no crash`() {
    ResponseFieldLogger.logFields(ObjectMapper(), "{}")
  }

  @Test
  fun `Given non-empty, when I logFields, then I expect no crash`() {
    ResponseFieldLogger.logFields(
      ObjectMapper(),
      """
      {
        "id": "asdf",
        "client_secret": 12345,
        "structured_obj": {
          "a": "a"
        }
      }
      """.trimIndent()
    )
  }
}
