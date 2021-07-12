package com.batsignal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BatAppsContactsManager {

        private static BatAppsContactsManager sInstance = null;
        private Set<String> mNumbers;

        // private constructor restricted to this class itself
        private BatAppsContactsManager() {

            mNumbers = Collections.synchronizedSet(new HashSet<>());
            updateAnonymousContacts();
        }

        // static method to create instance of Singleton class
        public static BatAppsContactsManager getInstance()
        {
            if (sInstance == null)
                sInstance = new BatAppsContactsManager();

            return sInstance;
        }

        public void updateAnonymousContacts() {

            synchronized (mNumbers) {

                mNumbers.clear();
                SharedPreferences preferences =
                        ApplicationDependencies.getApplication().getSharedPreferences(Constants.PREFERENCES, Context.MODE_PRIVATE);
                Set<String> set = preferences.getStringSet(Constants.EXTRA_CONTACTS_LIST, null);
                if (set != null)
                    mNumbers.addAll(set);
            }
        }

        public void updateAnonymousContacts(Set<String> contacts) {

            synchronized (mNumbers) {

                mNumbers.clear();
                if (contacts != null)
                    mNumbers.addAll(contacts);
            }
        }

        public boolean isAnonymousContact(String e164) {

            synchronized (mNumbers) {

                return mNumbers.contains(e164);
            }
        }

        public Set<String> getAnonymousContacts() {

            return mNumbers;
        }

        public static boolean shouldHideContacts(Context context) {

            // Create the Intent for the "Activate Now" action. This intent is configured to cross from
            // the primary profile to the managed, so if it is resolvable the second profile is active.
            Intent crossProfileIntent = new Intent(Constants.ACTION_ACTIVATE_APP);
            crossProfileIntent.addCategory(Intent.CATEGORY_DEFAULT);
            crossProfileIntent.setType("text/plain");

            PackageManager packageManager = context.getPackageManager();
            boolean hide = crossProfileIntent.resolveActivity(packageManager) == null;
            return hide;
        }

        public String appendIgnoreClause(String where) {

            Context appContext = ApplicationDependencies.getApplication();
            if(BatAppsContactsManager.shouldHideContacts(appContext)) {
                Set<String> numbersToIgnore = BatAppsContactsManager.getInstance().getAnonymousContacts();
                StringBuilder ignoreWhere = new StringBuilder(where);
                ArrayList<Long> recipientIds  = new ArrayList<>();
                for (String eachNumberToIgnore : numbersToIgnore) {
                    RecipientId eachRecipientToIgnore = RecipientId.from(null, eachNumberToIgnore);
                    if(eachRecipientToIgnore != null) {
                        recipientIds.add(eachRecipientToIgnore.toLong());
                    }
                }
                String recipientIdsString = recipientIds.toString();
                ignoreWhere.append(String.format(" AND %s NOT IN (%s)", ThreadDatabase.RECIPIENT_ID, recipientIdsString.substring(1,recipientIdsString.length()-1)));
                where = ignoreWhere.toString();
            }

            return where;
        }
}
