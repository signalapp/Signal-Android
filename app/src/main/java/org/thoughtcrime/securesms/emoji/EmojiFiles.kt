package org.thoughtcrime.securesms.emoji

import android.content.Context
import android.net.Uri
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.source
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream
import org.thoughtcrime.securesms.mms.PartAuthority
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * File structure:
 * <p>
 * emoji/
 * .version  -- Contains MD5 hash of current version plus a uuid mapping
 * `uuid`/ -- Directory for a specific MD5hash underneath which all the data lives.
 * | .names -- Contains name mappings for downloaded files. When a file finishes downloading, we create a random UUID name for it and add it to .names
 * | `uuid1`
 * | `uuid2`
 * | ...
 * <p>
 * .version format:
 * <p>
 * {"version": ..., "uuid": "..."}
 * <p>
 * .name format:
 * [
 * {"name": "...", "uuid": "..."}
 * ]
 */
private const val TAG = "EmojiFiles"

private const val EMOJI_DIRECTORY = "emoji"
private const val VERSION_FILE = ".version"
private const val NAME_FILE = ".names"
private const val JUMBO_FILE = ".jumbos"
private const val EMOJI_JSON = "emoji_data.json"

private fun Context.getEmojiDirectory(): File = getDir(EMOJI_DIRECTORY, Context.MODE_PRIVATE)
private fun Context.getVersionFile(): File = File(getEmojiDirectory(), VERSION_FILE)
private fun Context.getNameFile(versionUuid: UUID): File = File(File(getEmojiDirectory(), versionUuid.toString()).apply { mkdir() }, NAME_FILE)
private fun Context.getJumboFile(versionUuid: UUID): File = File(File(getEmojiDirectory(), versionUuid.toString()).apply { mkdir() }, JUMBO_FILE)

@Suppress("UNUSED_PARAMETER")
private fun getFilesUri(name: String, format: String): Uri = PartAuthority.getEmojiUri(name)

private fun getOutputStream(context: Context, outputFile: File): OutputStream {
  val attachmentSecret = AttachmentSecretProvider.getInstance(context).orCreateAttachmentSecret
  return ModernEncryptingPartOutputStream.createFor(attachmentSecret, outputFile, true).second
}

private fun getInputStream(context: Context, inputFile: File): InputStream {
  val attachmentSecret = AttachmentSecretProvider.getInstance(context).orCreateAttachmentSecret
  return ModernDecryptingPartInputStream.createFor(attachmentSecret, inputFile, 0)
}

object EmojiFiles {
  @JvmStatic
  fun getBaseDirectory(context: Context): File = context.getEmojiDirectory()

  @JvmStatic
  fun delete(context: Context, version: Version, uuid: UUID) {
    try {
      version.getFile(context, uuid).delete()
    } catch (e: IOException) {
      Log.i(TAG, "Failed to delete file.")
    }
  }

  @JvmStatic
  fun openForReading(context: Context, name: String): InputStream {
    val version: Version = Version.readVersion(context) ?: throw IOException("No emoji version is present on disk")
    val names: NameCollection = NameCollection.read(context, version)
    val dataUuid: UUID = names.getUUIDForName(name) ?: throw IOException("Could not get UUID for name $name")
    val file: File = version.getFile(context, dataUuid)

    return getInputStream(context, file)
  }

  fun openForReadingJumbo(context: Context, version: Version, names: JumboCollection, name: String): InputStream {
    val dataUuid: UUID = names.getUUIDForName(name) ?: throw IOException("Could not get UUID for name $name")
    val file: File = version.getFile(context, dataUuid)

    return getInputStream(context, file)
  }

  @JvmStatic
  fun openForWriting(context: Context, version: Version, uuid: UUID): OutputStream {
    return getOutputStream(context, version.getFile(context, uuid))
  }

  @JvmStatic
  fun getMd5(context: Context, version: Version, uuid: UUID): ByteArray? {
    val file = version.getFile(context, uuid)

    try {
      HashingSink.md5(blackholeSink()).use { hashingSink ->
        getInputStream(context, file).source().buffer().use { source ->
          source.readAll(hashingSink)

          return hashingSink.hash.toByteArray()
        }
      }
    } catch (e: Exception) {
      Log.i(TAG, "Could not read emoji data file md5", e)
      return null
    }
  }

  @JvmStatic
  fun getLatestEmojiData(context: Context, version: Version): ParsedEmojiData? {
    val names = NameCollection.read(context, version)
    val dataUuid = names.getUUIDForEmojiData() ?: return null
    val file = version.getFile(context, dataUuid)

    getInputStream(context, file).use {
      return EmojiJsonParser.parse(it, ::getFilesUri).getOrElse { throwable ->
        Log.w(TAG, "Failed to parse emoji_data", throwable)
        null
      }
    }
  }

