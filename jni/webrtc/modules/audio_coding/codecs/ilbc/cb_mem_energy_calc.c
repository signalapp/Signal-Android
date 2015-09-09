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

 WebRtcIlbcfix_CbMemEnergyCalc.c

******************************************************************/

#include "defines.h"

/* Compute the energy of the rest of the cb memory
 * by step wise adding and subtracting the next
 * sample and the last sample respectively */
void WebRtcIlbcfix_CbMemEnergyCalc(
    int32_t energy,   /* (i) input start energy */
    int16_t range,   /* (i) number of iterations */
    int16_t *ppi,   /* (i) input pointer 1 */
    int16_t *ppo,   /* (i) input pointer 2 */
    int16_t *energyW16,  /* (o) Energy in the CB vectors */
    int16_t *energyShifts, /* (o) Shift value of the energy */
    int16_t scale,   /* (i) The scaling of all energy values */
    int16_t base_size  /* (i) Index to where the energy values should be stored */
                                   )
{
  int16_t j,shft;
  int32_t tmp;
  int16_t *eSh_ptr;
  int16_t *eW16_ptr;


  eSh_ptr  = &energyShifts[1+base_size];
  eW16_ptr = &energyW16[1+base_size];

  for(j=0;j<range-1;j++) {

    /* Calculate next energy by a +/-
       operation on the edge samples */
    tmp  = WEBRTC_SPL_MUL_16_16(*ppi, *ppi);
    tmp -= WEBRTC_SPL_MUL_16_16(*ppo, *ppo);
    energy += WEBRTC_SPL_RSHIFT_W32(tmp, scale);
    energy = WEBRTC_SPL_MAX(energy, 0);

    ppi--;
    ppo--;

    /* Normalize the energy into a int16_t and store
       the number of shifts */

    shft = (int16_t)WebRtcSpl_NormW32(energy);
    *eSh_ptr++ = shft;

    tmp = WEBRTC_SPL_LSHIFT_W32(energy, shft);
    *eW16_ptr++ = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp, 16);
  }
}
