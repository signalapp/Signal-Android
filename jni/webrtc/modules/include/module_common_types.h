/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_INCLUDE_MODULE_COMMON_TYPES_H_
#define WEBRTC_MODULES_INCLUDE_MODULE_COMMON_TYPES_H_

#include <assert.h>
#include <string.h>  // memcpy

#include <algorithm>
#include <limits>

#include "webrtc/base/constructormagic.h"
#include "webrtc/common_types.h"
#include "webrtc/common_video/rotation.h"
#include "webrtc/typedefs.h"

namespace webrtc {

struct RTPAudioHeader {
  uint8_t numEnergy;                  // number of valid entries in arrOfEnergy
  uint8_t arrOfEnergy[kRtpCsrcSize];  // one energy byte (0-9) per channel
  bool isCNG;                         // is this CNG
  size_t channel;                     // number of channels 2 = stereo
};

const int16_t kNoPictureId = -1;
const int16_t kMaxOneBytePictureId = 0x7F;    // 7 bits
const int16_t kMaxTwoBytePictureId = 0x7FFF;  // 15 bits
const int16_t kNoTl0PicIdx = -1;
const uint8_t kNoTemporalIdx = 0xFF;
const uint8_t kNoSpatialIdx = 0xFF;
const uint8_t kNoGofIdx = 0xFF;
const uint8_t kNumVp9Buffers = 8;
const size_t kMaxVp9RefPics = 3;
const size_t kMaxVp9FramesInGof = 0xFF;  // 8 bits
const size_t kMaxVp9NumberOfSpatialLayers = 8;
const int kNoKeyIdx = -1;

struct RTPVideoHeaderVP8 {
  void InitRTPVideoHeaderVP8() {
    nonReference = false;
    pictureId = kNoPictureId;
    tl0PicIdx = kNoTl0PicIdx;
    temporalIdx = kNoTemporalIdx;
    layerSync = false;
    keyIdx = kNoKeyIdx;
    partitionId = 0;
    beginningOfPartition = false;
  }

  bool nonReference;          // Frame is discardable.
  int16_t pictureId;          // Picture ID index, 15 bits;
                              // kNoPictureId if PictureID does not exist.
  int16_t tl0PicIdx;          // TL0PIC_IDX, 8 bits;
                              // kNoTl0PicIdx means no value provided.
  uint8_t temporalIdx;        // Temporal layer index, or kNoTemporalIdx.
  bool layerSync;             // This frame is a layer sync frame.
                              // Disabled if temporalIdx == kNoTemporalIdx.
  int keyIdx;                 // 5 bits; kNoKeyIdx means not used.
  int partitionId;            // VP8 partition ID
  bool beginningOfPartition;  // True if this packet is the first
                              // in a VP8 partition. Otherwise false
};

enum TemporalStructureMode {
  kTemporalStructureMode1,  // 1 temporal layer structure - i.e., IPPP...
  kTemporalStructureMode2,  // 2 temporal layers 01...
  kTemporalStructureMode3,  // 3 temporal layers 0212...
  kTemporalStructureMode4   // 3 temporal layers 02120212...
};

struct GofInfoVP9 {
  void SetGofInfoVP9(TemporalStructureMode tm) {
    switch (tm) {
      case kTemporalStructureMode1:
        num_frames_in_gof = 1;
        temporal_idx[0] = 0;
        temporal_up_switch[0] = false;
        num_ref_pics[0] = 1;
        pid_diff[0][0] = 1;
        break;
      case kTemporalStructureMode2:
        num_frames_in_gof = 2;
        temporal_idx[0] = 0;
        temporal_up_switch[0] = false;
        num_ref_pics[0] = 1;
        pid_diff[0][0] = 2;

        temporal_idx[1] = 1;
        temporal_up_switch[1] = true;
        num_ref_pics[1] = 1;
        pid_diff[1][0] = 1;
        break;
      case kTemporalStructureMode3:
        num_frames_in_gof = 4;
        temporal_idx[0] = 0;
        temporal_up_switch[0] = false;
        num_ref_pics[0] = 1;
        pid_diff[0][0] = 4;

        temporal_idx[1] = 2;
        temporal_up_switch[1] = true;
        num_ref_pics[1] = 1;
        pid_diff[1][0] = 1;

        temporal_idx[2] = 1;
        temporal_up_switch[2] = true;
        num_ref_pics[2] = 1;
        pid_diff[2][0] = 2;

        temporal_idx[3] = 2;
        temporal_up_switch[3] = false;
        num_ref_pics[3] = 2;
        pid_diff[3][0] = 1;
        pid_diff[3][1] = 2;
        break;
      case kTemporalStructureMode4:
        num_frames_in_gof = 8;
        temporal_idx[0] = 0;
        temporal_up_switch[0] = false;
        num_ref_pics[0] = 1;
        pid_diff[0][0] = 4;

        temporal_idx[1] = 2;
        temporal_up_switch[1] = true;
        num_ref_pics[1] = 1;
        pid_diff[1][0] = 1;

        temporal_idx[2] = 1;
        temporal_up_switch[2] = true;
        num_ref_pics[2] = 1;
        pid_diff[2][0] = 2;

        temporal_idx[3] = 2;
        temporal_up_switch[3] = false;
        num_ref_pics[3] = 2;
        pid_diff[3][0] = 1;
        pid_diff[3][1] = 2;

        temporal_idx[4] = 0;
        temporal_up_switch[0] = false;
        num_ref_pics[4] = 1;
        pid_diff[4][0] = 4;

        temporal_idx[5] = 2;
        temporal_up_switch[1] = false;
        num_ref_pics[5] = 2;
        pid_diff[5][0] = 1;
        pid_diff[5][1] = 2;

        temporal_idx[6] = 1;
        temporal_up_switch[2] = false;
        num_ref_pics[6] = 2;
        pid_diff[6][0] = 2;
        pid_diff[6][1] = 4;

        temporal_idx[7] = 2;
        temporal_up_switch[3] = false;
        num_ref_pics[7] = 2;
        pid_diff[7][0] = 1;
        pid_diff[7][1] = 2;
        break;
      default:
        assert(false);
    }
  }

