// ЛР4. Экраны деталей автомобиля, аренды, успеха, бронирований и избранного.
package com.example.drivenext.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.drivenext.R
import com.example.drivenext.data.*
import com.example.drivenext.databinding.FragmentRentalBinding
import com.example.drivenext.databinding.ItemBookingBinding
import com.example.drivenext.domain.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.gotrue.auth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class RentalFragment : Fragment() {
    private var bound: FragmentRentalBinding? = null
    private lateinit var model: RentalViewModel
    private lateinit var cars: CarAdapter
    private lateinit var bookingAdapter: BookingAdapter
    private val dateFormat=DateTimeFormatter.ofPattern("HH:mm, dd MMMM yyyy", Locale("ru"))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth=AuthDependencies.repository(requireContext())
        val owner=checkNotNull(auth.client.auth.currentUserOrNull()).id
        model=ViewModelProvider(this,object : AbstractSavedStateViewModelFactory(this,arguments) {
            override fun <T: ViewModel> create(key: String, cls: Class<T>, state: SavedStateHandle): T {
                @Suppress("UNCHECKED_CAST")
                return RentalViewModel(SupabaseCatalogRepository(auth),RoomRentalRepository(RentalDatabase.get(requireContext()),owner),state) as T
            }
        })[RentalViewModel::class.java]
        if(model.route.value==null) {
            val car=arguments?.getString("car")?.let { Json.decodeFromString<Car>(it) }
            model.open(RentalPage.valueOf(requireArguments().getString("page")!!),car)
        } else model.refresh()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bound=FragmentRentalBinding.inflate(inflater,container,false);return checkNotNull(bound).root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b=checkNotNull(bound)
        cars=CarAdapter({model.details(it)},{model.rent(it)})
        bookingAdapter=BookingAdapter { model.booking(it) }
        b.rentalList.layoutManager=LinearLayoutManager(requireContext())
        b.rentalBack.setOnClickListener { if(!back()) (parentFragment as? CatalogFragment)?.closeRental() }
        b.favoriteButton.setOnClickListener {model.toggleFavorite()}
        b.rentalRetry.setOnClickListener {model.refresh()}
        b.detailsReserve.setOnClickListener {model.route.value?.car?.let {model.rent(it)}}
        b.rentalStart.setOnClickListener {pickDate(true)};b.rentalEnd.setOnClickListener {pickDate(false)}
        b.rentContinue.setOnClickListener {model.confirm()}
        b.successBookings.setOnClickListener {model.showBookings()}
        b.successHome.setOnClickListener {(parentFragment as? CatalogFragment)?.rentalHome()}
        b.bookingCancel.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.lr4_cancel_question)
                .setNegativeButton(R.string.lr4_no,null).setPositiveButton(R.string.lr4_yes_cancel) {_,_->model.cancel()}.show()
        }
        model.route.observe(viewLifecycleOwner) {render()}
        model.start.observe(viewLifecycleOwner) {renderQuote()};model.end.observe(viewLifecycleOwner) {renderQuote()}
        model.busy.observe(viewLifecycleOwner) {busy ->
            b.rentalProgress.isVisible=busy;b.favoriteButton.isEnabled=!busy;b.detailsReserve.isEnabled=!busy
            b.bookingCancel.isEnabled=!busy;b.rentalBack.isEnabled=!busy;renderQuote()
        }
        model.error.observe(viewLifecycleOwner) {b.rentalError.text=it;b.rentalError.isVisible=it!=null;b.rentalRetry.isVisible=it!=null}
        model.favorite.observe(viewLifecycleOwner) {yes ->
            b.favoriteButton.setIconResource(if(yes) R.drawable.rental_heart_filled else R.drawable.rental_heart_outline)
            b.favoriteButton.iconTint=ColorStateList.valueOf(if(yes) Color.rgb(194,24,62) else Color.BLACK)
            b.favoriteButton.contentDescription=getString(if(yes) R.string.lr4_favorite_remove else R.string.lr4_favorite_add)
        }
        model.favorites.observe(viewLifecycleOwner) {cars.submit(it);renderEmpty()}
        model.bookings.observe(viewLifecycleOwner) {bookingAdapter.submit(it);renderEmpty()}
    }
    private fun render() {
        val b=bound?:return;val route=model.route.value?:return;val page=route.page
        b.detailsPanel.isVisible=page==RentalPage.DETAILS;b.rentPanel.isVisible=page==RentalPage.RENT
        b.successPanel.isVisible=page==RentalPage.SUCCESS;b.bookingPanel.isVisible=page==RentalPage.BOOKING
        val list=page==RentalPage.FAVORITES || page==RentalPage.BOOKINGS
        b.rentalScroll.isVisible=!list;b.rentalList.isVisible=list
        b.rentalBack.isVisible=page !in listOf(RentalPage.SUCCESS,RentalPage.FAVORITES)
        b.favoriteButton.isVisible=page==RentalPage.DETAILS
        b.rentalTitle.text=when(page) {
            RentalPage.DETAILS->getString(R.string.lr4_details);RentalPage.RENT->getString(R.string.lr4_rent)
            RentalPage.SUCCESS->getString(R.string.lr4_success);RentalPage.BOOKINGS->getString(R.string.lr4_bookings)
            RentalPage.FAVORITES->getString(R.string.lr4_favorites);RentalPage.BOOKING->getString(R.string.lr4_booking,route.booking?.id?:0)
        }
        (parentFragment as? CatalogFragment)?.rentalNavigation(page in listOf(RentalPage.DETAILS,RentalPage.FAVORITES))
        route.car?.let {car ->
            val title=getString(R.string.lr4_car_title,car.brand,car.model,car.year)
            b.detailsCarName.text=title;b.detailsSpecs.text=getString(R.string.lr4_car_specs,car.transmission,car.fuel,car.seats)
            b.detailsPrice.text=getString(R.string.lr4_day_price,car.price);b.detailsAddress.text=car.address;b.detailsDescription.text=car.description
            b.rentCarName.text=title;b.rentDayPrice.text=getString(R.string.lr4_day_price,car.price);b.rentAddress.text=car.address
        }
        route.booking?.let {value ->
            b.bookingCarName.text=getString(R.string.lr4_car_title,value.car.brand,value.car.model,value.car.year)
            b.bookingDayPrice.text=getString(R.string.lr4_day_price,value.car.price);b.bookingAddress.text=value.address
            b.bookingStart.text=getString(R.string.lr4_start,format(value.start));b.bookingEnd.text=getString(R.string.lr4_end,format(value.end))
            b.bookingDriver.text=getString(R.string.lr4_driver,value.driver.ifBlank {getString(R.string.lr4_not_provided)})
            b.bookingLicense.text=getString(R.string.lr4_license,value.license.ifBlank {getString(R.string.lr4_not_provided)})
            b.bookingStatus.text=getString(R.string.lr4_status,getString(if(value.cancelled) R.string.lr4_cancelled else if(value.active(LocalDateTime.now())) R.string.lr4_approved else R.string.lr4_completed))
            val cost=value.cost
            b.bookingCost.text=getString(R.string.lr4_cost_rent,cost.days,cost.rent)
            b.bookingInsurance.text=getString(R.string.lr4_cost_insurance,cost.days,cost.insurance)
            b.bookingTotal.text=getString(R.string.lr4_cost_total,cost.total)
            b.bookingCancel.isVisible=value.active(LocalDateTime.now())
        }
        if(list) b.rentalList.adapter=if(page==RentalPage.FAVORITES) cars else bookingAdapter
        renderQuote();renderEmpty()
    }
    private fun renderQuote() {
        val b=bound?:return
        b.rentalStart.text=getString(R.string.lr4_start,format(model.start.value!!));b.rentalEnd.text=getString(R.string.lr4_end,format(model.end.value!!))
        val cost=model.quote();b.rentContinue.isEnabled=cost!=null && model.busy.value!=true
        b.rentalDatesError.isVisible=cost==null
        b.rentCost.text=cost?.let {getString(R.string.lr4_cost_rent,it.days,it.rent)}.orEmpty()
        b.rentInsurance.text=cost?.let {getString(R.string.lr4_cost_insurance,it.days,it.insurance)}.orEmpty()
        b.rentTotal.text=cost?.let {getString(R.string.lr4_cost_total,it.total)}.orEmpty()
        b.rentDeposit.text=cost?.let {getString(R.string.lr4_cost_deposit,it.deposit)}.orEmpty()
    }
    private fun renderEmpty() {
        val b=bound?:return;val page=model.route.value?.page
        b.rentalEmpty.isVisible=(page==RentalPage.FAVORITES && model.favorites.value.isNullOrEmpty()) || (page==RentalPage.BOOKINGS && model.bookings.value.isNullOrEmpty())
        b.rentalEmpty.setText(if(page==RentalPage.FAVORITES) R.string.lr4_empty_favorites else R.string.lr4_empty_bookings)
    }
    private fun format(value: String)=LocalDateTime.parse(value).format(dateFormat)
    private fun pickDate(start: Boolean) {
        val current=LocalDateTime.parse(if(start) model.start.value else model.end.value)
        val dialog=DatePickerDialog(requireContext(),{_,year,month,day ->
            TimePickerDialog(requireContext(),{_,hour,minute -> model.setDate(start,LocalDateTime.of(year,month+1,day,hour,minute)) },current.hour,current.minute,true).show()
        },current.year,current.monthValue-1,current.dayOfMonth)
        dialog.datePicker.minDate=java.time.LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();dialog.show()
    }
    fun back(): Boolean {
        if(model.back()) return true
        if(model.route.value?.page==RentalPage.SUCCESS || model.completedFlow) {(parentFragment as? CatalogFragment)?.rentalHome();return true}
        return false
    }
    override fun onDestroyView() {bound=null;super.onDestroyView()}
    companion object {
        fun create(page: RentalPage,car: Car?)=RentalFragment().apply {arguments=Bundle().apply {putString("page",page.name);car?.let {putString("car",Json.encodeToString(it))}}}
    }
    inner class BookingAdapter(private val select: (Booking)->Unit) : RecyclerView.Adapter<BookingAdapter.Holder>() {
        private var items=emptyList<Booking>()
        fun submit(values: List<Booking>) {items=values;notifyDataSetChanged()}
        inner class Holder(val binding: ItemBookingBinding):RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup,type: Int)=Holder(ItemBookingBinding.inflate(LayoutInflater.from(parent.context),parent,false))
        override fun getItemCount()=items.size
        override fun onBindViewHolder(holder: Holder,position: Int) {
            val item=items[position];val active=item.active(LocalDateTime.now())
            holder.binding.bookingRowName.text="${item.car.brand} ${item.car.model}"
            holder.binding.bookingRowDate.text=getString(if(item.cancelled) R.string.lr4_list_cancelled else if(active) R.string.lr4_list_start else R.string.lr4_list_end,format(if(active) item.start else item.end))
            holder.binding.root.setOnClickListener {if(active) select(item)}
        }
    }
}
