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

 WebRtcIlbcfix_Refiner.c

******************************************************************/

#include "defines.h"
#include "constants.h"
#include "enh_upsample.h"
#include "my_corr.h"

/*----------------------------------------------------------------*
 * find segment starting near idata+estSegPos that has highest
 * correlation with idata+centerStartPos through
 * idata+centerStartPos+ENH_BLOCKL-1 segment is found at a
 * resolution of ENH_UPSO times the original of the original
 * sampling rate
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Refiner(
    int16_t *updStartPos, /* (o) updated start point (Q-2) */
    int16_t *idata,   /* (i) original data buffer */
    int16_t idatal,   /* (i) dimension of idata */
    int16_t centerStartPos, /* (i) beginning center segment */
    int16_t estSegPos,  /* (i) estimated beginning other segment (Q-2) */
    int16_t *surround,  /* (i/o) The contribution from this sequence
                                           summed with earlier contributions */
    int16_t gain    /* (i) Gain to use for this sequence */
                           ){
  int16_t estSegPosRounded,searchSegStartPos,searchSegEndPos,corrdim;
  int16_t tloc,tloc2,i,st,en,fraction;

  int32_t maxtemp, scalefact;
  int16_t *filtStatePtr, *polyPtr;
  /* Stack based */
  int16_t filt[7];
  int32_t corrVecUps[ENH_CORRDIM*ENH_UPS0];
  int32_t corrVecTemp[ENH_CORRDIM];
  int16_t vect[ENH_VECTL];
  int16_t corrVec[ENH_CORRDIM];

  /* defining array bounds */

  estSegPosRounded=WEBRTC_SPL_RSHIFT_W16((estSegPos - 2),2);

  searchSegStartPos=estSegPosRounded-ENH_SLOP;

  if (searchSegStartPos<0) {
    searchSegStartPos=0;
  }
  searchSegEndPos=estSegPosRounded+ENH_SLOP;

  if(searchSegEndPos+ENH_BLOCKL >= idatal) {
    searchSegEndPos=idatal-ENH_BLOCKL-1;
  }
  corrdim=searchSegEndPos-searchSegStartPos+1;

  /* compute upsampled correlation and find
     location of max */

  WebRtcIlbcfix_MyCorr(corrVecTemp,idata+searchSegStartPos,
                       (int16_t)(corrdim+ENH_BLOCKL-1),idata+centerStartPos,ENH_BLOCKL);

  /* Calculate the rescaling factor for the correlation in order to
     put the correlation in a int16_t vector instead */
  maxtemp=WebRtcSpl_MaxAbsValueW32(corrVecTemp, (int16_t)corrdim);

  scalefact=WebRtcSpl_GetSizeInBits(maxtemp)-15;

  if (scalefact>0) {
    for (i=0;i<corrdim;i++) {
      corrVec[i]=(int16_t)WEBRTC_SPL_RSHIFT_W32(corrVecTemp[i], scalefact);
    }
  } else {
    for (i=0;i<corrdim;i++) {
      corrVec[i]=(int16_t)corrVecTemp[i];
    }
  }
  /* In order to guarantee that all values are initialized */
  for (i=corrdim;i<ENH_CORRDIM;i++) {
    corrVec[i]=0;
  }

  /* Upsample the correlation */
  WebRtcIlbcfix_EnhUpsample(corrVecUps,corrVec);

  /* Find maximum */
  tloc=WebRtcSpl_MaxIndexW32(corrVecUps, (int16_t) (ENH_UPS0*corrdim));

  /* make vector can be upsampled without ever running outside
     bounds */
  *updStartPos = (int16_t)WEBRTC_SPL_MUL_16_16(searchSegStartPos,4) + tloc + 4;

  tloc2 = WEBRTC_SPL_RSHIFT_W16((tloc+3), 2);

  st=searchSegStartPos+tloc2-ENH_FL0;

  /* initialize the vector to be filtered, stuff with zeros
     when data is outside idata buffer */
  if(st<0){
    WebRtcSpl_MemSetW16(vect, 0, (int16_t)(-st));
    WEBRTC_SPL_MEMCPY_W16(&vect[-st], idata, (ENH_VECTL+st));
  }
  else{
    en=st+ENH_VECTL;

    if(en>idatal){
      WEBRTC_SPL_MEMCPY_W16(vect, &idata[st],
                            (ENH_VECTL-(en-idatal)));
      WebRtcSpl_MemSetW16(&vect[ENH_VECTL-(en-idatal)], 0,
                          (int16_t)(en-idatal));
    }
    else {
      WEBRTC_SPL_MEMCPY_W16(vect, &idata[st], ENH_VECTL);
    }
  }
  /* Calculate which of the 4 fractions to use */
  fraction=(int16_t)WEBRTC_SPL_MUL_16_16(tloc2,ENH_UPS0)-tloc;

  /* compute the segment (this is actually a convolution) */

  filtStatePtr = filt + 6;
  polyPtr = (int16_t*)WebRtcIlbcfix_kEnhPolyPhaser[fraction];
  for (i=0;i<7;i++) {
    *filtStatePtr-- = *polyPtr++;
  }

  WebRtcSpl_FilterMAFastQ12(
      &vect[6], vect, filt,
      ENH_FLO_MULT2_PLUS1, ENH_BLOCKL);

  /* Add the contribution from this vector (scaled with gain) to the total surround vector */
  WebRtcSpl_AddAffineVectorToVector(
      surround, vect, gain,
      (int32_t)32768, 16, ENH_BLOCKL);

  return;
}
