// ЛР2–4. Формы авторизации и регистрации (ЛР2); контейнер каталога и экранов ЛР3–4.
package com.example.drivenext.presentation

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.drivenext.R
import com.example.drivenext.data.AuthDependencies
import com.example.drivenext.databinding.FragmentAuthBinding
import com.example.drivenext.domain.AuthPage
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class AuthFragment : Fragment() {
    private var bound: FragmentAuthBinding? = null
    private val binding get() = checkNotNull(bound)
    private lateinit var model: AuthViewModel
    private var photoTarget = "avatar"
    private var cameraFile: File? = null
    private var photoBusy = false
    private val gallery = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) importPhoto(uri) }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file=cameraFile
        if (taken && file != null) importPhoto(Uri.fromFile(file)) else { file?.delete(); cameraFile=null }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoTarget=savedInstanceState?.getString("photoTarget") ?: "avatar"
        cameraFile=savedInstanceState?.getString("cameraFile")?.let(::File)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(AuthDependencies.repository(requireContext())) as T
            }
        })[AuthViewModel::class.java]
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bound = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b=binding; val d=model.draft
        fun field(edit: TextInputEditText, value: String, setter: (String)->Unit) {
            edit.setText(value); edit.doAfterTextChanged { setter(it?.toString().orEmpty()); enableActions() }
        }
        field(b.login.loginEmail,model.loginEmail) { model.loginEmail=it }; field(b.login.loginPassword,model.loginPassword) { model.loginPassword=it }
        field(b.account.accountEmail,d.email) { d.email=it }; field(b.account.accountPassword,d.password) { d.password=it }; field(b.account.accountRepeat,d.repeat) { d.repeat=it }
        field(b.person.personSurname,d.surname) { d.surname=it }; field(b.person.personName,d.name) { d.name=it }; field(b.person.personPatronymic,d.patronymic) { d.patronymic=it }
        field(b.person.personBirthday,d.birthday) { d.birthday=it }; field(b.documents.documentsLicense,d.license) { d.license=it }; field(b.documents.documentsIssued,d.issued) { d.issued=it }
        b.account.accountTerms.isChecked=d.agreed
        b.account.accountTerms.setOnCheckedChangeListener { _, checked -> d.agreed=checked; enableActions() }
        if (d.gender.isNotEmpty()) b.person.personGender.check(if (d.gender=="male") R.id.personMale else R.id.personFemale)
        b.person.personGender.setOnCheckedChangeListener { _, id -> d.gender=when(id) { R.id.personMale -> "male"; R.id.personFemale -> "female"; else -> "" }; enableActions() }
        b.back.setOnClickListener { model.back() }
        b.choice.choiceLogin.setOnClickListener { model.go(AuthPage.LOGIN) }; b.choice.choiceRegister.setOnClickListener { model.go(AuthPage.ACCOUNT) }
        b.login.loginRegister.setOnClickListener { model.go(AuthPage.ACCOUNT) }; b.login.loginSubmit.setOnClickListener { model.login() }
        b.login.loginYandex.setOnClickListener { model.yandex() }
        listOf(b.account.accountNext,b.person.personNext,b.documents.documentsNext,b.success.successNext).forEach { it.setOnClickListener { model.next() } }
        b.home.homeLogout.setOnClickListener { model.logout() }
        b.account.accountPolicy.setOnClickListener { MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.auth_terms_title).setMessage(R.string.auth_terms_text).setPositiveButton(R.string.ok,null).show() }
        b.person.personBirthday.setOnClickListener { pickDate(true) }; b.documents.documentsIssued.setOnClickListener { pickDate(false) }
        b.documents.documentsAvatar.setOnClickListener { choosePhoto("avatar") }; b.documents.documentsLicensePhoto.setOnClickListener { choosePhoto("license") }; b.documents.documentsPassportPhoto.setOnClickListener { choosePhoto("passport") }
        var renderedResetVersion = model.formResetVersion
        model.page.observe(viewLifecycleOwner) { page ->
            val inset=if(page==AuthPage.HOME) 0 else (24*resources.displayMetrics.density).toInt()
            b.root.setPadding(inset,inset,inset,inset)
            // ЛР3–4: после входа показываем каталог и вложенные экраны.
            b.catalogContainer.isVisible=page==AuthPage.HOME
            b.authScroll.isVisible=page!=AuthPage.HOME
            val catalog=childFragmentManager.findFragmentByTag("catalog")
            if(!childFragmentManager.isStateSaved) {
                if(page==AuthPage.HOME && catalog==null) childFragmentManager.beginTransaction().replace(R.id.catalogContainer,CatalogFragment(),"catalog").commitNow()
                else if(page!=AuthPage.HOME && catalog!=null) childFragmentManager.beginTransaction().remove(catalog).commitNow()
            }
            if (renderedResetVersion != model.formResetVersion) {
                renderedResetVersion = model.formResetVersion
                listOf(b.account.accountEmail,b.account.accountPassword,b.account.accountRepeat,b.person.personSurname,b.person.personName,b.person.personPatronymic,b.person.personBirthday,b.documents.documentsLicense,b.documents.documentsIssued).forEach { it.text?.clear() }
                b.account.accountTerms.isChecked=false; b.person.personGender.clearCheck(); renderPhotos()
            }
            listOf(b.choice.root,b.login.root,b.account.root,b.person.root,b.documents.root,b.success.root,b.home.root)
                .forEachIndexed { index, item -> item.isVisible=index==page.ordinal }
            b.header.isVisible=page in listOf(AuthPage.LOGIN,AuthPage.ACCOUNT,AuthPage.PERSON,AuthPage.DOCUMENTS)
            b.title.isVisible=page!=AuthPage.LOGIN
            if (page==AuthPage.LOGIN && b.login.loginEmail.text.toString()!=model.loginEmail) b.login.loginEmail.setText(model.loginEmail)
            if (page in listOf(AuthPage.SUCCESS,AuthPage.HOME)) {
                b.account.accountPassword.text?.clear(); b.account.accountRepeat.text?.clear(); b.login.loginPassword.text?.clear()
            }
            enableActions()
        }
        model.busy.observe(viewLifecycleOwner) { b.progress.isVisible=it || photoBusy; enableActions() }
        model.message.observe(viewLifecycleOwner) { code ->
            b.error.isVisible=code!=null
            if (code!=null) b.error.setText(messageResource(code))
        }
        renderPhotos()
        consumeIntent(requireActivity().intent)
    }
    fun logoutFromCatalog() { model.logout(AuthPage.LOGIN) }
    fun back(): Boolean {
        if(!::model.isInitialized) return false
        if(model.page.value==AuthPage.HOME && (childFragmentManager.findFragmentByTag("catalog") as? CatalogFragment)?.back()==true) return true
        return model.back()
    }
    fun consumeIntent(intent: Intent) {
        val uri=intent.data ?: return
        if (uri.scheme!="drivenext" || uri.host!="auth") return
        intent.data=null
        val error = uri.getQueryParameter("error_code") ?: uri.getQueryParameter("error")
        model.callback(uri.getQueryParameter("code"),error)
    }
    private fun enableActions() {
        val b=bound ?: return; val d=model.draft; val idle=model.busy.value!=true && !photoBusy
        fun descendants(v: View) {
            if (v is ViewGroup) (0 until v.childCount).forEach { descendants(v.getChildAt(it)) }
            else v.isEnabled=idle
        }
        descendants(b.root)
        b.login.loginSubmit.isEnabled=idle && model.loginEmail.isNotBlank() && model.loginPassword.isNotBlank()
        b.account.accountNext.isEnabled=idle && d.email.isNotBlank() && d.password.isNotBlank() && d.repeat.isNotBlank()
        b.person.personNext.isEnabled=idle && d.surname.isNotBlank() && d.name.isNotBlank() && d.birthday.isNotBlank() && d.gender.isNotBlank()
        b.documents.documentsNext.isEnabled=idle && d.license.isNotBlank() && d.issued.isNotBlank() && d.licensePhoto!=null && d.passportPhoto!=null
    }
    private fun pickDate(birthday: Boolean) {
        if (parentFragmentManager.findFragmentByTag("auth-date")!=null) return
        val today=MaterialDatePicker.todayInUtcMilliseconds()
        val picker=MaterialDatePicker.Builder.datePicker().setTitleText(if(birthday) R.string.auth_birthday else R.string.auth_issued)
            .setSelection(today).setCalendarConstraints(CalendarConstraints.Builder().setStart(LocalDate.of(1900,1,1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()).setEnd(today).setValidator(DateValidatorPointBackward.now()).build()).build()
        picker.addOnPositiveButtonClickListener { millis ->
            val date=Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            val value=date.format(DateTimeFormatter.ofPattern(if(birthday) "MM/dd/uuuu" else "dd/MM/uuuu"))
            if (birthday) bound?.person?.personBirthday?.setText(value) else bound?.documents?.documentsIssued?.setText(value)
        }
        picker.show(parentFragmentManager,"auth-date")
    }
    private fun choosePhoto(target: String) {
        photoTarget=target
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.auth_select_photo)
            .setItems(arrayOf(getString(R.string.auth_gallery),getString(R.string.auth_camera))) { _, which ->
                if (which==0) gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else try {
                    val directory=File(requireContext().cacheDir,"auth-camera").apply { mkdirs() }
                    cameraFile=File.createTempFile("photo-",".jpg",directory)
                    camera.launch(FileProvider.getUriForFile(requireContext(),"${requireContext().packageName}.files",checkNotNull(cameraFile)))
                } catch (_: Exception) { model.message.value="photo"; cameraFile?.delete(); cameraFile=null }
            }.show()
    }
    private fun importPhoto(uri: Uri) {
        if (photoBusy) return
        photoBusy=true; binding.progress.isVisible=true; enableActions()
        val context=requireContext().applicationContext; val target=photoTarget
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file=withContext(Dispatchers.IO) {
                    val bytes=context.contentResolver.openInputStream(uri)?.use { stream ->
                        val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                        while(true) { val n=stream.read(buffer); if(n<0) break; check(out.size()+n<=20*1024*1024); out.write(buffer,0,n) }
                        out.toByteArray()
                    } ?: error("image")
                    val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
                    check(options.outWidth>0 && options.outHeight>0)
                    options.inJustDecodeBounds=false; options.inSampleSize=1
                    while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>1600) options.inSampleSize*=2
                    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: error("image")
                    val dir=File(context.filesDir,"auth-draft").apply { mkdirs() }
                    val file=File.createTempFile("$target-",".jpg",dir)
                    try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)) } } finally { bitmap.recycle() }
                    file
                }
                val old=when(target) { "license"->model.draft.licensePhoto; "passport"->model.draft.passportPhoto; else->model.draft.avatar }
                when(target) { "license"->model.draft.licensePhoto=file.path; "passport"->model.draft.passportPhoto=file.path; else->model.draft.avatar=file.path }
                old?.let { File(it).delete() }
                renderPhotos(); model.message.value=null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { model.message.value="photo" }
            finally { cameraFile?.delete(); cameraFile=null; photoBusy=false; bound?.progress?.isVisible=model.busy.value==true; enableActions() }
        }
    }
    private fun renderPhotos() {
        val b=bound ?: return; val d=model.draft
        if (d.avatar==null) b.documents.documentsAvatarImage.setImageResource(R.drawable.auth_avatar)
        else b.documents.documentsAvatarImage.setImageURI(Uri.fromFile(File(d.avatar!!)))
        b.documents.documentsLicensePhoto.setText(if(d.licensePhoto==null) R.string.auth_license_photo else R.string.auth_license_attached)
        b.documents.documentsPassportPhoto.setText(if(d.passportPhoto==null) R.string.auth_passport_photo else R.string.auth_passport_attached)
        enableActions()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString("photoTarget",photoTarget); outState.putString("cameraFile",cameraFile?.path) }
    override fun onDestroyView() { bound=null; super.onDestroyView() }
    private fun messageResource(code: String) = when(code) {
        "email"->R.string.auth_error_email; "password"->R.string.auth_error_password; "match"->R.string.auth_error_match; "terms"->R.string.auth_error_terms
        "name"->R.string.auth_error_name; "gender"->R.string.auth_error_gender; "date"->R.string.auth_error_date; "license"->R.string.auth_error_license; "photos"->R.string.auth_error_photos
        "configuration"->R.string.auth_error_configuration; "credentials"->R.string.auth_error_credentials; "confirmation"->R.string.auth_error_confirmation; "registered"->R.string.auth_error_registered
        "network"->R.string.auth_error_network; "rate_limit"->R.string.auth_error_rate_limit; "oauth_confirmation"->R.string.auth_error_oauth_confirmation; "oauth_cancelled"->R.string.auth_error_oauth_cancelled; "photo"->R.string.auth_error_photo
        else->R.string.auth_error_request_failed
    }
}
