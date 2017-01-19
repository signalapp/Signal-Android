/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_DoThePlc.c

******************************************************************/

#include "defines.h"
#include "constants.h"
#include "comp_corr.h"
#include "bw_expand.h"

/*----------------------------------------------------------------*
 *  Packet loss concealment routine. Conceals a residual signal
 *  and LP parameters. If no packet loss, update state.
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_DoThePlc(
    int16_t *PLCresidual,  /* (o) concealed residual */
    int16_t *PLClpc,    /* (o) concealed LP parameters */
    int16_t PLI,     /* (i) packet loss indicator
                                                           0 - no PL, 1 = PL */
    int16_t *decresidual,  /* (i) decoded residual */
    int16_t *lpc,    /* (i) decoded LPC (only used for no PL) */
    int16_t inlag,    /* (i) pitch lag */
    iLBC_Dec_Inst_t *iLBCdec_inst
    /* (i/o) decoder instance */
                            ){
  int16_t i, pick;
  int32_t cross, ener, cross_comp, ener_comp = 0;
  int32_t measure, maxMeasure, energy;
  int16_t max, crossSquareMax, crossSquare;
  int16_t j, lag, tmp1, tmp2, randlag;
  int16_t shift1, shift2, shift3, shiftMax;
  int16_t scale3;
  int16_t corrLen;
  int32_t tmpW32, tmp2W32;
  int16_t use_gain;
  int16_t tot_gain;
  int16_t max_perSquare;
  int16_t scale1, scale2;
  int16_t totscale;
  int32_t nom;
  int16_t denom;
  int16_t pitchfact;
  int16_t use_lag;
  int ind;
  int16_t randvec[BLOCKL_MAX];

  /* Packet Loss */
  if (PLI == 1) {

    (*iLBCdec_inst).consPLICount += 1;

    /* if previous frame not lost,
       determine pitch pred. gain */

    if (iLBCdec_inst->prevPLI != 1) {

      /* Maximum 60 samples are correlated, preserve as high accuracy
         as possible without getting overflow */
      max = WebRtcSpl_MaxAbsValueW16((*iLBCdec_inst).prevResidual, (int16_t)iLBCdec_inst->blockl);
      scale3 = (WebRtcSpl_GetSizeInBits(max)<<1) - 25;
      if (scale3 < 0) {
        scale3 = 0;
      }

      /* Store scale for use when interpolating between the
       * concealment and the received packet */
      iLBCdec_inst->prevScale = scale3;

      /* Search around the previous lag +/-3 to find the
         best pitch period */
      lag = inlag - 3;

      /* Guard against getting outside the frame */
      corrLen = WEBRTC_SPL_MIN(60, iLBCdec_inst->blockl-(inlag+3));

      WebRtcIlbcfix_CompCorr( &cross, &ener,
                              iLBCdec_inst->prevResidual, lag, iLBCdec_inst->blockl, corrLen, scale3);

      /* Normalize and store cross^2 and the number of shifts */
      shiftMax = WebRtcSpl_GetSizeInBits(WEBRTC_SPL_ABS_W32(cross))-15;
      crossSquareMax = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(WEBRTC_SPL_SHIFT_W32(cross, -shiftMax),
                                                                WEBRTC_SPL_SHIFT_W32(cross, -shiftMax), 15);

      for (j=inlag-2;j<=inlag+3;j++) {
        WebRtcIlbcfix_CompCorr( &cross_comp, &ener_comp,
                                iLBCdec_inst->prevResidual, j, iLBCdec_inst->blockl, corrLen, scale3);

        /* Use the criteria (corr*corr)/energy to compare if
           this lag is better or not. To avoid the division,
           do a cross multiplication */
        shift1 = WebRtcSpl_GetSizeInBits(WEBRTC_SPL_ABS_W32(cross_comp))-15;
        crossSquare = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(WEBRTC_SPL_SHIFT_W32(cross_comp, -shift1),
                                                               WEBRTC_SPL_SHIFT_W32(cross_comp, -shift1), 15);

        shift2 = WebRtcSpl_GetSizeInBits(ener)-15;
        measure = WEBRTC_SPL_MUL_16_16(WEBRTC_SPL_SHIFT_W32(ener, -shift2),
                                       crossSquare);

        shift3 = WebRtcSpl_GetSizeInBits(ener_comp)-15;
        maxMeasure = WEBRTC_SPL_MUL_16_16(WEBRTC_SPL_SHIFT_W32(ener_comp, -shift3),
                                          crossSquareMax);

        /* Calculate shift value, so that the two measures can
           be put in the same Q domain */
        if(((shiftMax<<1)+shift3) > ((shift1<<1)+shift2)) {
          tmp1 = WEBRTC_SPL_MIN(31, (shiftMax<<1)+shift3-(shift1<<1)-shift2);
          tmp2 = 0;
        } else {
          tmp1 = 0;
          tmp2 = WEBRTC_SPL_MIN(31, (shift1<<1)+shift2-(shiftMax<<1)-shift3);
        }

        if ((measure>>tmp1) > (maxMeasure>>tmp2)) {
          /* New lag is better => record lag, measure and domain */
          lag = j;
          crossSquareMax = crossSquare;
          cross = cross_comp;
          shiftMax = shift1;
          ener = ener_comp;
        }
      }

      /* Calculate the periodicity for the lag with the maximum correlation.

         Definition of the periodicity:
         abs(corr(vec1, vec2))/(sqrt(energy(vec1))*sqrt(energy(vec2)))

         Work in the Square domain to simplify the calculations
         max_perSquare is less than 1 (in Q15)
      */
      tmp2W32=WebRtcSpl_DotProductWithScale(&iLBCdec_inst->prevResidual[iLBCdec_inst->blockl-corrLen],
                                            &iLBCdec_inst->prevResidual[iLBCdec_inst->blockl-corrLen],
                                            corrLen, scale3);

      if ((tmp2W32>0)&&(ener_comp>0)) {
        /* norm energies to int16_t, compute the product of the energies and
           use the upper int16_t as the denominator */

        scale1=(int16_t)WebRtcSpl_NormW32(tmp2W32)-16;
        tmp1=(int16_t)WEBRTC_SPL_SHIFT_W32(tmp2W32, scale1);

        scale2=(int16_t)WebRtcSpl_NormW32(ener)-16;
        tmp2=(int16_t)WEBRTC_SPL_SHIFT_W32(ener, scale2);
        denom=(int16_t)WEBRTC_SPL_MUL_16_16_RSFT(tmp1, tmp2, 16); /* denom in Q(scale1+scale2-16) */

        /* Square the cross correlation and norm it such that max_perSquare
           will be in Q15 after the division */

        totscale = scale1+scale2-1;
        tmp1 = (int16_t)WEBRTC_SPL_SHIFT_W32(cross, (totscale>>1));
        tmp2 = (int16_t)WEBRTC_SPL_SHIFT_W32(cross, totscale-(totscale>>1));

        nom = WEBRTC_SPL_MUL_16_16(tmp1, tmp2);
        max_perSquare = (int16_t)WebRtcSpl_DivW32W16(nom, denom);

      } else {
        max_perSquare = 0;
      }
    }

    /* previous frame lost, use recorded lag and gain */

    else {
      lag = iLBCdec_inst->prevLag;
      max_perSquare = iLBCdec_inst->perSquare;
    }

    /* Attenuate signal and scale down pitch pred gain if
       several frames lost consecutively */

    use_gain = 32767;   /* 1.0 in Q15 */

    if (iLBCdec_inst->consPLICount*iLBCdec_inst->blockl>320) {
      use_gain = 29491;  /* 0.9 in Q15 */
    } else if (iLBCdec_inst->consPLICount*iLBCdec_inst->blockl>640) {
      use_gain = 22938;  /* 0.7 in Q15 */
    } else if (iLBCdec_inst->consPLICount*iLBCdec_inst->blockl>960) {
      use_gain = 16384;  /* 0.5 in Q15 */
    } else if (iLBCdec_inst->consPLICount*iLBCdec_inst->blockl>1280) {
      use_gain = 0;   /* 0.0 in Q15 */
    }

    /* Compute mixing factor of picth repeatition and noise:
       for max_per>0.7 set periodicity to 1.0
       0.4<max_per<0.7 set periodicity to (maxper-0.4)/0.7-0.4)
       max_per<0.4 set periodicity to 0.0
    */

    if (max_perSquare>7868) { /* periodicity > 0.7  (0.7^4=0.2401 in Q15) */
      pitchfact = 32767;
    } else if (max_perSquare>839) { /* 0.4 < periodicity < 0.7 (0.4^4=0.0256 in Q15) */
      /* find best index and interpolate from that */
      ind = 5;
      while ((max_perSquare<WebRtcIlbcfix_kPlcPerSqr[ind])&&(ind>0)) {
        ind--;
      }
      /* pitch fact is approximated by first order */
      tmpW32 = (int32_t)WebRtcIlbcfix_kPlcPitchFact[ind] +
          WEBRTC_SPL_MUL_16_16_RSFT(WebRtcIlbcfix_kPlcPfSlope[ind], (max_perSquare-WebRtcIlbcfix_kPlcPerSqr[ind]), 11);

      pitchfact = (int16_t)WEBRTC_SPL_MIN(tmpW32, 32767); /* guard against overflow */

    } else { /* periodicity < 0.4 */
      pitchfact = 0;
    }

    /* avoid repetition of same pitch cycle (buzzyness) */
    use_lag = lag;
    if (lag<80) {
      use_lag = 2*lag;
    }

    /* compute concealed residual */
    energy = 0;

    for (i=0; i<iLBCdec_inst->blockl; i++) {

      /* noise component -  52 < randlagFIX < 117 */
      iLBCdec_inst->seed = (int16_t)(WEBRTC_SPL_MUL_16_16(iLBCdec_inst->seed, 31821)+(int32_t)13849);
      randlag = 53 + (int16_t)(iLBCdec_inst->seed & 63);

      pick = i - randlag;

      if (pick < 0) {
        randvec[i] = iLBCdec_inst->prevResidual[iLBCdec_inst->blockl+pick];
      } else {
        randvec[i] = iLBCdec_inst->prevResidual[pick];
      }

      /* pitch repeatition component */
      pick = i - use_lag;

      if (pick < 0) {
        PLCresidual[i] = iLBCdec_inst->prevResidual[iLBCdec_inst->blockl+pick];
      } else {
        PLCresidual[i] = PLCresidual[pick];
      }

      /* Attinuate total gain for each 10 ms */
      if (i<80) {
        tot_gain=use_gain;
      } else if (i<160) {
        tot_gain=(int16_t)WEBRTC_SPL_MUL_16_16_RSFT(31130, use_gain, 15); /* 0.95*use_gain */
      } else {
        tot_gain=(int16_t)WEBRTC_SPL_MUL_16_16_RSFT(29491, use_gain, 15); /* 0.9*use_gain */
      }


      /* mix noise and pitch repeatition */

      PLCresidual[i] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(tot_gain,
                                                                (int16_t)WEBRTC_SPL_RSHIFT_W32( (WEBRTC_SPL_MUL_16_16(pitchfact, PLCresidual[i]) +
                                                                                                       WEBRTC_SPL_MUL_16_16((32767-pitchfact), randvec[i]) + 16384),
                                                                                                      15),
                                                                15);

      /* Shifting down the result one step extra to ensure that no overflow
         will occur */
      energy += WEBRTC_SPL_MUL_16_16_RSFT(PLCresidual[i],
                                          PLCresidual[i], (iLBCdec_inst->prevScale+1));

    }

    /* less than 30 dB, use only noise */
    if (energy < (WEBRTC_SPL_SHIFT_W32(((int32_t)iLBCdec_inst->blockl*900),-(iLBCdec_inst->prevScale+1)))) {
      energy = 0;
      for (i=0; i<iLBCdec_inst->blockl; i++) {
        PLCresidual[i] = randvec[i];
      }
    }

    /* use the old LPC */
    WEBRTC_SPL_MEMCPY_W16(PLClpc, (*iLBCdec_inst).prevLpc, LPC_FILTERORDER+1);

    /* Update state in case there are multiple frame losses */
    iLBCdec_inst->prevLag = lag;
    iLBCdec_inst->perSquare = max_perSquare;
  }

  /* no packet loss, copy input */

  else {
    WEBRTC_SPL_MEMCPY_W16(PLCresidual, decresidual, iLBCdec_inst->blockl);
    WEBRTC_SPL_MEMCPY_W16(PLClpc, lpc, (LPC_FILTERORDER+1));
    iLBCdec_inst->consPLICount = 0;
  }

  /* update state */
  iLBCdec_inst->prevPLI = PLI;
  WEBRTC_SPL_MEMCPY_W16(iLBCdec_inst->prevLpc, PLClpc, (LPC_FILTERORDER+1));
  WEBRTC_SPL_MEMCPY_W16(iLBCdec_inst->prevResidual, PLCresidual, iLBCdec_inst->blockl);

  return;
}
