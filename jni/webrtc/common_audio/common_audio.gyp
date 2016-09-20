# Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'includes': [
    '../build/common.gypi',
  ],
  'targets': [
    {
      'target_name': 'common_audio',
      'type': 'static_library',
      'dependencies': [
        '<(webrtc_root)/system_wrappers/system_wrappers.gyp:system_wrappers',
      ],
      'include_dirs': [
        'resampler/include',
        'signal_processing/include',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          'resampler/include',
          'signal_processing/include',
          'vad/include',
        ],
      },
      'sources': [
        'audio_converter.cc',
        'audio_converter.h',
        'audio_ring_buffer.cc',
        'audio_ring_buffer.h',
        'audio_util.cc',
        'blocker.cc',
        'blocker.h',
        'channel_buffer.cc',
        'channel_buffer.h',
        'fft4g.c',
        'fft4g.h',
        'fir_filter.cc',
        'fir_filter.h',
        'fir_filter_neon.h',
        'fir_filter_sse.h',
        'include/audio_util.h',
        'lapped_transform.cc',
        'lapped_transform.h',
        'real_fourier.cc',
        'real_fourier.h',
        'real_fourier_ooura.cc',
        'real_fourier_ooura.h',
        'resampler/include/push_resampler.h',
        'resampler/include/resampler.h',
        'resampler/push_resampler.cc',
        'resampler/push_sinc_resampler.cc',
        'resampler/push_sinc_resampler.h',
        'resampler/resampler.cc',
        'resampler/sinc_resampler.cc',
        'resampler/sinc_resampler.h',
        'ring_buffer.c',
        'ring_buffer.h',
        'signal_processing/include/real_fft.h',
        'signal_processing/include/signal_processing_library.h',
        'signal_processing/include/spl_inl.h',
        'signal_processing/auto_corr_to_refl_coef.c',
        'signal_processing/auto_correlation.c',
        'signal_processing/complex_fft.c',
        'signal_processing/complex_fft_tables.h',
        'signal_processing/complex_bit_reverse.c',
        'signal_processing/copy_set_operations.c',
        'signal_processing/cross_correlation.c',
        'signal_processing/division_operations.c',
        'signal_processing/dot_product_with_scale.c',
        'signal_processing/downsample_fast.c',
        'signal_processing/energy.c',
        'signal_processing/filter_ar.c',
        'signal_processing/filter_ar_fast_q12.c',
        'signal_processing/filter_ma_fast_q12.c',
        'signal_processing/get_hanning_window.c',
        'signal_processing/get_scaling_square.c',
        'signal_processing/ilbc_specific_functions.c',
        'signal_processing/levinson_durbin.c',
        'signal_processing/lpc_to_refl_coef.c',
        'signal_processing/min_max_operations.c',
        'signal_processing/randomization_functions.c',
        'signal_processing/refl_coef_to_lpc.c',
        'signal_processing/real_fft.c',
        'signal_processing/resample.c',
        'signal_processing/resample_48khz.c',
        'signal_processing/resample_by_2.c',
        'signal_processing/resample_by_2_internal.c',
        'signal_processing/resample_by_2_internal.h',
        'signal_processing/resample_fractional.c',
        'signal_processing/spl_init.c',
        'signal_processing/spl_inl.c',
        'signal_processing/spl_sqrt.c',
        'signal_processing/spl_sqrt_floor.c',
        'signal_processing/splitting_filter.c',
        'signal_processing/sqrt_of_one_minus_x_squared.c',
        'signal_processing/vector_scaling_operations.c',
        'sparse_fir_filter.cc',
        'sparse_fir_filter.h',
        'vad/include/vad.h',
        'vad/include/webrtc_vad.h',
        'vad/vad.cc',
        'vad/webrtc_vad.c',
        'vad/vad_core.c',
        'vad/vad_core.h',
        'vad/vad_filterbank.c',
        'vad/vad_filterbank.h',
        'vad/vad_gmm.c',
        'vad/vad_gmm.h',
        'vad/vad_sp.c',
        'vad/vad_sp.h',
        'wav_header.cc',
        'wav_header.h',
        'wav_file.cc',
        'wav_file.h',
        'window_generator.cc',
        'window_generator.h',
      ],
      'conditions': [
        ['rtc_use_openmax_dl==1', {
          'sources': [
            'real_fourier_openmax.cc',
            'real_fourier_openmax.h',
          ],
          'defines': ['RTC_USE_OPENMAX_DL',],
          'conditions': [
            ['build_openmax_dl==1', {
              'dependencies': ['<(DEPTH)/third_party/openmax_dl/dl/dl.gyp:openmax_dl',],
            }],
          ],
        }],
        ['target_arch=="ia32" or target_arch=="x64"', {
          'dependencies': ['common_audio_sse2',],
        }],
        ['build_with_neon==1', {
          'dependencies': ['common_audio_neon',],
        }],
        ['target_arch=="arm"', {
          'sources': [
            'signal_processing/complex_bit_reverse_arm.S',
            'signal_processing/spl_sqrt_floor_arm.S',
          ],
          'sources!': [
            'signal_processing/complex_bit_reverse.c',
            'signal_processing/spl_sqrt_floor.c',
          ],
          'conditions': [
            ['arm_version>=7', {
              'sources': [
                'signal_processing/filter_ar_fast_q12_armv7.S',
              ],
              'sources!': [
                'signal_processing/filter_ar_fast_q12.c',
              ],
            }],
          ],  # conditions
        }],
        ['target_arch=="mipsel" and mips_arch_variant!="r6"', {
          'sources': [
            'signal_processing/include/spl_inl_mips.h',
            'signal_processing/complex_bit_reverse_mips.c',
            'signal_processing/complex_fft_mips.c',
            'signal_processing/cross_correlation_mips.c',
            'signal_processing/downsample_fast_mips.c',
            'signal_processing/filter_ar_fast_q12_mips.c',
            'signal_processing/min_max_operations_mips.c',
            'signal_processing/resample_by_2_mips.c',
            'signal_processing/spl_sqrt_floor_mips.c',
          ],
          'sources!': [
            'signal_processing/complex_bit_reverse.c',
            'signal_processing/complex_fft.c',
            'signal_processing/filter_ar_fast_q12.c',
            'signal_processing/spl_sqrt_floor.c',
          ],
          'conditions': [
            ['mips_dsp_rev>0', {
              'sources': [
                'signal_processing/vector_scaling_operations_mips.c',
              ],
            }],
          ],
        }],
      ],  # conditions
      # Ignore warning on shift operator promotion.
      'msvs_disabled_warnings': [ 4334, ],
    },
  ],  # targets
  'conditions': [
    ['target_arch=="ia32" or target_arch=="x64"', {
      'targets': [
        {
          'target_name': 'common_audio_sse2',
          'type': 'static_library',
          'sources': [
            'fir_filter_sse.cc',
            'resampler/sinc_resampler_sse.cc',
          ],
          'conditions': [
            ['os_posix==1', {
              'cflags': [ '-msse2', ],
              'xcode_settings': {
                'OTHER_CFLAGS': [ '-msse2', ],
              },
            }],
          ],
        },
      ],  # targets
    }],
    ['build_with_neon==1', {
      'targets': [
        {
          'target_name': 'common_audio_neon',
          'type': 'static_library',
          'includes': ['../build/arm_neon.gypi',],
          'sources': [
            'fir_filter_neon.cc',
            'resampler/sinc_resampler_neon.cc',
            'signal_processing/cross_correlation_neon.c',
            'signal_processing/downsample_fast_neon.c',
            'signal_processing/min_max_operations_neon.c',
          ],
        },
      ],  # targets
    }],
    ['include_tests==1', {
      'targets' : [
        {
          'target_name': 'common_audio_unittests',
          'type': '<(gtest_target_type)',
          'dependencies': [
            'common_audio',
            '<(webrtc_root)/test/test.gyp:test_support_main',
            '<(DEPTH)/testing/gmock.gyp:gmock',
            '<(DEPTH)/testing/gtest.gyp:gtest',
          ],
          'sources': [
            'audio_converter_unittest.cc',
            'audio_ring_buffer_unittest.cc',
            'audio_util_unittest.cc',
            'blocker_unittest.cc',
            'fir_filter_unittest.cc',
            'lapped_transform_unittest.cc',
            'real_fourier_unittest.cc',
            'resampler/resampler_unittest.cc',
            'resampler/push_resampler_unittest.cc',
            'resampler/push_sinc_resampler_unittest.cc',
            'resampler/sinusoidal_linear_chirp_source.cc',
            'resampler/sinusoidal_linear_chirp_source.h',
            'ring_buffer_unittest.cc',
            'signal_processing/real_fft_unittest.cc',
            'signal_processing/signal_processing_unittest.cc',
            'sparse_fir_filter_unittest.cc',
            'vad/vad_core_unittest.cc',
            'vad/vad_filterbank_unittest.cc',
            'vad/vad_gmm_unittest.cc',
            'vad/vad_sp_unittest.cc',
            'vad/vad_unittest.cc',
            'vad/vad_unittest.h',
            'wav_header_unittest.cc',
            'wav_file_unittest.cc',
            'window_generator_unittest.cc',
          ],
          'conditions': [
            ['rtc_use_openmax_dl==1', {
              'defines': ['RTC_USE_OPENMAX_DL',],
            }],
            ['OS=="android"', {
              'dependencies': [
                '<(DEPTH)/testing/android/native_test.gyp:native_test_native_code',
              ],
            }],
            # Does not compile on iOS for arm: webrtc:5544.
            ['OS!="ios" or target_arch!="arm"' , {
              'sources': [
                'resampler/sinc_resampler_unittest.cc',
              ],
            }],
          ],
        },
      ],  # targets
      'conditions': [
        ['OS=="android"', {
          'targets': [
            {
              'target_name': 'common_audio_unittests_apk_target',
              'type': 'none',
              'dependencies': [
                '<(android_tests_path):common_audio_unittests_apk',
              ],
            },
          ],
          'conditions': [
            ['test_isolation_mode != "noop"',
              {
                'targets': [
                  {
                    'target_name': 'common_audio_unittests_apk_run',
                    'type': 'none',
                    'dependencies': [
                      '<(android_tests_path):common_audio_unittests_apk',
                    ],
                    'includes': [
                      '../build/isolate.gypi',
                    ],
                    'sources': [
                      'common_audio_unittests_apk.isolate',
                    ],
                  },
                ],
              },
            ],
          ],
        }],  # OS=="android"
        ['test_isolation_mode != "noop"', {
          'targets': [
            {
              'target_name': 'common_audio_unittests_run',
              'type': 'none',
              'dependencies': [
                'common_audio_unittests',
              ],
              'includes': [
                '../build/isolate.gypi',
              ],
              'sources': [
                'common_audio_unittests.isolate',
              ],
            },
          ],
        }],
      ],
    }],
  ],  # conditions
}
