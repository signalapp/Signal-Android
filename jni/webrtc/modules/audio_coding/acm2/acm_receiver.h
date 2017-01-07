/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_ACM2_ACM_RECEIVER_H_
#define WEBRTC_MODULES_AUDIO_CODING_ACM2_ACM_RECEIVER_H_

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "webrtc/base/array_view.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/optional.h"
#include "webrtc/base/thread_annotations.h"
#include "webrtc/common_audio/vad/include/webrtc_vad.h"
#include "webrtc/engine_configurations.h"
#include "webrtc/modules/audio_coding/acm2/acm_resampler.h"
#include "webrtc/modules/audio_coding/acm2/call_statistics.h"
#include "webrtc/modules/audio_coding/acm2/initial_delay_manager.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module.h"
#include "webrtc/modules/audio_coding/neteq/include/neteq.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/typedefs.h"

namespace webrtc {

struct CodecInst;
class NetEq;

namespace acm2 {

class AcmReceiver {
 public:
  struct Decoder {
    int acm_codec_id;
    uint8_t payload_type;
    // This field is meaningful for codecs where both mono and
    // stereo versions are registered under the same ID.
    size_t channels;
    int sample_rate_hz;
  };

  // Constructor of the class
  explicit AcmReceiver(const AudioCodingModule::Config& config);

  // Destructor of the class.
  ~AcmReceiver();

  //
  // Inserts a payload with its associated RTP-header into NetEq.
  //
  // Input:
  //   - rtp_header           : RTP header for the incoming payload containing
  //                            information about payload type, sequence number,
  //                            timestamp, SSRC and marker bit.
  //   - incoming_payload     : Incoming audio payload.
  //   - length_payload       : Length of incoming audio payload in bytes.
  //
  // Return value             : 0 if OK.
  //                           <0 if NetEq returned an error.
  //
  int InsertPacket(const WebRtcRTPHeader& rtp_header,
                   rtc::ArrayView<const uint8_t> incoming_payload);

  //
  // Asks NetEq for 10 milliseconds of decoded audio.
  //
  // Input:
  //   -desired_freq_hz       : specifies the sampling rate [Hz] of the output
  //                            audio. If set -1 indicates to resampling is
  //                            is required and the audio returned at the
  //                            sampling rate of the decoder.
  //
  // Output:
  //   -audio_frame           : an audio frame were output data and
  //                            associated parameters are written to.
  //   -muted                 : if true, the sample data in audio_frame is not
  //                            populated, and must be interpreted as all zero.
  //
  // Return value             : 0 if OK.
  //                           -1 if NetEq returned an error.
  //
  int GetAudio(int desired_freq_hz, AudioFrame* audio_frame, bool* muted);

  //
  // Adds a new codec to the NetEq codec database.
  //
  // Input:
  //   - acm_codec_id        : ACM codec ID; -1 means external decoder.
  //   - payload_type        : payload type.
  //   - sample_rate_hz      : sample rate.
  //   - audio_decoder       : pointer to a decoder object. If it's null, then
  //                           NetEq will internally create a decoder object
  //                           based on the value of |acm_codec_id| (which
  //                           mustn't be -1). Otherwise, NetEq will use the
  //                           given decoder for the given payload type. NetEq
  //                           won't take ownership of the decoder; it's up to
  //                           the caller to delete it when it's no longer
  //                           needed.
  //
  //                           Providing an existing decoder object here is
  //                           necessary for external decoders, but may also be
  //                           used for built-in decoders if NetEq doesn't have
  //                           all the info it needs to construct them properly
  //                           (e.g. iSAC, where the decoder needs to be paired
  //                           with an encoder).
  //
  // Return value             : 0 if OK.
  //                           <0 if NetEq returned an error.
  //
  int AddCodec(int acm_codec_id,
               uint8_t payload_type,
               size_t channels,
               int sample_rate_hz,
               AudioDecoder* audio_decoder,
               const std::string& name);

  //
  // Sets a minimum delay for packet buffer. The given delay is maintained,
  // unless channel condition dictates a higher delay.
  //
  // Input:
  //   - delay_ms             : minimum delay in milliseconds.
  //
  // Return value             : 0 if OK.
  //                           <0 if NetEq returned an error.
  //
  int SetMinimumDelay(int delay_ms);

  //
  // Sets a maximum delay [ms] for the packet buffer. The target delay does not
  // exceed the given value, even if channel condition requires so.
  //
  // Input:
  //   - delay_ms             : maximum delay in milliseconds.
  //
  // Return value             : 0 if OK.
  //                           <0 if NetEq returned an error.
  //
  int SetMaximumDelay(int delay_ms);

