Building TextSecure
===================

All commands are executed from inside of the TextSecure directory

Fetch ActionBarSherlock:

    git clone --branch 4.2.0 git://github.com/JakeWharton/ActionBarSherlock.git ../ActionBarSherlock

Configure ActionBarSherlock for your android target:

    android update project --path ../ActionBarSherlock/actionbarsherlock --target 1

Configure TextSecure for your android target, linking to ASB:

    android update project --path . --target 1 --library ../ActionBarSherlock/actionbarsherlock

Finally, both codebases must share the android-support jar. As TextSecure's is newer, use it:

    cp libs/android-support-v4.jar ../ActionBarSherlock/actionbarsherlock/libs/android-support-v4.jar

Assuming your android toolchain is correctly configured, it should now be possible to build the TextSecure apk.

    ant debug
