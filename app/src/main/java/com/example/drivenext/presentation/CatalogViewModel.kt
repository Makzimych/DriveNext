package com.example.drivenext.presentation

import androidx.lifecycle.*
import com.example.drivenext.domain.*
import kotlinx.coroutines.*

class CatalogViewModel(private val repository: CatalogRepository, private val saved: SavedStateHandle) : ViewModel() {
    val page = saved.getLiveData("catalog_page", CatalogPage.HOME.name)
    val query = saved.getLiveData("catalog_query", "")
    val cars = MutableLiveData<List<Car>>(emptyList())
    val profile = MutableLiveData<UserProfile>()
    val loading = MutableLiveData(false)
    val profileBusy = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)
    val profileError = MutableLiveData<String?>(null)
    private var searchJob: Job? = null
    var selected: Car? = null
        private set
    init { load(false); refreshProfile() }
    fun navigate(target: CatalogPage) {
        page.value = target.name
        if (target == CatalogPage.HOME || target == CatalogPage.SEARCH) load(false)
        if (target == CatalogPage.SETTINGS || target == CatalogPage.PROFILE) refreshProfile()
    }
    fun search(text: String) { query.value = text; saved["catalog_query"] = text; page.value = if(text.isBlank()) CatalogPage.HOME.name else CatalogPage.SEARCH.name; load(true) }
    fun show(car: Car, rent: Boolean) { selected=car; page.value=if(rent) CatalogPage.RENT.name else CatalogPage.DETAILS.name }
    fun retry() = load(false)
    private fun load(debounce: Boolean) {
        searchJob?.cancel()
        val text = if(page.value == CatalogPage.SEARCH.name) query.value.orEmpty() else ""
        searchJob = viewModelScope.launch {
            loading.value=true; error.value=null
            try {
                if(debounce) delay(350)
                cars.value=withTimeout(30_000) { repository.cars(text) }
            } catch(e: CancellationException) {
                if(e is TimeoutCancellationException) error.value=if(text.isBlank()) "Не удалось загрузить данные. Попробуйте снова." else "Не удалось выполнить поиск. Попробуйте снова."
                else throw e
            } catch(_: Exception) { error.value=if(text.isBlank()) "Не удалось загрузить данные. Попробуйте снова." else "Не удалось выполнить поиск. Попробуйте снова." }
            finally { if(isActive) loading.value=false }
        }
    }
    fun refreshProfile() {
        if(profileBusy.value == true) return
        viewModelScope.launch {
            profileBusy.value=true; profileError.value=null
            try { profile.value=withTimeout(30_000) { repository.profile() } }
            catch(e: CancellationException) { if(e is TimeoutCancellationException) profileError.value="Не удалось загрузить профиль. Попробуйте снова." else throw e }
            catch(_: Exception) { profileError.value="Не удалось загрузить профиль. Попробуйте снова." }
            finally { profileBusy.value=false }
        }
    }
    fun uploadAvatar(bytes: ByteArray) {
        if(profileBusy.value == true) return
        viewModelScope.launch {
            profileBusy.value=true; profileError.value=null
            try { withTimeout(30_000) { repository.avatar(bytes); profile.value=repository.profile() } }
            catch(e: CancellationException) { if(e is TimeoutCancellationException) profileError.value="Не удалось сохранить фото. Попробуйте снова." else throw e }
            catch(_: Exception) { profileError.value="Не удалось сохранить фото. Попробуйте снова." }
            finally { profileBusy.value=false }
        }
    }
    fun back(): Boolean = when(page.value) {
        CatalogPage.PROFILE.name, CatalogPage.BOOKINGS.name, CatalogPage.ADD_CAR.name -> { navigate(CatalogPage.SETTINGS); true }
        CatalogPage.DETAILS.name, CatalogPage.RENT.name -> { page.value=if(query.value.isNullOrBlank()) CatalogPage.HOME.name else CatalogPage.SEARCH.name; true }
        CatalogPage.SEARCH.name -> { search(""); true }
        CatalogPage.SETTINGS.name -> { navigate(CatalogPage.HOME); true }
        else -> false
    }
}
