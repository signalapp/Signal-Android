/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/aecm/include/echo_control_mobile.h"

#ifdef AEC_DEBUG
#include <stdio.h>
#endif
#include <stdlib.h>

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_processing/aecm/aecm_core.h"
#include "webrtc/modules/audio_processing/utility/ring_buffer.h"

#define BUF_SIZE_FRAMES 50 // buffer size (frames)
// Maximum length of resampled signal. Must be an integer multiple of frames
// (ceil(1/(1 + MIN_SKEW)*2) + 1)*FRAME_LEN
// The factor of 2 handles wb, and the + 1 is as a safety margin
#define MAX_RESAMP_LEN (5 * FRAME_LEN)

static const size_t kBufSizeSamp = BUF_SIZE_FRAMES * FRAME_LEN; // buffer size (samples)
static const int kSampMsNb = 8; // samples per ms in nb
// Target suppression levels for nlp modes
// log{0.001, 0.00001, 0.00000001}
static const int kInitCheck = 42;

typedef struct
{
    int sampFreq;
    int scSampFreq;
    short bufSizeStart;
    int knownDelay;

    // Stores the last frame added to the farend buffer
    short farendOld[2][FRAME_LEN];
    short initFlag; // indicates if AEC has been initialized

    // Variables used for averaging far end buffer size
    short counter;
    short sum;
    short firstVal;
    short checkBufSizeCtr;

    // Variables used for delay shifts
    short msInSndCardBuf;
    short filtDelay;
    int timeForDelayChange;
    int ECstartup;
    int checkBuffSize;
    int delayChange;
    short lastDelayDiff;

    int16_t echoMode;

#ifdef AEC_DEBUG
    FILE *bufFile;
    FILE *delayFile;
    FILE *preCompFile;
    FILE *postCompFile;
#endif // AEC_DEBUG
    // Structures
    RingBuffer *farendBuf;

    int lastError;

    AecmCore_t *aecmCore;
} aecmob_t;

// Estimates delay to set the position of the farend buffer read pointer
// (controlled by knownDelay)
static int WebRtcAecm_EstBufDelay(aecmob_t *aecmInst, short msInSndCardBuf);

// Stuffs the farend buffer if the estimated delay is too large
static int WebRtcAecm_DelayComp(aecmob_t *aecmInst);

int32_t WebRtcAecm_Create(void **aecmInst)
{
    aecmob_t *aecm;
    if (aecmInst == NULL)
    {
        return -1;
    }

    aecm = malloc(sizeof(aecmob_t));
    *aecmInst = aecm;
    if (aecm == NULL)
    {
        return -1;
    }

    WebRtcSpl_Init();

    if (WebRtcAecm_CreateCore(&aecm->aecmCore) == -1)
    {
        WebRtcAecm_Free(aecm);
        aecm = NULL;
        return -1;
    }

    aecm->farendBuf = WebRtc_CreateBuffer(kBufSizeSamp,
                                          sizeof(int16_t));
    if (!aecm->farendBuf)
    {
        WebRtcAecm_Free(aecm);
        aecm = NULL;
        return -1;
    }

    aecm->initFlag = 0;
    aecm->lastError = 0;

#ifdef AEC_DEBUG
    aecm->aecmCore->farFile = fopen("aecFar.pcm","wb");
    aecm->aecmCore->nearFile = fopen("aecNear.pcm","wb");
    aecm->aecmCore->outFile = fopen("aecOut.pcm","wb");
    //aecm->aecmCore->outLpFile = fopen("aecOutLp.pcm","wb");

    aecm->bufFile = fopen("aecBuf.dat", "wb");
    aecm->delayFile = fopen("aecDelay.dat", "wb");
    aecm->preCompFile = fopen("preComp.pcm", "wb");
    aecm->postCompFile = fopen("postComp.pcm", "wb");
#endif // AEC_DEBUG
    return 0;
}

