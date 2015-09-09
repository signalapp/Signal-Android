/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <math.h>
#include <string.h>
//#include <stdio.h>
#include <stdlib.h>
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_processing/ns/include/noise_suppression.h"
#include "webrtc/modules/audio_processing/ns/ns_core.h"
#include "webrtc/modules/audio_processing/ns/windows_private.h"
#include "webrtc/modules/audio_processing/utility/fft4g.h"

// Set Feature Extraction Parameters
void WebRtcNs_set_feature_extraction_parameters(NSinst_t* inst) {
  //bin size of histogram
  inst->featureExtractionParams.binSizeLrt      = (float)0.1;
  inst->featureExtractionParams.binSizeSpecFlat = (float)0.05;
  inst->featureExtractionParams.binSizeSpecDiff = (float)0.1;

  //range of histogram over which lrt threshold is computed
  inst->featureExtractionParams.rangeAvgHistLrt = (float)1.0;

  //scale parameters: multiply dominant peaks of the histograms by scale factor to obtain
  // thresholds for prior model
  inst->featureExtractionParams.factor1ModelPars = (float)1.20; //for lrt and spectral diff
  inst->featureExtractionParams.factor2ModelPars = (float)0.9;  //for spectral_flatness:
  // used when noise is flatter than speech

  //peak limit for spectral flatness (varies between 0 and 1)
  inst->featureExtractionParams.thresPosSpecFlat = (float)0.6;

  //limit on spacing of two highest peaks in histogram: spacing determined by bin size
  inst->featureExtractionParams.limitPeakSpacingSpecFlat = 
      2 * inst->featureExtractionParams.binSizeSpecFlat;
  inst->featureExtractionParams.limitPeakSpacingSpecDiff =
      2 * inst->featureExtractionParams.binSizeSpecDiff;

  //limit on relevance of second peak:
  inst->featureExtractionParams.limitPeakWeightsSpecFlat = (float)0.5;
  inst->featureExtractionParams.limitPeakWeightsSpecDiff = (float)0.5;

  // fluctuation limit of lrt feature
  inst->featureExtractionParams.thresFluctLrt = (float)0.05;

  //limit on the max and min values for the feature thresholds
  inst->featureExtractionParams.maxLrt = (float)1.0;
  inst->featureExtractionParams.minLrt = (float)0.20;

  inst->featureExtractionParams.maxSpecFlat = (float)0.95;
  inst->featureExtractionParams.minSpecFlat = (float)0.10;

  inst->featureExtractionParams.maxSpecDiff = (float)1.0;
  inst->featureExtractionParams.minSpecDiff = (float)0.16;

  //criteria of weight of histogram peak  to accept/reject feature
  inst->featureExtractionParams.thresWeightSpecFlat = (int)(0.3
      * (inst->modelUpdatePars[1])); //for spectral flatness
  inst->featureExtractionParams.thresWeightSpecDiff = (int)(0.3
      * (inst->modelUpdatePars[1])); //for spectral difference
}

// Initialize state
int WebRtcNs_InitCore(NSinst_t* inst, uint32_t fs) {
  int i;
  //We only support 10ms frames

  //check for valid pointer
  if (inst == NULL) {
    return -1;
  }

  // Initialization of struct
  if (fs == 8000 || fs == 16000 || fs == 32000) {
    inst->fs = fs;
  } else {
    return -1;
  }
  inst->windShift = 0;
  if (fs == 8000) {
    // We only support 10ms frames
    inst->blockLen = 80;
    inst->blockLen10ms = 80;
    inst->anaLen = 128;
    inst->window = kBlocks80w128;
    inst->outLen = 0;
  } else if (fs == 16000) {
    // We only support 10ms frames
    inst->blockLen = 160;
    inst->blockLen10ms = 160;
    inst->anaLen = 256;
    inst->window = kBlocks160w256;
    inst->outLen = 0;
  } else if (fs == 32000) {
    // We only support 10ms frames
    inst->blockLen = 160;
    inst->blockLen10ms = 160;
    inst->anaLen = 256;
    inst->window = kBlocks160w256;
    inst->outLen = 0;
  }
  inst->magnLen = inst->anaLen / 2 + 1; // Number of frequency bins

  // Initialize fft work arrays.
  inst->ip[0] = 0; // Setting this triggers initialization.
  memset(inst->dataBuf, 0, sizeof(float) * ANAL_BLOCKL_MAX);
  WebRtc_rdft(inst->anaLen, 1, inst->dataBuf, inst->ip, inst->wfft);

  memset(inst->dataBuf, 0, sizeof(float) * ANAL_BLOCKL_MAX);
  memset(inst->syntBuf, 0, sizeof(float) * ANAL_BLOCKL_MAX);

  //for HB processing
  memset(inst->dataBufHB, 0, sizeof(float) * ANAL_BLOCKL_MAX);

  //for quantile noise estimation
  memset(inst->quantile, 0, sizeof(float) * HALF_ANAL_BLOCKL);
  for (i = 0; i < SIMULT * HALF_ANAL_BLOCKL; i++) {
    inst->lquantile[i] = (float)8.0;
    inst->density[i] = (float)0.3;
  }

  for (i = 0; i < SIMULT; i++) {
    inst->counter[i] = (int)floor((float)(END_STARTUP_LONG * (i + 1)) / (float)SIMULT);
  }

  inst->updates = 0;

  // Wiener filter initialization
  for (i = 0; i < HALF_ANAL_BLOCKL; i++) {
    inst->smooth[i] = (float)1.0;
  }

  // Set the aggressiveness: default
  inst->aggrMode = 0;

  //initialize variables for new method
  inst->priorSpeechProb = (float)0.5; //prior prob for speech/noise
  for (i = 0; i < HALF_ANAL_BLOCKL; i++) {
    inst->magnPrev[i]      = (float)0.0; //previous mag spectrum
    inst->noisePrev[i]     = (float)0.0; //previous noise-spectrum
    inst->logLrtTimeAvg[i] = LRT_FEATURE_THR; //smooth LR ratio (same as threshold)
    inst->magnAvgPause[i]  = (float)0.0; //conservative noise spectrum estimate
    inst->speechProbHB[i]  = (float)0.0; //for estimation of HB in second pass
    inst->initMagnEst[i]   = (float)0.0; //initial average mag spectrum
  }

  //feature quantities
  inst->featureData[0] = SF_FEATURE_THR;  //spectral flatness (start on threshold)
  inst->featureData[1] = (float)0.0;      //spectral entropy: not used in this version
  inst->featureData[2] = (float)0.0;      //spectral variance: not used in this version
  inst->featureData[3] = LRT_FEATURE_THR; //average lrt factor (start on threshold)
  inst->featureData[4] = SF_FEATURE_THR;  //spectral template diff (start on threshold)
  inst->featureData[5] = (float)0.0;      //normalization for spectral-diff
  inst->featureData[6] = (float)0.0;      //window time-average of input magnitude spectrum

  //histogram quantities: used to estimate/update thresholds for features
  for (i = 0; i < HIST_PAR_EST; i++) {
    inst->histLrt[i] = 0;
    inst->histSpecFlat[i] = 0;
    inst->histSpecDiff[i] = 0;
  }

  inst->blockInd = -1; //frame counter
  inst->priorModelPars[0] = LRT_FEATURE_THR; //default threshold for lrt feature
  inst->priorModelPars[1] = (float)0.5;      //threshold for spectral flatness:
  // determined on-line
  inst->priorModelPars[2] = (float)1.0;      //sgn_map par for spectral measure:
  // 1 for flatness measure
  inst->priorModelPars[3] = (float)0.5;      //threshold for template-difference feature:
  // determined on-line
  inst->priorModelPars[4] = (float)1.0;      //default weighting parameter for lrt feature
  inst->priorModelPars[5] = (float)0.0;      //default weighting parameter for
  // spectral flatness feature
  inst->priorModelPars[6] = (float)0.0;      //default weighting parameter for
  // spectral difference feature

  inst->modelUpdatePars[0] = 2;   //update flag for parameters:
  // 0 no update, 1=update once, 2=update every window
  inst->modelUpdatePars[1] = 500; //window for update
  inst->modelUpdatePars[2] = 0;   //counter for update of conservative noise spectrum
  //counter if the feature thresholds are updated during the sequence
  inst->modelUpdatePars[3] = inst->modelUpdatePars[1];

  inst->signalEnergy = 0.0;
  inst->sumMagn = 0.0;
  inst->whiteNoiseLevel = 0.0;
  inst->pinkNoiseNumerator = 0.0;
  inst->pinkNoiseExp = 0.0;

  WebRtcNs_set_feature_extraction_parameters(inst); // Set feature configuration

  //default mode
  WebRtcNs_set_policy_core(inst, 0);


  memset(inst->outBuf, 0, sizeof(float) * 3 * BLOCKL_MAX);

  inst->initFlag = 1;
  return 0;
}

