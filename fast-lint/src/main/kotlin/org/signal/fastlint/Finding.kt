package org.signal.fastlint

import java.io.File

/** A single issue reported by a [Rule]. */
data class Finding(
  val checkId: String,
  val file: File,
  val line: Int,
  val message: String
)
