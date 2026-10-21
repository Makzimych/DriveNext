// ЛР2–4. Общий экземпляр репозитория авторизации для входа и доступа к данным текущего пользователя.
package com.example.drivenext.data

import android.content.Context

object AuthDependencies {
    @Volatile private var instance: SupabaseAuthRepository? = null
    fun repository(context: Context): SupabaseAuthRepository = instance ?: synchronized(this) {
        instance ?: SupabaseAuthRepository(context.applicationContext).also { instance = it }
    }
}
