package org.thoughtcrime.securesms.webrtc.video

interface CameraEventListener {
    fun onCameraSwitchCompleted(newCameraState: CameraState)
}

data class CameraState(val activeDirection: Direction, val cameraCount: Int) {
    companion object {
        val UNKNOWN = CameraState(Direction.NONE, 0)
    }

    val enabled: Boolean
        get() = activeDirection != Direction.NONE

    enum class Direction {
        FRONT, BACK, NONE, PENDING
    }
}