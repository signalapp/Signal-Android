package org.thoughtcrime.securesms.export;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MentionUtil;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.ThreadBodyUtil;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Quote;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.media.DecryptableUriMediaInput;
import org.thoughtcrime.securesms.media.MediaInput;
import org.thoughtcrime.securesms.mms.LocationSlide;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


public class ChatFormatter {

    public static final  String KEY         = "ChatExporter";
    private static final String SCHEMA_PATH = "export_chat_xml_schema.xsd";

    private final        long                           threadId;
    private final        Map<AttachmentId, MediaRecord> selectedMedia;
    private final        Map<String, Uri>               otherFiles;
    private final        Context context;

    private       Document               dom;
    private final Cursor                 conversation;
    private final MmsSmsDatabase.Reader  reader;
    String timePeriod;

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");

    ChatFormatter (@NonNull Context context, long threadId, Date fromDate, Date untilDate) {
        this.context = context;
        this.threadId = threadId;
        this.selectedMedia = new HashMap<> ();
        this.otherFiles = new HashMap<> ();
        // Map of paths for media and their message record ids
        MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase (context);
        timePeriod =  dateFormatter.format (atStartOfDay (fromDate)) + " - " + dateFormatter.format (atEndOfDay (untilDate));
        int countBeforeStartDate = db.getConversationCount (threadId, atStartOfDay (fromDate).getTime ());
        int countBeforeEndDate = db.getConversationCount (threadId, atEndOfDay (untilDate).getTime ());
        this.conversation = db.getConversation (threadId, db.getMessagePositionOnOrAfterTimestamp (threadId, atEndOfDay (untilDate).getTime ()), countBeforeEndDate - countBeforeStartDate );
        conversation.moveToLast ();
        this.reader = db.readerFor (conversation);
    }

    void closeAll () {
        reader.close ();
        conversation.close ();

    }

    @SuppressLint("LogTagInlined")
    public String parseConversationToXML () {
        // instance of a DocumentBuilderFactory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance ();
        String finalstring = "";
        /*final SchemaFactory sf =
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final Schema schema = sf.newSchema(new StreamSource (
                getClass().getResourceAsStream(SCHEMA_PATH)));
        dbf.setSchema(schema);*/
        try {
            // use factory to get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder ();
            // create instance of DOM
            dom = db.newDocument ();

            // create the root element
            Element rootEle = dom.createElement ("chatExport");
            dom.appendChild (dom.createProcessingInstruction (StreamResult.PI_DISABLE_OUTPUT_ESCAPING, "")); // <=== ADD THIS LINE for reading emojis
            dom.appendChild (rootEle);
            dom.appendChild (dom.createProcessingInstruction (StreamResult.PI_ENABLE_OUTPUT_ESCAPING, "")); // <=== ADD THIS LINE
            // create data elements and place them under root
            Element chat = addElement (rootEle, "chat");
            createConversationElem (chat);
            if(reader.getCurrent () != null)
                createMessageElem (chat);

            //write the content into xml file
            TransformerFactory transformerFactory = TransformerFactory.newInstance ();
            Transformer transformer = transformerFactory.newTransformer ();
            transformer.setOutputProperty (OutputKeys.INDENT, "yes");
            transformer.setOutputProperty (OutputKeys.METHOD, "xml");
            transformer.setOutputProperty (OutputKeys.ENCODING, "UTF-16");
            transformer.setOutputProperty ("{http://xml.apache.org/xslt}indent-amount", "4");
            DOMSource source = new DOMSource (dom);

            StringWriter outWriter = new StringWriter ();
            StreamResult result = new StreamResult (outWriter);
            transformer.transform (source, result);
            StringBuffer sb = outWriter.getBuffer ();
            finalstring = sb.toString ();
            Log.blockUntilAllWritesFinished ();
            //TODO--removelog
            Log.d ("ANGELA XML_VIEWER:", finalstring);
        } catch (ParserConfigurationException | TransformerConfigurationException pce) {
            System.out.println ("UsersXML: Error trying to instantiate DocumentBuilder " + pce);
        } catch (TransformerException transformerException) {
            transformerException.printStackTrace ();
        }
        closeAll ();
        return finalstring;
    }

