/*
Source:
http://www1.cse.wustl.edu/~schmidt/ACE-copying.html

License:
Copyright and Licensing Information for ACE(TM), TAO(TM), CIAO(TM), DAnCE(TM),
and CoSMIC(TM)

ACE(TM), TAO(TM), CIAO(TM), DAnCE>(TM), and CoSMIC(TM) (henceforth referred to
as "DOC software") are copyrighted by Douglas C. Schmidt and his research
group at Washington University, University of California, Irvine, and
Vanderbilt University, Copyright (c) 1993-2009, all rights reserved. Since DOC
software is open-source, freely available software, you are free to use,
modify, copy, and distribute--perpetually and irrevocably--the DOC software
source code and object code produced from the source, as well as copy and
distribute modified versions of this software. You must, however, include this
copyright statement along with any code built using DOC software that you
release. No copyright statement needs to be provided if you just ship binary
executables of your software products.
You can use DOC software in commercial and/or binary software releases and are
under no obligation to redistribute any of your source code that is built
using DOC software. Note, however, that you may not misappropriate the DOC
software code, such as copyrighting it yourself or claiming authorship of the
DOC software code, in a way that will prevent DOC software from being
distributed freely using an open-source development model. You needn't inform
anyone that you're using DOC software in your software, though we encourage
you to let us know so we can promote your project in the DOC software success
stories.

The ACE, TAO, CIAO, DAnCE, and CoSMIC web sites are maintained by the DOC
Group at the Institute for Software Integrated Systems (ISIS) and the Center
for Distributed Object Computing of Washington University, St. Louis for the
development of open-source software as part of the open-source software
community. Submissions are provided by the submitter ``as is'' with no
warranties whatsoever, including any warranty of merchantability,
noninfringement of third party intellectual property, or fitness for any
particular purpose. In no event shall the submitter be liable for any direct,
indirect, special, exemplary, punitive, or consequential damages, including
without limitation, lost profits, even if advised of the possibility of such
damages. Likewise, DOC software is provided as is with no warranties of any
kind, including the warranties of design, merchantability, and fitness for a
particular purpose, noninfringement, or arising from a course of dealing,
usage or trade practice. Washington University, UC Irvine, Vanderbilt
University, their employees, and students shall have no liability with respect
to the infringement of copyrights, trade secrets or any patents by DOC
software or any part thereof. Moreover, in no event will Washington
University, UC Irvine, or Vanderbilt University, their employees, or students
be liable for any lost revenue or profits or other special, indirect and
consequential damages.

DOC software is provided with no support and without any obligation on the
part of Washington University, UC Irvine, Vanderbilt University, their
employees, or students to assist in its use, correction, modification, or
enhancement. A number of companies around the world provide commercial support
for DOC software, however. DOC software is Y2K-compliant, as long as the
underlying OS platform is Y2K-compliant. Likewise, DOC software is compliant
with the new US daylight savings rule passed by Congress as "The Energy Policy
Act of 2005," which established new daylight savings times (DST) rules for the
United States that expand DST as of March 2007. Since DOC software obtains
time/date and calendaring information from operating systems users will not be
affected by the new DST rules as long as they upgrade their operating systems
accordingly.

The names ACE(TM), TAO(TM), CIAO(TM), DAnCE(TM), CoSMIC(TM), Washington
University, UC Irvine, and Vanderbilt University, may not be used to endorse
or promote products or services derived from this source without express
written permission from Washington University, UC Irvine, or Vanderbilt
University. This license grants no permission to call products or services
derived from this source ACE(TM), TAO(TM), CIAO(TM), DAnCE(TM), or CoSMIC(TM),
nor does it grant permission for the name Washington University, UC Irvine, or
Vanderbilt University to appear in their names.
*/

/*
 *  This source code contain modifications to the original source code
 *  which can be found here:
 *  http://www.cs.wustl.edu/~schmidt/win32-cv-1.html (section 3.2).
 *  Modifications:
 *  1) Dynamic detection of native support for condition variables.
 *  2) Use of WebRTC defined types and classes. Renaming of some functions.
 *  3) Introduction of a second event for wake all functionality. This prevents
 *     a thread from spinning on the same condition variable, preventing other
 *     threads from waking up.
 */

