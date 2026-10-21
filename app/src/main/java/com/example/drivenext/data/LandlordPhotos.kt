// ЛР5. Копирование выбранных изображений в закрытую папку приложения с ограничением размера.
package com.example.drivenext.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class LandlordPhotos(context: Context, owner: String, id: String) {
    private val app=context.applicationContext
    private val directory=File(app.filesDir,"landlord-photos/$owner/$id")
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val source=app.contentResolver.openInputStream(uri)?.use { stream ->
            val out=ByteArrayOutputStream(); val buffer=ByteArray(8192)
            while(true) {
                val count=stream.read(buffer); if(count<0) break
                check(out.size()+count<=20*1024*1024)
                out.write(buffer,0,count)
            }
            out.toByteArray()
        } ?: error("Не удалось открыть изображение")
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(source,0,source.size,options)
        check(options.outWidth>0 && options.outHeight>0)
        options.inSampleSize=1
        while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>1600) options.inSampleSize*=2
        options.inJustDecodeBounds=false
        val decoded=checkNotNull(BitmapFactory.decodeByteArray(source,0,source.size,options))
        // Снимки телефона могут хранить поворот отдельно от пикселей, в метаданных EXIF.
        val orientation=try {ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)} catch(_: Exception) {ExifInterface.ORIENTATION_NORMAL}
        val matrix=Matrix().apply {
            when(orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f,1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f,-1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {setRotate(90f);postScale(-1f,1f)}
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {setRotate(-90f);postScale(-1f,1f)}
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        val bitmap=try {Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)}
            catch(e: Exception) {decoded.recycle();throw e}
        if(bitmap!==decoded) decoded.recycle()
        directory.mkdirs()
        val file=File(directory,"${UUID.randomUUID()}.jpg")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)) }
            file.absolutePath
        } catch(e: Exception) { file.delete(); throw e }
        finally { bitmap.recycle() }
    }
    suspend fun remove(path: String) = withContext(Dispatchers.IO) {
        val file=File(path).canonicalFile
        if(file.parentFile==directory.canonicalFile) file.delete()
        Unit
    }
}
