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
 * The core AEC algorithm, which is presented with time-aligned signals.
 */

#include "webrtc/modules/audio_processing/aec/aec_core.h"

#ifdef WEBRTC_AEC_DEBUG_DUMP
#include <stdio.h>
#endif

#include <assert.h>
#include <math.h>
#include <stddef.h>  // size_t
#include <stdlib.h>
#include <string.h>

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_processing/aec/aec_common.h"
#include "webrtc/modules/audio_processing/aec/aec_core_internal.h"
#include "webrtc/modules/audio_processing/aec/aec_rdft.h"
#include "webrtc/modules/audio_processing/utility/delay_estimator_wrapper.h"
#include "webrtc/modules/audio_processing/utility/ring_buffer.h"
#include "webrtc/system_wrappers/interface/cpu_features_wrapper.h"
#include "webrtc/typedefs.h"

// Buffer size (samples)
static const size_t kBufSizePartitions = 250;  // 1 second of audio in 16 kHz.

// Metrics
static const int subCountLen = 4;
static const int countLen = 50;

// Quantities to control H band scaling for SWB input
static const int flagHbandCn = 1;  // flag for adding comfort noise in H band
static const float cnScaleHband =
    (float)0.4;  // scale for comfort noise in H band
// Initial bin for averaging nlp gain in low band
static const int freqAvgIc = PART_LEN / 2;

// Matlab code to produce table:
// win = sqrt(hanning(63)); win = [0 ; win(1:32)];
// fprintf(1, '\t%.14f, %.14f, %.14f,\n', win);
ALIGN16_BEG const float ALIGN16_END WebRtcAec_sqrtHanning[65] = {
    0.00000000000000f, 0.02454122852291f, 0.04906767432742f, 0.07356456359967f,
    0.09801714032956f, 0.12241067519922f, 0.14673047445536f, 0.17096188876030f,
    0.19509032201613f, 0.21910124015687f, 0.24298017990326f, 0.26671275747490f,
    0.29028467725446f, 0.31368174039889f, 0.33688985339222f, 0.35989503653499f,
    0.38268343236509f, 0.40524131400499f, 0.42755509343028f, 0.44961132965461f,
    0.47139673682600f, 0.49289819222978f, 0.51410274419322f, 0.53499761988710f,
    0.55557023301960f, 0.57580819141785f, 0.59569930449243f, 0.61523159058063f,
    0.63439328416365f, 0.65317284295378f, 0.67155895484702f, 0.68954054473707f,
    0.70710678118655f, 0.72424708295147f, 0.74095112535496f, 0.75720884650648f,
    0.77301045336274f, 0.78834642762661f, 0.80320753148064f, 0.81758481315158f,
    0.83146961230255f, 0.84485356524971f, 0.85772861000027f, 0.87008699110871f,
    0.88192126434835f, 0.89322430119552f, 0.90398929312344f, 0.91420975570353f,
    0.92387953251129f, 0.93299279883474f, 0.94154406518302f, 0.94952818059304f,
    0.95694033573221f, 0.96377606579544f, 0.97003125319454f, 0.97570213003853f,
    0.98078528040323f, 0.98527764238894f, 0.98917650996478f, 0.99247953459871f,
    0.99518472667220f, 0.99729045667869f, 0.99879545620517f, 0.99969881869620f,
    1.00000000000000f};

// Matlab code to produce table:
// weightCurve = [0 ; 0.3 * sqrt(linspace(0,1,64))' + 0.1];
// fprintf(1, '\t%.4f, %.4f, %.4f, %.4f, %.4f, %.4f,\n', weightCurve);
ALIGN16_BEG const float ALIGN16_END WebRtcAec_weightCurve[65] = {
    0.0000f, 0.1000f, 0.1378f, 0.1535f, 0.1655f, 0.1756f, 0.1845f, 0.1926f,
    0.2000f, 0.2069f, 0.2134f, 0.2195f, 0.2254f, 0.2309f, 0.2363f, 0.2414f,
    0.2464f, 0.2512f, 0.2558f, 0.2604f, 0.2648f, 0.2690f, 0.2732f, 0.2773f,
    0.2813f, 0.2852f, 0.2890f, 0.2927f, 0.2964f, 0.3000f, 0.3035f, 0.3070f,
    0.3104f, 0.3138f, 0.3171f, 0.3204f, 0.3236f, 0.3268f, 0.3299f, 0.3330f,
    0.3360f, 0.3390f, 0.3420f, 0.3449f, 0.3478f, 0.3507f, 0.3535f, 0.3563f,
    0.3591f, 0.3619f, 0.3646f, 0.3673f, 0.3699f, 0.3726f, 0.3752f, 0.3777f,
    0.3803f, 0.3828f, 0.3854f, 0.3878f, 0.3903f, 0.3928f, 0.3952f, 0.3976f,
    0.4000f};

// Matlab code to produce table:
// overDriveCurve = [sqrt(linspace(0,1,65))' + 1];
// fprintf(1, '\t%.4f, %.4f, %.4f, %.4f, %.4f, %.4f,\n', overDriveCurve);
ALIGN16_BEG const float ALIGN16_END WebRtcAec_overDriveCurve[65] = {
    1.0000f, 1.1250f, 1.1768f, 1.2165f, 1.2500f, 1.2795f, 1.3062f, 1.3307f,
    1.3536f, 1.3750f, 1.3953f, 1.4146f, 1.4330f, 1.4507f, 1.4677f, 1.4841f,
    1.5000f, 1.5154f, 1.5303f, 1.5449f, 1.5590f, 1.5728f, 1.5863f, 1.5995f,
    1.6124f, 1.6250f, 1.6374f, 1.6495f, 1.6614f, 1.6731f, 1.6847f, 1.6960f,
    1.7071f, 1.7181f, 1.7289f, 1.7395f, 1.7500f, 1.7603f, 1.7706f, 1.7806f,
    1.7906f, 1.8004f, 1.8101f, 1.8197f, 1.8292f, 1.8385f, 1.8478f, 1.8570f,
    1.8660f, 1.8750f, 1.8839f, 1.8927f, 1.9014f, 1.9100f, 1.9186f, 1.9270f,
    1.9354f, 1.9437f, 1.9520f, 1.9601f, 1.9682f, 1.9763f, 1.9843f, 1.9922f,
    2.0000f};

// Target suppression levels for nlp modes.
// log{0.001, 0.00001, 0.00000001}
static const float kTargetSupp[3] = {-6.9f, -11.5f, -18.4f};

// Two sets of parameters, one for the extended filter mode.
static const float kExtendedMinOverDrive[3] = {3.0f, 6.0f, 15.0f};
static const float kNormalMinOverDrive[3] = {1.0f, 2.0f, 5.0f};
const float WebRtcAec_kExtendedSmoothingCoefficients[2][2] = {{0.9f, 0.1f},
                                                              {0.92f, 0.08f}};
const float WebRtcAec_kNormalSmoothingCoefficients[2][2] = {{0.9f, 0.1f},
                                                            {0.93f, 0.07f}};

// Number of partitions forming the NLP's "preferred" bands.
enum {
  kPrefBandSize = 24
};

#ifdef WEBRTC_AEC_DEBUG_DUMP
extern int webrtc_aec_instance_count;
#endif

WebRtcAec_FilterFar_t WebRtcAec_FilterFar;
WebRtcAec_ScaleErrorSignal_t WebRtcAec_ScaleErrorSignal;
WebRtcAec_FilterAdaptation_t WebRtcAec_FilterAdaptation;
WebRtcAec_OverdriveAndSuppress_t WebRtcAec_OverdriveAndSuppress;
WebRtcAec_ComfortNoise_t WebRtcAec_ComfortNoise;
WebRtcAec_SubbandCoherence_t WebRtcAec_SubbandCoherence;

__inline static float MulRe(float aRe, float aIm, float bRe, float bIm) {
  return aRe * bRe - aIm * bIm;
}

__inline static float MulIm(float aRe, float aIm, float bRe, float bIm) {
  return aRe * bIm + aIm * bRe;
}

static int CmpFloat(const void* a, const void* b) {
  const float* da = (const float*)a;
  const float* db = (const float*)b;

  return (*da > *db) - (*da < *db);
}

static void FilterFar(AecCore* aec, float yf[2][PART_LEN1]) {
  int i;
  for (i = 0; i < aec->num_partitions; i++) {
    int j;
    int xPos = (i + aec->xfBufBlockPos) * PART_LEN1;
    int pos = i * PART_LEN1;
    // Check for wrap
    if (i + aec->xfBufBlockPos >= aec->num_partitions) {
      xPos -= aec->num_partitions * (PART_LEN1);
    }

    for (j = 0; j < PART_LEN1; j++) {
      yf[0][j] += MulRe(aec->xfBuf[0][xPos + j],
                        aec->xfBuf[1][xPos + j],
                        aec->wfBuf[0][pos + j],
                        aec->wfBuf[1][pos + j]);
      yf[1][j] += MulIm(aec->xfBuf[0][xPos + j],
                        aec->xfBuf[1][xPos + j],
                        aec->wfBuf[0][pos + j],
                        aec->wfBuf[1][pos + j]);
    }
  }
}