int32_t WebRtcAecm_Free(void *aecmInst)
{
    aecmob_t *aecm = aecmInst;

    if (aecm == NULL)
    {
        return -1;
    }

#ifdef AEC_DEBUG
    fclose(aecm->aecmCore->farFile);
    fclose(aecm->aecmCore->nearFile);
    fclose(aecm->aecmCore->outFile);
    //fclose(aecm->aecmCore->outLpFile);

    fclose(aecm->bufFile);
    fclose(aecm->delayFile);
    fclose(aecm->preCompFile);
    fclose(aecm->postCompFile);
#endif // AEC_DEBUG
    WebRtcAecm_FreeCore(aecm->aecmCore);
    WebRtc_FreeBuffer(aecm->farendBuf);
    free(aecm);

    return 0;
}

int32_t WebRtcAecm_Init(void *aecmInst, int32_t sampFreq)
{
    aecmob_t *aecm = aecmInst;
    AecmConfig aecConfig;

    if (aecm == NULL)
    {
        return -1;
    }

    if (sampFreq != 8000 && sampFreq != 16000)
    {
        aecm->lastError = AECM_BAD_PARAMETER_ERROR;
        return -1;
    }
    aecm->sampFreq = sampFreq;

    // Initialize AECM core
    if (WebRtcAecm_InitCore(aecm->aecmCore, aecm->sampFreq) == -1)
    {
        aecm->lastError = AECM_UNSPECIFIED_ERROR;
        return -1;
    }

    // Initialize farend buffer
    if (WebRtc_InitBuffer(aecm->farendBuf) == -1)
    {
        aecm->lastError = AECM_UNSPECIFIED_ERROR;
        return -1;
    }

    aecm->initFlag = kInitCheck; // indicates that initialization has been done

    aecm->delayChange = 1;

    aecm->sum = 0;
    aecm->counter = 0;
    aecm->checkBuffSize = 1;
    aecm->firstVal = 0;

    aecm->ECstartup = 1;
    aecm->bufSizeStart = 0;
    aecm->checkBufSizeCtr = 0;
    aecm->filtDelay = 0;
    aecm->timeForDelayChange = 0;
    aecm->knownDelay = 0;
    aecm->lastDelayDiff = 0;

    memset(&aecm->farendOld[0][0], 0, 160);

    // Default settings.
    aecConfig.cngMode = AecmTrue;
    aecConfig.echoMode = 3;

    if (WebRtcAecm_set_config(aecm, aecConfig) == -1)
    {
        aecm->lastError = AECM_UNSPECIFIED_ERROR;
        return -1;
    }

    return 0;
}

int32_t WebRtcAecm_BufferFarend(void *aecmInst, const int16_t *farend,
                                int16_t nrOfSamples)
{
    aecmob_t *aecm = aecmInst;
    int32_t retVal = 0;

    if (aecm == NULL)
    {
        return -1;
    }

    if (farend == NULL)
    {
        aecm->lastError = AECM_NULL_POINTER_ERROR;
        return -1;
    }

    if (aecm->initFlag != kInitCheck)
    {
        aecm->lastError = AECM_UNINITIALIZED_ERROR;
        return -1;
    }

    if (nrOfSamples != 80 && nrOfSamples != 160)
    {
        aecm->lastError = AECM_BAD_PARAMETER_ERROR;
        return -1;
    }

    // TODO: Is this really a good idea?
    if (!aecm->ECstartup)
    {
        WebRtcAecm_DelayComp(aecm);
    }

    WebRtc_WriteBuffer(aecm->farendBuf, farend, (size_t) nrOfSamples);

    return retVal;
}

