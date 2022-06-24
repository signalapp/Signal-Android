package org.signal.spinner

interface Plugin {
  fun get(): PluginResult
  val name: String
  val path: String
}
