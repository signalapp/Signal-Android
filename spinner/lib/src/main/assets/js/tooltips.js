function adjustTooltipPosition(tooltipSpan) {
  // Reset any previous alignment classes
  tooltipSpan.classList.remove('align-left', 'align-right');

  // Get tooltip and viewport dimensions
  const tooltipRect = tooltipSpan.getBoundingClientRect();
  const viewportWidth = window.innerWidth;

  // Check if tooltip overflows on the left
  if (tooltipRect.left < 0) {
    tooltipSpan.classList.add('align-left');
  }
  // Check if tooltip overflows on the right
  else if (tooltipRect.right > viewportWidth) {
    tooltipSpan.classList.add('align-right');
  }
}

function initializeRecipientTooltips() {
  const popupElements = document.querySelectorAll('.popup[data-recipient-id]');
  const cache = {};

  popupElements.forEach(element => {
    const recipientId = element.getAttribute('data-recipient-id');
    const tooltipSpan = element.querySelector('.tooltip');

    if (!recipientId || !tooltipSpan) {
      return;
    }

    let hasLoaded = false;

    element.addEventListener('mouseenter', async function() {
      if (hasLoaded) {
        return;
      }

      if (cache[recipientId]) {
        tooltipSpan.textContent = cache[recipientId];
        adjustTooltipPosition(tooltipSpan);
        hasLoaded = true;
        return;
      }

      try {
        const response = await fetch(`/api?api=recipient&id=${recipientId}`);
        if (!response.ok) {
          tooltipSpan.textContent = `Error loading recipient`;
          hasLoaded = true;
          return;
        }

        const data = await response.json();

        const lines = [];

        // Name (primary)
        let nameLine = data.isGroup ? `Group: ${data.name || recipientId}` : `Name: ${data.name || recipientId}`;
        if (data.nickname) {
          nameLine += ` (${data.nickname})`;
        }
        lines.push(nameLine);

        // Group-specific information
        if (data.isGroup) {
          if (data.groupType) {
            lines.push(`Type: ${data.groupType}`);
          }
          if (data.groupId) {
            lines.push(`Group ID: ${data.groupId}`);
          }
          if (data.participantCount !== null && data.participantCount !== undefined) {
            const memberText = data.participantCount === 1 ? 'member' : 'members';
            lines.push(`Members: ${data.participantCount} ${memberText}`);
          }
          const groupStatus = data.isActiveGroup ? 'Active' : 'Inactive';
          lines.push(`Status: ${groupStatus}`);
        } else {
          // Individual-specific information
          // ACI
          if (data.aci) {
            lines.push(`ACI: ${data.aci}`);
          }

          // PNI
          if (data.pni) {
            lines.push(`PNI: ${data.pni}`);
          }

          // Username
          if (data.username) {
            lines.push(`Username: @${data.username}`);
          }

          // Phone number
          if (data.e164) {
            lines.push(`Phone: ${data.e164}`);
          }

          // About/Bio
          if (data.about) {
            const aboutText = data.aboutEmoji ? `${data.aboutEmoji} ${data.about}` : data.about;
            lines.push(`About: ${aboutText}`);
          }

          // Note
          if (data.note) {
            lines.push(`Note: 📝 ${data.note}`);
          }

          // Status indicators
          const statusParts = [];
          if (data.isBlocked) {
            statusParts.push('🚫 Blocked');
          }
          if (data.isSystemContact) {
            statusParts.push('📱 Contact');
          }
          if (statusParts.length > 0) {
            lines.push(`Status: ${statusParts.join(' | ')}`);
          }
        }

        const displayText = lines.join('\n');
        tooltipSpan.textContent = displayText;
        adjustTooltipPosition(tooltipSpan);
        cache[recipientId] = displayText;
        hasLoaded = true;
      } catch (error) {
        console.error('Error fetching recipient data:', error);
        tooltipSpan.textContent = `Error: ${error.message}`;
        hasLoaded = true;
      }
    });
  });
}

