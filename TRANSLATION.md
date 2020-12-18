## How to translate Session into new languages quickly and easily

There are people all around the world who can benefit from access to Session — and that means we need to make Session available in as many languages as possible. We’ve had a number of requests from community members wanting to help out by translating Session into currently unsupported (or only partially supported) languages. If you speak multiple languages and you want to help us make Session available in another language you speak, this guide will show you how to translate Session’s iOS and Android apps quickly and easily.

### Translating Session for iOS:

Translating Session iOS into a new language is easy! You’ll need a web browser, a plaintext editor (TextEdit on macOS or Notepad on Windows will do), and a little bit of time and patience.

#### Step 1: Retrieving English strings
- Go to [Session’s iOS GitHub page](https://github.com/loki-project/session-ios)
- In the list of folders and files, click **Session**
- Click **Meta**
- Click **Translations**
- Click **en.lproj**
- Click **localizable.strings**
- Scroll down to Line #2557 (using the line numbers on the left-hand side of the text). This line should contain the text `// MARK: - Session` (note: if this line does not contain that text, use Ctrl+F, Cmd+F, or your browser’s Find on Page function to find the line which contains that text)
- Select and copy all text from that line down to the end of the file
- Open a plaintext editor (TextEdit on macOS, Notepad on Windows, or your preferred plaintext editor) and create a new file
- Paste the text you copied earlier into this blank file

#### Step 2: Translate!
This file will now have a large number of lines of code. Each line contains 2 sets of information, both in quotation marks, like this:

`"continue_2" = "Continue";`

For each line of code, translate the word or sentence to the **right** of the equals sign into the language you are translating into. Do **not** edit the text to the **left** of the equals sign. 

Once you are finished translating, save the file with a filename that specifies which language you have translated the app into, and email the file to us at support@getsession.org along with information about the language you have translated the app into. Be sure to specify in your email that you have translated the iOS app (for information on translating the Android app, see below). Our developers will then take your translation and apply it to the Session iOS app. Thank you for helping make Session more accessible for everyone!


### Translating for Android:

It’s just as easy to add new translations on Session Android! Once again, you’ll need a web browser, a plaintext editor (TextEdit on macOS or Notepad on Windows will do), and a little bit of time and patience.

#### Step 1: Retrieving English strings
- Go to [Session’s Android GitHub page](https://github.com/loki-project/session-android)
- In the list of files and folders, click **res**
- Click **values** (you will need to scroll down to find this folder; make sure you click the folder named **values** and not any of the folders named **values-xx** or with other suffixes)
- Click **strings.xml**
- Scroll down to Line #1657 (using the line numbers on the left-hand side of the text). This line should contain the text `<!-- Session -->` (note: if this line does not contain that text, use Ctrl+F, Cmd+F, or your browser’s Find on Page function to find the line which contains that text)
- Select and copy all text from that line down to the end of the file
- Open a plaintext editor (TextEdit on macOS, Notepad on Windows, or your preferred plaintext editor) and create a new file
- Paste the text you copied earlier into this blank file

#### Step 2: Translate!
This file will now have a large number of lines of code. Each line will contain `<string name=”xxx”>` and `</string>` tags. To translate Session Android, translate the text between these tags. For example:

`<string name="continue_2">Continue</string>`

In this line, translate the word **Continue** into the language you are translating into. Do not translate any other text. Do **not** translate the text inside either pair of angled brackets <>.

Translate the word or sentence between each pair of `<string></string>` tags, on each line. 

Once you are finished translating, save the file with a filename that specifies which language you have translated the app into, and email the file to us at support@getsession.org along with information about the language you have translated the app into. Be sure to specify in your email that you have translated the Android app (for information on translating the iOS app, see above). Our developers will then take your translation and apply it to the Session Android app. Thank you for helping make Session more accessible for everyone!
