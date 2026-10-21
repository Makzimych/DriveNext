// ЛР3–4. Автомобили, профиль и контракт каталога (ЛР3); условия аренды и номер ВУ (ЛР4).
package com.example.drivenext.domain

import kotlinx.serialization.Serializable

@Serializable
data class Car(
    val id: String, val brand: String, val model: String,
    val price: Int, val transmission: String, val fuel: String,
    val seats: Int, val year: Int, val description: String = "",
    val address: String = "Москва, Авиамоторная ул., 8, стр. 2",
    val insurancePerDay: Long = 300, val deposit: Long = 15000
)
data class UserProfile(val name: String, val email: String, val avatar: ByteArray? = null, val license: String = "")
interface CatalogRepository {
    suspend fun cars(query: String): List<Car>
    suspend fun profile(): UserProfile
    suspend fun avatar(bytes: ByteArray)
}
enum class CatalogPage { HOME, SEARCH, FAVORITES, SETTINGS, PROFILE, BOOKINGS, ADD_CAR, DETAILS, RENT }
