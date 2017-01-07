/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/* PayloadTypes.h */
/* Used by NetEqRTPplay application */

/* RTP defined codepoints */
#define NETEQ_CODEC_PCMU_PT				0
#define NETEQ_CODEC_GSMFR_PT			3
#define NETEQ_CODEC_G723_PT				4
#define NETEQ_CODEC_DVI4_PT				125 // 8 kHz version
//#define NETEQ_CODEC_DVI4_16_PT			6  // 16 kHz version
#define NETEQ_CODEC_PCMA_PT				8
#define NETEQ_CODEC_G722_PT				9
#define NETEQ_CODEC_CN_PT				13
//#define NETEQ_CODEC_G728_PT				15
//#define NETEQ_CODEC_DVI4_11_PT			16  // 11.025 kHz version
//#define NETEQ_CODEC_DVI4_22_PT			17  // 22.050 kHz version
#define NETEQ_CODEC_G729_PT				18

/* Dynamic RTP codepoints as defined in VoiceEngine (file VEAPI.cpp) */
#define NETEQ_CODEC_IPCMWB_PT			97
#define NETEQ_CODEC_SPEEX8_PT			98
#define NETEQ_CODEC_SPEEX16_PT			99
#define NETEQ_CODEC_EG711U_PT			100
#define NETEQ_CODEC_EG711A_PT			101
#define NETEQ_CODEC_ILBC_PT				102
#define NETEQ_CODEC_ISAC_PT				103
#define NETEQ_CODEC_ISACLC_PT			119
#define NETEQ_CODEC_ISACSWB_PT			104
#define NETEQ_CODEC_AVT_PT				106
#define NETEQ_CODEC_G722_1_16_PT		108
#define NETEQ_CODEC_G722_1_24_PT		109
#define NETEQ_CODEC_G722_1_32_PT		110
#define NETEQ_CODEC_OPUS_PT             111
#define NETEQ_CODEC_AMR_PT				112
#define NETEQ_CODEC_GSMEFR_PT			113
//#define NETEQ_CODEC_ILBCRCU_PT			114
#define NETEQ_CODEC_G726_16_PT			115
#define NETEQ_CODEC_G726_24_PT			116
#define NETEQ_CODEC_G726_32_PT			121
#define NETEQ_CODEC_RED_PT				117
#define NETEQ_CODEC_G726_40_PT			118
//#define NETEQ_CODEC_ENERGY_PT			120
#define NETEQ_CODEC_CN_WB_PT			105
#define NETEQ_CODEC_CN_SWB_PT           126
#define NETEQ_CODEC_G729_1_PT			107
#define NETEQ_CODEC_G729D_PT			123
#define NETEQ_CODEC_MELPE_PT			124

/* Extra dynamic codepoints */
#define NETEQ_CODEC_AMRWB_PT			120
#define NETEQ_CODEC_PCM16B_PT			93
#define NETEQ_CODEC_PCM16B_WB_PT		94
#define NETEQ_CODEC_PCM16B_SWB32KHZ_PT	95
#define NETEQ_CODEC_PCM16B_SWB48KHZ_PT	96
#define NETEQ_CODEC_MPEG4AAC_PT			122


/* Not default in VoiceEngine */
#define NETEQ_CODEC_G722_1C_24_PT		84
#define NETEQ_CODEC_G722_1C_32_PT		85
#define NETEQ_CODEC_G722_1C_48_PT		86

#define NETEQ_CODEC_SILK_8_PT			80
#define NETEQ_CODEC_SILK_12_PT			81
#define NETEQ_CODEC_SILK_16_PT			82
#define NETEQ_CODEC_SILK_24_PT			83

