package org.thoughtcrime.securesms.webrtc.data

// get the video rotation from a specific rotation, locked into 90 degree
// chunks offset by 45 degrees
fun Int.quadrantRotation() = when (this % 360) {
    in 315 .. 360,
    in 0 until 45 -> 0
    in 45 until 135 -> 90
    in 135 until 225 -> 180
    else -> 270
}