int32_t WebRtcAecm_Process(void *aecmInst, const int16_t *nearendNoisy,
                           const int16_t *nearendClean, int16_t *out,
                           int16_t nrOfSamples, int16_t msInSndCardBuf)
{
    aecmob_t *aecm = aecmInst;
    int32_t retVal = 0;
    short i;
    short nmbrOfFilledBuffers;
    short nBlocks10ms;
    short nFrames;
#ifdef AEC_DEBUG
    short msInAECBuf;
#endif

    if (aecm == NULL)
    {
        return -1;
    }

    if (nearendNoisy == NULL)
    {
        aecm->lastError = AECM_NULL_POINTER_ERROR;
        return -1;
    }

    if (out == NULL)
    {
        aecm->lastError = AECM_NULL_POINTER_ERROR;
        return -1;
    }

    if (aecm->initFlag != kInitCheck)
    {
        aecm->lastError = AECM_UNINITIALIZED_ERROR;
        return -1;
    }

    if (nrOfSamples != 80 && nrOfSamples != 160)
    {
        aecm->lastError = AECM_BAD_PARAMETER_ERROR;
        return -1;
    }

    if (msInSndCardBuf < 0)
    {
        msInSndCardBuf = 0;
        aecm->lastError = AECM_BAD_PARAMETER_WARNING;
        retVal = -1;
    } else if (msInSndCardBuf > 500)
    {
        msInSndCardBuf = 500;
        aecm->lastError = AECM_BAD_PARAMETER_WARNING;
        retVal = -1;
    }
    msInSndCardBuf += 10;
    aecm->msInSndCardBuf = msInSndCardBuf;

    nFrames = nrOfSamples / FRAME_LEN;
    nBlocks10ms = nFrames / aecm->aecmCore->mult;

    if (aecm->ECstartup)
    {
        if (nearendClean == NULL)
        {
            if (out != nearendNoisy)
            {
                memcpy(out, nearendNoisy, sizeof(short) * nrOfSamples);
            }
        } else if (out != nearendClean)
        {
            memcpy(out, nearendClean, sizeof(short) * nrOfSamples);
        }

        nmbrOfFilledBuffers =
            (short) WebRtc_available_read(aecm->farendBuf) / FRAME_LEN;
        // The AECM is in the start up mode
        // AECM is disabled until the soundcard buffer and farend buffers are OK

        // Mechanism to ensure that the soundcard buffer is reasonably stable.
        if (aecm->checkBuffSize)
        {
            aecm->checkBufSizeCtr++;
            // Before we fill up the far end buffer we require the amount of data on the
            // sound card to be stable (+/-8 ms) compared to the first value. This
            // comparison is made during the following 4 consecutive frames. If it seems
            // to be stable then we start to fill up the far end buffer.

            if (aecm->counter == 0)
            {
                aecm->firstVal = aecm->msInSndCardBuf;
                aecm->sum = 0;
            }

            if (abs(aecm->firstVal - aecm->msInSndCardBuf)
                    < WEBRTC_SPL_MAX(0.2 * aecm->msInSndCardBuf, kSampMsNb))
            {
                aecm->sum += aecm->msInSndCardBuf;
                aecm->counter++;
            } else
            {
                aecm->counter = 0;
            }

            if (aecm->counter * nBlocks10ms >= 6)
            {
                // The farend buffer size is determined in blocks of 80 samples
                // Use 75% of the average value of the soundcard buffer
                aecm->bufSizeStart
                        = WEBRTC_SPL_MIN((3 * aecm->sum
                                        * aecm->aecmCore->mult) / (aecm->counter * 40), BUF_SIZE_FRAMES);
                // buffersize has now been determined
                aecm->checkBuffSize = 0;
            }

            if (aecm->checkBufSizeCtr * nBlocks10ms > 50)
            {
                // for really bad sound cards, don't disable echocanceller for more than 0.5 sec
                aecm->bufSizeStart = WEBRTC_SPL_MIN((3 * aecm->msInSndCardBuf
                                * aecm->aecmCore->mult) / 40, BUF_SIZE_FRAMES);
                aecm->checkBuffSize = 0;
            }
        }

        // if checkBuffSize changed in the if-statement above
        if (!aecm->checkBuffSize)
        {
            // soundcard buffer is now reasonably stable
            // When the far end buffer is filled with approximately the same amount of
            // data as the amount on the sound card we end the start up phase and start
            // to cancel echoes.

            if (nmbrOfFilledBuffers == aecm->bufSizeStart)
            {
                aecm->ECstartup = 0; // Enable the AECM
            } else if (nmbrOfFilledBuffers > aecm->bufSizeStart)
            {
                WebRtc_MoveReadPtr(aecm->farendBuf,
                                   (int) WebRtc_available_read(aecm->farendBuf)
                                   - (int) aecm->bufSizeStart * FRAME_LEN);
                aecm->ECstartup = 0;
            }
        }

    } else
    {
        // AECM is enabled

        // Note only 1 block supported for nb and 2 blocks for wb
        for (i = 0; i < nFrames; i++)
        {
            int16_t farend[FRAME_LEN];
            const int16_t* farend_ptr = NULL;

            nmbrOfFilledBuffers =
                (short) WebRtc_available_read(aecm->farendBuf) / FRAME_LEN;

            // Check that there is data in the far end buffer
            if (nmbrOfFilledBuffers > 0)
            {
                // Get the next 80 samples from the farend buffer
                WebRtc_ReadBuffer(aecm->farendBuf, (void**) &farend_ptr, farend,
                                  FRAME_LEN);

                // Always store the last frame for use when we run out of data
                memcpy(&(aecm->farendOld[i][0]), farend_ptr,
                       FRAME_LEN * sizeof(short));
            } else
            {
                // We have no data so we use the last played frame
                memcpy(farend, &(aecm->farendOld[i][0]), FRAME_LEN * sizeof(short));
                farend_ptr = farend;
            }

            // Call buffer delay estimator when all data is extracted,
            // i,e. i = 0 for NB and i = 1 for WB
            if ((i == 0 && aecm->sampFreq == 8000) || (i == 1 && aecm->sampFreq == 16000))
            {
                WebRtcAecm_EstBufDelay(aecm, aecm->msInSndCardBuf);
            }

            // Call the AECM
            /*WebRtcAecm_ProcessFrame(aecm->aecmCore, farend, &nearend[FRAME_LEN * i],
             &out[FRAME_LEN * i], aecm->knownDelay);*/
            if (WebRtcAecm_ProcessFrame(aecm->aecmCore,
                                        farend_ptr,
                                        &nearendNoisy[FRAME_LEN * i],
                                        (nearendClean
                                         ? &nearendClean[FRAME_LEN * i]
                                         : NULL),
                                        &out[FRAME_LEN * i]) == -1)
                return -1;
        }
    }

#ifdef AEC_DEBUG
    msInAECBuf = (short) WebRtc_available_read(aecm->farendBuf) /
        (kSampMsNb * aecm->aecmCore->mult);
    fwrite(&msInAECBuf, 2, 1, aecm->bufFile);
    fwrite(&(aecm->knownDelay), sizeof(aecm->knownDelay), 1, aecm->delayFile);
#endif

    return retVal;
}

