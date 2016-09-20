# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'conditions': [
    ['enable_protobuf==1', {
      'targets': [
        {
          'target_name': 'neteq_rtpplay',
          'type': 'executable',
          'dependencies': [
            '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
            '<(webrtc_root)/test/test.gyp:test_support',
            '<(webrtc_root)/system_wrappers/system_wrappers.gyp:metrics_default',
            'neteq',
            'neteq_unittest_tools',
          ],
          'sources': [
            'tools/neteq_rtpplay.cc',
          ],
          'defines': [
          ],
        }, # neteq_rtpplay
      ],
    }],
  ],
  'targets': [
    {
      'target_name': 'RTPencode',
      'type': 'executable',
      'dependencies': [
        # TODO(hlundin): Make RTPencode use ACM to encode files.
        '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
        'cng',
        'g711',
        'g722',
        'ilbc',
        'isac',
        'neteq_test_tools',  # Test helpers
        'pcm16b',
        'webrtc_opus',
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
        'CODEC_OPUS',
      ],
      'include_dirs': [
        'include',
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
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers_default',
        'neteq_unittest_tools',
      ],
      'sources': [
        'tools/rtp_analyze.cc',
      ],
    },

    {
      'target_name': 'RTPchange',
      'type': 'executable',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
        'neteq_test_tools',
      ],
      'sources': [
       'test/RTPchange.cc',
      ],
    },

    {
      'target_name': 'RTPtimeshift',
      'type': 'executable',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
        'neteq_test_tools',
      ],
      'sources': [
        'test/RTPtimeshift.cc',
      ],
    },

    {
      'target_name': 'rtpcat',
      'type': 'executable',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(webrtc_root)/test/test.gyp:rtp_test_utils',
      ],
      'sources': [
        'tools/rtpcat.cc',
      ],
    },

    {
      'target_name': 'rtp_to_text',
      'type': 'executable',
      'dependencies': [
        '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
        'neteq_test_tools',
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
        'webrtc_opus',
      ],
      'sources': [
        'test/audio_classifier_test.cc',
      ],
    },

    {
      'target_name': 'neteq_test_support',
      'type': 'static_library',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        'neteq',
        'neteq_unittest_tools',
        'pcm16b',
      ],
      'sources': [
        'tools/neteq_external_decoder_test.cc',
        'tools/neteq_external_decoder_test.h',
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
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/test/test.gyp:test_support_main',
        'neteq',
        'neteq_test_support',
      ],
      'sources': [
        'test/neteq_speed_test.cc',
      ],
    },

    {
      'target_name': 'neteq_opus_quality_test',
      'type': 'executable',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/test/test.gyp:test_support_main',
        'neteq',
        'neteq_test_support',
        'webrtc_opus',
      ],
      'sources': [
        'test/neteq_opus_quality_test.cc',
      ],
    },

    {
      'target_name': 'neteq_isac_quality_test',
      'type': 'executable',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/test/test.gyp:test_support_main',
        'isac_fix',
        'neteq',
        'neteq_test_support',
      ],
      'sources': [
        'test/neteq_isac_quality_test.cc',
      ],
    },

    {
      'target_name': 'neteq_pcmu_quality_test',
      'type': 'executable',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/test/test.gyp:test_support_main',
        'g711',
        'neteq',
        'neteq_test_support',
      ],
      'sources': [
        'test/neteq_pcmu_quality_test.cc',
      ],
    },

    {
      'target_name': 'neteq_ilbc_quality_test',
      'type': 'executable',
      'dependencies': [
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
        '<(webrtc_root)/test/test.gyp:test_support_main',
        'neteq',
        'neteq_test_support',
        'ilbc',
      ],
      'sources': [
        'test/neteq_ilbc_quality_test.cc',
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
        '<(DEPTH)/testing/gtest.gyp:gtest',
        '<(webrtc_root)/common.gyp:webrtc_common',
        'cng',
        'g711',
        'g722',
        'ilbc',
        'isac',
        'pcm16b',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'include',
          'test',
          '<(webrtc_root)',
        ],
      },
      'defines': [
      ],
      'include_dirs': [
        'include',
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
