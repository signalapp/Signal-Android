/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/builtin_audio_decoder_factory.h"

#include <vector>

#include "webrtc/base/checks.h"
#include "webrtc/base/optional.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/codecs/cng/webrtc_cng.h"
#include "webrtc/modules/audio_coding/codecs/g711/audio_decoder_pcm.h"
#ifdef WEBRTC_CODEC_G722
#include "webrtc/modules/audio_coding/codecs/g722/audio_decoder_g722.h"
#endif
#ifdef WEBRTC_CODEC_ILBC
#include "webrtc/modules/audio_coding/codecs/ilbc/audio_decoder_ilbc.h"
#endif
#ifdef WEBRTC_CODEC_ISACFX
#include "webrtc/modules/audio_coding/codecs/isac/fix/include/audio_decoder_isacfix.h"
#endif
#ifdef WEBRTC_CODEC_ISAC
#include "webrtc/modules/audio_coding/codecs/isac/main/include/audio_decoder_isac.h"
#endif
#ifdef WEBRTC_CODEC_OPUS
#include "webrtc/modules/audio_coding/codecs/opus/audio_decoder_opus.h"
#endif
#include "webrtc/modules/audio_coding/codecs/pcm16b/audio_decoder_pcm16b.h"

namespace webrtc {

namespace {

struct NamedDecoderConstructor {
  const char* name;
  std::unique_ptr<AudioDecoder> (*constructor)(const SdpAudioFormat&);
};

std::unique_ptr<AudioDecoder> Unique(AudioDecoder* d) {
  return std::unique_ptr<AudioDecoder>(d);
}

// TODO(kwiberg): These factory functions should probably be moved to each
// decoder.
NamedDecoderConstructor decoder_constructors[] = {
    {"pcmu",
     [](const SdpAudioFormat& format) {
       return format.clockrate_hz == 8000 && format.num_channels >= 1
                  ? Unique(new AudioDecoderPcmU(format.num_channels))
                  : nullptr;
     }},
    {"pcma",
     [](const SdpAudioFormat& format) {
       return format.clockrate_hz == 8000 && format.num_channels >= 1
                  ? Unique(new AudioDecoderPcmA(format.num_channels))
                  : nullptr;
     }},
#ifdef WEBRTC_CODEC_ILBC
    {"ilbc",
     [](const SdpAudioFormat& format) {
       return format.clockrate_hz == 8000 && format.num_channels == 1
                  ? Unique(new AudioDecoderIlbc)
                  : nullptr;
     }},
#endif
#if defined(WEBRTC_CODEC_ISACFX)
    {"isac",
     [](const SdpAudioFormat& format) {
       return format.clockrate_hz == 16000 && format.num_channels == 1
                  ? Unique(new AudioDecoderIsacFix(format.clockrate_hz))
                  : nullptr;
     }},
#elif defined(WEBRTC_CODEC_ISAC)
    {"isac",
     [](const SdpAudioFormat& format) {
       return (format.clockrate_hz == 16000 || format.clockrate_hz == 32000) &&
                      format.num_channels == 1
                  ? Unique(new AudioDecoderIsac(format.clockrate_hz))
                  : nullptr;
     }},
#endif
    {"l16",
     [](const SdpAudioFormat& format) {
       return format.num_channels >= 1
                  ? Unique(new AudioDecoderPcm16B(format.clockrate_hz,
                                                  format.num_channels))
                  : nullptr;
     }},
#ifdef WEBRTC_CODEC_G722
    {"g722",
     [](const SdpAudioFormat& format) {
       if (format.clockrate_hz == 8000) {
         if (format.num_channels == 1)
           return Unique(new AudioDecoderG722);
         if (format.num_channels == 2)
           return Unique(new AudioDecoderG722Stereo);
       }
       return Unique(nullptr);
     }},
#endif
#ifdef WEBRTC_CODEC_OPUS
    {"opus",
     [](const SdpAudioFormat& format) {
       rtc::Optional<int> num_channels = [&] {
         auto stereo = format.parameters.find("stereo");
         if (stereo != format.parameters.end()) {
           if (stereo->second == "0") {
             return rtc::Optional<int>(1);
           } else if (stereo->second == "1") {
             return rtc::Optional<int>(2);
           }
         }
         return rtc::Optional<int>();
       }();
       return format.clockrate_hz == 48000 && format.num_channels == 2 &&
                      num_channels
                  ? Unique(new AudioDecoderOpus(*num_channels))
                  : nullptr;
     }},
#endif
};

class BuiltinAudioDecoderFactory : public AudioDecoderFactory {
 public:
  std::vector<SdpAudioFormat> GetSupportedFormats() override {
    FATAL() << "Not implemented yet!";
  }

  std::unique_ptr<AudioDecoder> MakeAudioDecoder(
      const SdpAudioFormat& format) override {
    for (const auto& dc : decoder_constructors) {
      if (STR_CASE_CMP(format.name.c_str(), dc.name) == 0) {
        std::unique_ptr<AudioDecoder> dec = dc.constructor(format);
        if (dec) {
          const int expected_sample_rate_hz =
              STR_CASE_CMP(format.name.c_str(), "g722") == 0
                  ? 2 * format.clockrate_hz
                  : format.clockrate_hz;
          RTC_CHECK_EQ(expected_sample_rate_hz, dec->SampleRateHz());
        }
        return dec;
      }
    }
    return nullptr;
  }
};

}  // namespace

rtc::scoped_refptr<AudioDecoderFactory> CreateBuiltinAudioDecoderFactory() {
  return rtc::scoped_refptr<AudioDecoderFactory>(
      new rtc::RefCountedObject<BuiltinAudioDecoderFactory>);
}

}  // namespace webrtc
