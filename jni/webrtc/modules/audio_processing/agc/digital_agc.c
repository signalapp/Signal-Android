/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/* digital_agc.c
 *
 */

#include "webrtc/modules/audio_processing/agc/digital_agc.h"

#include <assert.h>
#include <string.h>
#ifdef AGC_DEBUG
#include <stdio.h>
#endif

#include "webrtc/modules/audio_processing/agc/include/gain_control.h"

// To generate the gaintable, copy&paste the following lines to a Matlab window:
// MaxGain = 6; MinGain = 0; CompRatio = 3; Knee = 1;
// zeros = 0:31; lvl = 2.^(1-zeros);
// A = -10*log10(lvl) * (CompRatio - 1) / CompRatio;
// B = MaxGain - MinGain;
// gains = round(2^16*10.^(0.05 * (MinGain + B * ( log(exp(-Knee*A)+exp(-Knee*B)) - log(1+exp(-Knee*B)) ) / log(1/(1+exp(Knee*B))))));
// fprintf(1, '\t%i, %i, %i, %i,\n', gains);
// % Matlab code for plotting the gain and input/output level characteristic (copy/paste the following 3 lines):
// in = 10*log10(lvl); out = 20*log10(gains/65536);
// subplot(121); plot(in, out); axis([-30, 0, -5, 20]); grid on; xlabel('Input (dB)'); ylabel('Gain (dB)');
// subplot(122); plot(in, in+out); axis([-30, 0, -30, 5]); grid on; xlabel('Input (dB)'); ylabel('Output (dB)');
// zoom on;

// Generator table for y=log2(1+e^x) in Q8.
enum { kGenFuncTableSize = 128 };
static const uint16_t kGenFuncTable[kGenFuncTableSize] = {
          256,   485,   786,  1126,  1484,  1849,  2217,  2586,
         2955,  3324,  3693,  4063,  4432,  4801,  5171,  5540,
         5909,  6279,  6648,  7017,  7387,  7756,  8125,  8495,
         8864,  9233,  9603,  9972, 10341, 10711, 11080, 11449,
        11819, 12188, 12557, 12927, 13296, 13665, 14035, 14404,
        14773, 15143, 15512, 15881, 16251, 16620, 16989, 17359,
        17728, 18097, 18466, 18836, 19205, 19574, 19944, 20313,
        20682, 21052, 21421, 21790, 22160, 22529, 22898, 23268,
        23637, 24006, 24376, 24745, 25114, 25484, 25853, 26222,
        26592, 26961, 27330, 27700, 28069, 28438, 28808, 29177,
        29546, 29916, 30285, 30654, 31024, 31393, 31762, 32132,
        32501, 32870, 33240, 33609, 33978, 34348, 34717, 35086,
        35456, 35825, 36194, 36564, 36933, 37302, 37672, 38041,
        38410, 38780, 39149, 39518, 39888, 40257, 40626, 40996,
        41365, 41734, 42104, 42473, 42842, 43212, 43581, 43950,
        44320, 44689, 45058, 45428, 45797, 46166, 46536, 46905
};

static const int16_t kAvgDecayTime = 250; // frames; < 3000

