/*
MIT License

Copyright (c) 2020 Tiago Ornelas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package org.thoughtcrime.securesms.components.segmentedprogressbar

/**
 * Created by Tiago Ornelas on 18/04/2020.
 * Model that holds the segment state
 */
class Segment(val animationDurationMillis: Long) {

  var animationProgressPercentage: Float = 0f

  var animationState: AnimationState = AnimationState.IDLE
    set(value) {
      animationProgressPercentage = when (value) {
        AnimationState.ANIMATED -> 1f
        AnimationState.IDLE -> 0f
        else -> animationProgressPercentage
      }
      field = value
    }

  /**
   * Represents possible drawing states of the segment
   */
  enum class AnimationState {
    ANIMATED,
    ANIMATING,
    IDLE
  }
}
