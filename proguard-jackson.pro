# Proguard configuration for Jackson 2.x (fasterxml package instead of codehaus package)

-keepattributes *Annotation*,EnclosingMethod,Signature
-keepnames class com.fasterxml.jackson.** {
*;
}
-keepnames interface com.fasterxml.jackson.** {
    *;
}
-dontwarn com.fasterxml.jackson.databind.**
-keep class org.codehaus.** { *; }

