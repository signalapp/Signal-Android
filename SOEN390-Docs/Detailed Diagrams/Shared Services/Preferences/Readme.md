# Preferences System

This diagram explains the structure of the Preferences System and the way the subcomponents work together to provide the interface that is used in the main application

### Diagram Summary:

- ''Preferences Fragments'' Each type of Preference is represented as a fragment that is loaded dynamically through "ApplicationPreferencesActivity". Each one of those activities is in fact adapts "TextSecurePrefernce" instance that itself access Android API to set the specified key:values for every type  of preference. NO Database is brought in this system


### Sample Code Use:

- [Loading Preference fragment dynamically ](https://github.com/signalapp/Signal-Android/blob/344af622b7f562bbf4350b7a7757b5f9b271222d/src/org/thoughtcrime/securesms/ApplicationPreferencesActivity.java#L247)

 ````java
 case PREFERENCE_CATEGORY_SMS_MMS:
          fragment = new SmsMmsPreferenceFragment();
          break;
    
````
