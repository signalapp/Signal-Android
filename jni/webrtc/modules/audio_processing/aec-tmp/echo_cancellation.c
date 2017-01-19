/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * Contains the API functions for the AEC.
 */
#include "webrtc/modules/audio_processing/aec/include/echo_cancellation.h"

#include <math.h>
#ifdef WEBRTC_AEC_DEBUG_DUMP
#include <stdio.h>
#endif
#include <stdlib.h>
#include <string.h>

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_processing/aec/aec_core.h"
#include "webrtc/modules/audio_processing/aec/aec_resampler.h"
#include "webrtc/modules/audio_processing/aec/echo_cancellation_internal.h"
#include "webrtc/modules/audio_processing/utility/ring_buffer.h"
#include "webrtc/typedefs.h"

// Measured delays [ms]
// Device                Chrome  GTP
// MacBook Air           10
// MacBook Retina        10      100
// MacPro                30?
//
// Win7 Desktop          70      80?
// Win7 T430s            110
// Win8 T420s            70
//
// Daisy                 50
// Pixel (w/ preproc?)           240
// Pixel (w/o preproc?)  110     110

// The extended filter mode gives us the flexibility to ignore the system's
// reported delays. We do this for platforms which we believe provide results
// which are incompatible with the AEC's expectations. Based on measurements
// (some provided above) we set a conservative (i.e. lower than measured)
// fixed delay.
//
// WEBRTC_UNTRUSTED_DELAY will only have an impact when |extended_filter_mode|
// is enabled. See the note along with |DelayCorrection| in
// echo_cancellation_impl.h for more details on the mode.
//
// Justification:
// Chromium/Mac: Here, the true latency is so low (~10-20 ms), that it plays
// havoc with the AEC's buffering. To avoid this, we set a fixed delay of 20 ms
// and then compensate by rewinding by 10 ms (in wideband) through
// kDelayDiffOffsetSamples. This trick does not seem to work for larger rewind
// values, but fortunately this is sufficient.
//
// Chromium/Linux(ChromeOS): The values we get on this platform don't correspond
// well to reality. The variance doesn't match the AEC's buffer changes, and the
// bulk values tend to be too low. However, the range across different hardware
// appears to be too large to choose a single value.
//
// GTP/Linux(ChromeOS): TBD, but for the moment we will trust the values.
#if defined(WEBRTC_CHROMIUM_BUILD) && defined(WEBRTC_MAC)
#define WEBRTC_UNTRUSTED_DELAY
#endif

#if defined(WEBRTC_UNTRUSTED_DELAY) && defined(WEBRTC_MAC)
static const int kDelayDiffOffsetSamples = -160;
#else
// Not enabled for now.
static const int kDelayDiffOffsetSamples = 0;
#endif

#if defined(WEBRTC_MAC)
static const int kFixedDelayMs = 20;
#else
static const int kFixedDelayMs = 50;
#endif
#if !defined(WEBRTC_UNTRUSTED_DELAY)
static const int kMinTrustedDelayMs = 20;
#endif
static const int kMaxTrustedDelayMs = 500;

// Maximum length of resampled signal. Must be an integer multiple of frames
// (ceil(1/(1 + MIN_SKEW)*2) + 1)*FRAME_LEN
// The factor of 2 handles wb, and the + 1 is as a safety margin
// TODO(bjornv): Replace with kResamplerBufferSize
#define MAX_RESAMP_LEN (5 * FRAME_LEN)

static const int kMaxBufSizeStart = 62;  // In partitions
static const int sampMsNb = 8;           // samples per ms in nb
static const int initCheck = 42;

#ifdef WEBRTC_AEC_DEBUG_DUMP
int webrtc_aec_instance_count = 0;
#endif

// Estimates delay to set the position of the far-end buffer read pointer
// (controlled by knownDelay)
static void EstBufDelayNormal(aecpc_t* aecInst);
static void EstBufDelayExtended(aecpc_t* aecInst);
static int ProcessNormal(aecpc_t* self,
                         const float* near,
                         const float* near_high,
                         float* out,
                         float* out_high,
                         int16_t num_samples,
                         int16_t reported_delay_ms,
                         int32_t skew);
static void ProcessExtended(aecpc_t* self,
                            const float* near,
                            const float* near_high,
                            float* out,
                            float* out_high,
                            int16_t num_samples,
                            int16_t reported_delay_ms,
                            int32_t skew);