int32_t WebRtcAgc_CalculateGainTable(int32_t *gainTable, // Q16
                                     int16_t digCompGaindB, // Q0
                                     int16_t targetLevelDbfs,// Q0
                                     uint8_t limiterEnable,
                                     int16_t analogTarget) // Q0
{
    // This function generates the compressor gain table used in the fixed digital part.
    uint32_t tmpU32no1, tmpU32no2, absInLevel, logApprox;
    int32_t inLevel, limiterLvl;
    int32_t tmp32, tmp32no1, tmp32no2, numFIX, den, y32;
    const uint16_t kLog10 = 54426; // log2(10)     in Q14
    const uint16_t kLog10_2 = 49321; // 10*log10(2)  in Q14
    const uint16_t kLogE_1 = 23637; // log2(e)      in Q14
    uint16_t constMaxGain;
    uint16_t tmpU16, intPart, fracPart;
    const int16_t kCompRatio = 3;
    const int16_t kSoftLimiterLeft = 1;
    int16_t limiterOffset = 0; // Limiter offset
    int16_t limiterIdx, limiterLvlX;
    int16_t constLinApprox, zeroGainLvl, maxGain, diffGain;
    int16_t i, tmp16, tmp16no1;
    int zeros, zerosScale;

    // Constants
//    kLogE_1 = 23637; // log2(e)      in Q14
//    kLog10 = 54426; // log2(10)     in Q14
//    kLog10_2 = 49321; // 10*log10(2)  in Q14

    // Calculate maximum digital gain and zero gain level
    tmp32no1 = WEBRTC_SPL_MUL_16_16(digCompGaindB - analogTarget, kCompRatio - 1);
    tmp16no1 = analogTarget - targetLevelDbfs;
    tmp16no1 += WebRtcSpl_DivW32W16ResW16(tmp32no1 + (kCompRatio >> 1), kCompRatio);
    maxGain = WEBRTC_SPL_MAX(tmp16no1, (analogTarget - targetLevelDbfs));
    tmp32no1 = WEBRTC_SPL_MUL_16_16(maxGain, kCompRatio);
    zeroGainLvl = digCompGaindB;
    zeroGainLvl -= WebRtcSpl_DivW32W16ResW16(tmp32no1 + ((kCompRatio - 1) >> 1),
                                             kCompRatio - 1);
    if ((digCompGaindB <= analogTarget) && (limiterEnable))
    {
        zeroGainLvl += (analogTarget - digCompGaindB + kSoftLimiterLeft);
        limiterOffset = 0;
    }

    // Calculate the difference between maximum gain and gain at 0dB0v:
    //  diffGain = maxGain + (compRatio-1)*zeroGainLvl/compRatio
    //           = (compRatio-1)*digCompGaindB/compRatio
    tmp32no1 = WEBRTC_SPL_MUL_16_16(digCompGaindB, kCompRatio - 1);
    diffGain = WebRtcSpl_DivW32W16ResW16(tmp32no1 + (kCompRatio >> 1), kCompRatio);
    if (diffGain < 0 || diffGain >= kGenFuncTableSize)
    {
        assert(0);
        return -1;
    }

    // Calculate the limiter level and index:
    //  limiterLvlX = analogTarget - limiterOffset
    //  limiterLvl  = targetLevelDbfs + limiterOffset/compRatio
    limiterLvlX = analogTarget - limiterOffset;
    limiterIdx = 2
            + WebRtcSpl_DivW32W16ResW16(WEBRTC_SPL_LSHIFT_W32((int32_t)limiterLvlX, 13),
                                        (kLog10_2 / 2));
    tmp16no1 = WebRtcSpl_DivW32W16ResW16(limiterOffset + (kCompRatio >> 1), kCompRatio);
    limiterLvl = targetLevelDbfs + tmp16no1;

    // Calculate (through table lookup):
    //  constMaxGain = log2(1+2^(log2(e)*diffGain)); (in Q8)
    constMaxGain = kGenFuncTable[diffGain]; // in Q8

    // Calculate a parameter used to approximate the fractional part of 2^x with a
    // piecewise linear function in Q14:
    //  constLinApprox = round(3/2*(4*(3-2*sqrt(2))/(log(2)^2)-0.5)*2^14);
    constLinApprox = 22817; // in Q14

    // Calculate a denominator used in the exponential part to convert from dB to linear scale:
    //  den = 20*constMaxGain (in Q8)
    den = WEBRTC_SPL_MUL_16_U16(20, constMaxGain); // in Q8

    for (i = 0; i < 32; i++)
    {
        // Calculate scaled input level (compressor):
        //  inLevel = fix((-constLog10_2*(compRatio-1)*(1-i)+fix(compRatio/2))/compRatio)
        tmp16 = (int16_t)WEBRTC_SPL_MUL_16_16(kCompRatio - 1, i - 1); // Q0
        tmp32 = WEBRTC_SPL_MUL_16_U16(tmp16, kLog10_2) + 1; // Q14
        inLevel = WebRtcSpl_DivW32W16(tmp32, kCompRatio); // Q14

        // Calculate diffGain-inLevel, to map using the genFuncTable
        inLevel = WEBRTC_SPL_LSHIFT_W32((int32_t)diffGain, 14) - inLevel; // Q14

        // Make calculations on abs(inLevel) and compensate for the sign afterwards.
        absInLevel = (uint32_t)WEBRTC_SPL_ABS_W32(inLevel); // Q14

        // LUT with interpolation
        intPart = (uint16_t)WEBRTC_SPL_RSHIFT_U32(absInLevel, 14);
        fracPart = (uint16_t)(absInLevel & 0x00003FFF); // extract the fractional part
        tmpU16 = kGenFuncTable[intPart + 1] - kGenFuncTable[intPart]; // Q8
        tmpU32no1 = WEBRTC_SPL_UMUL_16_16(tmpU16, fracPart); // Q22
        tmpU32no1 += WEBRTC_SPL_LSHIFT_U32((uint32_t)kGenFuncTable[intPart], 14); // Q22
        logApprox = WEBRTC_SPL_RSHIFT_U32(tmpU32no1, 8); // Q14
        // Compensate for negative exponent using the relation:
        //  log2(1 + 2^-x) = log2(1 + 2^x) - x
        if (inLevel < 0)
        {
            zeros = WebRtcSpl_NormU32(absInLevel);
            zerosScale = 0;
            if (zeros < 15)
            {
                // Not enough space for multiplication
                tmpU32no2 = WEBRTC_SPL_RSHIFT_U32(absInLevel, 15 - zeros); // Q(zeros-1)
                tmpU32no2 = WEBRTC_SPL_UMUL_32_16(tmpU32no2, kLogE_1); // Q(zeros+13)
                if (zeros < 9)
                {
                    tmpU32no1 = WEBRTC_SPL_RSHIFT_U32(tmpU32no1, 9 - zeros); // Q(zeros+13)
                    zerosScale = 9 - zeros;
                } else
                {
                    tmpU32no2 = WEBRTC_SPL_RSHIFT_U32(tmpU32no2, zeros - 9); // Q22
                }
            } else
            {
                tmpU32no2 = WEBRTC_SPL_UMUL_32_16(absInLevel, kLogE_1); // Q28
                tmpU32no2 = WEBRTC_SPL_RSHIFT_U32(tmpU32no2, 6); // Q22
            }
            logApprox = 0;
            if (tmpU32no2 < tmpU32no1)
            {
                logApprox = WEBRTC_SPL_RSHIFT_U32(tmpU32no1 - tmpU32no2, 8 - zerosScale); //Q14
            }
        }
        numFIX = WEBRTC_SPL_LSHIFT_W32(WEBRTC_SPL_MUL_16_U16(maxGain, constMaxGain), 6); // Q14
        numFIX -= (int32_t)logApprox * diffGain;  // Q14

        // Calculate ratio
        // Shift |numFIX| as much as possible.
        // Ensure we avoid wrap-around in |den| as well.
        if (numFIX > (den >> 8))  // |den| is Q8.
        {
            zeros = WebRtcSpl_NormW32(numFIX);
        } else
        {
            zeros = WebRtcSpl_NormW32(den) + 8;
        }
        numFIX = WEBRTC_SPL_LSHIFT_W32(numFIX, zeros); // Q(14+zeros)

        // Shift den so we end up in Qy1
        tmp32no1 = WEBRTC_SPL_SHIFT_W32(den, zeros - 8); // Q(zeros)
        if (numFIX < 0)
        {
            numFIX -= WEBRTC_SPL_RSHIFT_W32(tmp32no1, 1);
        } else
        {
            numFIX += WEBRTC_SPL_RSHIFT_W32(tmp32no1, 1);
        }
        y32 = WEBRTC_SPL_DIV(numFIX, tmp32no1); // in Q14
        if (limiterEnable && (i < limiterIdx))
        {
            tmp32 = WEBRTC_SPL_MUL_16_U16(i - 1, kLog10_2); // Q14
            tmp32 -= WEBRTC_SPL_LSHIFT_W32(limiterLvl, 14); // Q14
            y32 = WebRtcSpl_DivW32W16(tmp32 + 10, 20);
        }
        if (y32 > 39000)
        {
            tmp32 = WEBRTC_SPL_MUL(y32 >> 1, kLog10) + 4096; // in Q27
            tmp32 = WEBRTC_SPL_RSHIFT_W32(tmp32, 13); // in Q14
        } else
        {
            tmp32 = WEBRTC_SPL_MUL(y32, kLog10) + 8192; // in Q28
            tmp32 = WEBRTC_SPL_RSHIFT_W32(tmp32, 14); // in Q14
        }
        tmp32 += WEBRTC_SPL_LSHIFT_W32(16, 14); // in Q14 (Make sure final output is in Q16)

        // Calculate power
        if (tmp32 > 0)
        {
            intPart = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32, 14);
            fracPart = (uint16_t)(tmp32 & 0x00003FFF); // in Q14
            if (WEBRTC_SPL_RSHIFT_W32(fracPart, 13))
            {
                tmp16 = WEBRTC_SPL_LSHIFT_W16(2, 14) - constLinApprox;
                tmp32no2 = WEBRTC_SPL_LSHIFT_W32(1, 14) - fracPart;
                tmp32no2 *= tmp16;
                tmp32no2 = WEBRTC_SPL_RSHIFT_W32(tmp32no2, 13);
                tmp32no2 = WEBRTC_SPL_LSHIFT_W32(1, 14) - tmp32no2;
            } else
            {
                tmp16 = constLinApprox - WEBRTC_SPL_LSHIFT_W16(1, 14);
                tmp32no2 = fracPart * tmp16;
                tmp32no2 = WEBRTC_SPL_RSHIFT_W32(tmp32no2, 13);
            }
            fracPart = (uint16_t)tmp32no2;
            gainTable[i] = WEBRTC_SPL_LSHIFT_W32(1, intPart)
                    + WEBRTC_SPL_SHIFT_W32(fracPart, intPart - 14);
        } else
        {
            gainTable[i] = 0;
        }
    }

    return 0;
}

