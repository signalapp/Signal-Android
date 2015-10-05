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

 WebRtcIlbcfix_CreateAugmentedVec.c

******************************************************************/

#include "defines.h"
#include "constants.h"

/*----------------------------------------------------------------*
 *  Recreate a specific codebook vector from the augmented part.
 *
 *----------------------------------------------------------------*/

void WebRtcIlbcfix_CreateAugmentedVec(
    int16_t index,  /* (i) Index for the augmented vector to be created */
    int16_t *buffer,  /* (i) Pointer to the end of the codebook memory that
                                           is used for creation of the augmented codebook */
    int16_t *cbVec  /* (o) The construced codebook vector */
                                      ) {
  int16_t ilow;
  int16_t *ppo, *ppi;
  int16_t cbVecTmp[4];

  ilow = index-4;

  /* copy the first noninterpolated part */
  ppo = buffer-index;
  WEBRTC_SPL_MEMCPY_W16(cbVec, ppo, index);

  /* interpolation */
  ppo = buffer - 4;
  ppi = buffer - index - 4;

  /* perform cbVec[ilow+k] = ((ppi[k]*alphaTbl[k])>>15) + ((ppo[k]*alphaTbl[3-k])>>15);
     for k = 0..3
  */
  WebRtcSpl_ElementwiseVectorMult(&cbVec[ilow], ppi, WebRtcIlbcfix_kAlpha, 4, 15);
  WebRtcSpl_ReverseOrderMultArrayElements(cbVecTmp, ppo, &WebRtcIlbcfix_kAlpha[3], 4, 15);
  WebRtcSpl_AddVectorsAndShift(&cbVec[ilow], &cbVec[ilow], cbVecTmp, 4, 0);

  /* copy the second noninterpolated part */
  ppo = buffer - index;
  WEBRTC_SPL_MEMCPY_W16(cbVec+index,ppo,(SUBL-index));
}