static void ScaleErrorSignal(AecCore* aec, float ef[2][PART_LEN1]) {
  const float mu = aec->extended_filter_enabled ? kExtendedMu : aec->normal_mu;
  const float error_threshold = aec->extended_filter_enabled
                                    ? kExtendedErrorThreshold
                                    : aec->normal_error_threshold;
  int i;
  float abs_ef;
  for (i = 0; i < (PART_LEN1); i++) {
    ef[0][i] /= (aec->xPow[i] + 1e-10f);
    ef[1][i] /= (aec->xPow[i] + 1e-10f);
    abs_ef = sqrtf(ef[0][i] * ef[0][i] + ef[1][i] * ef[1][i]);

    if (abs_ef > error_threshold) {
      abs_ef = error_threshold / (abs_ef + 1e-10f);
      ef[0][i] *= abs_ef;
      ef[1][i] *= abs_ef;
    }

    // Stepsize factor
    ef[0][i] *= mu;
    ef[1][i] *= mu;
  }
}

// Time-unconstrined filter adaptation.
// TODO(andrew): consider for a low-complexity mode.
// static void FilterAdaptationUnconstrained(AecCore* aec, float *fft,
//                                          float ef[2][PART_LEN1]) {
//  int i, j;
//  for (i = 0; i < aec->num_partitions; i++) {
//    int xPos = (i + aec->xfBufBlockPos)*(PART_LEN1);
//    int pos;
//    // Check for wrap
//    if (i + aec->xfBufBlockPos >= aec->num_partitions) {
//      xPos -= aec->num_partitions * PART_LEN1;
//    }
//
//    pos = i * PART_LEN1;
//
//    for (j = 0; j < PART_LEN1; j++) {
//      aec->wfBuf[0][pos + j] += MulRe(aec->xfBuf[0][xPos + j],
//                                      -aec->xfBuf[1][xPos + j],
//                                      ef[0][j], ef[1][j]);
//      aec->wfBuf[1][pos + j] += MulIm(aec->xfBuf[0][xPos + j],
//                                      -aec->xfBuf[1][xPos + j],
//                                      ef[0][j], ef[1][j]);
//    }
//  }
//}

static void FilterAdaptation(AecCore* aec, float* fft, float ef[2][PART_LEN1]) {
  int i, j;
  for (i = 0; i < aec->num_partitions; i++) {
    int xPos = (i + aec->xfBufBlockPos) * (PART_LEN1);
    int pos;
    // Check for wrap
    if (i + aec->xfBufBlockPos >= aec->num_partitions) {
      xPos -= aec->num_partitions * PART_LEN1;
    }

    pos = i * PART_LEN1;

    for (j = 0; j < PART_LEN; j++) {

      fft[2 * j] = MulRe(aec->xfBuf[0][xPos + j],
                         -aec->xfBuf[1][xPos + j],
                         ef[0][j],
                         ef[1][j]);
      fft[2 * j + 1] = MulIm(aec->xfBuf[0][xPos + j],
                             -aec->xfBuf[1][xPos + j],
                             ef[0][j],
                             ef[1][j]);
    }
    fft[1] = MulRe(aec->xfBuf[0][xPos + PART_LEN],
                   -aec->xfBuf[1][xPos + PART_LEN],
                   ef[0][PART_LEN],
                   ef[1][PART_LEN]);

    aec_rdft_inverse_128(fft);
    memset(fft + PART_LEN, 0, sizeof(float) * PART_LEN);

    // fft scaling
    {
      float scale = 2.0f / PART_LEN2;
      for (j = 0; j < PART_LEN; j++) {
        fft[j] *= scale;
      }
    }
    aec_rdft_forward_128(fft);

    aec->wfBuf[0][pos] += fft[0];
    aec->wfBuf[0][pos + PART_LEN] += fft[1];

    for (j = 1; j < PART_LEN; j++) {
      aec->wfBuf[0][pos + j] += fft[2 * j];
      aec->wfBuf[1][pos + j] += fft[2 * j + 1];
    }
  }
}

static void OverdriveAndSuppress(AecCore* aec,
                                 float hNl[PART_LEN1],
                                 const float hNlFb,
                                 float efw[2][PART_LEN1]) {
  int i;
  for (i = 0; i < PART_LEN1; i++) {
    // Weight subbands
    if (hNl[i] > hNlFb) {
      hNl[i] = WebRtcAec_weightCurve[i] * hNlFb +
               (1 - WebRtcAec_weightCurve[i]) * hNl[i];
    }
    hNl[i] = powf(hNl[i], aec->overDriveSm * WebRtcAec_overDriveCurve[i]);

    // Suppress error signal
    efw[0][i] *= hNl[i];
    efw[1][i] *= hNl[i];

    // Ooura fft returns incorrect sign on imaginary component. It matters here
    // because we are making an additive change with comfort noise.
    efw[1][i] *= -1;
  }
}

static int PartitionDelay(const AecCore* aec) {
  // Measures the energy in each filter partition and returns the partition with
  // highest energy.
  // TODO(bjornv): Spread computational cost by computing one partition per
  // block?
  float wfEnMax = 0;
  int i;
  int delay = 0;

  for (i = 0; i < aec->num_partitions; i++) {
    int j;
    int pos = i * PART_LEN1;
    float wfEn = 0;
    for (j = 0; j < PART_LEN1; j++) {
      wfEn += aec->wfBuf[0][pos + j] * aec->wfBuf[0][pos + j] +
          aec->wfBuf[1][pos + j] * aec->wfBuf[1][pos + j];
    }

    if (wfEn > wfEnMax) {
      wfEnMax = wfEn;
      delay = i;
    }
  }
  return delay;
}

// Threshold to protect against the ill-effects of a zero far-end.
const float WebRtcAec_kMinFarendPSD = 15;

// Updates the following smoothed  Power Spectral Densities (PSD):
//  - sd  : near-end
//  - se  : residual echo
//  - sx  : far-end
//  - sde : cross-PSD of near-end and residual echo
//  - sxd : cross-PSD of near-end and far-end
//
// In addition to updating the PSDs, also the filter diverge state is determined
// upon actions are taken.
static void SmoothedPSD(AecCore* aec,
                        float efw[2][PART_LEN1],
                        float dfw[2][PART_LEN1],
                        float xfw[2][PART_LEN1]) {
  // Power estimate smoothing coefficients.
  const float* ptrGCoh = aec->extended_filter_enabled
      ? WebRtcAec_kExtendedSmoothingCoefficients[aec->mult - 1]
      : WebRtcAec_kNormalSmoothingCoefficients[aec->mult - 1];
  int i;
  float sdSum = 0, seSum = 0;

  for (i = 0; i < PART_LEN1; i++) {
    aec->sd[i] = ptrGCoh[0] * aec->sd[i] +
                 ptrGCoh[1] * (dfw[0][i] * dfw[0][i] + dfw[1][i] * dfw[1][i]);
    aec->se[i] = ptrGCoh[0] * aec->se[i] +
                 ptrGCoh[1] * (efw[0][i] * efw[0][i] + efw[1][i] * efw[1][i]);
    // We threshold here to protect against the ill-effects of a zero farend.
    // The threshold is not arbitrarily chosen, but balances protection and
    // adverse interaction with the algorithm's tuning.
    // TODO(bjornv): investigate further why this is so sensitive.
    aec->sx[i] =
        ptrGCoh[0] * aec->sx[i] +
        ptrGCoh[1] * WEBRTC_SPL_MAX(
            xfw[0][i] * xfw[0][i] + xfw[1][i] * xfw[1][i],
            WebRtcAec_kMinFarendPSD);

    aec->sde[i][0] =
        ptrGCoh[0] * aec->sde[i][0] +
        ptrGCoh[1] * (dfw[0][i] * efw[0][i] + dfw[1][i] * efw[1][i]);
    aec->sde[i][1] =
        ptrGCoh[0] * aec->sde[i][1] +
        ptrGCoh[1] * (dfw[0][i] * efw[1][i] - dfw[1][i] * efw[0][i]);

    aec->sxd[i][0] =
        ptrGCoh[0] * aec->sxd[i][0] +
        ptrGCoh[1] * (dfw[0][i] * xfw[0][i] + dfw[1][i] * xfw[1][i]);
    aec->sxd[i][1] =
        ptrGCoh[0] * aec->sxd[i][1] +
        ptrGCoh[1] * (dfw[0][i] * xfw[1][i] - dfw[1][i] * xfw[0][i]);

    sdSum += aec->sd[i];
    seSum += aec->se[i];
  }

  // Divergent filter safeguard.
  aec->divergeState = (aec->divergeState ? 1.05f : 1.0f) * seSum > sdSum;

  if (aec->divergeState)
    memcpy(efw, dfw, sizeof(efw[0][0]) * 2 * PART_LEN1);

  // Reset if error is significantly larger than nearend (13 dB).
  if (!aec->extended_filter_enabled && seSum > (19.95f * sdSum))
    memset(aec->wfBuf, 0, sizeof(aec->wfBuf));
}

