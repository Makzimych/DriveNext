package com.example.drivenext

import com.example.drivenext.domain.*
import org.junit.Assert.*
import org.junit.Test

class StartupInteractorTest {
    private class Repository : StartupRepository {
        var completed = false
        var connected = true
        var writeSucceeds = true
        override fun hasInternet() = connected
        override fun isOnboardingCompleted() = completed
        override fun completeOnboarding(): Boolean { if (writeSucceeds) completed = true; return writeSucceeds }
        override fun observeNetwork(onChanged: () -> Unit): () -> Unit = {}
    }
    private class Session(var valid: Boolean = false) : SessionRepository { override fun hasValidSession() = valid }
    @Test fun firstLaunchShowsOnboarding() { assertEquals(Destination.ONBOARDING, StartupInteractor(Repository(), Session()).destination()) }
    @Test fun completedOnboardingWithoutSessionShowsAuth() {
        val repository = Repository(); val interactor = StartupInteractor(repository, Session())
        assertTrue(interactor.completeOnboarding()); assertEquals(Destination.AUTH, interactor.destination())
    }
    @Test fun validSessionAfterOnboardingShowsHome() {
        val repository = Repository().apply { completed = true }
        assertEquals(Destination.HOME, StartupInteractor(repository, Session(true)).destination())
    }
    @Test fun failedSaveDoesNotSkipOnboarding() {
        val interactor = StartupInteractor(Repository().apply { writeSucceeds = false }, Session())
        assertFalse(interactor.completeOnboarding()); assertEquals(Destination.ONBOARDING, interactor.destination())
    }
    @Test fun noConnectionDoesNotEraseProgress() {
        val repository = Repository().apply { completed = true; connected = false }
        val interactor = StartupInteractor(repository, Session())
        assertFalse(interactor.hasInternet()); repository.connected = true
        assertTrue(interactor.hasInternet()); assertEquals(Destination.AUTH, interactor.destination())
    }
}