int WebRtcNs_set_policy_core(NSinst_t* inst, int mode) {
  // allow for modes:0,1,2,3
  if (mode < 0 || mode > 3) {
    return (-1);
  }

  inst->aggrMode = mode;
  if (mode == 0) {
    inst->overdrive = (float)1.0;
    inst->denoiseBound = (float)0.5;
    inst->gainmap = 0;
  } else if (mode == 1) {
    //inst->overdrive = (float)1.25;
    inst->overdrive = (float)1.0;
    inst->denoiseBound = (float)0.25;
    inst->gainmap = 1;
  } else if (mode == 2) {
    //inst->overdrive = (float)1.25;
    inst->overdrive = (float)1.1;
    inst->denoiseBound = (float)0.125;
    inst->gainmap = 1;
  } else if (mode == 3) {
    //inst->overdrive = (float)1.30;
    inst->overdrive = (float)1.25;
    inst->denoiseBound = (float)0.09;
    inst->gainmap = 1;
  }
  return 0;
}

// Estimate noise
void WebRtcNs_NoiseEstimation(NSinst_t* inst, float* magn, float* noise) {
  int i, s, offset;
  float lmagn[HALF_ANAL_BLOCKL], delta;

  if (inst->updates < END_STARTUP_LONG) {
    inst->updates++;
  }

  for (i = 0; i < inst->magnLen; i++) {
    lmagn[i] = (float)log(magn[i]);
  }

  // loop over simultaneous estimates
  for (s = 0; s < SIMULT; s++) {
    offset = s * inst->magnLen;

    // newquantest(...)
    for (i = 0; i < inst->magnLen; i++) {
      // compute delta
      if (inst->density[offset + i] > 1.0) {
        delta = FACTOR * (float)1.0 / inst->density[offset + i];
      } else {
        delta = FACTOR;
      }

      // update log quantile estimate
      if (lmagn[i] > inst->lquantile[offset + i]) {
        inst->lquantile[offset + i] += QUANTILE * delta
                                       / (float)(inst->counter[s] + 1);
      } else {
        inst->lquantile[offset + i] -= ((float)1.0 - QUANTILE) * delta
                                       / (float)(inst->counter[s] + 1);
      }

      // update density estimate
      if (fabs(lmagn[i] - inst->lquantile[offset + i]) < WIDTH) {
        inst->density[offset + i] = ((float)inst->counter[s] * inst->density[offset
            + i] + (float)1.0 / ((float)2.0 * WIDTH)) / (float)(inst->counter[s] + 1);
      }
    }  // end loop over magnitude spectrum

    if (inst->counter[s] >= END_STARTUP_LONG) {
      inst->counter[s] = 0;
      if (inst->updates >= END_STARTUP_LONG) {
        for (i = 0; i < inst->magnLen; i++) {
          inst->quantile[i] = (float)exp(inst->lquantile[offset + i]);
        }
      }
    }

    inst->counter[s]++;
  }  // end loop over simultaneous estimates

  // Sequentially update the noise during startup
  if (inst->updates < END_STARTUP_LONG) {
    // Use the last "s" to get noise during startup that differ from zero.
    for (i = 0; i < inst->magnLen; i++) {
      inst->quantile[i] = (float)exp(inst->lquantile[offset + i]);
    }
  }

  for (i = 0; i < inst->magnLen; i++) {
    noise[i] = inst->quantile[i];
  }
}

