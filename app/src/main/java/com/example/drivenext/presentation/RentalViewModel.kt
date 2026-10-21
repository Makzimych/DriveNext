// ЛР4. Даты аренды, бронирование, избранное, ошибки и история переходов.
package com.example.drivenext.presentation

import androidx.lifecycle.*
import com.example.drivenext.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

@Serializable
data class RentalRoute(val page: RentalPage, val car: Car? = null, val booking: Booking? = null)

class RentalViewModel(
    private val catalog: CatalogRepository, private val repository: RentalRepository,
    private val saved: SavedStateHandle, private val now: () -> LocalDateTime = { LocalDateTime.now() }
) : ViewModel() {
    private var history = saved.get<String>("history")?.let { Json.decodeFromString<List<RentalRoute>>(it) }.orEmpty()
    val route = MutableLiveData(saved.get<String>("route")?.let { Json.decodeFromString<RentalRoute>(it) })
    val busy = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)
    val favorite = MutableLiveData(false)
    val favorites = MutableLiveData<List<Car>>(emptyList())
    val bookings = MutableLiveData<List<Booking>>(emptyList())
    val start = saved.getLiveData("start", now().toLocalDate().plusDays(1).atTime(8, 0).toString())
    val end = saved.getLiveData("end", now().toLocalDate().plusDays(2).atTime(8, 0).toString())
    val completedFlow get() = saved.get<Boolean>("completed") == true
    private var profile: UserProfile? = null
    private var request: Job? = null
    fun open(page: RentalPage, car: Car? = null) {
        saved["completed"]=false; history = emptyList(); set(RentalRoute(page, car)); refresh()
    }
    private fun set(next: RentalRoute) {
        saved["route"] = Json.encodeToString(next); saved["history"] = Json.encodeToString(history)
        route.value = next
    }
    private fun push(next: RentalRoute) { route.value?.let { history = history + it }; set(next); refresh() }
    fun details(car: Car) = push(RentalRoute(RentalPage.DETAILS, car))
    fun rent(car: Car) = push(RentalRoute(RentalPage.RENT, car))
    fun booking(value: Booking) = push(RentalRoute(RentalPage.BOOKING, booking = value))
    fun setDate(isStart: Boolean, value: LocalDateTime) {
        if (isStart) { start.value=value.toString(); saved["start"]=value.toString() }
        else { end.value=value.toString(); saved["end"]=value.toString() }
    }
    fun quote(): RentalCost? = runCatching {
        val car = checkNotNull(route.value?.car)
        val from = LocalDateTime.parse(start.value); val to = LocalDateTime.parse(end.value)
        require(from.isAfter(now()))
        RentalCost.calculate(car.price.toLong(), car.insurancePerDay, car.deposit, from, to)
    }.getOrNull()
    fun refresh() {
        if(busy.value==true) return
        when(route.value?.page) {
            RentalPage.DETAILS -> work("Не удалось загрузить данные. Попробуйте снова.") {
                val id = checkNotNull(route.value?.car).id
                val fresh = catalog.cars("").first { it.id == id }
                favorite.value = repository.isFavorite(id)
                set(checkNotNull(route.value).copy(car = fresh))
            }
            RentalPage.RENT -> work("Не удалось загрузить данные. Попробуйте снова.") { profile = catalog.profile() }
            RentalPage.FAVORITES -> work("Не удалось загрузить данные. Попробуйте снова.") { favorites.value = repository.favorites() }
            RentalPage.BOOKINGS -> work("Не удалось загрузить данные. Попробуйте снова.") { bookings.value = repository.bookings() }
            RentalPage.BOOKING -> work("Не удалось загрузить данные. Попробуйте снова.") {
                val id=route.value?.booking?.id
                set(checkNotNull(route.value).copy(booking=repository.bookings().first { it.id==id }))
            }
            else -> Unit
        }
    }
    fun toggleFavorite() {
        if (busy.value==true) return
        work("Не удалось добавить в избранное. Попробуйте снова.") {
            favorite.value=repository.toggleFavorite(checkNotNull(route.value?.car))
        }
    }
    fun confirm() {
        if (busy.value==true || quote()==null) return
        val car=checkNotNull(route.value?.car)
        val from=checkNotNull(start.value); val to=checkNotNull(end.value)
        work("Не удалось создать бронирование. Попробуйте снова.") {
            val driver=profile ?: catalog.profile()
            val value=repository.book(Booking(car=car,start=from,end=to,address=car.address,
                driver=driver.name,license=driver.license,insurancePerDay=car.insurancePerDay,deposit=car.deposit))
            saved["completed"]=true; history=emptyList(); set(RentalRoute(RentalPage.SUCCESS,booking=value))
        }
    }
    fun cancel() {
        if (busy.value==true) return
        val id=checkNotNull(route.value?.booking).id
        work("Не удалось отменить бронирование. Попробуйте снова.") {
            repository.cancel(id); bookings.value=repository.bookings()
            // Убираем экран отменённого бронирования из истории переходов.
            history=emptyList()
            set(RentalRoute(RentalPage.BOOKINGS))
        }
    }
    fun showBookings() { history=emptyList(); set(RentalRoute(RentalPage.BOOKINGS)); refresh() }
    fun back(): Boolean {
        if (busy.value==true) return true
        if (route.value?.page==RentalPage.SUCCESS) return false
        if (history.isEmpty()) return false
        val previous=history.last(); history=history.dropLast(1);set(previous);refresh();return true
    }
    private fun work(message: String, action: suspend () -> Unit) {
        request?.cancel()
        request=viewModelScope.launch {
            busy.value=true;error.value=null
            try { withTimeout(30_000) { action() } }
            catch(e: CancellationException) { if(e is TimeoutCancellationException) error.value=message else throw e }
            catch(_: Exception) { error.value=message }
            finally { if(isActive) busy.value=false }
        }
    }
}
