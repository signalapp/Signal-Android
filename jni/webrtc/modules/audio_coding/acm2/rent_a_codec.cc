/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/acm2/rent_a_codec.h"

#include <memory>
#include <utility>

#include "webrtc/base/logging.h"
#include "webrtc/modules/audio_coding/codecs/cng/audio_encoder_cng.h"
#include "webrtc/modules/audio_coding/codecs/g711/audio_encoder_pcm.h"
#ifdef WEBRTC_CODEC_G722
#include "webrtc/modules/audio_coding/codecs/g722/audio_encoder_g722.h"
#endif
#ifdef WEBRTC_CODEC_ILBC
#include "webrtc/modules/audio_coding/codecs/ilbc/audio_encoder_ilbc.h"
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
#include "webrtc/modules/audio_coding/codecs/opus/audio_encoder_opus.h"
#endif
#include "webrtc/modules/audio_coding/codecs/pcm16b/audio_encoder_pcm16b.h"
#ifdef WEBRTC_CODEC_RED
#include "webrtc/modules/audio_coding/codecs/red/audio_encoder_copy_red.h"
#endif
#include "webrtc/modules/audio_coding/acm2/acm_codec_database.h"
#include "webrtc/modules/audio_coding/acm2/acm_common_defs.h"

#if defined(WEBRTC_CODEC_ISACFX) || defined(WEBRTC_CODEC_ISAC)
#include "webrtc/modules/audio_coding/codecs/isac/locked_bandwidth_info.h"
#endif

namespace webrtc {
namespace acm2 {

rtc::Optional<SdpAudioFormat> RentACodec::NetEqDecoderToSdpAudioFormat(
    NetEqDecoder nd) {
  switch (nd) {
    case NetEqDecoder::kDecoderPCMu:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("pcmu", 8000, 1));
    case NetEqDecoder::kDecoderPCMa:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("pcma", 8000, 1));
    case NetEqDecoder::kDecoderPCMu_2ch:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("pcmu", 8000, 2));
    case NetEqDecoder::kDecoderPCMa_2ch:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("pcma", 8000, 2));
    case NetEqDecoder::kDecoderILBC:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("ilbc", 8000, 1));
    case NetEqDecoder::kDecoderISAC:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("isac", 16000, 1));
    case NetEqDecoder::kDecoderISACswb:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("isac", 32000, 1));
    case NetEqDecoder::kDecoderPCM16B:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("l16", 8000, 1));
    case NetEqDecoder::kDecoderPCM16Bwb:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("l16", 16000, 1));
    case NetEqDecoder::kDecoderPCM16Bswb32kHz:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("l16", 32000, 1));
    case NetEqDecoder::kDecoderPCM16Bswb48kHz:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("l16", 48000, 1));
    case NetEqDecoder::kDecoderPCM16B_2ch:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("l16", 8000, 2));
    case NetEqDecoder::kDecoderPCM16Bwb_2ch:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("l16", 16000, 2));
    case NetEqDecoder::kDecoderPCM16Bswb32kHz_2ch:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("l16", 32000, 2));
    case NetEqDecoder::kDecoderPCM16Bswb48kHz_2ch:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("l16", 48000, 2));
    case NetEqDecoder::kDecoderPCM16B_5ch:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("l16", 8000, 5));
    case NetEqDecoder::kDecoderG722:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("g722", 8000, 1));
    case NetEqDecoder::kDecoderG722_2ch:
      return rtc::Optional<SdpAudioFormat>(SdpAudioFormat("g722", 8000, 2));
    case NetEqDecoder::kDecoderOpus:
      return rtc::Optional<SdpAudioFormat>(
          SdpAudioFormat("opus", 48000, 2,
                         std::map<std::string, std::string>{{"stereo", "0"}}));
    case NetEqDecoder::kDecoderOpus_2ch:
      return rtc::Optional<SdpAudioFormat>(
          SdpAudioFormat("opus", 48000, 2,
                         std::map<std::string, std::string>{{"stereo", "1"}}));

    default:
      return rtc::Optional<SdpAudioFormat>();
  }
}

rtc::Optional<RentACodec::CodecId> RentACodec::CodecIdByParams(
    const char* payload_name,
    int sampling_freq_hz,
    size_t channels) {
  return CodecIdFromIndex(
      ACMCodecDB::CodecId(payload_name, sampling_freq_hz, channels));
}

rtc::Optional<CodecInst> RentACodec::CodecInstById(CodecId codec_id) {
  rtc::Optional<int> mi = CodecIndexFromId(codec_id);
  return mi ? rtc::Optional<CodecInst>(Database()[*mi])
            : rtc::Optional<CodecInst>();
}

rtc::Optional<RentACodec::CodecId> RentACodec::CodecIdByInst(
    const CodecInst& codec_inst) {
  return CodecIdFromIndex(ACMCodecDB::CodecNumber(codec_inst));
}

