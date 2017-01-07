# Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

{
  'includes': [ '../build/common.gypi', ],
  'conditions': [
    ['os_posix==1 and OS!="mac" and OS!="ios"', {
      'conditions': [
        ['sysroot!=""', {
          'variables': {
            'pkg-config': '../../../build/linux/pkg-config-wrapper "<(sysroot)" "<(target_arch)"',
          },
        }, {
          'variables': {
            'pkg-config': 'pkg-config'
          },
        }],
      ],
    }],
  ],
  'targets': [
    {
      # The subset of rtc_base approved for use outside of libjingle.
      'target_name': 'rtc_base_approved',
      'type': 'static_library',
      'sources': [
        'array_view.h',
        'atomicops.h',
        'bind.h',
        'bitbuffer.cc',
        'bitbuffer.h',
        'buffer.h',
        'bufferqueue.cc',
        'bufferqueue.h',
        'bytebuffer.cc',
        'bytebuffer.h',
        'byteorder.h',
        'checks.cc',
        'checks.h',
        'constructormagic.h',
        'copyonwritebuffer.cc',
        'copyonwritebuffer.h',
        'criticalsection.cc',
        'criticalsection.h',
        'deprecation.h',
        'event.cc',
        'event.h',
        'event_tracer.cc',
        'event_tracer.h',
        'exp_filter.cc',
        'exp_filter.h',
        'location.h',
        'location.cc',
        'md5.cc',
        'md5.h',
        'md5digest.cc',
        'md5digest.h',
        'mod_ops.h',
        'onetimeevent.h',
        'optional.h',
        'platform_file.cc',
        'platform_file.h',
        'platform_thread.cc',
        'platform_thread.h',
        'platform_thread_types.h',
        'random.cc',
        'random.h',
        'rate_statistics.cc',
        'rate_statistics.h',
        'ratetracker.cc',
        'ratetracker.h',
        'refcount.h',
        'safe_conversions.h',
        'safe_conversions_impl.h',
        'scoped_ref_ptr.h',
        'stringencode.cc',
        'stringencode.h',
        'stringutils.cc',
        'stringutils.h',
        'swap_queue.h',
        'systeminfo.cc',
        'systeminfo.h',
        'template_util.h',
        'thread_annotations.h',
        'thread_checker.h',
        'thread_checker_impl.cc',
        'thread_checker_impl.h',
        'timestampaligner.cc',
        'timestampaligner.h',
        'timeutils.cc',
        'timeutils.h',
        'trace_event.h',
      ],
      'conditions': [
        ['build_with_chromium==1', {
          'dependencies': [
            '<(DEPTH)/base/base.gyp:base',
          ],
          'include_dirs': [
            '../../webrtc_overrides',
          ],
          'sources': [
            '../../webrtc_overrides/webrtc/base/logging.cc',
            '../../webrtc_overrides/webrtc/base/logging.h',
          ],
        }, {
          'sources': [
            'logging.cc',
            'logging.h',
            'logging_mac.mm',
          ],
        }],
        ['OS=="mac" and build_with_chromium==0', {
          'all_dependent_settings': {
            'xcode_settings': {
              'OTHER_LDFLAGS': [
                # needed for logging_mac.mm
                '-framework Foundation',
              ],
            },
          },
        }], # OS=="mac" and build_with_chromium==0
      ],
    },
    {
      'target_name': 'rtc_task_queue',
      'type': 'static_library',
      'dependencies': [
        'rtc_base_approved',
      ],
      'sources': [
        'task_queue.h',
        'task_queue_posix.h',
      ],
      'conditions': [
        ['build_libevent==1', {
          'dependencies': [
            '<(DEPTH)/base/third_party/libevent/libevent.gyp:libevent',
          ],
        }],
        ['enable_libevent==1', {
          'sources': [
            'task_queue_libevent.cc',
            'task_queue_posix.cc',
          ],
        }, {
          # If not libevent, fall back to the other task queues.
          'conditions': [
            ['OS=="mac" or OS=="ios"', {
             'sources': [
               'task_queue_gcd.cc',
               'task_queue_posix.cc',
             ],
            }],
            ['OS=="win"', {
              'sources': [ 'task_queue_win.cc' ],
            }]
          ],
        }],
      ],
    },
    {
      'target_name': 'rtc_base',
      'type': 'static_library',
      'dependencies': [
        '<(webrtc_root)/common.gyp:webrtc_common',
        'rtc_base_approved',
      ],
      'export_dependent_settings': [
        'rtc_base_approved',
      ],
      'defines': [
        'FEATURE_ENABLE_SSL',
        'SSL_USE_OPENSSL',
        'HAVE_OPENSSL_SSL_H',
        'LOGGING=1',
      ],
      'sources': [
        'arraysize.h',
        'asyncfile.cc',
        'asyncfile.h',
        'asyncinvoker.cc',
        'asyncinvoker.h',
        'asyncinvoker-inl.h',
        'asyncpacketsocket.cc',
        'asyncpacketsocket.h',
        'asyncresolverinterface.cc',
        'asyncresolverinterface.h',
        'asyncsocket.cc',
        'asyncsocket.h',
        'asynctcpsocket.cc',
        'asynctcpsocket.h',
        'asyncudpsocket.cc',
        'asyncudpsocket.h',
        'autodetectproxy.cc',
        'autodetectproxy.h',
        'base64.cc',
        'base64.h',
        'common.cc',
        'common.h',
        'crc32.cc',
        'crc32.h',
        'cryptstring.cc',
        'cryptstring.h',
        'diskcache.cc',
        'diskcache.h',
        'filerotatingstream.cc',
        'filerotatingstream.h',
        'fileutils.cc',
        'fileutils.h',
        'firewallsocketserver.cc',
        'firewallsocketserver.h',
        'flags.cc',
        'flags.h',
        'format_macros.h',
        'gunit_prod.h',
        'helpers.cc',
        'helpers.h',
        'httpbase.cc',
        'httpbase.h',
        'httpclient.cc',
        'httpclient.h',
        'httpcommon-inl.h',
        'httpcommon.cc',
        'httpcommon.h',
        'httprequest.cc',
        'httprequest.h',
        'iosfilesystem.mm',
        'ipaddress.cc',
        'ipaddress.h',
        'linked_ptr.h',
        'messagedigest.cc',
        'messagedigest.h',
        'messagehandler.cc',
        'messagehandler.h',
        'messagequeue.cc',
        'messagequeue.h',
        'nethelpers.cc',
        'nethelpers.h',
        'network.cc',
        'network.h',
        'networkmonitor.cc',
        'networkmonitor.h',
        'nullsocketserver.cc',
        'nullsocketserver.h',
        'openssl.h',
        'openssladapter.cc',
        'openssladapter.h',
        'openssldigest.cc',
        'openssldigest.h',
        'opensslidentity.cc',
        'opensslidentity.h',
        'opensslstreamadapter.cc',
        'opensslstreamadapter.h',
        'pathutils.cc',
        'pathutils.h',
        'physicalsocketserver.cc',
        'physicalsocketserver.h',
        'proxydetect.cc',
        'proxydetect.h',
        'proxyinfo.cc',
        'proxyinfo.h',
        'ratelimiter.cc',
        'ratelimiter.h',
        'rtccertificate.cc',
        'rtccertificate.h',
        'rtccertificategenerator.cc',
        'rtccertificategenerator.h',
        'sha1.cc',
        'sha1.h',
        'sha1digest.cc',
        'sha1digest.h',
        'sharedexclusivelock.cc',
        'sharedexclusivelock.h',
        'signalthread.cc',
        'signalthread.h',
        'sigslot.cc',
        'sigslot.h',
        'sigslotrepeater.h',
        'socket.h',
        'socketadapters.cc',
        'socketadapters.h',
        'socketaddress.cc',
        'socketaddress.h',
        'socketaddresspair.cc',
        'socketaddresspair.h',
        'socketfactory.h',
        'socketpool.cc',
        'socketpool.h',
        'socketserver.h',
        'socketstream.cc',
        'socketstream.h',
        'ssladapter.cc',
        'ssladapter.h',
        'sslfingerprint.cc',
        'sslfingerprint.h',
        'sslidentity.cc',
        'sslidentity.h',
        'sslsocketfactory.cc',
        'sslsocketfactory.h',
        'sslstreamadapter.cc',
        'sslstreamadapter.h',
        'stream.cc',
        'stream.h',
        'task.cc',
        'task.h',
        'taskparent.cc',
        'taskparent.h',
        'taskrunner.cc',
        'taskrunner.h',
        'thread.cc',
        'thread.h',
        'timing.cc',
        'timing.h',
        'urlencode.cc',
        'urlencode.h',
        'worker.cc',
        'worker.h',
      ],
      # TODO(henrike): issue 3307, make rtc_base build without disabling
      # these flags.
      'cflags!': [
        '-Wextra',
        '-Wall',
      ],
      'direct_dependent_settings': {
        'defines': [
          'FEATURE_ENABLE_SSL',
          'SSL_USE_OPENSSL',
          'HAVE_OPENSSL_SSL_H',
        ],
      },
      'include_dirs': [
        '../../third_party/jsoncpp/overrides/include',
        '../../third_party/jsoncpp/source/include',
      ],
      'conditions': [
        ['build_with_chromium==1', {
          'include_dirs': [
            '../../webrtc_overrides',
            '../../boringssl/src/include',
          ],
          'conditions': [
            ['OS=="win"', {
              'sources': [
                '../../webrtc_overrides/webrtc/base/win32socketinit.cc',
              ],
            }],
          ],
          'defines': [
            'NO_MAIN_THREAD_WRAPPING',
          ],
          'direct_dependent_settings': {
            'defines': [
              'NO_MAIN_THREAD_WRAPPING',
            ],
          },
        }, {
          'sources': [
            'bandwidthsmoother.cc',
            'bandwidthsmoother.h',
            'callback.h',
            'fileutils_mock.h',
            'httpserver.cc',
            'httpserver.h',
            'json.cc',
            'json.h',
            'logsinks.cc',
            'logsinks.h',
            'mathutils.h',
            'multipart.cc',
            'multipart.h',
            'natserver.cc',
            'natserver.h',
            'natsocketfactory.cc',
            'natsocketfactory.h',
            'nattypes.cc',
            'nattypes.h',
            'optionsfile.cc',
            'optionsfile.h',
            'profiler.cc',
            'profiler.h',
            'proxyserver.cc',
            'proxyserver.h',
            'referencecountedsingletonfactory.h',
            'rollingaccumulator.h',
            'scopedptrcollection.h',
            'sec_buffer.h',
            'sslconfig.h',
            'sslroots.h',
            'testbase64.h',
            'testclient.cc',
            'testclient.h',
            'transformadapter.cc',
            'transformadapter.h',
            'versionparsing.cc',
            'versionparsing.h',
            'virtualsocketserver.cc',
            'virtualsocketserver.h',
            'window.h',
            'windowpicker.h',
            'windowpickerfactory.h',
          ],
          'conditions': [
            ['build_json==1', {
              'dependencies': [
                '<(DEPTH)/third_party/jsoncpp/jsoncpp.gyp:jsoncpp',
              ],
            }, {
              'include_dirs': [
                '<(json_root)',
              ],
              'defines': [
                # When defined changes the include path for json.h to where it
                # is expected to be when building json outside of the standalone
                # build.
                'WEBRTC_EXTERNAL_JSON',
              ],
            }],
            ['OS=="linux"', {
              'sources': [
                'dbus.cc',
                'dbus.h',
                'libdbusglibsymboltable.cc',
                'libdbusglibsymboltable.h',
                'linuxfdwalk.c',
                'linuxfdwalk.h',
              ],
            }],
            ['os_posix==1', {
              'sources': [
                'latebindingsymboltable.cc',
                'latebindingsymboltable.h',
                'posix.cc',
                'posix.h',
              ],
            }],
            ['OS=="mac"', {
              'sources': [
                'macasyncsocket.cc',
                'macasyncsocket.h',
                'maccocoasocketserver.h',
                'maccocoasocketserver.mm',
                'macsocketserver.cc',
                'macsocketserver.h',
                'macwindowpicker.cc',
                'macwindowpicker.h',
              ],
            }],
            ['OS=="win"', {
              'sources': [
                'diskcache_win32.cc',
                'diskcache_win32.h',
                'win32regkey.cc',
                'win32regkey.h',
                'win32socketinit.cc',
                'win32socketinit.h',
                'win32socketserver.cc',
                'win32socketserver.h',
              ],
            }],
            ['OS=="win" and clang==1', {
              'msvs_settings': {
                'VCCLCompilerTool': {
                  'AdditionalOptions': [
                    # Disable warnings failing when compiling with Clang on Windows.
                    # https://bugs.chromium.org/p/webrtc/issues/detail?id=5366
                    '-Wno-sign-compare',
                    '-Wno-missing-braces',
                  ],
                },
              },
            }],
          ],  # conditions
        }],  # build_with_chromium==0
        ['OS=="android"', {
          'sources': [
            'ifaddrs-android.cc',
            'ifaddrs-android.h',
          ],
          'link_settings': {
            'libraries': [
              '-llog',
              '-lGLESv2',
            ],
          },
        }],
        ['(OS=="mac" or OS=="ios") and nacl_untrusted_build==0', {
          'sources': [
            'maccocoathreadhelper.h',
            'maccocoathreadhelper.mm',
            'macconversion.cc',
            'macconversion.h',
            'macifaddrs_converter.cc',
            'scoped_autorelease_pool.h',
            'scoped_autorelease_pool.mm',
          ],
        }],
        ['OS=="ios"', {
          'all_dependent_settings': {
            'xcode_settings': {
              'OTHER_LDFLAGS': [
                '-framework CFNetwork',
                '-framework Foundation',
                '-framework Security',
                '-framework SystemConfiguration',
                '-framework UIKit',
              ],
            },
          },
        }],
        ['use_x11==1', {
          'sources': [
            'x11windowpicker.cc',
            'x11windowpicker.h',
          ],
          'link_settings': {
            'libraries': [
              '-ldl',
              '-lrt',
              '-lXext',
              '-lX11',
              '-lXcomposite',
              '-lXrender',
            ],
          },
        }],
        ['OS=="linux"', {
          'link_settings': {
            'libraries': [
              '-ldl',
              '-lrt',
            ],
          },
        }],
        ['OS=="mac"', {
          'sources': [
            'macutils.cc',
            'macutils.h',
          ],
          'all_dependent_settings': {
            'link_settings': {
              'xcode_settings': {
                'OTHER_LDFLAGS': [
                  '-framework Cocoa',
                  '-framework Foundation',
                  '-framework IOKit',
                  '-framework Security',
                  '-framework SystemConfiguration',
                ],
              },
            },
          },
          'conditions': [
            ['target_arch=="ia32"', {
              'all_dependent_settings': {
                'link_settings': {
                  'xcode_settings': {
                    'OTHER_LDFLAGS': [
                      '-framework Carbon',
                    ],
                  },
                },
              },
            }],
          ],
        }],
        ['OS=="win" and nacl_untrusted_build==0', {
          'sources': [
            'win32.cc',
            'win32.h',
            'win32filesystem.cc',
            'win32filesystem.h',
            'win32securityerrors.cc',
            'win32window.cc',
            'win32window.h',
            'win32windowpicker.cc',
            'win32windowpicker.h',
            'winfirewall.cc',
            'winfirewall.h',
            'winping.cc',
            'winping.h',
          ],
          'link_settings': {
            'libraries': [
              '-lcrypt32.lib',
              '-liphlpapi.lib',
              '-lsecur32.lib',
            ],
          },
          # Suppress warnings about WIN32_LEAN_AND_MEAN.
          'msvs_disabled_warnings': [4005, 4703],
          'defines': [
            '_CRT_NONSTDC_NO_DEPRECATE',
          ],
        }],
        ['os_posix==1', {
          'sources': [
            'ifaddrs_converter.cc',
            'ifaddrs_converter.h',
            'unixfilesystem.cc',
            'unixfilesystem.h',
          ],
          'configurations': {
            'Debug_Base': {
              'defines': [
                # Chromium's build/common.gypi defines this for all posix
                # _except_ for ios & mac.  We want it there as well, e.g.
                # because ASSERT and friends trigger off of it.
                '_DEBUG',
              ],
            },
          }
        }],
        ['OS=="ios" or (OS=="mac" and target_arch!="ia32")', {
          'defines': [
            'CARBON_DEPRECATED=YES',
          ],
        }],
        ['OS=="linux" or OS=="android"', {
          'sources': [
            'linux.cc',
            'linux.h',
          ],
        }],
        ['build_ssl==1', {
          'dependencies': [
            '<(DEPTH)/third_party/boringssl/boringssl.gyp:boringssl',
          ],
        }, {
          'include_dirs': [
            '<(ssl_root)',
          ],
        }],
      ],
    },
    {
     'target_name': 'gtest_prod',
      'type': 'static_library',
      'sources': [
        'gtest_prod_util.h',
      ],
    },
  ],
}
