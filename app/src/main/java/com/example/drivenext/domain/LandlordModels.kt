// ЛР5. Шаги подключения автомобиля, проверка полей и контракт сохранения заявки.
package com.example.drivenext.domain

import kotlinx.serialization.Serializable
import java.time.Year

enum class LandlordPage { INTRO, ADDRESS, INFORMATION, PHOTOS, SUCCESS }

@Serializable
data class LandlordDraft(
    val address: String = "", val year: String = "", val brand: String = "",
    val model: String = "", val transmission: String = "", val mileage: String = "",
    val description: String = ""
) {
    fun validAddress() = address.trim().isNotEmpty() && address.trim().length <= 500
    fun validInformation(currentYear: Int = Year.now().value): Boolean =
        year.toIntOrNull()?.let { it in 1886..currentYear+1 } == true &&
        brand.trim().isNotEmpty() && brand.trim().length <= 100 &&
        model.trim().isNotEmpty() && model.trim().length <= 100 &&
        transmission in transmissions &&
        mileage.toLongOrNull()?.let { it in 0..Int.MAX_VALUE.toLong() } == true &&
        description.trim().isNotEmpty() && description.trim().length <= 4000
    fun normalized() = copy(address=address.trim(), year=year.trim(), brand=brand.trim(),
        model=model.trim(), mileage=mileage.trim(), description=description.trim())
    companion object {
        val transmissions = listOf("Механическая", "Автоматическая", "Роботизированная", "Вариатор")
        const val MAX_PHOTOS = 5
    }
}

interface LandlordRepository {
    suspend fun saveDetails(id: String, draft: LandlordDraft)
    suspend fun complete(id: String, draft: LandlordDraft, photos: List<String>)
}