int32_t WebRtcAecm_set_config(void *aecmInst, AecmConfig config)
{
    aecmob_t *aecm = aecmInst;

    if (aecm == NULL)
    {
        return -1;
    }

    if (aecm->initFlag != kInitCheck)
    {
        aecm->lastError = AECM_UNINITIALIZED_ERROR;
        return -1;
    }

    if (config.cngMode != AecmFalse && config.cngMode != AecmTrue)
    {
        aecm->lastError = AECM_BAD_PARAMETER_ERROR;
        return -1;
    }
    aecm->aecmCore->cngMode = config.cngMode;

    if (config.echoMode < 0 || config.echoMode > 4)
    {
        aecm->lastError = AECM_BAD_PARAMETER_ERROR;
        return -1;
    }
    aecm->echoMode = config.echoMode;

    if (aecm->echoMode == 0)
    {
        aecm->aecmCore->supGain = SUPGAIN_DEFAULT >> 3;
        aecm->aecmCore->supGainOld = SUPGAIN_DEFAULT >> 3;
        aecm->aecmCore->supGainErrParamA = SUPGAIN_ERROR_PARAM_A >> 3;
        aecm->aecmCore->supGainErrParamD = SUPGAIN_ERROR_PARAM_D >> 3;
        aecm->aecmCore->supGainErrParamDiffAB = (SUPGAIN_ERROR_PARAM_A >> 3)
                - (SUPGAIN_ERROR_PARAM_B >> 3);
        aecm->aecmCore->supGainErrParamDiffBD = (SUPGAIN_ERROR_PARAM_B >> 3)
                - (SUPGAIN_ERROR_PARAM_D >> 3);
    } else if (aecm->echoMode == 1)
    {
        aecm->aecmCore->supGain = SUPGAIN_DEFAULT >> 2;
        aecm->aecmCore->supGainOld = SUPGAIN_DEFAULT >> 2;
        aecm->aecmCore->supGainErrParamA = SUPGAIN_ERROR_PARAM_A >> 2;
        aecm->aecmCore->supGainErrParamD = SUPGAIN_ERROR_PARAM_D >> 2;
        aecm->aecmCore->supGainErrParamDiffAB = (SUPGAIN_ERROR_PARAM_A >> 2)
                - (SUPGAIN_ERROR_PARAM_B >> 2);
        aecm->aecmCore->supGainErrParamDiffBD = (SUPGAIN_ERROR_PARAM_B >> 2)
                - (SUPGAIN_ERROR_PARAM_D >> 2);
    } else if (aecm->echoMode == 2)
    {
        aecm->aecmCore->supGain = SUPGAIN_DEFAULT >> 1;
        aecm->aecmCore->supGainOld = SUPGAIN_DEFAULT >> 1;
        aecm->aecmCore->supGainErrParamA = SUPGAIN_ERROR_PARAM_A >> 1;
        aecm->aecmCore->supGainErrParamD = SUPGAIN_ERROR_PARAM_D >> 1;
        aecm->aecmCore->supGainErrParamDiffAB = (SUPGAIN_ERROR_PARAM_A >> 1)
                - (SUPGAIN_ERROR_PARAM_B >> 1);
        aecm->aecmCore->supGainErrParamDiffBD = (SUPGAIN_ERROR_PARAM_B >> 1)
                - (SUPGAIN_ERROR_PARAM_D >> 1);
    } else if (aecm->echoMode == 3)
    {
        aecm->aecmCore->supGain = SUPGAIN_DEFAULT;
        aecm->aecmCore->supGainOld = SUPGAIN_DEFAULT;
        aecm->aecmCore->supGainErrParamA = SUPGAIN_ERROR_PARAM_A;
        aecm->aecmCore->supGainErrParamD = SUPGAIN_ERROR_PARAM_D;
        aecm->aecmCore->supGainErrParamDiffAB = SUPGAIN_ERROR_PARAM_A - SUPGAIN_ERROR_PARAM_B;
        aecm->aecmCore->supGainErrParamDiffBD = SUPGAIN_ERROR_PARAM_B - SUPGAIN_ERROR_PARAM_D;
    } else if (aecm->echoMode == 4)
    {
        aecm->aecmCore->supGain = SUPGAIN_DEFAULT << 1;
        aecm->aecmCore->supGainOld = SUPGAIN_DEFAULT << 1;
        aecm->aecmCore->supGainErrParamA = SUPGAIN_ERROR_PARAM_A << 1;
        aecm->aecmCore->supGainErrParamD = SUPGAIN_ERROR_PARAM_D << 1;
        aecm->aecmCore->supGainErrParamDiffAB = (SUPGAIN_ERROR_PARAM_A << 1)
                - (SUPGAIN_ERROR_PARAM_B << 1);
        aecm->aecmCore->supGainErrParamDiffBD = (SUPGAIN_ERROR_PARAM_B << 1)
                - (SUPGAIN_ERROR_PARAM_D << 1);
    }

    return 0;
}

