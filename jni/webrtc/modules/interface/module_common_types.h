/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULE_COMMON_TYPES_H
#define MODULE_COMMON_TYPES_H

#include <assert.h>
#include <string.h>  // memcpy

#include <algorithm>

#include "webrtc/base/constructormagic.h"
#include "webrtc/common_types.h"
#include "webrtc/typedefs.h"

namespace webrtc {

struct RTPAudioHeader {
  uint8_t numEnergy;                  // number of valid entries in arrOfEnergy
  uint8_t arrOfEnergy[kRtpCsrcSize];  // one energy byte (0-9) per channel
  bool isCNG;                         // is this CNG
  uint8_t channel;                    // number of channels 2 = stereo
};

const int16_t kNoPictureId = -1;
const int16_t kNoTl0PicIdx = -1;
const uint8_t kNoTemporalIdx = 0xFF;
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

struct RTPVideoHeaderH264 {
  bool stap_a;
  bool single_nalu;
};

union RTPVideoTypeHeader {
  RTPVideoHeaderVP8 VP8;
  RTPVideoHeaderH264 H264;
};

enum RtpVideoCodecTypes {
  kRtpVideoNone,
  kRtpVideoGeneric,
  kRtpVideoVp8,
  kRtpVideoH264
};
struct RTPVideoHeader {
  uint16_t width;  // size
  uint16_t height;

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
          fragmentationOffset = new uint32_t[src.fragmentationVectorSize];
        }
        if (src.fragmentationLength) {
          fragmentationLength = new uint32_t[src.fragmentationVectorSize];
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
               src.fragmentationVectorSize * sizeof(uint32_t));
      }
      if (src.fragmentationLength) {
        memcpy(fragmentationLength, src.fragmentationLength,
               src.fragmentationVectorSize * sizeof(uint32_t));
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

  void VerifyAndAllocateFragmentationHeader(const uint16_t size) {
    if (fragmentationVectorSize < size) {
      uint16_t oldVectorSize = fragmentationVectorSize;
      {
        // offset
        uint32_t* oldOffsets = fragmentationOffset;
        fragmentationOffset = new uint32_t[size];
        memset(fragmentationOffset + oldVectorSize, 0,
               sizeof(uint32_t) * (size - oldVectorSize));
        // copy old values
        memcpy(fragmentationOffset, oldOffsets,
               sizeof(uint32_t) * oldVectorSize);
        delete[] oldOffsets;
      }
      // length
      {
        uint32_t* oldLengths = fragmentationLength;
        fragmentationLength = new uint32_t[size];
        memset(fragmentationLength + oldVectorSize, 0,
               sizeof(uint32_t) * (size - oldVectorSize));
        memcpy(fragmentationLength, oldLengths,
               sizeof(uint32_t) * oldVectorSize);
        delete[] oldLengths;
      }
      // time diff
      {
        uint16_t* oldTimeDiffs = fragmentationTimeDiff;
        fragmentationTimeDiff = new uint16_t[size];
        memset(fragmentationTimeDiff + oldVectorSize, 0,
               sizeof(uint16_t) * (size - oldVectorSize));
        memcpy(fragmentationTimeDiff, oldTimeDiffs,
               sizeof(uint16_t) * oldVectorSize);
        delete[] oldTimeDiffs;
      }
      // payload type
      {
        uint8_t* oldTimePlTypes = fragmentationPlType;
        fragmentationPlType = new uint8_t[size];
        memset(fragmentationPlType + oldVectorSize, 0,
               sizeof(uint8_t) * (size - oldVectorSize));
        memcpy(fragmentationPlType, oldTimePlTypes,
               sizeof(uint8_t) * oldVectorSize);
        delete[] oldTimePlTypes;
      }
      fragmentationVectorSize = size;
    }
  }

  uint16_t fragmentationVectorSize;  // Number of fragmentations
  uint32_t* fragmentationOffset;    // Offset of pointer to data for each fragm.
  uint32_t* fragmentationLength;    // Data size for each fragmentation
  uint16_t* fragmentationTimeDiff;  // Timestamp difference relative "now" for
                                    // each fragmentation
  uint8_t* fragmentationPlType;     // Payload type of each fragmentation

 private:
  DISALLOW_COPY_AND_ASSIGN(RTPFragmentationHeader);
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
  bool use_uep_protection;
  int max_fec_frames;
  FecMaskType fec_mask_type;
};

