/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/defines.h"
#include "webrtc/modules/audio_coding/neteq/include/neteq.h"
#include "webrtc/modules/audio_coding/neteq/tick_timer.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Forward declarations.
class BufferLevelFilter;
class DecoderDatabase;
class DelayManager;
class Expand;
class PacketBuffer;
class SyncBuffer;
struct RTPHeader;

// This is the base class for the decision tree implementations. Derived classes
// must implement the method GetDecisionSpecialized().
class DecisionLogic {
 public:
  // Static factory function which creates different types of objects depending
  // on the |playout_mode|.
  static DecisionLogic* Create(int fs_hz,
                               size_t output_size_samples,
                               NetEqPlayoutMode playout_mode,
                               DecoderDatabase* decoder_database,
                               const PacketBuffer& packet_buffer,
                               DelayManager* delay_manager,
                               BufferLevelFilter* buffer_level_filter,
                               const TickTimer* tick_timer);

  // Constructor.
  DecisionLogic(int fs_hz,
                size_t output_size_samples,
                NetEqPlayoutMode playout_mode,
                DecoderDatabase* decoder_database,
                const PacketBuffer& packet_buffer,
                DelayManager* delay_manager,
                BufferLevelFilter* buffer_level_filter,
                const TickTimer* tick_timer);

  virtual ~DecisionLogic();

  // Resets object to a clean state.
  void Reset();

  // Resets parts of the state. Typically done when switching codecs.
  void SoftReset();

  // Sets the sample rate and the output block size.
  void SetSampleRate(int fs_hz, size_t output_size_samples);

  // Returns the operation that should be done next. |sync_buffer| and |expand|
  // are provided for reference. |decoder_frame_length| is the number of samples
  // obtained from the last decoded frame. If there is a packet available, the
  // packet header should be supplied in |packet_header|; otherwise it should
  // be NULL. The mode resulting form the last call to NetEqImpl::GetAudio is
  // supplied in |prev_mode|. If there is a DTMF event to play, |play_dtmf|
  // should be set to true. The output variable |reset_decoder| will be set to
  // true if a reset is required; otherwise it is left unchanged (i.e., it can
  // remain true if it was true before the call).
  // This method end with calling GetDecisionSpecialized to get the actual
  // return value.
  Operations GetDecision(const SyncBuffer& sync_buffer,
                         const Expand& expand,
                         size_t decoder_frame_length,
                         const RTPHeader* packet_header,
                         Modes prev_mode,
                         bool play_dtmf,
                         size_t generated_noise_samples,
                         bool* reset_decoder);

  // These methods test the |cng_state_| for different conditions.
  bool CngRfc3389On() const { return cng_state_ == kCngRfc3389On; }
  bool CngOff() const { return cng_state_ == kCngOff; }

  // Resets the |cng_state_| to kCngOff.
  void SetCngOff() { cng_state_ = kCngOff; }

  // Reports back to DecisionLogic whether the decision to do expand remains or
  // not. Note that this is necessary, since an expand decision can be changed
  // to kNormal in NetEqImpl::GetDecision if there is still enough data in the
  // sync buffer.
  virtual void ExpandDecision(Operations operation);

  // Adds |value| to |sample_memory_|.
  void AddSampleMemory(int32_t value) {
    sample_memory_ += value;
  }

  // Accessors and mutators.
  void set_sample_memory(int32_t value) { sample_memory_ = value; }
  size_t noise_fast_forward() const { return noise_fast_forward_; }
  size_t packet_length_samples() const { return packet_length_samples_; }
  void set_packet_length_samples(size_t value) {
    packet_length_samples_ = value;
  }
  void set_prev_time_scale(bool value) { prev_time_scale_ = value; }
  NetEqPlayoutMode playout_mode() const { return playout_mode_; }

 protected:
  // The value 5 sets maximum time-stretch rate to about 100 ms/s.
  static const int kMinTimescaleInterval = 5;

  enum CngState {
    kCngOff,
    kCngRfc3389On,
    kCngInternalOn
  };

  // Returns the operation that should be done next. |sync_buffer| and |expand|
  // are provided for reference. |decoder_frame_length| is the number of samples
  // obtained from the last decoded frame. If there is a packet available, the
  // packet header should be supplied in |packet_header|; otherwise it should
  // be NULL. The mode resulting form the last call to NetEqImpl::GetAudio is
  // supplied in |prev_mode|. If there is a DTMF event to play, |play_dtmf|
  // should be set to true. The output variable |reset_decoder| will be set to
  // true if a reset is required; otherwise it is left unchanged (i.e., it can
  // remain true if it was true before the call).
  // Should be implemented by derived classes.
  virtual Operations GetDecisionSpecialized(const SyncBuffer& sync_buffer,
                                            const Expand& expand,
                                            size_t decoder_frame_length,
                                            const RTPHeader* packet_header,
                                            Modes prev_mode,
                                            bool play_dtmf,
                                            bool* reset_decoder,
                                            size_t generated_noise_samples) = 0;

  // Updates the |buffer_level_filter_| with the current buffer level
  // |buffer_size_packets|.
  void FilterBufferLevel(size_t buffer_size_packets, Modes prev_mode);

  DecoderDatabase* decoder_database_;
  const PacketBuffer& packet_buffer_;
  DelayManager* delay_manager_;
  BufferLevelFilter* buffer_level_filter_;
  const TickTimer* tick_timer_;
  int fs_mult_;
  size_t output_size_samples_;
  CngState cng_state_;  // Remember if comfort noise is interrupted by other
                        // event (e.g., DTMF).
  size_t noise_fast_forward_ = 0;
  size_t packet_length_samples_;
  int sample_memory_;
  bool prev_time_scale_;
  std::unique_ptr<TickTimer::Countdown> timescale_countdown_;
  int num_consecutive_expands_;
  const NetEqPlayoutMode playout_mode_;

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(DecisionLogic);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_H_