int32_t WebRtcAgc_InitDigital(DigitalAgc_t *stt, int16_t agcMode)
{

    if (agcMode == kAgcModeFixedDigital)
    {
        // start at minimum to find correct gain faster
        stt->capacitorSlow = 0;
    } else
    {
        // start out with 0 dB gain
        stt->capacitorSlow = 134217728; // (int32_t)(0.125f * 32768.0f * 32768.0f);
    }
    stt->capacitorFast = 0;
    stt->gain = 65536;
    stt->gatePrevious = 0;
    stt->agcMode = agcMode;
#ifdef AGC_DEBUG
    stt->frameCounter = 0;
#endif

    // initialize VADs
    WebRtcAgc_InitVad(&stt->vadNearend);
    WebRtcAgc_InitVad(&stt->vadFarend);

    return 0;
}

int32_t WebRtcAgc_AddFarendToDigital(DigitalAgc_t *stt, const int16_t *in_far,
                                     int16_t nrSamples)
{
    assert(stt != NULL);
    // VAD for far end
    WebRtcAgc_ProcessVad(&stt->vadFarend, in_far, nrSamples);

    return 0;
}

int32_t WebRtcAgc_ProcessDigital(DigitalAgc_t *stt, const int16_t *in_near,
                                 const int16_t *in_near_H, int16_t *out,
                                 int16_t *out_H, uint32_t FS,
                                 int16_t lowlevelSignal)
{
    // array for gains (one value per ms, incl start & end)
    int32_t gains[11];

    int32_t out_tmp, tmp32;
    int32_t env[10];
    int32_t nrg, max_nrg;
    int32_t cur_level;
    int32_t gain32, delta;
    int16_t logratio;
    int16_t lower_thr, upper_thr;
    int16_t zeros = 0, zeros_fast, frac = 0;
    int16_t decay;
    int16_t gate, gain_adj;
    int16_t k, n;
    int16_t L, L2; // samples/subframe

    // determine number of samples per ms
    if (FS == 8000)
    {
        L = 8;
        L2 = 3;
    } else if (FS == 16000)
    {
        L = 16;
        L2 = 4;
    } else if (FS == 32000)
    {
        L = 16;
        L2 = 4;
    } else
    {
        return -1;
    }

    // TODO(andrew): again, we don't need input and output pointers...
    if (in_near != out)
    {
        // Only needed if they don't already point to the same place.
        memcpy(out, in_near, 10 * L * sizeof(int16_t));
    }
    if (FS == 32000)
    {
        if (in_near_H != out_H)
        {
            memcpy(out_H, in_near_H, 10 * L * sizeof(int16_t));
        }
    }
    // VAD for near end
    logratio = WebRtcAgc_ProcessVad(&stt->vadNearend, out, L * 10);

    // Account for far end VAD
    if (stt->vadFarend.counter > 10)
    {
        tmp32 = WEBRTC_SPL_MUL_16_16(3, logratio);
        logratio = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32 - stt->vadFarend.logRatio, 2);
    }

    // Determine decay factor depending on VAD
    //  upper_thr = 1.0f;
    //  lower_thr = 0.25f;
    upper_thr = 1024; // Q10
    lower_thr = 0; // Q10
    if (logratio > upper_thr)
    {
        // decay = -2^17 / DecayTime;  ->  -65
        decay = -65;
    } else if (logratio < lower_thr)
    {
        decay = 0;
    } else
    {
        // decay = (int16_t)(((lower_thr - logratio)
        //       * (2^27/(DecayTime*(upper_thr-lower_thr)))) >> 10);
        // SUBSTITUTED: 2^27/(DecayTime*(upper_thr-lower_thr))  ->  65
        tmp32 = WEBRTC_SPL_MUL_16_16((lower_thr - logratio), 65);
        decay = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32, 10);
    }

    // adjust decay factor for long silence (detected as low standard deviation)
    // This is only done in the adaptive modes
    if (stt->agcMode != kAgcModeFixedDigital)
    {
        if (stt->vadNearend.stdLongTerm < 4000)
        {
            decay = 0;
        } else if (stt->vadNearend.stdLongTerm < 8096)
        {
            // decay = (int16_t)(((stt->vadNearend.stdLongTerm - 4000) * decay) >> 12);
            tmp32 = WEBRTC_SPL_MUL_16_16((stt->vadNearend.stdLongTerm - 4000), decay);
            decay = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32, 12);
        }

        if (lowlevelSignal != 0)
        {
            decay = 0;
        }
    }
