package pw.phylame.crawler

import android.app.Application
import io.realm.Realm
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.concurrent.Executors
import java.util.concurrent.Future

private typealias Action = () -> Unit
private var innerApp: App? = null

val app: App get() = innerApp!!

class App : Application() {
    // registered cleanups
    private val cleanups = LinkedHashSet<Action>()

    // common worker thread pool
    private val executor by lazy {
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        cleaned(pool::shutdown)
        pool
    }

    override fun onCreate() {
        super.onCreate()
        Realm.init(this)
        innerApp = this
    }

    /**
     * Disposes the application.
     */
    fun dispose() {
        for (cleanup in cleanups) {
            cleanup()
        }
    }

    /**
     * Registers clean up action executed when disposing application.
     */
    fun cleaned(action: Action) {
        cleanups += action
    }

    // execute an action in background thread and without result
    fun schedule(action: () -> Unit): Future<*> = executor.submit(action)

    // execute an action with result in background thread and notify consume with result when finished.
    fun <R> schedule(action: () -> R, consume: (R) -> Unit): Subscription = Observable.create<R> {
        it.onNext(action())
        it.onCompleted()
    }.subscribeOn(Schedulers.from(executor))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(consume)
}
