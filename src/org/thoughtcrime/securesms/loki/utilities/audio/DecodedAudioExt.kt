package org.thoughtcrime.securesms.loki.utilities.audio;

import java.nio.ShortBuffer
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Computes audio RMS values for the first channel only.
 *
 * A typical RMS calculation algorithm is:
 * 1. Square each sample
 * 2. Sum the squared samples
 * 3. Divide the sum of the squared samples by the number of samples
 * 4. Take the square root of step 3., the mean of the squared samples
 *
 * @param maxFrames Defines amount of output RMS frames.
 * If number of samples per channel is less than "maxFrames",
 * the result array will match the source sample size instead.
 *
 * @return RMS values float array where is each value is within [0..1] range.
 */
fun DecodedAudio.calculateRms(maxFrames: Int): FloatArray {
    return calculateRms(this.samples, this.numSamples, this.channels, maxFrames)
}

fun calculateRms(samples: ShortBuffer, numSamples: Int, channels: Int, maxFrames: Int): FloatArray {
    val numFrames: Int
    val frameStep: Float

    val samplesPerChannel = numSamples / channels
    if (samplesPerChannel <= maxFrames) {
        frameStep = 1f
        numFrames = samplesPerChannel
    } else {
        frameStep = numSamples / maxFrames.toFloat()
        numFrames = maxFrames
    }

    val rmsValues = FloatArray(numFrames)

    var squaredFrameSum = 0.0
    var currentFrameIdx = 0

    fun calculateFrameRms(nextFrameIdx: Int) {
        rmsValues[currentFrameIdx] = sqrt(squaredFrameSum.toFloat())

        // Advance to the next frame.
        squaredFrameSum = 0.0
        currentFrameIdx = nextFrameIdx
    }

    (0 until numSamples * channels step channels).forEach { sampleIdx ->
        val channelSampleIdx = sampleIdx / channels
        val frameIdx = (channelSampleIdx / frameStep).toInt()

        if (currentFrameIdx != frameIdx) {
            // Calculate RMS value for the previous frame.
            calculateFrameRms(frameIdx)
        }

        val samplesInCurrentFrame = ceil((currentFrameIdx + 1) * frameStep) - ceil(currentFrameIdx * frameStep)
        squaredFrameSum += (samples[sampleIdx] * samples[sampleIdx]) / samplesInCurrentFrame
    }
    // Calculate RMS value for the last frame.
    calculateFrameRms(-1)

    normalizeArray(rmsValues)
//    smoothArray(rmsValues, 1.0f)

    return rmsValues
}

/**
 * Normalizes the array's values to [0..1] range.
 */
fun normalizeArray(values: FloatArray) {
    var maxValue = -Float.MAX_VALUE
    var minValue = +Float.MAX_VALUE
    values.forEach { value ->
        if (value > maxValue) maxValue = value
        if (value < minValue) minValue = value
    }
    val span = maxValue - minValue

    if (span == 0f) {
        values.indices.forEach { i -> values[i] = 0f }
        return
    }

    values.indices.forEach { i -> values[i] = (values[i] - minValue) / span }
}

fun smoothArray(values: FloatArray, neighborWeight: Float = 1f): FloatArray {
    if (values.size < 3) return values

    val result = FloatArray(values.size)
    result[0] = values[0]
    result[values.size - 1] == values[values.size - 1]
    for (i in 1 until values.size - 1) {
        result[i] = (values[i] + values[i - 1] * neighborWeight +
                values[i + 1] * neighborWeight) / (1f + neighborWeight * 2f)
    }
    return result
}