// Window time domain data to be used by the fft.
__inline static void WindowData(float* x_windowed, const float* x) {
  int i;
  for (i = 0; i < PART_LEN; i++) {
    x_windowed[i] = x[i] * WebRtcAec_sqrtHanning[i];
    x_windowed[PART_LEN + i] =
        x[PART_LEN + i] * WebRtcAec_sqrtHanning[PART_LEN - i];
  }
}

// Puts fft output data into a complex valued array.
__inline static void StoreAsComplex(const float* data,
                                    float data_complex[2][PART_LEN1]) {
  int i;
  data_complex[0][0] = data[0];
  data_complex[1][0] = 0;
  for (i = 1; i < PART_LEN; i++) {
    data_complex[0][i] = data[2 * i];
    data_complex[1][i] = data[2 * i + 1];
  }
  data_complex[0][PART_LEN] = data[1];
  data_complex[1][PART_LEN] = 0;
}

static void SubbandCoherence(AecCore* aec,
                             float efw[2][PART_LEN1],
                             float xfw[2][PART_LEN1],
                             float* fft,
                             float* cohde,
                             float* cohxd) {
  float dfw[2][PART_LEN1];
  int i;

  if (aec->delayEstCtr == 0)
    aec->delayIdx = PartitionDelay(aec);

  // Use delayed far.
  memcpy(xfw,
         aec->xfwBuf + aec->delayIdx * PART_LEN1,
         sizeof(xfw[0][0]) * 2 * PART_LEN1);

  // Windowed near fft
  WindowData(fft, aec->dBuf);
  aec_rdft_forward_128(fft);
  StoreAsComplex(fft, dfw);

  // Windowed error fft
  WindowData(fft, aec->eBuf);
  aec_rdft_forward_128(fft);
  StoreAsComplex(fft, efw);

  SmoothedPSD(aec, efw, dfw, xfw);

  // Subband coherence
  for (i = 0; i < PART_LEN1; i++) {
    cohde[i] =
        (aec->sde[i][0] * aec->sde[i][0] + aec->sde[i][1] * aec->sde[i][1]) /
        (aec->sd[i] * aec->se[i] + 1e-10f);
    cohxd[i] =
        (aec->sxd[i][0] * aec->sxd[i][0] + aec->sxd[i][1] * aec->sxd[i][1]) /
        (aec->sx[i] * aec->sd[i] + 1e-10f);
  }
}

static void GetHighbandGain(const float* lambda, float* nlpGainHband) {
  int i;

  nlpGainHband[0] = (float)0.0;
  for (i = freqAvgIc; i < PART_LEN1 - 1; i++) {
    nlpGainHband[0] += lambda[i];
  }
  nlpGainHband[0] /= (float)(PART_LEN1 - 1 - freqAvgIc);
}

static void ComfortNoise(AecCore* aec,
                         float efw[2][PART_LEN1],
                         complex_t* comfortNoiseHband,
                         const float* noisePow,
                         const float* lambda) {
  int i, num;
  float rand[PART_LEN];
  float noise, noiseAvg, tmp, tmpAvg;
  int16_t randW16[PART_LEN];
  complex_t u[PART_LEN1];

  const float pi2 = 6.28318530717959f;

  // Generate a uniform random array on [0 1]
  WebRtcSpl_RandUArray(randW16, PART_LEN, &aec->seed);
  for (i = 0; i < PART_LEN; i++) {
    rand[i] = ((float)randW16[i]) / 32768;
  }

  // Reject LF noise
  u[0][0] = 0;
  u[0][1] = 0;
  for (i = 1; i < PART_LEN1; i++) {
    tmp = pi2 * rand[i - 1];

    noise = sqrtf(noisePow[i]);
    u[i][0] = noise * cosf(tmp);
    u[i][1] = -noise * sinf(tmp);
  }
  u[PART_LEN][1] = 0;

  for (i = 0; i < PART_LEN1; i++) {
    // This is the proper weighting to match the background noise power
    tmp = sqrtf(WEBRTC_SPL_MAX(1 - lambda[i] * lambda[i], 0));
    // tmp = 1 - lambda[i];
    efw[0][i] += tmp * u[i][0];
    efw[1][i] += tmp * u[i][1];
  }

  // For H band comfort noise
  // TODO: don't compute noise and "tmp" twice. Use the previous results.
  noiseAvg = 0.0;
  tmpAvg = 0.0;
  num = 0;
  if (aec->sampFreq == 32000 && flagHbandCn == 1) {

    // average noise scale
    // average over second half of freq spectrum (i.e., 4->8khz)
    // TODO: we shouldn't need num. We know how many elements we're summing.
    for (i = PART_LEN1 >> 1; i < PART_LEN1; i++) {
      num++;
      noiseAvg += sqrtf(noisePow[i]);
    }
    noiseAvg /= (float)num;

    // average nlp scale
    // average over second half of freq spectrum (i.e., 4->8khz)
    // TODO: we shouldn't need num. We know how many elements we're summing.
    num = 0;
    for (i = PART_LEN1 >> 1; i < PART_LEN1; i++) {
      num++;
      tmpAvg += sqrtf(WEBRTC_SPL_MAX(1 - lambda[i] * lambda[i], 0));
    }
    tmpAvg /= (float)num;

    // Use average noise for H band
    // TODO: we should probably have a new random vector here.
    // Reject LF noise
    u[0][0] = 0;
    u[0][1] = 0;
    for (i = 1; i < PART_LEN1; i++) {
      tmp = pi2 * rand[i - 1];

      // Use average noise for H band
      u[i][0] = noiseAvg * (float)cos(tmp);
      u[i][1] = -noiseAvg * (float)sin(tmp);
    }
    u[PART_LEN][1] = 0;

    for (i = 0; i < PART_LEN1; i++) {
      // Use average NLP weight for H band
      comfortNoiseHband[i][0] = tmpAvg * u[i][0];
      comfortNoiseHband[i][1] = tmpAvg * u[i][1];
    }
  }
}

static void InitLevel(PowerLevel* level) {
  const float kBigFloat = 1E17f;

  level->averagelevel = 0;
  level->framelevel = 0;
  level->minlevel = kBigFloat;
  level->frsum = 0;
  level->sfrsum = 0;
  level->frcounter = 0;
  level->sfrcounter = 0;
}

static void InitStats(Stats* stats) {
  stats->instant = kOffsetLevel;
  stats->average = kOffsetLevel;
  stats->max = kOffsetLevel;
  stats->min = kOffsetLevel * (-1);
  stats->sum = 0;
  stats->hisum = 0;
  stats->himean = kOffsetLevel;
  stats->counter = 0;
  stats->hicounter = 0;
}

static void InitMetrics(AecCore* self) {
  self->stateCounter = 0;
  InitLevel(&self->farlevel);
  InitLevel(&self->nearlevel);
  InitLevel(&self->linoutlevel);
  InitLevel(&self->nlpoutlevel);

  InitStats(&self->erl);
  InitStats(&self->erle);
  InitStats(&self->aNlp);
  InitStats(&self->rerl);
}

static void UpdateLevel(PowerLevel* level, float in[2][PART_LEN1]) {
  // Do the energy calculation in the frequency domain. The FFT is performed on
  // a segment of PART_LEN2 samples due to overlap, but we only want the energy
  // of half that data (the last PART_LEN samples). Parseval's relation states
  // that the energy is preserved according to
  //
  // \sum_{n=0}^{N-1} |x(n)|^2 = 1/N * \sum_{n=0}^{N-1} |X(n)|^2
  //                           = ENERGY,
  //
  // where N = PART_LEN2. Since we are only interested in calculating the energy
  // for the last PART_LEN samples we approximate by calculating ENERGY and
  // divide by 2,
  //
  // \sum_{n=N/2}^{N-1} |x(n)|^2 ~= ENERGY / 2
  //
  // Since we deal with real valued time domain signals we only store frequency
  // bins [0, PART_LEN], which is what |in| consists of. To calculate ENERGY we
  // need to add the contribution from the missing part in
  // [PART_LEN+1, PART_LEN2-1]. These values are, up to a phase shift, identical
  // with the values in [1, PART_LEN-1], hence multiply those values by 2. This
  // is the values in the for loop below, but multiplication by 2 and division
  // by 2 cancel.

  // TODO(bjornv): Investigate reusing energy calculations performed at other
  // places in the code.
  int k = 1;
  // Imaginary parts are zero at end points and left out of the calculation.
  float energy = (in[0][0] * in[0][0]) / 2;
  energy += (in[0][PART_LEN] * in[0][PART_LEN]) / 2;

  for (k = 1; k < PART_LEN; k++) {
    energy += (in[0][k] * in[0][k] + in[1][k] * in[1][k]);
  }
  energy /= PART_LEN2;

  level->sfrsum += energy;
  level->sfrcounter++;

  if (level->sfrcounter > subCountLen) {
    level->framelevel = level->sfrsum / (subCountLen * PART_LEN);
    level->sfrsum = 0;
    level->sfrcounter = 0;
    if (level->framelevel > 0) {
      if (level->framelevel < level->minlevel) {
        level->minlevel = level->framelevel;  // New minimum.
      } else {
        level->minlevel *= (1 + 0.001f);  // Small increase.
      }
    }
    level->frcounter++;
    level->frsum += level->framelevel;
    if (level->frcounter > countLen) {
      level->averagelevel = level->frsum / countLen;
      level->frsum = 0;
      level->frcounter = 0;
    }
  }
}

