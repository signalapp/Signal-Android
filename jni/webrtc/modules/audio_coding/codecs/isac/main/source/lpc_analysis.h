/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * lpc_analysis.h
 *
 * LPC functions
 *
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_ANALYSIS_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_ANALYSIS_H_

#include "settings.h"
#include "structs.h"

double WebRtcIsac_LevDurb(double *a, double *k, double *r, int order);

void WebRtcIsac_GetVars(const double *input, const int16_t *pitchGains_Q12,
                       double *oldEnergy, double *varscale);

void WebRtcIsac_GetLpcCoefLb(double *inLo, double *inHi, MaskFiltstr *maskdata,
                             double signal_noise_ratio, const int16_t *pitchGains_Q12,
                             double *lo_coeff, double *hi_coeff);


void WebRtcIsac_GetLpcGain(
    double         signal_noise_ratio,
    const double*  filtCoeffVecs,
    int            numVecs,
    double*        gain,
    double         corrLo[][UB_LPC_ORDER + 1],
    const double*  varscale);

void WebRtcIsac_GetLpcCoefUb(
    double*      inSignal,
    MaskFiltstr* maskdata,
    double*      lpCoeff,
    double       corr[][UB_LPC_ORDER + 1],
    double*      varscale,
    int16_t  bandwidth);

#endif /* WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_LPC_ANALYIS_H_ */
