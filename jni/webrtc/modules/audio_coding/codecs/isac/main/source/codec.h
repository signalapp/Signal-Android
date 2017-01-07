/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * codec.h
 *
 * This header file contains the calls to the internal encoder
 * and decoder functions.
 *
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_CODEC_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_CODEC_H_

#include "structs.h"


void WebRtcIsac_ResetBitstream(Bitstr* bit_stream);

int WebRtcIsac_EstimateBandwidth(BwEstimatorstr* bwest_str, Bitstr* streamdata,
                                 size_t packet_size,
                                 uint16_t rtp_seq_number,
                                 uint32_t send_ts, uint32_t arr_ts,
                                 enum IsacSamplingRate encoderSampRate,
                                 enum IsacSamplingRate decoderSampRate);

int WebRtcIsac_DecodeLb(const TransformTables* transform_tables,
                        float* signal_out,
                        ISACLBDecStruct* ISACdec_obj,
                        int16_t* current_framesamples,
                        int16_t isRCUPayload);

int WebRtcIsac_DecodeRcuLb(float* signal_out, ISACLBDecStruct* ISACdec_obj,
                           int16_t* current_framesamples);

int WebRtcIsac_EncodeLb(const TransformTables* transform_tables,
                        float* in,
                        ISACLBEncStruct* ISACencLB_obj,
                        int16_t codingMode,
                        int16_t bottleneckIndex);

int WebRtcIsac_EncodeStoredDataLb(const IsacSaveEncoderData* ISACSavedEnc_obj,
                                  Bitstr* ISACBitStr_obj, int BWnumber,
                                  float scale);

int WebRtcIsac_EncodeStoredDataUb(
    const ISACUBSaveEncDataStruct* ISACSavedEnc_obj, Bitstr* bitStream,
    int32_t jitterInfo, float scale, enum ISACBandwidth bandwidth);

int16_t WebRtcIsac_GetRedPayloadUb(
    const ISACUBSaveEncDataStruct* ISACSavedEncObj, Bitstr* bitStreamObj,
    enum ISACBandwidth bandwidth);

/******************************************************************************
 * WebRtcIsac_RateAllocation()
 * Internal function to perform a rate-allocation for upper and lower-band,
 * given a total rate.
 *
 * Input:
 *   - inRateBitPerSec           : a total bit-rate in bits/sec.
 *
 * Output:
 *   - rateLBBitPerSec           : a bit-rate allocated to the lower-band
 *                                 in bits/sec.
 *   - rateUBBitPerSec           : a bit-rate allocated to the upper-band
 *                                 in bits/sec.
 *
 * Return value                  : 0 if rate allocation has been successful.
 *                                -1 if failed to allocate rates.
 */

int16_t WebRtcIsac_RateAllocation(int32_t inRateBitPerSec,
                                  double* rateLBBitPerSec,
                                  double* rateUBBitPerSec,
                                  enum ISACBandwidth* bandwidthKHz);


/******************************************************************************
 * WebRtcIsac_DecodeUb16()
 *
 * Decode the upper-band if the codec is in 0-16 kHz mode.
 *
 * Input/Output:
 *       -ISACdec_obj        : pointer to the upper-band decoder object. The
 *                             bit-stream is stored inside the decoder object.
 *
 * Output:
 *       -signal_out         : decoded audio, 480 samples 30 ms.
 *
 * Return value              : >0 number of decoded bytes.
 *                             <0 if an error occurred.
 */
int WebRtcIsac_DecodeUb16(const TransformTables* transform_tables,
                          float* signal_out,
                          ISACUBDecStruct* ISACdec_obj,
                          int16_t isRCUPayload);

/******************************************************************************
 * WebRtcIsac_DecodeUb12()
 *
 * Decode the upper-band if the codec is in 0-12 kHz mode.
 *
 * Input/Output:
 *       -ISACdec_obj        : pointer to the upper-band decoder object. The
 *                             bit-stream is stored inside the decoder object.
 *
 * Output:
 *       -signal_out         : decoded audio, 480 samples 30 ms.
 *
 * Return value              : >0 number of decoded bytes.
 *                             <0 if an error occurred.
 */
