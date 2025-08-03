package com.serkantken.secuasist.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.serkantken.secuasist.views.fragments.CargoFragment
import com.serkantken.secuasist.views.fragments.ContactsFragment
import com.serkantken.secuasist.views.fragments.FaultTrackingFragment
import com.serkantken.secuasist.views.fragments.VillaFragment

class MainViewPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    private val fragments: List<Fragment> = listOf(
        VillaFragment(),
        ContactsFragment(),
        CargoFragment(),
        FaultTrackingFragment()
    )

    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> VillaFragment() // Villalar
            1 -> ContactsFragment() // Kişiler
            2 -> CargoFragment()  // Kargolar
            3 -> FaultTrackingFragment() // Kameralar
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}