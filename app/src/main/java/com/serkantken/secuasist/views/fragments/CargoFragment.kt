package com.serkantken.secuasist.views.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.serkantken.secuasist.adapters.CargoCompanyAdapter
import com.serkantken.secuasist.adapters.DisplayCargoCompany
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.FragmentCargoBinding
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.activities.MainActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

class CargoFragment : Fragment() {

    private var _binding: FragmentCargoBinding? = null
    private val binding get() = _binding!!

    private lateinit var cargoCompanyAdapter: CargoCompanyAdapter
    private lateinit var appDatabase: AppDatabase
    private val searchQuery = MutableStateFlow<String?>(null)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCargoBinding.inflate(inflater, container, false)
        appDatabase = AppDatabase.getDatabase(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rvCargoCompanies) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                0,
                systemBars.top + Tools(requireActivity()).convertDpToPixel(55),
                0,
                systemBars.bottom + Tools(requireActivity()).convertDpToPixel(72)
            )
            insets
        }

        setupRecyclerView()
        observeCargoCompanies()
    }

    private fun setupRecyclerView() {
        cargoCompanyAdapter = CargoCompanyAdapter(
            onItemClick = { displayCompany -> // Sadece onItemClick kullanacağız
                (activity as? MainActivity)?.showSelectCargoRecipientsSheet(displayCompany.companyId)
            },
            onItemLongClick = { company: CargoCompany -> // Düzenleme için ayrı bir lambda
                (activity as? MainActivity)?.showAddEditCargoCompanyDialog(company)
            }
        )
        binding.rvCargoCompanies.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = cargoCompanyAdapter
            clipToPadding = false
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    private fun observeCargoCompanies() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.cargoCompanyDao().getAllCargoCompanies() // Sizin fonksiyonunuz ne ise
                .combine(searchQuery) { allCompanies, query ->
                    if (query.isNullOrBlank()) {
                        allCompanies
                    } else {
                        // Sadece şirket adına göre ara
                        allCompanies.filter { company ->
                            company.companyName.contains(query, ignoreCase = true)
                        }
                    }
                }
                .mapLatest { filteredCompanies ->
                    // Her bir 'CargoCompany' nesnesini 'DisplayCargoCompany' nesnesine dönüştür.
                    // Bu işlem, her şirket için ek bir veritabanı sorgusu içerdiğinden asenkrondur.
                    // async/awaitAll kullanarak bu sorguları paralel çalıştırıp performansı artırıyoruz.
                    filteredCompanies.map { company ->
                        async {
                            // Her şirket için "aranmamış kargosu var mı?" bilgisini veritabanından kontrol et.
                            val hasUncalled = appDatabase.cargoDao().hasUncalledCargosForCompany(company.companyId)

                            // Adaptörün beklediği DisplayCargoCompany nesnesini oluştur.
                            // NOT: DisplayCargoCompany sınıfınızın yapısına göre bu satırı düzenlemeniz gerekebilir.
                            DisplayCargoCompany(
                                company = company,
                                hasUncalledCargos = hasUncalled
                            )
                        }
                    }.awaitAll() // Bütün asenkron işlemlerin bitmesini bekle
                }
                .collectLatest { filteredCompanies ->
                    cargoCompanyAdapter.submitList(filteredCompanies)
                }
        }
    }

    // Bu yardımcı fonksiyon, CargoDao'nuzdaki ilgili metodu çağırmalıdır.
    // Bu fonksiyonun gerçek implementasyonu için CargoDao'nuzda bir metodunuz olmalı.
    // Örnek: suspend fun getUncalledCargosCountForCompany(companyId: Int): Int
    //        suspend fun hasUncalledCargosForCompany(companyId: Int): Boolean
    private suspend fun checkIfCompanyHasUncalledCargos(companyId: Int): Boolean {
        return try {
            // appDatabase.cargoDao() -> Bireysel kargoları yöneten DAO'ya erişim
            appDatabase.cargoDao().hasUncalledCargosForCompany(companyId)
        } catch (e: Exception) {
            Log.e("CargoFragment", "Error checking uncalled cargos for company $companyId: ${e.message}", e)
            false // Hata durumunda varsayılan olarak false döndür
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Bellek sızıntılarını önlemek için
    }
}