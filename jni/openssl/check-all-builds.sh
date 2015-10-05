#!/bin/sh
#

set -e
export LANG=C
export LC_ALL=C

PROGDIR=$(dirname "$0")
PROGNAME=$(basename "$0")

panic () {
  echo "ERROR: $@"
  exit 1
}

VERBOSE=1

# Dump message is $VERBOSE >= $1
# $1+: message.
dump_n () {
  local LOG_LEVEL=$1
  shift
  if [ "$VERBOSE" -ge "$LOG_LEVEL" ]; then
    printf "%s\n" "$@"
  fi
}

# Dump a message unless --quiet is used.
# $1+: message.
dump () {
  dump_n 1 "$@"
}

# Dump a message if --verbose is used only.
# $1+: message.
log () {
  dump_n 2 "$@"
}

# Run a command silently, unless --verbose or '--verbose --verbose'
# is used.
# $1+: Command
# Return: command status.
run () {
  log "COMMAND: $*"
  case $VERBOSE in
    0)
      "$@" >/dev/null 2>&1 || return $?
      ;;
    1)
      "$@" >/dev/null || return $?
      ;;
    *)
      "$@" || return $?
      ;;
  esac
}

# $1: string
# Out: input string, with capital letters replaced by small ones.
tolower () {
  echo "$1" | tr '[A-Z]' '[a-z]'
}

# Return value of a given variable.
# $1: Variable name
var_value () {
  eval printf \"%s\" \"\$$1\"
}

# Remove some items from a list
# $1: input space-separated list
# $2: space-separated list of items to remove from 1
# Out: items of $1 without items of $2
filter_out () {
  local TMP=$(mktemp)
  local RESULT
  printf "" > $TMP
  echo "$2" | tr ' ' '\n' > $TMP
  RESULT=$(echo "$1" | tr ' ' '\n' | fgrep -x -v -f $TMP | tr '\n' ' ')
  rm -f $TMP
  echo "$RESULT"
}

src_to_obj () {
  case $1 in
    *.c)
      echo ${1%%.c}.o
      ;;
    *.S)
      echo ${1%%.S}.o
      ;;
    *)
      echo $1
      ;;
  esac
}

# Determine host operating system.
HOST_OS=$(uname -s)
case $HOST_OS in
  Linux)
    HOST_OS=linux
    ;;
  Darwin)
    HOST_OS=darwin
    ;;
esac

# Determine host architecture
HOST_ARCH=$(uname -m)
case $HOST_ARCH in
  i?86)
    HOST_ARCH=x86
    ;;
esac

ANDROID_HOST_TAG=$HOST_OS-$HOST_ARCH

case $ANDROID_HOST_TAG in
  linux-x86_64|darwin-x86-64)
    ANDROID_HOST_TAG=$HOST_OS-x86
    ;;
  *)
    panic "Sorry, this script can only run on 64-bit Linux or Darwin"
esac

# Determine number of cores
case $HOST_OS in
  linux)
    NUM_CORES=$(grep -c "processor" /proc/cpuinfo)
    ;;
  darwin)
    NUM_CORES=$(sysctl -n hw.ncpu)
    ;;
  *)
    NUM_CORES=1
    ;;
esac

# The list of supported Android target architectures.

# NOTE: x86_64 is not ready yet, while the toolchain is in
# prebuilts/ it doesn't have a sysroot which means it requires
# a platform build to get Bionic and stuff.
ANDROID_ARCHS="arm arm64 x86 x86_64 mips"

BUILD_TYPES=
for ARCH in $ANDROID_ARCHS; do
  BUILD_TYPES="$BUILD_TYPES android-$ARCH"
done
ANDROID_BUILD_TYPES=$BUILD_TYPES

HOST_BUILD_TYPES="$HOST_OS-x86 $HOST_OS-generic32 $HOST_OS-generic64"
HOST_BUILD_TYPES="$HOST_BUILD_TYPES $HOST_OS-x86_64"

BUILD_TYPES="$ANDROID_BUILD_TYPES $HOST_BUILD_TYPES"