int32_t WebRtcAec_Create(void** aecInst) {
  aecpc_t* aecpc;
  if (aecInst == NULL) {
    return -1;
  }

  aecpc = malloc(sizeof(aecpc_t));
  *aecInst = aecpc;
  if (aecpc == NULL) {
    return -1;
  }

  if (WebRtcAec_CreateAec(&aecpc->aec) == -1) {
    WebRtcAec_Free(aecpc);
    aecpc = NULL;
    return -1;
  }

  if (WebRtcAec_CreateResampler(&aecpc->resampler) == -1) {
    WebRtcAec_Free(aecpc);
    aecpc = NULL;
    return -1;
  }
  // Create far-end pre-buffer. The buffer size has to be large enough for
  // largest possible drift compensation (kResamplerBufferSize) + "almost" an
  // FFT buffer (PART_LEN2 - 1).
  aecpc->far_pre_buf =
      WebRtc_CreateBuffer(PART_LEN2 + kResamplerBufferSize, sizeof(float));
  if (!aecpc->far_pre_buf) {
    WebRtcAec_Free(aecpc);
    aecpc = NULL;
    return -1;
  }

  aecpc->initFlag = 0;
  aecpc->lastError = 0;

#ifdef WEBRTC_AEC_DEBUG_DUMP
  {
    char filename[64];
    sprintf(filename, "aec_buf%d.dat", webrtc_aec_instance_count);
    aecpc->bufFile = fopen(filename, "wb");
    sprintf(filename, "aec_skew%d.dat", webrtc_aec_instance_count);
    aecpc->skewFile = fopen(filename, "wb");
    sprintf(filename, "aec_delay%d.dat", webrtc_aec_instance_count);
    aecpc->delayFile = fopen(filename, "wb");
    webrtc_aec_instance_count++;
  }
#endif

  return 0;
}

int32_t WebRtcAec_Free(void* aecInst) {
  aecpc_t* aecpc = aecInst;

  if (aecpc == NULL) {
    return -1;
  }

  WebRtc_FreeBuffer(aecpc->far_pre_buf);

#ifdef WEBRTC_AEC_DEBUG_DUMP
  fclose(aecpc->bufFile);
  fclose(aecpc->skewFile);
  fclose(aecpc->delayFile);
#endif

  WebRtcAec_FreeAec(aecpc->aec);
  WebRtcAec_FreeResampler(aecpc->resampler);
  free(aecpc);

  return 0;
}

int32_t WebRtcAec_Init(void* aecInst, int32_t sampFreq, int32_t scSampFreq) {
  aecpc_t* aecpc = aecInst;
  AecConfig aecConfig;

  if (sampFreq != 8000 && sampFreq != 16000 && sampFreq != 32000) {
    aecpc->lastError = AEC_BAD_PARAMETER_ERROR;
    return -1;
  }
  aecpc->sampFreq = sampFreq;

  if (scSampFreq < 1 || scSampFreq > 96000) {
    aecpc->lastError = AEC_BAD_PARAMETER_ERROR;
    return -1;
  }
  aecpc->scSampFreq = scSampFreq;

  // Initialize echo canceller core
  if (WebRtcAec_InitAec(aecpc->aec, aecpc->sampFreq) == -1) {
    aecpc->lastError = AEC_UNSPECIFIED_ERROR;
    return -1;
  }

  if (WebRtcAec_InitResampler(aecpc->resampler, aecpc->scSampFreq) == -1) {
    aecpc->lastError = AEC_UNSPECIFIED_ERROR;
    return -1;
  }

  if (WebRtc_InitBuffer(aecpc->far_pre_buf) == -1) {
    aecpc->lastError = AEC_UNSPECIFIED_ERROR;
    return -1;
  }
  WebRtc_MoveReadPtr(aecpc->far_pre_buf, -PART_LEN);  // Start overlap.

  aecpc->initFlag = initCheck;  // indicates that initialization has been done

  if (aecpc->sampFreq == 32000) {
    aecpc->splitSampFreq = 16000;
  } else {
    aecpc->splitSampFreq = sampFreq;
  }

  aecpc->delayCtr = 0;
  aecpc->sampFactor = (aecpc->scSampFreq * 1.0f) / aecpc->splitSampFreq;
  // Sampling frequency multiplier (SWB is processed as 160 frame size).
  aecpc->rate_factor = aecpc->splitSampFreq / 8000;

  aecpc->sum = 0;
  aecpc->counter = 0;
  aecpc->checkBuffSize = 1;
  aecpc->firstVal = 0;

  aecpc->startup_phase = WebRtcAec_reported_delay_enabled(aecpc->aec);
  aecpc->bufSizeStart = 0;
  aecpc->checkBufSizeCtr = 0;
  aecpc->msInSndCardBuf = 0;
  aecpc->filtDelay = -1;  // -1 indicates an initialized state.
  aecpc->timeForDelayChange = 0;
  aecpc->knownDelay = 0;
  aecpc->lastDelayDiff = 0;

  aecpc->skewFrCtr = 0;
  aecpc->resample = kAecFalse;
  aecpc->highSkewCtr = 0;
  aecpc->skew = 0;

  aecpc->farend_started = 0;

  // Default settings.
  aecConfig.nlpMode = kAecNlpModerate;
  aecConfig.skewMode = kAecFalse;
  aecConfig.metricsMode = kAecFalse;
  aecConfig.delay_logging = kAecFalse;

  if (WebRtcAec_set_config(aecpc, aecConfig) == -1) {
    aecpc->lastError = AEC_UNSPECIFIED_ERROR;
    return -1;
  }

  return 0;
}

