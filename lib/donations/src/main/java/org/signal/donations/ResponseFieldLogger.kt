package org.signal.donations

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.signal.core.util.logging.Log
import java.io.IOException

internal object ResponseFieldLogger {

  private val TAG = Log.tag(ResponseFieldLogger::class.java)

  fun logFields(objectMapper: ObjectMapper, json: String?) {
    if (json == null) {
      Log.w(TAG, "Response body was null. No keys to print.")
      return
    }

    try {
      val mapType = object : TypeReference<Map<String, Any>>() {}
      val map = objectMapper.readValue(json, mapType)

      Log.w(TAG, "Map keys (${map.size}): ${map.keys.joinToString()}", true)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to produce key map.", true)
    }
  }
}
