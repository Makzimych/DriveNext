// ЛР5. Проверка формы, ошибок сохранения, восстановления шагов и ограничения фотографий.
package com.example.drivenext.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.drivenext.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LandlordViewModelTest {
    @get:Rule val liveData=InstantTaskExecutorRule()
    private val dispatcher=StandardTestDispatcher()
    @Before fun before() {Dispatchers.setMain(dispatcher)}
    @After fun after() {Dispatchers.resetMain()}
    private val valid=LandlordDraft("Москва, улица Тестовая, 1","2022","Kia","Rio","Автоматическая","45000","Чистый салон")
    private class Repository:LandlordRepository {
        var fail=false;var writes=0;var completed=0;var savedId=""
        override suspend fun saveDetails(id:String,draft:LandlordDraft) {
            delay(100);if(fail) error("Ошибка диска");writes++;savedId=id
        }
        override suspend fun complete(id:String,draft:LandlordDraft,photos:List<String>) {
            delay(100);if(fail) error("Ошибка диска");completed++;assertEquals(savedId,id)
        }
    }
    private fun information(vm:LandlordViewModel) {vm.next();vm.update(valid);vm.next()}
    @Test fun emptyAndInvalidFieldsDisableNext() {
        val vm=LandlordViewModel(Repository(),SavedStateHandle());vm.next()
        assertFalse(vm.canContinue());vm.update(valid.copy(address="   "));assertFalse(vm.canContinue())
        vm.update(valid);vm.next();assertTrue(vm.canContinue())
        listOf(valid.copy(year="1800"),valid.copy(mileage="-1"),valid.copy(mileage="99999999999999999"),
            valid.copy(transmission=""),valid.copy(brand=" "),valid.copy(description=" "))
            .forEach {vm.update(it);assertFalse(vm.canContinue())}
    }
    @Test fun failureDoesNotAdvanceAndRetryUsesSameDraft()=runTest(dispatcher) {
        val repo=Repository();val vm=LandlordViewModel(repo,SavedStateHandle());information(vm)
        val id=vm.id;repo.fail=true;vm.next();advanceUntilIdle()
        assertEquals(LandlordPage.INFORMATION.name,vm.page.value);assertNotNull(vm.error.value)
        repo.fail=false;vm.next();vm.next();advanceUntilIdle()
        assertEquals(LandlordPage.PHOTOS.name,vm.page.value);assertEquals(1,repo.writes);assertEquals(id,repo.savedId)
    }
    @Test fun importedPhotosAreLimitedAndCancelledPickerAddsNothing()=runTest(dispatcher) {
        val vm=LandlordViewModel(Repository(),SavedStateHandle());information(vm);vm.next();advanceUntilIdle()
        assertFalse(vm.canContinue())
        vm.importPhotos(emptyList(),{it},{ });advanceUntilIdle();assertTrue(vm.photos.value!!.isEmpty())
        vm.importPhotos(listOf("1","2","3","4"),{it},{ });advanceUntilIdle()
        vm.importPhotos(listOf("5","6","7"),{it},{ });advanceUntilIdle()
        assertEquals(listOf("1","2","3","4","5"),vm.photos.value);assertTrue(vm.canContinue())
    }
    @Test fun invalidImageDoesNotDiscardOtherSelectedPhotos()=runTest(dispatcher) {
        val vm=LandlordViewModel(Repository(),SavedStateHandle());information(vm);vm.next();advanceUntilIdle()
        vm.importPhotos(listOf("bad","good"),{if(it=="bad") error("Файл повреждён") else it},{ })
        advanceUntilIdle();assertEquals(listOf("good"),vm.photos.value);assertNotNull(vm.error.value)
    }
    @Test fun completionFailureStaysOnPhotosAndSuccessClearsBackPath()=runTest(dispatcher) {
        val repo=Repository();val vm=LandlordViewModel(repo,SavedStateHandle());information(vm);vm.next();advanceUntilIdle()
        vm.importPhotos(listOf("photo"),{it},{ });advanceUntilIdle()
        repo.fail=true;vm.next();advanceUntilIdle();assertEquals(LandlordPage.PHOTOS.name,vm.page.value)
        repo.fail=false;vm.next();vm.next();advanceUntilIdle()
        assertEquals(1,repo.completed);assertEquals(LandlordPage.SUCCESS.name,vm.page.value);assertFalse(vm.back())
    }
    @Test fun recreationKeepsFieldsPhotosCameraDestinationAndStep()=runTest(dispatcher) {
        val state=SavedStateHandle();val repo=Repository();val vm=LandlordViewModel(repo,state)
        information(vm);vm.next();advanceUntilIdle()
        vm.importPhotos(listOf("photo"),{it},{ });advanceUntilIdle();vm.pendingCamera="camera.jpg"
        val copy=LandlordViewModel(repo,SavedStateHandle(state.keys().associateWith {state.get<Any?>(it)}))
        assertEquals(vm.id,copy.id);assertEquals(valid,copy.draft.value);assertEquals(listOf("photo"),copy.photos.value)
        assertEquals("camera.jpg",copy.pendingCamera);assertTrue(copy.back());assertEquals(LandlordPage.INFORMATION.name,copy.page.value)
        assertTrue(copy.back());assertEquals(LandlordPage.ADDRESS.name,copy.page.value)
        assertTrue(copy.back());assertEquals(LandlordPage.INTRO.name,copy.page.value);assertFalse(copy.back())
    }
}