# Parse command-line
DO_HELP=
SRC_DIR=$(cd $PROGDIR && pwd)
OUT_DIR=out
BUILD_DIR=
BUILD_TYPES=
NUM_JOBS=$NUM_CORES
ANDROID_BUILD_TOP=$(cd $PROGDIR/../.. && pwd)
for OPT; do
  case $OPT in
    --help|-h|-?)
      DO_HELP=true
      ;;
    --build-dir=*)
      BUILD_DIR=${OPT##--build-dir=}
      ;;
    --verbose)
      VERBOSE=$(( $VERBOSE + 1 ))
      ;;
    --jobs=*)
      NUM_JOBS=${OPT##--jobs=}
      ;;
    --quiet)
      VERBOSE=$(( $VERBOSE - 1 ))
      ;;
    -j*)
      NUM_JOBS=${OPT##-j}
      ;;
    -*)
      panic "Unknown option '$OPT', see --help for details."
      ;;
    *)
      BUILD_TYPES="$BUILD_TYPES $OPT"
      ;;
  esac
done

# Print help when needed.
if [ "$DO_HELP" ]; then
  echo \
"Usage: $PROGNAME [options] [<build-type> ...]

This script is used to ensure that all OpenSSL build variants compile
properly. It can be used after modifying external/openssl/openssl.config
and re-running import_openssl.sh to check that any changes didn't break
the build.

A <build-type> is a description of a given build of the library and its
program. Its format is:

  <compiler>-<system>-<arch>

Where: <compiler> is either 'gcc' or 'clang'.
       <system>   is 'android', 'linux' or 'darwin'.
       <arch>     is 'arm', 'x86'  or 'mips'.

By default, it rebuilds the sources for the following build types:
"
  for BUILD_TYPE in $BUILD_TYPES; do
    echo "  $BUILD_TYPE"
  done

  echo \
"However, you can pass custom values on the command-line instead.

This scripts generates a custom Makefile in a temporary directory, then
launches 'make' in it to build all binaries in parallel. In case of
problem, you can use the --build-dir=<path> option to specify a custom
build-directory, which will _not_ be removed when the script exits.

For example, to better see why a build fails:

   ./$PROGNAME --build-dir=/tmp/mydir
   make -C /tmp/mydir V=1

Valid options:

  --help|-h|-?        Print this message.
  --build-dir=<path>  Specify build directory.
  --jobs=<count>      Run <count> parallel build jobs [$NUM_JOBS].
  -j<count>           Same as --jobs=<count>.
  --verbose           Increase verbosity.
  --quiet             Decrease verbosity.
"
  exit 0
fi

log "Host OS: $HOST_OS"
log "Host arch: $HOST_ARCH"
log "Host CPU count: $NUM_CORES"

if [ -z "$BUILD_TYPES" ]; then
  BUILD_TYPES="$ANDROID_BUILD_TYPES $HOST_BUILD_TYPES"
fi
log "Build types: $BUILD_TYPES"

if [ -z "$BUILD_DIR" ]; then
  # Create a temporary directory, ensure it gets destroyed properly
  # when the script exits.
  BUILD_DIR=$(mktemp -d)
  clean_build_dir () {
    log "Cleaning up temporary directory: $BUILD_DIR"
    rm -rf "$BUILD_DIR"
    exit $1
  }
  trap "clean_build_dir 0" EXIT
  trap "clean_build_dir \$?" INT HUP QUIT TERM
  log "Using temporary build directory: $BUILD_DIR"
else
  log "Using user build directory: $BUILD_DIR"
fi

