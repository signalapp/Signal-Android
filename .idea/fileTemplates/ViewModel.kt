#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME}

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.thoughtcrime.securesms.util.rx.RxStore

#end
#parse("File Header.java")
class ${NAME}ViewModel : ViewModel() {

  private val store = RxStore(${NAME}State())
  private val disposables = CompositeDisposable()
  
  val state: Flowable<${NAME}State> = store.stateFlowable

  override fun onCleared() {
    disposables.clear()
  }
}