  void CopyGofInfoVP9(const GofInfoVP9& src) {
    num_frames_in_gof = src.num_frames_in_gof;
    for (size_t i = 0; i < num_frames_in_gof; ++i) {
      temporal_idx[i] = src.temporal_idx[i];
      temporal_up_switch[i] = src.temporal_up_switch[i];
      num_ref_pics[i] = src.num_ref_pics[i];
      for (uint8_t r = 0; r < num_ref_pics[i]; ++r) {
        pid_diff[i][r] = src.pid_diff[i][r];
      }
    }
  }

  size_t num_frames_in_gof;
  uint8_t temporal_idx[kMaxVp9FramesInGof];
  bool temporal_up_switch[kMaxVp9FramesInGof];
  uint8_t num_ref_pics[kMaxVp9FramesInGof];
  uint8_t pid_diff[kMaxVp9FramesInGof][kMaxVp9RefPics];
  uint16_t pid_start;
};

struct RTPVideoHeaderVP9 {
  void InitRTPVideoHeaderVP9() {
    inter_pic_predicted = false;
    flexible_mode = false;
    beginning_of_frame = false;
    end_of_frame = false;
    ss_data_available = false;
    picture_id = kNoPictureId;
    max_picture_id = kMaxTwoBytePictureId;
    tl0_pic_idx = kNoTl0PicIdx;
    temporal_idx = kNoTemporalIdx;
    spatial_idx = kNoSpatialIdx;
    temporal_up_switch = false;
    inter_layer_predicted = false;
    gof_idx = kNoGofIdx;
    num_ref_pics = 0;
    num_spatial_layers = 1;
  }