// Interface used by the CallStats class to distribute call statistics.
// Callbacks will be triggered as soon as the class has been registered to a
// CallStats object using RegisterStatsObserver.
class CallStatsObserver {
 public:
  virtual void OnRttUpdate(uint32_t rtt_ms) = 0;

  virtual ~CallStatsObserver() {}
};

// class describing a complete, or parts of an encoded frame.
class EncodedVideoData {
 public:
  EncodedVideoData()
      : payloadType(0),
        timeStamp(0),
        renderTimeMs(0),
        encodedWidth(0),
        encodedHeight(0),
        completeFrame(false),
        missingFrame(false),
        payloadData(NULL),
        payloadSize(0),
        bufferSize(0),
        fragmentationHeader(),
        frameType(kVideoFrameDelta),
        codec(kVideoCodecUnknown) {};

  EncodedVideoData(const EncodedVideoData& data) {
    payloadType = data.payloadType;
    timeStamp = data.timeStamp;
    renderTimeMs = data.renderTimeMs;
    encodedWidth = data.encodedWidth;
    encodedHeight = data.encodedHeight;
    completeFrame = data.completeFrame;
    missingFrame = data.missingFrame;
    payloadSize = data.payloadSize;
    fragmentationHeader.CopyFrom(data.fragmentationHeader);
    frameType = data.frameType;
    codec = data.codec;
    if (data.payloadSize > 0) {
      payloadData = new uint8_t[data.payloadSize];
      memcpy(payloadData, data.payloadData, data.payloadSize);
    } else {
      payloadData = NULL;
    }
  }

  ~EncodedVideoData() {
    delete[] payloadData;
  };

  EncodedVideoData& operator=(const EncodedVideoData& data) {
    if (this == &data) {
      return *this;
    }
    payloadType = data.payloadType;
    timeStamp = data.timeStamp;
    renderTimeMs = data.renderTimeMs;
    encodedWidth = data.encodedWidth;
    encodedHeight = data.encodedHeight;
    completeFrame = data.completeFrame;
    missingFrame = data.missingFrame;
    payloadSize = data.payloadSize;
    fragmentationHeader.CopyFrom(data.fragmentationHeader);
    frameType = data.frameType;
    codec = data.codec;
    if (data.payloadSize > 0) {
      delete[] payloadData;
      payloadData = new uint8_t[data.payloadSize];
      memcpy(payloadData, data.payloadData, data.payloadSize);
      bufferSize = data.payloadSize;
    }
    return *this;
  };
  void VerifyAndAllocate(const uint32_t size) {
    if (bufferSize < size) {
      uint8_t* oldPayload = payloadData;
      payloadData = new uint8_t[size];
      memcpy(payloadData, oldPayload, sizeof(uint8_t) * payloadSize);

      bufferSize = size;
      delete[] oldPayload;
    }
  }

  uint8_t payloadType;
  uint32_t timeStamp;
  int64_t renderTimeMs;
  uint32_t encodedWidth;
  uint32_t encodedHeight;
  bool completeFrame;
  bool missingFrame;
  uint8_t* payloadData;
  uint32_t payloadSize;
  uint32_t bufferSize;
  RTPFragmentationHeader fragmentationHeader;
  FrameType frameType;
  VideoCodecType codec;
};

struct VideoContentMetrics {
  VideoContentMetrics()
      : motion_magnitude(0.0f),
        spatial_pred_err(0.0f),
        spatial_pred_err_h(0.0f),
        spatial_pred_err_v(0.0f) {}

  void Reset() {
    motion_magnitude = 0.0f;
    spatial_pred_err = 0.0f;
    spatial_pred_err_h = 0.0f;
    spatial_pred_err_v = 0.0f;
  }
  float motion_magnitude;
  float spatial_pred_err;
  float spatial_pred_err_h;
  float spatial_pred_err_v;
};

/*************************************************
 *
 * VideoFrame class
 *
 * The VideoFrame class allows storing and
 * handling of video frames.
 *
 *
 *************************************************/
