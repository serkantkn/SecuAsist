package com.serkantken.secuasist.views.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.launch
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.recyclerview.widget.LinearLayoutManager
import com.serkantken.secuasist.R
import com.serkantken.secuasist.adapters.ContactsAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.FragmentContactsBinding
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.VillaContact
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.activities.MainActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.text.contains
import kotlin.text.filter
import androidx.core.net.toUri
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.serkantken.secuasist.SecuAsistApplication

@ExperimentalBadgeUtils
class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var appDatabase: AppDatabase
    private var currentSearchQuery: String? = null
    private val searchQuery = MutableStateFlow<String?>(null)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        appDatabase = AppDatabase.getDatabase(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rvContacts) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                Tools(requireActivity()).convertDpToPixel(16),
                systemBars.top + Tools(requireActivity()).convertDpToPixel(55),
                Tools(requireActivity()).convertDpToPixel(16),
                systemBars.bottom + Tools(requireActivity()).convertDpToPixel(72)
            )
            insets
        }

        setupRecyclerView()
        observeContacts()
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter(
            requireActivity(),
            onItemClick = { contact, _ -> // VillaContact bilgisi bu fragment'ta genelde null olacak
                (activity as? MainActivity)?.showAddEditContactDialog(contact, null)
            },
            onCallClick = { contact ->
                val phoneNumber = contact.contactPhone
                if (phoneNumber.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Bu kişiye ait telefon numarası bulunamadı.", Toast.LENGTH_SHORT).show()
                    return@ContactsAdapter
                }

                lifecycleScope.launch {
                    contact.lastCallTimestamp = System.currentTimeMillis()
                    appDatabase.contactDao().update(contact)
                }
                val intent = Intent(Intent.ACTION_CALL)
                intent.data = "tel:$phoneNumber".toUri() // "tel:" ön eki zorunludur.

                try {
                    startActivity(intent)
                } catch (e: SecurityException) {
                    // Eğer manifest'e izin eklemeyi unutursak bu hata oluşur.
                    Toast.makeText(requireContext(), "Telefon arama izni verilmemiş.", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            },
            onDeleteClick = { contact ->
                showDeleteConfirmationDialog(contact)
            },
            isShowingInfo = false,
            isChoosingContact = false
        )
        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            clipToPadding = false
            adapter = contactsAdapter
        }
    }

    private fun showDeleteConfirmationDialog(contact: Contact) {
        AlertDialog.Builder(requireContext())
            .setTitle("Kişiyi Sil")
            .setMessage("${contact.contactName} adlı kişiyi kalıcı olarak silmek istediğinize emin misiniz? Bu işlem geri alınamaz.")
            .setPositiveButton("Evet, Sil") { _, _ ->
                lifecycleScope.launch {
                    try {
                        appDatabase.contactDao().delete(contact)
                        (activity?.application as SecuAsistApplication).sendDelete("CONTACT", contact.contactId)
                        (activity as? MainActivity)?.showToast("${contact.contactName} silindi.")
                    } catch (e: Exception) {
                        (activity as? MainActivity)?.showToast("Silinirken hata oluştu: ${e.localizedMessage}")
                    }
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    private fun observeContacts() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Veritabanından gelen asıl akış ile arama sorgusu akışını birleştir
            appDatabase.contactDao().getAllContactsAsFlow() // Veya sizin kullandığınız fonksiyon
                .combine(searchQuery) { allContacts, query ->
                    if (query.isNullOrBlank()) {
                        allContacts // Arama boşsa, tüm listeyi döndür
                    } else {
                        // Arama doluysa, isim veya telefonda ara (büyük/küçük harf duyarsız)
                        allContacts.filter { contact ->
                            contact.contactName?.contains(query, ignoreCase = true) ?: false
                                    // || contact.contactPhone?.contains(query, ignoreCase = true) ?: false
                        }
                    }
                }
                .collectLatest { filteredContacts ->
                    // Adapter'ınıza filtrelenmiş listeyi gönderin
                    contactsAdapter.submitList(filteredContacts)
                }
        }
    }

    // Dönüş tipi Flow<List<Contact>> olarak güncellendi
    private fun getFilteredContactsFlow(): Flow<List<Contact>> {
        val contactsFlow = appDatabase.contactDao().getAllContactsAsFlow()
        return contactsFlow.map { contactList ->
            if (currentSearchQuery.isNullOrBlank()) {
                contactList
            } else {
                contactList.filter {
                    it.contactName?.contains(currentSearchQuery!!, ignoreCase = true) == true ||
                            it.contactPhone?.contains(currentSearchQuery!!, ignoreCase = true) == true
                }
            }
            // Pair<Contact, VillaContact?>'e yapılan map işlemi kaldırıldı.
        }
    }

    // Bu metod MainActivity'deki arama çubuğundan çağrılacak
    fun filterContacts(query: String?) {
        currentSearchQuery = query
        // Veri akışını yeniden tetiklemek için observeContacts'ı doğrudan çağırmak yerine,
        // LiveData veya StateFlow kullanmak daha reaktif olurdu.
        // Şimdilik, basitçe listeyi yeniden toplamak için observe'u tekrar çağırabiliriz
        // veya daha iyisi, Flow'un doğası gereği currentSearchQuery değiştiğinde
        // getFilteredContactsFlow yeni bir emisyon yapacak ve collectLatest bunu alacaktır.
        // Bu yüzden sadece query'yi güncellemek yeterli olabilir, ancak emin olmak için
        // listeyi yeniden yükleyebiliriz. En temizi ViewModel ile olurdu.
        // Şimdilik, Flow'un yeniden tetiklenmesine güvenelim veya basitçe:
        lifecycleScope.launch {
            contactsAdapter.submitList(getFilteredContactsFlow().first()) // Anlık güncelleme için
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}