function initializeThreadTooltips() {
  const popupElements = document.querySelectorAll('.popup[data-thread-id]');
  const cache = {};

  popupElements.forEach(element => {
    const threadId = element.getAttribute('data-thread-id');
    const tooltipSpan = element.querySelector('.tooltip');

    if (!threadId || !tooltipSpan) {
      return;
    }

    let hasLoaded = false;

    element.addEventListener('mouseenter', async function() {
      if (hasLoaded) {
        return;
      }

      if (cache[threadId]) {
        tooltipSpan.textContent = cache[threadId];
        adjustTooltipPosition(tooltipSpan);
        hasLoaded = true;
        return;
      }

      try {
        const response = await fetch(`/api?api=thread&id=${threadId}`);
        if (!response.ok) {
          tooltipSpan.textContent = `Error loading thread`;
          hasLoaded = true;
          return;
        }

        const data = await response.json();

        const lines = [];

        // Thread ID
        lines.push(`Thread ID: ${data.threadId}`);

        // Recipient
        lines.push(`Recipient: ${data.recipientName} (ID: ${data.recipientId})`);

        // Unread count
        if (data.unreadCount > 0) {
          lines.push(`Unread: ${data.unreadCount}`);
        }

        // Unread self mentions
        if (data.unreadSelfMentionsCount > 0) {
          lines.push(`Unread Mentions: ${data.unreadSelfMentionsCount}`);
        }

        // Snippet
        if (data.snippet) {
          const snippetPreview = data.snippet.length > 50
            ? data.snippet.substring(0, 50) + '...'
            : data.snippet;
          lines.push(`Snippet: ${snippetPreview}`);
        }

        // Date
        if (data.date) {
          const date = new Date(data.date);
          lines.push(`Last Activity: ${date.toLocaleString()}`);
        }

        // Status flags
        const statusParts = [];
        if (data.pinned) {
          statusParts.push('📌 Pinned');
        }
        if (data.archived) {
          statusParts.push('📦 Archived');
        }
        if (statusParts.length > 0) {
          lines.push(`Status: ${statusParts.join(' | ')}`);
        }

        const displayText = lines.join('\n');
        tooltipSpan.textContent = displayText;
        adjustTooltipPosition(tooltipSpan);
        cache[threadId] = displayText;
        hasLoaded = true;
      } catch (error) {
        console.error('Error fetching thread data:', error);
        tooltipSpan.textContent = `Error: ${error.message}`;
        hasLoaded = true;
      }
    });
  });
}

function initializeMessageTooltips() {
  const popupElements = document.querySelectorAll('.popup[data-message-id]');
  const cache = {};

  popupElements.forEach(element => {
    const messageId = element.getAttribute('data-message-id');
    const tooltipSpan = element.querySelector('.tooltip');

    if (!messageId || !tooltipSpan) {
      return;
    }

    let hasLoaded = false;

    element.addEventListener('mouseenter', async function() {
      if (hasLoaded) {
        return;
      }

      if (cache[messageId]) {
        tooltipSpan.textContent = cache[messageId];
        adjustTooltipPosition(tooltipSpan);
        hasLoaded = true;
        return;
      }

      try {
        const response = await fetch(`/api?api=message&id=${messageId}`);
        if (!response.ok) {
          tooltipSpan.textContent = `Error loading message`;
          hasLoaded = true;
          return;
        }

        const data = await response.json();

        const lines = [];

        // Message ID
        lines.push(`Message ID: ${data.messageId}`);

        // Type
        lines.push(`Type: ${data.type}`);

        // From
        lines.push(`From: ${data.fromRecipientName} (ID: ${data.fromRecipientId})`);

        // To
        if (data.toRecipientName) {
          lines.push(`To: ${data.toRecipientName} (ID: ${data.toRecipientId})`);
        }

        // Thread ID
        lines.push(`Thread ID: ${data.threadId}`);

        // Body
        if (data.body) {
          const bodyPreview = data.body.length > 50
            ? data.body.substring(0, 50) + '...'
            : data.body;
          lines.push(`Body: ${bodyPreview}`);
        }

        // Dates
        if (data.dateSent) {
          const dateSent = new Date(data.dateSent);
          lines.push(`Sent: ${dateSent.toLocaleString()}`);
        }

        if (data.dateReceived) {
          const dateReceived = new Date(data.dateReceived);
          lines.push(`Received: ${dateReceived.toLocaleString()}`);
        }

        const displayText = lines.join('\n');
        tooltipSpan.textContent = displayText;
        adjustTooltipPosition(tooltipSpan);
        cache[messageId] = displayText;
        hasLoaded = true;
      } catch (error) {
        console.error('Error fetching message data:', error);
        tooltipSpan.textContent = `Error: ${error.message}`;
        hasLoaded = true;
      }
    });
  });
}

function initializeTooltips() {
  initializeRecipientTooltips();
  initializeThreadTooltips();
  initializeMessageTooltips();
}