  bool inter_pic_predicted;  // This layer frame is dependent on previously
                             // coded frame(s).
  bool flexible_mode;        // This frame is in flexible mode.
  bool beginning_of_frame;   // True if this packet is the first in a VP9 layer
                             // frame.
  bool end_of_frame;  // True if this packet is the last in a VP9 layer frame.
  bool ss_data_available;  // True if SS data is available in this payload
                           // descriptor.
  int16_t picture_id;      // PictureID index, 15 bits;
                           // kNoPictureId if PictureID does not exist.
  int16_t max_picture_id;  // Maximum picture ID index; either 0x7F or 0x7FFF;
  int16_t tl0_pic_idx;     // TL0PIC_IDX, 8 bits;
                           // kNoTl0PicIdx means no value provided.
  uint8_t temporal_idx;    // Temporal layer index, or kNoTemporalIdx.
  uint8_t spatial_idx;     // Spatial layer index, or kNoSpatialIdx.
  bool temporal_up_switch;  // True if upswitch to higher frame rate is possible
                            // starting from this frame.
  bool inter_layer_predicted;  // Frame is dependent on directly lower spatial
                               // layer frame.

  uint8_t gof_idx;  // Index to predefined temporal frame info in SS data.

  uint8_t num_ref_pics;  // Number of reference pictures used by this layer
                         // frame.
  uint8_t pid_diff[kMaxVp9RefPics];  // P_DIFF signaled to derive the PictureID
                                     // of the reference pictures.
  int16_t ref_picture_id[kMaxVp9RefPics];  // PictureID of reference pictures.

  // SS data.
  size_t num_spatial_layers;  // Always populated.
  bool spatial_layer_resolution_present;
  uint16_t width[kMaxVp9NumberOfSpatialLayers];
  uint16_t height[kMaxVp9NumberOfSpatialLayers];
  GofInfoVP9 gof;
};

// The packetization types that we support: single, aggregated, and fragmented.
enum H264PacketizationTypes {
  kH264SingleNalu,  // This packet contains a single NAL unit.
  kH264StapA,       // This packet contains STAP-A (single time
                    // aggregation) packets. If this packet has an
                    // associated NAL unit type, it'll be for the
                    // first such aggregated packet.
  kH264FuA,         // This packet contains a FU-A (fragmentation
                    // unit) packet, meaning it is a part of a frame
                    // that was too large to fit into a single packet.
};

struct RTPVideoHeaderH264 {
  uint8_t nalu_type;  // The NAL unit type. If this is a header for a
                      // fragmented packet, it's the NAL unit type of
                      // the original data. If this is the header for an
                      // aggregated packet, it's the NAL unit type of
                      // the first NAL unit in the packet.
  H264PacketizationTypes packetization_type;
};

union RTPVideoTypeHeader {
  RTPVideoHeaderVP8 VP8;
  RTPVideoHeaderVP9 VP9;
  RTPVideoHeaderH264 H264;
};

enum RtpVideoCodecTypes {
  kRtpVideoNone,
  kRtpVideoGeneric,
  kRtpVideoVp8,
  kRtpVideoVp9,
  kRtpVideoH264
};
// Since RTPVideoHeader is used as a member of a union, it can't have a
// non-trivial default constructor.
struct RTPVideoHeader {
  uint16_t width;  // size
  uint16_t height;
  VideoRotation rotation;

  PlayoutDelay playout_delay;

  bool isFirstPacket;    // first packet in frame
  uint8_t simulcastIdx;  // Index if the simulcast encoder creating
                         // this frame, 0 if not using simulcast.
  RtpVideoCodecTypes codec;
  RTPVideoTypeHeader codecHeader;
};
union RTPTypeHeader {
  RTPAudioHeader Audio;
  RTPVideoHeader Video;
};

struct WebRtcRTPHeader {
  RTPHeader header;
  FrameType frameType;
  RTPTypeHeader type;
  // NTP time of the capture time in local timebase in milliseconds.
  int64_t ntp_time_ms;
};

class RTPFragmentationHeader {
 public:
  RTPFragmentationHeader()
      : fragmentationVectorSize(0),
        fragmentationOffset(NULL),
        fragmentationLength(NULL),
        fragmentationTimeDiff(NULL),
        fragmentationPlType(NULL) {};

  ~RTPFragmentationHeader() {
    delete[] fragmentationOffset;
    delete[] fragmentationLength;
    delete[] fragmentationTimeDiff;
    delete[] fragmentationPlType;
  }