  class Version(@JsonProperty val version: Int, @JsonProperty val uuid: UUID, @JsonProperty val density: String) {

    fun getFile(context: Context, uuid: UUID): File = File(getDirectory(context), uuid.toString())

    private fun getDirectory(context: Context): File = File(context.getEmojiDirectory(), this.uuid.toString()).apply { mkdir() }

    companion object {
      private val objectMapper = ObjectMapper().registerKotlinModule()

      @JvmStatic
      @JvmOverloads
      fun readVersion(context: Context, skipValidation: Boolean = false): Version? {
        val version = try {
          getInputStream(context, context.getVersionFile()).use {
            val tree: JsonNode = objectMapper.readTree(it)
            Version(
              version = tree["version"].asInt(),
              uuid = objectMapper.convertValue(tree["uuid"], UUID::class.java),
              density = tree["density"].asText()
            )
          }
        } catch (e: Exception) {
          Log.w(TAG, "Could not read current emoji version from disk.", e)
          null
        }

        return if (skipValidation || isVersionValid(context, version)) {
          version
        } else {
          null
        }
      }

      @JvmStatic
      fun writeVersion(context: Context, version: Version) {
        val versionFile: File = context.getVersionFile()

        try {
          if (versionFile.exists()) {
            versionFile.delete()
          }

          getOutputStream(context, versionFile).use {
            objectMapper.writeValue(it, version)
          }
        } catch (e: Exception) {
          Log.w(TAG, "Could not write current emoji version from disk.")
        }
      }

      @JvmStatic
      fun isVersionValid(context: Context, version: Version?): Boolean {
        if (version == null) {
          Log.d(TAG, "Version does not exist.")
          return false
        }

        val nameCollection = NameCollection.read(context, version)

        return if (nameCollection.names.isEmpty()) {
          Log.d(TAG, "NameCollection file is empty.")
          false
        } else {
          Log.d(TAG, "Verifying all name files exist.")
          val allNamesExist = nameCollection.names
            .map { version.getFile(context, it.uuid) }
            .all { it.exists() }

          Log.d(TAG, "All names exist? $allNamesExist")

          allNamesExist
        }
      }
    }
  }

  class Name(@JsonProperty val name: String, @JsonProperty val uuid: UUID) {
    companion object {
      @JvmStatic
      fun forEmojiDataJson(): Name = Name(EMOJI_JSON, UUID.randomUUID())
    }
  }

  class NameCollection(@JsonProperty val versionUuid: UUID, @JsonProperty val names: List<Name>) {
    companion object {

      private val objectMapper = ObjectMapper().registerKotlinModule()

      @JvmStatic
      fun read(context: Context, version: Version): NameCollection {
        try {
          getInputStream(context, context.getNameFile(version.uuid)).use { inputStream ->
            val tree: JsonNode = objectMapper.readTree(inputStream)
            val elements = tree["names"].elements().asSequence().map {
              Name(
                name = it["name"].asText(),
                uuid = objectMapper.convertValue(it["uuid"], UUID::class.java)
              )
            }.toList()
            return NameCollection(objectMapper.convertValue(tree["versionUuid"], UUID::class.java), elements)
          }
        } catch (e: Exception) {
          return NameCollection(version.uuid, listOf())
        }
      }

      @JvmStatic
      fun append(context: Context, nameCollection: NameCollection, name: Name): NameCollection {
        val collection = NameCollection(nameCollection.versionUuid, nameCollection.names + name)
        getOutputStream(context, context.getNameFile(nameCollection.versionUuid)).use {
          objectMapper.writeValue(it, collection)
        }
        return collection
      }
    }

    @JsonIgnore
    fun getUUIDForEmojiData(): UUID? = getUUIDForName(EMOJI_JSON)

    @JsonIgnore
    fun getUUIDForName(name: String): UUID? = names.firstOrNull { it.name == name }?.uuid
  }

  class JumboCollection(@JsonProperty val versionUuid: UUID, @JsonProperty val names: List<Name>) {
    companion object {

      private val objectMapper = ObjectMapper().registerKotlinModule()

      @JvmStatic
      fun read(context: Context, version: Version): JumboCollection {
        try {
          getInputStream(context, context.getJumboFile(version.uuid)).use {
            return objectMapper.readValue(it)
          }
        } catch (e: Exception) {
          return JumboCollection(version.uuid, listOf())
        }
      }

      @JvmStatic
      fun append(context: Context, nameCollection: JumboCollection, name: Name): JumboCollection {
        val collection = JumboCollection(nameCollection.versionUuid, nameCollection.names + name)
        getOutputStream(context, context.getJumboFile(nameCollection.versionUuid)).use {
          objectMapper.writeValue(it, collection)
        }
        return collection
      }
    }

    @JsonIgnore
    fun getUUIDForName(name: String): UUID? = names.firstOrNull { it.name == name }?.uuid
  }
}
