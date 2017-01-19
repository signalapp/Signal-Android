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
 * filterbanks.c
 *
 * This file contains function WebRtcIsac_AllPassFilter2Float,
 * WebRtcIsac_SplitAndFilter, and WebRtcIsac_FilterAndCombine
 * which implement filterbanks that produce decimated lowpass and
 * highpass versions of a signal, and performs reconstruction.
 *
 */

#include "settings.h"
#include "filterbank_tables.h"
#include "codec.h"

/* This function performs all-pass filtering--a series of first order all-pass
 * sections are used to filter the input in a cascade manner.
 * The input is overwritten!!
 */
static void WebRtcIsac_AllPassFilter2Float(float *InOut, const float *APSectionFactors,
                                           int lengthInOut, int NumberOfSections,
                                           float *FilterState)
{
  int n, j;
  float temp;
  for (j=0; j<NumberOfSections; j++){
    for (n=0;n<lengthInOut;n++){
      temp = FilterState[j] + APSectionFactors[j] * InOut[n];
      FilterState[j] = -APSectionFactors[j] * temp + InOut[n];
      InOut[n] = temp;
    }
  }
}

/* HPstcoeff_in = {a1, a2, b1 - b0 * a1, b2 - b0 * a2}; */
static const float kHpStCoefInFloat[4] =
{-1.94895953203325f, 0.94984516000000f, -0.05101826139794f, 0.05015484000000f};

