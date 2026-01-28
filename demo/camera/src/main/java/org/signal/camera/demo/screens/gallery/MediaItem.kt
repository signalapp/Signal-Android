package org.signal.camera.demo.screens.gallery

import java.io.File

sealed class MediaItem {
  abstract val file: File
  abstract val name: String
  abstract val lastModified: Long
  
  data class Image(
    override val file: File,
    override val name: String = file.name,
    override val lastModified: Long = file.lastModified()
  ) : MediaItem()
  
  data class Video(
    override val file: File,
    override val name: String = file.name,
    override val lastModified: Long = file.lastModified()
  ) : MediaItem()
  
  val isImage: Boolean get() = this is Image
  val isVideo: Boolean get() = this is Video
}
