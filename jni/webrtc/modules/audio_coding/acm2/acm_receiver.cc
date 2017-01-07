/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/acm2/acm_receiver.h"

#include <stdlib.h>  // malloc

#include <algorithm>  // sort
#include <vector>

#include "webrtc/base/checks.h"
#include "webrtc/base/format_macros.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/safe_conversions.h"
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"
#include "webrtc/modules/audio_coding/acm2/acm_resampler.h"
#include "webrtc/modules/audio_coding/acm2/call_statistics.h"
#include "webrtc/modules/audio_coding/neteq/include/neteq.h"
#include "webrtc/system_wrappers/include/clock.h"
#include "webrtc/system_wrappers/include/trace.h"

namespace webrtc {

namespace acm2 {

namespace {

// Is the given codec a CNG codec?
// TODO(kwiberg): Move to RentACodec.
bool IsCng(int codec_id) {
  auto i = RentACodec::CodecIdFromIndex(codec_id);
  return (i && (*i == RentACodec::CodecId::kCNNB ||
                *i == RentACodec::CodecId::kCNWB ||
                *i == RentACodec::CodecId::kCNSWB ||
                *i == RentACodec::CodecId::kCNFB));
}

}  // namespace

AcmReceiver::AcmReceiver(const AudioCodingModule::Config& config)
    : last_audio_decoder_(nullptr),
      last_audio_buffer_(new int16_t[AudioFrame::kMaxDataSizeSamples]),
      neteq_(NetEq::Create(config.neteq_config, config.decoder_factory)),
      clock_(config.clock),
      resampled_last_output_frame_(true) {
  assert(clock_);
  memset(last_audio_buffer_.get(), 0, AudioFrame::kMaxDataSizeSamples);
}

AcmReceiver::~AcmReceiver() {
  delete neteq_;
}

int AcmReceiver::SetMinimumDelay(int delay_ms) {
  if (neteq_->SetMinimumDelay(delay_ms))
    return 0;
  LOG(LERROR) << "AcmReceiver::SetExtraDelay " << delay_ms;
  return -1;
}

int AcmReceiver::SetMaximumDelay(int delay_ms) {
  if (neteq_->SetMaximumDelay(delay_ms))
    return 0;
  LOG(LERROR) << "AcmReceiver::SetExtraDelay " << delay_ms;
  return -1;
}

int AcmReceiver::LeastRequiredDelayMs() const {
  return neteq_->LeastRequiredDelayMs();
}

rtc::Optional<int> AcmReceiver::last_packet_sample_rate_hz() const {
  rtc::CritScope lock(&crit_sect_);
  return last_packet_sample_rate_hz_;
}

int AcmReceiver::last_output_sample_rate_hz() const {
  return neteq_->last_output_sample_rate_hz();
}

int AcmReceiver::InsertPacket(const WebRtcRTPHeader& rtp_header,
                              rtc::ArrayView<const uint8_t> incoming_payload) {
  uint32_t receive_timestamp = 0;
  const RTPHeader* header = &rtp_header.header;  // Just a shorthand.

  {
    rtc::CritScope lock(&crit_sect_);

    const Decoder* decoder = RtpHeaderToDecoder(*header, incoming_payload[0]);
    if (!decoder) {
      LOG_F(LS_ERROR) << "Payload-type "
                      << static_cast<int>(header->payloadType)
                      << " is not registered.";
      return -1;
    }
    const int sample_rate_hz = [&decoder] {
      const auto ci = RentACodec::CodecIdFromIndex(decoder->acm_codec_id);
      return ci ? RentACodec::CodecInstById(*ci)->plfreq : -1;
    }();
    receive_timestamp = NowInTimestamp(sample_rate_hz);

    // If this is a CNG while the audio codec is not mono, skip pushing in
    // packets into NetEq.
    if (IsCng(decoder->acm_codec_id) && last_audio_decoder_ &&
        last_audio_decoder_->channels > 1)
        return 0;
    if (!IsCng(decoder->acm_codec_id) &&
        decoder->acm_codec_id !=
            *RentACodec::CodecIndexFromId(RentACodec::CodecId::kAVT)) {
      last_audio_decoder_ = decoder;
      last_packet_sample_rate_hz_ = rtc::Optional<int>(decoder->sample_rate_hz);
    }

  }  // |crit_sect_| is released.

  if (neteq_->InsertPacket(rtp_header, incoming_payload, receive_timestamp) <
      0) {
    LOG(LERROR) << "AcmReceiver::InsertPacket "
                << static_cast<int>(header->payloadType)
                << " Failed to insert packet";
    return -1;
  }
  return 0;
}

int AcmReceiver::GetAudio(int desired_freq_hz,
                          AudioFrame* audio_frame,
                          bool* muted) {
  // Accessing members, take the lock.
  rtc::CritScope lock(&crit_sect_);

  if (neteq_->GetAudio(audio_frame, muted) != NetEq::kOK) {
    LOG(LERROR) << "AcmReceiver::GetAudio - NetEq Failed.";
    return -1;
  }

  const int current_sample_rate_hz = neteq_->last_output_sample_rate_hz();

  // Update if resampling is required.
  const bool need_resampling =
      (desired_freq_hz != -1) && (current_sample_rate_hz != desired_freq_hz);

  if (need_resampling && !resampled_last_output_frame_) {
    // Prime the resampler with the last frame.
    int16_t temp_output[AudioFrame::kMaxDataSizeSamples];
    int samples_per_channel_int = resampler_.Resample10Msec(
        last_audio_buffer_.get(), current_sample_rate_hz, desired_freq_hz,
        audio_frame->num_channels_, AudioFrame::kMaxDataSizeSamples,
        temp_output);
    if (samples_per_channel_int < 0) {
      LOG(LERROR) << "AcmReceiver::GetAudio - "
                     "Resampling last_audio_buffer_ failed.";
      return -1;
    }
  }

  // TODO(henrik.lundin) Glitches in the output may appear if the output rate
  // from NetEq changes. See WebRTC issue 3923.
  if (need_resampling) {
    int samples_per_channel_int = resampler_.Resample10Msec(
        audio_frame->data_, current_sample_rate_hz, desired_freq_hz,
        audio_frame->num_channels_, AudioFrame::kMaxDataSizeSamples,
        audio_frame->data_);
    if (samples_per_channel_int < 0) {
      LOG(LERROR) << "AcmReceiver::GetAudio - Resampling audio_buffer_ failed.";
      return -1;
    }
    audio_frame->samples_per_channel_ =
        static_cast<size_t>(samples_per_channel_int);
    audio_frame->sample_rate_hz_ = desired_freq_hz;
    RTC_DCHECK_EQ(
        audio_frame->sample_rate_hz_,
        rtc::checked_cast<int>(audio_frame->samples_per_channel_ * 100));
    resampled_last_output_frame_ = true;
  } else {
    resampled_last_output_frame_ = false;
    // We might end up here ONLY if codec is changed.
  }

  // Store current audio in |last_audio_buffer_| for next time.
  memcpy(last_audio_buffer_.get(), audio_frame->data_,
         sizeof(int16_t) * audio_frame->samples_per_channel_ *
             audio_frame->num_channels_);

  call_stats_.DecodedByNetEq(audio_frame->speech_type_);
  return 0;
}

int32_t AcmReceiver::AddCodec(int acm_codec_id,
                              uint8_t payload_type,
                              size_t channels,
                              int sample_rate_hz,
                              AudioDecoder* audio_decoder,
                              const std::string& name) {
  const auto neteq_decoder = [acm_codec_id, channels]() -> NetEqDecoder {
    if (acm_codec_id == -1)
      return NetEqDecoder::kDecoderArbitrary;  // External decoder.
    const rtc::Optional<RentACodec::CodecId> cid =
        RentACodec::CodecIdFromIndex(acm_codec_id);
    RTC_DCHECK(cid) << "Invalid codec index: " << acm_codec_id;
    const rtc::Optional<NetEqDecoder> ned =
        RentACodec::NetEqDecoderFromCodecId(*cid, channels);
    RTC_DCHECK(ned) << "Invalid codec ID: " << static_cast<int>(*cid);
    return *ned;
  }();

  rtc::CritScope lock(&crit_sect_);

  // The corresponding NetEq decoder ID.
  // If this codec has been registered before.
  auto it = decoders_.find(payload_type);
  if (it != decoders_.end()) {
    const Decoder& decoder = it->second;
    if (acm_codec_id != -1 && decoder.acm_codec_id == acm_codec_id &&
        decoder.channels == channels &&
        decoder.sample_rate_hz == sample_rate_hz) {
      // Re-registering the same codec. Do nothing and return.
      return 0;
    }

    // Changing codec. First unregister the old codec, then register the new
    // one.
    if (neteq_->RemovePayloadType(payload_type) != NetEq::kOK) {
      LOG(LERROR) << "Cannot remove payload " << static_cast<int>(payload_type);
      return -1;
    }

    decoders_.erase(it);
  }

  int ret_val;
  if (!audio_decoder) {
    ret_val = neteq_->RegisterPayloadType(neteq_decoder, name, payload_type);
  } else {
    ret_val = neteq_->RegisterExternalDecoder(
        audio_decoder, neteq_decoder, name, payload_type);
  }
  if (ret_val != NetEq::kOK) {
    LOG(LERROR) << "AcmReceiver::AddCodec " << acm_codec_id
                << static_cast<int>(payload_type)
                << " channels: " << channels;
    return -1;
  }

  Decoder decoder;
  decoder.acm_codec_id = acm_codec_id;
  decoder.payload_type = payload_type;
  decoder.channels = channels;
  decoder.sample_rate_hz = sample_rate_hz;
  decoders_[payload_type] = decoder;
  return 0;
}

void AcmReceiver::FlushBuffers() {
  neteq_->FlushBuffers();
}

// If failed in removing one of the codecs, this method continues to remove as
// many as it can.
int AcmReceiver::RemoveAllCodecs() {
  int ret_val = 0;
  rtc::CritScope lock(&crit_sect_);
  for (auto it = decoders_.begin(); it != decoders_.end(); ) {
    auto cur = it;
    ++it;  // it will be valid even if we erase cur
    if (neteq_->RemovePayloadType(cur->second.payload_type) == 0) {
      decoders_.erase(cur);
    } else {
      LOG_F(LS_ERROR) << "Cannot remove payload "
                      << static_cast<int>(cur->second.payload_type);
      ret_val = -1;
    }
  }

  // No codec is registered, invalidate last audio decoder.
  last_audio_decoder_ = nullptr;
  last_packet_sample_rate_hz_ = rtc::Optional<int>();
  return ret_val;
}

int AcmReceiver::RemoveCodec(uint8_t payload_type) {
  rtc::CritScope lock(&crit_sect_);
  auto it = decoders_.find(payload_type);
  if (it == decoders_.end()) {  // Such a payload-type is not registered.
    return 0;
  }
  if (neteq_->RemovePayloadType(payload_type) != NetEq::kOK) {
    LOG(LERROR) << "AcmReceiver::RemoveCodec" << static_cast<int>(payload_type);
    return -1;
  }
  if (last_audio_decoder_ == &it->second) {
    last_audio_decoder_ = nullptr;
    last_packet_sample_rate_hz_ = rtc::Optional<int>();
  }
  decoders_.erase(it);
  return 0;
}

rtc::Optional<uint32_t> AcmReceiver::GetPlayoutTimestamp() {
  return neteq_->GetPlayoutTimestamp();
}

int AcmReceiver::FilteredCurrentDelayMs() const {
  return neteq_->FilteredCurrentDelayMs();
}

int AcmReceiver::LastAudioCodec(CodecInst* codec) const {
  rtc::CritScope lock(&crit_sect_);
  if (!last_audio_decoder_) {
    return -1;
  }
  *codec = *RentACodec::CodecInstById(
      *RentACodec::CodecIdFromIndex(last_audio_decoder_->acm_codec_id));
  codec->pltype = last_audio_decoder_->payload_type;
  codec->channels = last_audio_decoder_->channels;
  codec->plfreq = last_audio_decoder_->sample_rate_hz;
  return 0;
}

void AcmReceiver::GetNetworkStatistics(NetworkStatistics* acm_stat) {
  NetEqNetworkStatistics neteq_stat;
  // NetEq function always returns zero, so we don't check the return value.
  neteq_->NetworkStatistics(&neteq_stat);

  acm_stat->currentBufferSize = neteq_stat.current_buffer_size_ms;
  acm_stat->preferredBufferSize = neteq_stat.preferred_buffer_size_ms;
  acm_stat->jitterPeaksFound = neteq_stat.jitter_peaks_found ? true : false;
  acm_stat->currentPacketLossRate = neteq_stat.packet_loss_rate;
  acm_stat->currentDiscardRate = neteq_stat.packet_discard_rate;
  acm_stat->currentExpandRate = neteq_stat.expand_rate;
  acm_stat->currentSpeechExpandRate = neteq_stat.speech_expand_rate;
  acm_stat->currentPreemptiveRate = neteq_stat.preemptive_rate;
  acm_stat->currentAccelerateRate = neteq_stat.accelerate_rate;
  acm_stat->currentSecondaryDecodedRate = neteq_stat.secondary_decoded_rate;
  acm_stat->clockDriftPPM = neteq_stat.clockdrift_ppm;
  acm_stat->addedSamples = neteq_stat.added_zero_samples;
  acm_stat->meanWaitingTimeMs = neteq_stat.mean_waiting_time_ms;
  acm_stat->medianWaitingTimeMs = neteq_stat.median_waiting_time_ms;
  acm_stat->minWaitingTimeMs = neteq_stat.min_waiting_time_ms;
  acm_stat->maxWaitingTimeMs = neteq_stat.max_waiting_time_ms;
}

int AcmReceiver::DecoderByPayloadType(uint8_t payload_type,
                                      CodecInst* codec) const {
  rtc::CritScope lock(&crit_sect_);
  auto it = decoders_.find(payload_type);
  if (it == decoders_.end()) {
    LOG(LERROR) << "AcmReceiver::DecoderByPayloadType "
                << static_cast<int>(payload_type);
    return -1;
  }
  const Decoder& decoder = it->second;
  *codec = *RentACodec::CodecInstById(
      *RentACodec::CodecIdFromIndex(decoder.acm_codec_id));
  codec->pltype = decoder.payload_type;
  codec->channels = decoder.channels;
  codec->plfreq = decoder.sample_rate_hz;
  return 0;
}

int AcmReceiver::EnableNack(size_t max_nack_list_size) {
  neteq_->EnableNack(max_nack_list_size);
  return 0;
}

void AcmReceiver::DisableNack() {
  neteq_->DisableNack();
}

std::vector<uint16_t> AcmReceiver::GetNackList(
    int64_t round_trip_time_ms) const {
  return neteq_->GetNackList(round_trip_time_ms);
}

void AcmReceiver::ResetInitialDelay() {
  neteq_->SetMinimumDelay(0);
  // TODO(turajs): Should NetEq Buffer be flushed?
}

const AcmReceiver::Decoder* AcmReceiver::RtpHeaderToDecoder(
    const RTPHeader& rtp_header,
    uint8_t payload_type) const {
  auto it = decoders_.find(rtp_header.payloadType);
  const auto red_index =
      RentACodec::CodecIndexFromId(RentACodec::CodecId::kRED);
  if (red_index &&  // This ensures that RED is defined in WebRTC.
      it != decoders_.end() && it->second.acm_codec_id == *red_index) {
    // This is a RED packet, get the payload of the audio codec.
    it = decoders_.find(payload_type & 0x7F);
  }

  // Check if the payload is registered.
  return it != decoders_.end() ? &it->second : nullptr;
}

uint32_t AcmReceiver::NowInTimestamp(int decoder_sampling_rate) const {
  // Down-cast the time to (32-6)-bit since we only care about
  // the least significant bits. (32-6) bits cover 2^(32-6) = 67108864 ms.
  // We masked 6 most significant bits of 32-bit so there is no overflow in
  // the conversion from milliseconds to timestamp.
  const uint32_t now_in_ms = static_cast<uint32_t>(
      clock_->TimeInMilliseconds() & 0x03ffffff);
  return static_cast<uint32_t>(
      (decoder_sampling_rate / 1000) * now_in_ms);
}

void AcmReceiver::GetDecodingCallStatistics(
    AudioDecodingCallStats* stats) const {
  rtc::CritScope lock(&crit_sect_);
  *stats = call_stats_.GetDecodingStatistics();
}

}  // namespace acm2

}  // namespace webrtc