// only buffer L band for farend
int32_t WebRtcAec_BufferFarend(void* aecInst,
                               const float* farend,
                               int16_t nrOfSamples) {
  aecpc_t* aecpc = aecInst;
  int newNrOfSamples = (int)nrOfSamples;
  float new_farend[MAX_RESAMP_LEN];
  const float* farend_ptr = farend;

  if (farend == NULL) {
    aecpc->lastError = AEC_NULL_POINTER_ERROR;
    return -1;
  }

  if (aecpc->initFlag != initCheck) {
    aecpc->lastError = AEC_UNINITIALIZED_ERROR;
    return -1;
  }

  // number of samples == 160 for SWB input
  if (nrOfSamples != 80 && nrOfSamples != 160) {
    aecpc->lastError = AEC_BAD_PARAMETER_ERROR;
    return -1;
  }

  if (aecpc->skewMode == kAecTrue && aecpc->resample == kAecTrue) {
    // Resample and get a new number of samples
    WebRtcAec_ResampleLinear(aecpc->resampler,
                             farend,
                             nrOfSamples,
                             aecpc->skew,
                             new_farend,
                             &newNrOfSamples);
    farend_ptr = new_farend;
  }

  aecpc->farend_started = 1;
  WebRtcAec_SetSystemDelay(aecpc->aec,
                           WebRtcAec_system_delay(aecpc->aec) + newNrOfSamples);

  // Write the time-domain data to |far_pre_buf|.
  WebRtc_WriteBuffer(aecpc->far_pre_buf, farend_ptr, (size_t)newNrOfSamples);

  // Transform to frequency domain if we have enough data.
  while (WebRtc_available_read(aecpc->far_pre_buf) >= PART_LEN2) {
    // We have enough data to pass to the FFT, hence read PART_LEN2 samples.
    {
      float* ptmp;
      float tmp[PART_LEN2];
      WebRtc_ReadBuffer(aecpc->far_pre_buf, (void**)&ptmp, tmp, PART_LEN2);
      WebRtcAec_BufferFarendPartition(aecpc->aec, ptmp);
#ifdef WEBRTC_AEC_DEBUG_DUMP
      WebRtc_WriteBuffer(
          WebRtcAec_far_time_buf(aecpc->aec), &ptmp[PART_LEN], 1);
#endif
    }

    // Rewind |far_pre_buf| PART_LEN samples for overlap before continuing.
    WebRtc_MoveReadPtr(aecpc->far_pre_buf, -PART_LEN);
  }

  return 0;
}

