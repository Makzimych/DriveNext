// ЛР1–2. Выбор стартового экрана; в ЛР2 учитывается сохранённая сессия пользователя.
package com.example.drivenext.domain

enum class Destination { ONBOARDING, AUTH, HOME }

interface StartupRepository {
    fun hasInternet(): Boolean
    fun isOnboardingCompleted(): Boolean
    fun completeOnboarding(): Boolean
    fun observeNetwork(onChanged: () -> Unit): () -> Unit
}

interface SessionRepository { fun hasValidSession(): Boolean }

class StartupInteractor(private val repository: StartupRepository, private val sessions: SessionRepository) {
    fun hasInternet() = repository.hasInternet()
    fun destination(): Destination = when {
        !repository.isOnboardingCompleted() -> Destination.ONBOARDING
        sessions.hasValidSession() -> Destination.HOME
        else -> Destination.AUTH
    }
    fun completeOnboarding() = repository.completeOnboarding()
    fun observeNetwork(onChanged: () -> Unit) = repository.observeNetwork(onChanged)
}