  void CopyFrom(const RTPFragmentationHeader& src) {
    if (this == &src) {
      return;
    }

    if (src.fragmentationVectorSize != fragmentationVectorSize) {
      // new size of vectors

      // delete old
      delete[] fragmentationOffset;
      fragmentationOffset = NULL;
      delete[] fragmentationLength;
      fragmentationLength = NULL;
      delete[] fragmentationTimeDiff;
      fragmentationTimeDiff = NULL;
      delete[] fragmentationPlType;
      fragmentationPlType = NULL;

      if (src.fragmentationVectorSize > 0) {
        // allocate new
        if (src.fragmentationOffset) {
          fragmentationOffset = new size_t[src.fragmentationVectorSize];
        }
        if (src.fragmentationLength) {
          fragmentationLength = new size_t[src.fragmentationVectorSize];
        }
        if (src.fragmentationTimeDiff) {
          fragmentationTimeDiff = new uint16_t[src.fragmentationVectorSize];
        }
        if (src.fragmentationPlType) {
          fragmentationPlType = new uint8_t[src.fragmentationVectorSize];
        }
      }
      // set new size
      fragmentationVectorSize = src.fragmentationVectorSize;
    }

    if (src.fragmentationVectorSize > 0) {
      // copy values
      if (src.fragmentationOffset) {
        memcpy(fragmentationOffset, src.fragmentationOffset,
               src.fragmentationVectorSize * sizeof(size_t));
      }
      if (src.fragmentationLength) {
        memcpy(fragmentationLength, src.fragmentationLength,
               src.fragmentationVectorSize * sizeof(size_t));
      }
      if (src.fragmentationTimeDiff) {
        memcpy(fragmentationTimeDiff, src.fragmentationTimeDiff,
               src.fragmentationVectorSize * sizeof(uint16_t));
      }
      if (src.fragmentationPlType) {
        memcpy(fragmentationPlType, src.fragmentationPlType,
               src.fragmentationVectorSize * sizeof(uint8_t));
      }
    }
  }

  void VerifyAndAllocateFragmentationHeader(const size_t size) {
    assert(size <= std::numeric_limits<uint16_t>::max());
    const uint16_t size16 = static_cast<uint16_t>(size);
    if (fragmentationVectorSize < size16) {
      uint16_t oldVectorSize = fragmentationVectorSize;
      {
        // offset
        size_t* oldOffsets = fragmentationOffset;
        fragmentationOffset = new size_t[size16];
        memset(fragmentationOffset + oldVectorSize, 0,
               sizeof(size_t) * (size16 - oldVectorSize));
        // copy old values
        memcpy(fragmentationOffset, oldOffsets,
               sizeof(size_t) * oldVectorSize);
        delete[] oldOffsets;
      }
      // length
      {
        size_t* oldLengths = fragmentationLength;
        fragmentationLength = new size_t[size16];
        memset(fragmentationLength + oldVectorSize, 0,
               sizeof(size_t) * (size16 - oldVectorSize));
        memcpy(fragmentationLength, oldLengths,
               sizeof(size_t) * oldVectorSize);
        delete[] oldLengths;
      }
      // time diff
      {
        uint16_t* oldTimeDiffs = fragmentationTimeDiff;
        fragmentationTimeDiff = new uint16_t[size16];
        memset(fragmentationTimeDiff + oldVectorSize, 0,
               sizeof(uint16_t) * (size16 - oldVectorSize));
        memcpy(fragmentationTimeDiff, oldTimeDiffs,
               sizeof(uint16_t) * oldVectorSize);
        delete[] oldTimeDiffs;
      }
      // payload type
      {
        uint8_t* oldTimePlTypes = fragmentationPlType;
        fragmentationPlType = new uint8_t[size16];
        memset(fragmentationPlType + oldVectorSize, 0,
               sizeof(uint8_t) * (size16 - oldVectorSize));
        memcpy(fragmentationPlType, oldTimePlTypes,
               sizeof(uint8_t) * oldVectorSize);
        delete[] oldTimePlTypes;
      }
      fragmentationVectorSize = size16;
    }
  }

