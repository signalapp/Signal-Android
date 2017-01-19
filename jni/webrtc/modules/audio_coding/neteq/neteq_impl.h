/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_NETEQ_IMPL_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_NETEQ_IMPL_H_

#include <vector>

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/audio_multi_vector.h"
#include "webrtc/modules/audio_coding/neteq/defines.h"
#include "webrtc/modules/audio_coding/neteq/interface/neteq.h"
#include "webrtc/modules/audio_coding/neteq/packet.h"  // Declare PacketList.
#include "webrtc/modules/audio_coding/neteq/random_vector.h"
#include "webrtc/modules/audio_coding/neteq/rtcp.h"
#include "webrtc/modules/audio_coding/neteq/statistics_calculator.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/system_wrappers/interface/thread_annotations.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Forward declarations.
class Accelerate;
class BackgroundNoise;
class BufferLevelFilter;
class ComfortNoise;
class CriticalSectionWrapper;
class DecisionLogic;
class DecoderDatabase;
class DelayManager;
class DelayPeakDetector;
class DtmfBuffer;
class DtmfToneGenerator;
class Expand;
class Merge;
class Normal;
class PacketBuffer;
class PayloadSplitter;
class PostDecodeVad;
class PreemptiveExpand;
class RandomVector;
class SyncBuffer;
class TimestampScaler;
struct AccelerateFactory;
struct DtmfEvent;
struct ExpandFactory;
struct PreemptiveExpandFactory;

class NetEqImpl : public webrtc::NetEq {
 public:
  // Creates a new NetEqImpl object. The object will assume ownership of all
  // injected dependencies, and will delete them when done.
  NetEqImpl(const NetEq::Config& config,
            BufferLevelFilter* buffer_level_filter,
            DecoderDatabase* decoder_database,
            DelayManager* delay_manager,
            DelayPeakDetector* delay_peak_detector,
            DtmfBuffer* dtmf_buffer,
            DtmfToneGenerator* dtmf_tone_generator,
            PacketBuffer* packet_buffer,
            PayloadSplitter* payload_splitter,
            TimestampScaler* timestamp_scaler,
            AccelerateFactory* accelerate_factory,
            ExpandFactory* expand_factory,
            PreemptiveExpandFactory* preemptive_expand_factory,
            bool create_components = true);

  virtual ~NetEqImpl();

  // Inserts a new packet into NetEq. The |receive_timestamp| is an indication
  // of the time when the packet was received, and should be measured with
  // the same tick rate as the RTP timestamp of the current payload.
  // Returns 0 on success, -1 on failure.
  virtual int InsertPacket(const WebRtcRTPHeader& rtp_header,
                           const uint8_t* payload,
                           int length_bytes,
                           uint32_t receive_timestamp);

  // Inserts a sync-packet into packet queue. Sync-packets are decoded to
  // silence and are intended to keep AV-sync intact in an event of long packet
  // losses when Video NACK is enabled but Audio NACK is not. Clients of NetEq
  // might insert sync-packet when they observe that buffer level of NetEq is
  // decreasing below a certain threshold, defined by the application.
  // Sync-packets should have the same payload type as the last audio payload
  // type, i.e. they cannot have DTMF or CNG payload type, nor a codec change
  // can be implied by inserting a sync-packet.
  // Returns kOk on success, kFail on failure.
  virtual int InsertSyncPacket(const WebRtcRTPHeader& rtp_header,
                               uint32_t receive_timestamp);

  // Instructs NetEq to deliver 10 ms of audio data. The data is written to
  // |output_audio|, which can hold (at least) |max_length| elements.
  // The number of channels that were written to the output is provided in
  // the output variable |num_channels|, and each channel contains
  // |samples_per_channel| elements. If more than one channel is written,
  // the samples are interleaved.
  // The speech type is written to |type|, if |type| is not NULL.
  // Returns kOK on success, or kFail in case of an error.
  virtual int GetAudio(size_t max_length, int16_t* output_audio,
                       int* samples_per_channel, int* num_channels,
                       NetEqOutputType* type);

  // Associates |rtp_payload_type| with |codec| and stores the information in
  // the codec database. Returns kOK on success, kFail on failure.
  virtual int RegisterPayloadType(enum NetEqDecoder codec,
                                  uint8_t rtp_payload_type);

