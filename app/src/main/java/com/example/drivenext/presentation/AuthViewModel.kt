package com.example.drivenext.presentation

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivenext.domain.AuthPage
import com.example.drivenext.domain.AuthRepository
import com.example.drivenext.domain.AuthValidation
import com.example.drivenext.domain.RegistrationDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    val page = MutableLiveData(AuthPage.CHOICE)
    val busy = MutableLiveData(false)
    val message = MutableLiveData<String?>(null)
    val draft = RegistrationDraft()
    var loginEmail = ""
    var loginPassword = ""
    private var accountCreated = false
    private var confirmationPending = false
    private var pendingCallback: Pair<String?, String?>? = null
    var formResetVersion = 0
        private set
    init { operation { repository.initialize(); if (repository.hasValidSession()) page.value = AuthPage.HOME } }
    fun go(target: AuthPage) { if (busy.value != true) { message.value = null; page.value = target } }
    fun back(): Boolean {
        if (busy.value == true) return true
        if (accountCreated && page.value == AuthPage.DOCUMENTS) return true
        val previous = when (page.value) {
            AuthPage.LOGIN, AuthPage.ACCOUNT -> AuthPage.CHOICE
            AuthPage.PERSON -> AuthPage.ACCOUNT
            AuthPage.DOCUMENTS -> AuthPage.PERSON
            else -> return false
        }
        go(previous); return true
    }
    fun login() {
        if (!AuthValidation.email(loginEmail)) { message.value = "email"; return }
        operation {
            repository.login(loginEmail, loginPassword)
            if (confirmationPending && loginEmail.trim().equals(draft.email.trim(), true)) {
                accountCreated = true; confirmationPending = false; repository.saveProfile(draft); page.value = AuthPage.SUCCESS
            } else page.value = AuthPage.HOME
            loginPassword = ""
        }
    }
    fun next() {
        val error = when (page.value) {
            AuthPage.ACCOUNT -> AuthValidation.account(draft)
            AuthPage.PERSON -> AuthValidation.person(draft)
            AuthPage.DOCUMENTS -> AuthValidation.documents(draft)
            else -> null
        }
        if (error != null) { message.value = error; return }
        when (page.value) {
            AuthPage.ACCOUNT -> go(AuthPage.PERSON)
            AuthPage.PERSON -> go(AuthPage.DOCUMENTS)
            AuthPage.DOCUMENTS -> operation {
                if (!accountCreated) {
                    accountCreated = repository.register(draft)
                    if (!accountCreated) {
                        confirmationPending = true; loginEmail = draft.email; draft.password = ""; draft.repeat = ""
                        page.value = AuthPage.LOGIN; message.value = "confirmation"; return@operation
                    }
                }
                repository.saveProfile(draft); draft.password = ""; draft.repeat = ""; page.value = AuthPage.SUCCESS
            }
            AuthPage.SUCCESS -> go(AuthPage.HOME)
            else -> Unit
        }
    }
    fun yandex() = operation { repository.yandex() }
    fun callback(code: String?, error: String?) {
        if (busy.value == true) { pendingCallback = Pair(code, error); return }
        if (error != null) {
            message.value = if (error == "provider_email_needs_verification") "oauth_confirmation" else "oauth_cancelled"
            return
        }
        if (code.isNullOrBlank()) return
        operation {
            repository.acceptCode(code)
            if (confirmationPending) {
                accountCreated = true
                repository.saveProfile(draft)
                confirmationPending = false
                page.value = AuthPage.SUCCESS
            } else page.value = AuthPage.HOME
        }
    }
    fun logout() = operation {
        repository.logout()
        draft.apply {
            email=""; password=""; repeat=""; agreed=false; surname=""; name=""; patronymic=""; birthday=""; gender=""; license=""; issued=""
            avatar=null; licensePhoto=null; passportPhoto=null
        }
        loginEmail=""; loginPassword=""; accountCreated=false; confirmationPending=false
        formResetVersion++
        page.value = AuthPage.CHOICE
    }
    private fun operation(block: suspend () -> Unit) {
        if (busy.value == true) return
        busy.value = true; message.value = null
        viewModelScope.launch {
            try { withTimeout(45_000) { block() } }
            catch (e: CancellationException) { if (e is kotlinx.coroutines.TimeoutCancellationException) message.value = "network" else throw e }
            catch (e: Exception) {
                val text = e.message.orEmpty()
                message.value = when {
                    "backend_not_configured" in text -> "configuration"
                    "Invalid login credentials" in text -> "credentials"
                    "Email not confirmed" in text -> "confirmation"
                    "already registered" in text -> "registered"
                    "rate limit" in text.lowercase() -> "rate_limit"
                    else -> "request_failed"
                }
            } finally {
                busy.value = false
                pendingCallback?.let { pendingCallback = null; callback(it.first, it.second) }
            }
        }
    }
}
