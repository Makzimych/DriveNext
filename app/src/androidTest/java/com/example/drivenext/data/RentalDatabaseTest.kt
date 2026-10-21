// ЛР4. Проверки Room на устройстве: сохранение записей, разделение аккаунтов и пересечения дат.
package com.example.drivenext.data
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.drivenext.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RentalDatabaseTest {
 private lateinit var context:Context
 private lateinit var db:RentalDatabase
 private lateinit var name:String
 private val car=Car("test","Kia","Rio",3000,"Автомат","Бензин",5,2022)
 @Before fun before() {context=InstrumentationRegistry.getInstrumentation().targetContext;name="lr4-test-${UUID.randomUUID()}.db";db=open()}
 private fun open()=Room.databaseBuilder(context,RentalDatabase::class.java,name).build()
 @After fun after(){db.close();context.deleteDatabase(name)}
 private fun value():Booking {val start=LocalDateTime.now().plusDays(10).withNano(0);return Booking(car=car,start=start.toString(),end=start.plusDays(3).toString(),address=car.address,driver="Учебный водитель",license="TEST",insurancePerDay=300,deposit=15000)}
 @Test fun accountDataAreIsolated()=runBlocking {
  val first=RoomRentalRepository(db,"first");val second=RoomRentalRepository(db,"second")
  first.toggleFavorite(car);first.book(value())
  assertTrue(second.favorites().isEmpty());assertTrue(second.bookings().isEmpty())
  assertFalse(second.isFavorite(car.id));assertEquals(1,first.bookings().size)
 }
 @Test fun favoritesAndCancelledBookingSurviveReopening()=runBlocking {
  var repo=RoomRentalRepository(db,"first");repo.toggleFavorite(car);val booking=repo.book(value());repo.cancel(booking.id)
  db.close();db=open();repo=RoomRentalRepository(db,"first")
  assertTrue(repo.isFavorite(car.id));assertEquals(car,repo.favorites().single());assertTrue(repo.bookings().single().cancelled)
 }
 @Test fun overlappingReservationIsNotInsertedTwice()=runBlocking {
  val repo=RoomRentalRepository(db,"first");val booking=value();repo.book(booking)
  try {repo.book(booking);fail("Duplicate booking inserted")} catch(expected:IllegalStateException) { }
  assertEquals(1,repo.bookings().size)
 }
}
