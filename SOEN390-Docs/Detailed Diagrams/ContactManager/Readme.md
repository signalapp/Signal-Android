# ContactManager Component
This diagram explains the structure of Contact Manager component and the way the subcomponents work together to provide the interface that is used in the main application
### Diagram Summary:
- ''ContactsDatabase'' is the class that is actually responsible for querying the contact android addressbook by using ContentResolver android API and content URI. However, you need CursorLoader that initializes a ContactsDatabase instance

- ''ContactAccessor'' is another way to access the database, it was introduced in TextSecure API and has be kept they way it is now

- ''ContactInformationSystem'' is represesnted in a number of classes that facilitate the handling of logic operations on contact data



### Sample Code Use:

- [Using contactAccessor to access the contact address book](https://github.com/signalapp/Signal-Android/blob/17dd681dc82935e1e588029bff44cdaa1e9aeea0/src/org/thoughtcrime/securesms/preferences/AdvancedPreferenceFragment.java#L108)
 ````java
String contactName = ContactAccessor.getInstance().getNameFromContact(getActivity(), contactUri);
````
