// ЛР5. Интерфейс арендодателя, Spinner, множественный выбор фото и последовательная съёмка.
package com.example.drivenext.presentation

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.example.drivenext.R
import com.example.drivenext.data.*
import com.example.drivenext.databinding.FragmentLandlordBinding
import com.example.drivenext.domain.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.gotrue.auth
import java.io.File

class LandlordFragment: Fragment() {
    private var bound: FragmentLandlordBinding?=null
    private lateinit var model: LandlordViewModel
    private lateinit var storage: LandlordPhotos
    private var syncing=false
    private var displayedPhotos: List<String>?=null
    private val gallery=registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if(uris.isNotEmpty()) importPhotos(uris)
    }
    private val camera=registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path=model.pendingCamera
        model.pendingCamera=null
        if(path!=null) {
            val file=File(path)
            if(success && file.length()>0) {
                model.importPhotos(listOf(Uri.fromFile(file).toString()), { uri ->
                    try {storage.import(Uri.parse(uri))} finally {file.delete()}
                }, storage::remove)
                continueCamera=true
            } else file.delete()
        }
    }
    private var continueCamera=false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app=requireContext().applicationContext
        val owner=checkNotNull(AuthDependencies.repository(app).client.auth.currentUserOrNull()?.id)
        model=ViewModelProvider(this,object: AbstractSavedStateViewModelFactory(this,savedInstanceState) {
            override fun <T: ViewModel> create(key:String,modelClass:Class<T>,state:SavedStateHandle):T {
                @Suppress("UNCHECKED_CAST")
                return LandlordViewModel(RoomLandlordRepository(LandlordDatabase.get(app),owner,
                    File(app.filesDir,"landlord-photos")),state) as T
            }
        })[LandlordViewModel::class.java]
        storage=LandlordPhotos(app,owner,model.id)
    }
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        bound=FragmentLandlordBinding.inflate(inflater,container,false)
        return checkNotNull(bound).root
    }
    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        val b=checkNotNull(bound)
        syncing=true
        val draft=checkNotNull(model.draft.value)
        b.landlordAddress.setText(draft.address); b.landlordYear.setText(draft.year)
        b.landlordBrand.setText(draft.brand); b.landlordModel.setText(draft.model)
        b.landlordMileage.setText(draft.mileage); b.landlordDescription.setText(draft.description)
        val items=listOf(getString(R.string.landlord_transmission))+LandlordDraft.transmissions
        b.landlordTransmission.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_item,items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        b.landlordTransmission.setSelection((LandlordDraft.transmissions.indexOf(draft.transmission)+1).coerceAtLeast(0))
        syncing=false
        listOf(b.landlordAddress,b.landlordYear,b.landlordBrand,b.landlordModel,b.landlordMileage,b.landlordDescription)
            .forEach { it.doAfterTextChanged { if(!syncing) updateDraft() } }
        b.landlordTransmission.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {if(!syncing) updateDraft()}
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
        }
        b.landlordContinue.setOnClickListener {
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(b.root.windowToken,0)
            b.root.clearFocus()
            if(model.page.value==LandlordPage.SUCCESS.name) home() else model.next()
        }
        b.landlordBack.setOnClickListener {backFromButton()}
        b.landlordGallery.setOnClickListener {
            try {gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
            catch(_: Exception) {model.error.value=getString(R.string.landlord_gallery_unavailable)}
        }
        b.landlordCamera.setOnClickListener {takePhoto()}
        model.page.observe(viewLifecycleOwner) {render(); b.landlordScroll.scrollTo(0,0)}
        model.draft.observe(viewLifecycleOwner) {renderButtons()}
        model.photos.observe(viewLifecycleOwner) {renderPhotos();renderButtons()}
        model.error.observe(viewLifecycleOwner) {b.landlordError.text=it;b.landlordError.isVisible=it!=null}
        model.busy.observe(viewLifecycleOwner) {busy ->
            b.landlordProgress.isVisible=busy
            listOf(b.landlordAddress,b.landlordYear,b.landlordBrand,b.landlordModel,b.landlordMileage,
                b.landlordDescription,b.landlordTransmission,b.landlordBack).forEach {it.isEnabled=!busy}
            renderButtons()
            if(!busy && continueCamera) {
                continueCamera=false
                if(model.photos.value.orEmpty().size<LandlordDraft.MAX_PHOTOS && model.page.value==LandlordPage.PHOTOS.name)
                    MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.landlord_more_camera)
                        .setPositiveButton(R.string.landlord_camera) {_,_->takePhoto()}
                        .setNegativeButton(R.string.landlord_camera_finish,null).show()
            }
        }
    }
    private fun updateDraft() {
        val b=bound?:return
        model.update(LandlordDraft(b.landlordAddress.text.toString(),b.landlordYear.text.toString(),
            b.landlordBrand.text.toString(),b.landlordModel.text.toString(),
            LandlordDraft.transmissions.getOrNull(b.landlordTransmission.selectedItemPosition-1).orEmpty(),
            b.landlordMileage.text.toString(),b.landlordDescription.text.toString()))
        val draft=checkNotNull(model.draft.value)
        b.landlordYearBox.error=if(draft.year.isNotBlank() && draft.year.toIntOrNull()?.let {it in 1886..java.time.Year.now().value+1}!=true)
            getString(R.string.landlord_year_error) else null
        b.landlordMileageBox.error=if(draft.mileage.isNotBlank() && draft.mileage.toLongOrNull()?.let {it in 0..Int.MAX_VALUE.toLong()}!=true)
            getString(R.string.landlord_mileage_error) else null
    }
    private fun render() {
        val b=bound?:return
        val page=LandlordPage.valueOf(checkNotNull(model.page.value))
        b.landlordIntro.isVisible=page==LandlordPage.INTRO
        b.landlordAddressPanel.isVisible=page==LandlordPage.ADDRESS
        b.landlordInformation.isVisible=page==LandlordPage.INFORMATION
        b.landlordPhotosPanel.isVisible=page==LandlordPage.PHOTOS
        b.landlordSuccessPanel.isVisible=page==LandlordPage.SUCCESS
        b.landlordHeader.isVisible=page!=LandlordPage.SUCCESS
        b.landlordTitle.setText(when(page) {
            LandlordPage.INTRO->R.string.landlord_intro_title
            LandlordPage.PHOTOS->R.string.landlord_photos_title
            else->R.string.landlord_form_title
        })
        b.landlordContinue.setText(when(page) {
            LandlordPage.INTRO->R.string.landlord_start
            LandlordPage.INFORMATION->R.string.landlord_submit
            LandlordPage.SUCCESS->R.string.landlord_home
            else->R.string.landlord_next
        })
        renderButtons()
        (parentFragment as? CatalogFragment)?.rentalNavigation(false)
    }
    private fun renderButtons() {
        val b=bound?:return
        b.landlordContinue.isEnabled=model.canContinue()
        val canAdd=model.busy.value!=true && model.photos.value.orEmpty().size<LandlordDraft.MAX_PHOTOS
        b.landlordGallery.isEnabled=canAdd;b.landlordCamera.isEnabled=canAdd
        b.landlordPhotoCount.text=getString(R.string.landlord_photo_count,model.photos.value.orEmpty().size)
        b.landlordPhotoGrid.isEnabled=model.busy.value!=true
    }
    private fun renderPhotos() {
        val b=bound?:return; val paths=model.photos.value.orEmpty().toList()
        if(displayedPhotos==paths) return
        displayedPhotos=paths;b.landlordPhotoGrid.removeAllViews()
        fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
        paths.chunked(1).firstOrNull()?.let { addPhotoRow(b.landlordPhotoGrid,it,0,dp(200)) }
        paths.drop(1).chunked(2).forEachIndexed {index,row->addPhotoRow(b.landlordPhotoGrid,row,1+index*2,dp(125))}
    }
    private fun addPhotoRow(parent:LinearLayout,paths:List<String>,start:Int,height:Int) {
        val row=LinearLayout(requireContext()).apply {orientation=LinearLayout.HORIZONTAL}
        parent.addView(row,LinearLayout.LayoutParams(-1,height))
        paths.forEachIndexed {index,path->
            val frame=FrameLayout(requireContext())
            row.addView(frame,LinearLayout.LayoutParams(0,-1,1f).apply {setMargins(3,3,3,3)})
            val image=ImageView(requireContext()).apply {
                scaleType=ImageView.ScaleType.CENTER_CROP
                contentDescription=getString(R.string.landlord_photo_description,start+index+1)
                val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeFile(path,options)
                // Маленькие изображения не уменьшаем; большие подбираем под ширину карточки.
                val target=resources.displayMetrics.widthPixels / paths.size
                options.inSampleSize=1
                while(options.outWidth/options.inSampleSize>target*2) options.inSampleSize*=2
                options.inJustDecodeBounds=false
                setImageBitmap(BitmapFactory.decodeFile(path,options))
            }
            frame.addView(image,FrameLayout.LayoutParams(-1,-1))
            val remove=TextView(requireContext()).apply {
                text="×";textSize=28f;gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(0xAA000000.toInt());contentDescription=getString(R.string.landlord_remove_photo,start+index+1)
                setOnClickListener {model.removePhoto(path,storage::remove)}
            }
            val size=(48*resources.displayMetrics.density).toInt()
            frame.addView(remove,FrameLayout.LayoutParams(size,size,Gravity.TOP or Gravity.END))
        }
    }
    private fun importPhotos(uris:List<Uri>) {
        model.importPhotos(uris.map {it.toString()}, {storage.import(Uri.parse(it))},storage::remove)
    }
    private fun takePhoto() {
        if(model.busy.value==true || model.photos.value.orEmpty().size>=LandlordDraft.MAX_PHOTOS) return
        try {
            val dir=File(requireContext().cacheDir,"auth-camera").apply {mkdirs()}
            val file=File.createTempFile("car-", ".jpg",dir)
            model.pendingCamera=file.absolutePath
            camera.launch(FileProvider.getUriForFile(requireContext(),"${requireContext().packageName}.files",file))
        } catch(_: Exception) {
            model.pendingCamera?.let {File(it).delete()};model.pendingCamera=null
            model.error.value=getString(R.string.landlord_camera_unavailable)
        }
    }
    private fun home() {(parentFragment as? CatalogFragment)?.rentalHome()}
    private fun backFromButton() {
        if(!back()) (parentFragment as? CatalogFragment)?.closeRental()
    }
    fun back():Boolean {
        if(model.page.value==LandlordPage.SUCCESS.name) {home();return true}
        return model.back()
    }
    override fun onDestroyView() {displayedPhotos=null;bound=null;super.onDestroyView()}
}