/* Function WebRtcIsac_SplitAndFilter
 * This function creates low-pass and high-pass decimated versions of part of
 the input signal, and part of the signal in the input 'lookahead buffer'.

 INPUTS:
 in: a length FRAMESAMPLES array of input samples
 prefiltdata: input data structure containing the filterbank states
 and lookahead samples from the previous encoding
 iteration.
 OUTPUTS:
 LP: a FRAMESAMPLES_HALF array of low-pass filtered samples that
 have been phase equalized.  The first QLOOKAHEAD samples are
 based on the samples in the two prefiltdata->INLABUFx arrays
 each of length QLOOKAHEAD.
 The remaining FRAMESAMPLES_HALF-QLOOKAHEAD samples are based
 on the first FRAMESAMPLES_HALF-QLOOKAHEAD samples of the input
 array in[].
 HP: a FRAMESAMPLES_HALF array of high-pass filtered samples that
 have been phase equalized.  The first QLOOKAHEAD samples are
 based on the samples in the two prefiltdata->INLABUFx arrays
 each of length QLOOKAHEAD.
 The remaining FRAMESAMPLES_HALF-QLOOKAHEAD samples are based
 on the first FRAMESAMPLES_HALF-QLOOKAHEAD samples of the input
 array in[].

 LP_la: a FRAMESAMPLES_HALF array of low-pass filtered samples.
 These samples are not phase equalized. They are computed
 from the samples in the in[] array.
 HP_la: a FRAMESAMPLES_HALF array of high-pass filtered samples
 that are not phase equalized. They are computed from
 the in[] vector.
 prefiltdata: this input data structure's filterbank state and
 lookahead sample buffers are updated for the next
 encoding iteration.
*/
void WebRtcIsac_SplitAndFilterFloat(float *pin, float *LP, float *HP,
                                    double *LP_la, double *HP_la,
                                    PreFiltBankstr *prefiltdata)
{
  int k,n;
  float CompositeAPFilterState[NUMBEROFCOMPOSITEAPSECTIONS];
  float ForTransform_CompositeAPFilterState[NUMBEROFCOMPOSITEAPSECTIONS];
  float ForTransform_CompositeAPFilterState2[NUMBEROFCOMPOSITEAPSECTIONS];
  float tempinoutvec[FRAMESAMPLES+MAX_AR_MODEL_ORDER];
  float tempin_ch1[FRAMESAMPLES+MAX_AR_MODEL_ORDER];
  float tempin_ch2[FRAMESAMPLES+MAX_AR_MODEL_ORDER];
  float in[FRAMESAMPLES];
  float ftmp;


  /* High pass filter */

  for (k=0;k<FRAMESAMPLES;k++) {
    in[k] = pin[k] + kHpStCoefInFloat[2] * prefiltdata->HPstates_float[0] +
        kHpStCoefInFloat[3] * prefiltdata->HPstates_float[1];
    ftmp = pin[k] - kHpStCoefInFloat[0] * prefiltdata->HPstates_float[0] -
        kHpStCoefInFloat[1] * prefiltdata->HPstates_float[1];
    prefiltdata->HPstates_float[1] = prefiltdata->HPstates_float[0];
    prefiltdata->HPstates_float[0] = ftmp;
  }

  /*
    % backwards all-pass filtering to obtain zero-phase
    [tmp1(N2+LA:-1:LA+1, 1), state1] = filter(Q.coef, Q.coef(end:-1:1), in(N:-2:2));
    tmp1(LA:-1:1) = filter(Q.coef, Q.coef(end:-1:1), Q.LookAheadBuf1, state1);
    Q.LookAheadBuf1 = in(N:-2:N-2*LA+2);
  */
  /*Backwards all-pass filter the odd samples of the input (upper channel)
    to eventually obtain zero phase.  The composite all-pass filter (comprised of both
    the upper and lower channel all-pass filsters in series) is used for the
    filtering. */

  /* First Channel */

  /*initial state of composite filter is zero */
  for (k=0;k<NUMBEROFCOMPOSITEAPSECTIONS;k++){
    CompositeAPFilterState[k] = 0.0;
  }
  /* put every other sample of input into a temporary vector in reverse (backward) order*/
  for (k=0;k<FRAMESAMPLES_HALF;k++) {
    tempinoutvec[k] = in[FRAMESAMPLES-1-2*k];
  }

  /* now all-pass filter the backwards vector.  Output values overwrite the input vector. */
  WebRtcIsac_AllPassFilter2Float(tempinoutvec, WebRtcIsac_kCompositeApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCOMPOSITEAPSECTIONS, CompositeAPFilterState);

  /* save the backwards filtered output for later forward filtering,
     but write it in forward order*/
  for (k=0;k<FRAMESAMPLES_HALF;k++) {
    tempin_ch1[FRAMESAMPLES_HALF+QLOOKAHEAD-1-k] = tempinoutvec[k];
  }

  /* save the backwards filter state  becaue it will be transformed
     later into a forward state */
  for (k=0; k<NUMBEROFCOMPOSITEAPSECTIONS; k++) {
    ForTransform_CompositeAPFilterState[k] = CompositeAPFilterState[k];
  }

  /* now backwards filter the samples in the lookahead buffer. The samples were
     placed there in the encoding of the previous frame.  The output samples
     overwrite the input samples */
  WebRtcIsac_AllPassFilter2Float(prefiltdata->INLABUF1_float,
                                 WebRtcIsac_kCompositeApFactorsFloat, QLOOKAHEAD,
                                 NUMBEROFCOMPOSITEAPSECTIONS, CompositeAPFilterState);

  /* save the output, but write it in forward order */
  /* write the lookahead samples for the next encoding iteration. Every other
     sample at the end of the input frame is written in reverse order for the
     lookahead length. Exported in the prefiltdata structure. */
  for (k=0;k<QLOOKAHEAD;k++) {
    tempin_ch1[QLOOKAHEAD-1-k]=prefiltdata->INLABUF1_float[k];
    prefiltdata->INLABUF1_float[k]=in[FRAMESAMPLES-1-2*k];
  }

  /* Second Channel.  This is exactly like the first channel, except that the
     even samples are now filtered instead (lower channel). */
  for (k=0;k<NUMBEROFCOMPOSITEAPSECTIONS;k++){
    CompositeAPFilterState[k] = 0.0;
  }

  for (k=0;k<FRAMESAMPLES_HALF;k++) {
    tempinoutvec[k] = in[FRAMESAMPLES-2-2*k];
  }

  WebRtcIsac_AllPassFilter2Float(tempinoutvec, WebRtcIsac_kCompositeApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCOMPOSITEAPSECTIONS, CompositeAPFilterState);

  for (k=0;k<FRAMESAMPLES_HALF;k++) {
    tempin_ch2[FRAMESAMPLES_HALF+QLOOKAHEAD-1-k] = tempinoutvec[k];
  }

  for (k=0; k<NUMBEROFCOMPOSITEAPSECTIONS; k++) {
    ForTransform_CompositeAPFilterState2[k] = CompositeAPFilterState[k];
  }


  WebRtcIsac_AllPassFilter2Float(prefiltdata->INLABUF2_float,
                                 WebRtcIsac_kCompositeApFactorsFloat, QLOOKAHEAD,NUMBEROFCOMPOSITEAPSECTIONS,
                                 CompositeAPFilterState);

  for (k=0;k<QLOOKAHEAD;k++) {
    tempin_ch2[QLOOKAHEAD-1-k]=prefiltdata->INLABUF2_float[k];
    prefiltdata->INLABUF2_float[k]=in[FRAMESAMPLES-2-2*k];
  }

  /* Transform filter states from backward to forward */
  /*At this point, each of the states of the backwards composite filters for the
    two channels are transformed into forward filtering states for the corresponding
    forward channel filters.  Each channel's forward filtering state from the previous
    encoding iteration is added to the transformed state to get a proper forward state */

  /* So the existing NUMBEROFCOMPOSITEAPSECTIONS x 1 (4x1) state vector is multiplied by a
     NUMBEROFCHANNELAPSECTIONSxNUMBEROFCOMPOSITEAPSECTIONS (2x4) transform matrix to get the
     new state that is added to the previous 2x1 input state */

  for (k=0;k<NUMBEROFCHANNELAPSECTIONS;k++){ /* k is row variable */
    for (n=0; n<NUMBEROFCOMPOSITEAPSECTIONS;n++){/* n is column variable */
      prefiltdata->INSTAT1_float[k] += ForTransform_CompositeAPFilterState[n]*
          WebRtcIsac_kTransform1Float[k*NUMBEROFCHANNELAPSECTIONS+n];
      prefiltdata->INSTAT2_float[k] += ForTransform_CompositeAPFilterState2[n]*
          WebRtcIsac_kTransform2Float[k*NUMBEROFCHANNELAPSECTIONS+n];
    }
  }

  /*obtain polyphase components by forward all-pass filtering through each channel */
  /* the backward filtered samples are now forward filtered with the corresponding channel filters */
  /* The all pass filtering automatically updates the filter states which are exported in the
     prefiltdata structure */
  WebRtcIsac_AllPassFilter2Float(tempin_ch1,WebRtcIsac_kUpperApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCHANNELAPSECTIONS, prefiltdata->INSTAT1_float);
  WebRtcIsac_AllPassFilter2Float(tempin_ch2,WebRtcIsac_kLowerApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCHANNELAPSECTIONS, prefiltdata->INSTAT2_float);

  /* Now Construct low-pass and high-pass signals as combinations of polyphase components */
  for (k=0; k<FRAMESAMPLES_HALF; k++) {
    LP[k] = 0.5f*(tempin_ch1[k] + tempin_ch2[k]);/* low pass signal*/
    HP[k] = 0.5f*(tempin_ch1[k] - tempin_ch2[k]);/* high pass signal*/
  }

  /* Lookahead LP and HP signals */
  /* now create low pass and high pass signals of the input vector.  However, no
     backwards filtering is performed, and hence no phase equalization is involved.
     Also, the input contains some samples that are lookahead samples.  The high pass
     and low pass signals that are created are used outside this function for analysis
     (not encoding) purposes */

  /* set up input */
  for (k=0; k<FRAMESAMPLES_HALF; k++) {
    tempin_ch1[k]=in[2*k+1];
    tempin_ch2[k]=in[2*k];
  }

  /* the input filter states are passed in and updated by the all-pass filtering routine and
     exported in the prefiltdata structure*/
  WebRtcIsac_AllPassFilter2Float(tempin_ch1,WebRtcIsac_kUpperApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCHANNELAPSECTIONS, prefiltdata->INSTATLA1_float);
  WebRtcIsac_AllPassFilter2Float(tempin_ch2,WebRtcIsac_kLowerApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCHANNELAPSECTIONS, prefiltdata->INSTATLA2_float);

  for (k=0; k<FRAMESAMPLES_HALF; k++) {
    LP_la[k] = (float)(0.5f*(tempin_ch1[k] + tempin_ch2[k])); /*low pass */
    HP_la[k] = (double)(0.5f*(tempin_ch1[k] - tempin_ch2[k])); /* high pass */
  }


}/*end of WebRtcIsac_SplitAndFilter */


