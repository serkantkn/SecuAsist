package com.serkantken.secuasist.views.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.serkantken.secuasist.adapters.VillaAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.FragmentVillaBinding
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.activities.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class VillaFragment : Fragment() {
    private var _binding: FragmentVillaBinding? = null
    private val binding get() = _binding!!
    private lateinit var villaAdapter: VillaAdapter
    private lateinit var appDatabase: AppDatabase
    private var villaObserverJob: Job? = null
    private var currentSortType = VillaSortType.VILLA_NO_ASC

    enum class VillaSortType {
        VILLA_NO_ASC, VILLA_NO_DESC, STREET_NAME_ASC, EMPTY_FIRST
    }

    enum class StatusFilter {
        UNDER_CONSTRUCTION, IS_SPECIAL, IS_RENTAL, NO_CARGO_CALLS
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentVillaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appDatabase = AppDatabase.getDatabase(requireContext())
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                Tools(requireActivity()).convertDpToPixel(12),
                systemBars.top + Tools(requireActivity()).convertDpToPixel(55),
                Tools(requireActivity()).convertDpToPixel(12),
                systemBars.bottom + Tools(requireActivity()).convertDpToPixel(72)
            )
            insets
        }
        setupRecyclerView()
        setupSwipeToRefresh()
        binding.swipeRefreshLayout.isRefreshing = true
        observeVillas()
    }

    private fun setupRecyclerView() {
        villaAdapter = VillaAdapter(
            onItemClick = { villa ->
                (activity as? MainActivity)?.showVillaInfoBalloon(villa)
            },
            onItemLongClick = { villa ->
                (activity as? MainActivity)?.showAddEditVillaDialog(villa)
                true
            }
        )
        binding.recyclerView.apply {
            adapter = villaAdapter
            clipToPadding = false
        }
    }

    private fun setupSwipeToRefresh() = binding.swipeRefreshLayout.setOnRefreshListener(::observeVillas)

    fun sortVillasBy(sortType: VillaSortType) {
        currentSortType = sortType
        binding.swipeRefreshLayout.isRefreshing = true
        observeVillas()
    }

    data class VillaFilterState(
        val selectedStreet: String? = null,
        val activeStatusFilters: Set<StatusFilter> = emptySet(),
        val sortBy: VillaSortType = VillaSortType.VILLA_NO_ASC,
        val searchQuery: String? = null // YENİ ALAN
    )

    fun setSearchQuery(query: String) {
        filterState.value = filterState.value.copy(searchQuery = query)
    }

    private val filterState = MutableStateFlow(VillaFilterState())

    fun clearFilters() {
        filterState.value = VillaFilterState()
    }

    fun setStreetFilter(streetName: String) {
        filterState.value = filterState.value.copy(selectedStreet = streetName)
    }

    fun setStatusFilters(newFilters: Set<StatusFilter>) {
        filterState.value = filterState.value.copy(activeStatusFilters = newFilters)
    }

    fun getCurrentFilterState(): VillaFilterState {
        return filterState.value
    }

    private fun observeVillas() {
        villaObserverJob?.cancel()
        villaObserverJob = viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.villaDao().getAllVillasWithContacts()
                .combine(filterState) { allVillas, currentFilter ->
                    var filteredList = allVillas

                    // 1. Sokağa Göre Filtrele
                    if (!currentFilter.selectedStreet.isNullOrEmpty()) {
                        filteredList = filteredList.filter {
                            it.villa.villaStreet == currentFilter.selectedStreet
                        }
                    }

                    // 2. Duruma Göre Filtrele
                    if (currentFilter.activeStatusFilters.isNotEmpty()) {
                        filteredList = filteredList.filter { villaWithContacts ->
                            val villa = villaWithContacts.villa
                            currentFilter.activeStatusFilters.all { filter ->
                                when (filter) {
                                    StatusFilter.UNDER_CONSTRUCTION -> villa.isVillaUnderConstruction == 1
                                    StatusFilter.IS_SPECIAL -> villa.isVillaSpecial == 1
                                    StatusFilter.IS_RENTAL -> villa.isVillaRental == 1
                                    StatusFilter.NO_CARGO_CALLS -> villa.isVillaCallForCargo == 0
                                }
                            }
                        }
                    }

                    // ARAMA FİLTRESİ ---
                    if (!currentFilter.searchQuery.isNullOrBlank()) {
                        val query = currentFilter.searchQuery.trim()
                        filteredList = filteredList.filter { villaWithContacts ->
                            val villa = villaWithContacts.villa
                            villa.villaNo.toString().contains(query) ||
                                    (villa.villaNotes?.contains(query, ignoreCase = true) ?: false)
                        }
                    }

                    // 4. Sıralama
                    when (currentFilter.sortBy) {
                        VillaSortType.VILLA_NO_ASC -> {
                            filteredList.sortedBy { it.villa.villaNo }
                        }
                        else -> filteredList
                    }
                }
                .collectLatest { villas ->
                    villaAdapter.submitList(villas) {
                        if (binding.swipeRefreshLayout.isRefreshing) {
                            binding.swipeRefreshLayout.isRefreshing = false
                        }
                        if (villas.isNotEmpty()) {
                            binding.recyclerView.smoothScrollToPosition(0)
                        }
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}