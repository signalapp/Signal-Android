##Translations

Please do not submit issues or pull requests for translation fixes. Anyone can update the translations in [Transifex](https://www.transifex.com/projects/p/textsecure-official/). Please submit your corrections there.

## Submitting useful bug reports
1. Search our issues first to make sure this is not a duplicate.
1. Read the [Submitting useful bug reports guide](https://github.com/WhisperSystems/TextSecure/wiki/Submitting-useful-bug-reports) before posting a bug.

## Development Ideology

Truths which we believe to be self-evident:

1. **The answer is not more options.**  If you feel compelled to add a
   preference that's exposed to the user, it's very possible you've made
   a wrong turn somewhere.
1. **The user doesn't know what a key is.**  We need to minimize the points
   at which a user is exposed to this sort of terminology as extremely as
   possible.
1. **There are no power users.** The idea that some users "understand"
   concepts better than others has proven to be, for the most part, false.
   If anything, "power users" are more dangerous than the rest, and we
   should avoid exposing dangerous functionality to them.
1. **If it's "like PGP," it's wrong.**  PGP is our guide for what
   not to do.
1. **It's an asynchronous world.**  Be wary of anything that is
   anti-asynchronous: ACKs, protocol confirmations, or any protocol-level
   "advisory" message.
1. **There is no such thing as time.** Protocol ideas that require synchronized
   clocks are doomed to failure. 

Before you submit a pull request, please check if your code follows the [Code style Guidelines](https://github.com/WhisperSystems/RedPhone/wiki/Code-style-Guidelines).
