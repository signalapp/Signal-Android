package org.signal.spinner

import android.content.Context
import com.github.jknack.handlebars.io.StringTemplateSource
import com.github.jknack.handlebars.io.TemplateLoader
import com.github.jknack.handlebars.io.TemplateSource
import org.signal.core.util.StreamUtil
import java.nio.charset.Charset

/**
 * A loader read handlebars templates from the assets directory.
 */
class AssetTemplateLoader(private val context: Context) : TemplateLoader {

  override fun sourceAt(location: String): TemplateSource {
    val content: String = StreamUtil.readFullyAsString(context.assets.open("$location.hbs"))
    return StringTemplateSource(location, content)
  }

  override fun resolve(location: String): String {
    return location
  }

  override fun getPrefix(): String {
    return ""
  }

  override fun getSuffix(): String {
    return ""
  }

  override fun setPrefix(prefix: String) {
    TODO("Not yet implemented")
  }

  override fun setSuffix(suffix: String) {
    TODO("Not yet implemented")
  }

  override fun setCharset(charset: Charset?) {
    TODO("Not yet implemented")
  }

  override fun getCharset(): Charset {
    return Charset.defaultCharset()
  }
}
