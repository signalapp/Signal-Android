package org.whispersystems.signalservice.api.services;

import com.google.protobuf.ByteString;

import org.signal.cds.ClientRequest;
import org.signal.cds.ClientResponse;
import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;

/**
 * Handles network interactions with CDSHv1, the HSM-backed CDS service.
 */
public final class CdshV1Service {

  private static final String TAG = CdshV1Service.class.getSimpleName();

  private static final int  MAX_E164S_PER_REQUEST = 5000;
  private static final UUID EMPTY_ACI             = new UUID(0, 0);
  private static final int  RESPONSE_ITEM_SIZE    = 8 + 16 + 16; // 1 uint64 + 2 UUIDs

  private final CdshSocket cdshSocket;

  public CdshV1Service(SignalServiceConfiguration configuration, String hexPublicKey, String hexCodeHash) {
    this.cdshSocket = new CdshSocket(configuration, hexPublicKey, hexCodeHash, CdshSocket.Version.V1);
  }

  public Single<ServiceResponse<Map<String, ACI>>> getRegisteredUsers(String username, String password, Set<String> e164Numbers) {
    List<String> addressBook = e164Numbers.stream().map(e -> e.substring(1)).collect(Collectors.toList());

    return cdshSocket
        .connect(username, password, buildClientRequests(addressBook))
        .map(CdshV1Service::parseEntries)
        .collect(Collectors.toList())
        .flatMap(pages -> {
          Map<String, ACI> all = new HashMap<>();
          for (Map<String, ACI> page : pages) {
            all.putAll(page);
          }
          return Single.just(all);
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

  private static Map<String, ACI> parseEntries(ClientResponse clientResponse) {
    Map<String, ACI> out    = new HashMap<>();
    ByteBuffer       parser = clientResponse.getE164PniAciTriples().asReadOnlyByteBuffer();

    while (parser.remaining() >= RESPONSE_ITEM_SIZE) {
      String e164      = "+" + parser.getLong();
      UUID   unusedPni = new UUID(parser.getLong(), parser.getLong());
      UUID   aci       = new UUID(parser.getLong(), parser.getLong());

      if (!aci.equals(EMPTY_ACI)) {
        out.put(e164, ACI.from(aci));
      }
    }

    return out;
  }

  private static List<ClientRequest> buildClientRequests(List<String> addressBook) {
    List<ClientRequest> out      = new ArrayList<>((addressBook.size() / MAX_E164S_PER_REQUEST) + 1);
    ByteString.Output   e164Page = ByteString.newOutput();
    int                 pageSize = 0;

    for (String address : addressBook) {
      if (pageSize >= MAX_E164S_PER_REQUEST) {
        pageSize = 0;
        out.add(e164sToRequest(e164Page.toByteString(), true));
        e164Page = ByteString.newOutput();
      }

      try {
        e164Page.write(ByteUtil.longToByteArray(Long.parseLong(address)));
      } catch (IOException e) {
        throw new AssertionError("Failed to write long to ByteString", e);
      }

      pageSize++;
    }

    if (pageSize > 0) {
      out.add(e164sToRequest(e164Page.toByteString(), false));
    }

    return out;
  }

  private static ClientRequest e164sToRequest(ByteString e164s, boolean more) {
    return ClientRequest.newBuilder()
                        .setNewE164S(e164s)
                        .setHasMore(more)
                        .build();
  }
}