// Extract thresholds for feature parameters
// histograms are computed over some window_size (given by inst->modelUpdatePars[1])
// thresholds and weights are extracted every window
// flag 0 means update histogram only, flag 1 means compute the thresholds/weights
// threshold and weights are returned in: inst->priorModelPars
void WebRtcNs_FeatureParameterExtraction(NSinst_t* inst, int flag) {
  int i, useFeatureSpecFlat, useFeatureSpecDiff, numHistLrt;
  int maxPeak1, maxPeak2;
  int weightPeak1SpecFlat, weightPeak2SpecFlat, weightPeak1SpecDiff, weightPeak2SpecDiff;

  float binMid, featureSum;
  float posPeak1SpecFlat, posPeak2SpecFlat, posPeak1SpecDiff, posPeak2SpecDiff;
  float fluctLrt, avgHistLrt, avgSquareHistLrt, avgHistLrtCompl;

  //3 features: lrt, flatness, difference
  //lrt_feature = inst->featureData[3];
  //flat_feature = inst->featureData[0];
  //diff_feature = inst->featureData[4];

  //update histograms
  if (flag == 0) {
    // LRT
    if ((inst->featureData[3] < HIST_PAR_EST * inst->featureExtractionParams.binSizeLrt)
        && (inst->featureData[3] >= 0.0)) {
      i = (int)(inst->featureData[3] / inst->featureExtractionParams.binSizeLrt);
      inst->histLrt[i]++;
    }
    // Spectral flatness
    if ((inst->featureData[0] < HIST_PAR_EST
         * inst->featureExtractionParams.binSizeSpecFlat)
        && (inst->featureData[0] >= 0.0)) {
      i = (int)(inst->featureData[0] / inst->featureExtractionParams.binSizeSpecFlat);
      inst->histSpecFlat[i]++;
    }
    // Spectral difference
    if ((inst->featureData[4] < HIST_PAR_EST
         * inst->featureExtractionParams.binSizeSpecDiff)
        && (inst->featureData[4] >= 0.0)) {
      i = (int)(inst->featureData[4] / inst->featureExtractionParams.binSizeSpecDiff);
      inst->histSpecDiff[i]++;
    }
  }

  // extract parameters for speech/noise probability
  if (flag == 1) {
    //lrt feature: compute the average over inst->featureExtractionParams.rangeAvgHistLrt
    avgHistLrt = 0.0;
    avgHistLrtCompl = 0.0;
    avgSquareHistLrt = 0.0;
    numHistLrt = 0;
    for (i = 0; i < HIST_PAR_EST; i++) {
      binMid = ((float)i + (float)0.5) * inst->featureExtractionParams.binSizeLrt;
      if (binMid <= inst->featureExtractionParams.rangeAvgHistLrt) {
        avgHistLrt += inst->histLrt[i] * binMid;
        numHistLrt += inst->histLrt[i];
      }
      avgSquareHistLrt += inst->histLrt[i] * binMid * binMid;
      avgHistLrtCompl += inst->histLrt[i] * binMid;
    }
    if (numHistLrt > 0) {
      avgHistLrt = avgHistLrt / ((float)numHistLrt);
    }
    avgHistLrtCompl = avgHistLrtCompl / ((float)inst->modelUpdatePars[1]);
    avgSquareHistLrt = avgSquareHistLrt / ((float)inst->modelUpdatePars[1]);
    fluctLrt = avgSquareHistLrt - avgHistLrt * avgHistLrtCompl;
    // get threshold for lrt feature:
    if (fluctLrt < inst->featureExtractionParams.thresFluctLrt) {
      //very low fluct, so likely noise
      inst->priorModelPars[0] = inst->featureExtractionParams.maxLrt;
    } else {
      inst->priorModelPars[0] = inst->featureExtractionParams.factor1ModelPars
                                * avgHistLrt;
      // check if value is within min/max range
      if (inst->priorModelPars[0] < inst->featureExtractionParams.minLrt) {
        inst->priorModelPars[0] = inst->featureExtractionParams.minLrt;
      }
      if (inst->priorModelPars[0] > inst->featureExtractionParams.maxLrt) {
        inst->priorModelPars[0] = inst->featureExtractionParams.maxLrt;
      }
    }
    // done with lrt feature

    //
    // for spectral flatness and spectral difference: compute the main peaks of histogram
    maxPeak1 = 0;
    maxPeak2 = 0;
    posPeak1SpecFlat = 0.0;
    posPeak2SpecFlat = 0.0;
    weightPeak1SpecFlat = 0;
    weightPeak2SpecFlat = 0;

    // peaks for flatness
    for (i = 0; i < HIST_PAR_EST; i++) {
      binMid = ((float)i + (float)0.5) * inst->featureExtractionParams.binSizeSpecFlat;
      if (inst->histSpecFlat[i] > maxPeak1) {
        // Found new "first" peak
        maxPeak2 = maxPeak1;
        weightPeak2SpecFlat = weightPeak1SpecFlat;
        posPeak2SpecFlat = posPeak1SpecFlat;

        maxPeak1 = inst->histSpecFlat[i];
        weightPeak1SpecFlat = inst->histSpecFlat[i];
        posPeak1SpecFlat = binMid;
      } else if (inst->histSpecFlat[i] > maxPeak2) {
        // Found new "second" peak
        maxPeak2 = inst->histSpecFlat[i];
        weightPeak2SpecFlat = inst->histSpecFlat[i];
        posPeak2SpecFlat = binMid;
      }
    }

    //compute two peaks for spectral difference
    maxPeak1 = 0;
    maxPeak2 = 0;
    posPeak1SpecDiff = 0.0;
    posPeak2SpecDiff = 0.0;
    weightPeak1SpecDiff = 0;
    weightPeak2SpecDiff = 0;
    // peaks for spectral difference
    for (i = 0; i < HIST_PAR_EST; i++) {
      binMid = ((float)i + (float)0.5) * inst->featureExtractionParams.binSizeSpecDiff;
      if (inst->histSpecDiff[i] > maxPeak1) {
        // Found new "first" peak
        maxPeak2 = maxPeak1;
        weightPeak2SpecDiff = weightPeak1SpecDiff;
        posPeak2SpecDiff = posPeak1SpecDiff;

        maxPeak1 = inst->histSpecDiff[i];
        weightPeak1SpecDiff = inst->histSpecDiff[i];
        posPeak1SpecDiff = binMid;
      } else if (inst->histSpecDiff[i] > maxPeak2) {
        // Found new "second" peak
        maxPeak2 = inst->histSpecDiff[i];
        weightPeak2SpecDiff = inst->histSpecDiff[i];
        posPeak2SpecDiff = binMid;
      }
    }

    // for spectrum flatness feature
    useFeatureSpecFlat = 1;
    // merge the two peaks if they are close
    if ((fabs(posPeak2SpecFlat - posPeak1SpecFlat)
         < inst->featureExtractionParams.limitPeakSpacingSpecFlat)
        && (weightPeak2SpecFlat
            > inst->featureExtractionParams.limitPeakWeightsSpecFlat
            * weightPeak1SpecFlat)) {
      weightPeak1SpecFlat += weightPeak2SpecFlat;
      posPeak1SpecFlat = (float)0.5 * (posPeak1SpecFlat + posPeak2SpecFlat);
    }
    //reject if weight of peaks is not large enough, or peak value too small
    if (weightPeak1SpecFlat < inst->featureExtractionParams.thresWeightSpecFlat
        || posPeak1SpecFlat < inst->featureExtractionParams.thresPosSpecFlat) {
      useFeatureSpecFlat = 0;
    }
    // if selected, get the threshold
    if (useFeatureSpecFlat == 1) {
      // compute the threshold
      inst->priorModelPars[1] = inst->featureExtractionParams.factor2ModelPars
                                * posPeak1SpecFlat;
      //check if value is within min/max range
      if (inst->priorModelPars[1] < inst->featureExtractionParams.minSpecFlat) {
        inst->priorModelPars[1] = inst->featureExtractionParams.minSpecFlat;
      }
      if (inst->priorModelPars[1] > inst->featureExtractionParams.maxSpecFlat) {
        inst->priorModelPars[1] = inst->featureExtractionParams.maxSpecFlat;
      }
    }
    // done with flatness feature

    // for template feature
    useFeatureSpecDiff = 1;
    // merge the two peaks if they are close
    if ((fabs(posPeak2SpecDiff - posPeak1SpecDiff)
         < inst->featureExtractionParams.limitPeakSpacingSpecDiff)
        && (weightPeak2SpecDiff
            > inst->featureExtractionParams.limitPeakWeightsSpecDiff
            * weightPeak1SpecDiff)) {
      weightPeak1SpecDiff += weightPeak2SpecDiff;
      posPeak1SpecDiff = (float)0.5 * (posPeak1SpecDiff + posPeak2SpecDiff);
    }
    // get the threshold value
    inst->priorModelPars[3] = inst->featureExtractionParams.factor1ModelPars
                              * posPeak1SpecDiff;
    //reject if weight of peaks is not large enough
    if (weightPeak1SpecDiff < inst->featureExtractionParams.thresWeightSpecDiff) {
      useFeatureSpecDiff = 0;
    }
    //check if value is within min/max range
    if (inst->priorModelPars[3] < inst->featureExtractionParams.minSpecDiff) {
      inst->priorModelPars[3] = inst->featureExtractionParams.minSpecDiff;
    }
    if (inst->priorModelPars[3] > inst->featureExtractionParams.maxSpecDiff) {
      inst->priorModelPars[3] = inst->featureExtractionParams.maxSpecDiff;
    }
    // done with spectral difference feature

    // don't use template feature if fluctuation of lrt feature is very low:
    //  most likely just noise state
    if (fluctLrt < inst->featureExtractionParams.thresFluctLrt) {
      useFeatureSpecDiff = 0;
    }

    // select the weights between the features
    // inst->priorModelPars[4] is weight for lrt: always selected
    // inst->priorModelPars[5] is weight for spectral flatness
    // inst->priorModelPars[6] is weight for spectral difference
    featureSum = (float)(1 + useFeatureSpecFlat + useFeatureSpecDiff);
    inst->priorModelPars[4] = (float)1.0 / featureSum;
    inst->priorModelPars[5] = ((float)useFeatureSpecFlat) / featureSum;
    inst->priorModelPars[6] = ((float)useFeatureSpecDiff) / featureSum;

    // set hists to zero for next update
    if (inst->modelUpdatePars[0] >= 1) {
      for (i = 0; i < HIST_PAR_EST; i++) {
        inst->histLrt[i] = 0;
        inst->histSpecFlat[i] = 0;
        inst->histSpecDiff[i] = 0;
      }
    }
  }  // end of flag == 1
}

