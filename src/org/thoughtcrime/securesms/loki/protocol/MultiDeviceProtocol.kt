package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.recipients.Recipient

object MultiDeviceProtocol {

    @JvmStatic
    fun sendUnlinkingRequest(context: Context, publicKey: String) {
        val unlinkingRequest = EphemeralMessage.createUnlinkingRequest(publicKey)
        ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(unlinkingRequest))
    }

    @JvmStatic
    fun sendTextPush(context: Context, recipient: Recipient, messageID: Long) {

    }

    @JvmStatic
    fun sendMediaPush(context: Context, recipient: Recipient, messageID: Long) {

    }

//    private static void sendMessagePush(Context context, MessageType type, Recipient recipient, long messageId) {
//        JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
//
//        // Just send the message normally if it's a group message or we're sending to one of our devices
//        String recipientHexEncodedPublicKey = recipient.getAddress().serialize();
//        if (GeneralUtilitiesKt.isPublicChat(context, recipientHexEncodedPublicKey) || PromiseUtil.get(MultiDeviceUtilities.isOneOfOurDevices(context, recipient.getAddress()), false)) {
//            if (type == MessageType.MEDIA) {
//                PushMediaSendJob.enqueue(context, jobManager, messageId, recipient.getAddress(), false);
//            } else {
//                jobManager.add(new PushTextSendJob(messageId, recipient.getAddress()));
//            }
//            return;
//        }
//
//        // If we get here then we are sending a message to a device that is not ours
//        boolean[] hasSentSyncMessage = { false };
//        MultiDeviceUtilities.getAllDevicePublicKeysWithFriendStatus(context, recipientHexEncodedPublicKey).success(devices -> {
//            int friendCount = MultiDeviceUtilities.getFriendCount(context, devices.keySet());
//            Util.runOnMain(() -> {
//            ArrayList<Job> jobs = new ArrayList<>();
//            for (Map.Entry<String, Boolean> entry : devices.entrySet()) {
//            String deviceHexEncodedPublicKey = entry.getKey();
//            boolean isFriend = entry.getValue();
//
//            Address address = Address.fromSerialized(deviceHexEncodedPublicKey);
//            long messageIDToUse = recipientHexEncodedPublicKey.equals(deviceHexEncodedPublicKey) ? messageId : -1L;
//
//            if (isFriend) {
//                // Send a normal message if the user is friends with the recipient
//                // We should also send a sync message if we haven't already sent one
//                boolean shouldSendSyncMessage = !hasSentSyncMessage[0] && address.isPhone();
//                if (type == MessageType.MEDIA) {
//                    jobs.add(new PushMediaSendJob(messageId, messageIDToUse, address, false, null, shouldSendSyncMessage));
//                } else {
//                    jobs.add(new PushTextSendJob(messageId, messageIDToUse, address, shouldSendSyncMessage));
//                }
//                if (shouldSendSyncMessage) { hasSentSyncMessage[0] = true; }
//            } else {
//                // Send friend requests to non-friends. If the user is friends with any
//                // of the devices then send out a default friend request message.
//                boolean isFriendsWithAny = (friendCount > 0);
//                String defaultFriendRequestMessage = isFriendsWithAny ? "Please accept to enable messages to be synced across devices" : null;
//                if (type == MessageType.MEDIA) {
//                    jobs.add(new PushMediaSendJob(messageId, messageIDToUse, address, true, defaultFriendRequestMessage, false));
//                } else {
//                    jobs.add(new PushTextSendJob(messageId, messageIDToUse, address, true, defaultFriendRequestMessage, false));
//                }
//            }
//        }
//
//            // Start the send
//            if (type == MessageType.MEDIA) {
//                PushMediaSendJob.enqueue(context, jobManager, (List<PushMediaSendJob>)(List)jobs);
//            } else {
//                // Schedule text send jobs
//                jobManager.startChain(jobs).enqueue();
//            }
//        });
//            return Unit.INSTANCE;
//        });
//    }
}
