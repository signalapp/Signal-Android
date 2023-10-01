package org.signal.wire

import com.squareup.wire.schema.*
import okio.Path
import okio.Path.Companion.toPath
import java.lang.UnsupportedOperationException
import java.nio.charset.Charset

/**
 * Iterate over all generated kotlin files and inline replace calls to countNonNull with calls to
 * a 'to be defined' where the proto is used implementation of 'countNonDefa' (short for countNonDefault).
 * This code runs as part of wire compilation via the gradle plugin system.
 *
 * To make the replacement easier, the new name is the same number of bytes as the old to allow using a
 * seeking read/write file descriptor to making the updates.
 *
 * The algorithm for searching/replacing is basic and could be optimized if necessary for faster build times.
 */
class Handler : SchemaHandler() {

  companion object {
    private val CHECK_FUNCTION = "countNonNull".encodeToByteArray()
    private val REPLACEMENT    = "countNonDefa".encodeToByteArray()
  }

  override fun handle(schema: Schema, context: Context) {
    context.fileSystem
      .listRecursively(context.outDirectory)
      .filter { it.name.endsWith("kt") }
      .forEach { path: Path ->
        val readInput = ByteArray(CHECK_FUNCTION.size)
        context.fileSystem.openReadWrite(path, mustCreate = false, mustExist = true).use {
          var fileOffset = 0L

          var read = it.read(fileOffset = fileOffset, array = readInput, arrayOffset = 0, byteCount = readInput.size)
          while (read >= 0) {
            if (readInput.contentEquals(CHECK_FUNCTION)) {
              it.write(fileOffset = fileOffset, array = REPLACEMENT, arrayOffset = 0, byteCount = REPLACEMENT.size)
              fileOffset += REPLACEMENT.size
            } else {
              fileOffset++
            }
            read = it.read(fileOffset = fileOffset, array = readInput, arrayOffset = 0, byteCount = readInput.size)
          }
        }
      }
  }

  override fun handle(extend: Extend, field: Field, context: Context): Path = throw UnsupportedOperationException()

  override fun handle(service: Service, context: Context): List<Path> = throw UnsupportedOperationException()

  override fun handle(type: Type, context: Context): Path = throw UnsupportedOperationException()
}
