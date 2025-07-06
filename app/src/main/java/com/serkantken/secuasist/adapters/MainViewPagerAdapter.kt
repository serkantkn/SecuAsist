package com.serkantken.secuasist.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.serkantken.secuasist.views.fragments.CameraFragment
import com.serkantken.secuasist.views.fragments.CargoFragment
import com.serkantken.secuasist.views.fragments.ContactsFragment
import com.serkantken.secuasist.views.fragments.VillaFragment

class MainViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    private val fragments: List<Fragment> = listOf(
        VillaFragment(),
        CargoFragment(),
        ContactsFragment(),
        CameraFragment()
    )

    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> VillaFragment() // Villalar
            1 -> CargoFragment()  // Kargolar
            2 -> ContactsFragment() // Kişiler
            3 -> CameraFragment() // Kameralar
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}