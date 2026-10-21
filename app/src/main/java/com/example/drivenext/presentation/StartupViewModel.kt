// ЛР1–2. Состояние загрузки и вводных экранов; переход к авторизации или каталогу с учётом сессии.
package com.example.drivenext.presentation

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.drivenext.domain.Destination
import com.example.drivenext.domain.StartupInteractor

enum class Screen { LOADING, ONBOARDING, NO_CONNECTION, AUTH, HOME }

class StartupViewModel(private val interactor: StartupInteractor, private val saved: SavedStateHandle) : ViewModel() {
    private val handler = Handler(Looper.getMainLooper())
    private val mutableScreen = MutableLiveData(Screen.LOADING)
    val screen: LiveData<Screen> = mutableScreen
    val page: LiveData<Int> = saved.getLiveData("page", 0)
    val error = MutableLiveData(false)
    private var ready = false
    private var destination = interactor.destination()
    private val refresh = Runnable { checkConnection() }
    private val stopObserving = interactor.observeNetwork { handler.post(refresh) }
    private val finishLoading = Runnable { ready = true; checkConnection() }

    init { handler.postDelayed(finishLoading, SPLASH_DURATION_MS) }

    fun checkConnection() {
        if (!ready) return
        destination = interactor.destination()
        mutableScreen.value = if (!interactor.hasInternet()) Screen.NO_CONNECTION else when (destination) {
            Destination.ONBOARDING -> Screen.ONBOARDING
            Destination.AUTH -> Screen.AUTH
            Destination.HOME -> Screen.HOME
        }
    }
    fun selectPage(value: Int) { saved["page"] = value.coerceIn(0, 2) }
    fun next() {
        if (mutableScreen.value != Screen.ONBOARDING) return
        if ((page.value ?: 0) < 2) selectPage((page.value ?: 0) + 1) else finishOnboarding()
    }
    fun finishOnboarding() {
        if (mutableScreen.value != Screen.ONBOARDING) return
        if (!interactor.completeOnboarding()) { error.value = true; return }
        destination = interactor.destination()
        checkConnection()
    }
    fun back(): Boolean {
        if (mutableScreen.value == Screen.ONBOARDING && (page.value ?: 0) > 0) {
            selectPage((page.value ?: 0) - 1)
            return true
        }
        return false
    }
    fun acknowledgeError() { error.value = false }
    override fun onCleared() {
        stopObserving()
        handler.removeCallbacksAndMessages(null)
    }
    companion object { const val SPLASH_DURATION_MS = 2500L }
}
