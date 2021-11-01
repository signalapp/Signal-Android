# Signal-LTS

Signal-LTS is an unofficial version of Signal with some extra features, and a longer release cycle.

Expect updates about every 3 months.

## Features

- **Deleting messages time extension**

  Messages must be deleted within 3 hours of sending on Signal and Molly. Signal-LTS has modified this to 24 hours (the limit the recipient will allow deleted messages up to).

- **Does not compress images or video**

  This fork is modified to not compress messages or video. This means that you can now easily send images and video to friends etc, without worrying about extreme compression reducing the quality. We did however make a security trade off here. Images sent via Signal-LTS **DO** contain their original metadata. It is highly recommended to send images and video **ONLY** to people you trust, as the metadata could give away things such as your location when taking the image, time you took it, phone model, etc etc to the recipient.

- **Refuses to send read receipts**

  This fork has been modified to not send read receipts at all. Meaning you may be able to see someone else has read your message, but not vice versa.

- **Refuses to send typing indicators**

  Same as above but for typing indicators.

# License & Legal

License and legal notices in the original [README](README-ORIG.md).
