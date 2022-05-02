package org.thoughtcrime.securesms.glide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.push.SignalServiceTrustStore
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.api.util.Tls12SocketFactory
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager
import org.whispersystems.signalservice.internal.util.Util
import java.io.InputStream
import java.lang.Exception
import java.security.KeyManagementException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Glide Model allowing the direct loading of a GiftBadge.
 *
 * This model will first resolve a GiftBadge into a Badge, and then it will delegate to the Badge loader.
 */
data class GiftBadgeModel(val giftBadge: GiftBadge) : Key {
  class Loader(val client: OkHttpClient) : ModelLoader<GiftBadgeModel, InputStream> {
    override fun buildLoadData(model: GiftBadgeModel, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
      return ModelLoader.LoadData(model, Fetcher(client, model))
    }

    override fun handles(model: GiftBadgeModel): Boolean = true
  }

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
  }

  class Fetcher(
    private val client: OkHttpClient,
    private val giftBadge: GiftBadgeModel
  ) : DataFetcher<InputStream> {

    private var okHttpStreamFetcher: OkHttpStreamFetcher? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
      try {
        val receiptCredentialPresentation = ReceiptCredentialPresentation(giftBadge.giftBadge.redemptionToken.toByteArray())
        val giftBadgeResponse = ApplicationDependencies.getDonationsService().getGiftBadge(Locale.getDefault(), receiptCredentialPresentation.receiptLevel).blockingGet()
        if (giftBadgeResponse.result.isPresent) {
          val badge = Badges.fromServiceBadge(giftBadgeResponse.result.get())
          okHttpStreamFetcher = OkHttpStreamFetcher(client, GlideUrl(badge.imageUrl.toString()))
          okHttpStreamFetcher?.loadData(priority, callback)
        } else if (giftBadgeResponse.applicationError.isPresent) {
          callback.onLoadFailed(Exception(giftBadgeResponse.applicationError.get()))
        } else if (giftBadgeResponse.executionError.isPresent) {
          callback.onLoadFailed(Exception(giftBadgeResponse.executionError.get()))
        } else {
          callback.onLoadFailed(Exception("No result or error in service response."))
        }
      } catch (e: Exception) {
        callback.onLoadFailed(e)
      }
    }

    override fun cleanup() {
      okHttpStreamFetcher?.cleanup()
    }

    override fun cancel() {
      okHttpStreamFetcher?.cancel()
    }

    override fun getDataClass(): Class<InputStream> {
      return InputStream::class.java
    }

    override fun getDataSource(): DataSource {
      return DataSource.REMOTE
    }
  }

  class Factory(private val client: OkHttpClient) : ModelLoaderFactory<GiftBadgeModel, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GiftBadgeModel, InputStream> {
      return Loader(client)
    }

    override fun teardown() {}
  }

  companion object {
    @JvmStatic
    fun createFactory(): Factory {
      return try {
        val baseClient = ApplicationDependencies.getOkHttpClient()
        val sslContext = SSLContext.getInstance("TLS")
        val trustStore: TrustStore = SignalServiceTrustStore(ApplicationDependencies.getApplication())
        val trustManagers = BlacklistingTrustManager.createFor(trustStore)

        sslContext.init(null, trustManagers, null)

        val client = baseClient.newBuilder()
          .sslSocketFactory(Tls12SocketFactory(sslContext.socketFactory), trustManagers[0] as X509TrustManager)
          .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
          .build()

        Factory(client)
      } catch (e: NoSuchAlgorithmException) {
        throw AssertionError(e)
      } catch (e: KeyManagementException) {
        throw AssertionError(e)
      }
    }
  }
}
