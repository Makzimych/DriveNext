// ЛР4. Сущности, запросы и единый экземпляр локальной базы Room.
package com.example.drivenext.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "favorites", primaryKeys = ["owner", "carId"])
data class FavoriteEntity(val owner: String, val carId: String, val carJson: String)
@Entity(tableName = "bookings", indices = [Index("owner")])
data class BookingEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val owner: String, val bookingJson: String)

@Dao
interface RentalDao {
    @Query("SELECT * FROM favorites WHERE owner = :owner ORDER BY carId")
    suspend fun favorites(owner: String): List<FavoriteEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE owner = :owner AND carId = :id)")
    suspend fun isFavorite(owner: String, id: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun favorite(item: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE owner = :owner AND carId = :id") suspend fun removeFavorite(owner: String, id: String)
    @Query("SELECT * FROM bookings WHERE owner = :owner ORDER BY id DESC") suspend fun bookings(owner: String): List<BookingEntity>
    @Insert suspend fun insert(item: BookingEntity): Long
    @Update suspend fun update(item: BookingEntity)
}
@Database(entities = [FavoriteEntity::class, BookingEntity::class], version = 1, exportSchema = true)
abstract class RentalDatabase : RoomDatabase() {
    abstract fun rentals(): RentalDao
    companion object {
        @Volatile private var instance: RentalDatabase? = null
        fun get(context: Context): RentalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RentalDatabase::class.java, "drivenext-rentals.db")
                .build().also { instance = it }
        }
    }
}