int32_t WebRtcAecm_get_config(void *aecmInst, AecmConfig *config)
{
    aecmob_t *aecm = aecmInst;

    if (aecm == NULL)
    {
        return -1;
    }

    if (config == NULL)
    {
        aecm->lastError = AECM_NULL_POINTER_ERROR;
        return -1;
    }

    if (aecm->initFlag != kInitCheck)
    {
        aecm->lastError = AECM_UNINITIALIZED_ERROR;
        return -1;
    }

    config->cngMode = aecm->aecmCore->cngMode;
    config->echoMode = aecm->echoMode;

    return 0;
}

int32_t WebRtcAecm_InitEchoPath(void* aecmInst,
                                const void* echo_path,
                                size_t size_bytes)
{
    aecmob_t *aecm = aecmInst;
    const int16_t* echo_path_ptr = echo_path;

    if (aecmInst == NULL) {
      return -1;
    }
    if (echo_path == NULL) {
      aecm->lastError = AECM_NULL_POINTER_ERROR;
      return -1;
    }
    if (size_bytes != WebRtcAecm_echo_path_size_bytes())
    {
        // Input channel size does not match the size of AECM
        aecm->lastError = AECM_BAD_PARAMETER_ERROR;
        return -1;
    }
    if (aecm->initFlag != kInitCheck)
    {
        aecm->lastError = AECM_UNINITIALIZED_ERROR;
        return -1;
    }

    WebRtcAecm_InitEchoPathCore(aecm->aecmCore, echo_path_ptr);

    return 0;
}

