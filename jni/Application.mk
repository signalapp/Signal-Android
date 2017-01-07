APP_ABI := armeabi armeabi-v7a x86
APP_PLATFORM := android-9
#APP_STL := stlport_static
APP_CPPFLAGS += -fexceptions -std=c++11 -D__STDC_CONSTANT_MACROS -D__STDC_LIMIT_MACROS -D__STDC_FORMAT_MACROS
APP_OPTIM := debug

NDK_TOOLCHAIN_VERSION := 4.9
#APP_STL := stlport_shared  #--> does not seem to contain C++11 features
APP_STL := gnustl_static

# Enable c++11 extentions in source code
#APP_CPPFLAGS += -std=c++11
