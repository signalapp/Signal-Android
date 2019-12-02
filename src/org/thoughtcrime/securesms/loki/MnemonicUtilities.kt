package org.thoughtcrime.securesms.loki

import android.content.Context
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.removing05PrefixIfNeeded
import java.io.File
import java.io.FileOutputStream

object MnemonicUtilities {

  @JvmStatic
  public fun getLanguageFileDirectory(context: Context): File {
    val languages = listOf( "english", "japanese", "portuguese", "spanish" )
    val directory = File(context.applicationInfo.dataDir)
    for (language in languages) {
      val fileName = "$language.txt"
      if (directory.list().contains(fileName)) { continue }
      val inputStream = context.assets.open("mnemonic/$fileName")
      val file = File(directory, fileName)
      val outputStream = FileOutputStream(file)
      val buffer = ByteArray(1024)
      while (true) {
        val count = inputStream.read(buffer)
        if (count < 0) { break }
        outputStream.write(buffer, 0, count)
      }
      inputStream.close()
      outputStream.close()
    }
    return directory
  }

  @JvmStatic
  public fun getFirst3Words(codec: MnemonicCodec, hexEncodedPublicKey: String): String {
    return codec.encode(hexEncodedPublicKey.removing05PrefixIfNeeded()).split(" ").slice(0 until 3).joinToString(" ")
  }
}