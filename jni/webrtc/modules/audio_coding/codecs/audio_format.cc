/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/audio_format.h"

namespace webrtc {

SdpAudioFormat::SdpAudioFormat(const SdpAudioFormat&) = default;
SdpAudioFormat::SdpAudioFormat(SdpAudioFormat&&) = default;

SdpAudioFormat::SdpAudioFormat(const char* name,
                               int clockrate_hz,
                               int num_channels)
    : name(name), clockrate_hz(clockrate_hz), num_channels(num_channels) {}

SdpAudioFormat::SdpAudioFormat(const char* name,
                               int clockrate_hz,
                               int num_channels,
                               Parameters&& param)
    : name(name),
      clockrate_hz(clockrate_hz),
      num_channels(num_channels),
      parameters(std::move(param)) {}

SdpAudioFormat::~SdpAudioFormat() = default;
SdpAudioFormat& SdpAudioFormat::operator=(const SdpAudioFormat&) = default;
SdpAudioFormat& SdpAudioFormat::operator=(SdpAudioFormat&&) = default;

void swap(SdpAudioFormat& a, SdpAudioFormat& b) {
  using std::swap;
  swap(a.name, b.name);
  swap(a.clockrate_hz, b.clockrate_hz);
  swap(a.num_channels, b.num_channels);
  swap(a.parameters, b.parameters);
}

std::ostream& operator<<(std::ostream& os, const SdpAudioFormat& saf) {
  os << "{name: " << saf.name;
  os << ", clockrate_hz: " << saf.clockrate_hz;
  os << ", num_channels: " << saf.num_channels;
  os << ", parameters: {";
  const char* sep = "";
  for (const auto& kv : saf.parameters) {
    os << sep << kv.first << ": " << kv.second;
    sep = ", ";
  }
  os << "}}";
  return os;
}

}  // namespace webrtc
