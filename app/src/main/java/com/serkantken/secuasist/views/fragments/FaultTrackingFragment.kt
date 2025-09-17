package com.serkantken.secuasist.views.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.serkantken.secuasist.adapters.FaultTabsAdapter
import com.serkantken.secuasist.adapters.MainTabsAdapter
import com.serkantken.secuasist.databinding.FragmentFaultTrackingBinding
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.activities.MainActivity.MainTab

class FaultTrackingFragment : Fragment() {

    private var _binding: FragmentFaultTrackingBinding? = null
    private val binding get() = _binding!!
    private val tabsList = mutableListOf<FaultTab>()
    private lateinit var faultTabsAdapter: FaultTabsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFaultTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    data class FaultTab(
        val id: Int, // Benzersiz bir kimlik (ViewPager'ın pozisyonuyla eşleşebilir)
        val title: String,
        var isSelected: Boolean = false
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = FaultTrackingPagerAdapter(this)
        binding.viewPagerCameraIntercom.adapter = pagerAdapter
        binding.viewPagerCameraIntercom.offscreenPageLimit = 2
        binding.viewPagerCameraIntercom.isUserInputEnabled = true

        binding.viewPagerCameraIntercom.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                faultTabsAdapter.selectTab(position) // Adapter'da sekmeyi seçili işaretle
            }
        })
        setupMainTabsRecyclerView()

        ViewCompat.setOnApplyWindowInsetsListener(binding.blurNavView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = v.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = systemBars.top + Tools(requireActivity()).convertDpToPixel(50)
            v.layoutParams = params
            insets
        }
    }

    private fun setupMainTabsRecyclerView() {
        // Tab verilerini oluştur
        tabsList.clear()
        tabsList.add(FaultTab(0, "Kameralar",  true)) // İlk tab seçili başlasın
        tabsList.add(FaultTab(1, "İnterkomlar",  false))
        // İkonları kendi projenizdeki drawable'lar ile değiştirin (ic_home, ic_cargo vb.)

        faultTabsAdapter = FaultTabsAdapter { clickedTab ->
            binding.viewPagerCameraIntercom.setCurrentItem(clickedTab.id, true)
            // SnapHelper zaten buraya kaydıracaktır, ama emin olmak için:
            // (binding.rvMainTabs.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(clickedTab.id, 0)
        }

        binding.rvFaultTabs.apply {
            layoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.HORIZONTAL, false)
            adapter = faultTabsAdapter
            // SnapHelper ekleme
            //val snapHelper = PagerSnapHelper() // Veya LinearSnapHelper deneyebilirsiniz
            //snapHelper.attachToRecyclerView(this)
        }
        faultTabsAdapter.submitList(tabsList)
        faultTabsAdapter.selectTab(0) // Başlangıçta ilk tab seçili
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