/* Combining */

/* HPstcoeff_out_1 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2}; */
static const float kHpStCoefOut1Float[4] =
{-1.99701049409000f, 0.99714204490000f, 0.01701049409000f, -0.01704204490000f};

/* HPstcoeff_out_2 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2}; */
static const float kHpStCoefOut2Float[4] =
{-1.98645294509837f, 0.98672435560000f, 0.00645294509837f, -0.00662435560000f};


/* Function WebRtcIsac_FilterAndCombine */
/* This is a decoder function that takes the decimated
   length FRAMESAMPLES_HALF input low-pass and
   high-pass signals and creates a reconstructed fullband
   output signal of length FRAMESAMPLES. WebRtcIsac_FilterAndCombine
   is the sibling function of WebRtcIsac_SplitAndFilter */
/* INPUTS:
   inLP: a length FRAMESAMPLES_HALF array of input low-pass
   samples.
   inHP: a length FRAMESAMPLES_HALF array of input high-pass
   samples.
   postfiltdata: input data structure containing the filterbank
   states from the previous decoding iteration.
   OUTPUTS:
   Out: a length FRAMESAMPLES array of output reconstructed
   samples (fullband) based on the input low-pass and
   high-pass signals.
   postfiltdata: the input data structure containing the filterbank
   states is updated for the next decoding iteration */
