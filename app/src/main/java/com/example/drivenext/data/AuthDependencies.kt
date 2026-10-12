package com.example.drivenext.data

import android.content.Context

// One session/client for startup routing and the authentication flow.
object AuthDependencies {
    @Volatile private var instance: SupabaseAuthRepository? = null
    fun repository(context: Context): SupabaseAuthRepository = instance ?: synchronized(this) {
        instance ?: SupabaseAuthRepository(context.applicationContext).also { instance = it }
    }
}
