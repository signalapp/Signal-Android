package org.signal.wire

import com.squareup.wire.schema.SchemaHandler

class Factory : SchemaHandler.Factory {
  override fun create(): SchemaHandler {
    return Handler()
  }
}