int32_t WebRtcAecm_GetEchoPath(void* aecmInst,
                               void* echo_path,
                               size_t size_bytes)
{
    aecmob_t *aecm = aecmInst;
    int16_t* echo_path_ptr = echo_path;

    if (aecmInst == NULL) {
      return -1;
    }
    if (echo_path == NULL) {
      aecm->lastError = AECM_NULL_POINTER_ERROR;
      return -1;
    }
    if (size_bytes != WebRtcAecm_echo_path_size_bytes())
    {
        // Input channel size does not match the size of AECM
        aecm->lastError = AECM_BAD_PARAMETER_ERROR;
        return -1;
    }
    if (aecm->initFlag != kInitCheck)
    {
        aecm->lastError = AECM_UNINITIALIZED_ERROR;
        return -1;
    }

    memcpy(echo_path_ptr, aecm->aecmCore->channelStored, size_bytes);
    return 0;
}

size_t WebRtcAecm_echo_path_size_bytes()
{
    return (PART_LEN1 * sizeof(int16_t));
}

int32_t WebRtcAecm_get_error_code(void *aecmInst)
{
    aecmob_t *aecm = aecmInst;

    if (aecm == NULL)
    {
        return -1;
    }

    return aecm->lastError;
}

static int WebRtcAecm_EstBufDelay(aecmob_t *aecm, short msInSndCardBuf)
{
    short delayNew, nSampSndCard;
    short nSampFar = (short) WebRtc_available_read(aecm->farendBuf);
    short diff;

    nSampSndCard = msInSndCardBuf * kSampMsNb * aecm->aecmCore->mult;

    delayNew = nSampSndCard - nSampFar;

    if (delayNew < FRAME_LEN)
    {
        WebRtc_MoveReadPtr(aecm->farendBuf, FRAME_LEN);
        delayNew += FRAME_LEN;
    }

    aecm->filtDelay = WEBRTC_SPL_MAX(0, (8 * aecm->filtDelay + 2 * delayNew) / 10);

    diff = aecm->filtDelay - aecm->knownDelay;
    if (diff > 224)
    {
        if (aecm->lastDelayDiff < 96)
        {
            aecm->timeForDelayChange = 0;
        } else
        {
            aecm->timeForDelayChange++;
        }
    } else if (diff < 96 && aecm->knownDelay > 0)
    {
        if (aecm->lastDelayDiff > 224)
        {
            aecm->timeForDelayChange = 0;
        } else
        {
            aecm->timeForDelayChange++;
        }
    } else
    {
        aecm->timeForDelayChange = 0;
    }
    aecm->lastDelayDiff = diff;

    if (aecm->timeForDelayChange > 25)
    {
        aecm->knownDelay = WEBRTC_SPL_MAX((int)aecm->filtDelay - 160, 0);
    }
    return 0;
}

static int WebRtcAecm_DelayComp(aecmob_t *aecm)
{
    int nSampFar = (int) WebRtc_available_read(aecm->farendBuf);
    int nSampSndCard, delayNew, nSampAdd;
    const int maxStuffSamp = 10 * FRAME_LEN;

    nSampSndCard = aecm->msInSndCardBuf * kSampMsNb * aecm->aecmCore->mult;
    delayNew = nSampSndCard - nSampFar;

    if (delayNew > FAR_BUF_LEN - FRAME_LEN * aecm->aecmCore->mult)
    {
        // The difference of the buffer sizes is larger than the maximum
        // allowed known delay. Compensate by stuffing the buffer.
        nSampAdd = (int)(WEBRTC_SPL_MAX(((nSampSndCard >> 1) - nSampFar),
                FRAME_LEN));
        nSampAdd = WEBRTC_SPL_MIN(nSampAdd, maxStuffSamp);

        WebRtc_MoveReadPtr(aecm->farendBuf, -nSampAdd);
        aecm->delayChange = 1; // the delay needs to be updated
    }

    return 0;
}
