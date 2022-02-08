#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME}

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.util.livedata.Store
import io.reactivex.rxjava3.disposables.CompositeDisposable

#end
#parse("File Header.java")
class ${NAME}ViewModel : ViewModel() {

  private val store = Store(${NAME}State())
  private val disposables = CompositeDisposable()
  
  val state: LiveData<${NAME}State> = store.stateLiveData

  override fun onCleared() {
    disposables.clear()
  }
}
