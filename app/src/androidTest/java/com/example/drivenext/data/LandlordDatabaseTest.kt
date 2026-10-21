// ЛР5. Проверка сохранения заявки и фотографий после повторного открытия базы и изоляции аккаунтов.
package com.example.drivenext.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.drivenext.domain.LandlordDraft
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class LandlordDatabaseTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="lr5-test-${UUID.randomUUID()}.db"
    private val root=File(context.cacheDir,"lr5-test-${UUID.randomUUID()}")
    private lateinit var db:LandlordDatabase
    private val draft=LandlordDraft("Москва, Тестовая, 1","2022","Kia","Rio","Автоматическая","1000","Описание")
    @Before fun before() {db=Room.databaseBuilder(context,LandlordDatabase::class.java,name).build()}
    @After fun after() {db.close();context.deleteDatabase(name);root.deleteRecursively()}
    @Test fun completedSubmissionSurvivesReopenAndIsPrivate()=runBlocking {
        val folder=File(root,"owner/one").apply {mkdirs()}
        val photo=File(folder,"photo.jpg").apply {writeBytes(byteArrayOf(1,2,3))}
        val repo=RoomLandlordRepository(db,"owner",root)
        repo.saveDetails("one",draft);repo.saveDetails("one",draft)
        assertFalse(db.submissions().find("owner","one")!!.approved)
        repo.complete("one",draft,listOf(photo.absolutePath))
        db.close();db=Room.databaseBuilder(context,LandlordDatabase::class.java,name).build()
        val saved=db.submissions().find("owner","one")!!
        assertTrue(saved.approved);assertEquals(draft,Json.decodeFromString<LandlordDraft>(saved.draftJson))
        assertEquals(listOf(photo.absolutePath),Json.decodeFromString<List<String>>(saved.photosJson))
        assertNull(db.submissions().find("another-account","one"))
    }
    @Test fun missingAndForeignPhotosCannotApproveSubmission()=runBlocking {
        val repo=RoomLandlordRepository(db,"owner",root);repo.saveDetails("one",draft)
        val foreign=File(root,"other/one/photo.jpg").apply {parentFile!!.mkdirs();writeBytes(byteArrayOf(1))}
        for(photos in listOf(emptyList(),listOf(File(root,"owner/one/missing.jpg").absolutePath),listOf(foreign.absolutePath))) {
            var rejected=false
            try {repo.complete("one",draft,photos)} catch(_:IllegalArgumentException) {rejected=true}
            assertTrue(rejected);assertFalse(db.submissions().find("owner","one")!!.approved)
        }
    }
}
