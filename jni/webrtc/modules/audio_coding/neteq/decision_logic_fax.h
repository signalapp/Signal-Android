/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_FAX_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_FAX_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/decision_logic.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Implementation of the DecisionLogic class for playout modes kPlayoutFax and
// kPlayoutOff.
class DecisionLogicFax : public DecisionLogic {
 public:
  // Constructor.
  DecisionLogicFax(int fs_hz,
                   int output_size_samples,
                   NetEqPlayoutMode playout_mode,
                   DecoderDatabase* decoder_database,
                   const PacketBuffer& packet_buffer,
                   DelayManager* delay_manager,
                   BufferLevelFilter* buffer_level_filter)
      : DecisionLogic(fs_hz, output_size_samples, playout_mode,
                      decoder_database, packet_buffer, delay_manager,
                      buffer_level_filter) {
  }

  // Destructor.
  virtual ~DecisionLogicFax() {}

 protected:
  // Returns the operation that should be done next. |sync_buffer| and |expand|
  // are provided for reference. |decoder_frame_length| is the number of samples
  // obtained from the last decoded frame. If there is a packet available, the
  // packet header should be supplied in |packet_header|; otherwise it should
  // be NULL. The mode resulting form the last call to NetEqImpl::GetAudio is
  // supplied in |prev_mode|. If there is a DTMF event to play, |play_dtmf|
  // should be set to true. The output variable |reset_decoder| will be set to
  // true if a reset is required; otherwise it is left unchanged (i.e., it can
  // remain true if it was true before the call).
  virtual Operations GetDecisionSpecialized(const SyncBuffer& sync_buffer,
                                            const Expand& expand,
                                            int decoder_frame_length,
                                            const RTPHeader* packet_header,
                                            Modes prev_mode,
                                            bool play_dtmf,
                                            bool* reset_decoder) OVERRIDE;

 private:
  DISALLOW_COPY_AND_ASSIGN(DecisionLogicFax);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_FAX_H_