int32_t WebRtcAec_Process(void* aecInst,
                          const float* nearend,
                          const float* nearendH,
                          float* out,
                          float* outH,
                          int16_t nrOfSamples,
                          int16_t msInSndCardBuf,
                          int32_t skew) {
  aecpc_t* aecpc = aecInst;
  int32_t retVal = 0;
  if (nearend == NULL) {
    aecpc->lastError = AEC_NULL_POINTER_ERROR;
    return -1;
  }

  if (out == NULL) {
    aecpc->lastError = AEC_NULL_POINTER_ERROR;
    return -1;
  }

  if (aecpc->initFlag != initCheck) {
    aecpc->lastError = AEC_UNINITIALIZED_ERROR;
    return -1;
  }

  // number of samples == 160 for SWB input
  if (nrOfSamples != 80 && nrOfSamples != 160) {
    aecpc->lastError = AEC_BAD_PARAMETER_ERROR;
    return -1;
  }

  // Check for valid pointers based on sampling rate
  if (aecpc->sampFreq == 32000 && nearendH == NULL) {
    aecpc->lastError = AEC_NULL_POINTER_ERROR;
    return -1;
  }

  if (msInSndCardBuf < 0) {
    msInSndCardBuf = 0;
    aecpc->lastError = AEC_BAD_PARAMETER_WARNING;
    retVal = -1;
  } else if (msInSndCardBuf > kMaxTrustedDelayMs) {
    // The clamping is now done in ProcessExtended/Normal().
    aecpc->lastError = AEC_BAD_PARAMETER_WARNING;
    retVal = -1;
  }

  // This returns the value of aec->extended_filter_enabled.
  if (WebRtcAec_delay_correction_enabled(aecpc->aec)) {
    ProcessExtended(
        aecpc, nearend, nearendH, out, outH, nrOfSamples, msInSndCardBuf, skew);
  } else {
    if (ProcessNormal(aecpc,
                      nearend,
                      nearendH,
                      out,
                      outH,
                      nrOfSamples,
                      msInSndCardBuf,
                      skew) != 0) {
      retVal = -1;
    }
  }

#ifdef WEBRTC_AEC_DEBUG_DUMP
  {
    int16_t far_buf_size_ms = (int16_t)(WebRtcAec_system_delay(aecpc->aec) /
                                        (sampMsNb * aecpc->rate_factor));
    (void)fwrite(&far_buf_size_ms, 2, 1, aecpc->bufFile);
    (void)fwrite(
        &aecpc->knownDelay, sizeof(aecpc->knownDelay), 1, aecpc->delayFile);
  }
#endif

  return retVal;
}

int WebRtcAec_set_config(void* handle, AecConfig config) {
  aecpc_t* self = (aecpc_t*)handle;
  if (self->initFlag != initCheck) {
    self->lastError = AEC_UNINITIALIZED_ERROR;
    return -1;
  }

  if (config.skewMode != kAecFalse && config.skewMode != kAecTrue) {
    self->lastError = AEC_BAD_PARAMETER_ERROR;
    return -1;
  }
  self->skewMode = config.skewMode;

  if (config.nlpMode != kAecNlpConservative &&
      config.nlpMode != kAecNlpModerate &&
      config.nlpMode != kAecNlpAggressive) {
    self->lastError = AEC_BAD_PARAMETER_ERROR;
    return -1;
  }

  if (config.metricsMode != kAecFalse && config.metricsMode != kAecTrue) {
    self->lastError = AEC_BAD_PARAMETER_ERROR;
    return -1;
  }

  if (config.delay_logging != kAecFalse && config.delay_logging != kAecTrue) {
    self->lastError = AEC_BAD_PARAMETER_ERROR;
    return -1;
  }

  WebRtcAec_SetConfigCore(
      self->aec, config.nlpMode, config.metricsMode, config.delay_logging);
  return 0;
}

int WebRtcAec_get_echo_status(void* handle, int* status) {
  aecpc_t* self = (aecpc_t*)handle;
  if (status == NULL) {
    self->lastError = AEC_NULL_POINTER_ERROR;
    return -1;
  }
  if (self->initFlag != initCheck) {
    self->lastError = AEC_UNINITIALIZED_ERROR;
    return -1;
  }

  *status = WebRtcAec_echo_state(self->aec);

  return 0;
}

