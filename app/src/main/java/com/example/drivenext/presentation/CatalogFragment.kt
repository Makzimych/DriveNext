// ЛР3–5. Каталог, поиск, настройки и профиль (ЛР3); подключение экранов аренды и избранного (ЛР4), подключение автомобиля (ЛР5).
package com.example.drivenext.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.drivenext.R
import com.example.drivenext.data.*
import com.example.drivenext.databinding.FragmentCatalogBinding
import com.example.drivenext.domain.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class CatalogFragment : Fragment() {
    private var bound: FragmentCatalogBinding?=null
    private lateinit var model: CatalogViewModel
    private var syncingSearch=false
    private val picker=registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bytes=withContext(Dispatchers.IO) {
                    val source=requireContext().contentResolver.openInputStream(uri)?.use { it.readBytesLimited() } ?: error("image")
                    val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeByteArray(source,0,source.size,opts)
                    check(opts.outWidth>0 && opts.outHeight>0)
                    opts.inJustDecodeBounds=false;opts.inSampleSize=1
                    while(maxOf(opts.outWidth,opts.outHeight)/opts.inSampleSize>1600) opts.inSampleSize*=2
                    val bitmap=checkNotNull(BitmapFactory.decodeByteArray(source,0,source.size,opts))
                    try { ByteArrayOutputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)); it.toByteArray() } }
                    finally { bitmap.recycle() }
                }
                model.uploadAvatar(bytes)
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { model.profileError.value="Не удалось открыть фотографию. Попробуйте другую." }
        }
    }
    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) {val n=read(buffer);if(n<0) break;check(out.size()+n<=20*1024*1024);out.write(buffer,0,n)}
        return out.toByteArray()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs=requireContext().getSharedPreferences("catalog",Context.MODE_PRIVATE)
        val initial=Bundle().apply { putString("catalog_query",prefs.getString("query", "")); putString("catalog_page",if(prefs.getString("query", "").isNullOrBlank()) CatalogPage.HOME.name else CatalogPage.SEARCH.name) }
        model=ViewModelProvider(this,object : AbstractSavedStateViewModelFactory(this,initial) {
            override fun <T: ViewModel> create(key:String,modelClass:Class<T>,state:SavedStateHandle):T {
                @Suppress("UNCHECKED_CAST")
                return CatalogViewModel(SupabaseCatalogRepository(AuthDependencies.repository(requireContext())),state) as T
            }
        })[CatalogViewModel::class.java]
    }
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        bound=FragmentCatalogBinding.inflate(inflater,container,false);return checkNotNull(bound).root
    }
    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        val b=checkNotNull(bound)
        // ЛР3: список автомобилей и поиск.
        val adapter=CarAdapter({model.show(it,false)},{model.show(it,true)})
        b.carsList.layoutManager=LinearLayoutManager(requireContext());b.carsList.adapter=adapter
        b.catalogSearch.setQuery(model.query.value,false)
        b.catalogSearch.setOnQueryTextListener(object:SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query:String?):Boolean {if(!syncingSearch) model.search(query.orEmpty());b.catalogSearch.clearFocus();return true}
            override fun onQueryTextChange(text:String?):Boolean {if(!syncingSearch) {requireContext().getSharedPreferences("catalog",Context.MODE_PRIVATE).edit().putString("query",text.orEmpty()).apply();model.search(text.orEmpty())};return true}
        })
        b.catalogNavigation.setOnItemSelectedListener { item ->
            val target=when(item.itemId) {R.id.tabHome->CatalogPage.HOME;R.id.tabBookings->CatalogPage.FAVORITES;else->CatalogPage.SETTINGS}
            if(model.page.value!=target.name) {
                if(target==CatalogPage.HOME) {requireContext().getSharedPreferences("catalog",Context.MODE_PRIVATE).edit().putString("query", "").apply();model.search("")}
                else model.navigate(target)
            }
            true
        }
        b.catalogBack.setOnClickListener {back()}
        b.catalogRetry.setOnClickListener {model.retry()}
        b.profileRetry.setOnClickListener {model.refreshProfile()}
        // ЛР3: настройки, профиль и смена фотографии.
        b.profileCard.setOnClickListener {model.navigate(CatalogPage.PROFILE)}
        b.settingsBookings.setOnClickListener {model.navigate(CatalogPage.BOOKINGS)}
        b.settingsAddCar.setOnClickListener {model.navigate(CatalogPage.ADD_CAR)}
        b.profileAvatar.setOnClickListener {pickAvatar()};b.changeAvatar.setOnClickListener {pickAvatar()}
        b.profileLogout.setOnClickListener { (parentFragment as? AuthFragment)?.logoutFromCatalog() }
        listOf(b.settingsTheme,b.settingsNotifications,b.settingsHelp,b.settingsInvite).forEach { button -> button.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(button.text).setMessage("Раздел пока недоступен.").setPositiveButton(R.string.ok,null).show()
        } }
        model.cars.observe(viewLifecycleOwner) {adapter.submit(it);renderStatus()}
        model.loading.observe(viewLifecycleOwner) {renderStatus()}
        model.error.observe(viewLifecycleOwner) {renderStatus()}
        model.profileBusy.observe(viewLifecycleOwner) {b.profileProgress.isVisible=it;b.changeAvatar.isEnabled=!it;b.profileAvatar.isEnabled=!it}
        model.profileError.observe(viewLifecycleOwner) {b.profileError.text=it;b.profileError.isVisible=it!=null;b.profileRetry.isVisible=it!=null}
        model.profile.observe(viewLifecycleOwner) {p ->
            b.settingsName.text=p.name;b.settingsEmail.text=p.email;b.profileName.text=p.name;b.profileEmail.text=p.email
            if(p.avatar==null) {b.settingsAvatar.setImageResource(R.drawable.auth_avatar);b.profileAvatar.setImageResource(R.drawable.auth_avatar)}
            else {val bitmap=BitmapFactory.decodeByteArray(p.avatar,0,p.avatar.size);b.settingsAvatar.setImageBitmap(bitmap);b.profileAvatar.setImageBitmap(bitmap)}
        }
        model.page.observe(viewLifecycleOwner) {value ->
            val page=CatalogPage.valueOf(value)
            val list=page==CatalogPage.HOME || page==CatalogPage.SEARCH
            b.catalogList.isVisible=list;b.settingsPanel.isVisible=page==CatalogPage.SETTINGS;b.profilePanel.isVisible=page==CatalogPage.PROFILE
            // ЛР4: подключение дочерних экранов бронирования и избранного.
            val rental=page in listOf(CatalogPage.FAVORITES,CatalogPage.BOOKINGS,CatalogPage.DETAILS,CatalogPage.RENT)
            b.rentalContainer.isVisible=rental
            // ЛР5: отдельный мастер подключения своего автомобиля.
            val landlord=page==CatalogPage.ADD_CAR
            b.landlordContainer.isVisible=landlord
            b.destinationPanel.isVisible=false
            b.catalogTitle.isVisible=!rental && !landlord
            b.catalogNavigation.isVisible=!landlord
            if(!childFragmentManager.isStateSaved) {
                val ownerFlow=childFragmentManager.findFragmentById(R.id.landlordContainer)
                if(landlord && ownerFlow==null) childFragmentManager.beginTransaction().replace(R.id.landlordContainer,LandlordFragment(),"landlord").commitNow()
                else if(!landlord && ownerFlow!=null) childFragmentManager.beginTransaction().remove(ownerFlow).commitNow()
                val old=childFragmentManager.findFragmentById(R.id.rentalContainer)
                if(rental) {
                    val target=RentalPage.valueOf(page.name)
                    val tag="rental_${page.name}_${model.selected?.id}"
                    if(old?.tag!=tag) childFragmentManager.beginTransaction().replace(R.id.rentalContainer,RentalFragment.create(target,model.selected),tag).commitNow()
                } else if(old!=null) childFragmentManager.beginTransaction().remove(old).commitNow()
            }
            b.catalogBack.isVisible=!rental && !landlord && page !in listOf(CatalogPage.HOME,CatalogPage.SETTINGS)
            b.catalogTitle.text=when(page) {CatalogPage.FAVORITES->"Избранное";CatalogPage.HOME->"Главная";CatalogPage.SEARCH->"Результаты поиска";CatalogPage.SETTINGS->"Настройки";CatalogPage.PROFILE->"Профиль";CatalogPage.BOOKINGS->"Мои бронирования";CatalogPage.ADD_CAR->"Добавление автомобиля";CatalogPage.DETAILS->"Детали";CatalogPage.RENT->"Оформление аренды"}
            val car=model.selected
            b.destinationText.text=when(page) {CatalogPage.DETAILS->car?.let {"${it.brand} ${it.model}\n${it.description}\n${it.price} ₽ / сутки"}.orEmpty();CatalogPage.RENT->car?.let {"${it.brand} ${it.model}\n${it.price} ₽ / сутки\n"}.orEmpty();else->"Раздел в разработке."}
            val tab=when(page) {CatalogPage.FAVORITES->R.id.tabBookings;CatalogPage.SETTINGS,CatalogPage.PROFILE,CatalogPage.ADD_CAR->R.id.tabSettings;else->R.id.tabHome}
            b.catalogNavigation.menu.findItem(tab).isChecked=true
            if(page==CatalogPage.HOME && b.catalogSearch.query.isNotEmpty()) {syncingSearch=true;b.catalogSearch.setQuery("",false);syncingSearch=false}
            if(page==CatalogPage.SEARCH && b.catalogSearch.query.toString()!=model.query.value) {syncingSearch=true;b.catalogSearch.setQuery(model.query.value,false);syncingSearch=false}
            renderStatus()
        }
    }
    private fun pickAvatar() {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
    private fun renderStatus() {val b=bound?:return;val loading=model.loading.value==true;val error=model.error.value
        b.catalogProgress.isVisible=loading;b.catalogError.text=error;b.catalogError.isVisible=error!=null;b.catalogRetry.isVisible=error!=null && !loading
        b.carsList.isVisible=!loading && error==null;b.catalogEmpty.isVisible=!loading && error==null && model.cars.value.isNullOrEmpty()
    }
    fun rentalNavigation(visible: Boolean) {bound?.catalogNavigation?.isVisible=visible}
    fun rentalHome() {
        requireContext().getSharedPreferences("catalog",Context.MODE_PRIVATE).edit().putString("query", "").apply()
        model.search("")
    }
    fun closeRental() {model.back()}
    fun back(): Boolean {
        val landlord=childFragmentManager.findFragmentById(R.id.landlordContainer) as? LandlordFragment
        if(landlord!=null && landlord.back()) return true
        val child=childFragmentManager.findFragmentById(R.id.rentalContainer) as? RentalFragment
        if(child!=null && child.back()) return true
        return if(::model.isInitialized) model.back() else false
    }
    override fun onDestroyView(){bound=null;super.onDestroyView()}
}
