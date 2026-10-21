package com.example.drivenext.domain

import kotlinx.serialization.Serializable

@Serializable
data class Car(
    val id: String, val brand: String, val model: String,
    val price: Int, val transmission: String, val fuel: String,
    val seats: Int, val year: Int, val description: String = ""
)
data class UserProfile(val name: String, val email: String, val avatar: ByteArray? = null)
interface CatalogRepository {
    suspend fun cars(query: String): List<Car>
    suspend fun profile(): UserProfile
    suspend fun avatar(bytes: ByteArray)
}
enum class CatalogPage { HOME, SEARCH, SETTINGS, PROFILE, BOOKINGS, ADD_CAR, DETAILS, RENT }
