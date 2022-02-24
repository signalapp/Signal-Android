package org.thoughtcrime.securesms.stories.settings.custom.name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import org.thoughtcrime.securesms.database.model.DistributionListId

class EditStoryNameViewModel(private val privateStoryId: DistributionListId, private val repository: EditStoryNameRepository) : ViewModel() {

  fun save(name: CharSequence): Completable {
    return repository.save(privateStoryId, name).observeOn(AndroidSchedulers.mainThread())
  }

  class Factory(private val privateStoryId: DistributionListId, private val repository: EditStoryNameRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(EditStoryNameViewModel(privateStoryId, repository)) as T
    }
  }
}