  uint16_t fragmentationVectorSize;  // Number of fragmentations
  size_t* fragmentationOffset;       // Offset of pointer to data for each
                                     // fragmentation
  size_t* fragmentationLength;       // Data size for each fragmentation
  uint16_t* fragmentationTimeDiff;   // Timestamp difference relative "now" for
                                     // each fragmentation
  uint8_t* fragmentationPlType;      // Payload type of each fragmentation

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(RTPFragmentationHeader);
};

struct RTCPVoIPMetric {
  // RFC 3611 4.7
  uint8_t lossRate;
  uint8_t discardRate;
  uint8_t burstDensity;
  uint8_t gapDensity;
  uint16_t burstDuration;
  uint16_t gapDuration;
  uint16_t roundTripDelay;
  uint16_t endSystemDelay;
  uint8_t signalLevel;
  uint8_t noiseLevel;
  uint8_t RERL;
  uint8_t Gmin;
  uint8_t Rfactor;
  uint8_t extRfactor;
  uint8_t MOSLQ;
  uint8_t MOSCQ;
  uint8_t RXconfig;
  uint16_t JBnominal;
  uint16_t JBmax;
  uint16_t JBabsMax;
};

// Types for the FEC packet masks. The type |kFecMaskRandom| is based on a
// random loss model. The type |kFecMaskBursty| is based on a bursty/consecutive
// loss model. The packet masks are defined in
// modules/rtp_rtcp/fec_private_tables_random(bursty).h
enum FecMaskType {
  kFecMaskRandom,
  kFecMaskBursty,
};

// Struct containing forward error correction settings.
struct FecProtectionParams {
  int fec_rate;
  int max_fec_frames;
  FecMaskType fec_mask_type;
};

// Interface used by the CallStats class to distribute call statistics.
// Callbacks will be triggered as soon as the class has been registered to a
// CallStats object using RegisterStatsObserver.
class CallStatsObserver {
 public:
  virtual void OnRttUpdate(int64_t avg_rtt_ms, int64_t max_rtt_ms) = 0;

  virtual ~CallStatsObserver() {}
};

/* This class holds up to 60 ms of super-wideband (32 kHz) stereo audio. It
 * allows for adding and subtracting frames while keeping track of the resulting
 * states.
 *
 * Notes
 * - The total number of samples in |data_| is
 *   samples_per_channel_ * num_channels_
 *
 * - Stereo data is interleaved starting with the left channel.
 *
 * - The +operator assume that you would never add exactly opposite frames when
 *   deciding the resulting state. To do this use the -operator.
 */
class AudioFrame {
 public:
  // Stereo, 32 kHz, 60 ms (2 * 32 * 60)
  enum : size_t {
    kMaxDataSizeSamples = 3840
  };

  enum VADActivity {
    kVadActive = 0,
    kVadPassive = 1,
    kVadUnknown = 2
  };
  enum SpeechType {
    kNormalSpeech = 0,
    kPLC = 1,
    kCNG = 2,
    kPLCCNG = 3,
    kUndefined = 4
  };

  AudioFrame();

  // Resets all members to their default state (except does not modify the
  // contents of |data_|).
  void Reset();

  void UpdateFrame(int id, uint32_t timestamp, const int16_t* data,
                   size_t samples_per_channel, int sample_rate_hz,
                   SpeechType speech_type, VADActivity vad_activity,
                   size_t num_channels = 1);

  void CopyFrom(const AudioFrame& src);

  void Mute();

  AudioFrame& operator>>=(const int rhs);
  AudioFrame& operator+=(const AudioFrame& rhs);

  int id_;
  // RTP timestamp of the first sample in the AudioFrame.
  uint32_t timestamp_;
  // Time since the first frame in milliseconds.
  // -1 represents an uninitialized value.
  int64_t elapsed_time_ms_;
  // NTP time of the estimated capture time in local timebase in milliseconds.
  // -1 represents an uninitialized value.
  int64_t ntp_time_ms_;
  int16_t data_[kMaxDataSizeSamples];
  size_t samples_per_channel_;
  int sample_rate_hz_;
  size_t num_channels_;
  SpeechType speech_type_;
  VADActivity vad_activity_;

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioFrame);
};

