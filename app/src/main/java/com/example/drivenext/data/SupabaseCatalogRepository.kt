// ЛР3–4. Серверный каталог, поиск и аватар (ЛР3); адрес, тарифы аренды и данные водителя (ЛР4).
package com.example.drivenext.data

import com.example.drivenext.domain.*
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class SupabaseCatalogRepository(private val auth: SupabaseAuthRepository) : CatalogRepository {
    private val client get() = auth.client
    override suspend fun cars(query: String): List<Car> {
        val text = query.trim().replace(Regex("[^\\p{L}\\p{N} -]"), "").take(100)
        return client.from("cars").select(columns = Columns.list("id", "brand", "model", "price", "transmission", "fuel", "seats", "year", "description", "address", "insurancePerDay:insurance_per_day", "deposit")) {
            if (text.isNotBlank()) filter { ilike("search_text", "%$text%") }
        }.decodeList<Car>().sortedWith(compareBy({ it.brand }, { it.model }))
    }
    override suspend fun profile(): UserProfile {
        val user = checkNotNull(client.auth.currentUserOrNull())
        val rows = client.from("profiles").select { filter { eq("id", user.id) } }.decodeList<JsonObject>()
        val row = rows.firstOrNull()
        fun JsonObject?.text(key: String) = this?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = listOf(row.text("name"), row.text("surname"), row.text("patronymic")).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { user.userMetadata.text("name").ifBlank { user.userMetadata.text("full_name").ifBlank { "Пользователь" } } }
        val photo = try { client.storage.from("identity-documents").downloadAuthenticated("${user.id}/avatar.jpg") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
        return UserProfile(name, user.email.orEmpty(), photo, row.text("license_number"))
    }
    override suspend fun avatar(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val uid = checkNotNull(client.auth.currentUserOrNull()?.id)
        val path = "$uid/avatar.jpg"
        client.storage.from("identity-documents").upload(path, bytes, upsert = true)
        client.from("profiles").update(buildJsonObject { put("avatar", path) }) { filter { eq("id", uid) } }
        Unit
    }
}