int WebRtcAec_GetMetrics(void* handle, AecMetrics* metrics) {
  const float kUpWeight = 0.7f;
  float dtmp;
  int stmp;
  aecpc_t* self = (aecpc_t*)handle;
  Stats erl;
  Stats erle;
  Stats a_nlp;

  if (handle == NULL) {
    return -1;
  }
  if (metrics == NULL) {
    self->lastError = AEC_NULL_POINTER_ERROR;
    return -1;
  }
  if (self->initFlag != initCheck) {
    self->lastError = AEC_UNINITIALIZED_ERROR;
    return -1;
  }

  WebRtcAec_GetEchoStats(self->aec, &erl, &erle, &a_nlp);

  // ERL
  metrics->erl.instant = (int)erl.instant;

  if ((erl.himean > kOffsetLevel) && (erl.average > kOffsetLevel)) {
    // Use a mix between regular average and upper part average.
    dtmp = kUpWeight * erl.himean + (1 - kUpWeight) * erl.average;
    metrics->erl.average = (int)dtmp;
  } else {
    metrics->erl.average = kOffsetLevel;
  }

  metrics->erl.max = (int)erl.max;

  if (erl.min < (kOffsetLevel * (-1))) {
    metrics->erl.min = (int)erl.min;
  } else {
    metrics->erl.min = kOffsetLevel;
  }

  // ERLE
  metrics->erle.instant = (int)erle.instant;

  if ((erle.himean > kOffsetLevel) && (erle.average > kOffsetLevel)) {
    // Use a mix between regular average and upper part average.
    dtmp = kUpWeight * erle.himean + (1 - kUpWeight) * erle.average;
    metrics->erle.average = (int)dtmp;
  } else {
    metrics->erle.average = kOffsetLevel;
  }

  metrics->erle.max = (int)erle.max;

  if (erle.min < (kOffsetLevel * (-1))) {
    metrics->erle.min = (int)erle.min;
  } else {
    metrics->erle.min = kOffsetLevel;
  }

  // RERL
  if ((metrics->erl.average > kOffsetLevel) &&
      (metrics->erle.average > kOffsetLevel)) {
    stmp = metrics->erl.average + metrics->erle.average;
  } else {
    stmp = kOffsetLevel;
  }
  metrics->rerl.average = stmp;

  // No other statistics needed, but returned for completeness.
  metrics->rerl.instant = stmp;
  metrics->rerl.max = stmp;
  metrics->rerl.min = stmp;

  // A_NLP
  metrics->aNlp.instant = (int)a_nlp.instant;

  if ((a_nlp.himean > kOffsetLevel) && (a_nlp.average > kOffsetLevel)) {
    // Use a mix between regular average and upper part average.
    dtmp = kUpWeight * a_nlp.himean + (1 - kUpWeight) * a_nlp.average;
    metrics->aNlp.average = (int)dtmp;
  } else {
    metrics->aNlp.average = kOffsetLevel;
  }

  metrics->aNlp.max = (int)a_nlp.max;

  if (a_nlp.min < (kOffsetLevel * (-1))) {
    metrics->aNlp.min = (int)a_nlp.min;
  } else {
    metrics->aNlp.min = kOffsetLevel;
  }

  return 0;
}

int WebRtcAec_GetDelayMetrics(void* handle, int* median, int* std) {
  aecpc_t* self = handle;
  if (median == NULL) {
    self->lastError = AEC_NULL_POINTER_ERROR;
    return -1;
  }
  if (std == NULL) {
    self->lastError = AEC_NULL_POINTER_ERROR;
    return -1;
  }
  if (self->initFlag != initCheck) {
    self->lastError = AEC_UNINITIALIZED_ERROR;
    return -1;
  }
  if (WebRtcAec_GetDelayMetricsCore(self->aec, median, std) == -1) {
    // Logging disabled.
    self->lastError = AEC_UNSUPPORTED_FUNCTION_ERROR;
    return -1;
  }

  return 0;
}

int32_t WebRtcAec_get_error_code(void* aecInst) {
  aecpc_t* aecpc = aecInst;
  return aecpc->lastError;
}

AecCore* WebRtcAec_aec_core(void* handle) {
  if (!handle) {
    return NULL;
  }
  return ((aecpc_t*)handle)->aec;
}

