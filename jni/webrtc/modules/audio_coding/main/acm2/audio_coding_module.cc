/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/interface/audio_coding_module.h"

#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_codec_database.h"
#include "webrtc/modules/audio_coding/main/acm2/audio_coding_module_impl.h"
#include "webrtc/system_wrappers/interface/clock.h"
#include "webrtc/system_wrappers/interface/trace.h"

namespace webrtc {

// Create module
AudioCodingModule* AudioCodingModule::Create(int id) {
  return Create(id, Clock::GetRealTimeClock());
}

AudioCodingModule* AudioCodingModule::Create(int id, Clock* clock) {
  AudioCodingModule::Config config;
  config.id = id;
  config.clock = clock;
  return new acm2::AudioCodingModuleImpl(config);
}

// Get number of supported codecs
int AudioCodingModule::NumberOfCodecs() {
  return acm2::ACMCodecDB::kNumCodecs;
}

// Get supported codec parameters with id
int AudioCodingModule::Codec(int list_id, CodecInst* codec) {
  // Get the codec settings for the codec with the given list ID
  return acm2::ACMCodecDB::Codec(list_id, codec);
}

// Get supported codec parameters with name, frequency and number of channels.
int AudioCodingModule::Codec(const char* payload_name,
                             CodecInst* codec,
                             int sampling_freq_hz,
                             int channels) {
  int codec_id;

  // Get the id of the codec from the database.
  codec_id = acm2::ACMCodecDB::CodecId(
      payload_name, sampling_freq_hz, channels);
  if (codec_id < 0) {
    // We couldn't find a matching codec, set the parameters to unacceptable
    // values and return.
    codec->plname[0] = '\0';
    codec->pltype = -1;
    codec->pacsize = 0;
    codec->rate = 0;
    codec->plfreq = 0;
    return -1;
  }

  // Get default codec settings.
  acm2::ACMCodecDB::Codec(codec_id, codec);

  // Keep the number of channels from the function call. For most codecs it
  // will be the same value as in default codec settings, but not for all.
  codec->channels = channels;

  return 0;
}

// Get supported codec Index with name, frequency and number of channels.
int AudioCodingModule::Codec(const char* payload_name,
                             int sampling_freq_hz,
                             int channels) {
  return acm2::ACMCodecDB::CodecId(payload_name, sampling_freq_hz, channels);
}

// Checks the validity of the parameters of the given codec
bool AudioCodingModule::IsCodecValid(const CodecInst& codec) {
  int mirror_id;

  int codec_number = acm2::ACMCodecDB::CodecNumber(codec, &mirror_id);

  if (codec_number < 0) {
    WEBRTC_TRACE(webrtc::kTraceError, webrtc::kTraceAudioCoding, -1,
                 "Invalid codec setting");
    return false;
  } else {
    return true;
  }
}

}  // namespace webrtc
