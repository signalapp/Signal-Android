package org.signal.buildtools

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.ExperimentalOkHttpApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalOkHttpApi::class)
class SmartlingClientTest {

  private lateinit var server: MockWebServer
  private lateinit var client: SmartlingClient

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    client = SmartlingClient(
      userIdentifier = "test-user",
      userSecret = "test-secret",
      projectId = "test-project",
      baseUrl = server.url("/").toString().removeSuffix("/")
    )
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `authenticate returns access token on success`() {
    server.enqueue(
      MockResponse.Builder()
        .code(200)
        .body(
          """
          {
            "response": {
              "data": {
                "accessToken": "test-token-123"
              }
            }
          }
          """.trimIndent()
        )
        .build()
    )

    val token = client.authenticate()

    assertEquals("test-token-123", token)

    val request = server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/auth-api/v2/authenticate", request.path)
    assertTrue(request.body.readUtf8().contains("test-user"))
  }

  @Test(expected = SmartlingClient.SmartlingException::class)
  fun `authenticate throws on HTTP error`() {
    server.enqueue(
      MockResponse.Builder()
        .code(401)
        .body("Unauthorized")
        .build()
    )

    client.authenticate()
  }

  @Test(expected = SmartlingClient.SmartlingException::class)
  fun `authenticate throws on malformed response`() {
    server.enqueue(
      MockResponse.Builder()
        .code(200)
        .body("{}")
        .build()
    )

    client.authenticate()
  }

  @Test
  fun `uploadFile returns response body on success`() {
    server.enqueue(
      MockResponse.Builder()
        .code(200)
        .body("""{"response": {"data": {"uploaded": true}}}""")
        .build()
    )

    val tempFile = File.createTempFile("test-strings", ".xml").apply {
      writeText("<resources><string name=\"test\">Test</string></resources>")
      deleteOnExit()
    }

    val response = client.uploadFile("auth-token", tempFile, "strings.xml")

    assertTrue(response.contains("uploaded"))

    val request = server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/files-api/v2/projects/test-project/file", request.path)
    assertEquals("Bearer auth-token", request.headers["Authorization"])
  }

  @Test(expected = SmartlingClient.SmartlingException::class)
  fun `uploadFile throws on HTTP error`() {
    server.enqueue(
      MockResponse.Builder()
        .code(500)
        .body("Internal Server Error")
        .build()
    )

    val tempFile = File.createTempFile("test-strings", ".xml").apply {
      writeText("<resources/>")
      deleteOnExit()
    }

    client.uploadFile("auth-token", tempFile, "strings.xml")
  }

  @Test
  fun `getLocales returns list of locale IDs`() {
    server.enqueue(
      MockResponse.Builder()
        .code(200)
        .body(
          """
          {
            "response": {
              "data": {
                "items": [
                  {"localeId": "de"},
                  {"localeId": "fr"},
                  {"localeId": "es"}
                ]
              }
            }
          }
          """.trimIndent()
        )
        .build()
    )

    val locales = client.getLocales("auth-token", "strings.xml")

    assertEquals(listOf("de", "fr", "es"), locales)

    val request = server.takeRequest()
    assertEquals("GET", request.method)
    assertEquals("/files-api/v2/projects/test-project/file/status?fileUri=strings.xml", request.path)
    assertEquals("Bearer auth-token", request.headers["Authorization"])
  }

  @Test(expected = SmartlingClient.SmartlingException::class)
  fun `getLocales throws on HTTP error`() {
    server.enqueue(
      MockResponse.Builder()
        .code(404)
        .body("Not Found")
        .build()
    )

    client.getLocales("auth-token", "strings.xml")
  }

  @Test(expected = SmartlingClient.SmartlingException::class)
  fun `getLocales throws on malformed response`() {
    server.enqueue(
      MockResponse.Builder()
        .code(200)
        .body("""{"response": {"data": {}}}""")
        .build()
    )

    client.getLocales("auth-token", "strings.xml")
  }

  @Test
  fun `downloadFile returns file content`() {
    val xmlContent = """<resources><string name="hello">Hallo</string></resources>"""
    server.enqueue(
      MockResponse.Builder()
        .code(200)
        .body(xmlContent)
        .build()
    )

    val content = client.downloadFile("auth-token", "strings.xml", "de")

    assertEquals(xmlContent, content)

    val request = server.takeRequest()
    assertEquals("GET", request.method)
    assertEquals("/files-api/v2/projects/test-project/locales/de/file?fileUri=strings.xml", request.path)
    assertEquals("Bearer auth-token", request.headers["Authorization"])
  }

  @Test(expected = SmartlingClient.SmartlingException::class)
  fun `downloadFile throws on HTTP error`() {
    server.enqueue(
      MockResponse.Builder()
        .code(500)
        .body("Internal Server Error")
        .build()
    )

    client.downloadFile("auth-token", "strings.xml", "de")
  }
}