static int ProcessNormal(aecpc_t* aecpc,
                         const float* nearend,
                         const float* nearendH,
                         float* out,
                         float* outH,
                         int16_t nrOfSamples,
                         int16_t msInSndCardBuf,
                         int32_t skew) {
  int retVal = 0;
  short i;
  short nBlocks10ms;
  short nFrames;
  // Limit resampling to doubling/halving of signal
  const float minSkewEst = -0.5f;
  const float maxSkewEst = 1.0f;

  msInSndCardBuf =
      msInSndCardBuf > kMaxTrustedDelayMs ? kMaxTrustedDelayMs : msInSndCardBuf;
  // TODO(andrew): we need to investigate if this +10 is really wanted.
  msInSndCardBuf += 10;
  aecpc->msInSndCardBuf = msInSndCardBuf;

  if (aecpc->skewMode == kAecTrue) {
    if (aecpc->skewFrCtr < 25) {
      aecpc->skewFrCtr++;
    } else {
      retVal = WebRtcAec_GetSkew(aecpc->resampler, skew, &aecpc->skew);
      if (retVal == -1) {
        aecpc->skew = 0;
        aecpc->lastError = AEC_BAD_PARAMETER_WARNING;
      }

      aecpc->skew /= aecpc->sampFactor * nrOfSamples;

      if (aecpc->skew < 1.0e-3 && aecpc->skew > -1.0e-3) {
        aecpc->resample = kAecFalse;
      } else {
        aecpc->resample = kAecTrue;
      }

      if (aecpc->skew < minSkewEst) {
        aecpc->skew = minSkewEst;
      } else if (aecpc->skew > maxSkewEst) {
        aecpc->skew = maxSkewEst;
      }

#ifdef WEBRTC_AEC_DEBUG_DUMP
      (void)fwrite(&aecpc->skew, sizeof(aecpc->skew), 1, aecpc->skewFile);
#endif
    }
  }

  nFrames = nrOfSamples / FRAME_LEN;
  nBlocks10ms = nFrames / aecpc->rate_factor;

  if (aecpc->startup_phase) {
    // Only needed if they don't already point to the same place.
    if (nearend != out) {
      memcpy(out, nearend, sizeof(*out) * nrOfSamples);
    }
    if (nearendH != outH) {
      memcpy(outH, nearendH, sizeof(*outH) * nrOfSamples);
    }

    // The AEC is in the start up mode
    // AEC is disabled until the system delay is OK

    // Mechanism to ensure that the system delay is reasonably stable.
    if (aecpc->checkBuffSize) {
      aecpc->checkBufSizeCtr++;
      // Before we fill up the far-end buffer we require the system delay
      // to be stable (+/-8 ms) compared to the first value. This
      // comparison is made during the following 6 consecutive 10 ms
      // blocks. If it seems to be stable then we start to fill up the
      // far-end buffer.
      if (aecpc->counter == 0) {
        aecpc->firstVal = aecpc->msInSndCardBuf;
        aecpc->sum = 0;
      }

      if (abs(aecpc->firstVal - aecpc->msInSndCardBuf) <
          WEBRTC_SPL_MAX(0.2 * aecpc->msInSndCardBuf, sampMsNb)) {
        aecpc->sum += aecpc->msInSndCardBuf;
        aecpc->counter++;
      } else {
        aecpc->counter = 0;
      }

      if (aecpc->counter * nBlocks10ms >= 6) {
        // The far-end buffer size is determined in partitions of
        // PART_LEN samples. Use 75% of the average value of the system
        // delay as buffer size to start with.
        aecpc->bufSizeStart =
            WEBRTC_SPL_MIN((3 * aecpc->sum * aecpc->rate_factor * 8) /
                               (4 * aecpc->counter * PART_LEN),
                           kMaxBufSizeStart);
        // Buffer size has now been determined.
        aecpc->checkBuffSize = 0;
      }

      if (aecpc->checkBufSizeCtr * nBlocks10ms > 50) {
        // For really bad systems, don't disable the echo canceller for
        // more than 0.5 sec.
        aecpc->bufSizeStart = WEBRTC_SPL_MIN(
            (aecpc->msInSndCardBuf * aecpc->rate_factor * 3) / 40,
            kMaxBufSizeStart);
        aecpc->checkBuffSize = 0;
      }
    }

    // If |checkBuffSize| changed in the if-statement above.
    if (!aecpc->checkBuffSize) {
      // The system delay is now reasonably stable (or has been unstable
      // for too long). When the far-end buffer is filled with
      // approximately the same amount of data as reported by the system
      // we end the startup phase.
      int overhead_elements =
          WebRtcAec_system_delay(aecpc->aec) / PART_LEN - aecpc->bufSizeStart;
      if (overhead_elements == 0) {
        // Enable the AEC
        aecpc->startup_phase = 0;
      } else if (overhead_elements > 0) {
        // TODO(bjornv): Do we need a check on how much we actually
        // moved the read pointer? It should always be possible to move
        // the pointer |overhead_elements| since we have only added data
        // to the buffer and no delay compensation nor AEC processing
        // has been done.
        WebRtcAec_MoveFarReadPtr(aecpc->aec, overhead_elements);

        // Enable the AEC
        aecpc->startup_phase = 0;
      }
    }
  } else {
    // AEC is enabled.
    if (WebRtcAec_reported_delay_enabled(aecpc->aec)) {
      EstBufDelayNormal(aecpc);
    }

    // Note that 1 frame is supported for NB and 2 frames for WB.
    for (i = 0; i < nFrames; i++) {
      // Call the AEC.
      WebRtcAec_ProcessFrame(aecpc->aec,
                             &nearend[FRAME_LEN * i],
                             &nearendH[FRAME_LEN * i],
                             aecpc->knownDelay,
                             &out[FRAME_LEN * i],
                             &outH[FRAME_LEN * i]);
      // TODO(bjornv): Re-structure such that we don't have to pass
      // |aecpc->knownDelay| as input. Change name to something like
      // |system_buffer_diff|.
    }
  }

  return retVal;
}

