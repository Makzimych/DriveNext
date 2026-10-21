// ЛР4. Бронирование, расчёт стоимости аренды и контракт локального хранилища.
package com.example.drivenext.domain

import java.time.Duration
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Booking(
    val id: Long = 0, val car: Car, val start: String, val end: String,
    val address: String, val driver: String, val license: String,
    val insurancePerDay: Long, val deposit: Long, val cancelled: Boolean = false
) {
    val cost get() = RentalCost.calculate(car.price.toLong(), insurancePerDay, deposit,
        LocalDateTime.parse(start), LocalDateTime.parse(end))
    fun active(now: LocalDateTime) = !cancelled && LocalDateTime.parse(end).isAfter(now)
}
data class RentalCost(val days: Long, val rent: Long, val insurance: Long, val deposit: Long) {
    val total get() = Math.addExact(rent, insurance)
    companion object {
        fun calculate(price: Long, insurance: Long, deposit: Long, start: LocalDateTime, end: LocalDateTime): RentalCost {
            require(price > 0 && insurance >= 0 && deposit >= 0 && end.isAfter(start))
            val duration = Duration.between(start, end)
            val days = duration.seconds / 86400 + if(duration.seconds % 86400 != 0L || duration.nano != 0) 1 else 0
            return RentalCost(days, Math.multiplyExact(price, days), Math.multiplyExact(insurance, days), deposit)
        }
    }
}
interface RentalRepository {
    suspend fun favorites(): List<Car>
    suspend fun isFavorite(id: String): Boolean
    suspend fun toggleFavorite(car: Car): Boolean
    suspend fun bookings(): List<Booking>
    suspend fun book(booking: Booking): Booking
    suspend fun cancel(id: Long)
}
enum class RentalPage { DETAILS, RENT, SUCCESS, BOOKINGS, BOOKING, FAVORITES }
