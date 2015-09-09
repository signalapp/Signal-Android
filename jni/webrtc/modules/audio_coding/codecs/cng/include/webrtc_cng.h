/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */


#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_CNG_MAIN_INTERFACE_WEBRTC_CNG_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_CNG_MAIN_INTERFACE_WEBRTC_CNG_H_

#include "typedefs.h"

#ifdef __cplusplus
extern "C" {
#endif

#define WEBRTC_CNG_MAX_LPC_ORDER 12
#define WEBRTC_CNG_MAX_OUTSIZE_ORDER 640

/* Define Error codes. */

/* 6100 Encoder */
#define CNG_ENCODER_NOT_INITIATED               6120
#define CNG_DISALLOWED_LPC_ORDER                6130
#define CNG_DISALLOWED_FRAME_SIZE               6140
#define CNG_DISALLOWED_SAMPLING_FREQUENCY       6150
/* 6200 Decoder */
#define CNG_DECODER_NOT_INITIATED               6220

typedef struct WebRtcCngEncInst CNG_enc_inst;
typedef struct WebRtcCngDecInst CNG_dec_inst;

/****************************************************************************
 * WebRtcCng_CreateEnc/Dec(...)
 *
 * These functions create an instance to the specified structure
 *
 * Input:
 *    - XXX_inst      : Pointer to created instance that should be created
 *
 * Return value       :  0 - Ok
 *                      -1 - Error
 */
int16_t WebRtcCng_CreateEnc(CNG_enc_inst** cng_inst);
int16_t WebRtcCng_CreateDec(CNG_dec_inst** cng_inst);

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
 *    - quality       : Number of refl. coefs, maximum allowed is 12
 *
 * Output:
 *    - cng_inst      : Initialized instance
 *
 * Return value       :  0 - Ok
 *                      -1 - Error
 */

int16_t WebRtcCng_InitEnc(CNG_enc_inst* cng_inst, uint16_t fs, int16_t interval,
                          int16_t quality);
int16_t WebRtcCng_InitDec(CNG_dec_inst* cng_inst);

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
int16_t WebRtcCng_FreeEnc(CNG_enc_inst* cng_inst);
int16_t WebRtcCng_FreeDec(CNG_dec_inst* cng_inst);

/****************************************************************************
 * WebRtcCng_Encode(...)
 *
 * These functions analyzes background noise
 *
 * Input:
 *    - cng_inst      : Pointer to created instance
 *    - speech        : Signal to be analyzed
 *    - nrOfSamples   : Size of speech vector
 *    - forceSID      : not zero to force SID frame and reset
 *
 * Output:
 *    - bytesOut      : Nr of bytes to transmit, might be 0
 *
 * Return value       :  0 - Ok
 *                      -1 - Error
 */
int16_t WebRtcCng_Encode(CNG_enc_inst* cng_inst, int16_t* speech,
                         int16_t nrOfSamples, uint8_t* SIDdata,
                         int16_t* bytesOut, int16_t forceSID);

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
                            int16_t length);

/****************************************************************************
 * WebRtcCng_Generate(...)
 *
 * These functions generates CN data when needed
 *
 * Input:
 *    - cng_inst      : Pointer to created instance that should be freed
 *    - outData       : pointer to area to write CN data
 *    - nrOfSamples   : How much data to generate
 *    - new_period    : >0 if a new period of CNG, will reset history
 *
 * Return value       :  0 - Ok
 *                      -1 - Error
 */
int16_t WebRtcCng_Generate(CNG_dec_inst* cng_inst, int16_t* outData,
                           int16_t nrOfSamples, int16_t new_period);

/*****************************************************************************
 * WebRtcCng_GetErrorCodeEnc/Dec(...)
 *
 * This functions can be used to check the error code of a CNG instance. When
 * a function returns -1 a error code will be set for that instance. The 
 * function below extract the code of the last error that occurred in the
 * specified instance.
 *
 * Input:
 *    - CNG_inst    : CNG enc/dec instance
 *
 * Return value     : Error code
 */
int16_t WebRtcCng_GetErrorCodeEnc(CNG_enc_inst* cng_inst);
int16_t WebRtcCng_GetErrorCodeDec(CNG_dec_inst* cng_inst);

#ifdef __cplusplus
}
#endif

#endif // WEBRTC_MODULES_AUDIO_CODING_CODECS_CNG_MAIN_INTERFACE_WEBRTC_CNG_H_