mkdir -p "$BUILD_DIR" && rm -rf "$BUILD_DIR"/*

MAKEFILE=$BUILD_DIR/GNUmakefile

# Return source files for a given module and architecture.
# $1: module prefix (e.g. CRYPTO)
# $2: build arch.
get_module_src_files_for_arch () {
  local prefix=$1
  local arch=$2
  local src_files="$(var_value OPENSSL_${prefix}_SOURCES)"
  src_files="$src_files $(var_value OPENSSL_${prefix}_SOURCES_${arch})"
  local exclude_files="$(var_value OPENSSL_${prefix}_SOURCES_EXCLUDES_${arch})"
  src_files=$(filter_out "$src_files" "$exclude_files")
  echo "$src_files"
}

# Return the compiler defines for a given module and architecture
# $1: module prefix (e.g. CRYPTO)
# $2 build arch.
get_module_defines_for_arch () {
  local prefix=$1
  local arch=$2
  local defines="$(var_value OPENSSL_${prefix}_DEFINES)"
  defines="$defines $(var_value OPENSSL_${prefix}_DEFINES_${arch})"
  echo "$defines"
}

# $1: module prefix (e.g. CRYPTO)
get_module_c_includes () {
  var_value OPENSSL_$1_INCLUDES
}

# $1: build type (e.g. gcc-android-arm)
# Out: build arch.
get_build_arch () {
  echo "$1" | cut -d- -f3
}

# $1: build arch
# Out: GNU configuration target (e.g. arm-linux-androideabi)
get_build_arch_target () {
  case $1 in
    arm64)
      echo "aarch64-linux-android"
      ;;
    arm)
      echo "arm-linux-androideabi"
      ;;
    x86)
      echo "x86_64-linux-android"
      ;;
    x86_64)
      echo "x86_64-linux-android"
      ;;
    mips)
      echo "mipsel-linux-android"
      ;;
    *)
      echo "$1-linux-android"
      ;;
  esac
}

GCC_VERSION=4.8
CLANG_VERSION=3.2

get_prebuilt_gcc_dir_for_arch () {
  local arch=$1
  local target=$(get_build_arch_target $arch)
  # Adjust $arch for x86_64 because the prebuilts are actually
  # under prebuilts/gcc/<host>/x86/
  case $arch in
    x86_64)
        arch=x86
        ;;
    arm64)
        arch=aarch64
        ;;
  esac
  echo "$ANDROID_BUILD_TOP/prebuilts/gcc/$ANDROID_HOST_TAG/$arch/$target-$GCC_VERSION"
}

get_prebuilt_clang () {
  echo "$ANDROID_BUILD_TOP/prebuilts/clang/$ANDROID_HOST_TAG/$CLANG_VERSION/clang"
}

get_prebuilt_ndk_sysroot_for_arch () {
  echo "$ANDROID_BUILD_TOP/prebuilts/ndk/current/platforms/android-9/arch-$1"
}

get_c_runtime_file () {
  local build_type=$1
  local arch=$(get_build_arch $build_type)
  local filename=$2
  echo "$(get_prebuilt_ndk_sysroot_for_arch $arch)/usr/lib/$filename"
}

# $1: build type (e.g. gcc-android-arm)
get_build_compiler () {
  local arch=$(get_build_arch $1)
  local target=$(get_build_arch_target $arch)
  local gcc_dir=$(get_prebuilt_gcc_dir_for_arch $arch);
  local result

  # Get the toolchain binary.
  case $1 in
    gcc-android-*)
      result="$gcc_dir/bin/$target-gcc"
      ;;
    clang-android-*)
      result="$(get_prebuilt_clang) -target $target -B$gcc_dir/$target/bin -I$gcc_dir/lib/gcc/$target/$GCC_VERSION/include"
      ;;
    gcc-*)
      result=gcc
      ;;
    clang-*) # Must have host clang compiler.
      result=clang
      ;;
  esac

  compiler_check=$(which $result 2>/dev/null || echo "")
  if [ -z "$compiler_check" ]; then
    panic "Could not find compiler: $result"
  fi

  # Get the Android sysroot if needed.
  case $1 in
    *-android-*)
      result="$result --sysroot=$(get_prebuilt_ndk_sysroot_for_arch $arch)"
      ;;
  esac

  # Force -m32 flag when needed for 32-bit builds.
  case $1 in
    *-x86|*-generic32)
      result="$result -m32"
      ;;
  esac
  echo "$result"
}

# $1: build type.
# Out: common compiler flags for this build.
get_build_c_flags () {
  local result="-O2 -fPIC"
  case $1 in
    *-android-arm)
      result="$result -march=armv7-a -mfpu=vfpv3-d16"
      ;;
  esac

  case $1 in
    *-generic32|*-generic64)
      # Generic builds do not compile without this flag.
      result="$result -DOPENSSL_NO_ASM"
      ;;
  esac
  echo "$result"
}

# $1: build type.
# Out: linker for this build.
get_build_linker () {
  get_build_compiler $1
}

clear_sources () {
  g_all_objs=""
}

# Generate build instructions to compile source files.
# Also update g_all_objs.
# $1: module prefix (e.g. CRYPTO)
# $2: build type
build_sources () {
  local prefix=$1
  local build_type=$2
  echo "## build_sources prefix='$prefix' build_type='$build_type'"
  local arch=$(get_build_arch $build_type)
  local src_files=$(get_module_src_files_for_arch $prefix $arch)
  local c_defines=$(get_module_defines_for_arch $prefix $arch)
  local c_includes=$(get_module_c_includes $prefix "$SRC_DIR")
  local build_cc=$(get_build_compiler $build_type)
  local build_cflags=$(get_build_c_flags $build_type)
  local build_linker=$(get_build_linker $build_type)
  local src obj def inc

  printf "OUT_DIR := $OUT_DIR/$build_type\n\n"
  printf "BUILD_CC := $build_cc\n\n"
  printf "BUILD_LINKER := $build_linker\n\n"
  printf "BUILD_CFLAGS := $build_cflags"
  for inc in $c_includes; do
    printf " -I\$(SRC_DIR)/$inc"
  done
  for def in $c_defines; do
    printf " -D$def"
  done
  printf "\n\n"
  printf "BUILD_OBJECTS :=\n\n"

  case $build_type in
    clang-android-*)
      # The version of clang that comes with the platform build doesn't
      # support simple linking of shared libraries and executables. One
      # has to provide the C runtime files explicitely.
      local crtbegin_so=$(get_c_runtime_file $build_type crtbegin_so.o)
      local crtend_so=$(get_c_runtime_file $build_type crtend_so.o)
      local crtbegin_exe=$(get_c_runtime_file $build_type crtbegin_dynamic.o)
      local crtend_exe=$(get_c_runtime_file $build_type crtend_android.o)
      printf "CRTBEGIN_SO := $crtbegin_so\n"
      printf "CRTEND_SO := $crtend_so\n"
      printf "CRTBEGIN_EXE := $crtbegin_exe\n"
      printf "CRTEND_EXE := $crtend_exe\n"
      printf "\n"
      ;;
  esac

  for src in $src_files; do
    obj=$(src_to_obj $src)
    g_all_objs="$g_all_objs $obj"
    printf "OBJ := \$(OUT_DIR)/$obj\n"
    printf "BUILD_OBJECTS += \$(OBJ)\n"
    printf "\$(OBJ): PRIVATE_CC := \$(BUILD_CC)\n"
    printf "\$(OBJ): PRIVATE_CFLAGS := \$(BUILD_CFLAGS)\n"
    printf "\$(OBJ): \$(SRC_DIR)/$src\n"
    printf "\t@echo [$build_type] CC $src\n"
    printf "\t@mkdir -p \$\$(dirname \$@)\n"
    printf "\t\$(hide) \$(PRIVATE_CC) \$(PRIVATE_CFLAGS) -c -o \$@ \$<\n"
    printf "\n"
  done
  printf "\n"
}

# $1: library name (e.g. crypto).
# $2: module prefix (e.g. CRYPTO).
# $3: build type.
# $4: source directory.
# $5: output directory.
build_shared_library () {
  local name=$1
  local prefix=$2
  local build_type=$3
  local src_dir="$4"
  local out_dir="$5"
  local shlib="lib${name}.so"
  local build_linker=$(get_build_linker $build_type)
  clear_sources
  build_sources $prefix $build_type

  # TODO(digit): Make the clang build link properly.
  printf "SHLIB=\$(OUT_DIR)/$shlib\n"
  printf "\$(SHLIB): PRIVATE_LINKER := \$(BUILD_LINKER)\n"
  case $build_type in
    clang-android-*)
      printf "\$(SHLIB): PRIVATE_CRTBEGIN := \$(CRTBEGIN_SO)\n"
      printf "\$(SHLIB): PRIVATE_CRTEND := \$(CRTEND_SO)\n"
      ;;
  esac
  printf "\$(SHLIB): \$(BUILD_OBJECTS)\n"
  printf "\t@echo [$build_type] SHARED_LIBRARY $(basename $shlib)\n"
  printf "\t@mkdir -p \$\$(dirname \$@)\n"
  case $build_type in
    clang-android-*)
      printf "\t\$(hide) \$(PRIVATE_LINKER) -nostdlib -shared -o \$@ \$(PRIVATE_CRTBEGIN) \$^ \$(PRIVATE_CRTEND)\n"
      ;;
    *)
      printf "\t\$(hide) \$(PRIVATE_LINKER) -shared -o \$@ \$^\n"
      ;;
  esac
  printf "\n"
}

# $1: executable name.
# $2: module prefix (e.g. APPS).
# $3: build type.
# $4: source directory.
# $5: output directory.
# $6: dependent shared libraries (e.g. 'crypto ssl')
build_executable () {
  local name=$1
  local prefix=$2
  local build_type=$3
  local src_dir="$4"
  local out_dir="$5"
  local shlibs="$6"
  local build_linker=$(get_build_linker $build_type)
  clear_sources
  build_sources $prefix $build_type

  # TODO(digit): Make the clang build link properly.
  exec=$name
  all_shlibs=
  printf "EXEC := \$(OUT_DIR)/$name\n"
  printf "openssl_all: \$(EXEC)\n"
  printf "\$(EXEC): PRIVATE_LINKER := \$(BUILD_LINKER)\n"
  printf "\$(EXEC): \$(BUILD_OBJECTS)"
  for lib in $shlibs; do
    printf " \$(OUT_DIR)/lib${lib}.so"
  done
  printf "\n"
  printf "\t@echo [$build_type] EXECUTABLE $name\n"
  printf "\t@mkdir -p \$\$(dirname \$@)\n"
  printf "\t\$(hide) \$(PRIVATE_LINKER) -o \$@ \$^\n"
  printf "\n"
}

ALL_BUILDS=

generate_openssl_build () {
  local build_type=$1
  local out="$OUT_DIR/$build_type"
  ALL_BUILDS="$ALL_BUILDS $build_type"
  echo "# Build type: $build_type"
  build_shared_library crypto CRYPTO $build_type "$SRC_DIR" "$out"
  build_shared_library ssl SSL $build_type "$SRC_DIR" "$out"
  build_executable openssl APPS $build_type "$SRC_DIR" "$out" "crypto ssl"
}

generate_makefile () {
  echo \
"# Auto-generated by $PROGDIR - do not edit

.PHONY: openssl_all

all: openssl_all

# Use 'make V=1' to print build commands.
ifeq (1,\$(V))
hide :=
else
hide := @
endif

SRC_DIR=$SRC_DIR
OUT_DIR=$OUT_DIR
"

  for BUILD_TYPE in $BUILD_TYPES; do
    generate_openssl_build gcc-$BUILD_TYPE
  done

# TODO(digit): Make the Clang build run.
#   for BUILD_TYPE in $ANDROID_BUILD_TYPES; do
#     generate_openssl_build clang-$BUILD_TYPE
#   done
}

. $SRC_DIR/openssl.config



dump "Generating Makefile"
log "Makefile path: $MAKEFILE"
generate_makefile > $MAKEFILE

dump "Building libraries with $NUM_JOBS jobs"
dump "For the following builds:"
for BUILD in $ALL_BUILDS; do
  dump "  $BUILD"
done
MAKE_FLAGS="-j$NUM_JOBS"
if [ "$VERBOSE" -gt 2 ]; then
  MAKE_FLAGS="$MAKE_FLAGS V=1"
fi
run make $MAKE_FLAGS -f "$MAKEFILE" -C "$BUILD_DIR"
case $? in
  0)
    dump "All OK, congratulations!"
    ;;
  *)
    dump "Error, try doing the following to inspect the issues:"
    dump "   $PROGNAME --build-dir=/tmp/mybuild"
    dump "   make -C /tmp/mybuild V=1"
    dump " "
    ;;
esac