#ifdef AGC_DEBUG
    stt->frameCounter++;
    fprintf(stt->logFile, "%5.2f\t%d\t%d\t%d\t", (float)(stt->frameCounter) / 100, logratio, decay, stt->vadNearend.stdLongTerm);
#endif
    // Find max amplitude per sub frame
    // iterate over sub frames
    for (k = 0; k < 10; k++)
    {
        // iterate over samples
        max_nrg = 0;
        for (n = 0; n < L; n++)
        {
            nrg = WEBRTC_SPL_MUL_16_16(out[k * L + n], out[k * L + n]);
            if (nrg > max_nrg)
            {
                max_nrg = nrg;
            }
        }
        env[k] = max_nrg;
    }

    // Calculate gain per sub frame
    gains[0] = stt->gain;
    for (k = 0; k < 10; k++)
    {
        // Fast envelope follower
        //  decay time = -131000 / -1000 = 131 (ms)
        stt->capacitorFast = AGC_SCALEDIFF32(-1000, stt->capacitorFast, stt->capacitorFast);
        if (env[k] > stt->capacitorFast)
        {
            stt->capacitorFast = env[k];
        }
        // Slow envelope follower
        if (env[k] > stt->capacitorSlow)
        {
            // increase capacitorSlow
            stt->capacitorSlow
                    = AGC_SCALEDIFF32(500, (env[k] - stt->capacitorSlow), stt->capacitorSlow);
        } else
        {
            // decrease capacitorSlow
            stt->capacitorSlow
                    = AGC_SCALEDIFF32(decay, stt->capacitorSlow, stt->capacitorSlow);
        }

        // use maximum of both capacitors as current level
        if (stt->capacitorFast > stt->capacitorSlow)
        {
            cur_level = stt->capacitorFast;
        } else
        {
            cur_level = stt->capacitorSlow;
        }
        // Translate signal level into gain, using a piecewise linear approximation
        // find number of leading zeros
        zeros = WebRtcSpl_NormU32((uint32_t)cur_level);
        if (cur_level == 0)
        {
            zeros = 31;
        }
        tmp32 = (WEBRTC_SPL_LSHIFT_W32(cur_level, zeros) & 0x7FFFFFFF);
        frac = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32, 19); // Q12
        tmp32 = WEBRTC_SPL_MUL((stt->gainTable[zeros-1] - stt->gainTable[zeros]), frac);
        gains[k + 1] = stt->gainTable[zeros] + WEBRTC_SPL_RSHIFT_W32(tmp32, 12);