static void ProcessExtended(aecpc_t* self,
                            const float* near,
                            const float* near_high,
                            float* out,
                            float* out_high,
                            int16_t num_samples,
                            int16_t reported_delay_ms,
                            int32_t skew) {
  int i;
  const int num_frames = num_samples / FRAME_LEN;
  const int delay_diff_offset = kDelayDiffOffsetSamples;
#if defined(WEBRTC_UNTRUSTED_DELAY)
  reported_delay_ms = kFixedDelayMs;
#else
  // This is the usual mode where we trust the reported system delay values.
  // Due to the longer filter, we no longer add 10 ms to the reported delay
  // to reduce chance of non-causality. Instead we apply a minimum here to avoid
  // issues with the read pointer jumping around needlessly.
  reported_delay_ms = reported_delay_ms < kMinTrustedDelayMs
                          ? kMinTrustedDelayMs
                          : reported_delay_ms;
  // If the reported delay appears to be bogus, we attempt to recover by using
  // the measured fixed delay values. We use >= here because higher layers
  // may already clamp to this maximum value, and we would otherwise not
  // detect it here.
  reported_delay_ms = reported_delay_ms >= kMaxTrustedDelayMs
                          ? kFixedDelayMs
                          : reported_delay_ms;
#endif
  self->msInSndCardBuf = reported_delay_ms;

  if (!self->farend_started) {
    // Only needed if they don't already point to the same place.
    if (near != out) {
      memcpy(out, near, sizeof(*out) * num_samples);
    }
    if (near_high != out_high) {
      memcpy(out_high, near_high, sizeof(*out_high) * num_samples);
    }
    return;
  }
  if (self->startup_phase) {
    // In the extended mode, there isn't a startup "phase", just a special
    // action on the first frame. In the trusted delay case, we'll take the
    // current reported delay, unless it's less then our conservative
    // measurement.
    int startup_size_ms =
        reported_delay_ms < kFixedDelayMs ? kFixedDelayMs : reported_delay_ms;
    int overhead_elements = (WebRtcAec_system_delay(self->aec) -
                             startup_size_ms / 2 * self->rate_factor * 8) /
                            PART_LEN;
    WebRtcAec_MoveFarReadPtr(self->aec, overhead_elements);
    self->startup_phase = 0;
  }

  if (WebRtcAec_reported_delay_enabled(self->aec)) {
    EstBufDelayExtended(self);
  }

  {
    // |delay_diff_offset| gives us the option to manually rewind the delay on
    // very low delay platforms which can't be expressed purely through
    // |reported_delay_ms|.
    const int adjusted_known_delay =
        WEBRTC_SPL_MAX(0, self->knownDelay + delay_diff_offset);

    for (i = 0; i < num_frames; ++i) {
      WebRtcAec_ProcessFrame(self->aec,
                             &near[FRAME_LEN * i],
                             &near_high[FRAME_LEN * i],
                             adjusted_known_delay,
                             &out[FRAME_LEN * i],
                             &out_high[FRAME_LEN * i]);
    }
  }
}

