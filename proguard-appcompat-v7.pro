# only obfuscate this one pagacke because LGE ROM bug
-keepnames class !android.support.v7.internal.view.menu.**, ** { *; }

-keep public class android.support.v7.widget.** { *; }
-keep public class android.support.v7.internal.widget.** { *; }

-keep public class * extends android.support.v4.view.ActionProvider {
    public <init>(android.content.Context);
}