static void UpdateMetrics(AecCore* aec) {
  float dtmp, dtmp2;

  const float actThresholdNoisy = 8.0f;
  const float actThresholdClean = 40.0f;
  const float safety = 0.99995f;
  const float noisyPower = 300000.0f;

  float actThreshold;
  float echo, suppressedEcho;

  if (aec->echoState) {  // Check if echo is likely present
    aec->stateCounter++;
  }

  if (aec->farlevel.frcounter == 0) {

    if (aec->farlevel.minlevel < noisyPower) {
      actThreshold = actThresholdClean;
    } else {
      actThreshold = actThresholdNoisy;
    }

    if ((aec->stateCounter > (0.5f * countLen * subCountLen)) &&
        (aec->farlevel.sfrcounter == 0)

        // Estimate in active far-end segments only
        &&
        (aec->farlevel.averagelevel >
         (actThreshold * aec->farlevel.minlevel))) {

      // Subtract noise power
      echo = aec->nearlevel.averagelevel - safety * aec->nearlevel.minlevel;

      // ERL
      dtmp = 10 * (float)log10(aec->farlevel.averagelevel /
                                   aec->nearlevel.averagelevel +
                               1e-10f);
      dtmp2 = 10 * (float)log10(aec->farlevel.averagelevel / echo + 1e-10f);

      aec->erl.instant = dtmp;
      if (dtmp > aec->erl.max) {
        aec->erl.max = dtmp;
      }

      if (dtmp < aec->erl.min) {
        aec->erl.min = dtmp;
      }

      aec->erl.counter++;
      aec->erl.sum += dtmp;
      aec->erl.average = aec->erl.sum / aec->erl.counter;

      // Upper mean
      if (dtmp > aec->erl.average) {
        aec->erl.hicounter++;
        aec->erl.hisum += dtmp;
        aec->erl.himean = aec->erl.hisum / aec->erl.hicounter;
      }

      // A_NLP
      dtmp = 10 * (float)log10(aec->nearlevel.averagelevel /
                                   (2 * aec->linoutlevel.averagelevel) +
                               1e-10f);

      // subtract noise power
      suppressedEcho = 2 * (aec->linoutlevel.averagelevel -
                            safety * aec->linoutlevel.minlevel);

      dtmp2 = 10 * (float)log10(echo / suppressedEcho + 1e-10f);

      aec->aNlp.instant = dtmp2;
      if (dtmp > aec->aNlp.max) {
        aec->aNlp.max = dtmp;
      }

      if (dtmp < aec->aNlp.min) {
        aec->aNlp.min = dtmp;
      }

      aec->aNlp.counter++;
      aec->aNlp.sum += dtmp;
      aec->aNlp.average = aec->aNlp.sum / aec->aNlp.counter;

      // Upper mean
      if (dtmp > aec->aNlp.average) {
        aec->aNlp.hicounter++;
        aec->aNlp.hisum += dtmp;
        aec->aNlp.himean = aec->aNlp.hisum / aec->aNlp.hicounter;
      }

      // ERLE

      // subtract noise power
      suppressedEcho = 2 * (aec->nlpoutlevel.averagelevel -
                            safety * aec->nlpoutlevel.minlevel);

      dtmp = 10 * (float)log10(aec->nearlevel.averagelevel /
                                   (2 * aec->nlpoutlevel.averagelevel) +
                               1e-10f);
      dtmp2 = 10 * (float)log10(echo / suppressedEcho + 1e-10f);

      dtmp = dtmp2;
      aec->erle.instant = dtmp;
      if (dtmp > aec->erle.max) {
        aec->erle.max = dtmp;
      }

      if (dtmp < aec->erle.min) {
        aec->erle.min = dtmp;
      }

      aec->erle.counter++;
      aec->erle.sum += dtmp;
      aec->erle.average = aec->erle.sum / aec->erle.counter;

      // Upper mean
      if (dtmp > aec->erle.average) {
        aec->erle.hicounter++;
        aec->erle.hisum += dtmp;
        aec->erle.himean = aec->erle.hisum / aec->erle.hicounter;
      }
    }

    aec->stateCounter = 0;
  }
}

static void TimeToFrequency(float time_data[PART_LEN2],
                            float freq_data[2][PART_LEN1],
                            int window) {
  int i = 0;

  // TODO(bjornv): Should we have a different function/wrapper for windowed FFT?
  if (window) {
    for (i = 0; i < PART_LEN; i++) {
      time_data[i] *= WebRtcAec_sqrtHanning[i];
      time_data[PART_LEN + i] *= WebRtcAec_sqrtHanning[PART_LEN - i];
    }
  }

  aec_rdft_forward_128(time_data);
  // Reorder.
  freq_data[1][0] = 0;
  freq_data[1][PART_LEN] = 0;
  freq_data[0][0] = time_data[0];
  freq_data[0][PART_LEN] = time_data[1];
  for (i = 1; i < PART_LEN; i++) {
    freq_data[0][i] = time_data[2 * i];
    freq_data[1][i] = time_data[2 * i + 1];
  }
}

