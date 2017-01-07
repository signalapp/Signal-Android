/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_ACM2_RENT_A_CODEC_H_
#define WEBRTC_MODULES_AUDIO_CODING_ACM2_RENT_A_CODEC_H_

#include <stddef.h>
#include <map>
#include <memory>

#include "webrtc/base/array_view.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/optional.h"
#include "webrtc/base/scoped_ref_ptr.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"
#include "webrtc/modules/audio_coding/codecs/audio_format.h"
#include "webrtc/modules/audio_coding/codecs/audio_encoder.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module_typedefs.h"
#include "webrtc/typedefs.h"

#include "webrtc/modules/audio_coding/codecs/isac/locked_bandwidth_info.h"

namespace webrtc {

struct CodecInst;
//class LockedIsacBandwidthInfo;

namespace acm2 {

class RentACodec {
 public:
  enum class CodecId {
#if defined(WEBRTC_CODEC_ISAC) || defined(WEBRTC_CODEC_ISACFX)
    kISAC,
#endif
#ifdef WEBRTC_CODEC_ISAC
    kISACSWB,
#endif
    // Mono
    kPCM16B,
    kPCM16Bwb,
    kPCM16Bswb32kHz,
    // Stereo
    kPCM16B_2ch,
    kPCM16Bwb_2ch,
    kPCM16Bswb32kHz_2ch,
    // Mono
    kPCMU,
    kPCMA,
    // Stereo
    kPCMU_2ch,
    kPCMA_2ch,
#ifdef WEBRTC_CODEC_ILBC
    kILBC,
#endif
#ifdef WEBRTC_CODEC_G722
    kG722,      // Mono
    kG722_2ch,  // Stereo
#endif
#ifdef WEBRTC_CODEC_OPUS
    kOpus,  // Mono and stereo
#endif
    kCNNB,
    kCNWB,
    kCNSWB,
#ifdef ENABLE_48000_HZ
    kCNFB,
#endif
    kAVT,
#ifdef WEBRTC_CODEC_RED
    kRED,
#endif
    kNumCodecs,  // Implementation detail. Don't use.

// Set unsupported codecs to -1.
#if !defined(WEBRTC_CODEC_ISAC) && !defined(WEBRTC_CODEC_ISACFX)
    kISAC = -1,
#endif
#ifndef WEBRTC_CODEC_ISAC
    kISACSWB = -1,
#endif
    // 48 kHz not supported, always set to -1.
    kPCM16Bswb48kHz = -1,
#ifndef WEBRTC_CODEC_ILBC
    kILBC = -1,
#endif
#ifndef WEBRTC_CODEC_G722
    kG722 = -1,      // Mono
    kG722_2ch = -1,  // Stereo
#endif
#ifndef WEBRTC_CODEC_OPUS
    kOpus = -1,  // Mono and stereo
#endif
#ifndef WEBRTC_CODEC_RED
    kRED = -1,
#endif
#ifndef ENABLE_48000_HZ
    kCNFB = -1,
#endif

    kNone = -1
  };

  enum class NetEqDecoder {
    kDecoderPCMu,
    kDecoderPCMa,
    kDecoderPCMu_2ch,
    kDecoderPCMa_2ch,
    kDecoderILBC,
    kDecoderISAC,
    kDecoderISACswb,
    kDecoderPCM16B,
    kDecoderPCM16Bwb,
    kDecoderPCM16Bswb32kHz,
    kDecoderPCM16Bswb48kHz,
    kDecoderPCM16B_2ch,
    kDecoderPCM16Bwb_2ch,
    kDecoderPCM16Bswb32kHz_2ch,
    kDecoderPCM16Bswb48kHz_2ch,
    kDecoderPCM16B_5ch,
    kDecoderG722,
    kDecoderG722_2ch,
    kDecoderRED,
    kDecoderAVT,
    kDecoderCNGnb,
    kDecoderCNGwb,
    kDecoderCNGswb32kHz,
    kDecoderCNGswb48kHz,
    kDecoderArbitrary,
    kDecoderOpus,
    kDecoderOpus_2ch,
  };

