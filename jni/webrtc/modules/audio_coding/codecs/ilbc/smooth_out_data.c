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

 WebRtcIlbcfix_Smooth_odata.c

******************************************************************/

#include "defines.h"
#include "constants.h"

int32_t WebRtcIlbcfix_Smooth_odata(
    int16_t *odata,
    int16_t *psseq,
    int16_t *surround,
    int16_t C)
{
  int i;

  int16_t err;
  int32_t errs;

  for(i=0;i<80;i++) {
    odata[i]= (int16_t)((C * surround[i] + 1024) >> 11);
  }

  errs=0;
  for(i=0;i<80;i++) {
    err = (psseq[i] - odata[i]) >> 3;
    errs += err * err;  /* errs in Q-6 */
  }

  return errs;
}