static void NonLinearProcessing(AecCore* aec, float* output, float* outputH) {
  float efw[2][PART_LEN1], xfw[2][PART_LEN1];
  complex_t comfortNoiseHband[PART_LEN1];
  float fft[PART_LEN2];
  float scale, dtmp;
  float nlpGainHband;
  int i;

  // Coherence and non-linear filter
  float cohde[PART_LEN1], cohxd[PART_LEN1];
  float hNlDeAvg, hNlXdAvg;
  float hNl[PART_LEN1];
  float hNlPref[kPrefBandSize];
  float hNlFb = 0, hNlFbLow = 0;
  const float prefBandQuant = 0.75f, prefBandQuantLow = 0.5f;
  const int prefBandSize = kPrefBandSize / aec->mult;
  const int minPrefBand = 4 / aec->mult;
  // Power estimate smoothing coefficients.
  const float* min_overdrive = aec->extended_filter_enabled
                                   ? kExtendedMinOverDrive
                                   : kNormalMinOverDrive;

  // Filter energy
  const int delayEstInterval = 10 * aec->mult;

  float* xfw_ptr = NULL;

  aec->delayEstCtr++;
  if (aec->delayEstCtr == delayEstInterval) {
    aec->delayEstCtr = 0;
  }

  // initialize comfort noise for H band
  memset(comfortNoiseHband, 0, sizeof(comfortNoiseHband));
  nlpGainHband = (float)0.0;
  dtmp = (float)0.0;

  // We should always have at least one element stored in |far_buf|.
  assert(WebRtc_available_read(aec->far_buf_windowed) > 0);
  // NLP
  WebRtc_ReadBuffer(aec->far_buf_windowed, (void**)&xfw_ptr, &xfw[0][0], 1);

  // TODO(bjornv): Investigate if we can reuse |far_buf_windowed| instead of
  // |xfwBuf|.
  // Buffer far.
  memcpy(aec->xfwBuf, xfw_ptr, sizeof(float) * 2 * PART_LEN1);

  WebRtcAec_SubbandCoherence(aec, efw, xfw, fft, cohde, cohxd);

  hNlXdAvg = 0;
  for (i = minPrefBand; i < prefBandSize + minPrefBand; i++) {
    hNlXdAvg += cohxd[i];
  }
  hNlXdAvg /= prefBandSize;
  hNlXdAvg = 1 - hNlXdAvg;

  hNlDeAvg = 0;
  for (i = minPrefBand; i < prefBandSize + minPrefBand; i++) {
    hNlDeAvg += cohde[i];
  }
  hNlDeAvg /= prefBandSize;

  if (hNlXdAvg < 0.75f && hNlXdAvg < aec->hNlXdAvgMin) {
    aec->hNlXdAvgMin = hNlXdAvg;
  }

  if (hNlDeAvg > 0.98f && hNlXdAvg > 0.9f) {
    aec->stNearState = 1;
  } else if (hNlDeAvg < 0.95f || hNlXdAvg < 0.8f) {
    aec->stNearState = 0;
  }

  if (aec->hNlXdAvgMin == 1) {
    aec->echoState = 0;
    aec->overDrive = min_overdrive[aec->nlp_mode];

    if (aec->stNearState == 1) {
      memcpy(hNl, cohde, sizeof(hNl));
      hNlFb = hNlDeAvg;
      hNlFbLow = hNlDeAvg;
    } else {
      for (i = 0; i < PART_LEN1; i++) {
        hNl[i] = 1 - cohxd[i];
      }
      hNlFb = hNlXdAvg;
      hNlFbLow = hNlXdAvg;
    }
  } else {

    if (aec->stNearState == 1) {
      aec->echoState = 0;
      memcpy(hNl, cohde, sizeof(hNl));
      hNlFb = hNlDeAvg;
      hNlFbLow = hNlDeAvg;
    } else {
      aec->echoState = 1;
      for (i = 0; i < PART_LEN1; i++) {
        hNl[i] = WEBRTC_SPL_MIN(cohde[i], 1 - cohxd[i]);
      }

      // Select an order statistic from the preferred bands.
      // TODO: Using quicksort now, but a selection algorithm may be preferred.
      memcpy(hNlPref, &hNl[minPrefBand], sizeof(float) * prefBandSize);
      qsort(hNlPref, prefBandSize, sizeof(float), CmpFloat);
      hNlFb = hNlPref[(int)floor(prefBandQuant * (prefBandSize - 1))];
      hNlFbLow = hNlPref[(int)floor(prefBandQuantLow * (prefBandSize - 1))];
    }
  }

  // Track the local filter minimum to determine suppression overdrive.
  if (hNlFbLow < 0.6f && hNlFbLow < aec->hNlFbLocalMin) {
    aec->hNlFbLocalMin = hNlFbLow;
    aec->hNlFbMin = hNlFbLow;
    aec->hNlNewMin = 1;
    aec->hNlMinCtr = 0;
  }
  aec->hNlFbLocalMin =
      WEBRTC_SPL_MIN(aec->hNlFbLocalMin + 0.0008f / aec->mult, 1);
  aec->hNlXdAvgMin = WEBRTC_SPL_MIN(aec->hNlXdAvgMin + 0.0006f / aec->mult, 1);

  if (aec->hNlNewMin == 1) {
    aec->hNlMinCtr++;
  }
  if (aec->hNlMinCtr == 2) {
    aec->hNlNewMin = 0;
    aec->hNlMinCtr = 0;
    aec->overDrive =
        WEBRTC_SPL_MAX(kTargetSupp[aec->nlp_mode] /
                           ((float)log(aec->hNlFbMin + 1e-10f) + 1e-10f),
                       min_overdrive[aec->nlp_mode]);
  }

  // Smooth the overdrive.
  if (aec->overDrive < aec->overDriveSm) {
    aec->overDriveSm = 0.99f * aec->overDriveSm + 0.01f * aec->overDrive;
  } else {
    aec->overDriveSm = 0.9f * aec->overDriveSm + 0.1f * aec->overDrive;
  }

  WebRtcAec_OverdriveAndSuppress(aec, hNl, hNlFb, efw);

  // Add comfort noise.
  WebRtcAec_ComfortNoise(aec, efw, comfortNoiseHband, aec->noisePow, hNl);

  // TODO(bjornv): Investigate how to take the windowing below into account if
  // needed.
  if (aec->metricsMode == 1) {
    // Note that we have a scaling by two in the time domain |eBuf|.
    // In addition the time domain signal is windowed before transformation,
    // losing half the energy on the average. We take care of the first
    // scaling only in UpdateMetrics().
    UpdateLevel(&aec->nlpoutlevel, efw);
  }
  // Inverse error fft.
  fft[0] = efw[0][0];
  fft[1] = efw[0][PART_LEN];
  for (i = 1; i < PART_LEN; i++) {
    fft[2 * i] = efw[0][i];
    // Sign change required by Ooura fft.
    fft[2 * i + 1] = -efw[1][i];
  }
  aec_rdft_inverse_128(fft);

  // Overlap and add to obtain output.
  scale = 2.0f / PART_LEN2;
  for (i = 0; i < PART_LEN; i++) {
    fft[i] *= scale;  // fft scaling
    fft[i] = fft[i] * WebRtcAec_sqrtHanning[i] + aec->outBuf[i];

    fft[PART_LEN + i] *= scale;  // fft scaling
    aec->outBuf[i] = fft[PART_LEN + i] * WebRtcAec_sqrtHanning[PART_LEN - i];

    // Saturate output to keep it in the allowed range.
    output[i] = WEBRTC_SPL_SAT(
        WEBRTC_SPL_WORD16_MAX, fft[i], WEBRTC_SPL_WORD16_MIN);
  }

  // For H band
  if (aec->sampFreq == 32000) {

    // H band gain
    // average nlp over low band: average over second half of freq spectrum
    // (4->8khz)
    GetHighbandGain(hNl, &nlpGainHband);

    // Inverse comfort_noise
    if (flagHbandCn == 1) {
      fft[0] = comfortNoiseHband[0][0];
      fft[1] = comfortNoiseHband[PART_LEN][0];
      for (i = 1; i < PART_LEN; i++) {
        fft[2 * i] = comfortNoiseHband[i][0];
        fft[2 * i + 1] = comfortNoiseHband[i][1];
      }
      aec_rdft_inverse_128(fft);
      scale = 2.0f / PART_LEN2;
    }

    // compute gain factor
    for (i = 0; i < PART_LEN; i++) {
      dtmp = aec->dBufH[i];
      dtmp = dtmp * nlpGainHband;  // for variable gain

      // add some comfort noise where Hband is attenuated
      if (flagHbandCn == 1) {
        fft[i] *= scale;  // fft scaling
        dtmp += cnScaleHband * fft[i];
      }

      // Saturate output to keep it in the allowed range.
      outputH[i] = WEBRTC_SPL_SAT(
          WEBRTC_SPL_WORD16_MAX, dtmp, WEBRTC_SPL_WORD16_MIN);
    }
  }

  // Copy the current block to the old position.
  memcpy(aec->dBuf, aec->dBuf + PART_LEN, sizeof(float) * PART_LEN);
  memcpy(aec->eBuf, aec->eBuf + PART_LEN, sizeof(float) * PART_LEN);

  // Copy the current block to the old position for H band
  if (aec->sampFreq == 32000) {
    memcpy(aec->dBufH, aec->dBufH + PART_LEN, sizeof(float) * PART_LEN);
  }

  memmove(aec->xfwBuf + PART_LEN1,
          aec->xfwBuf,
          sizeof(aec->xfwBuf) - sizeof(complex_t) * PART_LEN1);
}