// Compute spectral flatness on input spectrum
// magnIn is the magnitude spectrum
// spectral flatness is returned in inst->featureData[0]
void WebRtcNs_ComputeSpectralFlatness(NSinst_t* inst, float* magnIn) {
  int i;
  int shiftLP = 1; //option to remove first bin(s) from spectral measures
  float avgSpectralFlatnessNum, avgSpectralFlatnessDen, spectralTmp;

  // comute spectral measures
  // for flatness
  avgSpectralFlatnessNum = 0.0;
  avgSpectralFlatnessDen = inst->sumMagn;
  for (i = 0; i < shiftLP; i++) {
    avgSpectralFlatnessDen -= magnIn[i];
  }
  // compute log of ratio of the geometric to arithmetic mean: check for log(0) case
  for (i = shiftLP; i < inst->magnLen; i++) {
    if (magnIn[i] > 0.0) {
      avgSpectralFlatnessNum += (float)log(magnIn[i]);
    } else {
      inst->featureData[0] -= SPECT_FL_TAVG * inst->featureData[0];
      return;
    }
  }
  //normalize
  avgSpectralFlatnessDen = avgSpectralFlatnessDen / inst->magnLen;
  avgSpectralFlatnessNum = avgSpectralFlatnessNum / inst->magnLen;

  //ratio and inverse log: check for case of log(0)
  spectralTmp = (float)exp(avgSpectralFlatnessNum) / avgSpectralFlatnessDen;

  //time-avg update of spectral flatness feature
  inst->featureData[0] += SPECT_FL_TAVG * (spectralTmp - inst->featureData[0]);
  // done with flatness feature
}

// Compute the difference measure between input spectrum and a template/learned noise spectrum
// magnIn is the input spectrum
// the reference/template spectrum is inst->magnAvgPause[i]
// returns (normalized) spectral difference in inst->featureData[4]
void WebRtcNs_ComputeSpectralDifference(NSinst_t* inst, float* magnIn) {
  // avgDiffNormMagn = var(magnIn) - cov(magnIn, magnAvgPause)^2 / var(magnAvgPause)
  int i;
  float avgPause, avgMagn, covMagnPause, varPause, varMagn, avgDiffNormMagn;

  avgPause = 0.0;
  avgMagn = inst->sumMagn;
  // compute average quantities
  for (i = 0; i < inst->magnLen; i++) {
    //conservative smooth noise spectrum from pause frames
    avgPause += inst->magnAvgPause[i];
  }
  avgPause = avgPause / ((float)inst->magnLen);
  avgMagn = avgMagn / ((float)inst->magnLen);

  covMagnPause = 0.0;
  varPause = 0.0;
  varMagn = 0.0;
  // compute variance and covariance quantities
  for (i = 0; i < inst->magnLen; i++) {
    covMagnPause += (magnIn[i] - avgMagn) * (inst->magnAvgPause[i] - avgPause);
    varPause += (inst->magnAvgPause[i] - avgPause) * (inst->magnAvgPause[i] - avgPause);
    varMagn += (magnIn[i] - avgMagn) * (magnIn[i] - avgMagn);
  }
  covMagnPause = covMagnPause / ((float)inst->magnLen);
  varPause = varPause / ((float)inst->magnLen);
  varMagn = varMagn / ((float)inst->magnLen);
  // update of average magnitude spectrum
  inst->featureData[6] += inst->signalEnergy;

  avgDiffNormMagn = varMagn - (covMagnPause * covMagnPause) / (varPause + (float)0.0001);
  // normalize and compute time-avg update of difference feature
  avgDiffNormMagn = (float)(avgDiffNormMagn / (inst->featureData[5] + (float)0.0001));
  inst->featureData[4] += SPECT_DIFF_TAVG * (avgDiffNormMagn - inst->featureData[4]);
}

