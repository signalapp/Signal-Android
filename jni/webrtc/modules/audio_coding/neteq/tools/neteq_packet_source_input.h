/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_PACKET_SOURCE_INPUT_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_PACKET_SOURCE_INPUT_H_

#include <string>

#include "webrtc/modules/audio_coding/neteq/tools/neteq_input.h"

namespace webrtc {
namespace test {

class RtpFileSource;
class RtcEventLogSource;

// An adapter class to dress up a PacketSource object as a NetEqInput.
class NetEqPacketSourceInput : public NetEqInput {
 public:
  NetEqPacketSourceInput();
  rtc::Optional<int64_t> NextPacketTime() const override;
  std::unique_ptr<PacketData> PopPacket() override;
  rtc::Optional<RTPHeader> NextHeader() const override;
  bool ended() const override { return !next_output_event_ms_; }

 protected:
  virtual PacketSource* source() = 0;
  void LoadNextPacket();

  rtc::Optional<int64_t> next_output_event_ms_;

 private:
  std::unique_ptr<Packet> packet_;
};

// Implementation of NetEqPacketSourceInput to be used with an RtpFileSource.
class NetEqRtpDumpInput final : public NetEqPacketSourceInput {
 public:
  explicit NetEqRtpDumpInput(const std::string& file_name);

  rtc::Optional<int64_t> NextOutputEventTime() const override;
  void AdvanceOutputEvent() override;

 protected:
  PacketSource* source() override;

 private:
  static constexpr int64_t kOutputPeriodMs = 10;

  std::unique_ptr<RtpFileSource> source_;
};

// Implementation of NetEqPacketSourceInput to be used with an
// RtcEventLogSource.
class NetEqEventLogInput final : public NetEqPacketSourceInput {
 public:
  explicit NetEqEventLogInput(const std::string& file_name);

  rtc::Optional<int64_t> NextOutputEventTime() const override;
  void AdvanceOutputEvent() override;

 protected:
  PacketSource* source() override;

 private:
  std::unique_ptr<RtcEventLogSource> source_;
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_PACKET_SOURCE_INPUT_H_