void WebRtcIsac_FilterAndCombineFloat(float *InLP,
                                      float *InHP,
                                      float *Out,
                                      PostFiltBankstr *postfiltdata)
{
  int k;
  float tempin_ch1[FRAMESAMPLES+MAX_AR_MODEL_ORDER];
  float tempin_ch2[FRAMESAMPLES+MAX_AR_MODEL_ORDER];
  float ftmp, ftmp2;

  /* Form the polyphase signals*/
  for (k=0;k<FRAMESAMPLES_HALF;k++) {
    tempin_ch1[k]=InLP[k]+InHP[k]; /* Construct a new upper channel signal*/
    tempin_ch2[k]=InLP[k]-InHP[k]; /* Construct a new lower channel signal*/
  }


  /* all-pass filter the new upper channel signal. HOWEVER, use the all-pass filter factors
     that were used as a lower channel at the encoding side.  So at the decoder, the
     corresponding all-pass filter factors for each channel are swapped.*/
  WebRtcIsac_AllPassFilter2Float(tempin_ch1, WebRtcIsac_kLowerApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCHANNELAPSECTIONS,postfiltdata->STATE_0_UPPER_float);

  /* Now, all-pass filter the new lower channel signal. But since all-pass filter factors
     at the decoder are swapped from the ones at the encoder, the 'upper' channel
     all-pass filter factors (WebRtcIsac_kUpperApFactorsFloat) are used to filter this new
     lower channel signal */
  WebRtcIsac_AllPassFilter2Float(tempin_ch2, WebRtcIsac_kUpperApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCHANNELAPSECTIONS,postfiltdata->STATE_0_LOWER_float);


  /* Merge outputs to form the full length output signal.*/
  for (k=0;k<FRAMESAMPLES_HALF;k++) {
    Out[2*k]=tempin_ch2[k];
    Out[2*k+1]=tempin_ch1[k];
  }


  /* High pass filter */

  for (k=0;k<FRAMESAMPLES;k++) {
    ftmp2 = Out[k] + kHpStCoefOut1Float[2] * postfiltdata->HPstates1_float[0] +
        kHpStCoefOut1Float[3] * postfiltdata->HPstates1_float[1];
    ftmp = Out[k] - kHpStCoefOut1Float[0] * postfiltdata->HPstates1_float[0] -
        kHpStCoefOut1Float[1] * postfiltdata->HPstates1_float[1];
    postfiltdata->HPstates1_float[1] = postfiltdata->HPstates1_float[0];
    postfiltdata->HPstates1_float[0] = ftmp;
    Out[k] = ftmp2;
  }

  for (k=0;k<FRAMESAMPLES;k++) {
    ftmp2 = Out[k] + kHpStCoefOut2Float[2] * postfiltdata->HPstates2_float[0] +
        kHpStCoefOut2Float[3] * postfiltdata->HPstates2_float[1];
    ftmp = Out[k] - kHpStCoefOut2Float[0] * postfiltdata->HPstates2_float[0] -
        kHpStCoefOut2Float[1] * postfiltdata->HPstates2_float[1];
    postfiltdata->HPstates2_float[1] = postfiltdata->HPstates2_float[0];
    postfiltdata->HPstates2_float[0] = ftmp;
    Out[k] = ftmp2;
  }
}
