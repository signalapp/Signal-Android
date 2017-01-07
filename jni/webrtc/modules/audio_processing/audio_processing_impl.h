/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_AUDIO_PROCESSING_IMPL_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_AUDIO_PROCESSING_IMPL_H_

#include <list>
#include <memory>
#include <string>
#include <vector>

#include "webrtc/base/criticalsection.h"
#include "webrtc/base/thread_annotations.h"
#include "webrtc/modules/audio_processing/audio_buffer.h"
#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/system_wrappers/include/file_wrapper.h"

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
// Files generated at build-time by the protobuf compiler.
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/modules/audio_processing/debug.pb.h"
#else
#include "webrtc/modules/audio_processing/debug.pb.h"
#endif
#endif  // WEBRTC_AUDIOPROC_DEBUG_DUMP

namespace webrtc {

class AgcManagerDirect;
class AudioConverter;

template<typename T>
class Beamformer;

class AudioProcessingImpl : public AudioProcessing {
 public:
  // Methods forcing APM to run in a single-threaded manner.
  // Acquires both the render and capture locks.
  explicit AudioProcessingImpl(const Config& config);
  // AudioProcessingImpl takes ownership of beamformer.
  AudioProcessingImpl(const Config& config, Beamformer<float>* beamformer);
  virtual ~AudioProcessingImpl();
  int Initialize() override;
  int Initialize(int input_sample_rate_hz,
                 int output_sample_rate_hz,
                 int reverse_sample_rate_hz,
                 ChannelLayout input_layout,
                 ChannelLayout output_layout,
                 ChannelLayout reverse_layout) override;
  int Initialize(const ProcessingConfig& processing_config) override;
  void SetExtraOptions(const Config& config) override;
  void UpdateHistogramsOnCallEnd() override;
  int StartDebugRecording(const char filename[kMaxFilenameSize],
                          int64_t max_log_size_bytes) override;
  int StartDebugRecording(FILE* handle, int64_t max_log_size_bytes) override;

  int StartDebugRecordingForPlatformFile(rtc::PlatformFile handle) override;
  int StopDebugRecording() override;

  // Capture-side exclusive methods possibly running APM in a
  // multi-threaded manner. Acquire the capture lock.
  int ProcessStream(AudioFrame* frame) override;
  int ProcessStream(const float* const* src,
                    size_t samples_per_channel,
                    int input_sample_rate_hz,
                    ChannelLayout input_layout,
                    int output_sample_rate_hz,
                    ChannelLayout output_layout,
                    float* const* dest) override;
  int ProcessStream(const float* const* src,
                    const StreamConfig& input_config,
                    const StreamConfig& output_config,
                    float* const* dest) override;
  void set_output_will_be_muted(bool muted) override;
  int set_stream_delay_ms(int delay) override;
  void set_delay_offset_ms(int offset) override;
  int delay_offset_ms() const override;
  void set_stream_key_pressed(bool key_pressed) override;

  // Render-side exclusive methods possibly running APM in a
  // multi-threaded manner. Acquire the render lock.
  int ProcessReverseStream(AudioFrame* frame) override;
  int AnalyzeReverseStream(const float* const* data,
                           size_t samples_per_channel,
                           int sample_rate_hz,
                           ChannelLayout layout) override;
  int ProcessReverseStream(const float* const* src,
                           const StreamConfig& reverse_input_config,
                           const StreamConfig& reverse_output_config,
                           float* const* dest) override;

  // Methods only accessed from APM submodules or
  // from AudioProcessing tests in a single-threaded manner.
  // Hence there is no need for locks in these.
  int proc_sample_rate_hz() const override;
  int proc_split_sample_rate_hz() const override;
  size_t num_input_channels() const override;
  size_t num_proc_channels() const override;
  size_t num_output_channels() const override;
  size_t num_reverse_channels() const override;
  int stream_delay_ms() const override;
  bool was_stream_delay_set() const override
      EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  // Methods returning pointers to APM submodules.
  // No locks are aquired in those, as those locks
  // would offer no protection (the submodules are
  // created only once in a single-treaded manner
  // during APM creation).
  EchoCancellation* echo_cancellation() const override;
  EchoControlMobile* echo_control_mobile() const override;
  GainControl* gain_control() const override;
  HighPassFilter* high_pass_filter() const override;
  LevelEstimator* level_estimator() const override;
  NoiseSuppression* noise_suppression() const override;
  VoiceDetection* voice_detection() const override;