    private void createConversationElem (Element conv) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase (context);
        Recipient recipient = threadDatabase.getRecipientForThreadId (threadId);
        // ADD CONVERSATION INFORMATION
        if (recipient.isGroup ()) {
            Recipient groupRecipient = Recipient.resolved (recipient.getId ());
            GroupId groupId = groupRecipient.requireGroupId ();
            List<Recipient> registeredMembers = RecipientUtil.getEligibleForSending (groupRecipient.getParticipants ());


            Element group = addElement (conv, "group");
            addAttribute (group, "id", String.valueOf (groupId.getDecodedId ()[0]));
            addAttribute (group, "title", groupRecipient.getDisplayName (context));
            Element members = addElement (group, "members");
            addAttribute (members, "number_of_members", String.valueOf (registeredMembers.size ()));
            for (Recipient r : registeredMembers) {
                Element group_member = addElement (members, "contact");
                createPersonElem (group_member, r);
            }
        } else {
            Element contact = addElement (conv, "contact");
            createPersonElem (contact, recipient);
        }
    }

    private void createPersonElem (Element contact, Recipient recipient) {
        addAttribute (contact, "id", recipient.getId ().toString ());

        addAttribute (contact, "name", recipient.getDisplayName (context));
        if(recipient.hasAUserSetDisplayName (context)) addElement (contact, "profile_name", recipient.getProfileName ().toString ());
        if(recipient.isSelf()){
            addElement (contact, "relation", "self");
        }
        else if(recipient.isSystemContact ()){
            addElement (contact, "relation", "system_contact");
        }
        else if(recipient.isBlocked ()){
            addElement (contact, "relation", "blocked");
        }
        if(recipient.getCombinedAboutAndEmoji ()!=null) addElement (contact, "about", recipient.getCombinedAboutAndEmoji ());
        if (recipient.getEmail ().isPresent ())
            addElement (contact, "email", recipient.getEmail ().get ());
        if (recipient.hasSmsAddress ())
            addElement (contact, "phone", PhoneNumberFormatter.prettyPrint(recipient.getSmsAddress ().get ()));
        if (recipient.getContactPhoto ()!= null && recipient.getContactPhoto ().isProfilePhoto () && recipient.getContactPhoto ().getUri (context)!=null){
            addElement (contact, "contact_photo_uri", recipient.getContactPhoto ().getUri (context).getPath ());
            otherFiles.put (recipient.getId ().toString (), recipient.getContactPhoto ().getUri (context));
        }
    }

    private void createMessageElem (Element conv) {

        MessageRecord record = reader.getCurrent ();
        Element records = addElement (conv, "chat_records");
        addAttribute (conv, "selected_time_period", timePeriod);

        Date date, old_date = null;
        String date_, old_date_ = "";
        Element date_log = null;
        do {
            if (!record.isViewOnce ()) {
                date = new Date (record.getDateSent ());
                date_ = new SimpleDateFormat ("dd MMM,yyyy", Resources.getSystem ().getConfiguration ().locale).format (date);

                if (old_date != null)
                    old_date_ = new SimpleDateFormat ("dd MMM,yyyy", Resources.getSystem ().getConfiguration ().locale).format (old_date);

                if ((old_date == null) || !(old_date_.contentEquals (date_))) {
                    old_date = new Date (date.getTime ());
                    date_log = addElement (records, "Log");
                    addAttribute (date_log, "date", date_);
                }

                try {
                    Element turn = addElement (date_log, "turn");
                    Recipient author;
                    if (record.isOutgoing ()) {
                        author = Recipient.self ();
                    } else {
                        author = record.getIndividualRecipient ();
                    }
                    addAttribute (turn, "author", author.getProfileName ().getGivenName ());


                    Element message = addElement (turn, "message");
                    addAttribute (message, MmsSmsColumns.ID, String.valueOf (record.getId ()));
                    String timestamp_ = new SimpleDateFormat ("hh:mm:ss", Resources.getSystem ().getConfiguration ().locale).format (new Date (record.getDateSent ()));

                    addAttribute (message, "time", timestamp_);
                    if(record.isOutgoing ())
                        addAttribute (message, "status", getStatusFor (record));

                    Element body = addElement (message, "body");
                    createBodyElem(body, record);
                    if (!record.getReactions ().isEmpty ()) createReactionsElem (body, record);
                    if (!record.getIdentityKeyMismatches ().isEmpty ())
                        addAttribute (message, MmsSmsColumns.MISMATCHED_IDENTITIES, IdentityKeyMismatch.class.getName ());
                    long expires_in = record.getExpiresIn ();
                    if(expires_in>0)addAttribute (message, MmsSmsColumns.EXPIRES_IN, String.valueOf (expires_in));

                } catch (Exception e) {
                    System.out.println ("ANGELA XML error by accesing message details: " + e.getMessage () + " " + e.toString ());
                    e.printStackTrace ();
                }
            }
        }
        while ((record = reader.getPrevious ()) != null);
        conversation.close ();
    }

    private void createBodyElem (Element body, MessageRecord record) {
        try {
            if (record.isMms ()) {
                List<Mention> mentions = DatabaseFactory.getMentionDatabase (context).getMentionsForMessage (record.getId ());
                if (record.getBody () != null && !mentions.isEmpty ()) {
                    MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyAndMentionsWithDisplayNames (context, record.getBody (), mentions);
                    mentions = updated.getMentions ();
                    for (Mention m : mentions) {
                        Element mention = addElement (body, "mention");
                        addAttribute (mention, "id", String.valueOf (m.getRecipientId ()));
                        addElement (mention, "name", Recipient.resolved (m.getRecipientId ()).getDisplayName (context));
                    }
                    addTextElement (body, record);
                }
                else if (record.isMms () && !((MmsMessageRecord) record).getSharedContacts ().isEmpty ()) {
                    Element shared_contact = addElement (body, "shared_contact");
                    Contact contact = ((MmsMessageRecord) record).getSharedContacts ().get (0);
                    String displayName = ContactUtil.getDisplayName (contact);
                    addElement (shared_contact, "display_name", displayName);
                    if(contact.getEmails ().size ()>0)
                        for(Contact.Email e: contact.getEmails ()){
                            Element email = addElement (shared_contact, "email");
                            addAttribute (email, "type", e.getType ().name ());
                            if(e.getLabel ()!=null) addElement (email, "label", e.getLabel ());
                            addElement (email, "address", e.getEmail ());
                        }
                    if(contact.getPhoneNumbers ().size ()>0)
                        for(Contact.Phone ph: contact.getPhoneNumbers ()) {
                            Element phone = addElement (shared_contact, "phone");
                            addAttribute (phone, "type", ph.getType ().name ());
                            if(ph.getLabel ()!=null) addElement (phone, "label", ph.getLabel ());
                            addElement (phone, "number",  PhoneNumberFormatter.prettyPrint(ph.getNumber ()));
                        }
                    if(contact.getPostalAddresses ().size ()>0)
                        for(Contact.PostalAddress pa: contact.getPostalAddresses ()) {
                            Element post = addElement (shared_contact, "postal_address");
                            addAttribute (post, "type", pa.getType ().name ());
                            if(pa.getLabel ()!=null) addElement (post, "label", pa.getLabel ());
                            addElement (post, "street", pa.getStreet ());
                            addElement (post, "postal_Code", pa.getPostalCode ());
                            addElement (post, "neighborhood", pa.getNeighborhood ());
                            addElement (post, "po_box", pa.getPoBox ());
                            addElement (post, "city", pa.getCity ());
                            addElement (post, "country", pa.getCountry ());

                        }

                }
                else if (record.isMms () && !((MmsMessageRecord) record).getLinkPreviews ().isEmpty ()) {
                    for(LinkPreview lp: ((MmsMessageRecord) record).getLinkPreviews ()) {
                        Element link = addElement (body, "link");
                        addAttribute (link, "title", lp.getTitle ());
                        addElement (link, "url", escapeXML (lp.getUrl ()));
                        addElement (link, "description", lp.getDescription ());
                        if(lp.getAttachmentId ().isValid ()){
                            DatabaseAttachment a = DatabaseFactory.getAttachmentDatabase(context).getAttachment (lp.getAttachmentId ());
                            Element link_preview = addElement (link, "link_preview");
                            MediaRecord mediaRecord = new MediaRecord (a, record.getRecipient ().getId (), threadId, record.getDateSent (), record.isOutgoing ());
                                selectedMedia.put (lp.getAttachmentId (), mediaRecord);
                                if(mediaRecord.getAttachment ().getAttachmentId ()!=null)
                                    addAttribute (link_preview, "id", mediaRecord.getAttachment ().getAttachmentId ().toString ());
                                if(mediaRecord.getAttachment ().getFileName ()!=null)
                                    addElement (link_preview, "filename", mediaRecord.getAttachment ().getFileName ());
                                if(a.getUri ()!=null) {
                                    addElement (link_preview, "content_path", getContentPath (a.getContentType (),mediaRecord));
                                }
                                if(a.getContentType ()!=null){
                                    addAttribute (link_preview, "content_type", a.getContentType ());
                                }
                        }

                        if (Build.VERSION.SDK_INT >= 26) {
                            if(lp.getDate () > 0 )addElement (link, "date", dateFormatter.format(lp.getDate ()));
                        }
                    }
                }
                else if (record.isMms () && ((MediaMmsMessageRecord)record).getQuote() != null) {
                    Quote q = ((MediaMmsMessageRecord)record).getQuote();
                    Element quote = addElement (body, "quote");
                    assert q != null;
                    addAttribute (quote, "id", String.valueOf (q.getId ()));
                    addElement (quote, "author", Recipient.resolved (q.getAuthor ()).getProfileName ().getGivenName ());
                    assert q.getDisplayText () != null;
                    addElement (quote, "text",q.getDisplayText ().toString ());
                    for(Slide s: q.getAttachment ().getSlides ())
                        createAttachmentElem (quote, s);

                }
                else{
                    if(getMessageType (record).contentEquals ("OUTGOING") || getMessageType (record).contentEquals ("PUSH")) addTextElement (body, record);
                    else if(getMessageType (record).contentEquals ("CALL_LOG")) addElement (body, "type", getCallBody (record));
                    else{
                        Element text = addElement (body, "text");
                        createMessageTypeElem (text, record);
                    }
                    createMediaContentElem (body, record);
                }
            } else{
                if(getMessageType (record).contentEquals ("OUTGOING") || getMessageType (record).contentEquals ("PUSH")) addTextElement (body, record);
                else if(getMessageType (record).contentEquals ("CALL_LOG")){
                    Element text = addElement (body, "text");
                    addElement (text, "type", getCallBody (record));
                    createMessageTypeElem (text, record);
                }
                else{
                    Element text = addElement (body, "text");
                    createMessageTypeElem (text, record);
                }
            }
        }catch (Exception e) {
            e.printStackTrace ();
        }
    }

    private void addTextElement (Element body, MessageRecord record) {
        StringBuilder formattedMessageBody;
        formattedMessageBody = new StringBuilder (ThreadBodyUtil.getFormattedBodyFor (context, record));
        //Check if text is BASE_64 and decode it in case
        if (formattedMessageBody.length () % 4 == 0 && formattedMessageBody.toString ().endsWith ("=")){
            try {
                formattedMessageBody = new StringBuilder (String.valueOf (Base64.decode (escapeXML(record.getBody ()))));
            } catch (IOException e) {
                e.printStackTrace ();
            }
            addElement (body, "text", formattedMessageBody.toString ());}
        else
            if(!record.getBody ().isEmpty ())
                    addElement (body, "text", escapeXML(record.getBody ()));
    }

    private void createMediaContentElem (Element body, MessageRecord record) {
        try {
            if (((MmsMessageRecord) record).getSlideDeck ().containsMediaSlide ()) {
                Element media = addElement (body, "media_content");
                Element attachment;
                for (Slide s : ((MmsMessageRecord) record).getSlideDeck ().getSlides ()) {


                    attachment = addElement (media, "attachment");
                    createAttachmentElem (attachment, s);
                    MediaRecord mediaRecord = MediaRecord.from (context, conversation, record);

                    if (mediaRecord != null && mediaRecord.getAttachment ().hasData ()) {
                        selectedMedia.put (mediaRecord.getAttachment ().getAttachmentId (), mediaRecord);
                        if(mediaRecord.getAttachment ().getAttachmentId ()!=null)
                            addAttribute (attachment, "id", mediaRecord.getAttachment ().getAttachmentId ().toString ());
                        if(mediaRecord.getAttachment ().getFileName ()!=null)
                            addAttribute (attachment, "filename", mediaRecord.getAttachment ().getFileName ());
                    }
                    else
                        if(s.getUri ()!=null) otherFiles.put (s.getFileName ().get (),s.getUri ());
                        String content_type = s.asAttachment ().getContentType ();
                    addElement (attachment, "content_path", getContentPath(content_type,mediaRecord));

                }
            }
        } catch (Exception e) {
            e.printStackTrace ();
        }
    }

    private String getContentPath (String content_type, MediaRecord mediaRecord) {
        return ExportZipUtil.getMediaStoreContentPathForType(content_type) + ExportZipUtil.generateOutputFileName(content_type,mediaRecord.getDate ());
    }

    private void createAttachmentElem (Element attachment, Slide s) {
        try {
            if(s.asAttachment ().getFastPreflightId ()!=null)
                addAttribute (attachment, "id", s.asAttachment ().getFastPreflightId ());

            Element metadata = addElement (attachment, "metadata");
            String name;
            String size_ = String.valueOf (s.getFileSize ());
            addElement (metadata, "size_in_bytes", size_);
            if (s.hasAudio ()) {
                Element audio = addElement (metadata, "audio");
                addAttribute (audio, "content_type", s.getContentType ());
                if (s.getFileName ().isPresent ()) {
                    name = s.getFileName ().get ();
                    addElement (audio, "name", name);
                }
                if (s.asAttachment ().isVoiceNote ())
                    addAttribute (audio, "is", "voice_note");
                if(s.asAttachment ().getCaption ()!=null)
                    addElement (audio, "caption", String.valueOf (s.asAttachment ().getCaption ()));
                MediaInput dataSource;
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    dataSource = DecryptableUriMediaInput.createForUri(context, Objects.requireNonNull (s.getUri ()));
                    addElement (audio, "duration_sec", String.valueOf (TimeUnit.SECONDS.convert((dataSource.createExtractor ().getTrackFormat (0).getLong(MediaFormat.KEY_DURATION)), TimeUnit.SECONDS)));
                }
            } else if (s.hasVideo ()) {
                Element video = addElement (metadata, "video");
                addAttribute (video, "content_type", s.getContentType ());
                if (s.getFileName ().isPresent ()) {
                    name = s.getFileName ().get ();
                    addElement (video, "name", name);
                }
                addElement (video, "width", String.valueOf (s.asAttachment ().getWidth ()));
                addElement (video, "height", String.valueOf (s.asAttachment ().getHeight ()));
                if(s.asAttachment ().getCaption ()!=null)
                    addElement (video, "caption", String.valueOf (s.asAttachment ().getCaption ()));
                if (s.asAttachment ().getTransformProperties ().isVideoEdited ())
                    addElement (video, "video_edited", "true");
                if (s.asAttachment ().getTransformProperties ().isVideoTrim ())
                    addElement (video, "video_trim", "true");
                MediaInput dataSource;
                if(s.getUri () != null) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        dataSource = DecryptableUriMediaInput.createForUri(context, s.getUri ());
                        addElement (video, "duration_sec", String.valueOf (TimeUnit.SECONDS.convert((dataSource.createExtractor ().getTrackFormat (0).getLong(MediaFormat.KEY_DURATION)), TimeUnit.SECONDS)));
                    }
                    }
                } else if(s.hasLocation () && s.asAttachment ().getLocation ()!=null){
                Element location = addElement (metadata, "location");
                addElement (location, "description", String.valueOf( ((LocationSlide)s).getPlace().getDescription ()));
                addElement (location, "latitude", String.valueOf( ((LocationSlide)s).getPlace().getLatLong ().latitude));
                addElement (location, "longitude", String.valueOf( ((LocationSlide)s).getPlace().getLatLong ().longitude));
            }
            else if (s.hasImage ()) {
                Element image;
                image = addElement (metadata, "image");
                addAttribute (image, "content_type", s.getContentType ());
                if (s.getFileName ().isPresent ()) {
                    name = s.getFileName ().get ();
                    addElement (image, "name", name);
                }
                //if (s.asAttachment ().isQuote ())
                addElement (image, "width", String.valueOf (s.asAttachment ().getWidth ()));
                addElement (image, "height", String.valueOf (s.asAttachment ().getHeight ()));
                if(s.asAttachment ().getCaption ()!=null)
                    addElement (image, "caption", String.valueOf (s.asAttachment ().getCaption ()));
            } else if (s.hasDocument ()) {
                Element document = addElement (metadata, "document");
                addAttribute (document, "content_type", s.getContentType ());
                if (s.getFileName ().isPresent ()) {
                    name = s.getFileName ().get ();
                    addElement (document, "name", name);
                }
            }
            else if (s.hasSticker ()) {
                Element sticker = createStickerElem (metadata, s);
                addAttribute (sticker, "content_type", s.getContentType ());
                if (s.getFileName ().isPresent ()) {
                    name = s.getFileName ().get ();
                    addElement (sticker, "name", name);
                }
            }

            else {
                Element unknown = addElement (metadata, "unknown");
                addAttribute (unknown, "content_type", s.getContentType ());
                if (s.getFileName ().isPresent ()) {
                    name = s.getFileName ().get ();
                    addElement (unknown, "name", name);
                }
            }
            if (s.getBody ().isPresent ()) {
                addElement (attachment, "comment", escapeXML(s.getBody ().get ()));
            }
        } catch (Exception e) {
            e.printStackTrace ();
        }

    }

    private void createReactionsElem (Element body, MessageRecord record) {
        try {
            Element reactions = addElement (body, MmsSmsColumns.REACTIONS);
            for (ReactionRecord rr : record.getReactions ()) {
                addElement (reactions, "author_id", rr.getAuthor ().toString ());
                addElement (reactions, "author", Recipient.resolved (rr.getAuthor ()).getDisplayNameOrUsername (context));
                addElement (reactions, "time", dateFormatter.format (rr.getDateReceived ()));
                addElement (reactions, "emogi", rr.getEmoji ());

            }
        } catch (Exception e) {
            e.printStackTrace ();
        }
    }

    private Element createStickerElem (Element media, Slide s) {
        Element sticker = addElement (media, "sticker", s.getContentType ());
        addAttribute (sticker, "id", String.valueOf (Objects.requireNonNull (s.asAttachment ().getSticker ()).getStickerId ()));
        if (s.isBorderless ()) addElement (sticker, "is_borderless", "true");
        addElement (sticker, "emoji", Objects.requireNonNull (s.asAttachment ().getSticker ()).getEmoji ());
        return sticker;
    }

    private String getStatusFor (MessageRecord record) {
        if (record.isRemoteRead ())
            return "READ";
        if (record.isDelivered ())
            return "DELIVERED";
        if (record.isSent ())
            return "SENT";
        if (record.isPending ())
            return "PENDING";
        return "UNKNOWN";
    }

    private void createMessageTypeElem (Element message, MessageRecord record) {

        if (record.isKeyExchange ())
            addElement (message, "msg_type", "KEY_EXCHANGE");
        else if (record.isEndSession ())
            addElement (message, "msg_type", "END_SESSION");
        else if (record.isGroupUpdate ())
            addElement (message, "msg_type", "GROUP_UPDATE");
        else if (record.isSelfCreatedGroup ())
            addElement (message, "msg_type", "SELF_CREATED_GROUP");
        else if (record.isGroupV2 ())
            addElement (message, "msg_type", "GROUP_V2");
        else if (record.isGroupQuit ())
            addElement (message, "msg_type", "GROUP_QUIT");
        else if (record.isGroupAction ())
            addElement (message, "msg_type", "GROUP_ACTION");
        else if (record.isExpirationTimerUpdate ())
            addElement (message, "msg_type", "EXPIRATION_TIMER_UPDATE");
        else if (record.isJoined ())
            addElement (message, "msg_type", "MEMBER_HAS_JOINED");
        else if (record.isIncomingAudioCall ())
            addElement (message, "msg_type", "INCOMING_AUDIO_CALL");
        else if (record.isIncomingVideoCall ())
            addElement (message, "msg_type", "INCOMING_VIDEO_CALL");
        else if (record.isOutgoingAudioCall ())
            addElement (message, "msg_type", "OUTGOING_AUDIO_CALL");
        else if (record.isOutgoingVideoCall ())
            addElement (message, "msg_type", "OUTGOING_VIDEO_CALL");
        else if (record.isMissedAudioCall ())
            addElement (message, "msg_type", "IS_MISSED_AUDIO_CALL");
        else if (record.isVerificationStatusChange ())
            addElement (message, "msg_type", "VERIFICATION_STATUS_CHANGE");
        else if (record.isProfileChange ())
            addElement (message, "msg_type", "PROFILE_CHANGE");
        else if (record.isPendingInsecureSmsFallback ())
            addElement (message, "msg_type", "PENDING_INSECURE_SMS_FALLBACK");
        else if (record.isPending ())
            addElement (message, "msg_type", "PENDING");
        else if (record.isFailed ())
            addElement (message, "msg_type", "FAILED");
        else if (record.isForcedSms ())
            addElement (message, "msg_type", "FORCED_SMS");
        else if (record.isPush ())
            addElement (message, "msg_type", "PUSH");
        else if (record.isIdentityUpdate ())
            addElement (message, "msg_type", "IDENTITY_UPDATE");
        else if (record.isIdentityDefault ())
            addElement (message, "msg_type", "IDENTITY_DEFAULT");
        else if (record.isIdentityVerified ())
            addElement (message, "msg_type", "IDENTITY_VERIFIED");
        else if (record.isBundleKeyExchange ())
            addElement (message, "msg_type", "BUNDLE_KEY_EXCHANGE");
        else if (record.isContentBundleKeyExchange ())
            addElement (message, "msg_type", "CONTENT_BUNDLE_KEY_ENCHANGE");
        else if (record.isCorruptedKeyExchange ())
            addElement (message, "msg_type", "CORRUPTED_KEY_EXCHANGE");
        else if (record.isInvalidVersionKeyExchange ())
            addElement (message, "msg_type", "INVALID_KEY_EXCHANGE");
        else if (record.isGroupV1MigrationEvent ())
            addElement (message, "msg_type", "GROUP_V1_MIGRATION");
        else if (record.isFailedDecryptionType ())
            addElement (message, "msg_type", "FAILED_DESCRIPTION");
        else if (record.isOutgoing ())
            addElement (message, "msg_type", "OUTGOING");
        else if (record.isSent ())
            addElement (message, "msg_type", "SENT");
        else if (record.isCallLog ())
            addElement (message, "msg_type", "CALL_LOG");

    }

    private String getMessageType (MessageRecord record) {
        if (record.isKeyExchange ())
            return "KEY_EXCHANGE";
        else if (record.isEndSession ())
            return"END_SESSION";
        else if (record.isGroupUpdate ())
            return"GROUP_UPDATE";
        else if (record.isSelfCreatedGroup ())
            return "SELF_CREATED_GROUP";
        else if (record.isGroupV2 ())
            return "GROUP_V2";
        else if (record.isGroupQuit ())
            return "GROUP_QUIT";
        else if (record.isGroupAction ())
            return "GROUP_ACTION";
        else if (record.isExpirationTimerUpdate ())
            return "EXPIRATION_TIMER_UPDATE";
        else if (record.isJoined ())
            return "MEMBER_HAS_JOINED";
        else if (record.isIncomingAudioCall ())
            return "INCOMING_AUDIO_CALL";
        else if (record.isIncomingVideoCall ())
            return "INCOMING_VIDEO_CALL";
        else if (record.isOutgoingAudioCall ())
            return "OUTGOING_AUDIO_CALL";
        else if (record.isOutgoingVideoCall ())
            return "OUTGOING_VIDEO_CALL";
        else if (record.isMissedAudioCall ())
            return "IS_MISSED_AUDIO_CALL";
        else if (record.isVerificationStatusChange ())
            return"VERIFICATION_STATUS_CHANGE";
        else if (record.isProfileChange ())
            return "PROFILE_CHANGE";
        else if (record.isPendingInsecureSmsFallback ())
            return"PENDING_INSECURE_SMS_FALLBACK";
        else if (record.isPending ())
            return "PENDING";
        else if (record.isFailed ())
            return "FAILED";
        else if (record.isForcedSms ())
            return "FORCED_SMS";
        else if (record.isPush ())
            return "PUSH";
        else if (record.isIdentityUpdate ())
            return "IDENTITY_UPDATE";
        else if (record.isIdentityDefault ())
            return "IDENTITY_DEFAULT";
        else if (record.isIdentityVerified ())
            return "IDENTITY_VERIFIED";
        else if (record.isBundleKeyExchange ())
            return "BUNDLE_KEY_EXCHANGE";
        else if (record.isContentBundleKeyExchange ())
            return "CONTENT_BUNDLE_KEY_ENCHANGE";
        else if (record.isCorruptedKeyExchange ())
            return "CORRUPTED_KEY_EXCHANGE";
        else if (record.isInvalidVersionKeyExchange ())
            return "INVALID_KEY_EXCHANGE";
        else if (record.isGroupV1MigrationEvent ())
            return "GROUP_V1_MIGRATION";
        else if (record.isFailedDecryptionType ())
            return "FAILED_DESCRIPTION";
        else if (record.isCallLog ())
            return "CALL_LOG";
        else if (record.isOutgoing ())
            return "OUTGOING";
        else if (record.isSent ())
            return "SENT";
        return "UNKNOWN";
    }

    private Element addElement (Element parent, String tagname, String content) {
        Element elem = addElement (parent, tagname);
        elem.setTextContent (content);
        return elem;
    }

    private void addAttribute (Element parent, String tagname, String content) {
        parent.setAttribute (tagname, content);
    }

    private Element addElement (Element parent, String tagname) {
        Element elem = dom.createElement (tagname);
        parent.appendChild (elem);
        return elem;
    }


    private static String getCallBody (MessageRecord record) {
        if (record.isGroupCall ()) return "GROUP CALL";
        else if (record.isIncomingAudioCall ()) return "INCOMING AUDIO CALL";
        else if (record.isIncomingVideoCall ()) return "INCOMING VIDEO CALL";
        else if (record.isMissedAudioCall ()) return "MISSED AUDIO CALL";
        else if (record.isMissedVideoCall ()) return "MISSED VIDEO CALL";
        else if (record.isOutgoingAudioCall ()) return "OUTGOING AUDIO CALL";
        else if (record.isOutgoingVideoCall ()) return "OUTGOING VIDEO CALL";
        else return "UNKNOWN";
    }


    public Map<AttachmentId, MediaRecord> getAllMedia () {
        return selectedMedia;
    }

    public Map<String, Uri> getOtherFiles () {
        return otherFiles;
    }

   private String escapeXML(String s) {
        if (TextUtils.isEmpty(s)) return s;
        return s.replaceAll ("&", "&amp;")
                .replaceAll (">", "&gt;")
                .replaceAll ("<", "&lt;")
                .replaceAll ("\"", "&quot;")
                .replaceAll ("'", "&apos;");
    }

    public static Date atStartOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
    public static Date atEndOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    public static class MediaRecord {

        private final DatabaseAttachment attachment;
        private final RecipientId        recipientId;
        private final long               threadId;
        private final long               date;
        private final boolean            outgoing;

        private MediaRecord (@Nullable DatabaseAttachment attachment,
                             @NonNull RecipientId recipientId,
                             long threadId,
                             long date,
                             boolean outgoing) {
            this.attachment = attachment;
            this.recipientId = recipientId;
            this.threadId = threadId;
            this.date = date;
            this.outgoing = outgoing;
        }

        public static MediaRecord from (@NonNull Context context, @NonNull Cursor cursor, MessageRecord record) {
            AttachmentDatabase attachmentDatabase = DatabaseFactory.getAttachmentDatabase (context);
            List<DatabaseAttachment> attachments = attachmentDatabase.getAttachment (cursor);
            RecipientId recipientId = RecipientId.from (cursor.getLong (cursor.getColumnIndexOrThrow (MmsDatabase.RECIPIENT_ID)));
            long threadId = cursor.getLong (cursor.getColumnIndexOrThrow (MmsDatabase.THREAD_ID));
            boolean outgoing = MessageDatabase.Types.isOutgoingMessageType (cursor.getLong (cursor.getColumnIndexOrThrow (MmsDatabase.MESSAGE_BOX)));

            long date;

            if (MmsDatabase.Types.isPushType (cursor.getLong (cursor.getColumnIndexOrThrow (MmsDatabase.MESSAGE_BOX))))
                date = record.getDateSent ();
            else
                date = record.getDateReceived ();

            return new MediaRecord (attachments != null && attachments.size () > 0 ? attachments.get (0) : null,
                    recipientId,
                    threadId,
                    date,
                    outgoing);
        }

        public @Nullable
        DatabaseAttachment getAttachment () {
            return attachment;
        }

        public String getContentType () {
            assert attachment != null;
            return attachment.getContentType ();
        }

        public @NonNull
        RecipientId getRecipientId () {
            return recipientId;
        }

        public long getThreadId () {
            return threadId;
        }

        public long getDate () {
            return date;
        }

        public boolean isOutgoing () {
            return outgoing;
        }
    }


}