// Compute speech/noise probability
// speech/noise probability is returned in: probSpeechFinal
//magn is the input magnitude spectrum
//noise is the noise spectrum
//snrLocPrior is the prior snr for each freq.
//snr loc_post is the post snr for each freq.
void WebRtcNs_SpeechNoiseProb(NSinst_t* inst, float* probSpeechFinal, float* snrLocPrior,
                              float* snrLocPost) {
  int i, sgnMap;
  float invLrt, gainPrior, indPrior;
  float logLrtTimeAvgKsum, besselTmp;
  float indicator0, indicator1, indicator2;
  float tmpFloat1, tmpFloat2;
  float weightIndPrior0, weightIndPrior1, weightIndPrior2;
  float threshPrior0, threshPrior1, threshPrior2;
  float widthPrior, widthPrior0, widthPrior1, widthPrior2;

  widthPrior0 = WIDTH_PR_MAP;
  widthPrior1 = (float)2.0 * WIDTH_PR_MAP; //width for pause region:
  // lower range, so increase width in tanh map
  widthPrior2 = (float)2.0 * WIDTH_PR_MAP; //for spectral-difference measure

  //threshold parameters for features
  threshPrior0 = inst->priorModelPars[0];
  threshPrior1 = inst->priorModelPars[1];
  threshPrior2 = inst->priorModelPars[3];

  //sign for flatness feature
  sgnMap = (int)(inst->priorModelPars[2]);

  //weight parameters for features
  weightIndPrior0 = inst->priorModelPars[4];
  weightIndPrior1 = inst->priorModelPars[5];
  weightIndPrior2 = inst->priorModelPars[6];

  // compute feature based on average LR factor
  // this is the average over all frequencies of the smooth log lrt
  logLrtTimeAvgKsum = 0.0;
  for (i = 0; i < inst->magnLen; i++) {
    tmpFloat1 = (float)1.0 + (float)2.0 * snrLocPrior[i];
    tmpFloat2 = (float)2.0 * snrLocPrior[i] / (tmpFloat1 + (float)0.0001);
    besselTmp = (snrLocPost[i] + (float)1.0) * tmpFloat2;
    inst->logLrtTimeAvg[i] += LRT_TAVG * (besselTmp - (float)log(tmpFloat1)
                                          - inst->logLrtTimeAvg[i]);
    logLrtTimeAvgKsum += inst->logLrtTimeAvg[i];
  }
  logLrtTimeAvgKsum = (float)logLrtTimeAvgKsum / (inst->magnLen);
  inst->featureData[3] = logLrtTimeAvgKsum;
  // done with computation of LR factor

  //
  //compute the indicator functions
  //

  // average lrt feature
  widthPrior = widthPrior0;
  //use larger width in tanh map for pause regions
  if (logLrtTimeAvgKsum < threshPrior0) {
    widthPrior = widthPrior1;
  }
  // compute indicator function: sigmoid map
  indicator0 = (float)0.5 * ((float)tanh(widthPrior *
      (logLrtTimeAvgKsum - threshPrior0)) + (float)1.0);

  //spectral flatness feature
  tmpFloat1 = inst->featureData[0];
  widthPrior = widthPrior0;
  //use larger width in tanh map for pause regions
  if (sgnMap == 1 && (tmpFloat1 > threshPrior1)) {
    widthPrior = widthPrior1;
  }
  if (sgnMap == -1 && (tmpFloat1 < threshPrior1)) {
    widthPrior = widthPrior1;
  }
  // compute indicator function: sigmoid map
  indicator1 = (float)0.5 * ((float)tanh((float)sgnMap * 
      widthPrior * (threshPrior1 - tmpFloat1)) + (float)1.0);

  //for template spectrum-difference
  tmpFloat1 = inst->featureData[4];
  widthPrior = widthPrior0;
  //use larger width in tanh map for pause regions
  if (tmpFloat1 < threshPrior2) {
    widthPrior = widthPrior2;
  }
  // compute indicator function: sigmoid map
  indicator2 = (float)0.5 * ((float)tanh(widthPrior * (tmpFloat1 - threshPrior2))
                             + (float)1.0);

  //combine the indicator function with the feature weights
  indPrior = weightIndPrior0 * indicator0 + weightIndPrior1 * indicator1 + weightIndPrior2
             * indicator2;
  // done with computing indicator function

  //compute the prior probability
  inst->priorSpeechProb += PRIOR_UPDATE * (indPrior - inst->priorSpeechProb);
  // make sure probabilities are within range: keep floor to 0.01
  if (inst->priorSpeechProb > 1.0) {
    inst->priorSpeechProb = (float)1.0;
  }
  if (inst->priorSpeechProb < 0.01) {
    inst->priorSpeechProb = (float)0.01;
  }

  //final speech probability: combine prior model with LR factor:
  gainPrior = ((float)1.0 - inst->priorSpeechProb) / (inst->priorSpeechProb + (float)0.0001);
  for (i = 0; i < inst->magnLen; i++) {
    invLrt = (float)exp(-inst->logLrtTimeAvg[i]);
    invLrt = (float)gainPrior * invLrt;
    probSpeechFinal[i] = (float)1.0 / ((float)1.0 + invLrt);
  }
}

