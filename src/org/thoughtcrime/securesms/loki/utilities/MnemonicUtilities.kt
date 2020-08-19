package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.removing05PrefixIfNeeded
import java.io.File
import java.io.FileOutputStream

object MnemonicUtilities {

  public fun loadFileContents(context: Context, fileName: String): String {
      val inputStream = context.assets.open("mnemonic/$fileName.txt")
      val size = inputStream.available()
      val buffer = ByteArray(size)
      inputStream.read(buffer)
      inputStream.close()
      return String(buffer)
  }

  @JvmStatic
  public fun getFirst3Words(codec: MnemonicCodec, hexEncodedPublicKey: String): String {
      return codec.encode(hexEncodedPublicKey.removing05PrefixIfNeeded()).split(" ").slice(0 until 3).joinToString(" ")
  }
}