/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory.h>
#ifdef WEBRTC_ANDROID
#include <stdlib.h>
#endif
#include "pitch_estimator.h"
#include "lpc_analysis.h"
#include "codec.h"



void WebRtcIsac_AllPoleFilter(double *InOut, double *Coef, int lengthInOut, int orderCoef){

  /* the state of filter is assumed to be in InOut[-1] to InOut[-orderCoef] */
  double scal;
  double sum;
  int n,k;

  //if (fabs(Coef[0]-1.0)<0.001) {
  if ( (Coef[0] > 0.9999) && (Coef[0] < 1.0001) )
  {
    for(n = 0; n < lengthInOut; n++)
    {
      sum = Coef[1] * InOut[-1];
      for(k = 2; k <= orderCoef; k++){
        sum += Coef[k] * InOut[-k];
      }
      *InOut++ -= sum;
    }
  }
  else
  {
    scal = 1.0 / Coef[0];
    for(n=0;n<lengthInOut;n++)
    {
      *InOut *= scal;
      for(k=1;k<=orderCoef;k++){
        *InOut -= scal*Coef[k]*InOut[-k];
      }
      InOut++;
    }
  }
}


void WebRtcIsac_AllZeroFilter(double *In, double *Coef, int lengthInOut, int orderCoef, double *Out){

  /* the state of filter is assumed to be in In[-1] to In[-orderCoef] */

  int n, k;
  double tmp;

  for(n = 0; n < lengthInOut; n++)
  {
    tmp = In[0] * Coef[0];

    for(k = 1; k <= orderCoef; k++){
      tmp += Coef[k] * In[-k];
    }

    *Out++ = tmp;
    In++;
  }
}



void WebRtcIsac_ZeroPoleFilter(double *In, double *ZeroCoef, double *PoleCoef, int lengthInOut, int orderCoef, double *Out){

  /* the state of the zero section is assumed to be in In[-1] to In[-orderCoef] */
  /* the state of the pole section is assumed to be in Out[-1] to Out[-orderCoef] */

  WebRtcIsac_AllZeroFilter(In,ZeroCoef,lengthInOut,orderCoef,Out);
  WebRtcIsac_AllPoleFilter(Out,PoleCoef,lengthInOut,orderCoef);
}


void WebRtcIsac_AutoCorr(
    double *r,
    const double *x,
    int N,
    int order
                        )
{
  int  lag, n;
  double sum, prod;
  const double *x_lag;

  for (lag = 0; lag <= order; lag++)
  {
    sum = 0.0f;
    x_lag = &x[lag];
    prod = x[0] * x_lag[0];
    for (n = 1; n < N - lag; n++) {
      sum += prod;
      prod = x[n] * x_lag[n];
    }
    sum += prod;
    r[lag] = sum;
  }

}


void WebRtcIsac_BwExpand(double *out, double *in, double coef, short length) {
  int i;
  double  chirp;

  chirp = coef;

  out[0] = in[0];
  for (i = 1; i < length; i++) {
    out[i] = chirp * in[i];
    chirp *= coef;
  }
}

void WebRtcIsac_WeightingFilter(const double *in, double *weiout, double *whiout, WeightFiltstr *wfdata) {

  double  tmpbuffer[PITCH_FRAME_LEN + PITCH_WLPCBUFLEN];
  double  corr[PITCH_WLPCORDER+1], rc[PITCH_WLPCORDER+1];
  double apol[PITCH_WLPCORDER+1], apolr[PITCH_WLPCORDER+1];
  double  rho=0.9, *inp, *dp, *dp2;
  double  whoutbuf[PITCH_WLPCBUFLEN + PITCH_WLPCORDER];
  double  weoutbuf[PITCH_WLPCBUFLEN + PITCH_WLPCORDER];
  double  *weo, *who, opol[PITCH_WLPCORDER+1], ext[PITCH_WLPCWINLEN];
  int     k, n, endpos, start;

  /* Set up buffer and states */
  memcpy(tmpbuffer, wfdata->buffer, sizeof(double) * PITCH_WLPCBUFLEN);
  memcpy(tmpbuffer+PITCH_WLPCBUFLEN, in, sizeof(double) * PITCH_FRAME_LEN);
  memcpy(wfdata->buffer, tmpbuffer+PITCH_FRAME_LEN, sizeof(double) * PITCH_WLPCBUFLEN);

  dp=weoutbuf;
  dp2=whoutbuf;
  for (k=0;k<PITCH_WLPCORDER;k++) {
    *dp++ = wfdata->weostate[k];
    *dp2++ = wfdata->whostate[k];
    opol[k]=0.0;
  }
  opol[0]=1.0;
  opol[PITCH_WLPCORDER]=0.0;
  weo=dp;
  who=dp2;

  endpos=PITCH_WLPCBUFLEN + PITCH_SUBFRAME_LEN;
  inp=tmpbuffer + PITCH_WLPCBUFLEN;

  for (n=0; n<PITCH_SUBFRAMES; n++) {
    /* Windowing */
    start=endpos-PITCH_WLPCWINLEN;
    for (k=0; k<PITCH_WLPCWINLEN; k++) {
      ext[k]=wfdata->window[k]*tmpbuffer[start+k];
    }

    /* Get LPC polynomial */
    WebRtcIsac_AutoCorr(corr, ext, PITCH_WLPCWINLEN, PITCH_WLPCORDER);
    corr[0]=1.01*corr[0]+1.0; /* White noise correction */
    WebRtcIsac_LevDurb(apol, rc, corr, PITCH_WLPCORDER);
    WebRtcIsac_BwExpand(apolr, apol, rho, PITCH_WLPCORDER+1);

    /* Filtering */
    WebRtcIsac_ZeroPoleFilter(inp, apol, apolr, PITCH_SUBFRAME_LEN, PITCH_WLPCORDER, weo);
    WebRtcIsac_ZeroPoleFilter(inp, apolr, opol, PITCH_SUBFRAME_LEN, PITCH_WLPCORDER, who);

    inp+=PITCH_SUBFRAME_LEN;
    endpos+=PITCH_SUBFRAME_LEN;
    weo+=PITCH_SUBFRAME_LEN;
    who+=PITCH_SUBFRAME_LEN;
  }

  /* Export filter states */
  for (k=0;k<PITCH_WLPCORDER;k++) {
    wfdata->weostate[k]=weoutbuf[PITCH_FRAME_LEN+k];
    wfdata->whostate[k]=whoutbuf[PITCH_FRAME_LEN+k];
  }

  /* Export output data */
  memcpy(weiout, weoutbuf+PITCH_WLPCORDER, sizeof(double) * PITCH_FRAME_LEN);
  memcpy(whiout, whoutbuf+PITCH_WLPCORDER, sizeof(double) * PITCH_FRAME_LEN);
}


