// ЛР4. Проверки бронирования, отмены, избранного и истории переходов.
package com.example.drivenext.presentation
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.drivenext.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class RentalViewModelTest {
 @get:Rule val liveData=InstantTaskExecutorRule()
 private val dispatcher=StandardTestDispatcher()
 private val now=LocalDateTime.of(2026,10,16,8,0)
 private val car=Car("one","Kia","Rio",3000,"Автомат","Бензин",5,2022)
 @Before fun before(){Dispatchers.setMain(dispatcher)}
 @After fun after(){Dispatchers.resetMain()}
 private inner class Catalog:CatalogRepository {
  override suspend fun cars(query:String)=listOf(car)
  override suspend fun profile()=UserProfile("Водитель","test@example.com",license="1234567890")
  override suspend fun avatar(bytes:ByteArray){}
 }
 private class Repo:RentalRepository {
  var fail=false;var delaySave=false;var isFavorite=false;val values=mutableListOf<Booking>()
  override suspend fun favorites()=emptyList<Car>()
  override suspend fun isFavorite(id:String)=isFavorite
  override suspend fun toggleFavorite(car:Car):Boolean {if(fail) error("disk");isFavorite=!isFavorite;return isFavorite}
  override suspend fun bookings()=values.toList()
  override suspend fun book(booking:Booking):Booking {if(delaySave) delay(1000);if(fail) error("disk");return booking.copy(id=1).also {values+=it}}
  override suspend fun cancel(id:Long) {if(fail) error("disk");val index=values.indexOfFirst {it.id==id};values[index]=values[index].copy(cancelled=true)}
 }
 private fun vm(repo:Repo,state:SavedStateHandle=SavedStateHandle())=RentalViewModel(Catalog(),repo,state) {now}
 @Test fun failedSaveNeverShowsSuccessAndCanBeRetried()=runTest(dispatcher) {
  val repo=Repo();val vm=vm(repo);vm.open(RentalPage.RENT,car);advanceUntilIdle()
  repo.fail=true;vm.confirm();advanceUntilIdle();assertEquals(RentalPage.RENT,vm.route.value!!.page);assertNotNull(vm.error.value)
  repo.fail=false;vm.confirm();advanceUntilIdle();assertEquals(RentalPage.SUCCESS,vm.route.value!!.page)
  assertEquals("1234567890",repo.values.single().license);assertFalse(vm.back());assertTrue(vm.completedFlow)
 }
 @Test fun savingDisablesDuplicateConfirmation()=runTest(dispatcher) {
  val repo=Repo();repo.delaySave=true;val vm=vm(repo);vm.open(RentalPage.RENT,car);advanceUntilIdle()
  vm.confirm();runCurrent();vm.confirm();advanceUntilIdle();assertEquals(1,repo.values.size)
 }
 @Test fun invalidDatesCannotCreateBooking()=runTest(dispatcher) {
  val repo=Repo();val vm=vm(repo);vm.open(RentalPage.RENT,car);advanceUntilIdle()
  vm.setDate(false,now);assertNull(vm.quote());vm.confirm();advanceUntilIdle();assertTrue(repo.values.isEmpty())
 }
 @Test fun detailsToRentReturnsToDetailsAndRestoresState()=runTest(dispatcher) {
  val repo=Repo();val state=SavedStateHandle();val vm=vm(repo,state);vm.open(RentalPage.DETAILS,car);advanceUntilIdle()
  vm.rent(car);advanceUntilIdle();val restored=vm(repo,state)
  assertTrue(restored.back());advanceUntilIdle();assertEquals(RentalPage.DETAILS,restored.route.value!!.page);assertEquals(car,restored.route.value!!.car)
 }
 @Test fun cancellationReturnsToListWithoutCancelledDetailInHistory()=runTest(dispatcher) {
  val repo=Repo();repo.values+=Booking(1,car,now.plusDays(1).toString(),now.plusDays(2).toString(),car.address,"Test","",300,15000)
  val vm=vm(repo);vm.open(RentalPage.BOOKINGS);advanceUntilIdle();vm.booking(repo.values.single());advanceUntilIdle()
  vm.cancel();advanceUntilIdle();assertEquals(RentalPage.BOOKINGS,vm.route.value!!.page);assertTrue(vm.bookings.value!!.single().cancelled);assertFalse(vm.back())
 }
 @Test fun favoriteFailureDoesNotChangeHeartState()=runTest(dispatcher) {
  val repo=Repo();val vm=vm(repo);vm.open(RentalPage.DETAILS,car);advanceUntilIdle()
  repo.fail=true;vm.toggleFavorite();advanceUntilIdle();assertEquals(false,vm.favorite.value);assertNotNull(vm.error.value)
  repo.fail=false;vm.toggleFavorite();advanceUntilIdle();assertEquals(true,vm.favorite.value)
 }
}