rtc::Optional<CodecInst> RentACodec::CodecInstByParams(const char* payload_name,
                                                       int sampling_freq_hz,
                                                       size_t channels) {
  rtc::Optional<CodecId> codec_id =
      CodecIdByParams(payload_name, sampling_freq_hz, channels);
  if (!codec_id)
    return rtc::Optional<CodecInst>();
  rtc::Optional<CodecInst> ci = CodecInstById(*codec_id);
  RTC_DCHECK(ci);

  // Keep the number of channels from the function call. For most codecs it
  // will be the same value as in default codec settings, but not for all.
  ci->channels = channels;

  return ci;
}

bool RentACodec::IsCodecValid(const CodecInst& codec_inst) {
  return ACMCodecDB::CodecNumber(codec_inst) >= 0;
}

rtc::Optional<bool> RentACodec::IsSupportedNumChannels(CodecId codec_id,
                                                       size_t num_channels) {
  auto i = CodecIndexFromId(codec_id);
  return i ? rtc::Optional<bool>(
                 ACMCodecDB::codec_settings_[*i].channel_support >=
                 num_channels)
           : rtc::Optional<bool>();
}

rtc::ArrayView<const CodecInst> RentACodec::Database() {
  return rtc::ArrayView<const CodecInst>(ACMCodecDB::database_,
                                         NumberOfCodecs());
}

rtc::Optional<NetEqDecoder> RentACodec::NetEqDecoderFromCodecId(
    CodecId codec_id,
    size_t num_channels) {
  rtc::Optional<int> i = CodecIndexFromId(codec_id);
  if (!i)
    return rtc::Optional<NetEqDecoder>();
  const NetEqDecoder ned = ACMCodecDB::neteq_decoders_[*i];
  return rtc::Optional<NetEqDecoder>(
      (ned == NetEqDecoder::kDecoderOpus && num_channels == 2)
          ? NetEqDecoder::kDecoderOpus_2ch
          : ned);
}

RentACodec::RegistrationResult RentACodec::RegisterCngPayloadType(
    std::map<int, int>* pt_map,
    const CodecInst& codec_inst) {
  if (STR_CASE_CMP(codec_inst.plname, "CN") != 0)
    return RegistrationResult::kSkip;
  switch (codec_inst.plfreq) {
    case 8000:
    case 16000:
    case 32000:
    case 48000:
      (*pt_map)[codec_inst.plfreq] = codec_inst.pltype;
      return RegistrationResult::kOk;
    default:
      return RegistrationResult::kBadFreq;
  }
}

RentACodec::RegistrationResult RentACodec::RegisterRedPayloadType(
    std::map<int, int>* pt_map,
    const CodecInst& codec_inst) {
  if (STR_CASE_CMP(codec_inst.plname, "RED") != 0)
    return RegistrationResult::kSkip;
  switch (codec_inst.plfreq) {
    case 8000:
      (*pt_map)[codec_inst.plfreq] = codec_inst.pltype;
      return RegistrationResult::kOk;
    default:
      return RegistrationResult::kBadFreq;
  }
}

