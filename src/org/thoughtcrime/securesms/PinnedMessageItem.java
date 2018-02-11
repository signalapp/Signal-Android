package org.thoughtcrime.securesms;

public class PinnedMessageItem {
	private String messageContent;

	public PinnedMessageItem(String messageContent) {
		this.messageContent = messageContent;
	}

	public String getMessageContent() {
		return messageContent;
	}
}