static void ProcessBlock(AecCore* aec) {
  int i;
  float y[PART_LEN], e[PART_LEN];
  float scale;

  float fft[PART_LEN2];
  float xf[2][PART_LEN1], yf[2][PART_LEN1], ef[2][PART_LEN1];
  float df[2][PART_LEN1];
  float far_spectrum = 0.0f;
  float near_spectrum = 0.0f;
  float abs_far_spectrum[PART_LEN1];
  float abs_near_spectrum[PART_LEN1];

  const float gPow[2] = {0.9f, 0.1f};

  // Noise estimate constants.
  const int noiseInitBlocks = 500 * aec->mult;
  const float step = 0.1f;
  const float ramp = 1.0002f;
  const float gInitNoise[2] = {0.999f, 0.001f};

  float nearend[PART_LEN];
  float* nearend_ptr = NULL;
  float output[PART_LEN];
  float outputH[PART_LEN];

  float* xf_ptr = NULL;

  // Concatenate old and new nearend blocks.
  if (aec->sampFreq == 32000) {
    WebRtc_ReadBuffer(aec->nearFrBufH, (void**)&nearend_ptr, nearend, PART_LEN);
    memcpy(aec->dBufH + PART_LEN, nearend_ptr, sizeof(nearend));
  }
  WebRtc_ReadBuffer(aec->nearFrBuf, (void**)&nearend_ptr, nearend, PART_LEN);
  memcpy(aec->dBuf + PART_LEN, nearend_ptr, sizeof(nearend));

  // ---------- Ooura fft ----------

#ifdef WEBRTC_AEC_DEBUG_DUMP
  {
    float farend[PART_LEN];
    float* farend_ptr = NULL;
    WebRtc_ReadBuffer(aec->far_time_buf, (void**)&farend_ptr, farend, 1);
    rtc_WavWriteSamples(aec->farFile, farend_ptr, PART_LEN);
    rtc_WavWriteSamples(aec->nearFile, nearend_ptr, PART_LEN);
  }
#endif

  // We should always have at least one element stored in |far_buf|.
  assert(WebRtc_available_read(aec->far_buf) > 0);
  WebRtc_ReadBuffer(aec->far_buf, (void**)&xf_ptr, &xf[0][0], 1);

  // Near fft
  memcpy(fft, aec->dBuf, sizeof(float) * PART_LEN2);
  TimeToFrequency(fft, df, 0);

  // Power smoothing
  for (i = 0; i < PART_LEN1; i++) {
    far_spectrum = (xf_ptr[i] * xf_ptr[i]) +
                   (xf_ptr[PART_LEN1 + i] * xf_ptr[PART_LEN1 + i]);
    aec->xPow[i] =
        gPow[0] * aec->xPow[i] + gPow[1] * aec->num_partitions * far_spectrum;
    // Calculate absolute spectra
    abs_far_spectrum[i] = sqrtf(far_spectrum);

    near_spectrum = df[0][i] * df[0][i] + df[1][i] * df[1][i];
    aec->dPow[i] = gPow[0] * aec->dPow[i] + gPow[1] * near_spectrum;
    // Calculate absolute spectra
    abs_near_spectrum[i] = sqrtf(near_spectrum);
  }

  // Estimate noise power. Wait until dPow is more stable.
  if (aec->noiseEstCtr > 50) {
    for (i = 0; i < PART_LEN1; i++) {
      if (aec->dPow[i] < aec->dMinPow[i]) {
        aec->dMinPow[i] =
            (aec->dPow[i] + step * (aec->dMinPow[i] - aec->dPow[i])) * ramp;
      } else {
        aec->dMinPow[i] *= ramp;
      }
    }
  }

  // Smooth increasing noise power from zero at the start,
  // to avoid a sudden burst of comfort noise.
  if (aec->noiseEstCtr < noiseInitBlocks) {
    aec->noiseEstCtr++;
    for (i = 0; i < PART_LEN1; i++) {
      if (aec->dMinPow[i] > aec->dInitMinPow[i]) {
        aec->dInitMinPow[i] = gInitNoise[0] * aec->dInitMinPow[i] +
                              gInitNoise[1] * aec->dMinPow[i];
      } else {
        aec->dInitMinPow[i] = aec->dMinPow[i];
      }
    }
    aec->noisePow = aec->dInitMinPow;
  } else {
    aec->noisePow = aec->dMinPow;
  }

  // Block wise delay estimation used for logging
  if (aec->delay_logging_enabled) {
    int delay_estimate = 0;
    if (WebRtc_AddFarSpectrumFloat(
            aec->delay_estimator_farend, abs_far_spectrum, PART_LEN1) == 0) {
      delay_estimate = WebRtc_DelayEstimatorProcessFloat(
          aec->delay_estimator, abs_near_spectrum, PART_LEN1);
      if (delay_estimate >= 0) {
        // Update delay estimate buffer.
        aec->delay_histogram[delay_estimate]++;
      }
    }
  }

  // Update the xfBuf block position.
  aec->xfBufBlockPos--;
  if (aec->xfBufBlockPos == -1) {
    aec->xfBufBlockPos = aec->num_partitions - 1;
  }

  // Buffer xf
  memcpy(aec->xfBuf[0] + aec->xfBufBlockPos * PART_LEN1,
         xf_ptr,
         sizeof(float) * PART_LEN1);
  memcpy(aec->xfBuf[1] + aec->xfBufBlockPos * PART_LEN1,
         &xf_ptr[PART_LEN1],
         sizeof(float) * PART_LEN1);

  memset(yf, 0, sizeof(yf));

  // Filter far
  WebRtcAec_FilterFar(aec, yf);

  // Inverse fft to obtain echo estimate and error.
  fft[0] = yf[0][0];
  fft[1] = yf[0][PART_LEN];
  for (i = 1; i < PART_LEN; i++) {
    fft[2 * i] = yf[0][i];
    fft[2 * i + 1] = yf[1][i];
  }
  aec_rdft_inverse_128(fft);

  scale = 2.0f / PART_LEN2;
  for (i = 0; i < PART_LEN; i++) {
    y[i] = fft[PART_LEN + i] * scale;  // fft scaling
  }

  for (i = 0; i < PART_LEN; i++) {
    e[i] = nearend_ptr[i] - y[i];
  }

  // Error fft
  memcpy(aec->eBuf + PART_LEN, e, sizeof(float) * PART_LEN);
  memset(fft, 0, sizeof(float) * PART_LEN);
  memcpy(fft + PART_LEN, e, sizeof(float) * PART_LEN);
  // TODO(bjornv): Change to use TimeToFrequency().
  aec_rdft_forward_128(fft);

  ef[1][0] = 0;
  ef[1][PART_LEN] = 0;
  ef[0][0] = fft[0];
  ef[0][PART_LEN] = fft[1];
  for (i = 1; i < PART_LEN; i++) {
    ef[0][i] = fft[2 * i];
    ef[1][i] = fft[2 * i + 1];
  }

  if (aec->metricsMode == 1) {
    // Note that the first PART_LEN samples in fft (before transformation) are
    // zero. Hence, the scaling by two in UpdateLevel() should not be
    // performed. That scaling is taken care of in UpdateMetrics() instead.
    UpdateLevel(&aec->linoutlevel, ef);
  }

  // Scale error signal inversely with far power.
  WebRtcAec_ScaleErrorSignal(aec, ef);
  WebRtcAec_FilterAdaptation(aec, fft, ef);
  NonLinearProcessing(aec, output, outputH);

  if (aec->metricsMode == 1) {
    // Update power levels and echo metrics
    UpdateLevel(&aec->farlevel, (float(*)[PART_LEN1])xf_ptr);
    UpdateLevel(&aec->nearlevel, df);
    UpdateMetrics(aec);
  }

  // Store the output block.
  WebRtc_WriteBuffer(aec->outFrBuf, output, PART_LEN);
  // For H band
  if (aec->sampFreq == 32000) {
    WebRtc_WriteBuffer(aec->outFrBufH, outputH, PART_LEN);
  }

#ifdef WEBRTC_AEC_DEBUG_DUMP
  rtc_WavWriteSamples(aec->outLinearFile, e, PART_LEN);
  rtc_WavWriteSamples(aec->outFile, output, PART_LEN);
#endif
}

int WebRtcAec_CreateAec(AecCore** aecInst) {
  AecCore* aec = malloc(sizeof(AecCore));
  *aecInst = aec;
  if (aec == NULL) {
    return -1;
  }

  aec->nearFrBuf = WebRtc_CreateBuffer(FRAME_LEN + PART_LEN, sizeof(float));
  if (!aec->nearFrBuf) {
    WebRtcAec_FreeAec(aec);
    aec = NULL;
    return -1;
  }

  aec->outFrBuf = WebRtc_CreateBuffer(FRAME_LEN + PART_LEN, sizeof(float));
  if (!aec->outFrBuf) {
    WebRtcAec_FreeAec(aec);
    aec = NULL;
    return -1;
  }

  aec->nearFrBufH = WebRtc_CreateBuffer(FRAME_LEN + PART_LEN, sizeof(float));
  if (!aec->nearFrBufH) {
    WebRtcAec_FreeAec(aec);
    aec = NULL;
    return -1;
  }

  aec->outFrBufH = WebRtc_CreateBuffer(FRAME_LEN + PART_LEN, sizeof(float));
  if (!aec->outFrBufH) {
    WebRtcAec_FreeAec(aec);
    aec = NULL;
    return -1;
  }

  // Create far-end buffers.
  aec->far_buf =
      WebRtc_CreateBuffer(kBufSizePartitions, sizeof(float) * 2 * PART_LEN1);
  if (!aec->far_buf) {
    WebRtcAec_FreeAec(aec);
    aec = NULL;
    return -1;
  }
  aec->far_buf_windowed =
      WebRtc_CreateBuffer(kBufSizePartitions, sizeof(float) * 2 * PART_LEN1);
  if (!aec->far_buf_windowed) {
    WebRtcAec_FreeAec(aec);
    aec = NULL;
    return -1;
  }
#ifdef WEBRTC_AEC_DEBUG_DUMP
  aec->instance_index = webrtc_aec_instance_count;
  aec->far_time_buf =
      WebRtc_CreateBuffer(kBufSizePartitions, sizeof(float) * PART_LEN);
  if (!aec->far_time_buf) {
    WebRtcAec_FreeAec(aec);
    aec = NULL;
    return -1;
  }
  aec->farFile = aec->nearFile = aec->outFile = aec->outLinearFile = NULL;
  aec->debug_dump_count = 0;
#endif
  aec->delay_estimator_farend =
      WebRtc_CreateDelayEstimatorFarend(PART_LEN1, kHistorySizeBlocks);
  if (aec->delay_estimator_farend == NULL) {
    WebRtcAec_FreeAec(aec);
    aec = NULL;
    return -1;
  }
  aec->delay_estimator = WebRtc_CreateDelayEstimator(
      aec->delay_estimator_farend, kLookaheadBlocks);
  if (aec->delay_estimator == NULL) {
    WebRtcAec_FreeAec(aec);
    aec = NULL;
    return -1;
  }

  // Assembly optimization
  WebRtcAec_FilterFar = FilterFar;
  WebRtcAec_ScaleErrorSignal = ScaleErrorSignal;
  WebRtcAec_FilterAdaptation = FilterAdaptation;
  WebRtcAec_OverdriveAndSuppress = OverdriveAndSuppress;
  WebRtcAec_ComfortNoise = ComfortNoise;
  WebRtcAec_SubbandCoherence = SubbandCoherence;

#if defined(WEBRTC_ARCH_X86_FAMILY)
  if (WebRtc_GetCPUInfo(kSSE2)) {
    WebRtcAec_InitAec_SSE2();
  }
#endif

#if defined(MIPS_FPU_LE)
  WebRtcAec_InitAec_mips();
#endif

#if defined(WEBRTC_DETECT_ARM_NEON) || defined(WEBRTC_ARCH_ARM_NEON)
  WebRtcAec_InitAec_neon();
#endif

  aec_rdft_init();

  return 0;
}

