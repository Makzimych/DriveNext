// ЛР3. Проверки поиска, повторной загрузки и возврата из профиля.
package com.example.drivenext.presentation
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.drivenext.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
 @get:Rule val liveData=InstantTaskExecutorRule()
 private val dispatcher=StandardTestDispatcher()
 @Before fun before(){Dispatchers.setMain(dispatcher)}
 @After fun after(){Dispatchers.resetMain()}
 private class Repo:CatalogRepository {
  val queries=mutableListOf<String>();var fail=false
  override suspend fun cars(query:String):List<Car>{queries+=query;if(fail) error("offline");delay(if(query=="old") 1000 else 10);return listOf(Car(query,"Toyota",query,3000,"Автомат","Бензин",5,2023))}
  override suspend fun profile()=UserProfile("Test","test@mail.ru")
  override suspend fun avatar(bytes:ByteArray){}
 }
 @Test fun onlyLatestSearchReplacesResults()=runTest(dispatcher){
  val repo=Repo();val vm=CatalogViewModel(repo,SavedStateHandle());advanceUntilIdle()
  vm.search("old");advanceTimeBy(400);runCurrent();vm.search("new");advanceUntilIdle()
  assertEquals("new",vm.cars.value!!.single().id);assertEquals(false,vm.loading.value)
 }
 @Test fun failedRequestCanBeRetried()=runTest(dispatcher){
  val repo=Repo();repo.fail=true;val vm=CatalogViewModel(repo,SavedStateHandle());advanceUntilIdle()
  assertNotNull(vm.error.value);assertEquals(false,vm.loading.value)
  repo.fail=false;vm.retry();advanceUntilIdle();assertNull(vm.error.value);assertEquals(1,vm.cars.value!!.size)
 }
 @Test fun restoredSearchLoadsFreshServerResults()=runTest(dispatcher){
  val repo=Repo();val vm=CatalogViewModel(repo,SavedStateHandle(mapOf("catalog_query" to "Camry","catalog_page" to "SEARCH")));advanceUntilIdle()
  assertEquals(listOf("Camry"),repo.queries);assertEquals("Camry",vm.cars.value!!.single().id)
 }
 @Test fun profileBackReturnsToSettings()=runTest(dispatcher){
  val vm=CatalogViewModel(Repo(),SavedStateHandle());advanceUntilIdle();vm.navigate(CatalogPage.PROFILE);advanceUntilIdle()
  assertTrue(vm.back());assertEquals(CatalogPage.SETTINGS.name,vm.page.value)
 }
}
