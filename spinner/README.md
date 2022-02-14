# Spinner
Spinner is a development tool that lets you inspect and run queries against an app's database(s) in a convenient web interface.

## Getting Started
Install one of the spinner build variants (e.g. `./gradlew installPlayProdSpinner`) and run the following adb command:

```bash
adb forward tcp:5000 tcp:5000
```

Then, navigate to `localhost:5000` in your web browser.

Magic!

## How does it work?
Spinner is just a [NanoHttpd](https://github.com/NanoHttpd/nanohttpd) server that runs a little webapp in the background. 
You initialize Spinner in `Application.onCreate` with a list of databases you wish to let it run queries against.
Then, you can use the `adb forward` command to route the Android device's port to a port on your local machine.

## What's with the name?
It's a riff on Flipper, a development tool we used to use. It was very useful, but also wildly unstable (at least on Linux).
