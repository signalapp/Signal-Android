/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <sys/types.h>
#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include "webrtc/base/linuxfdwalk.h"

// Parses a file descriptor number in base 10, requiring the strict format used
// in /proc/*/fd. Returns the value, or -1 if not a valid string.
static int parse_fd(const char *s) {
  if (!*s) {
    // Empty string is invalid.
    return -1;
  }
  int val = 0;
  do {
    if (*s < '0' || *s > '9') {
      // Non-numeric characters anywhere are invalid.
      return -1;
    }
    int digit = *s++ - '0';
    val = val * 10 + digit;
  } while (*s);
  return val;
}

int fdwalk(void (*func)(void *, int), void *opaque) {
  DIR *dir = opendir("/proc/self/fd");
  if (!dir) {
    return -1;
  }
  int opendirfd = dirfd(dir);
  int parse_errors = 0;
  struct dirent *ent;
  // Have to clear errno to distinguish readdir() completion from failure.
  while (errno = 0, (ent = readdir(dir)) != NULL) {
    if (strcmp(ent->d_name, ".") == 0 ||
        strcmp(ent->d_name, "..") == 0) {
      continue;
    }
    // We avoid atoi or strtol because those are part of libc and they involve
    // locale stuff, which is probably not safe from a post-fork context in a
    // multi-threaded app.
    int fd = parse_fd(ent->d_name);
    if (fd < 0) {
      parse_errors = 1;
      continue;
    }
    if (fd != opendirfd) {
      (*func)(opaque, fd);
    }
  }
  int saved_errno = errno;
  if (closedir(dir) < 0) {
    if (!saved_errno) {
      // Return the closedir error.
      return -1;
    }
    // Else ignore it because we have a more relevant error to return.
  }
  if (saved_errno) {
    errno = saved_errno;
    return -1;
  } else if (parse_errors) {
    errno = EBADF;
    return -1;
  } else {
    return 0;
  }
}