 protected:
  // Overridden in a mock.
  virtual int InitializeLocked()
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);

 private:
  struct ApmPublicSubmodules;
  struct ApmPrivateSubmodules;

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  // State for the debug dump.
  struct ApmDebugDumpThreadState {
    ApmDebugDumpThreadState() : event_msg(new audioproc::Event()) {}
    std::unique_ptr<audioproc::Event> event_msg;  // Protobuf message.
    std::string event_str;  // Memory for protobuf serialization.

    // Serialized string of last saved APM configuration.
    std::string last_serialized_config;
  };

  struct ApmDebugDumpState {
    ApmDebugDumpState() : debug_file(FileWrapper::Create()) {}
    // Number of bytes that can still be written to the log before the maximum
    // size is reached. A value of <= 0 indicates that no limit is used.
    int64_t num_bytes_left_for_log_ = -1;
    std::unique_ptr<FileWrapper> debug_file;
    ApmDebugDumpThreadState render;
    ApmDebugDumpThreadState capture;
  };
#endif

  // Method for modifying the formats struct that are called from both
  // the render and capture threads. The check for whether modifications
  // are needed is done while holding the render lock only, thereby avoiding
  // that the capture thread blocks the render thread.
  // The struct is modified in a single-threaded manner by holding both the
  // render and capture locks.
  int MaybeInitialize(const ProcessingConfig& config)
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  int MaybeInitializeRender(const ProcessingConfig& processing_config)
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  int MaybeInitializeCapture(const ProcessingConfig& processing_config)
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  // Method for checking for the need of conversion. Accesses the formats
  // structs in a read manner but the requirement for the render lock to be held
  // was added as it currently anyway is always called in that manner.
  bool rev_conversion_needed() const EXCLUSIVE_LOCKS_REQUIRED(crit_render_);
  bool render_check_rev_conversion_needed() const
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  // Methods requiring APM running in a single-threaded manner.
  // Are called with both the render and capture locks already
  // acquired.
  void InitializeExperimentalAgc()
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  void InitializeTransient()
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  void InitializeBeamformer()
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  void InitializeIntelligibility()
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  void InitializeHighPassFilter()
      EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializeNoiseSuppression()
      EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializeLevelEstimator()
      EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializeVoiceDetection()
      EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializeEchoCanceller()
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  void InitializeGainController()
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  void InitializeEchoControlMobile()
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  int InitializeLocked(const ProcessingConfig& config)
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);

  // Capture-side exclusive methods possibly running APM in a multi-threaded
  // manner that are called with the render lock already acquired.
  int ProcessStreamLocked() EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  bool output_copy_needed() const EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  bool is_fwd_processed() const EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  bool fwd_synthesis_needed() const EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  bool fwd_analysis_needed() const EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void MaybeUpdateHistograms() EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  // Render-side exclusive methods possibly running APM in a multi-threaded
  // manner that are called with the render lock already acquired.
  // TODO(ekm): Remove once all clients updated to new interface.
  int AnalyzeReverseStreamLocked(const float* const* src,
                                 const StreamConfig& input_config,
                                 const StreamConfig& output_config)
      EXCLUSIVE_LOCKS_REQUIRED(crit_render_);
  bool is_rev_processed() const EXCLUSIVE_LOCKS_REQUIRED(crit_render_);
  bool rev_synthesis_needed() const EXCLUSIVE_LOCKS_REQUIRED(crit_render_);
  bool rev_analysis_needed() const EXCLUSIVE_LOCKS_REQUIRED(crit_render_);
  int ProcessReverseStreamLocked() EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

// Debug dump methods that are internal and called without locks.
// TODO(peah): Make thread safe.
#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  // TODO(andrew): make this more graceful. Ideally we would split this stuff
  // out into a separate class with an "enabled" and "disabled" implementation.
  static int WriteMessageToDebugFile(FileWrapper* debug_file,
                                     int64_t* filesize_limit_bytes,
                                     rtc::CriticalSection* crit_debug,
                                     ApmDebugDumpThreadState* debug_state);
  int WriteInitMessage() EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);

  // Writes Config message. If not |forced|, only writes the current config if
  // it is different from the last saved one; if |forced|, writes the config
  // regardless of the last saved.
  int WriteConfigMessage(bool forced) EXCLUSIVE_LOCKS_REQUIRED(crit_capture_)
      EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  // Critical section.
  rtc::CriticalSection crit_debug_;

  // Debug dump state.
  ApmDebugDumpState debug_dump_;
