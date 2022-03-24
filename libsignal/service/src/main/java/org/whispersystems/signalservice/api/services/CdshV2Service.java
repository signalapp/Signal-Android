package org.whispersystems.signalservice.api.services;

import com.google.protobuf.ByteString;

import org.signal.cds.ClientRequest;
import org.signal.cds.ClientResponse;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;

/**
 * Handles network interactions with CDSHv2, the HSM-backed CDS service.
 */
public final class CdshV2Service {

  private static final String TAG = CdshV2Service.class.getSimpleName();

  private static final UUID EMPTY_UUID         = new UUID(0, 0);
  private static final int  RESPONSE_ITEM_SIZE = 8 + 16 + 16; // 1 uint64 + 2 UUIDs

  private final CdshSocket cdshSocket;

  public CdshV2Service(SignalServiceConfiguration configuration, String hexPublicKey, String hexCodeHash) {
    this.cdshSocket = new CdshSocket(configuration, hexPublicKey, hexCodeHash, CdshSocket.Version.V2);
  }

  public Single<ServiceResponse<Response>> getRegisteredUsers(String username, String password, Request request) {
    return cdshSocket
        .connect(username, password, buildClientRequests(request))
        .map(CdshV2Service::parseEntries)
        .collect(Collectors.toList())
        .flatMap(pages -> {
          byte[]                    token = null;
          Map<String, ResponseItem> all   = new HashMap<>();

          for (Response page : pages) {
            all.putAll(page.getResults());
            token = token == null ? page.getToken() : token;
          }

          if (token == null) {
            throw new IOException("No token found in response!");
          }

          return Single.just(new Response(all, token));
        })
        .map(result -> ServiceResponse.forResult(result, 200, null))
        .onErrorReturn(error -> {
          if (error instanceof NonSuccessfulResponseCodeException) {
            int status = ((NonSuccessfulResponseCodeException) error).getCode();
            return ServiceResponse.forApplicationError(error, status, null);
          } else {
            return ServiceResponse.forUnknownError(error);
          }
        });
  }

  private static Response parseEntries(ClientResponse clientResponse) {
    byte[]                    token   = !clientResponse.getToken().isEmpty() ? clientResponse.getToken().toByteArray() : null;
    Map<String, ResponseItem> results = new HashMap<>();
    ByteBuffer                parser  = clientResponse.getE164PniAciTriples().asReadOnlyByteBuffer();

    while (parser.remaining() >= RESPONSE_ITEM_SIZE) {
      String e164    = "+" + parser.getLong();
      UUID   pniUuid = new UUID(parser.getLong(), parser.getLong());
      UUID   aciUuid = new UUID(parser.getLong(), parser.getLong());

      if (!pniUuid.equals(EMPTY_UUID)) {
        PNI pni = PNI.from(pniUuid);
        ACI aci = aciUuid.equals(EMPTY_UUID) ? null : ACI.from(aciUuid);
        results.put(e164, new ResponseItem(pni, Optional.ofNullable(aci)));
      }
    }

    return new Response(results, token);
  }

  private static List<ClientRequest> buildClientRequests(Request request) {
    List<Long> previousE164s = parseAndSortE164Strings(request.previousE164s);
    List<Long> newE164s      = parseAndSortE164Strings(request.newE164s);
    List<Long> removedE164s  = parseAndSortE164Strings(request.removedE164s);

    return Collections.singletonList(ClientRequest.newBuilder()
                                                  .setPrevE164S(toByteString(previousE164s))
                                                  .setNewE164S(toByteString(newE164s))
                                                  .setDiscardE164S(toByteString(removedE164s))
                                                  .setAciUakPairs(toByteString(request.serviceIds))
                                                  .setToken(ByteString.copyFrom(request.token))
                                                  .setHasMore(false)
                                                  .build());
  }

  private static ByteString toByteString(List<Long> numbers) {
    ByteString.Output os = ByteString.newOutput();

    for (long number : numbers) {
      try {
        os.write(ByteUtil.longToByteArray(number));
      } catch (IOException e) {
        throw new AssertionError("Failed to write long to ByteString", e);
      }
    }

    return os.toByteString();
  }

  private static ByteString toByteString(Map<ServiceId, ProfileKey> serviceIds) {
    ByteString.Output os = ByteString.newOutput();

    for (Map.Entry<ServiceId, ProfileKey> entry : serviceIds.entrySet()) {
      try {
        os.write(UuidUtil.toByteArray(entry.getKey().uuid()));
        os.write(UnidentifiedAccess.deriveAccessKeyFrom(entry.getValue()));
      } catch (IOException e) {
        throw new AssertionError("Failed to write long to ByteString", e);
      }
    }

    return os.toByteString();
  }

  private static List<Long> parseAndSortE164Strings(Collection<String> e164s) {
    return e164s.stream()
                .map(Long::parseLong)
                .sorted()
                .collect(Collectors.toList());

  }

  public static final class Request {
    private final Set<String> previousE164s;
    private final Set<String> newE164s;
    private final Set<String> removedE164s;

    private final Map<ServiceId, ProfileKey> serviceIds;

    private final byte[] token;

    public Request(Set<String> previousE164s, Set<String> newE164s, Map<ServiceId, ProfileKey> serviceIds, Optional<byte[]> token) {
      this.previousE164s = previousE164s;
      this.newE164s      = newE164s;
      this.removedE164s  = Collections.emptySet();
      this.serviceIds    = serviceIds;
      this.token         = token.isPresent() ? token.get() : new byte[32];
    }

    public int totalE164s() {
      return previousE164s.size() + newE164s.size() - removedE164s.size();
    }

    public int serviceIdSize() {
      return previousE164s.size() + newE164s.size() + removedE164s.size() + serviceIds.size();
    }
  }

  public static final class Response {
    private final Map<String, ResponseItem> results;
    private final byte[]                    token;

    public Response(Map<String, ResponseItem> results, byte[] token) {
      this.results = results;
      this.token   = token;
    }

    public Map<String, ResponseItem> getResults() {
      return results;
    }

    public byte[] getToken() {
      return token;
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
}
