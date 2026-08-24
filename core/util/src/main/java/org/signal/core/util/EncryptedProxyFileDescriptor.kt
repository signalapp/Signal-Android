/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A seekable [ParcelFileDescriptor] whose contents are transparently encrypted before being
 * written to a backing file on disk, via [StorageManager.openProxyFileDescriptor].
 *
 * Uses the same encryption scheme as attachments.
 *
 * It's possible a device may not support this -- check [isSupported] first.
 */
@RequiresApi(26)
class EncryptedProxyFileDescriptor private constructor(
  private val parcelFileDescriptor: ParcelFileDescriptor,
  @get:VisibleForTesting
  internal val backingFile: File
) : SeekableFileDescriptor {

  companion object {
    private val TAG = Log.tag(EncryptedProxyFileDescriptor::class.java)

    private const val DIRECTORY = "encrypted-proxy-fd"
    private const val SELF_TEST_DEBUG_NAME = "self-test"
    private const val SELF_TEST_HEADER_SIZE = 28
    private const val SELF_TEST_LARGE_WRITE_SIZE = 512 * 1024
    private const val SELF_TEST_FOOTER_OFFSET = SELF_TEST_HEADER_SIZE + SELF_TEST_LARGE_WRITE_SIZE
    private const val SELF_TEST_FOOTER_SIZE = 16
    private const val SELF_TEST_SIZE = SELF_TEST_FOOTER_OFFSET + SELF_TEST_FOOTER_SIZE
    private const val SELF_TEST_PATCH_OFFSET = 8
    private const val SELF_TEST_PATCH_SIZE = 8
    private const val KEY_SIZE = 32
    private const val BLOCK_SIZE = 16

    private val activeFiles: MutableSet<String> = mutableSetOf()

    @Volatile
    private var cachedSelfTestResult: Boolean? = null

    /**
     * Whether proxy file descriptors work on this device, verified by an end-to-end self-test.
     * The result is cached for the lifetime of the process.
     *
     * The self-test touches the filesystem, so the first call must not happen on the main thread.
     */
    @JvmStatic
    @WorkerThread
    fun isSupported(context: Context): Boolean {
      cachedSelfTestResult?.let { return it }

      val result = try {
        selfTest(context)
      } catch (t: Throwable) {
        Log.w(TAG, "Self-test failed.", t)
        false
      }

      Log.i(TAG, "Self-test result: $result")
      cachedSelfTestResult = result
      return result
    }

    /**
     * Retires proxy file descriptors for the remainder of the process, so that callers fall back to whatever
     * they use when they're unavailable. For failures that only surface once a descriptor is in real use, which
     * the self-test cannot be relied upon to predict.
     */
    @JvmStatic
    fun markUnsupported() {
      if (cachedSelfTestResult != false) {
        Log.w(TAG, "Marking proxy file descriptors as unsupported.")
      }
      cachedSelfTestResult = false
    }

    /**
     * Creates a new descriptor whose backing file lives in the app's cache directory, or null if one
     * could not be opened.
     *
     * @param debugName Used to name the backing file. Contents are encrypted regardless.
     */
    @JvmStatic
    fun create(context: Context, debugName: String): EncryptedProxyFileDescriptor? {
      if (cachedSelfTestResult == false) {
        return null
      }

      val storageManager = context.getSystemService(StorageManager::class.java)
      if (storageManager == null) {
        Log.w(TAG, "StorageManager is unavailable.")
        return null
      }

      val backingFile: File
      val channel: FileChannel
      try {
        val directory = File(context.cacheDir, DIRECTORY)
        directory.mkdirs()
        deleteStaleFiles(directory)

        backingFile = createBackingFile(debugName, directory)
        channel = RandomAccessFile(backingFile, "rw").channel
      } catch (e: IOException) {
        Log.w(TAG, "Failed to create backing file.", e)
        return null
      }

      val key = ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }

      val thread = HandlerThread("EncryptedProxyFd")
      thread.start()

      val callback = Callback(channel, key) {
        if (!backingFile.delete()) {
          Log.w(TAG, "Failed to delete backing file on release.")
        }
        synchronized(activeFiles) {
          activeFiles -= backingFile.name
        }
        thread.quitSafely()
      }

      return try {
        val parcelFileDescriptor = storageManager.openProxyFileDescriptor(
          ParcelFileDescriptor.MODE_READ_WRITE,
          callback,
          Handler(thread.looper)
        )
        EncryptedProxyFileDescriptor(parcelFileDescriptor, backingFile)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to open proxy file descriptor.", e)
        callback.onRelease()
        null
      }
    }

    /**
     * Creating the file and registering it must be atomic with respect to [deleteStaleFiles], which
     * would otherwise be free to delete a backing file created by a concurrent [create].
     */
    @Throws(IOException::class)
    private fun createBackingFile(debugName: String, directory: File): File {
      return synchronized(activeFiles) {
        File.createTempFile(debugName, ".enc", directory).also { activeFiles += it.name }
      }
    }

    private fun deleteStaleFiles(directory: File) {
      synchronized(activeFiles) {
        val stale = directory.listFiles()?.filter { it.name !in activeFiles } ?: emptyList()

        for (file in stale) {
          if (!file.delete()) {
            Log.w(TAG, "Failed to delete stale file.")
          }
        }
      }
    }

    /**
     * Verifies that data written through a proxy descriptor reads back intact, and that only ciphertext
     * lands in the backing file. Mirrors MP4 muxer write pattern.
     */
    private fun selfTest(context: Context): Boolean {
      val descriptor = create(context, SELF_TEST_DEBUG_NAME)
      if (descriptor == null) {
        Log.w(TAG, "Self-test: failed to create descriptor.")
        return false
      }

      return descriptor.use { proxy ->
        val fd = proxy.fileDescriptor
        val random = SecureRandom()
        val expected = ByteArray(SELF_TEST_SIZE).also { random.nextBytes(it) }

        if (!pwriteExact(fd, "header", expected, 0, SELF_TEST_HEADER_SIZE, 0)) {
          return@use false
        }

        if (!pwriteExact(fd, "reservation", expected, SELF_TEST_HEADER_SIZE, SELF_TEST_LARGE_WRITE_SIZE, SELF_TEST_HEADER_SIZE.toLong())) {
          return@use false
        }

        if (!pwriteExact(fd, "footer", expected, SELF_TEST_FOOTER_OFFSET, SELF_TEST_FOOTER_SIZE, SELF_TEST_FOOTER_OFFSET.toLong())) {
          return@use false
        }

        val patch = ByteArray(SELF_TEST_PATCH_SIZE).also { random.nextBytes(it) }
        System.arraycopy(patch, 0, expected, SELF_TEST_PATCH_OFFSET, patch.size)
        if (!pwriteExact(fd, "patch", patch, 0, patch.size, SELF_TEST_PATCH_OFFSET.toLong())) {
          return@use false
        }

        if (Os.fstat(fd).st_size != SELF_TEST_SIZE.toLong()) {
          Log.w(TAG, "Self-test: unexpected file size.")
          return@use false
        }

        val actual = ByteArray(SELF_TEST_SIZE)
        if (!preadFully(fd, actual, 0, actual.size, 0)) {
          Log.w(TAG, "Self-test: short read.")
          return@use false
        }

        if (!expected.contentEquals(actual)) {
          Log.w(TAG, "Self-test: data mismatch.")
          return@use false
        }

        val onDisk = proxy.backingFile.readBytes()
        if (onDisk.size != SELF_TEST_SIZE || onDisk.contentEquals(expected)) {
          Log.w(TAG, "Self-test: backing file does not look encrypted.")
          return@use false
        }

        true
      }
    }

    /**
     * A single [Os.pwrite] that has to transfer everything it was handed.
     */
    private fun pwriteExact(fd: FileDescriptor, label: String, data: ByteArray, byteOffset: Int, byteCount: Int, fileOffset: Long): Boolean {
      val written = try {
        Os.pwrite(fd, data, byteOffset, byteCount, fileOffset)
      } catch (e: ErrnoException) {
        Log.w(TAG, "Self-test: $label write failed.", e)
        return false
      }

      if (written != byteCount) {
        Log.w(TAG, "Self-test: $label write was short. Wanted $byteCount, wrote $written.")
        return false
      }

      return true
    }

    private fun preadFully(fd: FileDescriptor, data: ByteArray, byteOffset: Int, byteCount: Int, fileOffset: Long): Boolean {
      var read = 0
      while (read < byteCount) {
        val result = Os.pread(fd, data, byteOffset + read, byteCount - read, fileOffset + read)
        if (result <= 0) {
          return false
        }
        read += result
      }
      return true
    }

    /**
     * Encrypts or decrypts [length] bytes of [input] (starting at index 0) into [output] (also starting at
     * index 0), where those bytes sit at byte [offset] of the file. In AES/CTR both directions are the same
     * operation, so this one function serves reads and writes, and any byte range can be processed on its own
     * as long as the offset it came from is provided.
     *
     * Uses the same zero-IV counter layout as ModernDecryptingPartInputStream: the block counter is written
     * as a 4-byte big-endian value at the end of the IV, which supports offsets up to 64 GiB.
     */
    @VisibleForTesting
    internal fun encryptOrDecrypt(key: ByteArray, offset: Long, input: ByteArray, length: Int, output: ByteArray) {
      require(offset >= 0) { "Offset must be non-negative, but was $offset." }
      require(length <= input.size) { "Requested $length bytes from an input of ${input.size}." }
      require(length <= output.size) { "Requested $length bytes into an output of ${output.size}." }

      try {
        val iv = ByteArray(BLOCK_SIZE)
        val remainder = (offset % BLOCK_SIZE).toInt()
        Conversions.longTo4ByteArray(iv, 12, offset / BLOCK_SIZE)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

        if (remainder > 0) {
          cipher.update(ByteArray(remainder))
        }

        cipher.update(input, 0, length, output, 0)
      } catch (e: GeneralSecurityException) {
        throw AssertionError(e)
      }
    }
  }

  override val fileDescriptor: FileDescriptor
    get() = parcelFileDescriptor.fileDescriptor

  override val parcelFd: ParcelFileDescriptor
    get() = parcelFileDescriptor

  override fun close() {
    parcelFileDescriptor.close()
  }

  @VisibleForTesting
  internal class Callback(
    private val channel: FileChannel,
    private val key: ByteArray,
    private val onReleased: () -> Unit
  ) : ProxyFileDescriptorCallback() {

    @Throws(ErrnoException::class)
    override fun onGetSize(): Long {
      try {
        return channel.size()
      } catch (e: IOException) {
        throw ErrnoException("onGetSize", OsConstants.EIO, e)
      }
    }

    @Throws(ErrnoException::class)
    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
      try {
        val ciphertext = ByteArray(size)
        val buffer = ByteBuffer.wrap(ciphertext)
        var totalRead = 0

        while (totalRead < size) {
          val read = channel.read(buffer, offset + totalRead)
          if (read < 0) {
            break
          }
          totalRead += read
        }

        if (totalRead > 0) {
          encryptOrDecrypt(key, offset, ciphertext, totalRead, data)
        }

        return totalRead
      } catch (e: IOException) {
        throw ErrnoException("onRead", OsConstants.EIO, e)
      }
    }

    @Throws(ErrnoException::class)
    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
      try {
        val ciphertext = ByteArray(size)
        encryptOrDecrypt(key, offset, data, size, ciphertext)

        val buffer = ByteBuffer.wrap(ciphertext)
        while (buffer.hasRemaining()) {
          channel.write(buffer, offset + buffer.position())
        }

        return size
      } catch (e: IOException) {
        throw ErrnoException("onWrite", OsConstants.EIO, e)
      }
    }

    @Throws(ErrnoException::class)
    override fun onFsync() {
      try {
        channel.force(true)
      } catch (e: IOException) {
        throw ErrnoException("onFsync", OsConstants.EIO, e)
      }
    }

    override fun onRelease() {
      key.fill(0)

      try {
        channel.close()
      } catch (e: IOException) {
        Log.w(TAG, "Failed to close backing channel", e)
      }

      onReleased()
    }
  }
}
