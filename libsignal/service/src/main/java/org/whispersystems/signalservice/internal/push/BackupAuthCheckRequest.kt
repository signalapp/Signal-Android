package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import okio.ByteString.Companion.encode
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import java.nio.charset.StandardCharsets

/**
 * Request body JSON for verifying stored KBS auth credentials.
 */
@Suppress("unused")
class BackupAuthCheckRequest @JsonCreator constructor(
  val number: String?,
  val passwords: List<String>
)

/**
 * Verify KBS auth credentials JSON response.
 */
data class BackupAuthCheckResponse @JsonCreator constructor(
  private val matches: Map<String, Map<String, Any>>
) {
  private val actualMatches = matches["matches"] ?: emptyMap()

  val match: String? = actualMatches.entries.firstOrNull { it.value.toString() == "match" }?.key?.toBasic()
  val invalid: List<String> = actualMatches.filterValues { it.toString() == "invalid" }.keys.map { it.toBasic() }

  /** Server expects and returns values as <username>:<password> but we prefer the full encoded Basic auth header format */
  private fun String.toBasic(): String {
    return "Basic ${encode(StandardCharsets.ISO_8859_1).base64()}"
  }
}

/**
 * Processes a response from the verify stored KBS auth credentials request.
 */
class BackupAuthCheckProcessor(response: ServiceResponse<BackupAuthCheckResponse>) : ServiceResponseProcessor<BackupAuthCheckResponse>(response) {
  fun getInvalid(): List<String> {
    return response.result.map { it.invalid }.orElse(emptyList())
  }

  fun getValid(): String? {
    return response.result.map { it.match }.orElse(null)
  }
}
