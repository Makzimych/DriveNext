package com.example.drivenext

import android.os.Bundle
import android.content.Intent
import com.example.drivenext.presentation.AuthFragment
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.example.drivenext.data.AndroidStartupRepository
import com.example.drivenext.data.AuthDependencies
import com.example.drivenext.databinding.ActivityMainBinding
import com.example.drivenext.domain.StartupInteractor
import com.example.drivenext.presentation.OnboardingAdapter
import com.example.drivenext.presentation.Screen
import com.example.drivenext.presentation.StartupViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: StartupViewModel
    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) { viewModel.selectPage(position) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        viewModel = ViewModelProvider(this, object : AbstractSavedStateViewModelFactory(this@MainActivity, savedInstanceState) {
            override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
                val interactor = StartupInteractor(AndroidStartupRepository(applicationContext), AuthDependencies.repository(applicationContext))
                @Suppress("UNCHECKED_CAST")
                return StartupViewModel(interactor, handle) as T
            }
        })[StartupViewModel::class.java]
        binding.onboarding.pager.adapter = OnboardingAdapter(this)
        binding.onboarding.pager.setCurrentItem(viewModel.page.value ?: 0, false)
        binding.onboarding.pager.registerOnPageChangeCallback(pageCallback)
        binding.onboarding.next.setOnClickListener { viewModel.next() }
        binding.onboarding.skip.setOnClickListener { viewModel.finishOnboarding() }
        binding.offline.retry.setOnClickListener { viewModel.checkConnection() }
        viewModel.screen.observe(this) { screen ->
            binding.loading.root.visibility = if (screen == Screen.LOADING) View.VISIBLE else View.GONE
            binding.offline.root.visibility = if (screen == Screen.NO_CONNECTION) View.VISIBLE else View.GONE
            binding.onboarding.root.visibility = if (screen == Screen.ONBOARDING) View.VISIBLE else View.GONE
            binding.destination.root.visibility = View.GONE
            binding.authContainer.visibility = if (screen == Screen.AUTH || screen == Screen.HOME) View.VISIBLE else View.GONE
            if ((screen == Screen.AUTH || screen == Screen.HOME) && !supportFragmentManager.isStateSaved && supportFragmentManager.findFragmentByTag("auth-flow") == null) {
                supportFragmentManager.beginTransaction().replace(R.id.authContainer, AuthFragment(), "auth-flow").commitNow()
            }
        }
        viewModel.page.observe(this) { page ->
            if (binding.onboarding.pager.currentItem != page) binding.onboarding.pager.setCurrentItem(page, true)
            binding.onboarding.next.setText(if (page == 2) R.string.go else R.string.next)
            binding.onboarding.indicators.contentDescription = getString(R.string.slide_position, page + 1, 3)
            listOf(binding.onboarding.dot0, binding.onboarding.dot1, binding.onboarding.dot2).forEachIndexed { index, dot ->
                dot.setBackgroundResource(if (page == index) R.drawable.indicator_active else R.drawable.indicator_inactive)
                dot.layoutParams = dot.layoutParams.apply { width = resources.getDimensionPixelSize(if (page == index) R.dimen.size_32 else R.dimen.size_16) }
            }
        }
        viewModel.error.observe(this) { show ->
            if (show) {
                viewModel.acknowledgeError()
                MaterialAlertDialogBuilder(this).setTitle(R.string.error_title).setMessage(R.string.storage_error).setPositiveButton(R.string.ok, null).show()
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if ((supportFragmentManager.findFragmentByTag("auth-flow") as? AuthFragment)?.takeIf { binding.authContainer.visibility == View.VISIBLE }?.back() != true && !viewModel.back()) { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        (supportFragmentManager.findFragmentByTag("auth-flow") as? AuthFragment)?.consumeIntent(intent)
    }
    override fun onResume() { super.onResume(); if (::viewModel.isInitialized) viewModel.checkConnection() }
    override fun onDestroy() {
        if (::binding.isInitialized) binding.onboarding.pager.unregisterOnPageChangeCallback(pageCallback)
        super.onDestroy()
    }
}