static void EstBufDelayNormal(aecpc_t* aecpc) {
  int nSampSndCard = aecpc->msInSndCardBuf * sampMsNb * aecpc->rate_factor;
  int current_delay = nSampSndCard - WebRtcAec_system_delay(aecpc->aec);
  int delay_difference = 0;

  // Before we proceed with the delay estimate filtering we:
  // 1) Compensate for the frame that will be read.
  // 2) Compensate for drift resampling.
  // 3) Compensate for non-causality if needed, since the estimated delay can't
  //    be negative.

  // 1) Compensating for the frame(s) that will be read/processed.
  current_delay += FRAME_LEN * aecpc->rate_factor;

  // 2) Account for resampling frame delay.
  if (aecpc->skewMode == kAecTrue && aecpc->resample == kAecTrue) {
    current_delay -= kResamplingDelay;
  }

  // 3) Compensate for non-causality, if needed, by flushing one block.
  if (current_delay < PART_LEN) {
    current_delay += WebRtcAec_MoveFarReadPtr(aecpc->aec, 1) * PART_LEN;
  }

  // We use -1 to signal an initialized state in the "extended" implementation;
  // compensate for that.
  aecpc->filtDelay = aecpc->filtDelay < 0 ? 0 : aecpc->filtDelay;
  aecpc->filtDelay =
      WEBRTC_SPL_MAX(0, (short)(0.8 * aecpc->filtDelay + 0.2 * current_delay));

  delay_difference = aecpc->filtDelay - aecpc->knownDelay;
  if (delay_difference > 224) {
    if (aecpc->lastDelayDiff < 96) {
      aecpc->timeForDelayChange = 0;
    } else {
      aecpc->timeForDelayChange++;
    }
  } else if (delay_difference < 96 && aecpc->knownDelay > 0) {
    if (aecpc->lastDelayDiff > 224) {
      aecpc->timeForDelayChange = 0;
    } else {
      aecpc->timeForDelayChange++;
    }
  } else {
    aecpc->timeForDelayChange = 0;
  }
  aecpc->lastDelayDiff = delay_difference;

  if (aecpc->timeForDelayChange > 25) {
    aecpc->knownDelay = WEBRTC_SPL_MAX((int)aecpc->filtDelay - 160, 0);
  }
}

static void EstBufDelayExtended(aecpc_t* self) {
  int reported_delay = self->msInSndCardBuf * sampMsNb * self->rate_factor;
  int current_delay = reported_delay - WebRtcAec_system_delay(self->aec);
  int delay_difference = 0;

  // Before we proceed with the delay estimate filtering we:
  // 1) Compensate for the frame that will be read.
  // 2) Compensate for drift resampling.
  // 3) Compensate for non-causality if needed, since the estimated delay can't
  //    be negative.

  // 1) Compensating for the frame(s) that will be read/processed.
  current_delay += FRAME_LEN * self->rate_factor;

  // 2) Account for resampling frame delay.
  if (self->skewMode == kAecTrue && self->resample == kAecTrue) {
    current_delay -= kResamplingDelay;
  }

  // 3) Compensate for non-causality, if needed, by flushing two blocks.
  if (current_delay < PART_LEN) {
    current_delay += WebRtcAec_MoveFarReadPtr(self->aec, 2) * PART_LEN;
  }

  if (self->filtDelay == -1) {
    self->filtDelay = WEBRTC_SPL_MAX(0, 0.5 * current_delay);
  } else {
    self->filtDelay = WEBRTC_SPL_MAX(
        0, (short)(0.95 * self->filtDelay + 0.05 * current_delay));
  }

  delay_difference = self->filtDelay - self->knownDelay;
  if (delay_difference > 384) {
    if (self->lastDelayDiff < 128) {
      self->timeForDelayChange = 0;
    } else {
      self->timeForDelayChange++;
    }
  } else if (delay_difference < 128 && self->knownDelay > 0) {
    if (self->lastDelayDiff > 384) {
      self->timeForDelayChange = 0;
    } else {
      self->timeForDelayChange++;
    }
  } else {
    self->timeForDelayChange = 0;
  }
  self->lastDelayDiff = delay_difference;

  if (self->timeForDelayChange > 25) {
    self->knownDelay = WEBRTC_SPL_MAX((int)self->filtDelay - 256, 0);
  }
}
