# Signal Android 
- [Signal Android](#signal-android)
  - [Contributing Bug reports](#contributing-bug-reports)
  - [Joining the Beta](#joining-the-beta)
  - [Contributing Code](#contributing-code)
  - [Contributing Ideas](#contributing-ideas)
- [Help](#help)
  - [Support](#support)
  - [Installation \& Documentation](#installation--documentation)
- [Legal things](#legal-things)
  - [Cryptography Notice](#cryptography-notice)
  - [License](#license)

(Utilized VSCode Markdown ALl in One Extension for TOC)
Signal uses your phone's data connection (WiFi/3G/4G/5G) to communicate securely. Millions of people use Signal every day for free and instantaneous communication anywhere in the world. Send and receive high-fidelity messages, participate in HD voice/video calls, and explore a growing set of new features that help you stay connected. Signal’s advanced privacy-preserving technology is always enabled, so you can focus on sharing the moments that matter with the people who matter to you.

Signal is an application that is compatible with the Desktop, Android, and IOS devicesThis specific repository is concerned with Signal Android. 
Signal Android is specifically compatible with the Android Operating System, found in Samsung phones, Google phones, and more. 

Currently available on the Play Store and [signal.org](https://signal.org/android/apk/).

<a href='https://play.google.com/store/apps/details?id=org.thoughtcrime.securesms&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height='80px'/></a>

## Contributing Bug reports
We use GitHub for bug tracking. Please search the existing issues for your bug and create a new one if the issue is not yet tracked!

https://github.com/signalapp/Signal-Android/issues

## Joining the Beta
Want to live life on the bleeding edge and help out with testing?

You can subscribe to Signal Android Beta releases here:
https://play.google.com/apps/testing/org.thoughtcrime.securesms

If you're interested in a life of peace and tranquility, stick with the standard releases.

## Contributing Code

To contribute to the Signal Android repository:
- fork the repo: https://github.com/signalapp/Signal-Android
- Clone your forked repo
- Make changes in your fork on a specific branch
- Push to your fork
- Create a pull request with the original Signal Android repo
- Helpful Link on contributing to open source projects: https://www.dataschool.io/how-to-contribute-on-github/ 

If you're new to the Signal codebase, we recommend going through our issues and picking out a simple bug to fix (check the "easy" label in our issues) in order to get yourself familiar. Also please have a look at the [CONTRIBUTING.md](https://github.com/signalapp/Signal-Android/blob/main/CONTRIBUTING.md), that might answer some of your questions.

- When contributing code, it is important to remember to test your code thoroughly before creating a pull reuqest.
- Different types of testing that can be done are functionality testing, unit tests, performance testing and more. 
- Helpful testing methods can be found in this link: https://techbeacon.com/app-dev-testing/5-key-software-testing-steps-every-engineer-should-perform 

For larger changes and feature ideas, we ask that you propose it on the [unofficial Community Forum](https://community.signalusers.org) for a high-level discussion with the wider community before implementation.

## Contributing Ideas

We want to continuously improve on Signal's functionality and features.
We would greatly appreciate your ideas to enable us to thrive and grow! 

Have something you want to say about Signal projects or want to be part of the conversation? Get involved in the [community forum](https://community.signalusers.org).

Help
====
## Support
For troubleshooting and questions, please visit our support center!

https://support.signal.org/

## Installation & Documentation

To build the signal application, here are a set of steps to follow that are the best for anyone who wants to contribute, including beginners. 

- Step 1: In your CLI, cd into the directory you would like to have your project in. 
  - Example: cd /Desktop 
- Step 2: In CLI, perform "git clone [url of forked repo]"
- Step 3: Download a JDK that is Java 8 for proper installation
- Step 4: Open your project in Android Studio to enable easy access to the SDK manaeger
  - Download Android Studio if necessary
- Step 5: In Android Studio, go to Tools -> SDK Manager -> SDK Tools and check every box in the Tool kit and click apply to download all the SDK Tools
- Step 6: In CLI, perform ./gradlew build 
- Step 7: Ensure to locate the apks, which should be in the output folder after it says 
  "Build Successful"

Although you can use a variety of IDEs for this project, Android Studio is the best for these reasons (especially for beginners):
- Handles Functionality of Android Based Applications 
- Enables Pairing of different devices for the application 
- Has easy access to the SDK Manager for tracking of tools 

Additional Documentation & Build information can be found in the Signal Android Wiki:
- https://github.com/signalapp/Signal-Android/wiki/How-to-build-Signal-from-the-sources#prerequisites 
- https://github.com/signalapp/Signal-Android/wiki

# Legal things
## Cryptography Notice

This distribution includes cryptographic software. The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software.
BEFORE using any encryption software, please check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to see if this is permitted.
See <http://www.wassenaar.org/> for more information.

The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1, which includes information security software using or performing cryptographic functions with asymmetric algorithms.
The form and manner of this distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.

## License

Copyright 2013-2022 Signal

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html

Google Play and the Google Play logo are trademarks of Google LLC.