  // Provides an externally created decoder object |decoder| to insert in the
  // decoder database. The decoder implements a decoder of type |codec| and
  // associates it with |rtp_payload_type|. Returns kOK on success, kFail on
  // failure.
  virtual int RegisterExternalDecoder(AudioDecoder* decoder,
                                      enum NetEqDecoder codec,
                                      uint8_t rtp_payload_type);

  // Removes |rtp_payload_type| from the codec database. Returns 0 on success,
  // -1 on failure.
  virtual int RemovePayloadType(uint8_t rtp_payload_type);

  virtual bool SetMinimumDelay(int delay_ms);

  virtual bool SetMaximumDelay(int delay_ms);

  virtual int LeastRequiredDelayMs() const;

  virtual int SetTargetDelay() { return kNotImplemented; }

  virtual int TargetDelay() { return kNotImplemented; }

  virtual int CurrentDelay() { return kNotImplemented; }

  // Sets the playout mode to |mode|.
  virtual void SetPlayoutMode(NetEqPlayoutMode mode);

  // Returns the current playout mode.
  virtual NetEqPlayoutMode PlayoutMode() const;

  // Writes the current network statistics to |stats|. The statistics are reset
  // after the call.
  virtual int NetworkStatistics(NetEqNetworkStatistics* stats);

  // Writes the last packet waiting times (in ms) to |waiting_times|. The number
  // of values written is no more than 100, but may be smaller if the interface
  // is polled again before 100 packets has arrived.
  virtual void WaitingTimes(std::vector<int>* waiting_times);

  // Writes the current RTCP statistics to |stats|. The statistics are reset
  // and a new report period is started with the call.
  virtual void GetRtcpStatistics(RtcpStatistics* stats);

  // Same as RtcpStatistics(), but does not reset anything.
  virtual void GetRtcpStatisticsNoReset(RtcpStatistics* stats);

  // Enables post-decode VAD. When enabled, GetAudio() will return
  // kOutputVADPassive when the signal contains no speech.
  virtual void EnableVad();

  // Disables post-decode VAD.
  virtual void DisableVad();

  virtual bool GetPlayoutTimestamp(uint32_t* timestamp);

  virtual int SetTargetNumberOfChannels() { return kNotImplemented; }

  virtual int SetTargetSampleRate() { return kNotImplemented; }

  // Returns the error code for the last occurred error. If no error has
  // occurred, 0 is returned.
  virtual int LastError();

  // Returns the error code last returned by a decoder (audio or comfort noise).
  // When LastError() returns kDecoderErrorCode or kComfortNoiseErrorCode, check
  // this method to get the decoder's error code.
  virtual int LastDecoderError();

  // Flushes both the packet buffer and the sync buffer.
  virtual void FlushBuffers();

  virtual void PacketBufferStatistics(int* current_num_packets,
                                      int* max_num_packets) const;

  // Get sequence number and timestamp of the latest RTP.
  // This method is to facilitate NACK.
  virtual int DecodedRtpInfo(int* sequence_number, uint32_t* timestamp) const;

  // This accessor method is only intended for testing purposes.
  virtual const SyncBuffer* sync_buffer_for_test() const;

 protected:
  static const int kOutputSizeMs = 10;
  static const int kMaxFrameSize = 2880;  // 60 ms @ 48 kHz.
  // TODO(hlundin): Provide a better value for kSyncBufferSize.
  static const int kSyncBufferSize = 2 * kMaxFrameSize;

