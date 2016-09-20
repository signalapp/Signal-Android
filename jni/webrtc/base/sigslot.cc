// sigslot.h: Signal/Slot classes
//
// Written by Sarah Thompson (sarah@telergy.com) 2002.
//
// License: Public domain. You are free to use this code however you like, with
// the proviso that the author takes on no responsibility or liability for any
// use.

#include "webrtc/base/sigslot.h"

namespace sigslot {

#ifdef _SIGSLOT_HAS_POSIX_THREADS

multi_threaded_global::multi_threaded_global() {
  pthread_mutex_init(get_mutex(), NULL);
}

multi_threaded_global::multi_threaded_global(const multi_threaded_global&) {
}

multi_threaded_global::~multi_threaded_global() = default;

void multi_threaded_global::lock() {
  pthread_mutex_lock(get_mutex());
}

void multi_threaded_global::unlock() {
  pthread_mutex_unlock(get_mutex());
}

multi_threaded_local::multi_threaded_local() {
  pthread_mutex_init(&m_mutex, NULL);
}

multi_threaded_local::multi_threaded_local(const multi_threaded_local&) {
  pthread_mutex_init(&m_mutex, NULL);
}

multi_threaded_local::~multi_threaded_local() {
  pthread_mutex_destroy(&m_mutex);
}

void multi_threaded_local::lock() {
  pthread_mutex_lock(&m_mutex);
}

void multi_threaded_local::unlock() {
  pthread_mutex_unlock(&m_mutex);
}

#endif  // _SIGSLOT_HAS_POSIX_THREADS

};  // namespace sigslot
