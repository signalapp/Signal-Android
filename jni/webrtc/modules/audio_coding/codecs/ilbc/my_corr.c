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

 WebRtcIlbcfix_MyCorr.c

******************************************************************/

#include "defines.h"

/*----------------------------------------------------------------*
 * compute cross correlation between sequences
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_MyCorr(
    int32_t *corr,  /* (o) correlation of seq1 and seq2 */
    int16_t *seq1,  /* (i) first sequence */
    int16_t dim1,  /* (i) dimension first seq1 */
    const int16_t *seq2, /* (i) second sequence */
    int16_t dim2   /* (i) dimension seq2 */
                          ){
  int16_t max, scale, loops;

  /* Calculate correlation between the two sequences. Scale the
     result of the multiplcication to maximum 26 bits in order
     to avoid overflow */
  max=WebRtcSpl_MaxAbsValueW16(seq1, dim1);
  scale=WebRtcSpl_GetSizeInBits(max);

  scale = (int16_t)(WEBRTC_SPL_MUL_16_16(2,scale)-26);
  if (scale<0) {
    scale=0;
  }

  loops=dim1-dim2+1;

  /* Calculate the cross correlations */
  WebRtcSpl_CrossCorrelation(corr, (int16_t*)seq2, seq1, dim2, loops, scale, 1);

  return;
}
