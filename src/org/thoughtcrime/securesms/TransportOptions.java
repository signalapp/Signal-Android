package org.thoughtcrime.securesms;

import android.Manifest;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.CharacterCalculator;
import org.thoughtcrime.securesms.util.MmsCharacterCalculator;
import org.thoughtcrime.securesms.util.PushCharacterCalculator;
import org.thoughtcrime.securesms.util.SmsCharacterCalculator;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionInfoCompat;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionManagerCompat;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.thoughtcrime.securesms.TransportOption.Type;

public class TransportOptions {

  private static final String TAG = TransportOptions.class.getSimpleName();

  private final List<OnTransportChangedListener> listeners = new LinkedList<>();
  private final Context                          context;
  private final List<TransportOption>            enabledTransports;

  private Type                      defaultTransportType  = Type.SMS;
  private Optional<Integer>         defaultSubscriptionId = Optional.absent();
  private Optional<TransportOption> selectedOption        = Optional.absent();

  private final Optional<Integer> systemSubscriptionId;

  public TransportOptions(Context context, boolean media) {
    this.context              = context;
    this.enabledTransports    = initializeAvailableTransports(media);
    this.systemSubscriptionId = new SubscriptionManagerCompat(context).getPreferredSubscriptionId();
  }

  public void reset(boolean media) {
    List<TransportOption> transportOptions = initializeAvailableTransports(media);

    this.enabledTransports.clear();
    this.enabledTransports.addAll(transportOptions);

    if (selectedOption.isPresent() && !isEnabled(selectedOption.get())) {
      setSelectedTransport(null);
    } else {
      this.defaultTransportType = Type.SMS;
      this.defaultSubscriptionId = Optional.absent();

      notifyTransportChangeListeners();
    }
  }

  public void setDefaultTransport(Type type) {
    this.defaultTransportType = type;

    if (!selectedOption.isPresent()) {
      notifyTransportChangeListeners();
    }
  }

  public void setDefaultSubscriptionId(Optional<Integer> subscriptionId) {
    if  (defaultSubscriptionId.equals(subscriptionId)) {
      return;
    }

    this.defaultSubscriptionId = subscriptionId;

    if (!selectedOption.isPresent()) {
      notifyTransportChangeListeners();
    }
  }

  public void setSelectedTransport(@Nullable  TransportOption transportOption) {
    this.selectedOption = Optional.fromNullable(transportOption);
    notifyTransportChangeListeners();
  }

  public boolean isManualSelection() {
    return this.selectedOption.isPresent();
  }

  public @NonNull TransportOption getSelectedTransport() {
    if (selectedOption.isPresent()) return selectedOption.get();

    if (defaultTransportType == Type.SMS) {
      TransportOption transportOption = findEnabledSmsTransportOption(defaultSubscriptionId.or(systemSubscriptionId));
      if (transportOption != null) {
        return transportOption;
      }
    }

    for (TransportOption transportOption : enabledTransports) {
      if (transportOption.getType() == defaultTransportType) {
        return transportOption;
      }
    }

    throw new AssertionError("No options of default type!");
  }

  private @Nullable TransportOption findEnabledSmsTransportOption(Optional<Integer> subscriptionId) {
    if (subscriptionId.isPresent()) {
      final int subId = subscriptionId.get();

      for (TransportOption transportOption : enabledTransports) {
        if (transportOption.getType() == Type.SMS &&
            subId == transportOption.getSimSubscriptionId().or(-1)) {
          return transportOption;
        }
      }
    }
    return null;
  }

  public void disableTransport(Type type) {
    TransportOption selected = selectedOption.orNull();

    Iterator<TransportOption> iterator = enabledTransports.iterator();
    while (iterator.hasNext()) {
      TransportOption option = iterator.next();

      if (option.isType(type)) {
        if (selected == option) {
          setSelectedTransport(null);
        }
        iterator.remove();
      }
    }
  }

  public List<TransportOption> getEnabledTransports() {
    return enabledTransports;
  }

  public void addOnTransportChangedListener(OnTransportChangedListener listener) {
    this.listeners.add(listener);
  }

  private List<TransportOption> initializeAvailableTransports(boolean isMediaMessage) {
    List<TransportOption> results = new LinkedList<>();

    if (isMediaMessage) {
      results.addAll(getTransportOptionsForSimCards(context.getString(R.string.ConversationActivity_transport_insecure_mms),
                                                    context.getString(R.string.conversation_activity__type_message_mms_insecure),
                                                    new MmsCharacterCalculator()));
    } else {
      results.addAll(getTransportOptionsForSimCards(context.getString(R.string.ConversationActivity_transport_insecure_sms),
                                                    context.getString(R.string.conversation_activity__type_message_sms_insecure),
                                                    new SmsCharacterCalculator()));
    }

    results.add(new TransportOption(Type.TEXTSECURE, R.drawable.ic_send_push_white_24dp,
                                    context.getResources().getColor(R.color.textsecure_primary),
                                    context.getString(R.string.ConversationActivity_transport_signal),
                                    context.getString(R.string.conversation_activity__type_message_push),
                                    new PushCharacterCalculator()));

    return results;
  }

  private @NonNull List<TransportOption> getTransportOptionsForSimCards(@NonNull String text,
                                                                        @NonNull String composeHint,
                                                                        @NonNull CharacterCalculator characterCalculator)
  {
    List<TransportOption>              results             = new LinkedList<>();
    SubscriptionManagerCompat          subscriptionManager = new SubscriptionManagerCompat(context);
    Collection<SubscriptionInfoCompat> subscriptions;

    if (Permissions.hasAll(context, Manifest.permission.READ_PHONE_STATE)) {
      subscriptions = subscriptionManager.getActiveAndReadySubscriptionInfos();
    } else {
      subscriptions = Collections.emptyList();
    }

    if (subscriptions.size() < 2) {
      results.add(new TransportOption(Type.SMS, R.drawable.ic_send_sms_white_24dp,
                                      context.getResources().getColor(R.color.grey_600),
                                      text, composeHint, characterCalculator));
    } else {
      for (SubscriptionInfoCompat subscriptionInfo : subscriptions) {
        results.add(new TransportOption(Type.SMS, R.drawable.ic_send_sms_white_24dp,
                                        context.getResources().getColor(R.color.grey_600),
                                        text, composeHint, characterCalculator,
                                        Optional.of(subscriptionInfo.getDisplayName()),
                                        Optional.of(subscriptionInfo.getSubscriptionId())));
      }
    }

    return results;
  }

  private void notifyTransportChangeListeners() {
    for (OnTransportChangedListener listener : listeners) {
      listener.onChange(getSelectedTransport(), selectedOption.isPresent());
    }
  }

  private boolean isEnabled(TransportOption transportOption) {
    for (TransportOption option : enabledTransports) {
      if (option.equals(transportOption)) return true;
    }

    return false;
  }

  public interface OnTransportChangedListener {
    public void onChange(TransportOption newTransport, boolean manuallySelected);
  }
}
