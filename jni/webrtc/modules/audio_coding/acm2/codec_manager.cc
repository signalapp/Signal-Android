/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/acm2/codec_manager.h"

#include "webrtc/base/checks.h"
#include "webrtc/base/format_macros.h"
#include "webrtc/engine_configurations.h"
#include "webrtc/modules/audio_coding/acm2/rent_a_codec.h"
#include "webrtc/system_wrappers/include/trace.h"

namespace webrtc {
namespace acm2 {

namespace {

// Check if the given codec is a valid to be registered as send codec.
int IsValidSendCodec(const CodecInst& send_codec) {
  int dummy_id = 0;
  if ((send_codec.channels != 1) && (send_codec.channels != 2)) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, dummy_id,
                 "Wrong number of channels (%" PRIuS ", only mono and stereo "
                 "are supported)",
                 send_codec.channels);
    return -1;
  }

  auto maybe_codec_id = RentACodec::CodecIdByInst(send_codec);
  if (!maybe_codec_id) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, dummy_id,
                 "Invalid codec setting for the send codec.");
    return -1;
  }

  // Telephone-event cannot be a send codec.
  if (!STR_CASE_CMP(send_codec.plname, "telephone-event")) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, dummy_id,
                 "telephone-event cannot be a send codec");
    return -1;
  }

  if (!RentACodec::IsSupportedNumChannels(*maybe_codec_id, send_codec.channels)
           .value_or(false)) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, dummy_id,
                 "%" PRIuS " number of channels not supportedn for %s.",
                 send_codec.channels, send_codec.plname);
    return -1;
  }
  return RentACodec::CodecIndexFromId(*maybe_codec_id).value_or(-1);
}

bool IsOpus(const CodecInst& codec) {
  return
#ifdef WEBRTC_CODEC_OPUS
      !STR_CASE_CMP(codec.plname, "opus") ||
#endif
      false;
}

}  // namespace

CodecManager::CodecManager() {
  thread_checker_.DetachFromThread();
}

CodecManager::~CodecManager() = default;

bool CodecManager::RegisterEncoder(const CodecInst& send_codec) {
  RTC_DCHECK(thread_checker_.CalledOnValidThread());
  int codec_id = IsValidSendCodec(send_codec);

  // Check for reported errors from function IsValidSendCodec().
  if (codec_id < 0) {
    return false;
  }

  int dummy_id = 0;
  switch (RentACodec::RegisterRedPayloadType(
      &codec_stack_params_.red_payload_types, send_codec)) {
    case RentACodec::RegistrationResult::kOk:
      return true;
    case RentACodec::RegistrationResult::kBadFreq:
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, dummy_id,
                   "RegisterSendCodec() failed, invalid frequency for RED"
                   " registration");
      return false;
    case RentACodec::RegistrationResult::kSkip:
      break;
  }
  switch (RentACodec::RegisterCngPayloadType(
      &codec_stack_params_.cng_payload_types, send_codec)) {
    case RentACodec::RegistrationResult::kOk:
      return true;
    case RentACodec::RegistrationResult::kBadFreq:
      WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, dummy_id,
                   "RegisterSendCodec() failed, invalid frequency for CNG"
                   " registration");
      return false;
    case RentACodec::RegistrationResult::kSkip:
      break;
  }

  if (IsOpus(send_codec)) {
    // VAD/DTX not supported.
    codec_stack_params_.use_cng = false;
  }

  send_codec_inst_ = rtc::Optional<CodecInst>(send_codec);
  recreate_encoder_ = true;  // Caller must recreate it.
  return true;
}

CodecInst CodecManager::ForgeCodecInst(
    const AudioEncoder* external_speech_encoder) {
  CodecInst ci;
  ci.channels = external_speech_encoder->NumChannels();
  ci.plfreq = external_speech_encoder->SampleRateHz();
  ci.pacsize = rtc::CheckedDivExact(
      static_cast<int>(external_speech_encoder->Max10MsFramesInAPacket() *
                       ci.plfreq),
      100);
  ci.pltype = -1;  // Not valid.
  ci.rate = -1;    // Not valid.
  static const char kName[] = "external";
  memcpy(ci.plname, kName, sizeof(kName));
  return ci;
}

