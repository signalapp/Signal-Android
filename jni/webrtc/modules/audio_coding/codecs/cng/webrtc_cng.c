/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc_cng.h"

#include <string.h>
#include <stdlib.h>

#include "cng_helpfuns.h"
#include "signal_processing_library.h"

typedef struct WebRtcCngDecInst_t_ {
  uint32_t dec_seed;
  int32_t dec_target_energy;
  int32_t dec_used_energy;
  int16_t dec_target_reflCoefs[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t dec_used_reflCoefs[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t dec_filtstate[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t dec_filtstateLow[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t dec_Efiltstate[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t dec_EfiltstateLow[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t dec_order;
  int16_t dec_target_scale_factor;  /* Q29 */
  int16_t dec_used_scale_factor;  /* Q29 */
  int16_t target_scale_factor;  /* Q13 */
  int16_t errorcode;
  int16_t initflag;
} WebRtcCngDecInst_t;

typedef struct WebRtcCngEncInst_t_ {
  int16_t enc_nrOfCoefs;
  uint16_t enc_sampfreq;
  int16_t enc_interval;
  int16_t enc_msSinceSID;
  int32_t enc_Energy;
  int16_t enc_reflCoefs[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int32_t enc_corrVector[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  uint32_t enc_seed;
  int16_t errorcode;
  int16_t initflag;
} WebRtcCngEncInst_t;

const int32_t WebRtcCng_kDbov[94] = {
  1081109975,  858756178,  682134279,  541838517,  430397633,  341876992,
  271562548,  215709799,  171344384,  136103682,  108110997,   85875618,
  68213428,   54183852,   43039763,   34187699,   27156255,   21570980,
  17134438,   13610368,   10811100,    8587562,    6821343,    5418385,
  4303976,    3418770,    2715625,    2157098,    1713444,    1361037,
  1081110,     858756,     682134,     541839,     430398,     341877,
  271563,     215710,     171344,     136104,     108111,      85876,
  68213,      54184,      43040,      34188,      27156,      21571,
  17134,      13610,      10811,       8588,       6821,       5418,
  4304,       3419,       2716,       2157,       1713,       1361,
  1081,        859,        682,        542,        430,        342,
  272,        216,        171,        136,        108,         86,
  68,         54,         43,         34,         27,         22,
  17,         14,         11,          9,          7,          5,
  4,          3,          3,          2,          2,           1,
  1,          1,          1,          1
};

const int16_t WebRtcCng_kCorrWindow[WEBRTC_CNG_MAX_LPC_ORDER] = {
  32702, 32636, 32570, 32505, 32439, 32374,
  32309, 32244, 32179, 32114, 32049, 31985
};

/****************************************************************************
 * WebRtcCng_CreateEnc/Dec(...)
 *
 * These functions create an instance to the specified structure
 *
 * Input:
 *      - XXX_inst      : Pointer to created instance that should be created
 *
 * Return value         :  0 - Ok
 *                        -1 - Error
 */
int16_t WebRtcCng_CreateEnc(CNG_enc_inst** cng_inst) {
  if (cng_inst != NULL) {
    *cng_inst = (CNG_enc_inst*) malloc(sizeof(WebRtcCngEncInst_t));
    if (*cng_inst != NULL) {
      (*(WebRtcCngEncInst_t**) cng_inst)->errorcode = 0;
      (*(WebRtcCngEncInst_t**) cng_inst)->initflag = 0;

      /* Needed to get the right function pointers in SPLIB. */
      WebRtcSpl_Init();

      return 0;
    } else {
      /* The memory could not be allocated. */
      return -1;
    }
  } else {
    /* The input pointer is invalid (NULL). */
    return -1;
  }
}

int16_t WebRtcCng_CreateDec(CNG_dec_inst** cng_inst) {
  if (cng_inst != NULL ) {
    *cng_inst = (CNG_dec_inst*) malloc(sizeof(WebRtcCngDecInst_t));
    if (*cng_inst != NULL ) {
      (*(WebRtcCngDecInst_t**) cng_inst)->errorcode = 0;
      (*(WebRtcCngDecInst_t**) cng_inst)->initflag = 0;

      /* Needed to get the right function pointers in SPLIB. */
      WebRtcSpl_Init();

      return 0;
    } else {
      /* The memory could not be allocated */
      return -1;
    }
  } else {
    /* The input pointer is invalid (NULL). */
    return -1;
  }
}

/****************************************************************************
 * WebRtcCng_InitEnc/Dec(...)
 *
 * This function initializes a instance
 *
 * Input:
 *    - cng_inst      : Instance that should be initialized
 *
 *    - fs            : 8000 for narrowband and 16000 for wideband
 *    - interval      : generate SID data every interval ms
 *    - quality       : TBD
 *
 * Output:
 *    - cng_inst      : Initialized instance
 *
 * Return value       :  0 - Ok
 *                      -1 - Error
 */
int16_t WebRtcCng_InitEnc(CNG_enc_inst* cng_inst, uint16_t fs, int16_t interval,
                          int16_t quality) {
  int i;
  WebRtcCngEncInst_t* inst = (WebRtcCngEncInst_t*) cng_inst;
  memset(inst, 0, sizeof(WebRtcCngEncInst_t));

  /* Check LPC order */
  if (quality > WEBRTC_CNG_MAX_LPC_ORDER || quality <= 0) {
    inst->errorcode = CNG_DISALLOWED_LPC_ORDER;
    return -1;
  }

  inst->enc_sampfreq = fs;
  inst->enc_interval = interval;
  inst->enc_nrOfCoefs = quality;
  inst->enc_msSinceSID = 0;
  inst->enc_seed = 7777;  /* For debugging only. */
  inst->enc_Energy = 0;
  for (i = 0; i < (WEBRTC_CNG_MAX_LPC_ORDER + 1); i++) {
    inst->enc_reflCoefs[i] = 0;
    inst->enc_corrVector[i] = 0;
  }
  inst->initflag = 1;

  return 0;
}

int16_t WebRtcCng_InitDec(CNG_dec_inst* cng_inst) {
  int i;

  WebRtcCngDecInst_t* inst = (WebRtcCngDecInst_t*) cng_inst;

  memset(inst, 0, sizeof(WebRtcCngDecInst_t));
  inst->dec_seed = 7777;  /* For debugging only. */
  inst->dec_order = 5;
  inst->dec_target_scale_factor = 0;
  inst->dec_used_scale_factor = 0;
  for (i = 0; i < (WEBRTC_CNG_MAX_LPC_ORDER + 1); i++) {
    inst->dec_filtstate[i] = 0;
    inst->dec_target_reflCoefs[i] = 0;
    inst->dec_used_reflCoefs[i] = 0;
  }
  inst->dec_target_reflCoefs[0] = 0;
  inst->dec_used_reflCoefs[0] = 0;
  inst->dec_used_energy = 0;
  inst->initflag = 1;

  return 0;
}

/****************************************************************************
 * WebRtcCng_FreeEnc/Dec(...)
 *
 * These functions frees the dynamic memory of a specified instance
 *
 * Input:
 *    - cng_inst      : Pointer to created instance that should be freed
 *
 * Return value       :  0 - Ok
 *                      -1 - Error
 */
int16_t WebRtcCng_FreeEnc(CNG_enc_inst* cng_inst) {
  free(cng_inst);
  return 0;
}

int16_t WebRtcCng_FreeDec(CNG_dec_inst* cng_inst) {
  free(cng_inst);
  return 0;
}

/****************************************************************************
 * WebRtcCng_Encode(...)
 *
 * These functions analyzes background noise
 *
 * Input:
 *    - cng_inst      : Pointer to created instance
 *    - speech        : Signal (noise) to be analyzed
 *    - nrOfSamples   : Size of speech vector
 *    - bytesOut      : Nr of bytes to transmit, might be 0
 *
 * Return value       :  0 - Ok
 *                      -1 - Error
 */
int16_t WebRtcCng_Encode(CNG_enc_inst* cng_inst, int16_t* speech,
                         int16_t nrOfSamples, uint8_t* SIDdata,
                         int16_t* bytesOut, int16_t forceSID) {
  WebRtcCngEncInst_t* inst = (WebRtcCngEncInst_t*) cng_inst;

  int16_t arCoefs[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int32_t corrVector[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t refCs[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t hanningW[WEBRTC_CNG_MAX_OUTSIZE_ORDER];
  int16_t ReflBeta = 19661;  /* 0.6 in q15. */
  int16_t ReflBetaComp = 13107;  /* 0.4 in q15. */
  int32_t outEnergy;
  int outShifts;
  int i, stab;
  int acorrScale;
  int index;
  int16_t ind, factor;
  int32_t* bptr;
  int32_t blo, bhi;
  int16_t negate;
  const int16_t* aptr;
  int16_t speechBuf[WEBRTC_CNG_MAX_OUTSIZE_ORDER];

  /* Check if encoder initiated. */
  if (inst->initflag != 1) {
    inst->errorcode = CNG_ENCODER_NOT_INITIATED;
    return -1;
  }

  /* Check framesize. */
  if (nrOfSamples > WEBRTC_CNG_MAX_OUTSIZE_ORDER) {
    inst->errorcode = CNG_DISALLOWED_FRAME_SIZE;
    return -1;
  }

  for (i = 0; i < nrOfSamples; i++) {
    speechBuf[i] = speech[i];
  }

  factor = nrOfSamples;

  /* Calculate energy and a coefficients. */
  outEnergy = WebRtcSpl_Energy(speechBuf, nrOfSamples, &outShifts);
  while (outShifts > 0) {
    /* We can only do 5 shifts without destroying accuracy in
     * division factor. */
    if (outShifts > 5) {
      outEnergy <<= (outShifts - 5);
      outShifts = 5;
    } else {
      factor /= 2;
      outShifts--;
    }
  }
  outEnergy = WebRtcSpl_DivW32W16(outEnergy, factor);

  if (outEnergy > 1) {
    /* Create Hanning Window. */
    WebRtcSpl_GetHanningWindow(hanningW, nrOfSamples / 2);
    for (i = 0; i < (nrOfSamples / 2); i++)
      hanningW[nrOfSamples - i - 1] = hanningW[i];

    WebRtcSpl_ElementwiseVectorMult(speechBuf, hanningW, speechBuf, nrOfSamples,
                                    14);

    WebRtcSpl_AutoCorrelation(speechBuf, nrOfSamples, inst->enc_nrOfCoefs,
                              corrVector, &acorrScale);

    if (*corrVector == 0)
      *corrVector = WEBRTC_SPL_WORD16_MAX;

    /* Adds the bandwidth expansion. */
    aptr = WebRtcCng_kCorrWindow;
    bptr = corrVector;

    /* (zzz) lpc16_1 = 17+1+820+2+2 = 842 (ordo2=700). */
    for (ind = 0; ind < inst->enc_nrOfCoefs; ind++) {
      /* The below code multiplies the 16 b corrWindow values (Q15) with
       * the 32 b corrvector (Q0) and shifts the result down 15 steps. */
      negate = *bptr < 0;
      if (negate)
        *bptr = -*bptr;

      blo = (int32_t) * aptr * (*bptr & 0xffff);
      bhi = ((blo >> 16) & 0xffff)
          + ((int32_t)(*aptr++) * ((*bptr >> 16) & 0xffff));
      blo = (blo & 0xffff) | ((bhi & 0xffff) << 16);

      *bptr = (((bhi >> 16) & 0x7fff) << 17) | ((uint32_t) blo >> 15);
      if (negate)
        *bptr = -*bptr;
      bptr++;
    }
    /* End of bandwidth expansion. */

    stab = WebRtcSpl_LevinsonDurbin(corrVector, arCoefs, refCs,
                                    inst->enc_nrOfCoefs);

    if (!stab) {
      /* Disregard from this frame */
      *bytesOut = 0;
      return 0;
    }

  } else {
    for (i = 0; i < inst->enc_nrOfCoefs; i++)
      refCs[i] = 0;
  }

  if (forceSID) {
    /* Read instantaneous values instead of averaged. */
    for (i = 0; i < inst->enc_nrOfCoefs; i++)
      inst->enc_reflCoefs[i] = refCs[i];
    inst->enc_Energy = outEnergy;
  } else {
    /* Average history with new values. */
    for (i = 0; i < (inst->enc_nrOfCoefs); i++) {
      inst->enc_reflCoefs[i] = (int16_t) WEBRTC_SPL_MUL_16_16_RSFT(
          inst->enc_reflCoefs[i], ReflBeta, 15);
      inst->enc_reflCoefs[i] += (int16_t) WEBRTC_SPL_MUL_16_16_RSFT(
          refCs[i], ReflBetaComp, 15);
    }
    inst->enc_Energy = (outEnergy >> 2) + (inst->enc_Energy >> 1)
        + (inst->enc_Energy >> 2);
  }

  if (inst->enc_Energy < 1) {
    inst->enc_Energy = 1;
  }

  if ((inst->enc_msSinceSID > (inst->enc_interval - 1)) || forceSID) {

    /* Search for best dbov value. */
    index = 0;
    for (i = 1; i < 93; i++) {
      /* Always round downwards. */
      if ((inst->enc_Energy - WebRtcCng_kDbov[i]) > 0) {
        index = i;
        break;
      }
    }
    if ((i == 93) && (index == 0))
      index = 94;
    SIDdata[0] = index;

    /* Quantize coefficients with tweak for WebRtc implementation of RFC3389. */
    if (inst->enc_nrOfCoefs == WEBRTC_CNG_MAX_LPC_ORDER) {
      for (i = 0; i < inst->enc_nrOfCoefs; i++) {
        /* Q15 to Q7 with rounding. */
        SIDdata[i + 1] = ((inst->enc_reflCoefs[i] + 128) >> 8);
      }
    } else {
      for (i = 0; i < inst->enc_nrOfCoefs; i++) {
        /* Q15 to Q7 with rounding. */
        SIDdata[i + 1] = (127 + ((inst->enc_reflCoefs[i] + 128) >> 8));
      }
    }

    inst->enc_msSinceSID = 0;
    *bytesOut = inst->enc_nrOfCoefs + 1;

    inst->enc_msSinceSID += (1000 * nrOfSamples) / inst->enc_sampfreq;
    return inst->enc_nrOfCoefs + 1;
  } else {
    inst->enc_msSinceSID += (1000 * nrOfSamples) / inst->enc_sampfreq;
    *bytesOut = 0;
    return 0;
  }
}

/****************************************************************************
 * WebRtcCng_UpdateSid(...)
 *
 * These functions updates the CN state, when a new SID packet arrives
 *
 * Input:
 *    - cng_inst      : Pointer to created instance that should be freed
 *    - SID           : SID packet, all headers removed
 *    - length        : Length in bytes of SID packet
 *
 * Return value       :  0 - Ok
 *                      -1 - Error
 */
int16_t WebRtcCng_UpdateSid(CNG_dec_inst* cng_inst, uint8_t* SID,
                            int16_t length) {

  WebRtcCngDecInst_t* inst = (WebRtcCngDecInst_t*) cng_inst;
  int16_t refCs[WEBRTC_CNG_MAX_LPC_ORDER];
  int32_t targetEnergy;
  int i;

  if (inst->initflag != 1) {
    inst->errorcode = CNG_DECODER_NOT_INITIATED;
    return -1;
  }

  /* Throw away reflection coefficients of higher order than we can handle. */
  if (length > (WEBRTC_CNG_MAX_LPC_ORDER + 1))
    length = WEBRTC_CNG_MAX_LPC_ORDER + 1;

  inst->dec_order = length - 1;

  if (SID[0] > 93)
    SID[0] = 93;
  targetEnergy = WebRtcCng_kDbov[SID[0]];
  /* Take down target energy to 75%. */
  targetEnergy = targetEnergy >> 1;
  targetEnergy += targetEnergy >> 2;

  inst->dec_target_energy = targetEnergy;

  /* Reconstruct coeffs with tweak for WebRtc implementation of RFC3389. */
  if (inst->dec_order == WEBRTC_CNG_MAX_LPC_ORDER) {
    for (i = 0; i < (inst->dec_order); i++) {
      refCs[i] = SID[i + 1] << 8; /* Q7 to Q15*/
      inst->dec_target_reflCoefs[i] = refCs[i];
    }
  } else {
    for (i = 0; i < (inst->dec_order); i++) {
      refCs[i] = (SID[i + 1] - 127) << 8; /* Q7 to Q15. */
      inst->dec_target_reflCoefs[i] = refCs[i];
    }
  }

  for (i = (inst->dec_order); i < WEBRTC_CNG_MAX_LPC_ORDER; i++) {
    refCs[i] = 0;
    inst->dec_target_reflCoefs[i] = refCs[i];
  }

  return 0;
}

/****************************************************************************
 * WebRtcCng_Generate(...)
 *
 * These functions generates CN data when needed
 *
 * Input:
 *    - cng_inst      : Pointer to created instance that should be freed
 *    - outData       : pointer to area to write CN data
 *    - nrOfSamples   : How much data to generate
 *
 * Return value        :  0 - Ok
 *                       -1 - Error
 */
int16_t WebRtcCng_Generate(CNG_dec_inst* cng_inst, int16_t* outData,
                           int16_t nrOfSamples, int16_t new_period) {
  WebRtcCngDecInst_t* inst = (WebRtcCngDecInst_t*) cng_inst;

  int i;
  int16_t excitation[WEBRTC_CNG_MAX_OUTSIZE_ORDER];
  int16_t low[WEBRTC_CNG_MAX_OUTSIZE_ORDER];
  int16_t lpPoly[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t ReflBetaStd = 26214;  /* 0.8 in q15. */
  int16_t ReflBetaCompStd = 6553;  /* 0.2 in q15. */
  int16_t ReflBetaNewP = 19661;  /* 0.6 in q15. */
  int16_t ReflBetaCompNewP = 13107;  /* 0.4 in q15. */
  int16_t Beta, BetaC, tmp1, tmp2, tmp3;
  int32_t targetEnergy;
  int16_t En;
  int16_t temp16;

  if (nrOfSamples > WEBRTC_CNG_MAX_OUTSIZE_ORDER) {
    inst->errorcode = CNG_DISALLOWED_FRAME_SIZE;
    return -1;
  }

  if (new_period) {
    inst->dec_used_scale_factor = inst->dec_target_scale_factor;
    Beta = ReflBetaNewP;
    BetaC = ReflBetaCompNewP;
  } else {
    Beta = ReflBetaStd;
    BetaC = ReflBetaCompStd;
  }

  /* Here we use a 0.5 weighting, should possibly be modified to 0.6. */
  tmp1 = inst->dec_used_scale_factor << 2; /* Q13->Q15 */
  tmp2 = inst->dec_target_scale_factor << 2; /* Q13->Q15 */
  tmp3 = (int16_t) WEBRTC_SPL_MUL_16_16_RSFT(tmp1, Beta, 15);
  tmp3 += (int16_t) WEBRTC_SPL_MUL_16_16_RSFT(tmp2, BetaC, 15);
  inst->dec_used_scale_factor = tmp3 >> 2; /* Q15->Q13 */

  inst->dec_used_energy = inst->dec_used_energy >> 1;
  inst->dec_used_energy += inst->dec_target_energy >> 1;

  /* Do the same for the reflection coeffs. */
  for (i = 0; i < WEBRTC_CNG_MAX_LPC_ORDER; i++) {
    inst->dec_used_reflCoefs[i] = (int16_t) WEBRTC_SPL_MUL_16_16_RSFT(
        inst->dec_used_reflCoefs[i], Beta, 15);
    inst->dec_used_reflCoefs[i] += (int16_t) WEBRTC_SPL_MUL_16_16_RSFT(
        inst->dec_target_reflCoefs[i], BetaC, 15);
  }

  /* Compute the polynomial coefficients. */
  WebRtcCng_K2a16(inst->dec_used_reflCoefs, WEBRTC_CNG_MAX_LPC_ORDER, lpPoly);


  targetEnergy = inst->dec_used_energy;

  /* Calculate scaling factor based on filter energy. */
  En = 8192;  /* 1.0 in Q13. */
  for (i = 0; i < (WEBRTC_CNG_MAX_LPC_ORDER); i++) {

    /* Floating point value for reference.
       E *= 1.0 - (inst->dec_used_reflCoefs[i] / 32768.0) *
       (inst->dec_used_reflCoefs[i] / 32768.0);
     */

    /* Same in fixed point. */
    /* K(i).^2 in Q15. */
    temp16 = (int16_t) WEBRTC_SPL_MUL_16_16_RSFT(
        inst->dec_used_reflCoefs[i], inst->dec_used_reflCoefs[i], 15);
    /* 1 - K(i).^2 in Q15. */
    temp16 = 0x7fff - temp16;
    En = (int16_t) WEBRTC_SPL_MUL_16_16_RSFT(En, temp16, 15);
  }

  /* float scaling= sqrt(E * inst->dec_target_energy / (1 << 24)); */

  /* Calculate sqrt(En * target_energy / excitation energy) */
  targetEnergy = WebRtcSpl_Sqrt(inst->dec_used_energy);

  En = (int16_t) WebRtcSpl_Sqrt(En) << 6;
  En = (En * 3) >> 1;  /* 1.5 estimates sqrt(2). */
  inst->dec_used_scale_factor = (int16_t)((En * targetEnergy) >> 12);

  /* Generate excitation. */
  /* Excitation energy per sample is 2.^24 - Q13 N(0,1). */
  for (i = 0; i < nrOfSamples; i++) {
    excitation[i] = WebRtcSpl_RandN(&inst->dec_seed) >> 1;
  }

  /* Scale to correct energy. */
  WebRtcSpl_ScaleVector(excitation, excitation, inst->dec_used_scale_factor,
                        nrOfSamples, 13);

  /* |lpPoly| - Coefficients in Q12.
   * |excitation| - Speech samples.
   * |nst->dec_filtstate| - State preservation.
   * |outData| - Filtered speech samples. */
  WebRtcSpl_FilterAR(lpPoly, WEBRTC_CNG_MAX_LPC_ORDER + 1, excitation,
                     nrOfSamples, inst->dec_filtstate, WEBRTC_CNG_MAX_LPC_ORDER,
                     inst->dec_filtstateLow, WEBRTC_CNG_MAX_LPC_ORDER, outData,
                     low, nrOfSamples);

  return 0;
}

/****************************************************************************
 * WebRtcCng_GetErrorCodeEnc/Dec(...)
 *
 * This functions can be used to check the error code of a CNG instance. When
 * a function returns -1 a error code will be set for that instance. The 
 * function below extract the code of the last error that occured in the 
 * specified instance.
 *
 * Input:
 *    - CNG_inst    : CNG enc/dec instance
 *
 * Return value     : Error code
 */
int16_t WebRtcCng_GetErrorCodeEnc(CNG_enc_inst* cng_inst) {
  /* Typecast pointer to real structure. */
  WebRtcCngEncInst_t* inst = (WebRtcCngEncInst_t*) cng_inst;
  return inst->errorcode;
}

int16_t WebRtcCng_GetErrorCodeDec(CNG_dec_inst* cng_inst) {
  /* Typecast pointer to real structure. */
  WebRtcCngDecInst_t* inst = (WebRtcCngDecInst_t*) cng_inst;
  return inst->errorcode;
}
