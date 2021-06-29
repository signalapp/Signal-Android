package org.thoughtcrime.securesms.conversation.v2.utilities

class ThumbnailDimensDelegate {

    companion object {
        // dimens array constants
        private const val WIDTH = 0
        private const val HEIGHT = 1
        private const val DIMENS_ARRAY_SIZE = 2

        // bounds array constants
        private const val MIN_WIDTH = 0
        private const val MIN_HEIGHT = 1
        private const val MAX_WIDTH = 2
        private const val MAX_HEIGHT = 3
        private const val BOUNDS_ARRAY_SIZE = 4

        // const zero int array
        private val EMPTY_DIMENS = intArrayOf(0,0)

    }

    private val measured: IntArray = IntArray(DIMENS_ARRAY_SIZE)
    private val dimens: IntArray = IntArray(DIMENS_ARRAY_SIZE)
    private val bounds: IntArray = IntArray(BOUNDS_ARRAY_SIZE)

    fun resourceSize(): IntArray {
        if (dimens.all { it == 0 }) {
            // dimens are (0, 0), don't go any further
            return EMPTY_DIMENS
        }

        val naturalWidth = dimens[WIDTH].toDouble()
        val naturalHeight = dimens[HEIGHT].toDouble()
        val minWidth = dimens[MIN_WIDTH]
        val maxWidth = dimens[MAX_WIDTH]
        val minHeight = dimens[MIN_HEIGHT]
        val maxHeight = dimens[MAX_HEIGHT]

        // calculate actual measured
        var measuredWidth: Double = naturalWidth
        var measuredHeight: Double = naturalHeight

        val widthInBounds = measuredWidth >= minWidth && measuredWidth <= maxWidth
        val heightInBounds = measuredHeight >= minHeight && measuredHeight <= maxHeight

        if (!widthInBounds || !heightInBounds) {
            val minWidthRatio: Double = naturalWidth / minWidth
            val maxWidthRatio: Double = naturalWidth / maxWidth
            val minHeightRatio: Double = naturalHeight / minHeight
            val maxHeightRatio: Double = naturalHeight / maxHeight
            if (maxWidthRatio > 1 || maxHeightRatio > 1) {
                if (maxWidthRatio >= maxHeightRatio) {
                    measuredWidth /= maxWidthRatio
                    measuredHeight /= maxWidthRatio
                } else {
                    measuredWidth /= maxHeightRatio
                    measuredHeight /= maxHeightRatio
                }
                measuredWidth = Math.max(measuredWidth, minWidth.toDouble())
                measuredHeight = Math.max(measuredHeight, minHeight.toDouble())
            } else if (minWidthRatio < 1 || minHeightRatio < 1) {
                if (minWidthRatio <= minHeightRatio) {
                    measuredWidth /= minWidthRatio
                    measuredHeight /= minWidthRatio
                } else {
                    measuredWidth /= minHeightRatio
                    measuredHeight /= minHeightRatio
                }
                measuredWidth = Math.min(measuredWidth, maxWidth.toDouble())
                measuredHeight = Math.min(measuredHeight, maxHeight.toDouble())
            }
        }
        measured[WIDTH] = measuredWidth.toInt()
        measured[HEIGHT] = measuredHeight.toInt()
        return measured
    }

    fun setBounds(minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int) {
        bounds[MIN_WIDTH] = minWidth
        bounds[MIN_HEIGHT] = minHeight
        bounds[MAX_WIDTH] = maxWidth
        bounds[MAX_HEIGHT] = maxHeight
    }

    fun setDimens(width: Int, height: Int) {
        dimens[WIDTH] = width
        dimens[HEIGHT] = height
    }

}