int WebRtcAec_FreeAec(AecCore* aec) {
  if (aec == NULL) {
    return -1;
  }

  WebRtc_FreeBuffer(aec->nearFrBuf);
  WebRtc_FreeBuffer(aec->outFrBuf);

  WebRtc_FreeBuffer(aec->nearFrBufH);
  WebRtc_FreeBuffer(aec->outFrBufH);

  WebRtc_FreeBuffer(aec->far_buf);
  WebRtc_FreeBuffer(aec->far_buf_windowed);
#ifdef WEBRTC_AEC_DEBUG_DUMP
  WebRtc_FreeBuffer(aec->far_time_buf);
  rtc_WavClose(aec->farFile);
  rtc_WavClose(aec->nearFile);
  rtc_WavClose(aec->outFile);
  rtc_WavClose(aec->outLinearFile);
#endif
  WebRtc_FreeDelayEstimator(aec->delay_estimator);
  WebRtc_FreeDelayEstimatorFarend(aec->delay_estimator_farend);

  free(aec);
  return 0;
}

#ifdef WEBRTC_AEC_DEBUG_DUMP
// Open a new Wav file for writing. If it was already open with a different
// sample frequency, close it first.
static void ReopenWav(rtc_WavFile** wav_file,
                      const char* name,
                      int seq1,
                      int seq2,
                      int sample_rate) {
  int written UNUSED;
  char filename[64];
  if (*wav_file) {
    if (rtc_WavSampleRate(*wav_file) == sample_rate)
      return;
    rtc_WavClose(*wav_file);
  }
  written = snprintf(filename, sizeof(filename), "%s%d-%d.wav",
                     name, seq1, seq2);
  assert(written >= 0);  // no output error
  assert((size_t)written < sizeof(filename));  // buffer was large enough
  *wav_file = rtc_WavOpen(filename, sample_rate, 1);
}
#endif  // WEBRTC_AEC_DEBUG_DUMP

int WebRtcAec_InitAec(AecCore* aec, int sampFreq) {
  int i;

  aec->sampFreq = sampFreq;

  if (sampFreq == 8000) {
    aec->normal_mu = 0.6f;
    aec->normal_error_threshold = 2e-6f;
  } else {
    aec->normal_mu = 0.5f;
    aec->normal_error_threshold = 1.5e-6f;
  }

  if (WebRtc_InitBuffer(aec->nearFrBuf) == -1) {
    return -1;
  }

  if (WebRtc_InitBuffer(aec->outFrBuf) == -1) {
    return -1;
  }

  if (WebRtc_InitBuffer(aec->nearFrBufH) == -1) {
    return -1;
  }

  if (WebRtc_InitBuffer(aec->outFrBufH) == -1) {
    return -1;
  }

  // Initialize far-end buffers.
  if (WebRtc_InitBuffer(aec->far_buf) == -1) {
    return -1;
  }
  if (WebRtc_InitBuffer(aec->far_buf_windowed) == -1) {
    return -1;
  }
#ifdef WEBRTC_AEC_DEBUG_DUMP
  if (WebRtc_InitBuffer(aec->far_time_buf) == -1) {
    return -1;
  }
  ReopenWav(&aec->farFile, "aec_far",
            aec->instance_index, aec->debug_dump_count, sampFreq);
  ReopenWav(&aec->nearFile, "aec_near",
            aec->instance_index, aec->debug_dump_count, sampFreq);
  ReopenWav(&aec->outFile, "aec_out",
            aec->instance_index, aec->debug_dump_count, sampFreq);
  ReopenWav(&aec->outLinearFile, "aec_out_linear",
            aec->instance_index, aec->debug_dump_count, sampFreq);
  ++aec->debug_dump_count;
#endif
  aec->system_delay = 0;

  if (WebRtc_InitDelayEstimatorFarend(aec->delay_estimator_farend) != 0) {
    return -1;
  }
  if (WebRtc_InitDelayEstimator(aec->delay_estimator) != 0) {
    return -1;
  }
  aec->delay_logging_enabled = 0;
  memset(aec->delay_histogram, 0, sizeof(aec->delay_histogram));

  aec->reported_delay_enabled = 1;
  aec->extended_filter_enabled = 0;
  aec->num_partitions = kNormalNumPartitions;

  // Update the delay estimator with filter length.  We use half the
  // |num_partitions| to take the echo path into account.  In practice we say
  // that the echo has a duration of maximum half |num_partitions|, which is not
  // true, but serves as a crude measure.
  WebRtc_set_allowed_offset(aec->delay_estimator, aec->num_partitions / 2);
  // TODO(bjornv): I currently hard coded the enable.  Once we've established
  // that AECM has no performance regression, robust_validation will be enabled
  // all the time and the APIs to turn it on/off will be removed.  Hence, remove
  // this line then.
  WebRtc_enable_robust_validation(aec->delay_estimator, 1);

  // Default target suppression mode.
  aec->nlp_mode = 1;

  // Sampling frequency multiplier
  // SWB is processed as 160 frame size
  if (aec->sampFreq == 32000) {
    aec->mult = (short)aec->sampFreq / 16000;
  } else {
    aec->mult = (short)aec->sampFreq / 8000;
  }

  aec->farBufWritePos = 0;
  aec->farBufReadPos = 0;

  aec->inSamples = 0;
  aec->outSamples = 0;
  aec->knownDelay = 0;

  // Initialize buffers
  memset(aec->dBuf, 0, sizeof(aec->dBuf));
  memset(aec->eBuf, 0, sizeof(aec->eBuf));
  // For H band
  memset(aec->dBufH, 0, sizeof(aec->dBufH));

  memset(aec->xPow, 0, sizeof(aec->xPow));
  memset(aec->dPow, 0, sizeof(aec->dPow));
  memset(aec->dInitMinPow, 0, sizeof(aec->dInitMinPow));
  aec->noisePow = aec->dInitMinPow;
  aec->noiseEstCtr = 0;

  // Initial comfort noise power
  for (i = 0; i < PART_LEN1; i++) {
    aec->dMinPow[i] = 1.0e6f;
  }

  // Holds the last block written to
  aec->xfBufBlockPos = 0;
  // TODO: Investigate need for these initializations. Deleting them doesn't
  //       change the output at all and yields 0.4% overall speedup.
  memset(aec->xfBuf, 0, sizeof(complex_t) * kExtendedNumPartitions * PART_LEN1);
  memset(aec->wfBuf, 0, sizeof(complex_t) * kExtendedNumPartitions * PART_LEN1);
  memset(aec->sde, 0, sizeof(complex_t) * PART_LEN1);
  memset(aec->sxd, 0, sizeof(complex_t) * PART_LEN1);
  memset(
      aec->xfwBuf, 0, sizeof(complex_t) * kExtendedNumPartitions * PART_LEN1);
  memset(aec->se, 0, sizeof(float) * PART_LEN1);

  // To prevent numerical instability in the first block.
  for (i = 0; i < PART_LEN1; i++) {
    aec->sd[i] = 1;
  }
  for (i = 0; i < PART_LEN1; i++) {
    aec->sx[i] = 1;
  }

  memset(aec->hNs, 0, sizeof(aec->hNs));
  memset(aec->outBuf, 0, sizeof(float) * PART_LEN);

  aec->hNlFbMin = 1;
  aec->hNlFbLocalMin = 1;
  aec->hNlXdAvgMin = 1;
  aec->hNlNewMin = 0;
  aec->hNlMinCtr = 0;
  aec->overDrive = 2;
  aec->overDriveSm = 2;
  aec->delayIdx = 0;
  aec->stNearState = 0;
  aec->echoState = 0;
  aec->divergeState = 0;

  aec->seed = 777;
  aec->delayEstCtr = 0;

  // Metrics disabled by default
  aec->metricsMode = 0;
  InitMetrics(aec);

  return 0;
}