class VideoFrame {
 public:
  VideoFrame();
  ~VideoFrame();
  /**
  * Verifies that current allocated buffer size is larger than or equal to the
  * input size.
  * If the current buffer size is smaller, a new allocation is made and the old
  * buffer data
  * is copied to the new buffer.
  * Buffer size is updated to minimumSize.
  */
  int32_t VerifyAndAllocate(const uint32_t minimumSize);
  /**
  *    Update length of data buffer in frame. Function verifies that new length
  * is less or
  *    equal to allocated size.
  */
  int32_t SetLength(const uint32_t newLength);
  /*
  *    Swap buffer and size data
  */
  int32_t Swap(uint8_t*& newMemory, uint32_t& newLength, uint32_t& newSize);
  /*
  *    Swap buffer and size data
  */
  int32_t SwapFrame(VideoFrame& videoFrame);
  /**
  *    Copy buffer: If newLength is bigger than allocated size, a new buffer of
  * size length
  *    is allocated.
  */
  int32_t CopyFrame(const VideoFrame& videoFrame);
  /**
  *    Copy buffer: If newLength is bigger than allocated size, a new buffer of
  * size length
  *    is allocated.
  */
  int32_t CopyFrame(uint32_t length, const uint8_t* sourceBuffer);
  /**
  *    Delete VideoFrame and resets members to zero
  */
  void Free();
  /**
  *   Set frame timestamp (90kHz)
  */
  void SetTimeStamp(const uint32_t timeStamp) { _timeStamp = timeStamp; }
  /**
  *   Get pointer to frame buffer
  */
  uint8_t* Buffer() const { return _buffer; }

  uint8_t*& Buffer() { return _buffer; }

  /**
  *   Get allocated buffer size
  */
  uint32_t Size() const { return _bufferSize; }
  /**
  *   Get frame length
  */
  uint32_t Length() const { return _bufferLength; }
  /**
  *   Get frame timestamp (90kHz)
  */
  uint32_t TimeStamp() const { return _timeStamp; }
  /**
  *   Get frame width
  */
  uint32_t Width() const { return _width; }
  /**
  *   Get frame height
  */
  uint32_t Height() const { return _height; }
  /**
  *   Set frame width
  */
  void SetWidth(const uint32_t width) { _width = width; }
  /**
  *   Set frame height
  */
  void SetHeight(const uint32_t height) { _height = height; }
  /**
  *   Set render time in miliseconds
  */
  void SetRenderTime(const int64_t renderTimeMs) {
    _renderTimeMs = renderTimeMs;
  }
  /**
  *  Get render time in miliseconds
  */
  int64_t RenderTimeMs() const { return _renderTimeMs; }

 private:
  void Set(uint8_t* buffer, uint32_t size, uint32_t length, uint32_t timeStamp);

  uint8_t* _buffer;        // Pointer to frame buffer
  uint32_t _bufferSize;    // Allocated buffer size
  uint32_t _bufferLength;  // Length (in bytes) of buffer
  uint32_t _timeStamp;     // Timestamp of frame (90kHz)
  uint32_t _width;
  uint32_t _height;
  int64_t _renderTimeMs;
};  // end of VideoFrame class declaration

// inline implementation of VideoFrame class:
inline VideoFrame::VideoFrame()
    : _buffer(0),
      _bufferSize(0),
      _bufferLength(0),
      _timeStamp(0),
      _width(0),
      _height(0),
      _renderTimeMs(0) {
  //
}
inline VideoFrame::~VideoFrame() {
  if (_buffer) {
    delete[] _buffer;
    _buffer = NULL;
  }
}

inline int32_t VideoFrame::VerifyAndAllocate(const uint32_t minimumSize) {
  if (minimumSize < 1) {
    return -1;
  }
  if (minimumSize > _bufferSize) {
    // create buffer of sufficient size
    uint8_t* newBufferBuffer = new uint8_t[minimumSize];
    if (_buffer) {
      // copy old data
      memcpy(newBufferBuffer, _buffer, _bufferSize);
      delete[] _buffer;
    } else {
      memset(newBufferBuffer, 0, minimumSize * sizeof(uint8_t));
    }
    _buffer = newBufferBuffer;
    _bufferSize = minimumSize;
  }
  return 0;
}

inline int32_t VideoFrame::SetLength(const uint32_t newLength) {
  if (newLength > _bufferSize) {  // can't accomodate new value
    return -1;
  }
  _bufferLength = newLength;
  return 0;
}

