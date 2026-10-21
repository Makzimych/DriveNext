// ЛР1. Вводные страницы знакомства с приложением и их адаптер.
package com.example.drivenext.presentation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.drivenext.R
import com.example.drivenext.databinding.FragmentOnboardingPageBinding

class OnboardingPageFragment : Fragment(R.layout.fragment_onboarding_page) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentOnboardingPageBinding.bind(view)
        val index = requireArguments().getInt("page").coerceIn(0, 2)
        val titles = intArrayOf(R.string.rental_title, R.string.safe_title, R.string.offers_title)
        val descriptions = intArrayOf(R.string.rental_description, R.string.safe_description, R.string.offers_description)
        val images = intArrayOf(R.drawable.onboarding_rental, R.drawable.onboarding_safe, R.drawable.onboarding_offers)
        binding.title.setText(titles[index])
        binding.description.setText(descriptions[index])
        binding.illustration.setImageResource(images[index])
    }
    companion object {
        fun create(page: Int) = OnboardingPageFragment().apply { arguments = Bundle().apply { putInt("page", page) } }
    }
}

class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 3
    override fun createFragment(position: Int) = OnboardingPageFragment.create(position)
}
