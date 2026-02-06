package org.thoughtcrime.securesms.stickers

import com.fasterxml.jackson.annotation.JsonProperty
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob
import org.whispersystems.signalservice.internal.util.JsonUtil

/**
 * Maintains a list of "blessed" sticker packs that essentially serve as defaults.
 */
object BlessedPacks {

  @JvmField
  val ZOZO = Pack("fb535407d2f6497ec074df8b9c51dd1d", "17e971c134035622781d2ee249e6473b774583750b68c11bb82b7509c68b6dfd")

  @JvmField
  val BANDIT = Pack("9acc9e8aba563d26a4994e69263e3b25", "5a6dff3948c28efb9b7aaf93ecc375c69fc316e78077ed26867a14d10a0f6a12")

  @JvmField
  val SWOON_HANDS = Pack("e61fa0867031597467ccc036cc65d403", "13ae7b1a7407318280e9b38c1261ded38e0e7138b9f964a6ccbb73e40f737a9b")

  @JvmField
  val SWOON_FACES = Pack("cca32f5b905208b7d0f1e17f23fdc185", "8bf8e95f7a45bdeafe0c8f5b002ef01ab95b8f1b5baac4019ccd6b6be0b1837a")

  @JvmField
  val DAY_BY_DAY = Pack("cfc50156556893ef9838069d3890fe49", "5f5beab7d382443cb00a1e48eb95297b6b8cadfd0631e5d0d9dc949e6999ff4b")

  @JvmField
  val MY_DAILY_LIFE = Pack("ccc89a05dc077856b57351e90697976c", "45730e60f09d5566115223744537a6b7d9ea99ceeacb77a1fbd6801b9607fbcf")

  private val packs = listOf(
    BlessedPackInfo(
      pack = ZOZO,
      installMode = BlessedPackInfo.InstallMode.InstallByDefault
    ),
    BlessedPackInfo(
      pack = BANDIT,
      installMode = BlessedPackInfo.InstallMode.InstallByDefault
    ),
    BlessedPackInfo(
      pack = DAY_BY_DAY,
      installMode = BlessedPackInfo.InstallMode.InstallByDefault
    ),
    BlessedPackInfo(
      pack = MY_DAILY_LIFE,
      installMode = BlessedPackInfo.InstallMode.InstallByDefault
    ),
    BlessedPackInfo(
      pack = SWOON_HANDS,
      installMode = BlessedPackInfo.InstallMode.AvailableAsReference
    ),
    BlessedPackInfo(
      pack = SWOON_FACES,
      installMode = BlessedPackInfo.InstallMode.AvailableAsReference
    )
  )

  private val packIds: Set<String> by lazy {
    packs.map { it.pack.packId }.toSet()
  }

  /**
   * A list of [Job]s you should run on first app launch to install all of the required packs.
   */
  @JvmStatic
  fun getFirstInstallJobs(): List<Job> {
    val installedByDefault = packs
      .filter { it.installMode == BlessedPackInfo.InstallMode.InstallByDefault }
      .map { it.pack }
      .map { StickerPackDownloadJob.forInstall(it.packId, it.packKey, false) }

    val availableForReference = packs
      .filter { it.installMode == BlessedPackInfo.InstallMode.AvailableAsReference }
      .map { it.pack }
      .map { StickerPackDownloadJob.forReference(it.packId, it.packKey) }

    return installedByDefault + availableForReference
  }

  @JvmStatic
  fun contains(packId: String): Boolean {
    return packIds.contains(packId)
  }

  private data class BlessedPackInfo(
    val pack: Pack,
    val installMode: InstallMode
  ) {
    enum class InstallMode {
      /** Install the pack on initial app launch. */
      InstallByDefault,

      /** Do not fully install the pack. Instead, have it show up as an "available" pack in the sticker manager. */
      AvailableAsReference
    }
  }

  class Pack(
    @field:JsonProperty val packId: String,
    @field:JsonProperty val packKey: String
  ) {
    fun toJson(): String {
      return JsonUtil.toJson(this)
    }

    companion object {
      @JvmStatic
      fun fromJson(json: String): Pack {
        return JsonUtil.fromJson(json, Pack::class.java)
      }
    }
  }
}
