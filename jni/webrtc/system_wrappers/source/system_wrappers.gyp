# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'includes': [ '../../build/common.gypi', ],
  'targets': [
    {
      'target_name': 'system_wrappers',
      'type': 'static_library',
      'include_dirs': [
        'spreadsortlib',
        '../interface',
      ],
      'dependencies': [
        '../../base/base.gyp:webrtc_base',
      ],
      'direct_dependent_settings': {
        'include_dirs': [
          '../interface',
        ],
      },
      'sources': [
        '../interface/aligned_malloc.h',
        '../interface/atomic32.h',
        '../interface/clock.h',
        '../interface/compile_assert.h',
        '../interface/condition_variable_wrapper.h',
        '../interface/cpu_info.h',
        '../interface/cpu_features_wrapper.h',
        '../interface/critical_section_wrapper.h',
        '../interface/data_log.h',
        '../interface/data_log_c.h',
        '../interface/data_log_impl.h',
        '../interface/event_tracer.h',
        '../interface/event_wrapper.h',
        '../interface/field_trial.h',
        '../interface/file_wrapper.h',
        '../interface/fix_interlocked_exchange_pointer_win.h',
        '../interface/logcat_trace_context.h',
        '../interface/logging.h',
        '../interface/ref_count.h',
        '../interface/rtp_to_ntp.h',
        '../interface/rw_lock_wrapper.h',
        '../interface/scoped_ptr.h',
        '../interface/scoped_refptr.h',
        '../interface/scoped_vector.h',
        '../interface/sleep.h',
        '../interface/sort.h',
        '../interface/static_instance.h',
        '../interface/stl_util.h',
        '../interface/stringize_macros.h',
        '../interface/thread_annotations.h',
        '../interface/thread_wrapper.h',
        '../interface/tick_util.h',
        '../interface/timestamp_extrapolator.h',
        '../interface/trace.h',
        '../interface/trace_event.h',
        '../interface/utf_util_win.h',
        'aligned_malloc.cc',
        'atomic32_mac.cc',
        'atomic32_posix.cc',
        'atomic32_win.cc',
        'clock.cc',
        'condition_variable.cc',
        'condition_variable_posix.cc',
        'condition_variable_posix.h',
        'condition_variable_event_win.cc',
        'condition_variable_event_win.h',
        'condition_variable_native_win.cc',
        'condition_variable_native_win.h',
        'cpu_info.cc',
        'cpu_features.cc',
        'critical_section.cc',
        'critical_section_posix.cc',
        'critical_section_posix.h',
        'critical_section_win.cc',
        'critical_section_win.h',
        'data_log.cc',
        'data_log_c.cc',
        'data_log_no_op.cc',
        'event.cc',
        'event_posix.cc',
        'event_posix.h',
        'event_tracer.cc',
        'event_win.cc',
        'event_win.h',
        'file_impl.cc',
        'file_impl.h',
        'logcat_trace_context.cc',
        'logging.cc',
        'rtp_to_ntp.cc',
        'rw_lock.cc',
        'rw_lock_generic.cc',
        'rw_lock_generic.h',
        'rw_lock_posix.cc',
        'rw_lock_posix.h',
        'rw_lock_win.cc',
        'rw_lock_win.h',
        'set_thread_name_win.h',
        'sleep.cc',
        'sort.cc',
        'tick_util.cc',
        'thread.cc',
        'thread_posix.cc',
        'thread_posix.h',
        'thread_win.cc',
        'thread_win.h',
        'timestamp_extrapolator.cc',
        'trace_impl.cc',
        'trace_impl.h',
        'trace_posix.cc',
        'trace_posix.h',
        'trace_win.cc',
        'trace_win.h',
      ],
      'conditions': [
        ['enable_data_logging==1', {
          'sources!': [ 'data_log_no_op.cc', ],
        }, {
          'sources!': [ 'data_log.cc', ],
        },],
        ['OS=="android"', {
          'defines': [
            'WEBRTC_THREAD_RR',
            # TODO(leozwang): Investigate CLOCK_REALTIME and CLOCK_MONOTONIC
            # support on Android. Keep WEBRTC_CLOCK_TYPE_REALTIME for now,
            # remove it after I verify that CLOCK_MONOTONIC is fully functional
            # with condition and event functions in system_wrappers.
            'WEBRTC_CLOCK_TYPE_REALTIME',
           ],
          'dependencies': [ 'cpu_features_android', ],
          'link_settings': {
            'libraries': [
              '-llog',
            ],
          },
        }, {  # OS!="android"
          'sources!': [
            '../interface/logcat_trace_context.h',
            'logcat_trace_context.cc',
          ],
        }],
        ['OS=="linux"', {
          'defines': [
            'WEBRTC_THREAD_RR',
            # TODO(andrew): can we select this automatically?
            # Define this if the Linux system does not support CLOCK_MONOTONIC.
            #'WEBRTC_CLOCK_TYPE_REALTIME',
          ],
          'link_settings': {
            'libraries': [ '-lrt', ],
          },
        }],
        ['OS=="mac"', {
          'link_settings': {
            'libraries': [ '$(SDKROOT)/System/Library/Frameworks/ApplicationServices.framework', ],
          },
          'sources!': [
            'atomic32_posix.cc',
          ],
        }],
        ['OS=="ios" or OS=="mac"', {
          'defines': [
            'WEBRTC_THREAD_RR',
            'WEBRTC_CLOCK_TYPE_REALTIME',
          ],
        }],
        ['OS=="win"', {
          'link_settings': {
            'libraries': [ '-lwinmm.lib', ],
          },
        }],
      ], # conditions
      'target_conditions': [
        # We need to do this in a target_conditions block to override the
        # filename_rules filters.
        ['OS=="ios"', {
          # Pull in specific Mac files for iOS (which have been filtered out
          # by file name rules).
          'sources/': [
            ['include', '^atomic32_mac\\.'],
          ],
          'sources!': [
            'atomic32_posix.cc',
          ],
        }],
      ],
      # Disable warnings to enable Win64 build, issue 1323.
      'msvs_disabled_warnings': [
        4267,  # size_t to int truncation.
        4334,  # Ignore warning on shift operator promotion.
      ],
    }, {
      'target_name': 'field_trial_default',
      'type': 'static_library',
      'sources': [
        'field_trial_default.cc',
      ],
      'dependencies': [
        'system_wrappers',
      ]
    },
  ], # targets
  'conditions': [
    ['OS=="android"', {
      'targets': [
        {
          'target_name': 'cpu_features_android',
          'type': 'static_library',
          'sources': [
            'cpu_features_android.c',
          ],
          'conditions': [
            ['android_webview_build == 1', {
              'libraries': [
                'cpufeatures.a'
              ],
            }, {
              'dependencies': [
                '<(android_ndk_root)/android_tools_ndk.gyp:cpu_features',
              ],
            }],
          ],
        },
      ],
    }],
  ], # conditions
}

