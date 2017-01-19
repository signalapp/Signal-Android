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

 WebRtcIlbcfix_GetSyncSeq.c

******************************************************************/

#include "defines.h"
#include "constants.h"
#include "refiner.h"
#include "nearest_neighbor.h"

/*----------------------------------------------------------------*
 * get the pitch-synchronous sample sequence
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_GetSyncSeq(
    int16_t *idata,   /* (i) original data */
    int16_t idatal,   /* (i) dimension of data */
    int16_t centerStartPos, /* (i) where current block starts */
    int16_t *period,   /* (i) rough-pitch-period array       (Q-2) */
    int16_t *plocs,   /* (i) where periods of period array are taken (Q-2) */
    int16_t periodl,   /* (i) dimension period array */
    int16_t hl,    /* (i) 2*hl+1 is the number of sequences */
    int16_t *surround  /* (i/o) The contribution from this sequence
                                summed with earlier contributions */
                              ){
  int16_t i,centerEndPos,q;
  /* Stack based */
  int16_t lagBlock[2*ENH_HL+1];
  int16_t blockStartPos[2*ENH_HL+1]; /* Defines the position to search around (Q2) */
  int16_t plocs2[ENH_PLOCSL];

  centerEndPos=centerStartPos+ENH_BLOCKL-1;

  /* present (find predicted lag from this position) */

  WebRtcIlbcfix_NearestNeighbor(lagBlock+hl,plocs,
                                (int16_t)WEBRTC_SPL_MUL_16_16(2, (centerStartPos+centerEndPos)),
                                periodl);

  blockStartPos[hl]=(int16_t)WEBRTC_SPL_MUL_16_16(4, centerStartPos);

  /* past (find predicted position and perform a refined
     search to find the best sequence) */

  for(q=hl-1;q>=0;q--) {
    blockStartPos[q]=blockStartPos[q+1]-period[lagBlock[q+1]];

    WebRtcIlbcfix_NearestNeighbor(lagBlock+q, plocs,
                                  (int16_t)(blockStartPos[q] + (int16_t)WEBRTC_SPL_MUL_16_16(4, ENH_BLOCKL_HALF)-period[lagBlock[q+1]]),
                                  periodl);

    if((blockStartPos[q]-(int16_t)WEBRTC_SPL_MUL_16_16(4, ENH_OVERHANG))>=0) {

      /* Find the best possible sequence in the 4 times upsampled
         domain around blockStartPos+q */
      WebRtcIlbcfix_Refiner(blockStartPos+q,idata,idatal,
                            centerStartPos,blockStartPos[q],surround,WebRtcIlbcfix_kEnhWt[q]);

    } else {
      /* Don't add anything since this sequence would
         be outside the buffer */
    }
  }

  /* future (find predicted position and perform a refined
     search to find the best sequence) */

  for(i=0;i<periodl;i++) {
    plocs2[i]=(plocs[i]-period[i]);
  }

  for(q=hl+1;q<=WEBRTC_SPL_MUL_16_16(2, hl);q++) {

    WebRtcIlbcfix_NearestNeighbor(lagBlock+q,plocs2,
                                  (int16_t)(blockStartPos[q-1]+
                                                  (int16_t)WEBRTC_SPL_MUL_16_16(4, ENH_BLOCKL_HALF)),periodl);

    blockStartPos[q]=blockStartPos[q-1]+period[lagBlock[q]];

    if( (blockStartPos[q]+(int16_t)WEBRTC_SPL_MUL_16_16(4, (ENH_BLOCKL+ENH_OVERHANG)))
        <
        (int16_t)WEBRTC_SPL_MUL_16_16(4, idatal)) {

      /* Find the best possible sequence in the 4 times upsampled
         domain around blockStartPos+q */
      WebRtcIlbcfix_Refiner(blockStartPos+q, idata, idatal,
                            centerStartPos,blockStartPos[q],surround,WebRtcIlbcfix_kEnhWt[2*hl-q]);

    }
    else {
      /* Don't add anything since this sequence would
         be outside the buffer */
    }
  }
}