namespace {

// Returns a new speech encoder, or null on error.
// TODO(kwiberg): Don't handle errors here (bug 5033)
std::unique_ptr<AudioEncoder> CreateEncoder(
    const CodecInst& speech_inst,
    const rtc::scoped_refptr<LockedIsacBandwidthInfo>& bwinfo) {
#if defined(WEBRTC_CODEC_ISACFX)
  if (STR_CASE_CMP(speech_inst.plname, "isac") == 0)
    return std::unique_ptr<AudioEncoder>(
        new AudioEncoderIsacFix(speech_inst, bwinfo));
#endif
#if defined(WEBRTC_CODEC_ISAC)
  if (STR_CASE_CMP(speech_inst.plname, "isac") == 0)
    return std::unique_ptr<AudioEncoder>(
        new AudioEncoderIsac(speech_inst, bwinfo));
#endif
#ifdef WEBRTC_CODEC_OPUS
  if (STR_CASE_CMP(speech_inst.plname, "opus") == 0)
    return std::unique_ptr<AudioEncoder>(new AudioEncoderOpus(speech_inst));
#endif
  if (STR_CASE_CMP(speech_inst.plname, "pcmu") == 0)
    return std::unique_ptr<AudioEncoder>(new AudioEncoderPcmU(speech_inst));
  if (STR_CASE_CMP(speech_inst.plname, "pcma") == 0)
    return std::unique_ptr<AudioEncoder>(new AudioEncoderPcmA(speech_inst));
  if (STR_CASE_CMP(speech_inst.plname, "l16") == 0)
    return std::unique_ptr<AudioEncoder>(new AudioEncoderPcm16B(speech_inst));
#ifdef WEBRTC_CODEC_ILBC
  if (STR_CASE_CMP(speech_inst.plname, "ilbc") == 0)
    return std::unique_ptr<AudioEncoder>(new AudioEncoderIlbc(speech_inst));
#endif
#ifdef WEBRTC_CODEC_G722
  if (STR_CASE_CMP(speech_inst.plname, "g722") == 0)
    return std::unique_ptr<AudioEncoder>(new AudioEncoderG722(speech_inst));
#endif
  LOG_F(LS_ERROR) << "Could not create encoder of type " << speech_inst.plname;
  return std::unique_ptr<AudioEncoder>();
}

std::unique_ptr<AudioEncoder> CreateRedEncoder(
    std::unique_ptr<AudioEncoder> encoder,
    int red_payload_type) {
#ifdef WEBRTC_CODEC_RED
  AudioEncoderCopyRed::Config config;
  config.payload_type = red_payload_type;
  config.speech_encoder = std::move(encoder);
  return std::unique_ptr<AudioEncoder>(
      new AudioEncoderCopyRed(std::move(config)));
#else
  return std::unique_ptr<AudioEncoder>();
#endif
}

std::unique_ptr<AudioEncoder> CreateCngEncoder(
    std::unique_ptr<AudioEncoder> encoder,
    int payload_type,
    ACMVADMode vad_mode) {
  AudioEncoderCng::Config config;
  config.num_channels = encoder->NumChannels();
  config.payload_type = payload_type;
  config.speech_encoder = std::move(encoder);
  switch (vad_mode) {
    case VADNormal:
      config.vad_mode = Vad::kVadNormal;
      break;
    case VADLowBitrate:
      config.vad_mode = Vad::kVadLowBitrate;
      break;
    case VADAggr:
      config.vad_mode = Vad::kVadAggressive;
      break;
    case VADVeryAggr:
      config.vad_mode = Vad::kVadVeryAggressive;
      break;
    default:
      FATAL();
  }
  return std::unique_ptr<AudioEncoder>(new AudioEncoderCng(std::move(config)));
}

std::unique_ptr<AudioDecoder> CreateIsacDecoder(
    int sample_rate_hz,
    const rtc::scoped_refptr<LockedIsacBandwidthInfo>& bwinfo) {
#if defined(WEBRTC_CODEC_ISACFX)
  return std::unique_ptr<AudioDecoder>(
      new AudioDecoderIsacFix(sample_rate_hz, bwinfo));
#elif defined(WEBRTC_CODEC_ISAC)
  return std::unique_ptr<AudioDecoder>(
      new AudioDecoderIsac(sample_rate_hz, bwinfo));
#else
  FATAL() << "iSAC is not supported.";
  return std::unique_ptr<AudioDecoder>();
#endif
}

}  // namespace

RentACodec::RentACodec() {
#if defined(WEBRTC_CODEC_ISACFX) || defined(WEBRTC_CODEC_ISAC)
  isac_bandwidth_info_ = new LockedIsacBandwidthInfo;
#endif
}
RentACodec::~RentACodec() = default;

std::unique_ptr<AudioEncoder> RentACodec::RentEncoder(
    const CodecInst& codec_inst) {
  return CreateEncoder(codec_inst, isac_bandwidth_info_);
}

RentACodec::StackParameters::StackParameters() {
  // Register the default payload types for RED and CNG.
  for (const CodecInst& ci : RentACodec::Database()) {
    RentACodec::RegisterCngPayloadType(&cng_payload_types, ci);
    RentACodec::RegisterRedPayloadType(&red_payload_types, ci);
  }
}

RentACodec::StackParameters::~StackParameters() = default;

std::unique_ptr<AudioEncoder> RentACodec::RentEncoderStack(
    StackParameters* param) {
  if (!param->speech_encoder)
    return nullptr;

  if (param->use_codec_fec) {
    // Switch FEC on. On failure, remember that FEC is off.
    if (!param->speech_encoder->SetFec(true))
      param->use_codec_fec = false;
  } else {
    // Switch FEC off. This shouldn't fail.
    const bool success = param->speech_encoder->SetFec(false);
    RTC_DCHECK(success);
  }

  auto pt = [&param](const std::map<int, int>& m) {
    auto it = m.find(param->speech_encoder->SampleRateHz());
    return it == m.end() ? rtc::Optional<int>()
                         : rtc::Optional<int>(it->second);
  };
  auto cng_pt = pt(param->cng_payload_types);
  param->use_cng =
      param->use_cng && cng_pt && param->speech_encoder->NumChannels() == 1;
  auto red_pt = pt(param->red_payload_types);
  param->use_red = param->use_red && red_pt;

  if (param->use_cng || param->use_red) {
    // The RED and CNG encoders need to be in sync with the speech encoder, so
    // reset the latter to ensure its buffer is empty.
    param->speech_encoder->Reset();
  }
  std::unique_ptr<AudioEncoder> encoder_stack =
      std::move(param->speech_encoder);
  if (param->use_red) {
    encoder_stack = CreateRedEncoder(std::move(encoder_stack), *red_pt);
  }
  if (param->use_cng) {
    encoder_stack =
        CreateCngEncoder(std::move(encoder_stack), *cng_pt, param->vad_mode);
  }
  return encoder_stack;
}

std::unique_ptr<AudioDecoder> RentACodec::RentIsacDecoder(int sample_rate_hz) {
  return CreateIsacDecoder(sample_rate_hz, isac_bandwidth_info_);
}

}  // namespace acm2
}  // namespace webrtc
