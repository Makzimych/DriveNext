// ЛР4. Избранное, сохранение и отмена бронирований; разделение записей по аккаунтам.
package com.example.drivenext.data

import androidx.room.withTransaction
import com.example.drivenext.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

class RoomRentalRepository(private val db: RentalDatabase, private val owner: String) : RentalRepository {
    private val dao = db.rentals()
    init { require(owner.isNotBlank()) }
    override suspend fun favorites() = dao.favorites(owner).map { Json.decodeFromString<Car>(it.carJson) }
    override suspend fun isFavorite(id: String) = dao.isFavorite(owner, id)
    override suspend fun toggleFavorite(car: Car): Boolean = db.withTransaction {
        if (dao.isFavorite(owner, car.id)) { dao.removeFavorite(owner, car.id); false }
        else { dao.favorite(FavoriteEntity(owner, car.id, Json.encodeToString(car))); true }
    }
    override suspend fun bookings() = dao.bookings(owner).map { Json.decodeFromString<Booking>(it.bookingJson).copy(id = it.id) }
    override suspend fun book(booking: Booking): Booking = db.withTransaction {
        require(LocalDateTime.parse(booking.start).isAfter(LocalDateTime.now()))
        booking.cost // Проверяем даты и стоимость перед сохранением.
        val overlap = bookings().any { !it.cancelled && it.car.id == booking.car.id &&
            LocalDateTime.parse(it.start).isBefore(LocalDateTime.parse(booking.end)) &&
            LocalDateTime.parse(booking.start).isBefore(LocalDateTime.parse(it.end)) }
        check(!overlap) { "Для этого автомобиля уже есть ваше бронирование на выбранные даты." }
        val id = dao.insert(BookingEntity(owner = owner, bookingJson = Json.encodeToString(booking)))
        booking.copy(id = id)
    }
    override suspend fun cancel(id: Long) = db.withTransaction {
        val row = dao.bookings(owner).first { it.id == id }
        val value = Json.decodeFromString<Booking>(row.bookingJson)
        check(value.active(LocalDateTime.now()))
        dao.update(row.copy(bookingJson = Json.encodeToString(value.copy(cancelled = true))))
    }
}
