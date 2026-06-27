plugins {
  alias(conventionPlugins.plugins.signal.library)
}

android {
  namespace = "com.codewaves.stickyheadergrid"
}

dependencies {
  implementation("androidx.recyclerview:recyclerview:1.2.1")
}
