# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'targets': [
    {
      'target_name': 'audioproc_test_utils',
      'type': 'static_library',
      'dependencies': [
        '<(webrtc_root)/base/base.gyp:rtc_base_approved',
        '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
      ],
      'sources': [
        'test/test_utils.cc',
        'test/test_utils.h',
      ],
    },
    {
      'target_name': 'transient_suppression_test',
      'type': 'executable',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/test/test.gyp:test_support',
        '<(webrtc_root)/modules/modules.gyp:audio_processing',
      ],
      'sources': [
        'transient/transient_suppression_test.cc',
        'transient/file_utils.cc',
        'transient/file_utils.h',
      ],
    }, # transient_suppression_test
    {
      'target_name': 'click_annotate',
      'type': 'executable',
      'dependencies': [
        '<(webrtc_root)/modules/modules.gyp:audio_processing',
      ],
      'sources': [
        'transient/click_annotate.cc',
        'transient/file_utils.cc',
        'transient/file_utils.h',
      ],
    },  # click_annotate
    {
      'target_name': 'nonlinear_beamformer_test',
      'type': 'executable',
      'dependencies': [
        'audioproc_test_utils',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/modules/modules.gyp:audio_processing',
      ],
      'sources': [
        'beamformer/nonlinear_beamformer_test.cc',
      ],
    }, # nonlinear_beamformer_test
    {
      'target_name': 'intelligibility_proc',
      'type': 'executable',
      'dependencies': [
        'audioproc_test_utils',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(webrtc_root)/modules/modules.gyp:audio_processing',
        '<(webrtc_root)/test/test.gyp:test_support',
      ],
      'sources': [
        'intelligibility/test/intelligibility_proc.cc',
      ],
    }, # intelligibility_proc
  ],
  'conditions': [
    ['enable_protobuf==1', {
      'targets': [
        {
          'target_name': 'audioproc_unittest_proto',
          'type': 'static_library',
          'sources': [ 'test/unittest.proto', ],
          'variables': {
            'proto_in_dir': 'test',
            # Workaround to protect against gyp's pathname relativization when
            # this file is included by modules.gyp.
            'proto_out_protected': 'webrtc/modules/audio_processing',
            'proto_out_dir': '<(proto_out_protected)',
          },
          'includes': [ '../../build/protoc.gypi', ],
        },
        {
          'target_name': 'audioproc_protobuf_utils',
          'type': 'static_library',
          'dependencies': [
            'audioproc_debug_proto',
          ],
          'sources': [
            'test/protobuf_utils.cc',
            'test/protobuf_utils.h',
          ],
        },
        {
          'target_name': 'audioproc',
          'type': 'executable',
          'dependencies': [
            'audio_processing',
            'audioproc_debug_proto',
            'audioproc_test_utils',
            'audioproc_protobuf_utils',
            '<(DEPTH)/testing/gtest.gyp:gtest',
            '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
            '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers_default',
            '<(webrtc_root)/test/test.gyp:test_support',
          ],
          'sources': [ 'test/process_test.cc', ],
        },
        {
          'target_name': 'audioproc_f',
          'type': 'executable',
          'dependencies': [
            'audio_processing',
            'audioproc_debug_proto',
            'audioproc_test_utils',
            'audioproc_protobuf_utils',
            '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers_default',
            '<(webrtc_root)/test/test.gyp:test_support',
            '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
          ],
          'sources': [
            'test/audio_processing_simulator.cc',
            'test/audio_processing_simulator.h',
            'test/aec_dump_based_simulator.cc',
            'test/aec_dump_based_simulator.h',
            'test/wav_based_simulator.cc',
            'test/wav_based_simulator.h',
            'test/audioproc_float.cc',
          ],
        },
        {
          'target_name': 'unpack_aecdump',
          'type': 'executable',
          'dependencies': [
            'audioproc_debug_proto',
            'audioproc_test_utils',
            'audioproc_protobuf_utils',
            '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
            '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
            '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
          ],
          'sources': [ 'test/unpack.cc', ],
        },
      ],
    }],
  ],
}
