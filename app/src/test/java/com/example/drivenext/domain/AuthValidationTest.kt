package com.example.drivenext.domain
import org.junit.Assert.*
import org.junit.Test
class AuthValidationTest {
    @Test fun emailAllowsOnlyRequiredDomains() {
        assertTrue(AuthValidation.email("user@mail.ru")); assertTrue(AuthValidation.email("user@example.com"))
        listOf("a@b", "a@b.org", "a b@mail.ru", "a@-b.ru", "a@b..ru").forEach { assertFalse(it, AuthValidation.email(it)) }
    }
    @Test fun datesAreStrictAndUseDifferentFormats() {
        assertNull(AuthValidation.date("02/30/2000", true)); assertNull(AuthValidation.date("31/04/2020", false))
        assertNotNull(AuthValidation.date("02/29/2000", true)); assertNull(AuthValidation.date("02/29/2001", true))
        assertNull(AuthValidation.date("01/01/2099", false)); assertNull(AuthValidation.date("1/1/2000", false))
    }
    @Test fun accountRequiresMatchingPasswordsAndConsent() {
        val d = RegistrationDraft(email="a@mail.ru", password="123456", repeat="654321")
        assertEquals("match", AuthValidation.account(d)); d.repeat=d.password
        assertEquals("terms", AuthValidation.account(d)); d.agreed=true; assertNull(AuthValidation.account(d))
    }
    @Test fun optionalPatronymicAndAvatarDoNotBlockRegistration() {
        val d=RegistrationDraft(surname="Тест",name="Тест",birthday="01/01/2000",gender="male",license="1234567890",issued="01/01/2020",licensePhoto="test",passportPhoto="test")
        assertNull(AuthValidation.person(d)); assertNull(AuthValidation.documents(d))
        d.passportPhoto=null; assertEquals("photos", AuthValidation.documents(d))
        d.passportPhoto="test"; d.issued="01/01/1999"; assertEquals("date", AuthValidation.documents(d))
    }
}
