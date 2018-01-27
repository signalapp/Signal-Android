# Message Manager Component

This diagram explains the structure of the Message Manager component and the way the subcomponents work together to provide the interface that is used in the main application

### Diagram Summary:

- ''MessageSender'' is the main class that provides sending messages proxies to the clients. it manages schedualing message deletion, registering message sending jobs, access message related databases. It is responsible for encrypting and decrypting messages before sending them. However, it doesn't directly send messages, it delegates to signal API library

- ''OutgoingMessageManager'' and ''IncomingMessageManager'' are childern of ''Incoming/OutgoingMessages'' classes, and they provide instances and methods with which the clients can easily interact with received/sent messages. However, the instance of these classes are mostly instantiated in "PushReceivedJobs" and "PushDecryptJob"

- ''DatabaseFactory'' is the class responsible for creating the returning instance of the requested databases such as "MmsSmsDatabase"

- ''MessageRecord'' is the model object that represents specific database record 'tuple'. It allows the client such as Conversation Activity to get the Message info without the need to worry about handling db cursors.

### Sample Code Use:

- [Using IncomingMessages Objects](https://github.com/signalapp/Signal-Android/blob/4c2269175b6477c42f8aa6ec5cd4a376cc27a0f9/src/org/thoughtcrime/securesms/jobs/PushContentReceiveJob.java#L40)

 ````java
 //PushContentReceiveJob.java
SignalServiceEnvelope envelope   = new SignalServiceEnvelope(data, sessionKey);
handle(envelope);
..
...
....

//PushReceived.java
 public void handle(SignalServiceEnvelope envelope) {
    Address   source    = Address.fromExternal(context, envelope.getSource());
    Recipient recipient = Recipient.from(context, source, false);

    long messageId = DatabaseFactory.getPushDatabase(context).insert(envelope);
     
    jobManager.add(new PushDecryptJob(context, messageId));
    
````

- [Sending simple text message using Message Sender object](https://github.com/signalapp/Signal-Android/blob/2add02c62f39db39484c13158d3d45b4ef1d7491/src/org/thoughtcrime/securesms/service/QuickResponseService.java#L60)

````java
 return MessageSender.send(context, 
                              masterSecret, 
                              outgoingMessage, 
                              threadId, 
                              forceSms, () -> fragment.releaseOutgoingMessage(id));
                  
````