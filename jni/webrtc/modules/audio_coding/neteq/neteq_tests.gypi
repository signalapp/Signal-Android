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
      'target_name': 'neteq_rtpplay',
      'type': 'executable',
      'dependencies': [
        'neteq',
        'neteq_unittest_tools',
        'PCM16B',
        '<(webrtc_root)/test/test.gyp:test_support_main',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
      ],
      'sources': [
        'tools/neteq_rtpplay.cc',
      ],
      'defines': [
      ],
    }, # neteq_rtpplay

    {
      'target_name': 'RTPencode',
      'type': 'executable',
      'dependencies': [
        # TODO(hlundin): Make RTPencode use ACM to encode files.
        'neteq_test_tools',# Test helpers
        'G711',
        'G722',
        'PCM16B',
        'iLBC',
        'iSAC',
        'CNG',
        '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
      ],
      'defines': [
        'CODEC_ILBC',
        'CODEC_PCM16B',
        'CODEC_G711',
        'CODEC_G722',
        'CODEC_ISAC',
        'CODEC_PCM16B_WB',
        'CODEC_ISAC_SWB',
        'CODEC_PCM16B_32KHZ',
        'CODEC_PCM16B_48KHZ',
        'CODEC_CNGCODEC8',
        'CODEC_CNGCODEC16',
        'CODEC_CNGCODEC32',
        'CODEC_ATEVENT_DECODE',
        'CODEC_RED',
      ],
      'include_dirs': [
        'interface',
        'test',
        '<(webrtc_root)',
      ],
      'sources': [
        'test/RTPencode.cc',
      ],
      # Disable warnings to enable Win64 build, issue 1323.
      'msvs_disabled_warnings': [
        4267,  # size_t to int truncation.
      ],
    },

    {
      'target_name': 'RTPjitter',
      'type': 'executable',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
      ],
      'sources': [
        'test/RTPjitter.cc',
      ],
    },

    {
      'target_name': 'rtp_analyze',
      'type': 'executable',
      'dependencies': [
        'neteq_unittest_tools',
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/system_wrappers/source/system_wrappers.gyp:field_trial_default',
      ],
      'sources': [
        'tools/rtp_analyze.cc',
      ],
    },

    {
      'target_name': 'RTPchange',
      'type': 'executable',
      'dependencies': [
        'neteq_test_tools',
        '<(DEPTH)/testing/gtest.gyp:gtest',
      ],
      'sources': [
       'test/RTPchange.cc',
      ],
    },

    {
      'target_name': 'RTPtimeshift',
      'type': 'executable',
      'dependencies': [
       'neteq_test_tools',
        '<(DEPTH)/testing/gtest.gyp:gtest',
      ],
      'sources': [
        'test/RTPtimeshift.cc',
      ],
    },

    {
      'target_name': 'RTPcat',
      'type': 'executable',
      'dependencies': [
        'neteq_test_tools',
        '<(DEPTH)/testing/gtest.gyp:gtest',
      ],
      'sources': [
        'test/RTPcat.cc',
      ],
    },

    {
      'target_name': 'rtp_to_text',
      'type': 'executable',
      'dependencies': [
        'neteq_test_tools',
        '<(webrtc_root)/system_wrappers/source/system_wrappers.gyp:system_wrappers',
      ],
      'sources': [
        'test/rtp_to_text.cc',
      ],
    },

    {
      'target_name': 'audio_classifier_test',
      'type': 'executable',
      'dependencies': [
        'neteq',
      ],
      'sources': [
        'test/audio_classifier_test.cc',
      ],
    },

    {
      'target_name': 'neteq_test_support',
      'type': 'static_library',
      'dependencies': [
        'neteq',
        'PCM16B',
        'neteq_unittest_tools',
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
      ],
      'sources': [
        'tools/neteq_performance_test.cc',
        'tools/neteq_performance_test.h',
        'tools/neteq_quality_test.cc',
        'tools/neteq_quality_test.h',
      ],
    }, # neteq_test_support

    {
      'target_name': 'neteq_speed_test',
      'type': 'executable',
      'dependencies': [
        'neteq',
        'neteq_test_support',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/test/test.gyp:test_support_main',
      ],
      'sources': [
        'test/neteq_speed_test.cc',
      ],
    },

    {
      'target_name': 'neteq_opus_fec_quality_test',
      'type': 'executable',
      'dependencies': [
        'neteq',
        'neteq_test_support',
        'webrtc_opus',
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/test/test.gyp:test_support_main',
      ],
      'sources': [
        'test/neteq_opus_fec_quality_test.cc',
      ],
    },

    {
      'target_name': 'neteq_isac_quality_test',
      'type': 'executable',
      'dependencies': [
        'neteq',
        'neteq_test_support',
        'iSACFix',
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/test/test.gyp:test_support_main',
      ],
      'sources': [
        'test/neteq_isac_quality_test.cc',
      ],
    },

    {
     'target_name': 'neteq_test_tools',
      # Collection of useful functions used in other tests.
      'type': 'static_library',
      'variables': {
        # Expects RTP packets without payloads when enabled.
        'neteq_dummy_rtp%': 0,
      },
      'dependencies': [
        'G711',
        'G722',
        'PCM16B',
        'iLBC',
        'iSAC',
        'CNG',
        '<(DEPTH)/testing/gtest.gyp:gtest',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'interface',
          'test',
          '<(webrtc_root)',
        ],
      },
      'defines': [
      ],
      'include_dirs': [
        'interface',
        'test',
        '<(webrtc_root)',
      ],
      'sources': [
        'test/NETEQTEST_DummyRTPpacket.cc',
        'test/NETEQTEST_DummyRTPpacket.h',
        'test/NETEQTEST_RTPpacket.cc',
        'test/NETEQTEST_RTPpacket.h',
      ],
      # Disable warnings to enable Win64 build, issue 1323.
      'msvs_disabled_warnings': [
        4267,  # size_t to int truncation.
      ],
    },
  ], # targets
}
