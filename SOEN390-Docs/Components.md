# Potential Components List

Components will be based on critical user interface items and system components that they interact with to provide specific functionalities.

Critical User Interface components:
  - Registration
    - This is the screen the user sees the first time they use the app, before they create their profile
    - Subcomponents:
      - Country selection
        - User must select their country from a list
      - Phone number registration 
        - User registers their phone number with the app
        
  - Preferences
    - This is the application preferences menu, accessed from the conversation list view
    - Subcomponents:
      - SMS/MMS preferences
        - Allows user to set Signal as their default SMS app, request delivery reports and enable WiFi Calling compatibility mode 
      - Notification preferences
        - Allows user to customize app notifications, including toggles to enable or disable vibration, sounds, and LEDs
      - Privacy preferences
        - Allows user to enable password-protection, block screenshots, disable keyboard learning, relay calls through Signal server, toggle read receipts and view blocked contacts 
      - Appearance preferences
        - Allows user to change the theme and language of the app
      - Chat/media preferences
        - Allows user to specify media auto-download settings, chat settings (e.g. font size) and whether old messages should be deleted
      - Linked device preferences
        - Allows user to link another device using Signal by scanning a QR code
      - Profile preferences
        - Allows user to change their profile avatar and display name
      
  - Conversation list
    - This is the screen a registered user sees when they open the app. It displays a searchable list of the user's conversations.
    - Subcomponents:
      - Invite friends
        - Allows the user to invite others to use Signal via an invite URL, an external application (e.g. Facebook) or SMS
      - Create group
        - Creates a new group conversation with the specified name, photo and group members
      - Create conversation
        - Creates a new one-on-one conversation with a contact selected from the user's contact list
    
  - Conversation
    - This is the view of a single conversation, showing all sent and received messages.
    - Subcomponents:
      - WebRTC call
        - Starts a audio and/or video call using the WebRTC communication protocol
      - Share attachment
        - Allows user to send attachments, including pictures, audio clips and gifs
      - Media preview
        - Provides a preview and edit functionality of an attachment to be sent, such as a photo
      - Message info
        - Gives details of a particular message, including the timestamp, delivery method and recipients
      - Group conversation
        - A conversation that can have a group name, group photo and multiple members 
      - Conversation preferences
        - Preferences menu for a single conversation, with options to mute the conversation and set notification settings

Critical System components:
  - database
  - encryption
  - authorization manager
  - shared services
  - contact manager
  - message manager
  - attachment manager
  - push manager
