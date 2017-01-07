dnl Process this file with autoconf to produce a configure script. -*-m4-*-

dnl The package_version file will be automatically synced to the git revision
dnl by the update_version script when configured in the repository, but will
dnl remain constant in tarball releases unless it is manually edited.
m4_define([CURRENT_VERSION],
          m4_esyscmd([ ./update_version 2>/dev/null || true
                       if test -e package_version; then
                           . ./package_version
                           printf "$PACKAGE_VERSION"
                       else
                           printf "unknown"
                       fi ]))

AC_INIT([opus],[CURRENT_VERSION],[opus@xiph.org])

AC_CONFIG_SRCDIR(src/opus_encoder.c)
AC_CONFIG_MACRO_DIR([m4])

dnl enable silent rules on automake 1.11 and later
m4_ifdef([AM_SILENT_RULES], [AM_SILENT_RULES([yes])])

# For libtool.
dnl Please update these for releases.
OPUS_LT_CURRENT=5
OPUS_LT_REVISION=3
OPUS_LT_AGE=5

AC_SUBST(OPUS_LT_CURRENT)
AC_SUBST(OPUS_LT_REVISION)
AC_SUBST(OPUS_LT_AGE)

AM_INIT_AUTOMAKE([no-define])
AM_MAINTAINER_MODE([enable])

AC_CANONICAL_HOST
AC_MINGW32
AM_PROG_LIBTOOL
AM_PROG_CC_C_O

AC_PROG_CC_C99
AC_C_CONST
AC_C_INLINE

AM_PROG_AS

AC_DEFINE([OPUS_BUILD], [], [This is a build of OPUS])

#Use a hacked up version of autoconf's AC_C_RESTRICT because it's not
#strong enough a test to detect old buggy versions of GCC (e.g. 2.95.3)
#Note: Both this and the test for variable-size arrays below are also
#      done by AC_PROG_CC_C99, but not thoroughly enough apparently.
AC_CACHE_CHECK([for C/C++ restrict keyword], ac_cv_c_restrict,
  [ac_cv_c_restrict=no
   # The order here caters to the fact that C++ does not require restrict.
   for ac_kw in __restrict __restrict__ _Restrict restrict; do
     AC_COMPILE_IFELSE([AC_LANG_PROGRAM(
      [[typedef int * int_ptr;
        int foo (int_ptr $ac_kw ip, int * $ac_kw baz[]) {
        return ip[0];
       }]],
      [[int s[1];
        int * $ac_kw t = s;
        t[0] = 0;
        return foo(t, (void *)0)]])],
      [ac_cv_c_restrict=$ac_kw])
     test "$ac_cv_c_restrict" != no && break
   done
  ])

AH_VERBATIM([restrict],
[/* Define to the equivalent of the C99 'restrict' keyword, or to
   nothing if this is not supported.  Do not define if restrict is
   supported directly.  */
#undef restrict
/* Work around a bug in Sun C++: it does not support _Restrict or
   __restrict__, even though the corresponding Sun C compiler ends up with
   "#define restrict _Restrict" or "#define restrict __restrict__" in the
   previous line.  Perhaps some future version of Sun C++ will work with
   restrict; if so, hopefully it defines __RESTRICT like Sun C does.  */
#if defined __SUNPRO_CC && !defined __RESTRICT
# define _Restrict
# define __restrict__
#endif])

case $ac_cv_c_restrict in
   restrict) ;;
   no) AC_DEFINE([restrict], []) ;;
   *)  AC_DEFINE_UNQUOTED([restrict], [$ac_cv_c_restrict]) ;;
esac

AC_MSG_CHECKING(for C99 variable-size arrays)
AC_COMPILE_IFELSE([AC_LANG_PROGRAM([],
                   [[static int x; char a[++x]; a[sizeof a - 1] = 0; int N; return a[0];]])],
    [ has_var_arrays=yes
      use_alloca="no (using var arrays)"
      AC_DEFINE([VAR_ARRAYS], [1], [Use C99 variable-size arrays])
    ],[
      has_var_arrays=no
    ])
AC_MSG_RESULT([$has_var_arrays])

