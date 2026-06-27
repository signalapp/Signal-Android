plugins {
  alias(conventionPlugins.plugins.signal.library)
  id("kotlin-parcelize")
}

android {
  namespace = "org.signal.blurhash"
}