inline int32_t VideoFrame::SwapFrame(VideoFrame& videoFrame) {
  uint32_t tmpTimeStamp = _timeStamp;
  uint32_t tmpWidth = _width;
  uint32_t tmpHeight = _height;
  int64_t tmpRenderTime = _renderTimeMs;

  _timeStamp = videoFrame._timeStamp;
  _width = videoFrame._width;
  _height = videoFrame._height;
  _renderTimeMs = videoFrame._renderTimeMs;

  videoFrame._timeStamp = tmpTimeStamp;
  videoFrame._width = tmpWidth;
  videoFrame._height = tmpHeight;
  videoFrame._renderTimeMs = tmpRenderTime;

  return Swap(videoFrame._buffer, videoFrame._bufferLength,
              videoFrame._bufferSize);
}

inline int32_t VideoFrame::Swap(uint8_t*& newMemory, uint32_t& newLength,
                                uint32_t& newSize) {
  uint8_t* tmpBuffer = _buffer;
  uint32_t tmpLength = _bufferLength;
  uint32_t tmpSize = _bufferSize;
  _buffer = newMemory;
  _bufferLength = newLength;
  _bufferSize = newSize;
  newMemory = tmpBuffer;
  newLength = tmpLength;
  newSize = tmpSize;
  return 0;
}

inline int32_t VideoFrame::CopyFrame(uint32_t length,
                                     const uint8_t* sourceBuffer) {
  if (length > _bufferSize) {
    int32_t ret = VerifyAndAllocate(length);
    if (ret < 0) {
      return ret;
    }
  }
  memcpy(_buffer, sourceBuffer, length);
  _bufferLength = length;
  return 0;
}

inline int32_t VideoFrame::CopyFrame(const VideoFrame& videoFrame) {
  if (CopyFrame(videoFrame.Length(), videoFrame.Buffer()) != 0) {
    return -1;
  }
  _timeStamp = videoFrame._timeStamp;
  _width = videoFrame._width;
  _height = videoFrame._height;
  _renderTimeMs = videoFrame._renderTimeMs;
  return 0;
}

inline void VideoFrame::Free() {
  _timeStamp = 0;
  _bufferLength = 0;
  _bufferSize = 0;
  _height = 0;
  _width = 0;
  _renderTimeMs = 0;

  if (_buffer) {
    delete[] _buffer;
    _buffer = NULL;
  }
}

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
  static const int kMaxDataSizeSamples = 3840;

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
  virtual ~AudioFrame() {}

  // Resets all members to their default state (except does not modify the
  // contents of |data_|).
  void Reset();

  // |interleaved_| is not changed by this method.
  void UpdateFrame(int id, uint32_t timestamp, const int16_t* data,
                   int samples_per_channel, int sample_rate_hz,
                   SpeechType speech_type, VADActivity vad_activity,
                   int num_channels = 1, uint32_t energy = -1);

  AudioFrame& Append(const AudioFrame& rhs);

  void CopyFrom(const AudioFrame& src);

  void Mute();

  AudioFrame& operator>>=(const int rhs);
  AudioFrame& operator+=(const AudioFrame& rhs);
  AudioFrame& operator-=(const AudioFrame& rhs);

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
  int samples_per_channel_;
  int sample_rate_hz_;
  int num_channels_;
  SpeechType speech_type_;
  VADActivity vad_activity_;
  // Note that there is no guarantee that |energy_| is correct. Any user of this
  // member must verify that the value is correct.
  // TODO(henrike) Remove |energy_|.
  // See https://code.google.com/p/webrtc/issues/detail?id=3315.
  uint32_t energy_;
  bool interleaved_;

 private:
  DISALLOW_COPY_AND_ASSIGN(AudioFrame);
};

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
  energy_ = 0xffffffff;
  interleaved_ = true;
}

inline void AudioFrame::UpdateFrame(int id, uint32_t timestamp,
                                    const int16_t* data,
                                    int samples_per_channel, int sample_rate_hz,
                                    SpeechType speech_type,
                                    VADActivity vad_activity, int num_channels,
                                    uint32_t energy) {
  id_ = id;
  timestamp_ = timestamp;
  samples_per_channel_ = samples_per_channel;
  sample_rate_hz_ = sample_rate_hz;
  speech_type_ = speech_type;
  vad_activity_ = vad_activity;
  num_channels_ = num_channels;
  energy_ = energy;

  const int length = samples_per_channel * num_channels;
  assert(length <= kMaxDataSizeSamples && length >= 0);
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
  energy_ = src.energy_;
  interleaved_ = src.interleaved_;

  const int length = samples_per_channel_ * num_channels_;
  assert(length <= kMaxDataSizeSamples && length >= 0);
  memcpy(data_, src.data_, sizeof(int16_t) * length);
}

