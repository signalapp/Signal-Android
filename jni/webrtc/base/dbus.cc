/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifdef HAVE_DBUS_GLIB

#include "webrtc/base/dbus.h"

#include <glib.h>

#include "webrtc/base/logging.h"
#include "webrtc/base/thread.h"

namespace rtc {

// Avoid static object construction/destruction on startup/shutdown.
static pthread_once_t g_dbus_init_once = PTHREAD_ONCE_INIT;
static LibDBusGlibSymbolTable *g_dbus_symbol = NULL;

// Releases DBus-Glib symbols.
static void ReleaseDBusGlibSymbol() {
  if (g_dbus_symbol != NULL) {
    delete g_dbus_symbol;
    g_dbus_symbol = NULL;
  }
}

// Loads DBus-Glib symbols.
static void InitializeDBusGlibSymbol() {
  // This is thread safe.
  if (NULL == g_dbus_symbol) {
    g_dbus_symbol = new LibDBusGlibSymbolTable();

    // Loads dbus-glib
    if (NULL == g_dbus_symbol || !g_dbus_symbol->Load()) {
      LOG(LS_WARNING) << "Failed to load dbus-glib symbol table.";
      ReleaseDBusGlibSymbol();
    } else {
      // Nothing we can do if atexit() failed. Just ignore its returned value.
      atexit(ReleaseDBusGlibSymbol);
    }
  }
}

inline static LibDBusGlibSymbolTable *GetSymbols() {
  return DBusMonitor::GetDBusGlibSymbolTable();
}

// Implementation of class DBusSigMessageData
DBusSigMessageData::DBusSigMessageData(DBusMessage *message)
    : TypedMessageData<DBusMessage *>(message) {
  GetSymbols()->dbus_message_ref()(data());
}

DBusSigMessageData::~DBusSigMessageData() {
  GetSymbols()->dbus_message_unref()(data());
}

// Implementation of class DBusSigFilter

// Builds a DBus filter string from given DBus path, interface and member.
std::string DBusSigFilter::BuildFilterString(const std::string &path,
                                             const std::string &interface,
                                             const std::string &member) {
  std::string ret(DBUS_TYPE "='" DBUS_SIGNAL "'");
  if (!path.empty()) {
    ret += ("," DBUS_PATH "='");
    ret += path;
    ret += "'";
  }
  if (!interface.empty()) {
    ret += ("," DBUS_INTERFACE "='");
    ret += interface;
    ret += "'";
  }
  if (!member.empty()) {
    ret += ("," DBUS_MEMBER "='");
    ret += member;
    ret += "'";
  }
  return ret;
}

// Forwards the message to the given instance.
DBusHandlerResult DBusSigFilter::DBusCallback(DBusConnection *dbus_conn,
                                              DBusMessage *message,
                                              void *instance) {
  ASSERT(instance);
  if (instance) {
    return static_cast<DBusSigFilter *>(instance)->Callback(message);
  }
  return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

// Posts a message to caller thread.
DBusHandlerResult DBusSigFilter::Callback(DBusMessage *message) {
  if (caller_thread_) {
    caller_thread_->Post(RTC_FROM_HERE, this, DSM_SIGNAL,
                         new DBusSigMessageData(message));
  }
  // Don't "eat" the message here. Let it pop up.
  return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

// From MessageHandler.
void DBusSigFilter::OnMessage(Message *message) {
  if (message != NULL && DSM_SIGNAL == message->message_id) {
    DBusSigMessageData *msg =
        static_cast<DBusSigMessageData *>(message->pdata);
    if (msg) {
      ProcessSignal(msg->data());
      delete msg;
    }
  }
}

// Definition of private class DBusMonitoringThread.
// It creates a worker-thread to listen signals on DBus. The worker-thread will
// be running in a priate GMainLoop forever until either Stop() has been invoked
// or it hits an error.
class DBusMonitor::DBusMonitoringThread : public rtc::Thread {
 public:
  explicit DBusMonitoringThread(DBusMonitor *monitor,
                                GMainContext *context,
                                GMainLoop *mainloop,
                                std::vector<DBusSigFilter *> *filter_list)
      : monitor_(monitor),
        context_(context),
        mainloop_(mainloop),
        connection_(NULL),
        idle_source_(NULL),
        filter_list_(filter_list) {
    ASSERT(monitor_);
    ASSERT(context_);
    ASSERT(mainloop_);
    ASSERT(filter_list_);
  }

  virtual ~DBusMonitoringThread() {
    Stop();
  }

  // Override virtual method of Thread. Context: worker-thread.
  virtual void Run() {
    ASSERT(NULL == connection_);

    // Setup DBus connection and start monitoring.
    monitor_->OnMonitoringStatusChanged(DMS_INITIALIZING);
    if (!Setup()) {
      LOG(LS_ERROR) << "DBus monitoring setup failed.";
      monitor_->OnMonitoringStatusChanged(DMS_FAILED);
      CleanUp();
      return;
    }
    monitor_->OnMonitoringStatusChanged(DMS_RUNNING);
    g_main_loop_run(mainloop_);
    monitor_->OnMonitoringStatusChanged(DMS_STOPPED);

    // Done normally. Clean up DBus connection.
    CleanUp();
    return;
  }

  // Override virtual method of Thread. Context: caller-thread.
  virtual void Stop() {
    ASSERT(NULL == idle_source_);
    // Add an idle source and let the gmainloop quit on idle.
    idle_source_ = g_idle_source_new();
    if (idle_source_) {
      g_source_set_callback(idle_source_, &Idle, this, NULL);
      g_source_attach(idle_source_, context_);
    } else {
      LOG(LS_ERROR) << "g_idle_source_new() failed.";
      QuitGMainloop();  // Try to quit anyway.
    }

    Thread::Stop();  // Wait for the thread.
  }

 private:
  // Registers all DBus filters.
  void RegisterAllFilters() {
    ASSERT(NULL != GetSymbols()->dbus_g_connection_get_connection()(
        connection_));

    for (std::vector<DBusSigFilter *>::iterator it = filter_list_->begin();
         it != filter_list_->end(); ++it) {
      DBusSigFilter *filter = (*it);
      if (!filter) {
        LOG(LS_ERROR) << "DBusSigFilter list corrupted.";
        continue;
      }

      GetSymbols()->dbus_bus_add_match()(
          GetSymbols()->dbus_g_connection_get_connection()(connection_),
          filter->filter().c_str(), NULL);

      if (!GetSymbols()->dbus_connection_add_filter()(
              GetSymbols()->dbus_g_connection_get_connection()(connection_),
              &DBusSigFilter::DBusCallback, filter, NULL)) {
        LOG(LS_ERROR) << "dbus_connection_add_filter() failed."
                      << "Filter: " << filter->filter();
        continue;
      }
    }
  }

  // Unregisters all DBus filters.
  void UnRegisterAllFilters() {
    ASSERT(NULL != GetSymbols()->dbus_g_connection_get_connection()(
        connection_));

    for (std::vector<DBusSigFilter *>::iterator it = filter_list_->begin();
         it != filter_list_->end(); ++it) {
      DBusSigFilter *filter = (*it);
      if (!filter) {
        LOG(LS_ERROR) << "DBusSigFilter list corrupted.";
        continue;
      }
      GetSymbols()->dbus_connection_remove_filter()(
          GetSymbols()->dbus_g_connection_get_connection()(connection_),
          &DBusSigFilter::DBusCallback, filter);
    }
  }

  // Sets up the monitoring thread.
  bool Setup() {
    g_main_context_push_thread_default(context_);

    // Start connection to dbus.
    // If dbus daemon is not running, returns false immediately.
    connection_ = GetSymbols()->dbus_g_bus_get_private()(monitor_->type_,
        context_, NULL);
    if (NULL == connection_) {
      LOG(LS_ERROR) << "dbus_g_bus_get_private() unable to get connection.";
      return false;
    }
    if (NULL == GetSymbols()->dbus_g_connection_get_connection()(connection_)) {
      LOG(LS_ERROR) << "dbus_g_connection_get_connection() returns NULL. "
                    << "DBus daemon is probably not running.";
      return false;
    }

    // Application don't exit if DBus daemon die.
    GetSymbols()->dbus_connection_set_exit_on_disconnect()(
        GetSymbols()->dbus_g_connection_get_connection()(connection_), FALSE);

    // Connect all filters.
    RegisterAllFilters();

    return true;
  }

  // Cleans up the monitoring thread.
  void CleanUp() {
    if (idle_source_) {
      // We did an attach() with the GSource, so we need to destroy() it.
      g_source_destroy(idle_source_);
      // We need to unref() the GSource to end the last reference we got.
      g_source_unref(idle_source_);
      idle_source_ = NULL;
    }
    if (connection_) {
      if (GetSymbols()->dbus_g_connection_get_connection()(connection_)) {
        UnRegisterAllFilters();
        GetSymbols()->dbus_connection_close()(
            GetSymbols()->dbus_g_connection_get_connection()(connection_));
      }
      GetSymbols()->dbus_g_connection_unref()(connection_);
      connection_ = NULL;
    }
    g_main_loop_unref(mainloop_);
    mainloop_ = NULL;
    g_main_context_unref(context_);
    context_ = NULL;
  }

  // Handles callback on Idle. We only add this source when ready to stop.
  static gboolean Idle(gpointer data) {
    static_cast<DBusMonitoringThread *>(data)->QuitGMainloop();
    return TRUE;
  }

  // We only hit this when ready to quit.
  void QuitGMainloop() {
    g_main_loop_quit(mainloop_);
  }

  DBusMonitor *monitor_;

  GMainContext *context_;
  GMainLoop *mainloop_;
  DBusGConnection *connection_;
  GSource *idle_source_;

  std::vector<DBusSigFilter *> *filter_list_;
};

// Implementation of class DBusMonitor

// Returns DBus-Glib symbol handle. Initialize it first if hasn't.
LibDBusGlibSymbolTable *DBusMonitor::GetDBusGlibSymbolTable() {
  // This is multi-thread safe.
  pthread_once(&g_dbus_init_once, InitializeDBusGlibSymbol);

  return g_dbus_symbol;
};

// Creates an instance of DBusMonitor
DBusMonitor *DBusMonitor::Create(DBusBusType type) {
  if (NULL == DBusMonitor::GetDBusGlibSymbolTable()) {
    return NULL;
  }
  return new DBusMonitor(type);
}

DBusMonitor::DBusMonitor(DBusBusType type)
    : type_(type),
      status_(DMS_NOT_INITIALIZED),
      monitoring_thread_(NULL) {
  ASSERT(type_ == DBUS_BUS_SYSTEM || type_ == DBUS_BUS_SESSION);
}

DBusMonitor::~DBusMonitor() {
  StopMonitoring();
}

bool DBusMonitor::AddFilter(DBusSigFilter *filter) {
  if (monitoring_thread_) {
    return false;
  }
  if (!filter) {
    return false;
  }
  filter_list_.push_back(filter);
  return true;
}

bool DBusMonitor::StartMonitoring() {
  if (!monitoring_thread_) {
    g_type_init();
    // g_thread_init API is deprecated since glib 2.31.0, see release note:
    // http://mail.gnome.org/archives/gnome-announce-list/2011-October/msg00041.html
#if !GLIB_CHECK_VERSION(2, 31, 0)
    g_thread_init(NULL);
#endif
    GetSymbols()->dbus_g_thread_init()();

    GMainContext *context = g_main_context_new();
    if (NULL == context) {
      LOG(LS_ERROR) << "g_main_context_new() failed.";
      return false;
    }

    GMainLoop *mainloop = g_main_loop_new(context, FALSE);
    if (NULL == mainloop) {
      LOG(LS_ERROR) << "g_main_loop_new() failed.";
      g_main_context_unref(context);
      return false;
    }

    monitoring_thread_ = new DBusMonitoringThread(this, context, mainloop,
                                                  &filter_list_);
    if (monitoring_thread_ == NULL) {
      LOG(LS_ERROR) << "Failed to create DBus monitoring thread.";
      g_main_context_unref(context);
      g_main_loop_unref(mainloop);
      return false;
    }
    monitoring_thread_->Start();
  }
  return true;
}

bool DBusMonitor::StopMonitoring() {
  if (monitoring_thread_) {
    monitoring_thread_->Stop();
    monitoring_thread_ = NULL;
  }
  return true;
}

DBusMonitor::DBusMonitorStatus DBusMonitor::GetStatus() {
  return status_;
}

void DBusMonitor::OnMonitoringStatusChanged(DBusMonitorStatus status) {
  status_ = status;
}

#undef LATE

}  // namespace rtc

#endif  // HAVE_DBUS_GLIB
