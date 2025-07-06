package com.serkantken.secuasist.views.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.serkantken.secuasist.R
import com.serkantken.secuasist.adapters.VillaAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.FragmentVillaBinding
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.activities.MainActivity
import kotlinx.coroutines.launch

class VillaFragment : Fragment() {
    private var _binding: FragmentVillaBinding? = null
    private val binding get() = _binding!!
    private lateinit var villaAdapter: VillaAdapter
    private lateinit var appDatabase: AppDatabase

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
        observeVillas()
    }

    private fun setupRecyclerView() {
        villaAdapter = VillaAdapter(
            onItemClick = { villa ->
                // MainActivity'deki showAddEditVillaDialog fonksiyonunu çağır
                (activity as? MainActivity)?.showAddEditVillaDialog(villa)
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

    private fun observeVillas() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.villaDao().getAllVillasWithContacts().collect { villaWithContactsList ->
                villaAdapter.submitList(villaWithContactsList)
                // (activity as? MainActivity)?.showToast("Villalar güncellendi: ${villaWithContactsList.size} adet.")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // View Binding için hafıza sızıntılarını önle
    }
}