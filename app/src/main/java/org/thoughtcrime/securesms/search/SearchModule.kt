package org.thoughtcrime.securesms.search

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.android.scopes.ViewModelScoped
import org.session.libsession.utilities.concurrent.SignalExecutors
import org.thoughtcrime.securesms.contacts.ContactAccessor
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.SearchDatabase
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase

@Module
@InstallIn(ViewModelComponent::class)
object SearchModule {

    @Provides
    @ViewModelScoped
    fun provideSearchRepository(@ApplicationContext context: Context,
                                searchDatabase: SearchDatabase,
                                threadDatabase: ThreadDatabase,
                                groupDatabase: GroupDatabase,
                                contactDatabase: SessionContactDatabase) =
            SearchRepository(context, searchDatabase, threadDatabase, groupDatabase, contactDatabase, ContactAccessor.getInstance(), SignalExecutors.SERIAL)


}