  // Inserts a new packet into NetEq. This is used by the InsertPacket method
  // above. Returns 0 on success, otherwise an error code.
  // TODO(hlundin): Merge this with InsertPacket above?
  int InsertPacketInternal(const WebRtcRTPHeader& rtp_header,
                           const uint8_t* payload,
                           int length_bytes,
                           uint32_t receive_timestamp,
                           bool is_sync_packet)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Delivers 10 ms of audio data. The data is written to |output|, which can
  // hold (at least) |max_length| elements. The number of channels that were
  // written to the output is provided in the output variable |num_channels|,
  // and each channel contains |samples_per_channel| elements. If more than one
  // channel is written, the samples are interleaved.
  // Returns 0 on success, otherwise an error code.
  int GetAudioInternal(size_t max_length,
                       int16_t* output,
                       int* samples_per_channel,
                       int* num_channels) EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Provides a decision to the GetAudioInternal method. The decision what to
  // do is written to |operation|. Packets to decode are written to
  // |packet_list|, and a DTMF event to play is written to |dtmf_event|. When
  // DTMF should be played, |play_dtmf| is set to true by the method.
  // Returns 0 on success, otherwise an error code.
  int GetDecision(Operations* operation,
                  PacketList* packet_list,
                  DtmfEvent* dtmf_event,
                  bool* play_dtmf) EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Decodes the speech packets in |packet_list|, and writes the results to
  // |decoded_buffer|, which is allocated to hold |decoded_buffer_length|
  // elements. The length of the decoded data is written to |decoded_length|.
  // The speech type -- speech or (codec-internal) comfort noise -- is written
  // to |speech_type|. If |packet_list| contains any SID frames for RFC 3389
  // comfort noise, those are not decoded.
  int Decode(PacketList* packet_list,
             Operations* operation,
             int* decoded_length,
             AudioDecoder::SpeechType* speech_type)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Sub-method to Decode(). Performs the actual decoding.
  int DecodeLoop(PacketList* packet_list,
                 Operations* operation,
                 AudioDecoder* decoder,
                 int* decoded_length,
                 AudioDecoder::SpeechType* speech_type)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Sub-method which calls the Normal class to perform the normal operation.
  void DoNormal(const int16_t* decoded_buffer,
                size_t decoded_length,
                AudioDecoder::SpeechType speech_type,
                bool play_dtmf) EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Sub-method which calls the Merge class to perform the merge operation.
  void DoMerge(int16_t* decoded_buffer,
               size_t decoded_length,
               AudioDecoder::SpeechType speech_type,
               bool play_dtmf) EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Sub-method which calls the Expand class to perform the expand operation.
  int DoExpand(bool play_dtmf) EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Sub-method which calls the Accelerate class to perform the accelerate
  // operation.
  int DoAccelerate(int16_t* decoded_buffer,
                   size_t decoded_length,
                   AudioDecoder::SpeechType speech_type,
                   bool play_dtmf) EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Sub-method which calls the PreemptiveExpand class to perform the
  // preemtive expand operation.
  int DoPreemptiveExpand(int16_t* decoded_buffer,
                         size_t decoded_length,
                         AudioDecoder::SpeechType speech_type,
                         bool play_dtmf) EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Sub-method which calls the ComfortNoise class to generate RFC 3389 comfort
  // noise. |packet_list| can either contain one SID frame to update the
  // noise parameters, or no payload at all, in which case the previously
  // received parameters are used.
  int DoRfc3389Cng(PacketList* packet_list, bool play_dtmf)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Calls the audio decoder to generate codec-internal comfort noise when
  // no packet was received.
  void DoCodecInternalCng() EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Calls the DtmfToneGenerator class to generate DTMF tones.
  int DoDtmf(const DtmfEvent& dtmf_event, bool* play_dtmf)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Produces packet-loss concealment using alternative methods. If the codec
  // has an internal PLC, it is called to generate samples. Otherwise, the
  // method performs zero-stuffing.
  void DoAlternativePlc(bool increase_timestamp)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Overdub DTMF on top of |output|.
  int DtmfOverdub(const DtmfEvent& dtmf_event,
                  size_t num_channels,
                  int16_t* output) const EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Extracts packets from |packet_buffer_| to produce at least
  // |required_samples| samples. The packets are inserted into |packet_list|.
  // Returns the number of samples that the packets in the list will produce, or
  // -1 in case of an error.
  int ExtractPackets(int required_samples, PacketList* packet_list)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Resets various variables and objects to new values based on the sample rate
  // |fs_hz| and |channels| number audio channels.
  void SetSampleRateAndChannels(int fs_hz, size_t channels)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Returns the output type for the audio produced by the latest call to
  // GetAudio().
  NetEqOutputType LastOutputType() EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Updates Expand and Merge.
  virtual void UpdatePlcComponents(int fs_hz, size_t channels)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  // Creates DecisionLogic object for the given mode.
  virtual void CreateDecisionLogic(NetEqPlayoutMode mode)
      EXCLUSIVE_LOCKS_REQUIRED(crit_sect_);