// TODO(henrik.lundin) Can we remove the call to data_()?
// See https://bugs.chromium.org/p/webrtc/issues/detail?id=5647.
inline AudioFrame::AudioFrame()
    : data_() {
  Reset();
}

inline void AudioFrame::Reset() {
  id_ = -1;
  // TODO(wu): Zero is a valid value for |timestamp_|. We should initialize
  // to an invalid value, or add a new member to indicate invalidity.
  timestamp_ = 0;
  elapsed_time_ms_ = -1;
  ntp_time_ms_ = -1;
  samples_per_channel_ = 0;
  sample_rate_hz_ = 0;
  num_channels_ = 0;
  speech_type_ = kUndefined;
  vad_activity_ = kVadUnknown;
}

inline void AudioFrame::UpdateFrame(int id,
                                    uint32_t timestamp,
                                    const int16_t* data,
                                    size_t samples_per_channel,
                                    int sample_rate_hz,
                                    SpeechType speech_type,
                                    VADActivity vad_activity,
                                    size_t num_channels) {
  id_ = id;
  timestamp_ = timestamp;
  samples_per_channel_ = samples_per_channel;
  sample_rate_hz_ = sample_rate_hz;
  speech_type_ = speech_type;
  vad_activity_ = vad_activity;
  num_channels_ = num_channels;

  const size_t length = samples_per_channel * num_channels;
  assert(length <= kMaxDataSizeSamples);
  if (data != NULL) {
    memcpy(data_, data, sizeof(int16_t) * length);
  } else {
    memset(data_, 0, sizeof(int16_t) * length);
  }
}

inline void AudioFrame::CopyFrom(const AudioFrame& src) {
  if (this == &src) return;

  id_ = src.id_;
  timestamp_ = src.timestamp_;
  elapsed_time_ms_ = src.elapsed_time_ms_;
  ntp_time_ms_ = src.ntp_time_ms_;
  samples_per_channel_ = src.samples_per_channel_;
  sample_rate_hz_ = src.sample_rate_hz_;
  speech_type_ = src.speech_type_;
  vad_activity_ = src.vad_activity_;
  num_channels_ = src.num_channels_;

  const size_t length = samples_per_channel_ * num_channels_;
  assert(length <= kMaxDataSizeSamples);
  memcpy(data_, src.data_, sizeof(int16_t) * length);
}

inline void AudioFrame::Mute() {
  memset(data_, 0, samples_per_channel_ * num_channels_ * sizeof(int16_t));
}

inline AudioFrame& AudioFrame::operator>>=(const int rhs) {
  assert((num_channels_ > 0) && (num_channels_ < 3));
  if ((num_channels_ > 2) || (num_channels_ < 1)) return *this;

  for (size_t i = 0; i < samples_per_channel_ * num_channels_; i++) {
    data_[i] = static_cast<int16_t>(data_[i] >> rhs);
  }
  return *this;
}

namespace {
inline int16_t ClampToInt16(int32_t input) {
  if (input < -0x00008000) {
    return -0x8000;
  } else if (input > 0x00007FFF) {
    return 0x7FFF;
  } else {
    return static_cast<int16_t>(input);
  }
}
}

inline AudioFrame& AudioFrame::operator+=(const AudioFrame& rhs) {
  // Sanity check
  assert((num_channels_ > 0) && (num_channels_ < 3));
  if ((num_channels_ > 2) || (num_channels_ < 1)) return *this;
  if (num_channels_ != rhs.num_channels_) return *this;

  bool noPrevData = false;
  if (samples_per_channel_ != rhs.samples_per_channel_) {
    if (samples_per_channel_ == 0) {
      // special case we have no data to start with
      samples_per_channel_ = rhs.samples_per_channel_;
      noPrevData = true;
    } else {
      return *this;
    }
  }

  if ((vad_activity_ == kVadActive) || rhs.vad_activity_ == kVadActive) {
    vad_activity_ = kVadActive;
  } else if (vad_activity_ == kVadUnknown || rhs.vad_activity_ == kVadUnknown) {
    vad_activity_ = kVadUnknown;
  }

  if (speech_type_ != rhs.speech_type_) speech_type_ = kUndefined;

  if (noPrevData) {
    memcpy(data_, rhs.data_,
           sizeof(int16_t) * rhs.samples_per_channel_ * num_channels_);
  } else {
    // IMPROVEMENT this can be done very fast in assembly
    for (size_t i = 0; i < samples_per_channel_ * num_channels_; i++) {
      int32_t wrap_guard =
          static_cast<int32_t>(data_[i]) + static_cast<int32_t>(rhs.data_[i]);
      data_[i] = ClampToInt16(wrap_guard);
    }
  }
  return *this;
}