#include "webrtc/system_wrappers/source/condition_variable_event_win.h"
#include "webrtc/system_wrappers/source/critical_section_win.h"

namespace webrtc {

ConditionVariableEventWin::ConditionVariableEventWin() : eventID_(WAKEALL_0) {
  memset(&num_waiters_[0], 0, sizeof(num_waiters_));

  InitializeCriticalSection(&num_waiters_crit_sect_);

  events_[WAKEALL_0] = CreateEvent(NULL,  // no security attributes
                                   TRUE,  // manual-reset, sticky event
                                   FALSE,  // initial state non-signaled
                                   NULL);  // no name for event

  events_[WAKEALL_1] = CreateEvent(NULL,  // no security attributes
                                   TRUE,  // manual-reset, sticky event
                                   FALSE,  // initial state non-signaled
                                   NULL);  // no name for event

  events_[WAKE] = CreateEvent(NULL,  // no security attributes
                              FALSE,  // auto-reset, sticky event
                              FALSE,  // initial state non-signaled
                              NULL);  // no name for event
}

ConditionVariableEventWin::~ConditionVariableEventWin() {
  CloseHandle(events_[WAKE]);
  CloseHandle(events_[WAKEALL_1]);
  CloseHandle(events_[WAKEALL_0]);

  DeleteCriticalSection(&num_waiters_crit_sect_);
}

void ConditionVariableEventWin::SleepCS(CriticalSectionWrapper& crit_sect) {
  SleepCS(crit_sect, INFINITE);
}

bool ConditionVariableEventWin::SleepCS(CriticalSectionWrapper& crit_sect,
                                        unsigned long max_time_in_ms) {
  EnterCriticalSection(&num_waiters_crit_sect_);

  // Get the eventID for the event that will be triggered by next
  // WakeAll() call and start waiting for it.
  const EventWakeUpType eventID =
      (WAKEALL_0 == eventID_) ? WAKEALL_1 : WAKEALL_0;

  ++(num_waiters_[eventID]);
  LeaveCriticalSection(&num_waiters_crit_sect_);

  CriticalSectionWindows* cs =
      static_cast<CriticalSectionWindows*>(&crit_sect);
  LeaveCriticalSection(&cs->crit);
  HANDLE events[2];
  events[0] = events_[WAKE];
  events[1] = events_[eventID];
  const DWORD result = WaitForMultipleObjects(2,  // Wait on 2 events.
                                              events,
                                              FALSE,  // Wait for either.
                                              max_time_in_ms);

  const bool ret_val = (result != WAIT_TIMEOUT);

  EnterCriticalSection(&num_waiters_crit_sect_);
  --(num_waiters_[eventID]);

  // Last waiter should only be true for WakeAll(). WakeAll() correspond
  // to position 1 in events[] -> (result == WAIT_OBJECT_0 + 1)
  const bool last_waiter = (result == WAIT_OBJECT_0 + 1) &&
      (num_waiters_[eventID] == 0);
  LeaveCriticalSection(&num_waiters_crit_sect_);

  if (last_waiter) {
    // Reset/unset the WakeAll() event since all threads have been
    // released.
    ResetEvent(events_[eventID]);
  }

  EnterCriticalSection(&cs->crit);
  return ret_val;
}

void ConditionVariableEventWin::Wake() {
  EnterCriticalSection(&num_waiters_crit_sect_);
  const bool have_waiters = (num_waiters_[WAKEALL_0] > 0) ||
      (num_waiters_[WAKEALL_1] > 0);
  LeaveCriticalSection(&num_waiters_crit_sect_);

  if (have_waiters) {
    SetEvent(events_[WAKE]);
  }
}

void ConditionVariableEventWin::WakeAll() {
  EnterCriticalSection(&num_waiters_crit_sect_);

  // Update current WakeAll() event
  eventID_ = (WAKEALL_0 == eventID_) ? WAKEALL_1 : WAKEALL_0;

  // Trigger current event
  const EventWakeUpType eventID = eventID_;
  const bool have_waiters = num_waiters_[eventID] > 0;
  LeaveCriticalSection(&num_waiters_crit_sect_);

  if (have_waiters) {
    SetEvent(events_[eventID]);
  }
}

}  // namespace webrtc