inline void AudioFrame::Mute() {
  memset(data_, 0, samples_per_channel_ * num_channels_ * sizeof(int16_t));
}

inline AudioFrame& AudioFrame::operator>>=(const int rhs) {
  assert((num_channels_ > 0) && (num_channels_ < 3));
  if ((num_channels_ > 2) || (num_channels_ < 1)) return *this;

  for (int i = 0; i < samples_per_channel_ * num_channels_; i++) {
    data_[i] = static_cast<int16_t>(data_[i] >> rhs);
  }
  return *this;
}

inline AudioFrame& AudioFrame::Append(const AudioFrame& rhs) {
  // Sanity check
  assert((num_channels_ > 0) && (num_channels_ < 3));
  assert(interleaved_ == rhs.interleaved_);
  if ((num_channels_ > 2) || (num_channels_ < 1)) return *this;
  if (num_channels_ != rhs.num_channels_) return *this;

  if ((vad_activity_ == kVadActive) || rhs.vad_activity_ == kVadActive) {
    vad_activity_ = kVadActive;
  } else if (vad_activity_ == kVadUnknown || rhs.vad_activity_ == kVadUnknown) {
    vad_activity_ = kVadUnknown;
  }
  if (speech_type_ != rhs.speech_type_) {
    speech_type_ = kUndefined;
  }

  int offset = samples_per_channel_ * num_channels_;
  for (int i = 0; i < rhs.samples_per_channel_ * rhs.num_channels_; i++) {
    data_[offset + i] = rhs.data_[i];
  }
  samples_per_channel_ += rhs.samples_per_channel_;
  return *this;
}

inline AudioFrame& AudioFrame::operator+=(const AudioFrame& rhs) {
  // Sanity check
  assert((num_channels_ > 0) && (num_channels_ < 3));
  assert(interleaved_ == rhs.interleaved_);
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
    for (int i = 0; i < samples_per_channel_ * num_channels_; i++) {
      int32_t wrapGuard =
          static_cast<int32_t>(data_[i]) + static_cast<int32_t>(rhs.data_[i]);
      if (wrapGuard < -32768) {
        data_[i] = -32768;
      } else if (wrapGuard > 32767) {
        data_[i] = 32767;
      } else {
        data_[i] = (int16_t)wrapGuard;
      }
    }
  }
  energy_ = 0xffffffff;
  return *this;
}

inline AudioFrame& AudioFrame::operator-=(const AudioFrame& rhs) {
  // Sanity check
  assert((num_channels_ > 0) && (num_channels_ < 3));
  assert(interleaved_ == rhs.interleaved_);
  if ((num_channels_ > 2) || (num_channels_ < 1)) return *this;

  if ((samples_per_channel_ != rhs.samples_per_channel_) ||
      (num_channels_ != rhs.num_channels_)) {
    return *this;
  }
  if ((vad_activity_ != kVadPassive) || rhs.vad_activity_ != kVadPassive) {
    vad_activity_ = kVadUnknown;
  }
  speech_type_ = kUndefined;

  for (int i = 0; i < samples_per_channel_ * num_channels_; i++) {
    int32_t wrapGuard =
        static_cast<int32_t>(data_[i]) - static_cast<int32_t>(rhs.data_[i]);
    if (wrapGuard < -32768) {
      data_[i] = -32768;
    } else if (wrapGuard > 32767) {
      data_[i] = 32767;
    } else {
      data_[i] = (int16_t)wrapGuard;
    }
  }
  energy_ = 0xffffffff;
  return *this;
}

inline bool IsNewerSequenceNumber(uint16_t sequence_number,
                                  uint16_t prev_sequence_number) {
  return sequence_number != prev_sequence_number &&
         static_cast<uint16_t>(sequence_number - prev_sequence_number) < 0x8000;
}

inline bool IsNewerTimestamp(uint32_t timestamp, uint32_t prev_timestamp) {
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

}  // namespace webrtc

#endif  // MODULE_COMMON_TYPES_H