inline bool IsNewerSequenceNumber(uint16_t sequence_number,
                                  uint16_t prev_sequence_number) {
  // Distinguish between elements that are exactly 0x8000 apart.
  // If s1>s2 and |s1-s2| = 0x8000: IsNewer(s1,s2)=true, IsNewer(s2,s1)=false
  // rather than having IsNewer(s1,s2) = IsNewer(s2,s1) = false.
  if (static_cast<uint16_t>(sequence_number - prev_sequence_number) == 0x8000) {
    return sequence_number > prev_sequence_number;
  }
  return sequence_number != prev_sequence_number &&
         static_cast<uint16_t>(sequence_number - prev_sequence_number) < 0x8000;
}

inline bool IsNewerTimestamp(uint32_t timestamp, uint32_t prev_timestamp) {
  // Distinguish between elements that are exactly 0x80000000 apart.
  // If t1>t2 and |t1-t2| = 0x80000000: IsNewer(t1,t2)=true,
  // IsNewer(t2,t1)=false
  // rather than having IsNewer(t1,t2) = IsNewer(t2,t1) = false.
  if (static_cast<uint32_t>(timestamp - prev_timestamp) == 0x80000000) {
    return timestamp > prev_timestamp;
  }
  return timestamp != prev_timestamp &&
         static_cast<uint32_t>(timestamp - prev_timestamp) < 0x80000000;
}

inline uint16_t LatestSequenceNumber(uint16_t sequence_number1,
                                     uint16_t sequence_number2) {
  return IsNewerSequenceNumber(sequence_number1, sequence_number2)
             ? sequence_number1
             : sequence_number2;
}

inline uint32_t LatestTimestamp(uint32_t timestamp1, uint32_t timestamp2) {
  return IsNewerTimestamp(timestamp1, timestamp2) ? timestamp1 : timestamp2;
}

// Utility class to unwrap a sequence number to a larger type, for easier
// handling large ranges. Note that sequence numbers will never be unwrapped
// to a negative value.
class SequenceNumberUnwrapper {
 public:
  SequenceNumberUnwrapper() : last_seq_(-1) {}

  // Get the unwrapped sequence, but don't update the internal state.
  int64_t UnwrapWithoutUpdate(uint16_t sequence_number) {
    if (last_seq_ == -1)
      return sequence_number;

    uint16_t cropped_last = static_cast<uint16_t>(last_seq_);
    int64_t delta = sequence_number - cropped_last;
    if (IsNewerSequenceNumber(sequence_number, cropped_last)) {
      if (delta < 0)
        delta += (1 << 16);  // Wrap forwards.
    } else if (delta > 0 && (last_seq_ + delta - (1 << 16)) >= 0) {
      // If sequence_number is older but delta is positive, this is a backwards
      // wrap-around. However, don't wrap backwards past 0 (unwrapped).
      delta -= (1 << 16);
    }

    return last_seq_ + delta;
  }

  // Only update the internal state to the specified last (unwrapped) sequence.
  void UpdateLast(int64_t last_sequence) { last_seq_ = last_sequence; }

  // Unwrap the sequence number and update the internal state.
  int64_t Unwrap(uint16_t sequence_number) {
    int64_t unwrapped = UnwrapWithoutUpdate(sequence_number);
    UpdateLast(unwrapped);
    return unwrapped;
  }

 private:
  int64_t last_seq_;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_INCLUDE_MODULE_COMMON_TYPES_H_
