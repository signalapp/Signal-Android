# Database System
This diagram explains the structure of Database component and the way the subcomponents work together to provide the interface that is used in the main application
### Diagram Summary:
- ''Database'' is a superclass that is extended by all different types of databases 

- ''MmsSmsDatabase'' and ''MessagingDatabase'' are childern of ''Database'' class. There are many other child classes. Each of those classes uses SQLiteOpenHelper "android.database.sqlite.SQLiteOpenHelper" to access the local database

- ''DatabaseFactory'' is the class responsible for creating the returning instance of the requested databases such as "MmsSmsDatabase"

- ''MessageRecord'' is the model object that represents specific database record 'tuple'. It allows the client such as Conversation Activity to get the Message info without the need to worry about handling db cursors. 

### Sample Code Use:

- [Create db object of type sms](https://github.com/Radu-Raicea/SignalAndroid/blob/a579545bd10c99d8e8e5c2a4c3629b2a98259be8/src/org/thoughtcrime/securesms/ConversationAdapter.java#L176)
 ````java
MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase(context);
````

- [Using the modelObject by the client](https://github.com/Radu-Raicea/SignalAndroid/blob/a579545bd10c99d8e8e5c2a4c3629b2a98259be8/src/org/thoughtcrime/securesms/ConversationAdapter.java#L324)
````java
 if (messageRecord.isOutgoing() || messageRecord.getDateReceived() <= lastSeen) {
        return i;
      }
````