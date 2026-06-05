/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.spinner

import org.json.JSONObject

internal sealed class SpinnerQueryEvent {

  abstract fun serialize(): String

  data class Start(
    val trackId: Long,
    val trackName: String,
    val name: String,
    val query: String?,
    val table: String?,
    val holder: String?,
    val timestampMs: Long
  ) : SpinnerQueryEvent() {
    override fun serialize(): String {
      val out = JSONObject()
      out.put("type", "start")
      out.put("trackId", trackId)
      out.put("trackName", trackName)
      out.put("name", name)
      query?.let { out.put("query", it) }
      table?.let { out.put("table", it) }
      holder?.let { out.put("holder", it) }
      out.put("t", timestampMs)
      return out.toString(0)
    }
  }

  data class End(
    val trackId: Long,
    val name: String,
    val timestampMs: Long
  ) : SpinnerQueryEvent() {
    override fun serialize(): String {
      val out = JSONObject()
      out.put("type", "end")
      out.put("trackId", trackId)
      out.put("name", name)
      out.put("t", timestampMs)
      return out.toString(0)
    }
  }
}
