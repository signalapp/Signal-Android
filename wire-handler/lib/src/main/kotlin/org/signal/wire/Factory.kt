package org.signal.wire

import com.squareup.wire.schema.SchemaHandler

class Factory : SchemaHandler.Factory {
  override fun create(
    includes: List<String>,
    excludes: List<String>,
    exclusive: Boolean,
    outDirectory: String,
    options: Map<String, String>
  ): SchemaHandler {
    return Handler()
  }
}