  static rtc::Optional<SdpAudioFormat> NetEqDecoderToSdpAudioFormat(
      NetEqDecoder nd);

  static inline size_t NumberOfCodecs() {
    return static_cast<size_t>(CodecId::kNumCodecs);
  }

  static inline rtc::Optional<int> CodecIndexFromId(CodecId codec_id) {
    const int i = static_cast<int>(codec_id);
    return i >= 0 && i < static_cast<int>(NumberOfCodecs())
               ? rtc::Optional<int>(i)
               : rtc::Optional<int>();
  }

  static inline rtc::Optional<CodecId> CodecIdFromIndex(int codec_index) {
    return static_cast<size_t>(codec_index) < NumberOfCodecs()
               ? rtc::Optional<RentACodec::CodecId>(
                     static_cast<RentACodec::CodecId>(codec_index))
               : rtc::Optional<RentACodec::CodecId>();
  }

  static rtc::Optional<CodecId> CodecIdByParams(const char* payload_name,
                                                int sampling_freq_hz,
                                                size_t channels);
  static rtc::Optional<CodecInst> CodecInstById(CodecId codec_id);
  static rtc::Optional<CodecId> CodecIdByInst(const CodecInst& codec_inst);
  static rtc::Optional<CodecInst> CodecInstByParams(const char* payload_name,
                                                    int sampling_freq_hz,
                                                    size_t channels);
  static bool IsCodecValid(const CodecInst& codec_inst);

  static inline bool IsPayloadTypeValid(int payload_type) {
    return payload_type >= 0 && payload_type <= 127;
  }

  static rtc::ArrayView<const CodecInst> Database();

  static rtc::Optional<bool> IsSupportedNumChannels(CodecId codec_id,
                                                    size_t num_channels);

  static rtc::Optional<NetEqDecoder> NetEqDecoderFromCodecId(
      CodecId codec_id,
      size_t num_channels);

  // Parse codec_inst and extract payload types. If the given CodecInst was for
  // the wrong sort of codec, return kSkip; otherwise, if the rate was illegal,
  // return kBadFreq; otherwise, update the given RTP timestamp rate (Hz) ->
  // payload type map and return kOk.
  enum class RegistrationResult { kOk, kSkip, kBadFreq };
  static RegistrationResult RegisterCngPayloadType(std::map<int, int>* pt_map,
                                                   const CodecInst& codec_inst);
  static RegistrationResult RegisterRedPayloadType(std::map<int, int>* pt_map,
                                                   const CodecInst& codec_inst);

  RentACodec();
  ~RentACodec();

  // Creates and returns an audio encoder built to the given specification.
  // Returns null in case of error.
  std::unique_ptr<AudioEncoder> RentEncoder(const CodecInst& codec_inst);

  struct StackParameters {
    StackParameters();
    ~StackParameters();

    std::unique_ptr<AudioEncoder> speech_encoder;

    bool use_codec_fec = false;
    bool use_red = false;
    bool use_cng = false;
    ACMVADMode vad_mode = VADNormal;

    // Maps from RTP timestamp rate (in Hz) to payload type.
    std::map<int, int> cng_payload_types;
    std::map<int, int> red_payload_types;
  };

  // Creates and returns an audio encoder stack constructed to the given
  // specification. If the specification isn't compatible with the encoder, it
  // will be changed to match (things will be switched off). The speech encoder
  // will be stolen. If the specification isn't complete, returns nullptr.
  std::unique_ptr<AudioEncoder> RentEncoderStack(StackParameters* param);

  // Creates and returns an iSAC decoder.
  std::unique_ptr<AudioDecoder> RentIsacDecoder(int sample_rate_hz);

 private:
  std::unique_ptr<AudioEncoder> speech_encoder_;
  std::unique_ptr<AudioEncoder> cng_encoder_;
  std::unique_ptr<AudioEncoder> red_encoder_;
  rtc::scoped_refptr<LockedIsacBandwidthInfo> isac_bandwidth_info_;

  RTC_DISALLOW_COPY_AND_ASSIGN(RentACodec);
};

}  // namespace acm2
}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_ACM2_RENT_A_CODEC_H_
