# Contributing to Signal Android

Thank you for supporting Signal and looking for ways to help. Please note that some conventions here might be a bit different than what you are used to, even if you have contributed to other open source projects before. Reading this document will help you save time and work effectively with the developers and other contributors.


## Development Ideology

Truths which we believe to be self-evident:

1. **The answer is not more options.**  If you feel compelled to add a preference that's exposed to the user, it's very possible you've made a wrong turn somewhere.
1. **The user doesn't know what a key is.**  We need to minimize the points at which a user is exposed to this sort of terminology as extremely as possible.
1. **There are no power users.**  The idea that some users "understand" concepts better than others has proven to be, for the most part, false. If anything, "power users" are more dangerous than the rest, and we should avoid exposing dangerous functionality to them.
1. **If it's "like PGP," it's wrong.**  PGP is our guide for what not to do.
1. **It's an asynchronous world.**  Be wary of anything that is anti-asynchronous: ACKs, protocol confirmations, or any protocol-level "advisory" message.
1. **There is no such thing as time.**  Protocol ideas that require synchronized clocks are doomed to failure.


## Translations

Thanks to a dedicated community of volunteer translators, Signal is now available in more than one hundred languages. We use Transifex to manage our translation efforts, not GitHub. Any suggestions, corrections, or new translations should be submitted to the [Signal localization project for Android](https://www.transifex.com/signalapp/signal-android/).


## Issues

### Useful bug reports
1. Please search both open and closed issues to make sure your bug report is not a duplicate.
1. Read the [guide to submitting useful bug reports](https://github.com/signalapp/Signal-Android/wiki/Submitting-useful-bug-reports) before posting a bug.

### The issue tracker is for bugs, not feature requests
The GitHub issue tracker is not used for feature requests, but new ideas can be submitted and discussed on the [community forum](https://community.signalusers.org/c/feature-requests). The purpose of this issue tracker is to track bugs in the Android client. Bug reports should only be submitted for existing functionality that does not work as intended. Comments that are relevant and concise will help the developers solve issues more quickly.

### Send support questions to support
You can reach support by sending an email to support@signal.org or by visiting the [Signal Support Center](https://support.signal.org/) where you can also search for existing troubleshooting articles and find answers to frequently asked questions. Please do not post support questions on the GitHub issue tracker.

### GitHub is not a generic discussion forum
Conversations about open bug reports belong here. However, all other discussions should take place on the [community forum](https://community.signalusers.org). You can use the community forum to discuss anything that is related to Signal or to hang out with your fellow users in the "Off Topic" category.

### Don't bump issues
Every time someone comments on an issue, GitHub sends an email to [hundreds of people](https://github.com/signalapp/Signal-Android/watchers). Bumping issues with a "+1" (or asking for updates) generates a lot of unnecessary email notifications and does not help anyone solve the issue any faster. Please be respectful of everyone's time and only comment when you have new information to add.

### Open issues

#### If it's open, it's tracked
The developers read every issue, but high-priority bugs or features can take precedence over others. Signal is an open source project, and everyone is encouraged to play an active role in diagnosing and fixing open issues.

### Closed issues

#### "My issue was closed without giving a reason!"
Although we do our best, writing detailed explanations for every issue can be time consuming, and the topic also might have been covered previously in other related issues.


## Pull requests

### Smaller is better
Big changes are significantly less likely to be accepted. Large features often require protocol modifications and necessitate a staged rollout process that is coordinated across millions of users on multiple platforms (Android, iOS, and Desktop).

Try not to take on too much at once. As a first-time contributor, we recommend starting with small and simple PRs in order to become familiar with the codebase. Most of the work should go into discovering which three lines need to change rather than writing the code.

### Sign the Contributor License Agreement (CLA)
You will need to [sign our CLA](https://signal.org/cla/) before your pull request can be merged.

### Follow the Code Style Guidelines
Ensure that your code adheres to the [Code Style Guidelines](https://github.com/signalapp/Signal-Android/wiki/Code-Style-Guidelines) before submitting a pull request.

### Submit finished and well-tested pull requests
Please do not submit pull requests that are still a work in progress. Pull requests should be thoroughly tested and ready to merge before they are submitted.

### Merging can sometimes take a while
If your pull request follows all of the advice above but still has not been merged, this usually means that the developers haven't had time to review it yet. We understand that this might feel frustrating, and we apologize. The Signal team is still small, but [we are hiring](https://signal.org/workworkwork/).


## How can I contribute?
There are several other ways to get involved:
* Help new users learn about Signal.
  * Redirect support questions to support@signal.org and the [Signal Support Center](https://support.signal.org/).
  * Redirect non-bug discussions to the [community forum](https://community.signalusers.org).
* Improve documentation in the [wiki](https://github.com/signalapp/Signal-Android/wiki).
* Join the community of volunteer translators on Transifex:
  * [Android](https://www.transifex.com/signalapp/signal-android/)
  * [iOS](https://www.transifex.com/signalapp/signal-ios/)
  * [Desktop](https://www.transifex.com/signalapp/signal-desktop/)
* Find and mark duplicate issues.
* Try to reproduce issues and help with troubleshooting.
* Discover solutions to open issues and post any relevant findings.
* Test other people's pull requests.
* [Donate to Signal.](https://signal.org/donate/)
* Share Signal with your friends and family.

Signal is made for you. Thank you for your feedback and support.