int WebRtcIsac_DecodeUb12(const TransformTables* transform_tables,
                          float* signal_out,
                          ISACUBDecStruct* ISACdec_obj,
                          int16_t isRCUPayload);

/******************************************************************************
 * WebRtcIsac_EncodeUb16()
 *
 * Encode the upper-band if the codec is in 0-16 kHz mode.
 *
 * Input:
 *       -in                 : upper-band audio, 160 samples (10 ms).
 *
 * Input/Output:
 *       -ISACdec_obj        : pointer to the upper-band encoder object. The
 *                             bit-stream is stored inside the encoder object.
 *
 * Return value              : >0 number of encoded bytes.
 *                             <0 if an error occurred.
 */
int WebRtcIsac_EncodeUb16(const TransformTables* transform_tables,
                          float* in,
                          ISACUBEncStruct* ISACenc_obj,
                          int32_t jitterInfo);

/******************************************************************************
 * WebRtcIsac_EncodeUb12()
 *
 * Encode the upper-band if the codec is in 0-12 kHz mode.
 *
 * Input:
 *       -in                 : upper-band audio, 160 samples (10 ms).
 *
 * Input/Output:
 *       -ISACdec_obj        : pointer to the upper-band encoder object. The
 *                             bit-stream is stored inside the encoder object.
 *
 * Return value              : >0 number of encoded bytes.
 *                             <0 if an error occurred.
 */
int WebRtcIsac_EncodeUb12(const TransformTables* transform_tables,
                          float* in,
                          ISACUBEncStruct* ISACenc_obj,
                          int32_t jitterInfo);

/************************** initialization functions *************************/

void WebRtcIsac_InitMasking(MaskFiltstr* maskdata);

void WebRtcIsac_InitPreFilterbank(PreFiltBankstr* prefiltdata);

void WebRtcIsac_InitPostFilterbank(PostFiltBankstr* postfiltdata);

void WebRtcIsac_InitPitchFilter(PitchFiltstr* pitchfiltdata);

void WebRtcIsac_InitPitchAnalysis(PitchAnalysisStruct* State);


/**************************** transform functions ****************************/

void WebRtcIsac_InitTransform(TransformTables* tables);

void WebRtcIsac_Time2Spec(const TransformTables* tables,
                          double* inre1,
                          double* inre2,
                          int16_t* outre,
                          int16_t* outim,
                          FFTstr* fftstr_obj);

void WebRtcIsac_Spec2time(const TransformTables* tables,
                          double* inre,
                          double* inim,
                          double* outre1,
                          double* outre2,
                          FFTstr* fftstr_obj);

/******************************* filter functions ****************************/

void WebRtcIsac_AllPoleFilter(double* InOut, double* Coef, size_t lengthInOut,
                              int orderCoef);

void WebRtcIsac_AllZeroFilter(double* In, double* Coef, size_t lengthInOut,
                              int orderCoef, double* Out);

void WebRtcIsac_ZeroPoleFilter(double* In, double* ZeroCoef, double* PoleCoef,
                               size_t lengthInOut, int orderCoef, double* Out);


/***************************** filterbank functions **************************/

void WebRtcIsac_SplitAndFilterFloat(float* in, float* LP, float* HP,
                                    double* LP_la, double* HP_la,
                                    PreFiltBankstr* prefiltdata);


void WebRtcIsac_FilterAndCombineFloat(float* InLP, float* InHP, float* Out,
                                      PostFiltBankstr* postfiltdata);


/************************* normalized lattice filters ************************/

void WebRtcIsac_NormLatticeFilterMa(int orderCoef, float* stateF, float* stateG,
                                    float* lat_in, double* filtcoeflo,
                                    double* lat_out);

void WebRtcIsac_NormLatticeFilterAr(int orderCoef, float* stateF, float* stateG,
                                    double* lat_in, double* lo_filt_coef,
                                    float* lat_out);

void WebRtcIsac_Dir2Lat(double* a, int orderCoef, float* sth, float* cth);

void WebRtcIsac_AutoCorr(double* r, const double* x, size_t N, size_t order);

#endif /* WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_CODEC_H_ */
