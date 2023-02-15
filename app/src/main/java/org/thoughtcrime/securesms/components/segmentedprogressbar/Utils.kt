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

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue

fun Context.getThemeColor(attributeColor: Int): Int {
  val typedValue = TypedValue()
  this.theme.resolveAttribute(attributeColor, typedValue, true)
  return typedValue.data
}

fun SegmentedProgressBar.getDrawingComponents(
  segment: Segment,
  segmentIndex: Int
): Pair<MutableList<RectF>, MutableList<Paint>> {
  val rectangles = mutableListOf<RectF>()
  val paints = mutableListOf<Paint>()
  val segmentWidth = segmentWidth
  val startBound = segmentIndex * segmentWidth + ((segmentIndex) * margin)
  val endBound = startBound + segmentWidth
  val stroke = if (!strokeApplicable) 0f else this.segmentStrokeWidth.toFloat()

  val backgroundPaint = Paint().apply {
    style = Paint.Style.FILL
    color = segmentBackgroundColor
  }

  val selectedBackgroundPaint = Paint().apply {
    style = Paint.Style.FILL
    color = segmentSelectedBackgroundColor
  }

  val strokePaint = Paint().apply {
    color =
      if (segment.animationState == Segment.AnimationState.IDLE) segmentStrokeColor else segmentSelectedStrokeColor
    style = Paint.Style.STROKE
    strokeWidth = stroke
  }

  // Background component
  if (segment.animationState == Segment.AnimationState.ANIMATED) {
    rectangles.add(RectF(startBound + stroke, height - stroke, endBound - stroke, stroke))
    paints.add(selectedBackgroundPaint)
  } else {
    rectangles.add(RectF(startBound + stroke, height - stroke, endBound - stroke, stroke))
    paints.add(backgroundPaint)
  }

  // Progress component
  if (segment.animationState == Segment.AnimationState.ANIMATING) {
    rectangles.add(
      RectF(
        startBound + stroke,
        height - stroke,
        startBound + segment.animationProgressPercentage * segmentWidth,
        stroke
      )
    )
    paints.add(selectedBackgroundPaint)
  }

  // Stroke component
  if (stroke > 0) {
    rectangles.add(RectF(startBound + stroke, height - stroke, endBound - stroke, stroke))
    paints.add(strokePaint)
  }

  return Pair(rectangles, paints)
}
