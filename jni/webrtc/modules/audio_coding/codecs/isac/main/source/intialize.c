/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/* encode.c  - Encoding function for the iSAC coder */

#include "structs.h"
#include "codec.h"
#include "pitch_estimator.h"

#include <math.h>

void WebRtcIsac_InitMasking(MaskFiltstr *maskdata) {

  int k;

  for (k = 0; k < WINLEN; k++) {
    maskdata->DataBufferLo[k] = 0.0;
    maskdata->DataBufferHi[k] = 0.0;
  }
  for (k = 0; k < ORDERLO+1; k++) {
    maskdata->CorrBufLo[k] = 0.0;
    maskdata->PreStateLoF[k] = 0.0;
    maskdata->PreStateLoG[k] = 0.0;
    maskdata->PostStateLoF[k] = 0.0;
    maskdata->PostStateLoG[k] = 0.0;
  }
  for (k = 0; k < ORDERHI+1; k++) {
    maskdata->CorrBufHi[k] = 0.0;
    maskdata->PreStateHiF[k] = 0.0;
    maskdata->PreStateHiG[k] = 0.0;
    maskdata->PostStateHiF[k] = 0.0;
    maskdata->PostStateHiG[k] = 0.0;
  }

  maskdata->OldEnergy = 10.0;
  return;
}

void WebRtcIsac_InitPreFilterbank(PreFiltBankstr *prefiltdata)
{
  int k;

  for (k = 0; k < QLOOKAHEAD; k++) {
    prefiltdata->INLABUF1[k] = 0;
    prefiltdata->INLABUF2[k] = 0;

    prefiltdata->INLABUF1_float[k] = 0;
    prefiltdata->INLABUF2_float[k] = 0;
  }
  for (k = 0; k < 2*(QORDER-1); k++) {
    prefiltdata->INSTAT1[k] = 0;
    prefiltdata->INSTAT2[k] = 0;
    prefiltdata->INSTATLA1[k] = 0;
    prefiltdata->INSTATLA2[k] = 0;

    prefiltdata->INSTAT1_float[k] = 0;
    prefiltdata->INSTAT2_float[k] = 0;
    prefiltdata->INSTATLA1_float[k] = 0;
    prefiltdata->INSTATLA2_float[k] = 0;
  }

  /* High pass filter states */
  prefiltdata->HPstates[0] = 0.0;
  prefiltdata->HPstates[1] = 0.0;

  prefiltdata->HPstates_float[0] = 0.0f;
  prefiltdata->HPstates_float[1] = 0.0f;

  return;
}

void WebRtcIsac_InitPostFilterbank(PostFiltBankstr *postfiltdata)
{
  int k;

  for (k = 0; k < 2*POSTQORDER; k++) {
    postfiltdata->STATE_0_LOWER[k] = 0;
    postfiltdata->STATE_0_UPPER[k] = 0;

    postfiltdata->STATE_0_LOWER_float[k] = 0;
    postfiltdata->STATE_0_UPPER_float[k] = 0;
  }

  /* High pass filter states */
  postfiltdata->HPstates1[0] = 0.0;
  postfiltdata->HPstates1[1] = 0.0;

  postfiltdata->HPstates2[0] = 0.0;
  postfiltdata->HPstates2[1] = 0.0;

  postfiltdata->HPstates1_float[0] = 0.0f;
  postfiltdata->HPstates1_float[1] = 0.0f;

  postfiltdata->HPstates2_float[0] = 0.0f;
  postfiltdata->HPstates2_float[1] = 0.0f;

  return;
}


void WebRtcIsac_InitPitchFilter(PitchFiltstr *pitchfiltdata)
{
  int k;

  for (k = 0; k < PITCH_BUFFSIZE; k++) {
    pitchfiltdata->ubuf[k] = 0.0;
  }
  pitchfiltdata->ystate[0] = 0.0;
  for (k = 1; k < (PITCH_DAMPORDER); k++) {
    pitchfiltdata->ystate[k] = 0.0;
  }
  pitchfiltdata->oldlagp[0] = 50.0;
  pitchfiltdata->oldgainp[0] = 0.0;
}

void WebRtcIsac_InitWeightingFilter(WeightFiltstr *wfdata)
{
  int k;
  double t, dtmp, dtmp2, denum, denum2;

  for (k=0;k<PITCH_WLPCBUFLEN;k++)
    wfdata->buffer[k]=0.0;

  for (k=0;k<PITCH_WLPCORDER;k++) {
    wfdata->istate[k]=0.0;
    wfdata->weostate[k]=0.0;
    wfdata->whostate[k]=0.0;
  }

  /* next part should be in Matlab, writing to a global table */
  t = 0.5;
  denum = 1.0 / ((double) PITCH_WLPCWINLEN);
  denum2 = denum * denum;
  for (k=0;k<PITCH_WLPCWINLEN;k++) {
    dtmp = PITCH_WLPCASYM * t * denum + (1-PITCH_WLPCASYM) * t * t * denum2;
    dtmp *= 3.14159265;
    dtmp2 = sin(dtmp);
    wfdata->window[k] = dtmp2 * dtmp2;
    t++;
  }
}

/* clear all buffers */
void WebRtcIsac_InitPitchAnalysis(PitchAnalysisStruct *State)
{
  int k;

  for (k = 0; k < PITCH_CORR_LEN2+PITCH_CORR_STEP2+PITCH_MAX_LAG/2-PITCH_FRAME_LEN/2+2; k++)
    State->dec_buffer[k] = 0.0;
  for (k = 0; k < 2*ALLPASSSECTIONS+1; k++)
    State->decimator_state[k] = 0.0;
  for (k = 0; k < 2; k++)
    State->hp_state[k] = 0.0;
  for (k = 0; k < QLOOKAHEAD; k++)
    State->whitened_buf[k] = 0.0;
  for (k = 0; k < QLOOKAHEAD; k++)
    State->inbuf[k] = 0.0;

  WebRtcIsac_InitPitchFilter(&(State->PFstr_wght));

  WebRtcIsac_InitPitchFilter(&(State->PFstr));

  WebRtcIsac_InitWeightingFilter(&(State->Wghtstr));
}
