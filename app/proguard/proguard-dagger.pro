-keepattributes *Annotation*,EnclosingMethod
-keep @interface dagger.*,javax.inject.*
-keep @dagger.Module class *
-keepclassmembers class * {
    @javax.inject.* *;
    @dagger.* *;
    <init>();
}
-keepclasseswithmembernames class * {
  @javax.inject.* <fields>;
}
-keep class javax.inject.** { *; }
-keep class **$$ModuleAdapter
-keep class **$$InjectAdapter
-keep class **$$StaticInjection

-keep class dagger.** { *; }
-keep class * extends dagger.** { *; }
-keep interface dagger.** {*;}
-dontwarn dagger.internal.codegen.**