#ifdef AGC_DEBUG
        if (k == 0)
        {
            fprintf(stt->logFile, "%d\t%d\t%d\t%d\t%d\n", env[0], cur_level, stt->capacitorFast, stt->capacitorSlow, zeros);
        }
#endif
    }

    // Gate processing (lower gain during absence of speech)
    zeros = WEBRTC_SPL_LSHIFT_W16(zeros, 9) - WEBRTC_SPL_RSHIFT_W16(frac, 3);
    // find number of leading zeros
    zeros_fast = WebRtcSpl_NormU32((uint32_t)stt->capacitorFast);
    if (stt->capacitorFast == 0)
    {
        zeros_fast = 31;
    }
    tmp32 = (WEBRTC_SPL_LSHIFT_W32(stt->capacitorFast, zeros_fast) & 0x7FFFFFFF);
    zeros_fast = WEBRTC_SPL_LSHIFT_W16(zeros_fast, 9);
    zeros_fast -= (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32, 22);

    gate = 1000 + zeros_fast - zeros - stt->vadNearend.stdShortTerm;

    if (gate < 0)
    {
        stt->gatePrevious = 0;
    } else
    {
        tmp32 = WEBRTC_SPL_MUL_16_16(stt->gatePrevious, 7);
        gate = (int16_t)WEBRTC_SPL_RSHIFT_W32((int32_t)gate + tmp32, 3);
        stt->gatePrevious = gate;
    }
    // gate < 0     -> no gate
    // gate > 2500  -> max gate
    if (gate > 0)
    {
        if (gate < 2500)
        {
            gain_adj = WEBRTC_SPL_RSHIFT_W16(2500 - gate, 5);
        } else
        {
            gain_adj = 0;
        }
        for (k = 0; k < 10; k++)
        {
            if ((gains[k + 1] - stt->gainTable[0]) > 8388608)
            {
                // To prevent wraparound
                tmp32 = WEBRTC_SPL_RSHIFT_W32((gains[k+1] - stt->gainTable[0]), 8);
                tmp32 = WEBRTC_SPL_MUL(tmp32, (178 + gain_adj));
            } else
            {
                tmp32 = WEBRTC_SPL_MUL((gains[k+1] - stt->gainTable[0]), (178 + gain_adj));
                tmp32 = WEBRTC_SPL_RSHIFT_W32(tmp32, 8);
            }
            gains[k + 1] = stt->gainTable[0] + tmp32;
        }
    }

    // Limit gain to avoid overload distortion
    for (k = 0; k < 10; k++)
    {
        // To prevent wrap around
        zeros = 10;
        if (gains[k + 1] > 47453132)
        {
            zeros = 16 - WebRtcSpl_NormW32(gains[k + 1]);
        }
        gain32 = WEBRTC_SPL_RSHIFT_W32(gains[k+1], zeros) + 1;
        gain32 = WEBRTC_SPL_MUL(gain32, gain32);
        // check for overflow
        while (AGC_MUL32(WEBRTC_SPL_RSHIFT_W32(env[k], 12) + 1, gain32)
                > WEBRTC_SPL_SHIFT_W32((int32_t)32767, 2 * (1 - zeros + 10)))
        {
            // multiply by 253/256 ==> -0.1 dB
            if (gains[k + 1] > 8388607)
            {
                // Prevent wrap around
                gains[k + 1] = WEBRTC_SPL_MUL(WEBRTC_SPL_RSHIFT_W32(gains[k+1], 8), 253);
            } else
            {
                gains[k + 1] = WEBRTC_SPL_RSHIFT_W32(WEBRTC_SPL_MUL(gains[k+1], 253), 8);
            }
            gain32 = WEBRTC_SPL_RSHIFT_W32(gains[k+1], zeros) + 1;
            gain32 = WEBRTC_SPL_MUL(gain32, gain32);
        }
    }
    // gain reductions should be done 1 ms earlier than gain increases
    for (k = 1; k < 10; k++)
    {
        if (gains[k] > gains[k + 1])
        {
            gains[k] = gains[k + 1];
        }
    }
    // save start gain for next frame
    stt->gain = gains[10];

    // Apply gain
    // handle first sub frame separately
    delta = WEBRTC_SPL_LSHIFT_W32(gains[1] - gains[0], (4 - L2));
    gain32 = WEBRTC_SPL_LSHIFT_W32(gains[0], 4);
    // iterate over samples
    for (n = 0; n < L; n++)
    {
        // For lower band
        tmp32 = WEBRTC_SPL_MUL((int32_t)out[n], WEBRTC_SPL_RSHIFT_W32(gain32 + 127, 7));
        out_tmp = WEBRTC_SPL_RSHIFT_W32(tmp32 , 16);
        if (out_tmp > 4095)
        {
            out[n] = (int16_t)32767;
        } else if (out_tmp < -4096)
        {
            out[n] = (int16_t)-32768;
        } else
        {
            tmp32 = WEBRTC_SPL_MUL((int32_t)out[n], WEBRTC_SPL_RSHIFT_W32(gain32, 4));
            out[n] = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32 , 16);
        }
        // For higher band
        if (FS == 32000)
        {
            tmp32 = WEBRTC_SPL_MUL((int32_t)out_H[n],
                                   WEBRTC_SPL_RSHIFT_W32(gain32 + 127, 7));
            out_tmp = WEBRTC_SPL_RSHIFT_W32(tmp32 , 16);
            if (out_tmp > 4095)
            {
                out_H[n] = (int16_t)32767;
            } else if (out_tmp < -4096)
            {
                out_H[n] = (int16_t)-32768;
            } else
            {
                tmp32 = WEBRTC_SPL_MUL((int32_t)out_H[n],
                                       WEBRTC_SPL_RSHIFT_W32(gain32, 4));
                out_H[n] = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32 , 16);
            }
        }
        //

        gain32 += delta;
    }
    // iterate over subframes
    for (k = 1; k < 10; k++)
    {
        delta = WEBRTC_SPL_LSHIFT_W32(gains[k+1] - gains[k], (4 - L2));
        gain32 = WEBRTC_SPL_LSHIFT_W32(gains[k], 4);
        // iterate over samples
        for (n = 0; n < L; n++)
        {
            // For lower band
            tmp32 = WEBRTC_SPL_MUL((int32_t)out[k * L + n],
                                   WEBRTC_SPL_RSHIFT_W32(gain32, 4));
            out[k * L + n] = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32 , 16);
            // For higher band
            if (FS == 32000)
            {
                tmp32 = WEBRTC_SPL_MUL((int32_t)out_H[k * L + n],
                                       WEBRTC_SPL_RSHIFT_W32(gain32, 4));
                out_H[k * L + n] = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32 , 16);
            }
            gain32 += delta;
        }
    }

    return 0;
}

