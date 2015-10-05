/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_COMMON_DEFS_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_COMMON_DEFS_H_

#include <string.h>

#include "webrtc/common_types.h"
#include "webrtc/engine_configurations.h"
#include "webrtc/modules/audio_coding/main/interface/audio_coding_module_typedefs.h"
#include "webrtc/typedefs.h"

// Checks for enabled codecs, we prevent enabling codecs which are not
// compatible.
#if ((defined WEBRTC_CODEC_ISAC) && (defined WEBRTC_CODEC_ISACFX))
#error iSAC and iSACFX codecs cannot be enabled at the same time
#endif


namespace webrtc {

// 60 ms is the maximum block size we support. An extra 20 ms is considered
// for safety if process() method is not called when it should be, i.e. we
// accept 20 ms of jitter. 80 ms @ 48 kHz (full-band) stereo is 7680 samples.
#define AUDIO_BUFFER_SIZE_W16 7680

// There is one timestamp per each 10 ms of audio
// the audio buffer, at max, may contain 32 blocks of 10ms
// audio if the sampling frequency is 8000 Hz (80 samples per block).
// Therefore, The size of the buffer where we keep timestamps
// is defined as follows
#define TIMESTAMP_BUFFER_SIZE_W32  (AUDIO_BUFFER_SIZE_W16/80)

// The maximum size of a payload, that is 60 ms of PCM-16 @ 32 kHz stereo
#define MAX_PAYLOAD_SIZE_BYTE   7680

// General codec specific defines
const int kIsacWbDefaultRate = 32000;
const int kIsacSwbDefaultRate = 56000;
const int kIsacPacSize480 = 480;
const int kIsacPacSize960 = 960;
const int kIsacPacSize1440 = 1440;

// An encoded bit-stream is labeled by one of the following enumerators.
//
//   kNoEncoding              : There has been no encoding.
//   kActiveNormalEncoded     : Active audio frame coded by the codec.
//   kPassiveNormalEncoded    : Passive audio frame coded by the codec.
//   kPassiveDTXNB            : Passive audio frame coded by narrow-band CN.
//   kPassiveDTXWB            : Passive audio frame coded by wide-band CN.
//   kPassiveDTXSWB           : Passive audio frame coded by super-wide-band CN.
//   kPassiveDTXFB            : Passive audio frame coded by full-band CN.
enum WebRtcACMEncodingType {
  kNoEncoding,
  kActiveNormalEncoded,
  kPassiveNormalEncoded,
  kPassiveDTXNB,
  kPassiveDTXWB,
  kPassiveDTXSWB,
  kPassiveDTXFB
};

// A structure which contains codec parameters. For instance, used when
// initializing encoder and decoder.
//
//   codec_inst: c.f. common_types.h
//   enable_dtx: set true to enable DTX. If codec does not have
//               internal DTX, this will enable VAD.
//   enable_vad: set true to enable VAD.
//   vad_mode: VAD mode, c.f. audio_coding_module_typedefs.h
//             for possible values.
struct WebRtcACMCodecParams {
  CodecInst codec_inst;
  bool enable_dtx;
  bool enable_vad;
  ACMVADMode vad_mode;
};

// TODO(turajs): Remove when ACM1 is removed.
struct WebRtcACMAudioBuff {
  int16_t in_audio[AUDIO_BUFFER_SIZE_W16];
  int16_t in_audio_ix_read;
  int16_t in_audio_ix_write;
  uint32_t in_timestamp[TIMESTAMP_BUFFER_SIZE_W32];
  int16_t in_timestamp_ix_write;
  uint32_t last_timestamp;
  uint32_t last_in_timestamp;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_COMMON_DEFS_H_