bool CodecManager::SetCopyRed(bool enable) {
  if (enable && codec_stack_params_.use_codec_fec) {
    WEBRTC_TRACE(webrtc::kTraceWarning, webrtc::kTraceAudioCoding, 0,
                 "Codec internal FEC and RED cannot be co-enabled.");
    return false;
  }
  if (enable && send_codec_inst_ &&
      codec_stack_params_.red_payload_types.count(send_codec_inst_->plfreq) <
          1) {
    WEBRTC_TRACE(webrtc::kTraceWarning, webrtc::kTraceAudioCoding, 0,
                 "Cannot enable RED at %i Hz.", send_codec_inst_->plfreq);
    return false;
  }
  codec_stack_params_.use_red = enable;
  return true;
}

bool CodecManager::SetVAD(bool enable, ACMVADMode mode) {
  // Sanity check of the mode.
  RTC_DCHECK(mode == VADNormal || mode == VADLowBitrate || mode == VADAggr ||
             mode == VADVeryAggr);

  // Check that the send codec is mono. We don't support VAD/DTX for stereo
  // sending.
  const bool stereo_send =
      codec_stack_params_.speech_encoder
          ? (codec_stack_params_.speech_encoder->NumChannels() != 1)
          : false;
  if (enable && stereo_send) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, 0,
                 "VAD/DTX not supported for stereo sending");
    return false;
  }

  // TODO(kwiberg): This doesn't protect Opus when injected as an external
  // encoder.
  if (send_codec_inst_ && IsOpus(*send_codec_inst_)) {
    // VAD/DTX not supported, but don't fail.
    enable = false;
  }

  codec_stack_params_.use_cng = enable;
  codec_stack_params_.vad_mode = mode;
  return true;
}

bool CodecManager::SetCodecFEC(bool enable_codec_fec) {
  if (enable_codec_fec && codec_stack_params_.use_red) {
    WEBRTC_TRACE(webrtc::kTraceWarning, webrtc::kTraceAudioCoding, 0,
                 "Codec internal FEC and RED cannot be co-enabled.");
    return false;
  }

  codec_stack_params_.use_codec_fec = enable_codec_fec;
  return true;
}

bool CodecManager::MakeEncoder(RentACodec* rac, AudioCodingModule* acm) {
  RTC_DCHECK(rac);
  RTC_DCHECK(acm);

  if (!recreate_encoder_) {
    bool error = false;
    // Try to re-use the speech encoder we've given to the ACM.
    acm->ModifyEncoder([&](std::unique_ptr<AudioEncoder>* encoder) {
      if (!*encoder) {
        // There is no existing encoder.
        recreate_encoder_ = true;
        return;
      }

      // Extract the speech encoder from the ACM.
      std::unique_ptr<AudioEncoder> enc = std::move(*encoder);
      while (true) {
        auto sub_enc = enc->ReclaimContainedEncoders();
        if (sub_enc.empty()) {
          break;
        }
        RTC_CHECK_EQ(1u, sub_enc.size());

        // Replace enc with its sub encoder. We need to put the sub encoder in
        // a temporary first, since otherwise the old value of enc would be
        // destroyed before the new value got assigned, which would be bad
        // since the new value is a part of the old value.
        auto tmp_enc = std::move(sub_enc[0]);
        enc = std::move(tmp_enc);
      }

      // Wrap it in a new encoder stack and put it back.
      codec_stack_params_.speech_encoder = std::move(enc);
      *encoder = rac->RentEncoderStack(&codec_stack_params_);
      if (!*encoder) {
        error = true;
      }
    });
    if (error) {
      return false;
    }
    if (!recreate_encoder_) {
      return true;
    }
  }

  if (!send_codec_inst_) {
    // We don't have the information we need to create a new speech encoder.
    // (This is not an error.)
    return true;
  }

  codec_stack_params_.speech_encoder = rac->RentEncoder(*send_codec_inst_);
  auto stack = rac->RentEncoderStack(&codec_stack_params_);
  if (!stack) {
    return false;
  }
  acm->SetEncoder(std::move(stack));
  recreate_encoder_ = false;
  return true;
}

}  // namespace acm2
}  // namespace webrtc
