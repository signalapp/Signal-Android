package org.thoughtcrime.securesms.glide;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.push.SignalServiceTrustStore;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

/**
 * A loader which will load a sprite sheet for a particular badge at the correct dpi for this device.
 */
public class BadgeLoader implements ModelLoader<Badge, InputStream> {

  private final OkHttpClient client;

  private BadgeLoader(OkHttpClient client) {
    this.client = client;
  }

  @Override
  public @Nullable LoadData<InputStream> buildLoadData(@NonNull Badge request, int width, int height, @NonNull Options options) {
    return new LoadData<>(request, new OkHttpStreamFetcher(client, new GlideUrl(request.getImageUrl().toString())));
  }

  @Override
  public boolean handles(@NonNull Badge badgeSpriteSheetRequest) {
    return true;
  }

  public static Factory createFactory() {
    try {
      OkHttpClient   baseClient    = ApplicationDependencies.getOkHttpClient();
      SSLContext     sslContext    = SSLContext.getInstance("TLS");
      TrustStore     trustStore    = new SignalServiceTrustStore(ApplicationDependencies.getApplication());
      TrustManager[] trustManagers = BlacklistingTrustManager.createFor(trustStore);

      sslContext.init(null, trustManagers, null);

      OkHttpClient client = baseClient.newBuilder()
                                      .sslSocketFactory(new Tls12SocketFactory(sslContext.getSocketFactory()), (X509TrustManager) trustManagers[0])
                                      .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
                                      .build();

      return new Factory(client);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  public static class Factory implements ModelLoaderFactory<Badge, InputStream> {

    private final OkHttpClient client;

    private Factory(@NonNull OkHttpClient client) {
      this.client = client;
    }

    @Override
    public @NonNull ModelLoader<Badge, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
      return new BadgeLoader(client);
    }

    @Override
    public void teardown() {
    }
  }
}