#endif

  // Critical sections.
  rtc::CriticalSection crit_render_ ACQUIRED_BEFORE(crit_capture_);
  rtc::CriticalSection crit_capture_;

  // Structs containing the pointers to the submodules.
  std::unique_ptr<ApmPublicSubmodules> public_submodules_;
  std::unique_ptr<ApmPrivateSubmodules> private_submodules_
      GUARDED_BY(crit_capture_);

  // State that is written to while holding both the render and capture locks
  // but can be read without any lock being held.
  // As this is only accessed internally of APM, and all internal methods in APM
  // either are holding the render or capture locks, this construct is safe as
  // it is not possible to read the variables while writing them.
  struct ApmFormatState {
    ApmFormatState()
        :  // Format of processing streams at input/output call sites.
          api_format({{{kSampleRate16kHz, 1, false},
                       {kSampleRate16kHz, 1, false},
                       {kSampleRate16kHz, 1, false},
                       {kSampleRate16kHz, 1, false}}}),
          rev_proc_format(kSampleRate16kHz, 1) {}
    ProcessingConfig api_format;
    StreamConfig rev_proc_format;
  } formats_;

  // APM constants.
  const struct ApmConstants {
    ApmConstants(int agc_startup_min_volume, bool use_experimental_agc)
        :  // Format of processing streams at input/output call sites.
          agc_startup_min_volume(agc_startup_min_volume),
          use_experimental_agc(use_experimental_agc) {}
    int agc_startup_min_volume;
    bool use_experimental_agc;
  } constants_;

  struct ApmCaptureState {
    ApmCaptureState(bool transient_suppressor_enabled,
                    const std::vector<Point>& array_geometry,
                    SphericalPointf target_direction)
        : aec_system_delay_jumps(-1),
          delay_offset_ms(0),
          was_stream_delay_set(false),
          last_stream_delay_ms(0),
          last_aec_system_delay_ms(0),
          stream_delay_jumps(-1),
          output_will_be_muted(false),
          key_pressed(false),
          transient_suppressor_enabled(transient_suppressor_enabled),
          array_geometry(array_geometry),
          target_direction(target_direction),
          fwd_proc_format(kSampleRate16kHz),
          split_rate(kSampleRate16kHz) {}
    int aec_system_delay_jumps;
    int delay_offset_ms;
    bool was_stream_delay_set;
    int last_stream_delay_ms;
    int last_aec_system_delay_ms;
    int stream_delay_jumps;
    bool output_will_be_muted;
    bool key_pressed;
    bool transient_suppressor_enabled;
    std::vector<Point> array_geometry;
    SphericalPointf target_direction;
    std::unique_ptr<AudioBuffer> capture_audio;
    // Only the rate and samples fields of fwd_proc_format_ are used because the
    // forward processing number of channels is mutable and is tracked by the
    // capture_audio_.
    StreamConfig fwd_proc_format;
    int split_rate;
  } capture_ GUARDED_BY(crit_capture_);

  struct ApmCaptureNonLockedState {
    ApmCaptureNonLockedState(bool beamformer_enabled,
                             bool intelligibility_enabled)
        : fwd_proc_format(kSampleRate16kHz),
          split_rate(kSampleRate16kHz),
          stream_delay_ms(0),
          beamformer_enabled(beamformer_enabled),
          intelligibility_enabled(intelligibility_enabled) {}
    // Only the rate and samples fields of fwd_proc_format_ are used because the
    // forward processing number of channels is mutable and is tracked by the
    // capture_audio_.
    StreamConfig fwd_proc_format;
    int split_rate;
    int stream_delay_ms;
    bool beamformer_enabled;
    bool intelligibility_enabled;
  } capture_nonlocked_;

  struct ApmRenderState {
    std::unique_ptr<AudioConverter> render_converter;
    std::unique_ptr<AudioBuffer> render_audio;
  } render_ GUARDED_BY(crit_render_);
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_AUDIO_PROCESSING_IMPL_H_
