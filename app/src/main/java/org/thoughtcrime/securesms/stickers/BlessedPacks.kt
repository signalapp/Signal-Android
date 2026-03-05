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

  @JvmField
  val MY_DAILY_LIFE_2 = Pack("a2414255948558316f37c1d36c64cd28", "fda12937196d236f1ca9e1196a56542e1d1cef6ff84e2be03828717fa20ad366")

  @JvmField
  val ROCKY_TALK = Pack("42fb75e1827c0c945cfb5ca0975db03c", "eee27e2b9f773e0a55ea24c340b7be858711a6e2bd9b6ee7044343e0e428be65")

  @JvmField
  val COZY_SEASON = Pack("684d2b7bcfc2eec6f57f2e7be0078e0f", "866e0dcb4a1b25f2b04df270cd742723e4a6555c0a1abc3f3f30dcc5a2010c55")

  @JvmField
  val CHUG_THE_MOUSE = Pack("f19548e5afa38d1ce4f5c3191eba5e30", "2cb3076740f669aa44c6c063290b249a7d00a4b02ed8f9e9a5b902a37f1bbc41")

  @JvmField
  val CROCOS_FEELINGS = Pack("3044281a51307306e5442f2e9070953a", "c4caaa84397e1a630a5960f54a0b82753c88a5e52e0defe615ba4dd80f130cbf")

  private val packs = listOf(
    BlessedPackInfo(
      pack = ROCKY_TALK,
      installMode = BlessedPackInfo.InstallMode.InstallByDefault
    ),
    BlessedPackInfo(
      pack = MY_DAILY_LIFE,
      installMode = BlessedPackInfo.InstallMode.InstallByDefault
    ),
    BlessedPackInfo(
      pack = ZOZO,
      installMode = BlessedPackInfo.InstallMode.InstallByDefault
    ),
    BlessedPackInfo(
      pack = CROCOS_FEELINGS,
      installMode = BlessedPackInfo.InstallMode.InstallByDefault
    ),
    BlessedPackInfo(
      pack = SWOON_HANDS,
      installMode = BlessedPackInfo.InstallMode.AvailableAsReference
    ),
    BlessedPackInfo(
      pack = SWOON_FACES,
      installMode = BlessedPackInfo.InstallMode.AvailableAsReference
    ),
    BlessedPackInfo(
      pack = MY_DAILY_LIFE_2,
      installMode = BlessedPackInfo.InstallMode.AvailableAsReference
    ),
    BlessedPackInfo(
      pack = BANDIT,
      installMode = BlessedPackInfo.InstallMode.AvailableAsReference
    ),
    BlessedPackInfo(
      pack = DAY_BY_DAY,
      installMode = BlessedPackInfo.InstallMode.AvailableAsReference
    ),
    BlessedPackInfo(
      pack = COZY_SEASON,
      installMode = BlessedPackInfo.InstallMode.AvailableAsReference
    ),
    BlessedPackInfo(
      pack = CHUG_THE_MOUSE,
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
