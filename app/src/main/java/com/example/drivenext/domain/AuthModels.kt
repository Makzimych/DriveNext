package com.example.drivenext.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class AuthPage { CHOICE, LOGIN, ACCOUNT, PERSON, DOCUMENTS, SUCCESS, HOME }
data class RegistrationDraft(
    var email: String = "", var password: String = "", var repeat: String = "", var agreed: Boolean = false,
    var surname: String = "", var name: String = "", var patronymic: String = "", var birthday: String = "",
    var gender: String = "", var license: String = "", var issued: String = "",
    var avatar: String? = null, var licensePhoto: String? = null, var passportPhoto: String? = null
)
object AuthValidation {
    private val emailPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._%+\\-]*@[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?)*\\.(com|ru)$", RegexOption.IGNORE_CASE)
    fun email(value: String) = emailPattern.matches(value.trim())
    fun date(value: String, birthday: Boolean): LocalDate? = try {
        val pattern = if (birthday) "MM/dd/uuuu" else "dd/MM/uuuu"
        if (!Regex("\\d{2}/\\d{2}/\\d{4}").matches(value)) null else
            LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT))
                .takeIf { it <= LocalDate.now() && it.year >= 1900 }
    } catch (_: Exception) { null }
    fun account(d: RegistrationDraft): String? = when {
        !email(d.email) -> "email"
        d.password.length < 6 -> "password"
        d.password != d.repeat -> "match"
        !d.agreed -> "terms"
        else -> null
    }
    fun person(d: RegistrationDraft): String? = when {
        d.surname.isBlank() || d.name.isBlank() -> "name"
        date(d.birthday, true) == null -> "date"
        d.gender !in listOf("male", "female") -> "gender"
        else -> null
    }
    fun documents(d: RegistrationDraft): String? = when {
        d.license.trim().length != 10 -> "license"
        date(d.issued, false) == null -> "date"
        date(d.issued, false)!! < (date(d.birthday, true) ?: LocalDate.MIN) -> "date"
        d.licensePhoto == null || d.passportPhoto == null -> "photos"
        else -> null
    }
}
interface AuthRepository : SessionRepository {
    suspend fun initialize()
    suspend fun login(email: String, password: String)
    suspend fun register(draft: RegistrationDraft): Boolean
    suspend fun saveProfile(draft: RegistrationDraft)
    suspend fun yandex()
    suspend fun acceptCode(code: String)
    suspend fun logout()
}
