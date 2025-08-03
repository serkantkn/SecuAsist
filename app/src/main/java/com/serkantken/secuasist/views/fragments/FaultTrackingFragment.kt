package com.serkantken.secuasist.views.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.serkantken.secuasist.databinding.FragmentFaultTrackingBinding

class FaultTrackingFragment : Fragment() {

    private var _binding: FragmentFaultTrackingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFaultTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = FaultTrackingPagerAdapter(this)
        binding.viewPagerCameraIntercom.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayoutCameraIntercom, binding.viewPagerCameraIntercom) { tab, position ->
            tab.text = when (position) {
                0 -> "Kameralar"
                1 -> "İnterkomlar"
                else -> null
            }
        }.attach()

        // WindowInsets ayarları (ContactsFragment'taki gibi MainActivity'den yönetiliyorsa gerekmez)
        // ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
        //     val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        //     // Gerekirse padding ayarları
        //     v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        //     insets
        // }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class FaultTrackingPagerAdapter(fragment: Fragment) :
    FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2 // İki sekmemiz var: Kameralar ve İnterkomlar

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CameraListFragment() // Kameraları listeleyecek fragment
            1 -> IntercomListFragment() // İnterkomları listeleyecek fragment
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}