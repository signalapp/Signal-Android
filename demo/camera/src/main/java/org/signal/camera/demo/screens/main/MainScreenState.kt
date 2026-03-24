package org.signal.camera.demo.screens.main

data class MainScreenState(
  val saveStatus: SaveStatus? = null
)

sealed interface SaveStatus {
  data object Saving : SaveStatus
  data object Success : SaveStatus
  data class Error(val message: String?) : SaveStatus
}
