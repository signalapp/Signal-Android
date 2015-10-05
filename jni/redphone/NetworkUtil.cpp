#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>

#include <jni.h>
#include <android/log.h>

#include "NetworkUtil.h"

#define TAG "NetworkUtil"

int NetworkUtil::getAddressType(const char* serverIp) {
  struct addrinfo hint, *res = NULL;
  int result;

  memset(&hint, 0, sizeof(hint));

  hint.ai_family = PF_UNSPEC;
  hint.ai_flags  = AI_NUMERICHOST;

  if (getaddrinfo(serverIp, NULL, &hint, &res) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "getaddrinfo failed! %s", serverIp);
    result = -1;
  } else if (res->ai_family == AF_INET) {
    result = 1;
  } else if (res->ai_family == AF_INET6) {
    result = 0;
  } else {
    __android_log_print(ANDROID_LOG_WARN, TAG, "getaddrinfo returned unknown type for %s", serverIp);
    result = -1;
  }

  freeaddrinfo(res);

  return result;
}