void WebRtcAec_BufferFarendPartition(AecCore* aec, const float* farend) {
  float fft[PART_LEN2];
  float xf[2][PART_LEN1];

  // Check if the buffer is full, and in that case flush the oldest data.
  if (WebRtc_available_write(aec->far_buf) < 1) {
    WebRtcAec_MoveFarReadPtr(aec, 1);
  }
  // Convert far-end partition to the frequency domain without windowing.
  memcpy(fft, farend, sizeof(float) * PART_LEN2);
  TimeToFrequency(fft, xf, 0);
  WebRtc_WriteBuffer(aec->far_buf, &xf[0][0], 1);

  // Convert far-end partition to the frequency domain with windowing.
  memcpy(fft, farend, sizeof(float) * PART_LEN2);
  TimeToFrequency(fft, xf, 1);
  WebRtc_WriteBuffer(aec->far_buf_windowed, &xf[0][0], 1);
}

int WebRtcAec_MoveFarReadPtr(AecCore* aec, int elements) {
  int elements_moved = WebRtc_MoveReadPtr(aec->far_buf_windowed, elements);
  WebRtc_MoveReadPtr(aec->far_buf, elements);
#ifdef WEBRTC_AEC_DEBUG_DUMP
  WebRtc_MoveReadPtr(aec->far_time_buf, elements);
#endif
  aec->system_delay -= elements_moved * PART_LEN;
  return elements_moved;
}

void WebRtcAec_ProcessFrame(AecCore* aec,
                            const float* nearend,
                            const float* nearendH,
                            int knownDelay,
                            float* out,
                            float* outH) {
  int out_elements = 0;

  // For each frame the process is as follows:
  // 1) If the system_delay indicates on being too small for processing a
  //    frame we stuff the buffer with enough data for 10 ms.
  // 2) Adjust the buffer to the system delay, by moving the read pointer.
  // 3) TODO(bjornv): Investigate if we need to add this:
  //    If we can't move read pointer due to buffer size limitations we
  //    flush/stuff the buffer.
  // 4) Process as many partitions as possible.
  // 5) Update the |system_delay| with respect to a full frame of FRAME_LEN
  //    samples. Even though we will have data left to process (we work with
  //    partitions) we consider updating a whole frame, since that's the
  //    amount of data we input and output in audio_processing.
  // 6) Update the outputs.

  // TODO(bjornv): Investigate how we should round the delay difference; right
  // now we know that incoming |knownDelay| is underestimated when it's less
  // than |aec->knownDelay|. We therefore, round (-32) in that direction. In
  // the other direction, we don't have this situation, but might flush one
  // partition too little. This can cause non-causality, which should be
  // investigated. Maybe, allow for a non-symmetric rounding, like -16.
  int move_elements = (aec->knownDelay - knownDelay - 32) / PART_LEN;
  int moved_elements = 0;

  // TODO(bjornv): Change the near-end buffer handling to be the same as for
  // far-end, that is, with a near_pre_buf.
  // Buffer the near-end frame.
  WebRtc_WriteBuffer(aec->nearFrBuf, nearend, FRAME_LEN);
  // For H band
  if (aec->sampFreq == 32000) {
    WebRtc_WriteBuffer(aec->nearFrBufH, nearendH, FRAME_LEN);
  }

  // 1) At most we process |aec->mult|+1 partitions in 10 ms. Make sure we
  // have enough far-end data for that by stuffing the buffer if the
  // |system_delay| indicates others.
  if (aec->system_delay < FRAME_LEN) {
    // We don't have enough data so we rewind 10 ms.
    WebRtcAec_MoveFarReadPtr(aec, -(aec->mult + 1));
  }

  // 2) Compensate for a possible change in the system delay.
  WebRtc_MoveReadPtr(aec->far_buf_windowed, move_elements);
  moved_elements = WebRtc_MoveReadPtr(aec->far_buf, move_elements);
  aec->knownDelay -= moved_elements * PART_LEN;
#ifdef WEBRTC_AEC_DEBUG_DUMP
  WebRtc_MoveReadPtr(aec->far_time_buf, move_elements);
#endif

  // 4) Process as many blocks as possible.
  while (WebRtc_available_read(aec->nearFrBuf) >= PART_LEN) {
    ProcessBlock(aec);
  }

  // 5) Update system delay with respect to the entire frame.
  aec->system_delay -= FRAME_LEN;

  // 6) Update output frame.
  // Stuff the out buffer if we have less than a frame to output.
  // This should only happen for the first frame.
  out_elements = (int)WebRtc_available_read(aec->outFrBuf);
  if (out_elements < FRAME_LEN) {
    WebRtc_MoveReadPtr(aec->outFrBuf, out_elements - FRAME_LEN);
    if (aec->sampFreq == 32000) {
      WebRtc_MoveReadPtr(aec->outFrBufH, out_elements - FRAME_LEN);
    }
  }
  // Obtain an output frame.
  WebRtc_ReadBuffer(aec->outFrBuf, NULL, out, FRAME_LEN);
  // For H band.
  if (aec->sampFreq == 32000) {
    WebRtc_ReadBuffer(aec->outFrBufH, NULL, outH, FRAME_LEN);
  }
}

int WebRtcAec_GetDelayMetricsCore(AecCore* self, int* median, int* std) {
  int i = 0;
  int delay_values = 0;
  int num_delay_values = 0;
  int my_median = 0;
  const int kMsPerBlock = PART_LEN / (self->mult * 8);
  float l1_norm = 0;

  assert(self != NULL);
  assert(median != NULL);
  assert(std != NULL);

  if (self->delay_logging_enabled == 0) {
    // Logging disabled.
    return -1;
  }

  // Get number of delay values since last update.
  for (i = 0; i < kHistorySizeBlocks; i++) {
    num_delay_values += self->delay_histogram[i];
  }
  if (num_delay_values == 0) {
    // We have no new delay value data. Even though -1 is a valid estimate, it
    // will practically never be used since multiples of |kMsPerBlock| will
    // always be returned.
    *median = -1;
    *std = -1;
    return 0;
  }

  delay_values = num_delay_values >> 1;  // Start value for median count down.
  // Get median of delay values since last update.
  for (i = 0; i < kHistorySizeBlocks; i++) {
    delay_values -= self->delay_histogram[i];
    if (delay_values < 0) {
      my_median = i;
      break;
    }
  }
  // Account for lookahead.
  *median = (my_median - kLookaheadBlocks) * kMsPerBlock;

  // Calculate the L1 norm, with median value as central moment.
  for (i = 0; i < kHistorySizeBlocks; i++) {
    l1_norm += (float)abs(i - my_median) * self->delay_histogram[i];
  }
  *std = (int)(l1_norm / (float)num_delay_values + 0.5f) * kMsPerBlock;

  // Reset histogram.
  memset(self->delay_histogram, 0, sizeof(self->delay_histogram));

  return 0;
}

int WebRtcAec_echo_state(AecCore* self) { return self->echoState; }

void WebRtcAec_GetEchoStats(AecCore* self,
                            Stats* erl,
                            Stats* erle,
                            Stats* a_nlp) {
  assert(erl != NULL);
  assert(erle != NULL);
  assert(a_nlp != NULL);
  *erl = self->erl;
  *erle = self->erle;
  *a_nlp = self->aNlp;
}

#ifdef WEBRTC_AEC_DEBUG_DUMP
void* WebRtcAec_far_time_buf(AecCore* self) { return self->far_time_buf; }
#endif

void WebRtcAec_SetConfigCore(AecCore* self,
                             int nlp_mode,
                             int metrics_mode,
                             int delay_logging) {
  assert(nlp_mode >= 0 && nlp_mode < 3);
  self->nlp_mode = nlp_mode;
  self->metricsMode = metrics_mode;
  if (self->metricsMode) {
    InitMetrics(self);
  }
  self->delay_logging_enabled = delay_logging;
  if (self->delay_logging_enabled) {
    memset(self->delay_histogram, 0, sizeof(self->delay_histogram));
  }
}

void WebRtcAec_enable_reported_delay(AecCore* self, int enable) {
  self->reported_delay_enabled = enable;
}

int WebRtcAec_reported_delay_enabled(AecCore* self) {
  return self->reported_delay_enabled;
}

void WebRtcAec_enable_delay_correction(AecCore* self, int enable) {
  self->extended_filter_enabled = enable;
  self->num_partitions = enable ? kExtendedNumPartitions : kNormalNumPartitions;
  // Update the delay estimator with filter length.  See InitAEC() for details.
  WebRtc_set_allowed_offset(self->delay_estimator, self->num_partitions / 2);
}

int WebRtcAec_delay_correction_enabled(AecCore* self) {
  return self->extended_filter_enabled;
}

int WebRtcAec_system_delay(AecCore* self) { return self->system_delay; }

void WebRtcAec_SetSystemDelay(AecCore* self, int delay) {
  assert(delay >= 0);
  self->system_delay = delay;
}