int WebRtcNs_ProcessCore(NSinst_t* inst,
                         float* speechFrame,
                         float* speechFrameHB,
                         float* outFrame,
                         float* outFrameHB) {
  // main routine for noise reduction

  int     flagHB = 0;
  int     i;
  const int kStartBand = 5; // Skip first frequency bins during estimation.
  int     updateParsFlag;

  float   energy1, energy2, gain, factor, factor1, factor2;
  float   signalEnergy, sumMagn;
  float   snrPrior, currentEstimateStsa;
  float   tmpFloat1, tmpFloat2, tmpFloat3, probSpeech, probNonSpeech;
  float   gammaNoiseTmp, gammaNoiseOld;
  float   noiseUpdateTmp, fTmp;
  float   fout[BLOCKL_MAX];
  float   winData[ANAL_BLOCKL_MAX];
  float   magn[HALF_ANAL_BLOCKL], noise[HALF_ANAL_BLOCKL];
  float   theFilter[HALF_ANAL_BLOCKL], theFilterTmp[HALF_ANAL_BLOCKL];
  float   snrLocPost[HALF_ANAL_BLOCKL], snrLocPrior[HALF_ANAL_BLOCKL];
  float   probSpeechFinal[HALF_ANAL_BLOCKL] = { 0 };
  float   previousEstimateStsa[HALF_ANAL_BLOCKL];
  float   real[ANAL_BLOCKL_MAX], imag[HALF_ANAL_BLOCKL];
  // Variables during startup
  float   sum_log_i = 0.0;
  float   sum_log_i_square = 0.0;
  float   sum_log_magn = 0.0;
  float   sum_log_i_log_magn = 0.0;
  float   parametric_noise = 0.0;
  float   parametric_exp = 0.0;
  float   parametric_num = 0.0;

  // SWB variables
  int     deltaBweHB = 1;
  int     deltaGainHB = 1;
  float   decayBweHB = 1.0;
  float   gainMapParHB = 1.0;
  float   gainTimeDomainHB = 1.0;
  float   avgProbSpeechHB, avgProbSpeechHBTmp, avgFilterGainHB, gainModHB;

  // Check that initiation has been done
  if (inst->initFlag != 1) {
    return (-1);
  }
  // Check for valid pointers based on sampling rate
  if (inst->fs == 32000) {
    if (speechFrameHB == NULL) {
      return -1;
    }
    flagHB = 1;
    // range for averaging low band quantities for H band gain
    deltaBweHB = (int)inst->magnLen / 4;
    deltaGainHB = deltaBweHB;
  }
  //
  updateParsFlag = inst->modelUpdatePars[0];
  //

  // update analysis buffer for L band
  memcpy(inst->dataBuf, inst->dataBuf + inst->blockLen10ms,
         sizeof(float) * (inst->anaLen - inst->blockLen10ms));
  memcpy(inst->dataBuf + inst->anaLen - inst->blockLen10ms, speechFrame,
         sizeof(float) * inst->blockLen10ms);

  if (flagHB == 1) {
    // update analysis buffer for H band
    memcpy(inst->dataBufHB, inst->dataBufHB + inst->blockLen10ms,
           sizeof(float) * (inst->anaLen - inst->blockLen10ms));
    memcpy(inst->dataBufHB + inst->anaLen - inst->blockLen10ms, speechFrameHB,
           sizeof(float) * inst->blockLen10ms);
  }

  // check if processing needed
  if (inst->outLen == 0) {
    // windowing
    energy1 = 0.0;
    for (i = 0; i < inst->anaLen; i++) {
      winData[i] = inst->window[i] * inst->dataBuf[i];
      energy1 += winData[i] * winData[i];
    }
    if (energy1 == 0.0) {
      // synthesize the special case of zero input
      // we want to avoid updating statistics in this case:
      // Updating feature statistics when we have zeros only will cause thresholds to
      // move towards zero signal situations. This in turn has the effect that once the
      // signal is "turned on" (non-zero values) everything will be treated as speech
      // and there is no noise suppression effect. Depending on the duration of the
      // inactive signal it takes a considerable amount of time for the system to learn
      // what is noise and what is speech.

      // read out fully processed segment
      for (i = inst->windShift; i < inst->blockLen + inst->windShift; i++) {
        fout[i - inst->windShift] = inst->syntBuf[i];
      }
      // update synthesis buffer
      memcpy(inst->syntBuf, inst->syntBuf + inst->blockLen,
             sizeof(float) * (inst->anaLen - inst->blockLen));
      memset(inst->syntBuf + inst->anaLen - inst->blockLen, 0,
             sizeof(float) * inst->blockLen);

      // out buffer
      inst->outLen = inst->blockLen - inst->blockLen10ms;
      if (inst->blockLen > inst->blockLen10ms) {
        for (i = 0; i < inst->outLen; i++) {
          inst->outBuf[i] = fout[i + inst->blockLen10ms];
        }
      }
      for (i = 0; i < inst->blockLen10ms; ++i)
        outFrame[i] = WEBRTC_SPL_SAT(
            WEBRTC_SPL_WORD16_MAX, fout[i], WEBRTC_SPL_WORD16_MIN);

      // for time-domain gain of HB
      if (flagHB == 1)
        for (i = 0; i < inst->blockLen10ms; ++i)
          outFrameHB[i] = WEBRTC_SPL_SAT(
              WEBRTC_SPL_WORD16_MAX, inst->dataBufHB[i], WEBRTC_SPL_WORD16_MIN);

      return 0;
    }

    //
    inst->blockInd++; // Update the block index only when we process a block.
    // FFT
    WebRtc_rdft(inst->anaLen, 1, winData, inst->ip, inst->wfft);

    imag[0] = 0;
    real[0] = winData[0];
    magn[0] = (float)(fabs(real[0]) + 1.0f);
    imag[inst->magnLen - 1] = 0;
    real[inst->magnLen - 1] = winData[1];
    magn[inst->magnLen - 1] = (float)(fabs(real[inst->magnLen - 1]) + 1.0f);
    signalEnergy = (float)(real[0] * real[0]) + 
                   (float)(real[inst->magnLen - 1] * real[inst->magnLen - 1]);
    sumMagn = magn[0] + magn[inst->magnLen - 1];
    if (inst->blockInd < END_STARTUP_SHORT) {
      inst->initMagnEst[0] += magn[0];
      inst->initMagnEst[inst->magnLen - 1] += magn[inst->magnLen - 1];
      tmpFloat2 = log((float)(inst->magnLen - 1));
      sum_log_i = tmpFloat2;
      sum_log_i_square = tmpFloat2 * tmpFloat2;
      tmpFloat1 = log(magn[inst->magnLen - 1]);
      sum_log_magn = tmpFloat1;
      sum_log_i_log_magn = tmpFloat2 * tmpFloat1;
    }
    for (i = 1; i < inst->magnLen - 1; i++) {
      real[i] = winData[2 * i];
      imag[i] = winData[2 * i + 1];
      // magnitude spectrum
      fTmp = real[i] * real[i];
      fTmp += imag[i] * imag[i];
      signalEnergy += fTmp;
      magn[i] = ((float)sqrt(fTmp)) + 1.0f;
      sumMagn += magn[i];
      if (inst->blockInd < END_STARTUP_SHORT) {
        inst->initMagnEst[i] += magn[i];
        if (i >= kStartBand) {
          tmpFloat2 = log((float)i);
          sum_log_i += tmpFloat2;
          sum_log_i_square += tmpFloat2 * tmpFloat2;
          tmpFloat1 = log(magn[i]);
          sum_log_magn += tmpFloat1;
          sum_log_i_log_magn += tmpFloat2 * tmpFloat1;
        }
      }
    }
    signalEnergy = signalEnergy / ((float)inst->magnLen);
    inst->signalEnergy = signalEnergy;
    inst->sumMagn = sumMagn;

    //compute spectral flatness on input spectrum
    WebRtcNs_ComputeSpectralFlatness(inst, magn);
    // quantile noise estimate
    WebRtcNs_NoiseEstimation(inst, magn, noise);
    //compute simplified noise model during startup
    if (inst->blockInd < END_STARTUP_SHORT) {
      // Estimate White noise
      inst->whiteNoiseLevel += sumMagn / ((float)inst->magnLen) * inst->overdrive;
      // Estimate Pink noise parameters
      tmpFloat1 = sum_log_i_square * ((float)(inst->magnLen - kStartBand));
      tmpFloat1 -= (sum_log_i * sum_log_i);
      tmpFloat2 = (sum_log_i_square * sum_log_magn - sum_log_i * sum_log_i_log_magn);
      tmpFloat3 = tmpFloat2 / tmpFloat1;
      // Constrain the estimated spectrum to be positive
      if (tmpFloat3 < 0.0f) {
        tmpFloat3 = 0.0f;
      }
      inst->pinkNoiseNumerator += tmpFloat3;
      tmpFloat2 = (sum_log_i * sum_log_magn);
      tmpFloat2 -= ((float)(inst->magnLen - kStartBand)) * sum_log_i_log_magn;
      tmpFloat3 = tmpFloat2 / tmpFloat1;
      // Constrain the pink noise power to be in the interval [0, 1];
      if (tmpFloat3 < 0.0f) {
        tmpFloat3 = 0.0f;
      }
      if (tmpFloat3 > 1.0f) {
        tmpFloat3 = 1.0f;
      }
      inst->pinkNoiseExp += tmpFloat3;

      // Calculate frequency independent parts of parametric noise estimate.
      if (inst->pinkNoiseExp == 0.0f) {
        // Use white noise estimate
        parametric_noise = inst->whiteNoiseLevel;
      } else {
        // Use pink noise estimate
        parametric_num = exp(inst->pinkNoiseNumerator / (float)(inst->blockInd + 1));
        parametric_num *= (float)(inst->blockInd + 1);
        parametric_exp = inst->pinkNoiseExp / (float)(inst->blockInd + 1);
        parametric_noise = parametric_num / pow((float)kStartBand, parametric_exp);
      }
      for (i = 0; i < inst->magnLen; i++) {
        // Estimate the background noise using the white and pink noise parameters
        if ((inst->pinkNoiseExp > 0.0f) && (i >= kStartBand)) {
          // Use pink noise estimate
          parametric_noise = parametric_num / pow((float)i, parametric_exp);
        }
        theFilterTmp[i] = (inst->initMagnEst[i] - inst->overdrive * parametric_noise);
        theFilterTmp[i] /= (inst->initMagnEst[i] + (float)0.0001);
        // Weight quantile noise with modeled noise
        noise[i] *= (inst->blockInd);
        tmpFloat2 = parametric_noise * (END_STARTUP_SHORT - inst->blockInd);
        noise[i] += (tmpFloat2 / (float)(inst->blockInd + 1));
        noise[i] /= END_STARTUP_SHORT;
      }
    }
    //compute average signal during END_STARTUP_LONG time:
    // used to normalize spectral difference measure
    if (inst->blockInd < END_STARTUP_LONG) {
      inst->featureData[5] *= inst->blockInd;
      inst->featureData[5] += signalEnergy;
      inst->featureData[5] /= (inst->blockInd + 1);
    }

#ifdef PROCESS_FLOW_0
    if (inst->blockInd > END_STARTUP_LONG) {
      //option: average the quantile noise: for check with AEC2
      for (i = 0; i < inst->magnLen; i++) {
        noise[i] = (float)0.6 * inst->noisePrev[i] + (float)0.4 * noise[i];
      }
      for (i = 0; i < inst->magnLen; i++) {
        // Wiener with over sub-substraction:
        theFilter[i] = (magn[i] - inst->overdrive * noise[i]) / (magn[i] + (float)0.0001);
      }
    }
#else
    //start processing at frames == converged+1
    //
    // STEP 1: compute  prior and post snr based on quantile noise est
    //

    // compute DD estimate of prior SNR: needed for new method
    for (i = 0; i < inst->magnLen; i++) {
      // post snr
      snrLocPost[i] = (float)0.0;
      if (magn[i] > noise[i]) {
        snrLocPost[i] = magn[i] / (noise[i] + (float)0.0001) - (float)1.0;
      }
      // previous post snr
      // previous estimate: based on previous frame with gain filter
      previousEstimateStsa[i] = inst->magnPrev[i] / (inst->noisePrev[i] + (float)0.0001)
                                * (inst->smooth[i]);
      // DD estimate is sum of two terms: current estimate and previous estimate
      // directed decision update of snrPrior
      snrLocPrior[i] = DD_PR_SNR * previousEstimateStsa[i] + ((float)1.0 - DD_PR_SNR)
                       * snrLocPost[i];
      // post and prior snr needed for step 2
    }  // end of loop over freqs
#ifdef PROCESS_FLOW_1
    for (i = 0; i < inst->magnLen; i++) {
      // gain filter
      tmpFloat1 = inst->overdrive + snrLocPrior[i];
      tmpFloat2 = (float)snrLocPrior[i] / tmpFloat1;
      theFilter[i] = (float)tmpFloat2;
    }  // end of loop over freqs
#endif
    // done with step 1: dd computation of prior and post snr

    //
    //STEP 2: compute speech/noise likelihood
    //
#ifdef PROCESS_FLOW_2
    // compute difference of input spectrum with learned/estimated noise spectrum
    WebRtcNs_ComputeSpectralDifference(inst, magn);
    // compute histograms for parameter decisions (thresholds and weights for features)
    // parameters are extracted once every window time (=inst->modelUpdatePars[1])
    if (updateParsFlag >= 1) {
      // counter update
      inst->modelUpdatePars[3]--;
      // update histogram
      if (inst->modelUpdatePars[3] > 0) {
        WebRtcNs_FeatureParameterExtraction(inst, 0);
      }
      // compute model parameters
      if (inst->modelUpdatePars[3] == 0) {
        WebRtcNs_FeatureParameterExtraction(inst, 1);
        inst->modelUpdatePars[3] = inst->modelUpdatePars[1];
        // if wish to update only once, set flag to zero
        if (updateParsFlag == 1) {
          inst->modelUpdatePars[0] = 0;
        } else {
          // update every window:
          // get normalization for spectral difference for next window estimate
          inst->featureData[6] = inst->featureData[6]
                                 / ((float)inst->modelUpdatePars[1]);
          inst->featureData[5] = (float)0.5 * (inst->featureData[6]
                                               + inst->featureData[5]);
          inst->featureData[6] = (float)0.0;
        }
      }
    }
    // compute speech/noise probability
    WebRtcNs_SpeechNoiseProb(inst, probSpeechFinal, snrLocPrior, snrLocPost);
    // time-avg parameter for noise update
    gammaNoiseTmp = NOISE_UPDATE;
    for (i = 0; i < inst->magnLen; i++) {
      probSpeech = probSpeechFinal[i];
      probNonSpeech = (float)1.0 - probSpeech;
      // temporary noise update:
      // use it for speech frames if update value is less than previous
      noiseUpdateTmp = gammaNoiseTmp * inst->noisePrev[i] + ((float)1.0 - gammaNoiseTmp)
                       * (probNonSpeech * magn[i] + probSpeech * inst->noisePrev[i]);
      //
      // time-constant based on speech/noise state
      gammaNoiseOld = gammaNoiseTmp;
      gammaNoiseTmp = NOISE_UPDATE;
      // increase gamma (i.e., less noise update) for frame likely to be speech
      if (probSpeech > PROB_RANGE) {
        gammaNoiseTmp = SPEECH_UPDATE;
      }
      // conservative noise update
      if (probSpeech < PROB_RANGE) {
        inst->magnAvgPause[i] += GAMMA_PAUSE * (magn[i] - inst->magnAvgPause[i]);
      }
      // noise update
      if (gammaNoiseTmp == gammaNoiseOld) {
        noise[i] = noiseUpdateTmp;
      } else {
        noise[i] = gammaNoiseTmp * inst->noisePrev[i] + ((float)1.0 - gammaNoiseTmp)
                   * (probNonSpeech * magn[i] + probSpeech * inst->noisePrev[i]);
        // allow for noise update downwards:
        //  if noise update decreases the noise, it is safe, so allow it to happen
        if (noiseUpdateTmp < noise[i]) {
          noise[i] = noiseUpdateTmp;
        }
      }
    }  // end of freq loop
    // done with step 2: noise update

    //
    // STEP 3: compute dd update of prior snr and post snr based on new noise estimate
    //
    for (i = 0; i < inst->magnLen; i++) {
      // post and prior snr
      currentEstimateStsa = (float)0.0;
      if (magn[i] > noise[i]) {
        currentEstimateStsa = magn[i] / (noise[i] + (float)0.0001) - (float)1.0;
      }
      // DD estimate is sume of two terms: current estimate and previous estimate
      // directed decision update of snrPrior
      snrPrior = DD_PR_SNR * previousEstimateStsa[i] + ((float)1.0 - DD_PR_SNR)
                 * currentEstimateStsa;
      // gain filter
      tmpFloat1 = inst->overdrive + snrPrior;
      tmpFloat2 = (float)snrPrior / tmpFloat1;
      theFilter[i] = (float)tmpFloat2;
    }  // end of loop over freqs
    // done with step3
#endif
#endif

    for (i = 0; i < inst->magnLen; i++) {
      // flooring bottom
      if (theFilter[i] < inst->denoiseBound) {
        theFilter[i] = inst->denoiseBound;
      }
      // flooring top
      if (theFilter[i] > (float)1.0) {
        theFilter[i] = 1.0;
      }
      if (inst->blockInd < END_STARTUP_SHORT) {
        // flooring bottom
        if (theFilterTmp[i] < inst->denoiseBound) {
          theFilterTmp[i] = inst->denoiseBound;
        }
        // flooring top
        if (theFilterTmp[i] > (float)1.0) {
          theFilterTmp[i] = 1.0;
        }
        // Weight the two suppression filters
        theFilter[i] *= (inst->blockInd);
        theFilterTmp[i] *= (END_STARTUP_SHORT - inst->blockInd);
        theFilter[i] += theFilterTmp[i];
        theFilter[i] /= (END_STARTUP_SHORT);
      }
      // smoothing
#ifdef PROCESS_FLOW_0
      inst->smooth[i] *= SMOOTH; // value set to 0.7 in define.h file
      inst->smooth[i] += ((float)1.0 - SMOOTH) * theFilter[i];
#else
      inst->smooth[i] = theFilter[i];
#endif
      real[i] *= inst->smooth[i];
      imag[i] *= inst->smooth[i];
    }
    // keep track of noise and magn spectrum for next frame
    for (i = 0; i < inst->magnLen; i++) {
      inst->noisePrev[i] = noise[i];
      inst->magnPrev[i] = magn[i];
    }
    // back to time domain
    winData[0] = real[0];
    winData[1] = real[inst->magnLen - 1];
    for (i = 1; i < inst->magnLen - 1; i++) {
      winData[2 * i] = real[i];
      winData[2 * i + 1] = imag[i];
    }
    WebRtc_rdft(inst->anaLen, -1, winData, inst->ip, inst->wfft);

    for (i = 0; i < inst->anaLen; i++) {
      real[i] = 2.0f * winData[i] / inst->anaLen; // fft scaling
    }

    //scale factor: only do it after END_STARTUP_LONG time
    factor = (float)1.0;
    if (inst->gainmap == 1 && inst->blockInd > END_STARTUP_LONG) {
      factor1 = (float)1.0;
      factor2 = (float)1.0;

      energy2 = 0.0;
      for (i = 0; i < inst->anaLen; i++) {
        energy2 += (float)real[i] * (float)real[i];
      }
      gain = (float)sqrt(energy2 / (energy1 + (float)1.0));

#ifdef PROCESS_FLOW_2
      // scaling for new version
      if (gain > B_LIM) {
        factor1 = (float)1.0 + (float)1.3 * (gain - B_LIM);
        if (gain * factor1 > (float)1.0) {
          factor1 = (float)1.0 / gain;
        }
      }
      if (gain < B_LIM) {
        //don't reduce scale too much for pause regions:
        // attenuation here should be controlled by flooring
        if (gain <= inst->denoiseBound) {
          gain = inst->denoiseBound;
        }
        factor2 = (float)1.0 - (float)0.3 * (B_LIM - gain);
      }
      //combine both scales with speech/noise prob:
      // note prior (priorSpeechProb) is not frequency dependent
      factor = inst->priorSpeechProb * factor1 + ((float)1.0 - inst->priorSpeechProb)
               * factor2;
#else
      if (gain > B_LIM) {
        factor = (float)1.0 + (float)1.3 * (gain - B_LIM);
      } else {
        factor = (float)1.0 + (float)2.0 * (gain - B_LIM);
      }
      if (gain * factor > (float)1.0) {
        factor = (float)1.0 / gain;
      }
#endif
    }  // out of inst->gainmap==1

    // synthesis
    for (i = 0; i < inst->anaLen; i++) {
      inst->syntBuf[i] += factor * inst->window[i] * (float)real[i];
    }
    // read out fully processed segment
    for (i = inst->windShift; i < inst->blockLen + inst->windShift; i++) {
      fout[i - inst->windShift] = inst->syntBuf[i];
    }
    // update synthesis buffer
    memcpy(inst->syntBuf, inst->syntBuf + inst->blockLen,
           sizeof(float) * (inst->anaLen - inst->blockLen));
    memset(inst->syntBuf + inst->anaLen - inst->blockLen, 0,
           sizeof(float) * inst->blockLen);

    // out buffer
    inst->outLen = inst->blockLen - inst->blockLen10ms;
    if (inst->blockLen > inst->blockLen10ms) {
      for (i = 0; i < inst->outLen; i++) {
        inst->outBuf[i] = fout[i + inst->blockLen10ms];
      }
    }
  }  // end of if out.len==0
  else {
    for (i = 0; i < inst->blockLen10ms; i++) {
      fout[i] = inst->outBuf[i];
    }
    memcpy(inst->outBuf, inst->outBuf + inst->blockLen10ms,
           sizeof(float) * (inst->outLen - inst->blockLen10ms));
    memset(inst->outBuf + inst->outLen - inst->blockLen10ms, 0,
           sizeof(float) * inst->blockLen10ms);
    inst->outLen -= inst->blockLen10ms;
  }

  for (i = 0; i < inst->blockLen10ms; ++i)
    outFrame[i] = WEBRTC_SPL_SAT(
        WEBRTC_SPL_WORD16_MAX, fout[i], WEBRTC_SPL_WORD16_MIN);

  // for time-domain gain of HB
  if (flagHB == 1) {
    for (i = 0; i < inst->magnLen; i++) {
      inst->speechProbHB[i] = probSpeechFinal[i];
    }
    // average speech prob from low band
    // avg over second half (i.e., 4->8kHz) of freq. spectrum
    avgProbSpeechHB = 0.0;
    for (i = inst->magnLen - deltaBweHB - 1; i < inst->magnLen - 1; i++) {
      avgProbSpeechHB += inst->speechProbHB[i];
    }
    avgProbSpeechHB = avgProbSpeechHB / ((float)deltaBweHB);
    // average filter gain from low band
    // average over second half (i.e., 4->8kHz) of freq. spectrum
    avgFilterGainHB = 0.0;
    for (i = inst->magnLen - deltaGainHB - 1; i < inst->magnLen - 1; i++) {
      avgFilterGainHB += inst->smooth[i];
    }
    avgFilterGainHB = avgFilterGainHB / ((float)(deltaGainHB));
    avgProbSpeechHBTmp = (float)2.0 * avgProbSpeechHB - (float)1.0;
    // gain based on speech prob:
    gainModHB = (float)0.5 * ((float)1.0 + (float)tanh(gainMapParHB * avgProbSpeechHBTmp));
    //combine gain with low band gain
    gainTimeDomainHB = (float)0.5 * gainModHB + (float)0.5 * avgFilterGainHB;
    if (avgProbSpeechHB >= (float)0.5) {
      gainTimeDomainHB = (float)0.25 * gainModHB + (float)0.75 * avgFilterGainHB;
    }
    gainTimeDomainHB = gainTimeDomainHB * decayBweHB;
    //make sure gain is within flooring range
    // flooring bottom
    if (gainTimeDomainHB < inst->denoiseBound) {
      gainTimeDomainHB = inst->denoiseBound;
    }
    // flooring top
    if (gainTimeDomainHB > (float)1.0) {
      gainTimeDomainHB = 1.0;
    }
    //apply gain
    for (i = 0; i < inst->blockLen10ms; i++) {
      float o = gainTimeDomainHB * inst->dataBufHB[i];
      outFrameHB[i] = WEBRTC_SPL_SAT(
          WEBRTC_SPL_WORD16_MAX, o, WEBRTC_SPL_WORD16_MIN);
    }
  }  // end of H band gain computation
  //

  return 0;
}
