// ЛР5. Проверка шагов, восстановление черновика и переход к успеху только после записи в базу.
package com.example.drivenext.presentation

import androidx.lifecycle.*
import com.example.drivenext.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class LandlordViewModel(private val repository: LandlordRepository,
    private val saved: SavedStateHandle): ViewModel() {
    val id: String = saved.get<String>("landlord_id") ?: UUID.randomUUID().toString().also { saved["landlord_id"]=it }
    val page=saved.getLiveData("landlord_page",LandlordPage.INTRO.name)
    val draft=MutableLiveData(saved.get<String>("landlord_draft")?.let { Json.decodeFromString<LandlordDraft>(it) } ?: LandlordDraft())
    val photos=saved.getLiveData("landlord_photos",arrayListOf<String>())
    val busy=MutableLiveData(false)
    val error=MutableLiveData<String?>(null)
    var pendingCamera: String?
        get()=saved["landlord_camera"]
        set(value) { saved["landlord_camera"]=value }
    fun update(value: LandlordDraft) {
        if(busy.value==true) return
        draft.value=value; saved["landlord_draft"]=Json.encodeToString(value)
    }
    fun next() {
        if(busy.value==true) return
        error.value=null
        val value=checkNotNull(draft.value).normalized()
        when(LandlordPage.valueOf(checkNotNull(page.value))) {
            LandlordPage.INTRO -> page.value=LandlordPage.ADDRESS.name
            LandlordPage.ADDRESS -> if(value.validAddress()) page.value=LandlordPage.INFORMATION.name
            LandlordPage.INFORMATION -> if(value.validAddress() && value.validInformation()) launchWork {
                repository.saveDetails(id,value); page.value=LandlordPage.PHOTOS.name
            }
            LandlordPage.PHOTOS -> if(photos.value.orEmpty().size in 1..LandlordDraft.MAX_PHOTOS) launchWork {
                repository.complete(id,value,photos.value.orEmpty().toList())
                // Конечный шаг не позволяет вернуться к заполненной форме и создать дубликат.
                page.value=LandlordPage.SUCCESS.name
            }
            LandlordPage.SUCCESS -> Unit
        }
    }
    fun canContinue(): Boolean = busy.value!=true && when(LandlordPage.valueOf(checkNotNull(page.value))) {
        LandlordPage.INTRO, LandlordPage.SUCCESS -> true
        LandlordPage.ADDRESS -> draft.value?.validAddress()==true
        LandlordPage.INFORMATION -> draft.value?.let {it.validAddress() && it.validInformation()}==true
        LandlordPage.PHOTOS -> photos.value.orEmpty().size in 1..LandlordDraft.MAX_PHOTOS
    }
    fun importPhotos(sources: List<String>, importer: suspend (String)->String, discard: suspend (String)->Unit) {
        if(busy.value==true || page.value!=LandlordPage.PHOTOS.name) return
        val remaining=LandlordDraft.MAX_PHOTOS-photos.value.orEmpty().size
        if(sources.isEmpty() || remaining<=0) return
        launchWork {
            var failures=0
            for(source in sources.distinct().take(remaining)) {
                val path=try { importer(source) } catch(e: CancellationException) {throw e}
                    catch(_: Exception) { failures++;continue }
                if(photos.value.orEmpty().size<LandlordDraft.MAX_PHOTOS) {
                    photos.value=ArrayList(photos.value.orEmpty()+path)
                } else discard(path)
            }
            if(failures>0) error.value="Некоторые изображения не удалось открыть. Выберите другие фотографии."
        }
    }
    fun removePhoto(path: String, discard: suspend (String)->Unit) {
        if(busy.value==true || page.value!=LandlordPage.PHOTOS.name) return
        launchWork { discard(path);photos.value=ArrayList(photos.value.orEmpty()-path) }
    }
    fun back(): Boolean {
        if(busy.value==true) return true
        error.value=null
        page.value=when(LandlordPage.valueOf(checkNotNull(page.value))) {
            LandlordPage.ADDRESS -> LandlordPage.INTRO.name
            LandlordPage.INFORMATION -> LandlordPage.ADDRESS.name
            LandlordPage.PHOTOS -> LandlordPage.INFORMATION.name
            else -> return false
        }
        return true
    }
    private fun launchWork(block: suspend ()->Unit) {
        busy.value=true;error.value=null
        viewModelScope.launch {
            try {withTimeout(60_000) {block()}}
            catch(e: CancellationException) {
                if(e is TimeoutCancellationException) error.value="Сохранение заняло слишком много времени. Попробуйте ещё раз."
                else throw e
            }
            catch(_: Exception) {error.value="Не удалось сохранить автомобиль. Попробуйте ещё раз."}
            finally {busy.value=false}
        }
    }
}
