package org.whispersystems.signalservice.api.websocket;


import okhttp3.Response;

public interface ConnectivityListener {
  void onConnected();
  void onConnecting();
  void onDisconnected();
  void onAuthenticationFailure();
  boolean onGenericFailure(Response response, Throwable throwable);
}
