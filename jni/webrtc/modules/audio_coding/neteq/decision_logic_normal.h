/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_NORMAL_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_NORMAL_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/decision_logic.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Implementation of the DecisionLogic class for playout modes kPlayoutOn and
// kPlayoutStreaming.
class DecisionLogicNormal : public DecisionLogic {
 public:
  // Constructor.
  DecisionLogicNormal(int fs_hz,
                      size_t output_size_samples,
                      NetEqPlayoutMode playout_mode,
                      DecoderDatabase* decoder_database,
                      const PacketBuffer& packet_buffer,
                      DelayManager* delay_manager,
                      BufferLevelFilter* buffer_level_filter,
                      const TickTimer* tick_timer)
      : DecisionLogic(fs_hz,
                      output_size_samples,
                      playout_mode,
                      decoder_database,
                      packet_buffer,
                      delay_manager,
                      buffer_level_filter,
                      tick_timer) {}

 protected:
  static const int kAllowMergeWithoutExpandMs = 20;  // 20 ms.
  static const int kReinitAfterExpands = 100;
  static const int kMaxWaitForPacket = 10;

  // Returns the operation that should be done next. |sync_buffer| and |expand|
  // are provided for reference. |decoder_frame_length| is the number of samples
  // obtained from the last decoded frame. If there is a packet available, the
  // packet header should be supplied in |packet_header|; otherwise it should
  // be NULL. The mode resulting form the last call to NetEqImpl::GetAudio is
  // supplied in |prev_mode|. If there is a DTMF event to play, |play_dtmf|
  // should be set to true. The output variable |reset_decoder| will be set to
  // true if a reset is required; otherwise it is left unchanged (i.e., it can
  // remain true if it was true before the call).
  Operations GetDecisionSpecialized(const SyncBuffer& sync_buffer,
                                    const Expand& expand,
                                    size_t decoder_frame_length,
                                    const RTPHeader* packet_header,
                                    Modes prev_mode,
                                    bool play_dtmf,
                                    bool* reset_decoder,
                                    size_t generated_noise_samples) override;

  // Returns the operation to do given that the expected packet is not
  // available, but a packet further into the future is at hand.
  virtual Operations FuturePacketAvailable(
      const SyncBuffer& sync_buffer,
      const Expand& expand,
      size_t decoder_frame_length,
      Modes prev_mode,
      uint32_t target_timestamp,
      uint32_t available_timestamp,
      bool play_dtmf,
      size_t generated_noise_samples);

  // Returns the operation to do given that the expected packet is available.
  virtual Operations ExpectedPacketAvailable(Modes prev_mode, bool play_dtmf);

  // Returns the operation given that no packets are available (except maybe
  // a DTMF event, flagged by setting |play_dtmf| true).
  virtual Operations NoPacket(bool play_dtmf);

 private:
  // Returns the operation given that the next available packet is a comfort
  // noise payload (RFC 3389 only, not codec-internal).
  Operations CngOperation(Modes prev_mode,
                          uint32_t target_timestamp,
                          uint32_t available_timestamp,
                          size_t generated_noise_samples);

  // Checks if enough time has elapsed since the last successful timescale
  // operation was done (i.e., accelerate or preemptive expand).
  bool TimescaleAllowed() const {
    return !timescale_countdown_ || timescale_countdown_->Finished();
  }

  // Checks if the current (filtered) buffer level is under the target level.
  bool UnderTargetLevel() const;

  // Checks if |timestamp_leap| is so long into the future that a reset due
  // to exceeding kReinitAfterExpands will be done.
  bool ReinitAfterExpands(uint32_t timestamp_leap) const;

  // Checks if we still have not done enough expands to cover the distance from
  // the last decoded packet to the next available packet, the distance beeing
  // conveyed in |timestamp_leap|.
  bool PacketTooEarly(uint32_t timestamp_leap) const;

  // Checks if num_consecutive_expands_ >= kMaxWaitForPacket.
  bool MaxWaitForPacket() const;

  RTC_DISALLOW_COPY_AND_ASSIGN(DecisionLogicNormal);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_NORMAL_H_
