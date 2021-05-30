# Session Android

[Download on the Google Play Store](https://getsession.org/android)

Add the [F-Droid repo](https://fdroid.getsession.org/)

[Download the APK from here](https://github.com/loki-project/session-android/releases/latest)

## Summary

Session integrates directly with [Oxen Service Nodes](https://docs.oxen.io/about-the-oxen-blockchain/oxen-service-nodes), which are a set of distributed, decentralized and Sybil resistant nodes. Service Nodes act as servers which store messages offline, and a set of nodes which allow for onion routing functionality obfuscating users' IP addresses. For a full understanding of how Session works, read the [Session Whitepaper](https://getsession.org/whitepaper).

<img src="https://i.imgur.com/RSMrR1F.png" width="320" />

## Want to contribute? Found a bug or have a feature request?

Please search for any [existing issues](https://github.com/oxen-io/session-android/issues) that describe your bugs in order to avoid duplicate submissions. Submissions can be made by making a pull request to our `dev` branch. If you don't know where to start contributing, try reading the Github issues page for ideas.

## Build instructions

Build instructions can be found in [BUILDING.md](BUILDING.md).

## Translations

Want to help us translate Session into your language? You can do so at https://crowdin.com/project/session-android!

## Verifying signatures

**Step 1:**

```
wget https://raw.githubusercontent.com/oxen-io/oxen-core/master/utils/gpg_keys/KeeJef.asc
gpg --import KeeJef.asc
```

**Step 2:**

Get the signed hash for this release. `SESSION_VERSION` needs to be updated for the release you want to verify.

```
export SESSION_VERSION=1.10.4
wget https://github.com/oxen-io/session-android/releases/download/$SESSION_VERSION/signatures.asc
```

**Step 3:**

Verify the signature of the hashes of the files.

```
gpg --verify signatures.asc 2>&1 |grep "Good signature from"
```

The command above should print "`Good signature from "Kee Jefferys...`". If it does, the hashes are valid but we still have to make the sure the signed hashes matches the downloaded files.

**Step 4:**

Make sure the two commands below returns the same hash. If they do, files are valid.

```
sha256sum session-$SESSION_VERSION-universal.apk
grep universal.apk signatures.asc
```

## License

Copyright 2011 Whisper Systems

Copyright 2013-2017 Open Whisper Systems

Copyright 2019-2021 The Loki Project

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html