static const double APupper[ALLPASSSECTIONS] = {0.0347, 0.3826};
static const double APlower[ALLPASSSECTIONS] = {0.1544, 0.744};



void WebRtcIsac_AllpassFilterForDec(double *InOut,
                                   const double *APSectionFactors,
                                   int lengthInOut,
                                   double *FilterState)
{
  //This performs all-pass filtering--a series of first order all-pass sections are used
  //to filter the input in a cascade manner.
  int n,j;
  double temp;
  for (j=0; j<ALLPASSSECTIONS; j++){
    for (n=0;n<lengthInOut;n+=2){
      temp = InOut[n]; //store input
      InOut[n] = FilterState[j] + APSectionFactors[j]*temp;
      FilterState[j] = -APSectionFactors[j]*InOut[n] + temp;
    }
  }
}

void WebRtcIsac_DecimateAllpass(const double *in,
                                double *state_in,        /* array of size: 2*ALLPASSSECTIONS+1 */
                                int N,                   /* number of input samples */
                                double *out)             /* array of size N/2 */
{
  int n;
  double data_vec[PITCH_FRAME_LEN];

  /* copy input */
  memcpy(data_vec+1, in, sizeof(double) * (N-1));

  data_vec[0] = state_in[2*ALLPASSSECTIONS];   //the z^(-1) state
  state_in[2*ALLPASSSECTIONS] = in[N-1];

  WebRtcIsac_AllpassFilterForDec(data_vec+1, APupper, N, state_in);
  WebRtcIsac_AllpassFilterForDec(data_vec, APlower, N, state_in+ALLPASSSECTIONS);

  for (n=0;n<N/2;n++)
    out[n] = data_vec[2*n] + data_vec[2*n+1];

}



/* create high-pass filter ocefficients
 * z = 0.998 * exp(j*2*pi*35/8000);
 * p = 0.94 * exp(j*2*pi*140/8000);
 * HP_b = [1, -2*real(z), abs(z)^2];
 * HP_a = [1, -2*real(p), abs(p)^2]; */
static const double a_coef[2] = { 1.86864659625574, -0.88360000000000};
static const double b_coef[2] = {-1.99524591718270,  0.99600400000000};
static const float a_coef_float[2] = { 1.86864659625574f, -0.88360000000000f};
static const float b_coef_float[2] = {-1.99524591718270f,  0.99600400000000f};

/* second order high-pass filter */
void WebRtcIsac_Highpass(const double *in, double *out, double *state, int N)
{
  int k;

  for (k=0; k<N; k++) {
    *out = *in + state[1];
    state[1] = state[0] + b_coef[0] * *in + a_coef[0] * *out;
    state[0] = b_coef[1] * *in++ + a_coef[1] * *out++;
  }
}

void WebRtcIsac_Highpass_float(const float *in, double *out, double *state, int N)
{
  int k;

  for (k=0; k<N; k++) {
    *out = (double)*in + state[1];
    state[1] = state[0] + b_coef_float[0] * *in + a_coef_float[0] * *out;
    state[0] = b_coef_float[1] * (double)*in++ + a_coef_float[1] * *out++;
  }
}
