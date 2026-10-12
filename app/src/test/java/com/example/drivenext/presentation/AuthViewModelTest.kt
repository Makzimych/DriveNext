package com.example.drivenext.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.drivenext.domain.AuthPage
import com.example.drivenext.domain.AuthRepository
import com.example.drivenext.domain.RegistrationDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule val liveData = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Repository : AuthRepository {
        var initialized = CompletableDeferred(Unit)
        var loginGate = CompletableDeferred(Unit)
        var logins = 0; var signups = 0; var saves = 0; var codes = 0
        var failSave = false; var confirmation = false
        override fun hasValidSession() = false
        override suspend fun initialize() { initialized.await() }
        override suspend fun login(email: String, password: String) { logins++; loginGate.await() }
        override suspend fun register(draft: RegistrationDraft): Boolean { signups++; return !confirmation }
        override suspend fun saveProfile(draft: RegistrationDraft) { saves++; if (failSave) error("network") }
        override suspend fun yandex() {}
        override suspend fun acceptCode(code: String) { codes++ }
        override suspend fun logout() {}
    }
    @Test fun unverifiedOAuthEmailShowsConfirmationWithoutCreatingSession() = runTest(dispatcher) {
        val repository=Repository(); val model=AuthViewModel(repository); runCurrent()
        model.go(AuthPage.LOGIN)
        model.callback(null,"provider_email_needs_verification"); runCurrent()
        assertEquals("oauth_confirmation",model.message.value)
        assertEquals(AuthPage.LOGIN,model.page.value)
        assertEquals(0,repository.codes)
        assertEquals(false,model.busy.value)
    }
    private fun filled(model: AuthViewModel) {
        model.draft.apply { email="a@mail.ru"; password="123456"; repeat=password; agreed=true; surname="Тест"; name="Тест"; birthday="01/01/2000"; gender="male"; license="1234567890"; issued="01/01/2020"; licensePhoto="test"; passportPhoto="test" }
    }
    @Test fun backPreservesDraftAndDoesNotSkipSteps() = runTest(dispatcher) {
        val model=AuthViewModel(Repository()); runCurrent(); filled(model)
        model.go(AuthPage.ACCOUNT); model.next(); assertEquals(AuthPage.PERSON,model.page.value)
        model.next(); assertEquals(AuthPage.DOCUMENTS,model.page.value)
        assertTrue(model.back()); assertEquals(AuthPage.PERSON,model.page.value)
        assertTrue(model.back()); assertEquals("a@mail.ru",model.draft.email)
        assertEquals("123456",model.draft.password)
    }
    @Test fun repeatedLoginDoesNotCreateConcurrentRequests() = runTest(dispatcher) {
        val repository=Repository(); val model=AuthViewModel(repository); runCurrent()
        repository.loginGate=CompletableDeferred()
        model.loginEmail="a@mail.ru"; model.loginPassword="123456"
        model.login(); model.login(); runCurrent(); assertEquals(1,repository.logins)
        repository.loginGate.complete(Unit); runCurrent(); assertEquals(false,model.busy.value)
        assertEquals(AuthPage.HOME,model.page.value)
    }
    @Test fun retryProfileDoesNotRegisterAccountTwice() = runTest(dispatcher) {
        val repository=Repository(); val model=AuthViewModel(repository); runCurrent(); filled(model)
        model.go(AuthPage.DOCUMENTS); repository.failSave=true; model.next(); runCurrent()
        assertEquals(AuthPage.DOCUMENTS,model.page.value); assertEquals(false,model.busy.value)
        repository.failSave=false; model.next(); runCurrent()
        assertEquals(1,repository.signups); assertEquals(2,repository.saves)
        assertEquals(AuthPage.SUCCESS,model.page.value)
    }
    @Test fun emailConfirmationDoesNotShowFalseSuccess() = runTest(dispatcher) {
        val repository=Repository().apply { confirmation=true }; val model=AuthViewModel(repository); runCurrent(); filled(model)
        model.go(AuthPage.DOCUMENTS); model.next(); runCurrent()
        assertEquals(AuthPage.LOGIN,model.page.value); assertEquals(0,repository.saves)
        model.callback("confirmed",null); runCurrent()
        assertEquals(1,repository.saves); assertEquals(AuthPage.SUCCESS,model.page.value)
    }
    @Test fun callbackDuringInitializationIsHandledAfterInitialization() = runTest(dispatcher) {
        val repository=Repository().apply { initialized=CompletableDeferred() }
        val model=AuthViewModel(repository); model.callback("code",null); runCurrent()
        assertEquals(0,repository.codes); repository.initialized.complete(Unit); runCurrent()
        assertEquals(1,repository.codes); assertEquals(AuthPage.HOME,model.page.value)
    }
    @Test fun logoutClearsRegistrationForTheNextAccount() = runTest(dispatcher) {
        val model=AuthViewModel(Repository()); runCurrent(); filled(model)
        model.go(AuthPage.HOME); model.logout(); runCurrent()
        assertEquals(AuthPage.CHOICE,model.page.value); assertEquals("",model.draft.email)
        assertNull(model.draft.licensePhoto); assertFalse(model.draft.agreed)
    }
}
