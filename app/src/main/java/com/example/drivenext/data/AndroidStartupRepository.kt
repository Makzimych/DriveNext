package com.example.drivenext.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.drivenext.domain.StartupRepository

class AndroidStartupRepository(context: Context) : StartupRepository {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val preferences = context.getSharedPreferences("startup", Context.MODE_PRIVATE)

    override fun hasInternet(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    override fun isOnboardingCompleted() = preferences.getBoolean("onboarding_completed", false)
    // commit reports write failure, so the UI cannot silently lose the first-launch flag.
    override fun completeOnboarding() = preferences.edit().putBoolean("onboarding_completed", true).commit()

    override fun observeNetwork(onChanged: () -> Unit): () -> Unit {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChanged()
            override fun onLost(network: Network) = onChanged()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = onChanged()
        }
        connectivity.registerDefaultNetworkCallback(callback)
        return { connectivity.unregisterNetworkCallback(callback) }
    }
}