  const scoped_ptr<CriticalSectionWrapper> crit_sect_;
  const scoped_ptr<BufferLevelFilter> buffer_level_filter_
      GUARDED_BY(crit_sect_);
  const scoped_ptr<DecoderDatabase> decoder_database_ GUARDED_BY(crit_sect_);
  const scoped_ptr<DelayManager> delay_manager_ GUARDED_BY(crit_sect_);
  const scoped_ptr<DelayPeakDetector> delay_peak_detector_
      GUARDED_BY(crit_sect_);
  const scoped_ptr<DtmfBuffer> dtmf_buffer_ GUARDED_BY(crit_sect_);
  const scoped_ptr<DtmfToneGenerator> dtmf_tone_generator_
      GUARDED_BY(crit_sect_);
  const scoped_ptr<PacketBuffer> packet_buffer_ GUARDED_BY(crit_sect_);
  const scoped_ptr<PayloadSplitter> payload_splitter_ GUARDED_BY(crit_sect_);
  const scoped_ptr<TimestampScaler> timestamp_scaler_ GUARDED_BY(crit_sect_);
  const scoped_ptr<PostDecodeVad> vad_ GUARDED_BY(crit_sect_);
  const scoped_ptr<ExpandFactory> expand_factory_ GUARDED_BY(crit_sect_);
  const scoped_ptr<AccelerateFactory> accelerate_factory_
      GUARDED_BY(crit_sect_);
  const scoped_ptr<PreemptiveExpandFactory> preemptive_expand_factory_
      GUARDED_BY(crit_sect_);

  scoped_ptr<BackgroundNoise> background_noise_ GUARDED_BY(crit_sect_);
  scoped_ptr<DecisionLogic> decision_logic_ GUARDED_BY(crit_sect_);
  scoped_ptr<AudioMultiVector> algorithm_buffer_ GUARDED_BY(crit_sect_);
  scoped_ptr<SyncBuffer> sync_buffer_ GUARDED_BY(crit_sect_);
  scoped_ptr<Expand> expand_ GUARDED_BY(crit_sect_);
  scoped_ptr<Normal> normal_ GUARDED_BY(crit_sect_);
  scoped_ptr<Merge> merge_ GUARDED_BY(crit_sect_);
  scoped_ptr<Accelerate> accelerate_ GUARDED_BY(crit_sect_);
  scoped_ptr<PreemptiveExpand> preemptive_expand_ GUARDED_BY(crit_sect_);
  RandomVector random_vector_ GUARDED_BY(crit_sect_);
  scoped_ptr<ComfortNoise> comfort_noise_ GUARDED_BY(crit_sect_);
  Rtcp rtcp_ GUARDED_BY(crit_sect_);
  StatisticsCalculator stats_ GUARDED_BY(crit_sect_);
  int fs_hz_ GUARDED_BY(crit_sect_);
  int fs_mult_ GUARDED_BY(crit_sect_);
  int output_size_samples_ GUARDED_BY(crit_sect_);
  int decoder_frame_length_ GUARDED_BY(crit_sect_);
  Modes last_mode_ GUARDED_BY(crit_sect_);
  scoped_ptr<int16_t[]> mute_factor_array_ GUARDED_BY(crit_sect_);
  size_t decoded_buffer_length_ GUARDED_BY(crit_sect_);
  scoped_ptr<int16_t[]> decoded_buffer_ GUARDED_BY(crit_sect_);
  uint32_t playout_timestamp_ GUARDED_BY(crit_sect_);
  bool new_codec_ GUARDED_BY(crit_sect_);
  uint32_t timestamp_ GUARDED_BY(crit_sect_);
  bool reset_decoder_ GUARDED_BY(crit_sect_);
  uint8_t current_rtp_payload_type_ GUARDED_BY(crit_sect_);
  uint8_t current_cng_rtp_payload_type_ GUARDED_BY(crit_sect_);
  uint32_t ssrc_ GUARDED_BY(crit_sect_);
  bool first_packet_ GUARDED_BY(crit_sect_);
  int error_code_ GUARDED_BY(crit_sect_);  // Store last error code.
  int decoder_error_code_ GUARDED_BY(crit_sect_);
  const BackgroundNoiseMode background_noise_mode_ GUARDED_BY(crit_sect_);

  // These values are used by NACK module to estimate time-to-play of
  // a missing packet. Occasionally, NetEq might decide to decode more
  // than one packet. Therefore, these values store sequence number and
  // timestamp of the first packet pulled from the packet buffer. In
  // such cases, these values do not exactly represent the sequence number
  // or timestamp associated with a 10ms audio pulled from NetEq. NACK
  // module is designed to compensate for this.
  int decoded_packet_sequence_number_ GUARDED_BY(crit_sect_);
  uint32_t decoded_packet_timestamp_ GUARDED_BY(crit_sect_);

 private:
  DISALLOW_COPY_AND_ASSIGN(NetEqImpl);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_NETEQ_IMPL_H_
