/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/audio_decoder_impl.h"

#include <assert.h>

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/codecs/g711/audio_decoder_pcm.h"
#ifdef WEBRTC_CODEC_G722
#include "webrtc/modules/audio_coding/codecs/g722/audio_decoder_g722.h"
#endif
#ifdef WEBRTC_CODEC_ILBC
#include "webrtc/modules/audio_coding/codecs/ilbc/audio_decoder_ilbc.h"
#endif
#ifdef WEBRTC_CODEC_ISACFX
#include "webrtc/modules/audio_coding/codecs/isac/fix/include/audio_decoder_isacfix.h"
#include "webrtc/modules/audio_coding/codecs/isac/fix/include/audio_encoder_isacfix.h"
#endif
#ifdef WEBRTC_CODEC_ISAC
#include "webrtc/modules/audio_coding/codecs/isac/main/include/audio_decoder_isac.h"
#include "webrtc/modules/audio_coding/codecs/isac/main/include/audio_encoder_isac.h"
#endif
#ifdef WEBRTC_CODEC_OPUS
#include "webrtc/modules/audio_coding/codecs/opus/audio_decoder_opus.h"
#endif
#include "webrtc/modules/audio_coding/codecs/pcm16b/audio_decoder_pcm16b.h"

namespace webrtc {

bool CodecSupported(NetEqDecoder codec_type) {
  switch (codec_type) {
    case NetEqDecoder::kDecoderPCMu:
    case NetEqDecoder::kDecoderPCMa:
    case NetEqDecoder::kDecoderPCMu_2ch:
    case NetEqDecoder::kDecoderPCMa_2ch:
#ifdef WEBRTC_CODEC_ILBC
    case NetEqDecoder::kDecoderILBC:
#endif
#if defined(WEBRTC_CODEC_ISACFX) || defined(WEBRTC_CODEC_ISAC)
    case NetEqDecoder::kDecoderISAC:
#endif
#ifdef WEBRTC_CODEC_ISAC
    case NetEqDecoder::kDecoderISACswb:
#endif
    case NetEqDecoder::kDecoderPCM16B:
    case NetEqDecoder::kDecoderPCM16Bwb:
    case NetEqDecoder::kDecoderPCM16Bswb32kHz:
    case NetEqDecoder::kDecoderPCM16Bswb48kHz:
    case NetEqDecoder::kDecoderPCM16B_2ch:
    case NetEqDecoder::kDecoderPCM16Bwb_2ch:
    case NetEqDecoder::kDecoderPCM16Bswb32kHz_2ch:
    case NetEqDecoder::kDecoderPCM16Bswb48kHz_2ch:
    case NetEqDecoder::kDecoderPCM16B_5ch:
#ifdef WEBRTC_CODEC_G722
    case NetEqDecoder::kDecoderG722:
    case NetEqDecoder::kDecoderG722_2ch:
#endif
#ifdef WEBRTC_CODEC_OPUS
    case NetEqDecoder::kDecoderOpus:
    case NetEqDecoder::kDecoderOpus_2ch:
#endif
    case NetEqDecoder::kDecoderRED:
    case NetEqDecoder::kDecoderAVT:
    case NetEqDecoder::kDecoderCNGnb:
    case NetEqDecoder::kDecoderCNGwb:
    case NetEqDecoder::kDecoderCNGswb32kHz:
    case NetEqDecoder::kDecoderCNGswb48kHz:
    case NetEqDecoder::kDecoderArbitrary: {
      return true;
    }
    default: {
      return false;
    }
  }
}

}  // namespace webrtc