AS_IF([test "$has_var_arrays" = "no"],
  [
   AC_CHECK_HEADERS([alloca.h])
   AC_MSG_CHECKING(for alloca)
   AC_COMPILE_IFELSE([AC_LANG_PROGRAM([[#include <alloca.h>]],
                                      [[int foo=10; int *array = alloca(foo);]])],
     [ use_alloca=yes;
       AC_DEFINE([USE_ALLOCA], [], [Make use of alloca])
     ],[
       use_alloca=no
     ])
   AC_MSG_RESULT([$use_alloca])
  ])

LT_LIB_M

AC_ARG_ENABLE([fixed-point],
    [AS_HELP_STRING([--enable-fixed-point],
                    [compile without floating point (for machines without a fast enough FPU)])],,
    [enable_fixed_point=no])

AS_IF([test "$enable_fixed_point" = "yes"],[
  enable_float="no"
  AC_DEFINE([FIXED_POINT], [1], [Compile as fixed-point (for machines without a fast enough FPU)])
  PC_BUILD="fixed-point"
],[
  enable_float="yes";
  PC_BUILD="floating-point"
])

AM_CONDITIONAL([FIXED_POINT], [test "$enable_fixed_point" = "yes"])

AC_ARG_ENABLE([fixed-point-debug],
    [AS_HELP_STRING([--enable-fixed-point-debug], [debug fixed-point implementation])],,
    [enable_fixed_point_debug=no])

AS_IF([test "$enable_fixed_point_debug" = "yes"],[
  AC_DEFINE([FIXED_DEBUG], [1], [Debug fixed-point implementation])
])

AC_ARG_ENABLE([float_api],
    [AS_HELP_STRING([--disable-float-api],
                    [compile without the floating point API (for machines with no float library)])],,
    [enable_float_api=yes])

AM_CONDITIONAL([DISABLE_FLOAT_API], [test "$enable_float_api" = "no"])

AS_IF([test "$enable_float_api" = "no"],[
  AC_DEFINE([DISABLE_FLOAT_API], [1], [Do not build the float API])
])

AC_ARG_ENABLE([custom-modes],
    [AS_HELP_STRING([--enable-custom-modes], [enable non-Opus modes, e.g. 44.1 kHz & 2^n frames])],,
    [enable_custom_modes=no])

AS_IF([test "$enable_custom_modes" = "yes"],[
  AC_DEFINE([CUSTOM_MODES], [1], [Custom modes])
  PC_BUILD="$PC_BUILD, custom modes"
])

AM_CONDITIONAL([CUSTOM_MODES], [test "$enable_custom_modes" = "yes"])

has_float_approx=no
#case "$host_cpu" in
#i[[3456]]86 | x86_64 | powerpc64 | powerpc32 | ia64)
#  has_float_approx=yes
#  ;;
#esac

AC_ARG_ENABLE([float-approx],
    [AS_HELP_STRING([--enable-float-approx], [enable fast approximations for floating point])],
    [if test "$enable_float_approx" = "yes"; then
       AC_WARN([Floating point approximations are not supported on all platforms.])
     fi
    ],
    [enable_float_approx=$has_float_approx])

AS_IF([test "$enable_float_approx" = "yes"],[
  AC_DEFINE([FLOAT_APPROX], [1], [Float approximations])
])

AC_ARG_ENABLE([asm],
    [AS_HELP_STRING([--disable-asm], [Disable assembly optimizations])],,
    [enable_asm=yes])

AC_ARG_ENABLE([rtcd],
    [AS_HELP_STRING([--disable-rtcd], [Disable run-time CPU capabilities detection])],,
    [enable_rtcd=yes])

AC_ARG_ENABLE([intrinsics],
    [AS_HELP_STRING([--disable-intrinsics], [Disable intrinsics optimizations])],,
    [enable_intrinsics=yes])

rtcd_support=no
cpu_arm=no

AS_IF([test x"${enable_asm}" = x"yes"],[
    inline_optimization="No inline ASM for your platform, please send patches"
    case $host_cpu in
      arm*)
        dnl Currently we only have asm for fixed-point
        AS_IF([test "$enable_float" != "yes"],[
            cpu_arm=yes
            AC_DEFINE([OPUS_ARM_ASM], [],  [Make use of ARM asm optimization])
            AS_GCC_INLINE_ASSEMBLY(
                [inline_optimization="ARM"],
                [inline_optimization="disabled"]
            )
            AS_ASM_ARM_EDSP([OPUS_ARM_INLINE_EDSP=1],[OPUS_ARM_INLINE_EDSP=0])
            AS_ASM_ARM_MEDIA([OPUS_ARM_INLINE_MEDIA=1],
                [OPUS_ARM_INLINE_MEDIA=0])
            AS_ASM_ARM_NEON([OPUS_ARM_INLINE_NEON=1],[OPUS_ARM_INLINE_NEON=0])
            AS_IF([test x"$inline_optimization" = x"ARM"],[
                AM_CONDITIONAL([OPUS_ARM_INLINE_ASM],[true])
                AC_DEFINE([OPUS_ARM_INLINE_ASM], 1,
                    [Use generic ARMv4 inline asm optimizations])
                AS_IF([test x"$OPUS_ARM_INLINE_EDSP" = x"1"],[
                    AC_DEFINE([OPUS_ARM_INLINE_EDSP], [1],
                        [Use ARMv5E inline asm optimizations])
                    inline_optimization="$inline_optimization (EDSP)"
                ])
                AS_IF([test x"$OPUS_ARM_INLINE_MEDIA" = x"1"],[
                    AC_DEFINE([OPUS_ARM_INLINE_MEDIA], [1],
                        [Use ARMv6 inline asm optimizations])
                    inline_optimization="$inline_optimization (Media)"
                ])
                AS_IF([test x"$OPUS_ARM_INLINE_NEON" = x"1"],[
                    AC_DEFINE([OPUS_ARM_INLINE_NEON], 1,
                        [Use ARM NEON inline asm optimizations])
                    inline_optimization="$inline_optimization (NEON)"
                ])
            ])
            dnl We need Perl to translate RVCT-syntax asm to gas syntax.
            AC_CHECK_PROG([HAVE_PERL], perl, yes, no)
            AS_IF([test x"$HAVE_PERL" = x"yes"],[
                AM_CONDITIONAL([OPUS_ARM_EXTERNAL_ASM],[true])
                asm_optimization="ARM"
                AS_IF([test x"$OPUS_ARM_INLINE_EDSP" = x"1"], [
                    OPUS_ARM_PRESUME_EDSP=1
                    OPUS_ARM_MAY_HAVE_EDSP=1
                ],
                [
                    OPUS_ARM_PRESUME_EDSP=0
                    OPUS_ARM_MAY_HAVE_EDSP=0
                ])
                AS_IF([test x"$OPUS_ARM_INLINE_MEDIA" = x"1"], [
                    OPUS_ARM_PRESUME_MEDIA=1
                    OPUS_ARM_MAY_HAVE_MEDIA=1
                ],
                [
                    OPUS_ARM_PRESUME_MEDIA=0
                    OPUS_ARM_MAY_HAVE_MEDIA=0
                ])
                AS_IF([test x"$OPUS_ARM_INLINE_NEON" = x"1"], [
                    OPUS_ARM_PRESUME_NEON=1
                    OPUS_ARM_MAY_HAVE_NEON=1
                ],
                [
                    OPUS_ARM_PRESUME_NEON=0
                    OPUS_ARM_MAY_HAVE_NEON=0
                ])
                AS_IF([test x"$enable_rtcd" = x"yes"],[
                    AS_IF([test x"$OPUS_ARM_MAY_HAVE_EDSP" != x"1"],[
                        AC_MSG_NOTICE(
                          [Trying to force-enable armv5e EDSP instructions...])
                        AS_ASM_ARM_EDSP_FORCE([OPUS_ARM_MAY_HAVE_EDSP=1])
                    ])
                    AS_IF([test x"$OPUS_ARM_MAY_HAVE_MEDIA" != x"1"],[
                        AC_MSG_NOTICE(
                          [Trying to force-enable ARMv6 media instructions...])
                        AS_ASM_ARM_MEDIA_FORCE([OPUS_ARM_MAY_HAVE_MEDIA=1])
                    ])
                    AS_IF([test x"$OPUS_ARM_MAY_HAVE_NEON" != x"1"],[
                        AC_MSG_NOTICE(
                          [Trying to force-enable NEON instructions...])
                        AS_ASM_ARM_NEON_FORCE([OPUS_ARM_MAY_HAVE_NEON=1])
                    ])
                ])
                rtcd_support=
                AS_IF([test x"$OPUS_ARM_MAY_HAVE_EDSP" = x"1"],[
                    AC_DEFINE(OPUS_ARM_MAY_HAVE_EDSP, 1,
                        [Define if assembler supports EDSP instructions])
                    AS_IF([test x"$OPUS_ARM_PRESUME_EDSP" = x"1"],[
                        AC_DEFINE(OPUS_ARM_PRESUME_EDSP, 1,
                          [Define if binary requires EDSP instruction support])
                        asm_optimization="$asm_optimization (EDSP)"
                    ],
                        [rtcd_support="$rtcd_support (EDSP)"]
                    )
                ])
                AC_SUBST(OPUS_ARM_MAY_HAVE_EDSP)
                AS_IF([test x"$OPUS_ARM_MAY_HAVE_MEDIA" = x"1"],[
                    AC_DEFINE(OPUS_ARM_MAY_HAVE_MEDIA, 1,
                      [Define if assembler supports ARMv6 media instructions])
                    AS_IF([test x"$OPUS_ARM_PRESUME_MEDIA" = x"1"],[
                        AC_DEFINE(OPUS_ARM_PRESUME_MEDIA, 1,
                          [Define if binary requires ARMv6 media instruction support])
                        asm_optimization="$asm_optimization (Media)"
                    ],
                        [rtcd_support="$rtcd_support (Media)"]
                    )
                ])
                AC_SUBST(OPUS_ARM_MAY_HAVE_MEDIA)
                AS_IF([test x"$OPUS_ARM_MAY_HAVE_NEON" = x"1"],[
                    AC_DEFINE(OPUS_ARM_MAY_HAVE_NEON, 1,
                      [Define if compiler supports NEON instructions])
                    AS_IF([test x"$OPUS_ARM_PRESUME_NEON" = x"1"], [
                        AC_DEFINE(OPUS_ARM_PRESUME_NEON, 1,
                          [Define if binary requires NEON instruction support])
                        asm_optimization="$asm_optimization (NEON)"
                    ],
                        [rtcd_support="$rtcd_support (NEON)"]
                    )
                ])
                AC_SUBST(OPUS_ARM_MAY_HAVE_NEON)
                dnl Make sure turning on RTCD gets us at least one
                dnl instruction set.
                AS_IF([test x"$rtcd_support" != x""],
                    [rtcd_support=ARM"$rtcd_support"],
                    [rtcd_support="no"]
                )
                AC_MSG_CHECKING([for apple style tools])
                AC_PREPROC_IFELSE([AC_LANG_PROGRAM([
#ifndef __APPLE__
#error 1
#endif],[])],
                    [AC_MSG_RESULT([yes]); ARM2GNU_PARAMS="--apple"],
                    [AC_MSG_RESULT([no]); ARM2GNU_PARAMS=""])
                AC_SUBST(ARM2GNU_PARAMS)
            ],
            [
                AC_MSG_WARN(
                  [*** ARM assembly requires perl -- disabling optimizations])
                asm_optimization="(missing perl dependency for ARM)"
            ])
        ])
        ;;
    esac
],[
   inline_optimization="disabled"
   asm_optimization="disabled"
])

AM_CONDITIONAL([OPUS_ARM_INLINE_ASM],
    [test x"${inline_optimization%% *}" = x"ARM"])
AM_CONDITIONAL([OPUS_ARM_EXTERNAL_ASM],
    [test x"${asm_optimization%% *}" = x"ARM"])

AM_CONDITIONAL([HAVE_SSE], [false])
AM_CONDITIONAL([HAVE_SSE2], [false])
AM_CONDITIONAL([HAVE_SSE4_1], [false])
AM_CONDITIONAL([HAVE_AVX], [false])

m4_define([DEFAULT_X86_SSE_CFLAGS], [-msse])
m4_define([DEFAULT_X86_SSE2_CFLAGS], [-msse2])
m4_define([DEFAULT_X86_SSE4_1_CFLAGS], [-msse4.1])
m4_define([DEFAULT_X86_AVX_CFLAGS], [-mavx])
m4_define([DEFAULT_ARM_NEON_INTR_CFLAGS], [-mfpu=neon])
# With GCC on ARM32 softfp architectures (e.g. Android, or older Ubuntu) you need to specify
# -mfloat-abi=softfp for -mfpu=neon to work.  However, on ARM32 hardfp architectures (e.g. newer Ubuntu),
# this option will break things.

# As a heuristic, if host matches arm*eabi* but not arm*hf*, it's probably soft-float.
m4_define([DEFAULT_ARM_NEON_SOFTFP_INTR_CFLAGS], [-mfpu=neon -mfloat-abi=softfp])

AS_CASE([$host],
        [arm*hf*], [AS_VAR_SET([RESOLVED_DEFAULT_ARM_NEON_INTR_CFLAGS], "DEFAULT_ARM_NEON_INTR_CFLAGS")],
        [arm*eabi*], [AS_VAR_SET([RESOLVED_DEFAULT_ARM_NEON_INTR_CFLAGS], "DEFAULT_ARM_NEON_SOFTFP_INTR_CFLAGS")],
        [AS_VAR_SET([RESOLVED_DEFAULT_ARM_NEON_INTR_CFLAGS], "DEFAULT_ARM_NEON_INTR_CFLAGS")])

AC_ARG_VAR([X86_SSE_CFLAGS], [C compiler flags to compile SSE intrinsics @<:@default=]DEFAULT_X86_SSE_CFLAGS[@:>@])
AC_ARG_VAR([X86_SSE2_CFLAGS], [C compiler flags to compile SSE2 intrinsics @<:@default=]DEFAULT_X86_SSE2_CFLAGS[@:>@])
AC_ARG_VAR([X86_SSE4_1_CFLAGS], [C compiler flags to compile SSE4.1 intrinsics @<:@default=]DEFAULT_X86_SSE4_1_CFLAGS[@:>@])
AC_ARG_VAR([X86_AVX_CFLAGS], [C compiler flags to compile AVX intrinsics @<:@default=]DEFAULT_X86_AVX_CFLAGS[@:>@])
AC_ARG_VAR([ARM_NEON_INTR_CFLAGS], [C compiler flags to compile ARM NEON intrinsics @<:@default=]DEFAULT_ARM_NEON_INTR_CFLAGS / DEFAULT_ARM_NEON_SOFTFP_INTR_CFLAGS[@:>@])

AS_VAR_SET_IF([X86_SSE_CFLAGS], [], [AS_VAR_SET([X86_SSE_CFLAGS], "DEFAULT_X86_SSE_CFLAGS")])
AS_VAR_SET_IF([X86_SSE2_CFLAGS], [], [AS_VAR_SET([X86_SSE2_CFLAGS], "DEFAULT_X86_SSE2_CFLAGS")])
AS_VAR_SET_IF([X86_SSE4_1_CFLAGS], [], [AS_VAR_SET([X86_SSE4_1_CFLAGS], "DEFAULT_X86_SSE4_1_CFLAGS")])
AS_VAR_SET_IF([X86_AVX_CFLAGS], [], [AS_VAR_SET([X86_AVX_CFLAGS], "DEFAULT_X86_AVX_CFLAGS")])
AS_VAR_SET_IF([ARM_NEON_INTR_CFLAGS], [], [AS_VAR_SET([ARM_NEON_INTR_CFLAGS], ["$RESOLVED_DEFAULT_ARM_NEON_INTR_CFLAGS"])])

AC_DEFUN([OPUS_PATH_NE10],
   [
      AC_ARG_WITH(NE10,
                  AC_HELP_STRING([--with-NE10=PFX],[Prefix where libNE10 is installed (optional)]),
                  NE10_prefix="$withval", NE10_prefix="")
      AC_ARG_WITH(NE10-libraries,
                  AC_HELP_STRING([--with-NE10-libraries=DIR],
                        [Directory where libNE10 library is installed (optional)]),
                  NE10_libraries="$withval", NE10_libraries="")
      AC_ARG_WITH(NE10-includes,
                  AC_HELP_STRING([--with-NE10-includes=DIR],
                                 [Directory where libNE10 header files are installed (optional)]),
                  NE10_includes="$withval", NE10_includes="")

      if test "x$NE10_libraries" != "x" ; then
         NE10_LIBS="-L$NE10_libraries"
      elif test "x$NE10_prefix" = "xno" || test "x$NE10_prefix" = "xyes" ; then
         NE10_LIBS=""
      elif test "x$NE10_prefix" != "x" ; then
         NE10_LIBS="-L$NE10_prefix/lib"
      elif test "x$prefix" != "xNONE" ; then
         NE10_LIBS="-L$prefix/lib"
      fi

      if test "x$NE10_prefix" != "xno" ; then
         NE10_LIBS="$NE10_LIBS -lNE10"
      fi

      if test "x$NE10_includes" != "x" ; then
         NE10_CFLAGS="-I$NE10_includes"
      elif test "x$NE10_prefix" = "xno" || test "x$NE10_prefix" = "xyes" ; then
         NE10_CFLAGS=""
      elif test "x$ogg_prefix" != "x" ; then
         NE10_CFLAGS="-I$NE10_prefix/include"
      elif test "x$prefix" != "xNONE"; then
         NE10_CFLAGS="-I$prefix/include"
      fi

      AC_MSG_CHECKING(for NE10)
      save_CFLAGS="$CFLAGS"; CFLAGS="$NE10_CFLAGS"
      save_LIBS="$LIBS"; LIBS="$NE10_LIBS $LIBM"
      AC_LINK_IFELSE(
         [
            AC_LANG_PROGRAM(
               [[#include <NE10_init.h>
               ]],
               [[
                  ne10_fft_cfg_float32_t cfg;
                  cfg = ne10_fft_alloc_c2c_float32_neon(480);
               ]]
            )
         ],[
            HAVE_ARM_NE10=1
            AC_MSG_RESULT([yes])
         ],[
            HAVE_ARM_NE10=0
            AC_MSG_RESULT([no])
            NE10_CFLAGS=""
            NE10_LIBS=""
         ]
      )
      CFLAGS="$save_CFLAGS"; LIBS="$save_LIBS"
      #Now we know if libNE10 is installed or not
      AS_IF([test x"$HAVE_ARM_NE10" = x"1"],
         [
            AC_DEFINE([HAVE_ARM_NE10], 1, [NE10 library is installed on host. Make sure it is on target!])
            AC_SUBST(HAVE_ARM_NE10)
            AC_SUBST(NE10_CFLAGS)
            AC_SUBST(NE10_LIBS)
         ]
      )
   ]
)

AS_IF([test x"$enable_intrinsics" = x"yes"],[
   intrinsics_support=""
   AS_CASE([$host_cpu],
   [arm*|aarch64*],
   [
      cpu_arm=yes
      OPUS_CHECK_INTRINSICS(
         [ARM Neon],
         [$ARM_NEON_INTR_CFLAGS],
         [OPUS_ARM_MAY_HAVE_NEON_INTR],
         [OPUS_ARM_PRESUME_NEON_INTR],
         [[#include <arm_neon.h>
         ]],
         [[
            static float32x4_t A0, A1, SUMM;
            SUMM = vmlaq_f32(SUMM, A0, A1);
            return (int)vgetq_lane_f32(SUMM, 0);
         ]]
      )
      AS_IF([test x"$OPUS_ARM_MAY_HAVE_NEON_INTR" = x"1" && test x"$OPUS_ARM_PRESUME_NEON_INTR" != x"1"],
          [
             OPUS_ARM_NEON_INTR_CFLAGS="$ARM_NEON_INTR_CFLAGS"
             AC_SUBST([OPUS_ARM_NEON_INTR_CFLAGS])
          ]
      )

      AS_IF([test x"$OPUS_ARM_MAY_HAVE_NEON_INTR" = x"1"],
      [
         AC_DEFINE([OPUS_ARM_MAY_HAVE_NEON_INTR], 1, [Compiler supports ARMv7/Aarch64 Neon Intrinsics])
         intrinsics_support="$intrinsics_support (NEON)"

         AS_IF([test x"$enable_rtcd" != x"no" && test x"$OPUS_ARM_PRESUME_NEON_INTR" != x"1"],
            [AS_IF([test x"$rtcd_support" = x"no"],
               [rtcd_support="ARM (NEON Intrinsics)"],
               [rtcd_support="$rtcd_support (NEON Intrinsics)"])])

         AS_IF([test x"$OPUS_ARM_PRESUME_NEON_INTR" = x"1"],
            [AC_DEFINE([OPUS_ARM_PRESUME_NEON_INTR], 1, [Define if binary requires NEON intrinsics support])])

         OPUS_PATH_NE10()
         AS_IF([test x"$NE10_LIBS" != x""],
         [
              intrinsics_support="$intrinsics_support (NE10)"
              AS_IF([test x"enable_rtcd" != x"" \
               && test x"$OPUS_ARM_PRESUME_NEON_INTR" != x"1"],
                 [rtcd_support="$rtcd_support (NE10)"])
         ])

         OPUS_CHECK_INTRINSICS(
            [Aarch64 Neon],
            [$ARM_NEON_INTR_CFLAGS],
            [OPUS_ARM_MAY_HAVE_AARCH64_NEON_INTR],
            [OPUS_ARM_PRESUME_AARCH64_NEON_INTR],
            [[#include <arm_neon.h>
            ]],
            [[
               static int32_t IN;
               static int16_t OUT;
               OUT = vqmovns_s32(IN);
            ]]
         )

         AS_IF([test x"$OPUS_ARM_PRESUME_AARCH64_NEON_INTR" = x"1"],
         [
            AC_DEFINE([OPUS_ARM_PRESUME_AARCH64_NEON_INTR], 1, [Define if binary requires Aarch64 Neon Intrinsics])
            intrinsics_support="$intrinsics_support (NEON [Aarch64])"
         ])

         AS_IF([test x"$intrinsics_support" = x""],
            [intrinsics_support=no],
            [intrinsics_support="ARM$intrinsics_support"])
      ],
      [
         AC_MSG_WARN([Compiler does not support ARM intrinsics])
         intrinsics_support=no
      ])
   ],
   [i?86|x86_64],
   [
      OPUS_CHECK_INTRINSICS(
         [SSE],
         [$X86_SSE_CFLAGS],
         [OPUS_X86_MAY_HAVE_SSE],
         [OPUS_X86_PRESUME_SSE],
         [[#include <xmmintrin.h>
           #include <time.h>
         ]],
         [[
             __m128 mtest;
             mtest = _mm_set1_ps((float)time(NULL));
             mtest = _mm_mul_ps(mtest, mtest);
             return _mm_cvtss_si32(mtest);
         ]]
      )
      AS_IF([test x"$OPUS_X86_MAY_HAVE_SSE" = x"1" && test x"$OPUS_X86_PRESUME_SSE" != x"1"],
          [
             OPUS_X86_SSE_CFLAGS="$X86_SSE_CFLAGS"
             AC_SUBST([OPUS_X86_SSE_CFLAGS])
          ]
      )
      OPUS_CHECK_INTRINSICS(
         [SSE2],
         [$X86_SSE2_CFLAGS],
         [OPUS_X86_MAY_HAVE_SSE2],
         [OPUS_X86_PRESUME_SSE2],
         [[#include <emmintrin.h>
           #include <time.h>
         ]],
         [[
            __m128i mtest;
            mtest = _mm_set1_epi32((int)time(NULL));
            mtest = _mm_mul_epu32(mtest, mtest);
            return _mm_cvtsi128_si32(mtest);
         ]]
      )
      AS_IF([test x"$OPUS_X86_MAY_HAVE_SSE2" = x"1" && test x"$OPUS_X86_PRESUME_SSE2" != x"1"],
          [
             OPUS_X86_SSE2_CFLAGS="$X86_SSE2_CFLAGS"
             AC_SUBST([OPUS_X86_SSE2_CFLAGS])
          ]
      )
      OPUS_CHECK_INTRINSICS(
         [SSE4.1],
         [$X86_SSE4_1_CFLAGS],
         [OPUS_X86_MAY_HAVE_SSE4_1],
         [OPUS_X86_PRESUME_SSE4_1],
         [[#include <smmintrin.h>
           #include <time.h>
         ]],
         [[
            __m128i mtest;
            mtest = _mm_set1_epi32((int)time(NULL));
            mtest = _mm_mul_epi32(mtest, mtest);
            return _mm_cvtsi128_si32(mtest);
         ]]
      )
      AS_IF([test x"$OPUS_X86_MAY_HAVE_SSE4_1" = x"1" && test x"$OPUS_X86_PRESUME_SSE4_1" != x"1"],
          [
             OPUS_X86_SSE4_1_CFLAGS="$X86_SSE4_1_CFLAGS"
             AC_SUBST([OPUS_X86_SSE4_1_CFLAGS])
          ]
      )
      OPUS_CHECK_INTRINSICS(
         [AVX],
         [$X86_AVX_CFLAGS],
         [OPUS_X86_MAY_HAVE_AVX],
         [OPUS_X86_PRESUME_AVX],
         [[#include <immintrin.h>
           #include <time.h>
         ]],
         [[
             __m256 mtest;
             mtest = _mm256_set1_ps((float)time(NULL));
             mtest = _mm256_addsub_ps(mtest, mtest);
             return _mm_cvtss_si32(_mm256_extractf128_ps(mtest, 0));
         ]]
      )
      AS_IF([test x"$OPUS_X86_MAY_HAVE_AVX" = x"1" && test x"$OPUS_X86_PRESUME_AVX" != x"1"],
          [
             OPUS_X86_AVX_CFLAGS="$X86_AVX_CFLAGS"
             AC_SUBST([OPUS_X86_AVX_CFLAGS])
          ]
      )
         AS_IF([test x"$rtcd_support" = x"no"], [rtcd_support=""])
         AS_IF([test x"$OPUS_X86_MAY_HAVE_SSE" = x"1"],
         [
            AC_DEFINE([OPUS_X86_MAY_HAVE_SSE], 1, [Compiler supports X86 SSE Intrinsics])
            intrinsics_support="$intrinsics_support SSE"

            AS_IF([test x"$OPUS_X86_PRESUME_SSE" = x"1"],
               [AC_DEFINE([OPUS_X86_PRESUME_SSE], 1, [Define if binary requires SSE intrinsics support])],
               [rtcd_support="$rtcd_support SSE"])
         ],
         [
            AC_MSG_WARN([Compiler does not support SSE intrinsics])
         ])

         AS_IF([test x"$OPUS_X86_MAY_HAVE_SSE2" = x"1"],
         [
            AC_DEFINE([OPUS_X86_MAY_HAVE_SSE2], 1, [Compiler supports X86 SSE2 Intrinsics])
            intrinsics_support="$intrinsics_support SSE2"

            AS_IF([test x"$OPUS_X86_PRESUME_SSE2" = x"1"],
               [AC_DEFINE([OPUS_X86_PRESUME_SSE2], 1, [Define if binary requires SSE2 intrinsics support])],
               [rtcd_support="$rtcd_support SSE2"])
         ],
         [
            AC_MSG_WARN([Compiler does not support SSE2 intrinsics])
         ])

         AS_IF([test x"$OPUS_X86_MAY_HAVE_SSE4_1" = x"1"],
         [
            AC_DEFINE([OPUS_X86_MAY_HAVE_SSE4_1], 1, [Compiler supports X86 SSE4.1 Intrinsics])
            intrinsics_support="$intrinsics_support SSE4.1"

            AS_IF([test x"$OPUS_X86_PRESUME_SSE4_1" = x"1"],
               [AC_DEFINE([OPUS_X86_PRESUME_SSE4_1], 1, [Define if binary requires SSE4.1 intrinsics support])],
               [rtcd_support="$rtcd_support SSE4.1"])
         ],
         [
            AC_MSG_WARN([Compiler does not support SSE4.1 intrinsics])
         ])
         AS_IF([test x"$OPUS_X86_MAY_HAVE_AVX" = x"1"],
         [
            AC_DEFINE([OPUS_X86_MAY_HAVE_AVX], 1, [Compiler supports X86 AVX Intrinsics])
            intrinsics_support="$intrinsics_support AVX"

            AS_IF([test x"$OPUS_X86_PRESUME_AVX" = x"1"],
               [AC_DEFINE([OPUS_X86_PRESUME_AVX], 1, [Define if binary requires AVX intrinsics support])],
               [rtcd_support="$rtcd_support AVX"])
         ],
         [
            AC_MSG_WARN([Compiler does not support AVX intrinsics])
         ])

         AS_IF([test x"$intrinsics_support" = x""],
            [intrinsics_support=no],
            [intrinsics_support="x86$intrinsics_support"]
         )
         AS_IF([test x"$rtcd_support" = x""],
            [rtcd_support=no],
            [rtcd_support="x86$rtcd_support"],
        )

    AS_IF([test x"$enable_rtcd" = x"yes" && test x"$rtcd_support" != x""],[
            get_cpuid_by_asm="no"
            AC_MSG_CHECKING([How to get X86 CPU Info])
            AC_LINK_IFELSE([AC_LANG_PROGRAM([[
                 #include <stdio.h>
            ]],[[
                 unsigned int CPUInfo0;
                 unsigned int CPUInfo1;
                 unsigned int CPUInfo2;
                 unsigned int CPUInfo3;
                 unsigned int InfoType;
                 __asm__ __volatile__ (
                 "cpuid":
                 "=a" (CPUInfo0),
                 "=b" (CPUInfo1),
                 "=c" (CPUInfo2),
                 "=d" (CPUInfo3) :
                 "a" (InfoType), "c" (0)
                );
            ]])],
            [get_cpuid_by_asm="yes"
             AC_MSG_RESULT([Inline Assembly])
                 AC_DEFINE([CPU_INFO_BY_ASM], [1], [Get CPU Info by asm method])],
             [AC_LINK_IFELSE([AC_LANG_PROGRAM([[
                 #include <cpuid.h>
            ]],[[
                 unsigned int CPUInfo0;
                 unsigned int CPUInfo1;
                 unsigned int CPUInfo2;
                 unsigned int CPUInfo3;
                 unsigned int InfoType;
                 __get_cpuid(InfoType, &CPUInfo0, &CPUInfo1, &CPUInfo2, &CPUInfo3);
            ]])],
            [AC_MSG_RESULT([C method])
                 AC_DEFINE([CPU_INFO_BY_C], [1], [Get CPU Info by c method])],
            [AC_MSG_ERROR([no supported Get CPU Info method, please disable intrinsics])])])])
   ],
   [
      AC_MSG_WARN([No intrinsics support for your architecture])
      intrinsics_support="no"
   ])
],
[
   intrinsics_support="no"
])

AM_CONDITIONAL([CPU_ARM], [test "$cpu_arm" = "yes"])
AM_CONDITIONAL([HAVE_ARM_NEON_INTR],
    [test x"$OPUS_ARM_MAY_HAVE_NEON_INTR" = x"1"])
AM_CONDITIONAL([HAVE_ARM_NE10],
    [test x"$HAVE_ARM_NE10" = x"1"])
AM_CONDITIONAL([HAVE_SSE],
    [test x"$OPUS_X86_MAY_HAVE_SSE" = x"1"])
AM_CONDITIONAL([HAVE_SSE2],
    [test x"$OPUS_X86_MAY_HAVE_SSE2" = x"1"])
AM_CONDITIONAL([HAVE_SSE4_1],
    [test x"$OPUS_X86_MAY_HAVE_SSE4_1" = x"1"])
AM_CONDITIONAL([HAVE_AVX],
    [test x"$OPUS_X86_MAY_HAVE_AVX" = x"1"])

AS_IF([test x"$enable_rtcd" = x"yes"],[
    AS_IF([test x"$rtcd_support" != x"no"],[
        AC_DEFINE([OPUS_HAVE_RTCD], [1],
            [Use run-time CPU capabilities detection])
        OPUS_HAVE_RTCD=1
        AC_SUBST(OPUS_HAVE_RTCD)
    ])
],[
    rtcd_support="disabled"
])

AC_ARG_ENABLE([assertions],
    [AS_HELP_STRING([--enable-assertions],[enable additional software error checking])],,
    [enable_assertions=no])

AS_IF([test "$enable_assertions" = "yes"], [
  AC_DEFINE([ENABLE_ASSERTIONS], [1], [Assertions])
])

AC_ARG_ENABLE([fuzzing],
    [AS_HELP_STRING([--enable-fuzzing],[causes the encoder to make random decisions])],,
    [enable_fuzzing=no])

AS_IF([test "$enable_fuzzing" = "yes"], [
  AC_DEFINE([FUZZING], [1], [Fuzzing])
])

AC_ARG_ENABLE([ambisonics],
    [AS_HELP_STRING([--enable-ambisonics],[enable experimental ambisonic encoding and decoding support])],,
    [enable_ambisonics=no])

AS_IF([test "$enable_ambisonics" = "yes"], [
  AC_DEFINE([ENABLE_EXPERIMENTAL_AMBISONICS], [1], [Ambisonics Support])
])

AC_ARG_ENABLE([doc],
    [AS_HELP_STRING([--disable-doc], [Do not build API documentation])],,
    [enable_doc=yes])

AS_IF([test "$enable_doc" = "yes"], [
  AC_CHECK_PROG(HAVE_DOXYGEN, [doxygen], [yes], [no])
],[
  HAVE_DOXYGEN=no
])

AM_CONDITIONAL([HAVE_DOXYGEN], [test "$HAVE_DOXYGEN" = "yes"])

AC_ARG_ENABLE([extra-programs],
    [AS_HELP_STRING([--disable-extra-programs], [Do not build extra programs (demo and tests)])],,
    [enable_extra_programs=yes])

AM_CONDITIONAL([EXTRA_PROGRAMS], [test "$enable_extra_programs" = "yes"])


saved_CFLAGS="$CFLAGS"
CFLAGS="$CFLAGS -fvisibility=hidden"
AC_MSG_CHECKING([if ${CC} supports -fvisibility=hidden])
AC_COMPILE_IFELSE([AC_LANG_SOURCE([[char foo;]])],
    [ AC_MSG_RESULT([yes]) ],
    [ AC_MSG_RESULT([no])
      CFLAGS="$saved_CFLAGS"
    ])

CFLAGS="$CFLAGS -W"

warn_CFLAGS="-Wall -Wextra -Wcast-align -Wnested-externs -Wshadow -Wstrict-prototypes"
saved_CFLAGS="$CFLAGS"
CFLAGS="$CFLAGS $warn_CFLAGS"
AC_MSG_CHECKING([if ${CC} supports ${warn_CFLAGS}])
AC_COMPILE_IFELSE([AC_LANG_SOURCE([[char foo;]])],
    [ AC_MSG_RESULT([yes]) ],
    [ AC_MSG_RESULT([no])
      CFLAGS="$saved_CFLAGS"
    ])

saved_LIBS="$LIBS"
LIBS="$LIBS $LIBM"
AC_CHECK_FUNCS([lrintf])
AC_CHECK_FUNCS([lrint])
LIBS="$saved_LIBS"

AC_CHECK_FUNCS([__malloc_hook])

AC_SUBST([PC_BUILD])

AC_CONFIG_FILES([
    Makefile
    opus.pc
    opus-uninstalled.pc
    celt/arm/armopts.s
    doc/Makefile
    doc/Doxyfile
])
AC_CONFIG_HEADERS([config.h])

AC_OUTPUT

AC_MSG_NOTICE([
------------------------------------------------------------------------
  $PACKAGE_NAME $PACKAGE_VERSION:  Automatic configuration OK.

    Compiler support:

      C99 var arrays: ................ ${has_var_arrays}
      C99 lrintf: .................... ${ac_cv_func_lrintf}
      Use alloca: .................... ${use_alloca}

    General configuration:

      Floating point support: ........ ${enable_float}
      Fast float approximations: ..... ${enable_float_approx}
      Fixed point debugging: ......... ${enable_fixed_point_debug}
      Inline Assembly Optimizations: . ${inline_optimization}
      External Assembly Optimizations: ${asm_optimization}
      Intrinsics Optimizations.......: ${intrinsics_support}
      Run-time CPU detection: ........ ${rtcd_support}
      Custom modes: .................. ${enable_custom_modes}
      Assertion checking: ............ ${enable_assertions}
      Fuzzing: ....................... ${enable_fuzzing}
      Ambisonics support: .............${enable_ambisonics}

      API documentation: ............. ${enable_doc}
      Extra programs: ................ ${enable_extra_programs}
------------------------------------------------------------------------

 Type "make; make install" to compile and install
 Type "make check" to run the test suite
])

