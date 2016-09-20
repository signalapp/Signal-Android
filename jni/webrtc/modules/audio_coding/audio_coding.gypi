# Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'includes': [
    '../../build/common.gypi',
    'codecs/interfaces.gypi',
    'codecs/cng/cng.gypi',
    'codecs/g711/g711.gypi',
    'codecs/g722/g722.gypi',
    'codecs/ilbc/ilbc.gypi',
    'codecs/isac/isac.gypi',
    'codecs/isac/isac_common.gypi',
    'codecs/isac/isacfix.gypi',
    'codecs/pcm16b/pcm16b.gypi',
    'codecs/red/red.gypi',
    'neteq/neteq.gypi',
  ],
  'variables': {
    'variables': {
      'audio_codec_dependencies': [
        'cng',
        'g711',
        'pcm16b',
      ],
      'audio_codec_defines': [],
      'conditions': [
        ['include_ilbc==1', {
          'audio_codec_dependencies': ['ilbc',],
          'audio_codec_defines': ['WEBRTC_CODEC_ILBC',],
        }],
        ['include_opus==1', {
          'audio_codec_dependencies': ['webrtc_opus',],
          'audio_codec_defines': ['WEBRTC_CODEC_OPUS',],
        }],
        ['build_with_mozilla==0', {
          'conditions': [
            ['target_arch=="arm"', {
              'audio_codec_dependencies': ['isac_fix',],
              'audio_codec_defines': ['WEBRTC_CODEC_ISACFX',],
            }, {
              'audio_codec_dependencies': ['isac',],
              'audio_codec_defines': ['WEBRTC_CODEC_ISAC',],
            }],
          ],
          'audio_codec_dependencies': ['g722',],
          'audio_codec_defines': ['WEBRTC_CODEC_G722',],
        }],
        ['build_with_mozilla==0 and build_with_chromium==0', {
          'audio_codec_dependencies': ['red',],
          'audio_codec_defines': ['WEBRTC_CODEC_RED',],
        }],
      ],
    },
    'audio_codec_dependencies': '<(audio_codec_dependencies)',
    'audio_codec_defines': '<(audio_codec_defines)',
    'audio_coding_dependencies': [
      '<@(audio_codec_dependencies)',
      '<(webrtc_root)/common.gyp:webrtc_common',
      '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
      '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
    ],
    'audio_coding_defines': '<(audio_codec_defines)',
  },
  'targets': [
    {
      'target_name': 'audio_decoder_factory_interface',
      'type': 'static_library',
      'dependencies': [
        '<(webrtc_root)/common.gyp:webrtc_common',
      ],
      'include_dirs': [
        '<(webrtc_root)',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          '<(webrtc_root)',
        ],
      },
      'sources': [
        'codecs/audio_decoder_factory.h',
        'codecs/audio_format.cc',
        'codecs/audio_format.h',
      ],
    },
    {
      'target_name': 'builtin_audio_decoder_factory',
      'type': 'static_library',
      'defines': [
        '<@(audio_codec_defines)',
      ],
      'dependencies': [
        '<(webrtc_root)/common.gyp:webrtc_common',
        '<@(audio_codec_dependencies)',
        'audio_decoder_factory_interface',
      ],
      'include_dirs': [
        '<(webrtc_root)',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          '<(webrtc_root)',
        ],
      },
      'sources': [
        'codecs/builtin_audio_decoder_factory.cc',
        'codecs/builtin_audio_decoder_factory.h',
      ],
    },
    {
      'target_name': 'rent_a_codec',
      'type': 'static_library',
      'defines': [
        '<@(audio_codec_defines)',
      ],
      'dependencies': [
        '<(webrtc_root)/common.gyp:webrtc_common',
        '<@(audio_codec_dependencies)',
      ],
      'include_dirs': [
        '<(webrtc_root)',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          '<(webrtc_root)',
        ],
      },
      'sources': [
        'acm2/acm_codec_database.cc',
        'acm2/acm_codec_database.h',
        'acm2/rent_a_codec.cc',
        'acm2/rent_a_codec.h',
      ],
    },
    {
      'target_name': 'audio_coding_module',
      'type': 'static_library',
      'defines': [
        '<@(audio_coding_defines)',
      ],
      'dependencies': [
        '<@(audio_coding_dependencies)',
        '<(webrtc_root)/common.gyp:webrtc_common',
        '<(webrtc_root)/webrtc.gyp:rtc_event_log',
        'neteq',
        'rent_a_codec',
      ],
      'include_dirs': [
        'include',
        '../include',
        '<(webrtc_root)',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'include',
          '../include',
          '<(webrtc_root)',
        ],
      },
      'conditions': [
        ['include_opus==1', {
          'export_dependent_settings': ['webrtc_opus'],
        }],
      ],
      'sources': [
        'acm2/acm_common_defs.h',
        'acm2/acm_receiver.cc',
        'acm2/acm_receiver.h',
        'acm2/acm_resampler.cc',
        'acm2/acm_resampler.h',
        'acm2/audio_coding_module.cc',
        'acm2/call_statistics.cc',
        'acm2/call_statistics.h',
        'acm2/codec_manager.cc',
        'acm2/codec_manager.h',
        'acm2/initial_delay_manager.cc',
        'acm2/initial_delay_manager.h',
        'include/audio_coding_module.h',
        'include/audio_coding_module_typedefs.h',
      ],
    },
  ],
  'conditions': [
    ['include_opus==1', {
      'includes': ['codecs/opus/opus.gypi',],
    }],
    ['include_tests==1', {
      'targets': [
        {
          'target_name': 'acm_receive_test',
          'type': 'static_library',
          'defines': [
            '<@(audio_coding_defines)',
          ],
          'dependencies': [
            '<@(audio_coding_dependencies)',
            'audio_coding_module',
            'neteq_unittest_tools',
            '<(DEPTH)/testing/gtest.gyp:gtest',
          ],
          'sources': [
            'acm2/acm_receive_test_oldapi.cc',
            'acm2/acm_receive_test_oldapi.h',
          ],
        }, # acm_receive_test
        {
          'target_name': 'acm_send_test',
          'type': 'static_library',
          'defines': [
            '<@(audio_coding_defines)',
          ],
          'dependencies': [
            '<@(audio_coding_dependencies)',
            'audio_coding_module',
            'neteq_unittest_tools',
            '<(DEPTH)/testing/gtest.gyp:gtest',
          ],
          'sources': [
            'acm2/acm_send_test_oldapi.cc',
            'acm2/acm_send_test_oldapi.h',
          ],
        }, # acm_send_test
        {
          'target_name': 'delay_test',
          'type': 'executable',
          'dependencies': [
            'audio_coding_module',
            '<(DEPTH)/testing/gtest.gyp:gtest',
            '<(webrtc_root)/common.gyp:webrtc_common',
            '<(webrtc_root)/test/test.gyp:test_support',
            '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
            '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers_default',
            '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
          ],
          'sources': [
             'test/delay_test.cc',
             'test/Channel.cc',
             'test/PCMFile.cc',
             'test/utility.cc',
           ],
        }, # delay_test
        {
          'target_name': 'insert_packet_with_timing',
          'type': 'executable',
          'dependencies': [
            'audio_coding_module',
            '<(DEPTH)/testing/gtest.gyp:gtest',
            '<(webrtc_root)/common.gyp:webrtc_common',
            '<(webrtc_root)/test/test.gyp:test_support',
            '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
            '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers_default',
            '<(DEPTH)/third_party/gflags/gflags.gyp:gflags',
          ],
          'sources': [
             'test/insert_packet_with_timing.cc',
             'test/Channel.cc',
             'test/PCMFile.cc',
           ],
        }, # delay_test
      ],
    }],
  ],
}
