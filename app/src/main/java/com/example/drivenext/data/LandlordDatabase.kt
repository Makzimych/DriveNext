// ЛР5. Локальные заявки арендодателя. Отдельная база сохраняет совместимость с базой аренды ЛР4.
package com.example.drivenext.data

import android.content.Context
import androidx.room.*
import com.example.drivenext.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Entity(tableName="car_submissions", primaryKeys=["owner", "id"])
data class CarSubmissionEntity(
    val owner: String, val id: String, val draftJson: String,
    val photosJson: String = "[]", val approved: Boolean = false
)

@Dao
interface LandlordDao {
    @Query("SELECT * FROM car_submissions WHERE owner=:owner AND id=:id")
    suspend fun find(owner: String, id: String): CarSubmissionEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun save(value: CarSubmissionEntity)
}

@Database(entities=[CarSubmissionEntity::class], version=1, exportSchema=true)
abstract class LandlordDatabase: RoomDatabase() {
    abstract fun submissions(): LandlordDao
    companion object {
        @Volatile private var instance: LandlordDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LandlordDatabase::class.java,
                "drivenext-landlord.db").build().also { instance=it }
        }
    }
}

class RoomLandlordRepository(private val db: LandlordDatabase, private val owner: String,
    private val photoRoot: File): LandlordRepository {
    init { require(owner.isNotBlank()) }
    override suspend fun saveDetails(id: String, draft: LandlordDraft) {
        require(id.isNotBlank() && draft.validAddress() && draft.validInformation())
        db.withTransaction {
            // Повторное нажатие и повтор после ошибки используют тот же номер заявки.
            check(db.submissions().find(owner,id)?.approved != true)
            db.submissions().save(CarSubmissionEntity(owner,id,Json.encodeToString(draft.normalized())))
        }
    }
    override suspend fun complete(id: String, draft: LandlordDraft, photos: List<String>) {
        require(draft.validAddress() && draft.validInformation())
        require(photos.size in 1..LandlordDraft.MAX_PHOTOS && photos.distinct().size==photos.size)
        val directory=File(photoRoot,"$owner/$id").canonicalFile
        require(photos.all { path ->
            val file=File(path).canonicalFile
            file.parentFile==directory && file.isFile && file.length()>0
        })
        db.withTransaction {
            val old=checkNotNull(db.submissions().find(owner,id))
            db.submissions().save(old.copy(draftJson=Json.encodeToString(draft.normalized()),
                photosJson=Json.encodeToString(photos), approved=true))
        }
    }
}