  //
  // Get least required delay computed based on channel conditions. Note that
  // this is before applying any user-defined limits (specified by calling
  // (SetMinimumDelay() and/or SetMaximumDelay()).
  //
  int LeastRequiredDelayMs() const;

  //
  // Resets the initial delay to zero.
  //
  void ResetInitialDelay();

  // Returns the sample rate of the decoder associated with the last incoming
  // packet. If no packet of a registered non-CNG codec has been received, the
  // return value is empty. Also, if the decoder was unregistered since the last
  // packet was inserted, the return value is empty.
  rtc::Optional<int> last_packet_sample_rate_hz() const;

  // Returns last_output_sample_rate_hz from the NetEq instance.
  int last_output_sample_rate_hz() const;

  //
  // Get the current network statistics from NetEq.
  //
  // Output:
  //   - statistics           : The current network statistics.
  //
  void GetNetworkStatistics(NetworkStatistics* statistics);

  //
  // Flushes the NetEq packet and speech buffers.
  //
  void FlushBuffers();

  //
  // Removes a payload-type from the NetEq codec database.
  //
  // Input:
  //   - payload_type         : the payload-type to be removed.
  //
  // Return value             : 0 if OK.
  //                           -1 if an error occurred.
  //
  int RemoveCodec(uint8_t payload_type);

  //
  // Remove all registered codecs.
  //
  int RemoveAllCodecs();

  // Returns the RTP timestamp for the last sample delivered by GetAudio().
  // The return value will be empty if no valid timestamp is available.
  rtc::Optional<uint32_t> GetPlayoutTimestamp();

  // Returns the current total delay from NetEq (packet buffer and sync buffer)
  // in ms, with smoothing applied to even out short-time fluctuations due to
  // jitter. The packet buffer part of the delay is not updated during DTX/CNG
  // periods.
  //
  int FilteredCurrentDelayMs() const;

  //
  // Get the audio codec associated with the last non-CNG/non-DTMF received
  // payload. If no non-CNG/non-DTMF packet is received -1 is returned,
  // otherwise return 0.
  //
  int LastAudioCodec(CodecInst* codec) const;

  //
  // Get a decoder given its registered payload-type.
  //
  // Input:
  //    -payload_type         : the payload-type of the codec to be retrieved.
  //
  // Output:
  //    -codec                : codec associated with the given payload-type.
  //
  // Return value             : 0 if succeeded.
  //                           -1 if failed, e.g. given payload-type is not
  //                              registered.
  //
  int DecoderByPayloadType(uint8_t payload_type,
                           CodecInst* codec) const;

  //
  // Enable NACK and set the maximum size of the NACK list. If NACK is already
  // enabled then the maximum NACK list size is modified accordingly.
  //
  // Input:
  //    -max_nack_list_size  : maximum NACK list size
  //                           should be positive (none zero) and less than or
  //                           equal to |Nack::kNackListSizeLimit|
  // Return value
  //                         : 0 if succeeded.
  //                          -1 if failed
  //
  int EnableNack(size_t max_nack_list_size);

  // Disable NACK.
  void DisableNack();

  //
  // Get a list of packets to be retransmitted.
  //
  // Input:
  //    -round_trip_time_ms : estimate of the round-trip-time (in milliseconds).
  // Return value           : list of packets to be retransmitted.
  //
  std::vector<uint16_t> GetNackList(int64_t round_trip_time_ms) const;

  //
  // Get statistics of calls to GetAudio().
  void GetDecodingCallStatistics(AudioDecodingCallStats* stats) const;

 private:
  const Decoder* RtpHeaderToDecoder(const RTPHeader& rtp_header,
                                    uint8_t payload_type) const
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  uint32_t NowInTimestamp(int decoder_sampling_rate) const;

  rtc::CriticalSection crit_sect_;
  const Decoder* last_audio_decoder_ GUARDED_BY(crit_sect_);
  ACMResampler resampler_ GUARDED_BY(crit_sect_);
  std::unique_ptr<int16_t[]> last_audio_buffer_ GUARDED_BY(crit_sect_);
  CallStatistics call_stats_ GUARDED_BY(crit_sect_);
  NetEq* neteq_;
  // Decoders map is keyed by payload type
  std::map<uint8_t, Decoder> decoders_ GUARDED_BY(crit_sect_);
  Clock* clock_;  // TODO(henrik.lundin) Make const if possible.
  bool resampled_last_output_frame_ GUARDED_BY(crit_sect_);
  rtc::Optional<int> last_packet_sample_rate_hz_ GUARDED_BY(crit_sect_);
};

}  // namespace acm2

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_ACM2_ACM_RECEIVER_H_
