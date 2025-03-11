/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.cds;

import org.signal.libsignal.net.CdsiLookupRequest;
import org.signal.libsignal.net.CdsiLookupResponse;
import org.signal.libsignal.net.Network;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.NetworkResult;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.exceptions.CdsiInvalidArgumentException;
import org.whispersystems.signalservice.api.push.exceptions.CdsiInvalidTokenException;
import org.whispersystems.signalservice.api.push.exceptions.CdsiResourceExhaustedException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * Handles network interactions with CDSI, the SGX-backed version of the CDSv2 API.
 */
public final class CdsiV2Service {

  private final CdsiRequestHandler cdsiRequestHandler;

  public CdsiV2Service(@Nonnull Network network, boolean useLibsignalRouteBasedCDSIConnectionLogic) {
    this.cdsiRequestHandler = (username, password, request, tokenSaver) -> {
      try {
        Future<CdsiLookupResponse> cdsiRequest = network.cdsiLookup(username, password, buildLibsignalRequest(request), tokenSaver, useLibsignalRouteBasedCDSIConnectionLogic);
        return Single.fromFuture(cdsiRequest)
                     .onErrorResumeNext((Throwable err) -> {
                       if (err instanceof ExecutionException && err.getCause() != null) {
                         err = err.getCause();
                       }
                       return Single.error(mapLibsignalError(err));
                     })
                     .map(CdsiV2Service::parseLibsignalResponse)
                     .toObservable();
      } catch (Exception exception) {
        return Observable.error(mapLibsignalError(exception));
      }
    };
  }

  public Single<NetworkResult<Response>> getRegisteredUsers(String username, String password, Request request, Consumer<byte[]> tokenSaver) {
    return cdsiRequestHandler
        .handleRequest(username, password, request, tokenSaver)
        .collect(Collectors.toList())
        .flatMap(pages -> {
          Map<String, ResponseItem> all       = new HashMap<>();
          int                       quotaUsed = 0;

          for (Response page : pages) {
            all.putAll(page.getResults());
            quotaUsed += page.getQuotaUsedDebugOnly();
          }

          return Single.<NetworkResult<Response>>just(new NetworkResult.Success<>(new Response(all, quotaUsed)));
        })
        .onErrorReturn(error -> {
          if (error instanceof NonSuccessfulResponseCodeException) {
            return new NetworkResult.StatusCodeError<>((NonSuccessfulResponseCodeException) error);
          } else if (error instanceof IOException) {
            return new NetworkResult.NetworkError<>((IOException) error);
          } else {
            return new NetworkResult.ApplicationError<>(error);
          }
        });
  }

  private static CdsiLookupRequest buildLibsignalRequest(Request request) {
    HashMap<org.signal.libsignal.protocol.ServiceId, ProfileKey> serviceIds = new HashMap<>(request.serviceIds.size());
    request.serviceIds.forEach((key, value) -> serviceIds.put(key.getLibSignalServiceId(), value));
    return new CdsiLookupRequest(request.previousE164s, request.newE164s, serviceIds, false, Optional.ofNullable(request.token));
  }

  private static Response parseLibsignalResponse(CdsiLookupResponse response) {
    HashMap<String, ResponseItem> responses = new HashMap<>(response.entries().size());
    response.entries().forEach((key, value) -> responses.put(key, new ResponseItem(new PNI(value.pni), Optional.ofNullable(value.aci).map(ACI::new))));
    return new Response(responses, response.debugPermitsUsed);
  }

  private static Throwable mapLibsignalError(Throwable lookupError) {
    if (lookupError instanceof org.signal.libsignal.net.CdsiInvalidTokenException) {
      return new CdsiInvalidTokenException();
    } else if (lookupError instanceof org.signal.libsignal.net.RetryLaterException) {
      org.signal.libsignal.net.RetryLaterException e = (org.signal.libsignal.net.RetryLaterException) lookupError;
      return new CdsiResourceExhaustedException((int) e.duration.getSeconds());
    } else if (lookupError instanceof IllegalArgumentException) {
      return new CdsiInvalidArgumentException();
    }
    return lookupError;
  }

  public static final class Request {
    final Set<String> previousE164s;
    final Set<String> newE164s;
    final Set<String> removedE164s;

    final Map<ServiceId, ProfileKey> serviceIds;

    final byte[] token;

    public Request(Set<String> previousE164s, Set<String> newE164s, Map<ServiceId, ProfileKey> serviceIds, Optional<byte[]> token) {
      if (previousE164s.size() > 0 && !token.isPresent()) {
        throw new IllegalArgumentException("You must have a token if you have previousE164s!");
      }

      this.previousE164s = previousE164s;
      this.newE164s      = newE164s;
      this.removedE164s  = Collections.emptySet();
      this.serviceIds    = serviceIds;
      this.token         = token.orElse(null);
    }

    public int serviceIdSize() {
      return previousE164s.size() + newE164s.size() + removedE164s.size() + serviceIds.size();
    }
  }

  public static final class Response {
    private final Map<String, ResponseItem> results;
    private final int                       quotaUsed;

    public Response(Map<String, ResponseItem> results, int quoteUsed) {
      this.results   = results;
      this.quotaUsed = quoteUsed;
    }

    public Map<String, ResponseItem> getResults() {
      return results;
    }

    /**
     * Tells you how much quota you used in the request. This should only be used for debugging/logging purposed, and should never be relied upon for making
     * actual decisions.
     */
    public int getQuotaUsedDebugOnly() {
      return quotaUsed;
    }
  }

  public static final class ResponseItem {
    private final PNI           pni;
    private final Optional<ACI> aci;

    public ResponseItem(PNI pni, Optional<ACI> aci) {
      this.pni = pni;
      this.aci = aci;
    }

    public PNI getPni() {
      return pni;
    }

    public Optional<ACI> getAci() {
      return aci;
    }

    public boolean hasAci() {
      return aci.isPresent();
    }
  }

  private interface CdsiRequestHandler {
    Observable<Response> handleRequest(String username, String password, Request request, Consumer<byte[]> tokenSaver);
  }
}
