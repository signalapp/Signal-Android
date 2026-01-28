package org.signal.camera

import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Configuration for video output.
 * Allows the consumer to specify where videos should be saved.
 */
sealed class VideoOutput {
  /**
   * Save video to a specific file.
   * The consumer is responsible for creating the file and managing its lifecycle.
   */
  data class FileOutput(val file: File) : VideoOutput()
  
  /**
   * Save video to a file descriptor.
   * The consumer provides the file descriptor and is responsible for closing it.
   * This is useful for writing to pipes, sockets, or any other file-descriptor-backed output.
   */
  data class FileDescriptorOutput(val fileDescriptor: ParcelFileDescriptor) : VideoOutput()
}

/**
 * Result of video capture.
 */
sealed class VideoCaptureResult {
  /**
   * Video was successfully captured and saved.
   * @param outputFile The file where the video was saved (for FileOutput)
   * @param fileDescriptor The file descriptor used (for FileDescriptorOutput)
   */
  data class Success(
    val outputFile: File? = null,
    val fileDescriptor: ParcelFileDescriptor? = null
  ) : VideoCaptureResult()
  
  /**
   * Video capture failed.
   * @param fileDescriptor The file descriptor that was being used (for cleanup)
   */
  data class Error(
    val message: String?,
    val throwable: Throwable?,
    val fileDescriptor: ParcelFileDescriptor? = null
  ) : VideoCaptureResult()
}
