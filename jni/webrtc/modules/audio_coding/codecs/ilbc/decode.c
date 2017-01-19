/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_Decode.c

******************************************************************/

#include "defines.h"
#include "simple_lsf_dequant.h"
#include "decoder_interpolate_lsf.h"
#include "index_conv_dec.h"
#include "do_plc.h"
#include "constants.h"
#include "enhancer_interface.h"
#include "xcorr_coef.h"
#include "lsf_check.h"
#include "decode_residual.h"
#include "unpack_bits.h"
#include "hp_output.h"
#ifndef WEBRTC_ARCH_BIG_ENDIAN
#include "swap_bytes.h"
#endif

/*----------------------------------------------------------------*
 *  main decoder function
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_DecodeImpl(
    int16_t *decblock,    /* (o) decoded signal block */
    const uint16_t *bytes, /* (i) encoded signal bits */
    iLBC_Dec_Inst_t *iLBCdec_inst, /* (i/o) the decoder state
                                           structure */
    int16_t mode      /* (i) 0: bad packet, PLC,
                                                                   1: normal */
                           ) {
  int i;
  int16_t order_plus_one;

  int16_t last_bit;
  int16_t *data;
  /* Stack based */
  int16_t decresidual[BLOCKL_MAX];
  int16_t PLCresidual[BLOCKL_MAX + LPC_FILTERORDER];
  int16_t syntdenum[NSUB_MAX*(LPC_FILTERORDER+1)];
  int16_t PLClpc[LPC_FILTERORDER + 1];
#ifndef WEBRTC_ARCH_BIG_ENDIAN
  uint16_t swapped[NO_OF_WORDS_30MS];
#endif
  iLBC_bits *iLBCbits_inst = (iLBC_bits*)PLCresidual;

  /* Reuse some buffers that are non overlapping in order to save stack memory */
  data = &PLCresidual[LPC_FILTERORDER];

  if (mode) { /* the data are good */

    /* decode data */

    /* Unpacketize bits into parameters */

#ifndef WEBRTC_ARCH_BIG_ENDIAN
    WebRtcIlbcfix_SwapBytes(bytes, iLBCdec_inst->no_of_words, swapped);
    last_bit = WebRtcIlbcfix_UnpackBits(swapped, iLBCbits_inst, iLBCdec_inst->mode);
#else
    last_bit = WebRtcIlbcfix_UnpackBits(bytes, iLBCbits_inst, iLBCdec_inst->mode);
#endif

    /* Check for bit errors */
    if (iLBCbits_inst->startIdx<1)
      mode = 0;
    if ((iLBCdec_inst->mode==20) && (iLBCbits_inst->startIdx>3))
      mode = 0;
    if ((iLBCdec_inst->mode==30) && (iLBCbits_inst->startIdx>5))
      mode = 0;
    if (last_bit==1)
      mode = 0;

    if (mode) { /* No bit errors was detected, continue decoding */
      /* Stack based */
      int16_t lsfdeq[LPC_FILTERORDER*LPC_N_MAX];
      int16_t weightdenum[(LPC_FILTERORDER + 1)*NSUB_MAX];

      /* adjust index */
      WebRtcIlbcfix_IndexConvDec(iLBCbits_inst->cb_index);

      /* decode the lsf */
      WebRtcIlbcfix_SimpleLsfDeQ(lsfdeq, (int16_t*)(iLBCbits_inst->lsf), iLBCdec_inst->lpc_n);
      WebRtcIlbcfix_LsfCheck(lsfdeq, LPC_FILTERORDER, iLBCdec_inst->lpc_n);
      WebRtcIlbcfix_DecoderInterpolateLsp(syntdenum, weightdenum,
                                          lsfdeq, LPC_FILTERORDER, iLBCdec_inst);

      /* Decode the residual using the cb and gain indexes */
      WebRtcIlbcfix_DecodeResidual(iLBCdec_inst, iLBCbits_inst, decresidual, syntdenum);

      /* preparing the plc for a future loss! */
      WebRtcIlbcfix_DoThePlc( PLCresidual, PLClpc, 0,
                              decresidual, syntdenum + (LPC_FILTERORDER + 1)*(iLBCdec_inst->nsub - 1),
                              (int16_t)(iLBCdec_inst->last_lag), iLBCdec_inst);

      /* Use the output from doThePLC */
      WEBRTC_SPL_MEMCPY_W16(decresidual, PLCresidual, iLBCdec_inst->blockl);
    }

  }

  if (mode == 0) {
    /* the data is bad (either a PLC call
     * was made or a bit error was detected)
     */

    /* packet loss conceal */

    WebRtcIlbcfix_DoThePlc( PLCresidual, PLClpc, 1,
                            decresidual, syntdenum, (int16_t)(iLBCdec_inst->last_lag), iLBCdec_inst);

    WEBRTC_SPL_MEMCPY_W16(decresidual, PLCresidual, iLBCdec_inst->blockl);

    order_plus_one = LPC_FILTERORDER + 1;

    for (i = 0; i < iLBCdec_inst->nsub; i++) {
      WEBRTC_SPL_MEMCPY_W16(syntdenum+(i*order_plus_one),
                            PLClpc, order_plus_one);
    }
  }

  if ((*iLBCdec_inst).use_enhancer == 1) { /* Enhancer activated */

    /* Update the filter and filter coefficients if there was a packet loss */
    if (iLBCdec_inst->prev_enh_pl==2) {
      for (i=0;i<iLBCdec_inst->nsub;i++) {
        WEBRTC_SPL_MEMCPY_W16(&(iLBCdec_inst->old_syntdenum[i*(LPC_FILTERORDER+1)]),
                              syntdenum, (LPC_FILTERORDER+1));
      }
    }

    /* post filtering */
    (*iLBCdec_inst).last_lag =
        WebRtcIlbcfix_EnhancerInterface(data, decresidual, iLBCdec_inst);

    /* synthesis filtering */

    /* Set up the filter state */
    WEBRTC_SPL_MEMCPY_W16(&data[-LPC_FILTERORDER], iLBCdec_inst->syntMem, LPC_FILTERORDER);

    if (iLBCdec_inst->mode==20) {
      /* Enhancer has 40 samples delay */
      i=0;
      WebRtcSpl_FilterARFastQ12(
          data, data,
          iLBCdec_inst->old_syntdenum + (i+iLBCdec_inst->nsub-1)*(LPC_FILTERORDER+1),
          LPC_FILTERORDER+1, SUBL);

      for (i=1; i < iLBCdec_inst->nsub; i++) {
        WebRtcSpl_FilterARFastQ12(
            data+i*SUBL, data+i*SUBL,
            syntdenum+(i-1)*(LPC_FILTERORDER+1),
            LPC_FILTERORDER+1, SUBL);
      }

    } else if (iLBCdec_inst->mode==30) {
      /* Enhancer has 80 samples delay */
      for (i=0; i < 2; i++) {
        WebRtcSpl_FilterARFastQ12(
            data+i*SUBL, data+i*SUBL,
            iLBCdec_inst->old_syntdenum + (i+4)*(LPC_FILTERORDER+1),
            LPC_FILTERORDER+1, SUBL);
      }
      for (i=2; i < iLBCdec_inst->nsub; i++) {
        WebRtcSpl_FilterARFastQ12(
            data+i*SUBL, data+i*SUBL,
            syntdenum+(i-2)*(LPC_FILTERORDER+1),
            LPC_FILTERORDER+1, SUBL);
      }
    }

    /* Save the filter state */
    WEBRTC_SPL_MEMCPY_W16(iLBCdec_inst->syntMem, &data[iLBCdec_inst->blockl-LPC_FILTERORDER], LPC_FILTERORDER);

  } else { /* Enhancer not activated */
    int16_t lag;

    /* Find last lag (since the enhancer is not called to give this info) */
    lag = 20;
    if (iLBCdec_inst->mode==20) {
      lag = (int16_t)WebRtcIlbcfix_XcorrCoef(
          &decresidual[iLBCdec_inst->blockl-60],
          &decresidual[iLBCdec_inst->blockl-60-lag],
          60,
          80, lag, -1);
    } else {
      lag = (int16_t)WebRtcIlbcfix_XcorrCoef(
          &decresidual[iLBCdec_inst->blockl-ENH_BLOCKL],
          &decresidual[iLBCdec_inst->blockl-ENH_BLOCKL-lag],
          ENH_BLOCKL,
          100, lag, -1);
    }

    /* Store lag (it is needed if next packet is lost) */
    (*iLBCdec_inst).last_lag = (int)lag;

    /* copy data and run synthesis filter */
    WEBRTC_SPL_MEMCPY_W16(data, decresidual, iLBCdec_inst->blockl);

    /* Set up the filter state */
    WEBRTC_SPL_MEMCPY_W16(&data[-LPC_FILTERORDER], iLBCdec_inst->syntMem, LPC_FILTERORDER);

    for (i=0; i < iLBCdec_inst->nsub; i++) {
      WebRtcSpl_FilterARFastQ12(
          data+i*SUBL, data+i*SUBL,
          syntdenum + i*(LPC_FILTERORDER+1),
          LPC_FILTERORDER+1, SUBL);
    }

    /* Save the filter state */
    WEBRTC_SPL_MEMCPY_W16(iLBCdec_inst->syntMem, &data[iLBCdec_inst->blockl-LPC_FILTERORDER], LPC_FILTERORDER);
  }

  WEBRTC_SPL_MEMCPY_W16(decblock,data,iLBCdec_inst->blockl);

  /* High pass filter the signal (with upscaling a factor 2 and saturation) */
  WebRtcIlbcfix_HpOutput(decblock, (int16_t*)WebRtcIlbcfix_kHpOutCoefs,
                         iLBCdec_inst->hpimemy, iLBCdec_inst->hpimemx,
                         iLBCdec_inst->blockl);

  WEBRTC_SPL_MEMCPY_W16(iLBCdec_inst->old_syntdenum,
                        syntdenum, iLBCdec_inst->nsub*(LPC_FILTERORDER+1));

  iLBCdec_inst->prev_enh_pl=0;

  if (mode==0) { /* PLC was used */
    iLBCdec_inst->prev_enh_pl=1;
  }
}
