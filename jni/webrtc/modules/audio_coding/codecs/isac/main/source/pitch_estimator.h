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
 * pitch_estimator.h
 *
 * Pitch functions
 *
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_PITCH_ESTIMATOR_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_PITCH_ESTIMATOR_H_

#include "structs.h"



void WebRtcIsac_PitchAnalysis(const double *in,               /* PITCH_FRAME_LEN samples */
                              double *out,                    /* PITCH_FRAME_LEN+QLOOKAHEAD samples */
                              PitchAnalysisStruct *State,
                              double *lags,
                              double *gains);

void WebRtcIsac_InitializePitch(const double *in,
                                const double old_lag,
                                const double old_gain,
                                PitchAnalysisStruct *State,
                                double *lags);

void WebRtcIsac_PitchfilterPre(double *indat,
                               double *outdat,
                               PitchFiltstr *pfp,
                               double *lags,
                               double *gains);

void WebRtcIsac_PitchfilterPost(double *indat,
                                double *outdat,
                                PitchFiltstr *pfp,
                                double *lags,
                                double *gains);

void WebRtcIsac_PitchfilterPre_la(double *indat,
                                  double *outdat,
                                  PitchFiltstr *pfp,
                                  double *lags,
                                  double *gains);

void WebRtcIsac_PitchfilterPre_gains(double *indat,
                                     double *outdat,
                                     double out_dG[][PITCH_FRAME_LEN + QLOOKAHEAD],
                                     PitchFiltstr *pfp,
                                     double *lags,
                                     double *gains);

void WebRtcIsac_WeightingFilter(const double *in, double *weiout, double *whiout, WeightFiltstr *wfdata);

void WebRtcIsac_Highpass(const double *in, double *out, double *state, int N);

void WebRtcIsac_DecimateAllpass(const double *in,
                                double *state_in,        /* array of size: 2*ALLPASSSECTIONS+1 */
                                int N,                   /* number of input samples */
                                double *out);            /* array of size N/2 */

#endif /* WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_PITCH_ESTIMATOR_H_ */
