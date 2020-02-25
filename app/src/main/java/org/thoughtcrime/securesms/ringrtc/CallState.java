package org.thoughtcrime.securesms.ringrtc;

/**
 *
 * Enumeration of call state
 *
 */
public enum CallState {

  /** Idle, setting up objects */
  IDLE,

  /** Dialing.  Outgoing call is signaling the remote peer */
  DIALING,

  /** Answering.  Incoming call is responding to remote peer */
  ANSWERING,

  /** Remote ringing. Outgoing call, ICE negotiation is complete */
  REMOTE_RINGING,

  /** Local ringing. Incoming call, ICE negotiation is complete */
  LOCAL_RINGING,

  /** Connected. Incoming/Outgoing call, the call is connected */
  CONNECTED,

  /** Terminated.  Incoming/Outgoing call, the call is terminated */
  TERMINATED,

  /** Busy.  Outgoing call received a busy notification */
  RECEIVED_BUSY;

}
