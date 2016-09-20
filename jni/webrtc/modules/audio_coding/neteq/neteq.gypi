# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'variables': {
    'codecs': [
      'cng',
      'g711',
      'pcm16b',
    ],
    'neteq_defines': [],
    'conditions': [
      ['include_ilbc==1', {
        'codecs': ['ilbc',],
        'neteq_defines': ['WEBRTC_CODEC_ILBC',],
      }],
      ['include_opus==1', {
        'codecs': ['webrtc_opus',],
        'neteq_defines': ['WEBRTC_CODEC_OPUS',],
      }],
      ['build_with_mozilla==0', {
        'conditions': [
          ['target_arch=="arm"', {
            'codecs': ['isac_fix',],
            'neteq_defines': ['WEBRTC_CODEC_ISACFX',],
          }, {
            'codecs': ['isac',],
            'neteq_defines': ['WEBRTC_CODEC_ISAC',],
          }],
        ],
        'codecs': ['g722',],
        'neteq_defines': ['WEBRTC_CODEC_G722',],
      }],
    ],
    'neteq_dependencies': [
      '<@(codecs)',
      '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
      '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
      'audio_decoder_interface',
    ],
  },
  'targets': [
    {
      'target_name': 'neteq',
      'type': 'static_library',
      'dependencies': [
        '<@(neteq_dependencies)',
        '<(webrtc_root)/common.gyp:webrtc_common',
        'builtin_audio_decoder_factory',
        'rent_a_codec',
      ],
      'defines': [
        '<@(neteq_defines)',
      ],
      'sources': [
        'include/neteq.h',
        'accelerate.cc',
        'accelerate.h',
        'audio_classifier.cc',
        'audio_classifier.h',
        'audio_decoder_impl.cc',
        'audio_decoder_impl.h',
        'audio_multi_vector.cc',
        'audio_multi_vector.h',
        'audio_vector.cc',
        'audio_vector.h',
        'background_noise.cc',
        'background_noise.h',
        'buffer_level_filter.cc',
        'buffer_level_filter.h',
        'comfort_noise.cc',
        'comfort_noise.h',
        'cross_correlation.cc',
        'cross_correlation.h',
        'decision_logic.cc',
        'decision_logic.h',
        'decision_logic_fax.cc',
        'decision_logic_fax.h',
        'decision_logic_normal.cc',
        'decision_logic_normal.h',
        'decoder_database.cc',
        'decoder_database.h',
        'defines.h',
        'delay_manager.cc',
        'delay_manager.h',
        'delay_peak_detector.cc',
        'delay_peak_detector.h',
        'dsp_helper.cc',
        'dsp_helper.h',
        'dtmf_buffer.cc',
        'dtmf_buffer.h',
        'dtmf_tone_generator.cc',
        'dtmf_tone_generator.h',
        'expand.cc',
        'expand.h',
        'merge.cc',
        'merge.h',
        'nack_tracker.h',
        'nack_tracker.cc',
        'neteq_impl.cc',
        'neteq_impl.h',
        'neteq.cc',
        'statistics_calculator.cc',
        'statistics_calculator.h',
        'normal.cc',
        'normal.h',
        'packet.cc',
        'packet.h',
        'packet_buffer.cc',
        'packet_buffer.h',
        'payload_splitter.cc',
        'payload_splitter.h',
        'post_decode_vad.cc',
        'post_decode_vad.h',
        'preemptive_expand.cc',
        'preemptive_expand.h',
        'random_vector.cc',
        'random_vector.h',
        'rtcp.cc',
        'rtcp.h',
        'sync_buffer.cc',
        'sync_buffer.h',
        'tick_timer.cc',
        'tick_timer.h',
        'timestamp_scaler.cc',
        'timestamp_scaler.h',
        'time_stretch.cc',
        'time_stretch.h',
      ],
    },
  ], # targets
  'conditions': [
    ['include_tests==1', {
      'includes': ['neteq_tests.gypi',],
      'targets': [
        {
          'target_name': 'audio_decoder_unittests',
          'type': '<(gtest_target_type)',
          'dependencies': [
            '<@(codecs)',
            'g722',
            'ilbc',
            'isac',
            'isac_fix',
            'audio_decoder_interface',
            'neteq_unittest_tools',
            '<(DEPTH)/testing/gtest.gyp:gtest',
            '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
            '<(webrtc_root)/test/test.gyp:test_support_main',
          ],
          'defines': [
            '<@(neteq_defines)',
          ],
          'sources': [
            'audio_decoder_unittest.cc',
          ],
          'conditions': [
            ['OS=="android"', {
              'dependencies': [
                '<(DEPTH)/testing/android/native_test.gyp:native_test_native_code',
              ],
            }],
            ['OS=="ios"', {
              'mac_bundle_resources': [
                '<(DEPTH)/resources/audio_coding/testfile32kHz.pcm',
              ],
            }],
          ],
        }, # audio_decoder_unittests

        {
          'target_name': 'rtc_event_log_source',
          'type': 'static_library',
          'dependencies': [
            '<(webrtc_root)/webrtc.gyp:rtc_event_log_parser',
            '<(webrtc_root)/webrtc.gyp:rtc_event_log_proto',
          ],
          'export_dependent_settings': [
            '<(webrtc_root)/webrtc.gyp:rtc_event_log_parser',
          ],
          'sources': [
            'tools/rtc_event_log_source.h',
            'tools/rtc_event_log_source.cc',
          ],
        },

        {
          'target_name': 'neteq_unittest_proto',
          'type': 'static_library',
          'sources': [
            'neteq_unittest.proto',
          ],
          'variables': {
            'proto_in_dir': '.',
            # Workaround to protect against gyp's pathname relativization when
            # this file is included by modules.gyp.
            'proto_out_protected': 'webrtc/audio_coding/neteq',
            'proto_out_dir': '<(proto_out_protected)',
          },
          'includes': ['../../../build/protoc.gypi',],
        },

        {
          'target_name': 'neteq_unittest_tools',
          'type': 'static_library',
          'dependencies': [
            'neteq',
            'rtp_rtcp',
            'rtc_event_log_source',
            '<(webrtc_root)/common_audio/common_audio.gyp:common_audio',
            '<(webrtc_root)/test/test.gyp:rtp_test_utils',
          ],
          'direct_dependent_settings': {
            'include_dirs': [
              'tools',
            ],
          },
          'include_dirs': [
            'tools',
          ],
          'sources': [
            'tools/audio_checksum.h',
            'tools/audio_loop.cc',
            'tools/audio_loop.h',
            'tools/audio_sink.h',
            'tools/constant_pcm_packet_source.cc',
            'tools/constant_pcm_packet_source.h',
            'tools/fake_decode_from_file.cc',
            'tools/fake_decode_from_file.h',
            'tools/input_audio_file.cc',
            'tools/input_audio_file.h',
            'tools/neteq_input.h',
            'tools/neteq_packet_source_input.cc',
            'tools/neteq_packet_source_input.h',
            'tools/neteq_replacement_input.cc',
            'tools/neteq_replacement_input.h',
            'tools/neteq_test.cc',
            'tools/neteq_test.h',
            'tools/output_audio_file.h',
            'tools/output_wav_file.h',
            'tools/packet.cc',
            'tools/packet.h',
            'tools/packet_source.h',
            'tools/resample_input_audio_file.cc',
            'tools/resample_input_audio_file.h',
            'tools/rtp_file_source.cc',
            'tools/rtp_file_source.h',
            'tools/rtp_generator.cc',
            'tools/rtp_generator.h',
          ],
        }, # neteq_unittest_tools
      ], # targets
    }], # include_tests
  ], # conditions
}