void WebRtcAgc_InitVad(AgcVad_t *state)
{
    int16_t k;

    state->HPstate = 0; // state of high pass filter
    state->logRatio = 0; // log( P(active) / P(inactive) )
    // average input level (Q10)
    state->meanLongTerm = WEBRTC_SPL_LSHIFT_W16(15, 10);

    // variance of input level (Q8)
    state->varianceLongTerm = WEBRTC_SPL_LSHIFT_W32(500, 8);

    state->stdLongTerm = 0; // standard deviation of input level in dB
    // short-term average input level (Q10)
    state->meanShortTerm = WEBRTC_SPL_LSHIFT_W16(15, 10);

    // short-term variance of input level (Q8)
    state->varianceShortTerm = WEBRTC_SPL_LSHIFT_W32(500, 8);

    state->stdShortTerm = 0; // short-term standard deviation of input level in dB
    state->counter = 3; // counts updates
    for (k = 0; k < 8; k++)
    {
        // downsampling filter
        state->downState[k] = 0;
    }
}

int16_t WebRtcAgc_ProcessVad(AgcVad_t *state, // (i) VAD state
                                   const int16_t *in, // (i) Speech signal
                                   int16_t nrSamples) // (i) number of samples
{
    int32_t out, nrg, tmp32, tmp32b;
    uint16_t tmpU16;
    int16_t k, subfr, tmp16;
    int16_t buf1[8];
    int16_t buf2[4];
    int16_t HPstate;
    int16_t zeros, dB;

    // process in 10 sub frames of 1 ms (to save on memory)
    nrg = 0;
    HPstate = state->HPstate;
    for (subfr = 0; subfr < 10; subfr++)
    {
        // downsample to 4 kHz
        if (nrSamples == 160)
        {
            for (k = 0; k < 8; k++)
            {
                tmp32 = (int32_t)in[2 * k] + (int32_t)in[2 * k + 1];
                tmp32 = WEBRTC_SPL_RSHIFT_W32(tmp32, 1);
                buf1[k] = (int16_t)tmp32;
            }
            in += 16;

            WebRtcSpl_DownsampleBy2(buf1, 8, buf2, state->downState);
        } else
        {
            WebRtcSpl_DownsampleBy2(in, 8, buf2, state->downState);
            in += 8;
        }

        // high pass filter and compute energy
        for (k = 0; k < 4; k++)
        {
            out = buf2[k] + HPstate;
            tmp32 = WEBRTC_SPL_MUL(600, out);
            HPstate = (int16_t)(WEBRTC_SPL_RSHIFT_W32(tmp32, 10) - buf2[k]);
            tmp32 = WEBRTC_SPL_MUL(out, out);
            nrg += WEBRTC_SPL_RSHIFT_W32(tmp32, 6);
        }
    }
    state->HPstate = HPstate;

    // find number of leading zeros
    if (!(0xFFFF0000 & nrg))
    {
        zeros = 16;
    } else
    {
        zeros = 0;
    }
    if (!(0xFF000000 & (nrg << zeros)))
    {
        zeros += 8;
    }
    if (!(0xF0000000 & (nrg << zeros)))
    {
        zeros += 4;
    }
    if (!(0xC0000000 & (nrg << zeros)))
    {
        zeros += 2;
    }
    if (!(0x80000000 & (nrg << zeros)))
    {
        zeros += 1;
    }

    // energy level (range {-32..30}) (Q10)
    dB = WEBRTC_SPL_LSHIFT_W16(15 - zeros, 11);

    // Update statistics

    if (state->counter < kAvgDecayTime)
    {
        // decay time = AvgDecTime * 10 ms
        state->counter++;
    }

    // update short-term estimate of mean energy level (Q10)
    tmp32 = (WEBRTC_SPL_MUL_16_16(state->meanShortTerm, 15) + (int32_t)dB);
    state->meanShortTerm = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32, 4);

    // update short-term estimate of variance in energy level (Q8)
    tmp32 = WEBRTC_SPL_RSHIFT_W32(WEBRTC_SPL_MUL_16_16(dB, dB), 12);
    tmp32 += WEBRTC_SPL_MUL(state->varianceShortTerm, 15);
    state->varianceShortTerm = WEBRTC_SPL_RSHIFT_W32(tmp32, 4);

    // update short-term estimate of standard deviation in energy level (Q10)
    tmp32 = WEBRTC_SPL_MUL_16_16(state->meanShortTerm, state->meanShortTerm);
    tmp32 = WEBRTC_SPL_LSHIFT_W32(state->varianceShortTerm, 12) - tmp32;
    state->stdShortTerm = (int16_t)WebRtcSpl_Sqrt(tmp32);

    // update long-term estimate of mean energy level (Q10)
    tmp32 = WEBRTC_SPL_MUL_16_16(state->meanLongTerm, state->counter) + (int32_t)dB;
    state->meanLongTerm = WebRtcSpl_DivW32W16ResW16(
        tmp32, WebRtcSpl_AddSatW16(state->counter, 1));

    // update long-term estimate of variance in energy level (Q8)
    tmp32 = WEBRTC_SPL_RSHIFT_W32(WEBRTC_SPL_MUL_16_16(dB, dB), 12);
    tmp32 += WEBRTC_SPL_MUL(state->varianceLongTerm, state->counter);
    state->varianceLongTerm = WebRtcSpl_DivW32W16(
        tmp32, WebRtcSpl_AddSatW16(state->counter, 1));

    // update long-term estimate of standard deviation in energy level (Q10)
    tmp32 = WEBRTC_SPL_MUL_16_16(state->meanLongTerm, state->meanLongTerm);
    tmp32 = WEBRTC_SPL_LSHIFT_W32(state->varianceLongTerm, 12) - tmp32;
    state->stdLongTerm = (int16_t)WebRtcSpl_Sqrt(tmp32);

    // update voice activity measure (Q10)
    tmp16 = WEBRTC_SPL_LSHIFT_W16(3, 12);
    tmp32 = WEBRTC_SPL_MUL_16_16(tmp16, (dB - state->meanLongTerm));
    tmp32 = WebRtcSpl_DivW32W16(tmp32, state->stdLongTerm);
    tmpU16 = (13 << 12);
    tmp32b = WEBRTC_SPL_MUL_16_U16(state->logRatio, tmpU16);
    tmp32 += WEBRTC_SPL_RSHIFT_W32(tmp32b, 10);

    state->logRatio = (int16_t)WEBRTC_SPL_RSHIFT_W32(tmp32, 6);

    // limit
    if (state->logRatio > 2048)
    {
        state->logRatio = 2048;
    }
    if (state->logRatio < -2048)
    {
        state->logRatio = -2048;
    }

    return state->logRatio; // Q10
}
