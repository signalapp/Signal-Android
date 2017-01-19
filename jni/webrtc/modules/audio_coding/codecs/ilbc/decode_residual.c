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

 WebRtcIlbcfix_DecodeResidual.c

******************************************************************/

#include <string.h>

#include "defines.h"
#include "state_construct.h"
#include "cb_construct.h"
#include "index_conv_dec.h"
#include "do_plc.h"
#include "constants.h"
#include "enhancer_interface.h"
#include "xcorr_coef.h"
#include "lsf_check.h"

/*----------------------------------------------------------------*
 *  frame residual decoder function (subrutine to iLBC_decode)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_DecodeResidual(
    iLBC_Dec_Inst_t *iLBCdec_inst,
    /* (i/o) the decoder state structure */
    iLBC_bits *iLBC_encbits, /* (i/o) Encoded bits, which are used
                                for the decoding  */
    int16_t *decresidual,  /* (o) decoded residual frame */
    int16_t *syntdenum   /* (i) the decoded synthesis filter
                                  coefficients */
                                  ) {
  int16_t meml_gotten, Nfor, Nback, diff, start_pos;
  int16_t subcount, subframe;
  int16_t *reverseDecresidual = iLBCdec_inst->enh_buf; /* Reversed decoded data, used for decoding backwards in time (reuse memory in state) */
  int16_t *memVec = iLBCdec_inst->prevResidual;  /* Memory for codebook and filter state (reuse memory in state) */
  int16_t *mem = &memVec[CB_HALFFILTERLEN];   /* Memory for codebook */

  diff = STATE_LEN - iLBCdec_inst->state_short_len;

  if (iLBC_encbits->state_first == 1) {
    start_pos = (iLBC_encbits->startIdx-1)*SUBL;
  } else {
    start_pos = (iLBC_encbits->startIdx-1)*SUBL + diff;
  }

  /* decode scalar part of start state */

  WebRtcIlbcfix_StateConstruct(iLBC_encbits->idxForMax,
                               iLBC_encbits->idxVec, &syntdenum[(iLBC_encbits->startIdx-1)*(LPC_FILTERORDER+1)],
                               &decresidual[start_pos], iLBCdec_inst->state_short_len
                               );

  if (iLBC_encbits->state_first) { /* put adaptive part in the end */

    /* setup memory */

    WebRtcSpl_MemSetW16(mem, 0, (int16_t)(CB_MEML-iLBCdec_inst->state_short_len));
    WEBRTC_SPL_MEMCPY_W16(mem+CB_MEML-iLBCdec_inst->state_short_len, decresidual+start_pos,
                          iLBCdec_inst->state_short_len);

    /* construct decoded vector */

    WebRtcIlbcfix_CbConstruct(
        &decresidual[start_pos+iLBCdec_inst->state_short_len],
        iLBC_encbits->cb_index, iLBC_encbits->gain_index,
        mem+CB_MEML-ST_MEM_L_TBL,
        ST_MEM_L_TBL, (int16_t)diff
                              );

  }
  else {/* put adaptive part in the beginning */

    /* setup memory */

    meml_gotten = iLBCdec_inst->state_short_len;
    WebRtcSpl_MemCpyReversedOrder(mem+CB_MEML-1,
                                  decresidual+start_pos, meml_gotten);
    WebRtcSpl_MemSetW16(mem, 0, (int16_t)(CB_MEML-meml_gotten));

    /* construct decoded vector */

    WebRtcIlbcfix_CbConstruct(
        reverseDecresidual,
        iLBC_encbits->cb_index, iLBC_encbits->gain_index,
        mem+CB_MEML-ST_MEM_L_TBL,
        ST_MEM_L_TBL, diff
                              );

    /* get decoded residual from reversed vector */

    WebRtcSpl_MemCpyReversedOrder(&decresidual[start_pos-1],
                                  reverseDecresidual, diff);
  }

  /* counter for predicted subframes */

  subcount=1;

  /* forward prediction of subframes */

  Nfor = iLBCdec_inst->nsub-iLBC_encbits->startIdx-1;

  if( Nfor > 0 ) {

    /* setup memory */
    WebRtcSpl_MemSetW16(mem, 0, CB_MEML-STATE_LEN);
    WEBRTC_SPL_MEMCPY_W16(mem+CB_MEML-STATE_LEN,
                          decresidual+(iLBC_encbits->startIdx-1)*SUBL, STATE_LEN);

    /* loop over subframes to encode */

    for (subframe=0; subframe<Nfor; subframe++) {

      /* construct decoded vector */
      WebRtcIlbcfix_CbConstruct(
          &decresidual[(iLBC_encbits->startIdx+1+subframe)*SUBL],
          iLBC_encbits->cb_index+subcount*CB_NSTAGES,
          iLBC_encbits->gain_index+subcount*CB_NSTAGES,
          mem, MEM_LF_TBL, SUBL
                                );

      /* update memory */
      memmove(mem, mem + SUBL, (CB_MEML - SUBL) * sizeof(*mem));
      WEBRTC_SPL_MEMCPY_W16(mem+CB_MEML-SUBL,
                            &decresidual[(iLBC_encbits->startIdx+1+subframe)*SUBL], SUBL);

      subcount++;
    }

  }

  /* backward prediction of subframes */

  Nback = iLBC_encbits->startIdx-1;

  if( Nback > 0 ){

    /* setup memory */

    meml_gotten = SUBL*(iLBCdec_inst->nsub+1-iLBC_encbits->startIdx);
    if( meml_gotten > CB_MEML ) {
      meml_gotten=CB_MEML;
    }

    WebRtcSpl_MemCpyReversedOrder(mem+CB_MEML-1,
                                  decresidual+(iLBC_encbits->startIdx-1)*SUBL, meml_gotten);
    WebRtcSpl_MemSetW16(mem, 0, (int16_t)(CB_MEML-meml_gotten));

    /* loop over subframes to decode */

    for (subframe=0; subframe<Nback; subframe++) {

      /* construct decoded vector */
      WebRtcIlbcfix_CbConstruct(
          &reverseDecresidual[subframe*SUBL],
          iLBC_encbits->cb_index+subcount*CB_NSTAGES,
          iLBC_encbits->gain_index+subcount*CB_NSTAGES,
          mem, MEM_LF_TBL, SUBL
                                );

      /* update memory */
      memmove(mem, mem + SUBL, (CB_MEML - SUBL) * sizeof(*mem));
      WEBRTC_SPL_MEMCPY_W16(mem+CB_MEML-SUBL,
                            &reverseDecresidual[subframe*SUBL], SUBL);

      subcount++;
    }

    /* get decoded residual from reversed vector */
    WebRtcSpl_MemCpyReversedOrder(decresidual+SUBL*Nback-1,
                                  reverseDecresidual, SUBL*Nback);
  }
}
