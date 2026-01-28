package org.signal.camera.demo.screens.gallery

data class GalleryScreenState(
  val mediaItems: List<MediaItem> = emptyList(),
  val isLoading: Boolean = true,
  val error: String? = null
)
