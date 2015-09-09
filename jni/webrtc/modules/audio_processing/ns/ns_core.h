/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_NS_MAIN_SOURCE_NS_CORE_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_NS_MAIN_SOURCE_NS_CORE_H_

#include "webrtc/modules/audio_processing/ns/defines.h"

typedef struct NSParaExtract_t_ {

  //bin size of histogram
  float binSizeLrt;
  float binSizeSpecFlat;
  float binSizeSpecDiff;
  //range of histogram over which lrt threshold is computed
  float rangeAvgHistLrt;
  //scale parameters: multiply dominant peaks of the histograms by scale factor to obtain
  //thresholds for prior model
  float factor1ModelPars; //for lrt and spectral difference
  float factor2ModelPars; //for spectral_flatness: used when noise is flatter than speech
  //peak limit for spectral flatness (varies between 0 and 1)
  float thresPosSpecFlat;
  //limit on spacing of two highest peaks in histogram: spacing determined by bin size
  float limitPeakSpacingSpecFlat;
  float limitPeakSpacingSpecDiff;
  //limit on relevance of second peak:
  float limitPeakWeightsSpecFlat;
  float limitPeakWeightsSpecDiff;
  //limit on fluctuation of lrt feature
  float thresFluctLrt;
  //limit on the max and min values for the feature thresholds
  float maxLrt;
  float minLrt;
  float maxSpecFlat;
  float minSpecFlat;
  float maxSpecDiff;
  float minSpecDiff;
  //criteria of weight of histogram peak  to accept/reject feature
  int thresWeightSpecFlat;
  int thresWeightSpecDiff;

} NSParaExtract_t;

typedef struct NSinst_t_ {

  uint32_t        fs;
  int             blockLen;
  int             blockLen10ms;
  int             windShift;
  int             outLen;
  int             anaLen;
  int             magnLen;
  int             aggrMode;
  const float*    window;
  float           dataBuf[ANAL_BLOCKL_MAX];
  float           syntBuf[ANAL_BLOCKL_MAX];
  float           outBuf[3 * BLOCKL_MAX];

  int             initFlag;
  // parameters for quantile noise estimation
  float           density[SIMULT* HALF_ANAL_BLOCKL];
  float           lquantile[SIMULT* HALF_ANAL_BLOCKL];
  float           quantile[HALF_ANAL_BLOCKL];
  int             counter[SIMULT];
  int             updates;
  // parameters for Wiener filter
  float           smooth[HALF_ANAL_BLOCKL];
  float           overdrive;
  float           denoiseBound;
  int             gainmap;
  // fft work arrays.
  int             ip[IP_LENGTH];
  float           wfft[W_LENGTH];

  // parameters for new method: some not needed, will reduce/cleanup later
  int32_t         blockInd;                           //frame index counter
  int             modelUpdatePars[4];                 //parameters for updating or estimating
  // thresholds/weights for prior model
  float           priorModelPars[7];                  //parameters for prior model
  float           noisePrev[HALF_ANAL_BLOCKL];        //noise spectrum from previous frame
  float           magnPrev[HALF_ANAL_BLOCKL];         //magnitude spectrum of previous frame
  float           logLrtTimeAvg[HALF_ANAL_BLOCKL];    //log lrt factor with time-smoothing
  float           priorSpeechProb;                    //prior speech/noise probability
  float           featureData[7];                     //data for features
  float           magnAvgPause[HALF_ANAL_BLOCKL];     //conservative noise spectrum estimate
  float           signalEnergy;                       //energy of magn
  float           sumMagn;                            //sum of magn
  float           whiteNoiseLevel;                    //initial noise estimate
  float           initMagnEst[HALF_ANAL_BLOCKL];      //initial magnitude spectrum estimate
  float           pinkNoiseNumerator;                 //pink noise parameter: numerator
  float           pinkNoiseExp;                       //pink noise parameter: power of freq
  NSParaExtract_t featureExtractionParams;            //parameters for feature extraction
  //histograms for parameter estimation
  int             histLrt[HIST_PAR_EST];
  int             histSpecFlat[HIST_PAR_EST];
  int             histSpecDiff[HIST_PAR_EST];
  //quantities for high band estimate
  float           speechProbHB[HALF_ANAL_BLOCKL];     //final speech/noise prob: prior + LRT
  float           dataBufHB[ANAL_BLOCKL_MAX];         //buffering data for HB

} NSinst_t;


#ifdef __cplusplus
extern "C" {
#endif

/****************************************************************************
 * WebRtcNs_InitCore(...)
 *
 * This function initializes a noise suppression instance
 *
 * Input:
 *      - inst          : Instance that should be initialized
 *      - fs            : Sampling frequency
 *
 * Output:
 *      - inst          : Initialized instance
 *
 * Return value         :  0 - Ok
 *                        -1 - Error
 */
int WebRtcNs_InitCore(NSinst_t* inst, uint32_t fs);

/****************************************************************************
 * WebRtcNs_set_policy_core(...)
 *
 * This changes the aggressiveness of the noise suppression method.
 *
 * Input:
 *      - inst          : Instance that should be initialized
 *      - mode          : 0: Mild (6 dB), 1: Medium (10 dB), 2: Aggressive (15 dB)
 *
 * Output:
 *      - NS_inst      : Initialized instance
 *
 * Return value         :  0 - Ok
 *                        -1 - Error
 */
int WebRtcNs_set_policy_core(NSinst_t* inst, int mode);

/****************************************************************************
 * WebRtcNs_ProcessCore
 *
 * Do noise suppression.
 *
 * Input:
 *      - inst          : Instance that should be initialized
 *      - inFrameLow    : Input speech frame for lower band
 *      - inFrameHigh   : Input speech frame for higher band
 *
 * Output:
 *      - inst          : Updated instance
 *      - outFrameLow   : Output speech frame for lower band
 *      - outFrameHigh  : Output speech frame for higher band
 *
 * Return value         :  0 - OK
 *                        -1 - Error
 */


int WebRtcNs_ProcessCore(NSinst_t* inst,
                         float* inFrameLow,
                         float* inFrameHigh,
                         float* outFrameLow,
                         float* outFrameHigh);


#ifdef __cplusplus
}
#endif
#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_NS_MAIN_SOURCE_NS_CORE_H_
