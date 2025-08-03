package com.serkantken.secuasist.views.fragments

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.serkantken.secuasist.R
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.adapters.AvailableVillasAdapter
import com.serkantken.secuasist.adapters.SelectedVillasAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.BottomSheetSelectRecipientsBinding
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.SelectableVilla
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.network.CargoDto
import com.serkantken.secuasist.network.WebSocketMessage
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.activities.CallingActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class SelectCargoRecipientsSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSelectRecipientsBinding? = null
    private val binding get() = _binding!!

    private lateinit var availableVillasAdapter: AvailableVillasAdapter
    private lateinit var selectedVillasAdapter: SelectedVillasAdapter

    private lateinit var appDatabase: AppDatabase
    private val gson = Gson()

    private var companyId: Int = -1

    private var allSelectableVillas: List<SelectableVilla> = emptyList()
    private val currentAvailableVillas = mutableListOf<SelectableVilla>()
    private val currentSelectedVillas = mutableListOf<SelectableVilla>()

    companion object {
        const val TAG = "SelectCargoRecipientsSheet"
        private const val ARG_COMPANY_ID = "company_id"
        const val EXTRA_CARGO_IDS = "CARGO_IDS_LIST" // CallingActivity için extra key

        fun newInstance(companyId: Int): SelectCargoRecipientsSheet {
            val args = Bundle()
            args.putInt(ARG_COMPANY_ID, companyId)
            val fragment = SelectCargoRecipientsSheet()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme)
        arguments?.let {
            companyId = it.getInt(ARG_COMPANY_ID, -1)
        }
        if (companyId == -1) {
            Log.e(TAG, "Company ID is missing!")
            dismiss()
            return
        }
        appDatabase = AppDatabase.getDatabase(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectRecipientsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Adım 2: Dialog penceresinin arka planını tamamen şeffaf yap (isteğe bağlı ama önerilir)
        dialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        Tools(requireActivity()).blur(arrayOf(binding.blurWindow), 10f, true)

        setupRecyclerViews()
        setupSearch()
        setupCreateCargosButton()
        loadInitialData()

        dialog?.setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout
            bottomSheet?.let {
                val layoutParams = it.layoutParams
                if (layoutParams != null) {
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    it.layoutParams = layoutParams
                }
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isFitToContents = false // false yaparak içeriğin ötesine genişlemesine izin ver
                behavior.skipCollapsed = true    // isteğe bağlı: daraltılmış durumu atla

                // PeekHeight'ı ekran yüksekliği yapmak, isFitToContents false iken tam genişlemeyi garantiler
                val displayMetrics = DisplayMetrics()
                requireActivity().display?.getRealMetrics(displayMetrics)
                behavior.peekHeight = displayMetrics.heightPixels
            }
        }
        isCancelable = false

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerViews() {
        availableVillasAdapter = AvailableVillasAdapter { selectableVilla ->
            moveVillaToSelected(selectableVilla)
        }
        binding.rvAvailableVillas.adapter = availableVillasAdapter

        selectedVillasAdapter = SelectedVillasAdapter { selectableVilla ->
            moveVillaToAvailable(selectableVilla)
        }
        binding.rvSelectedVillas.adapter = selectedVillasAdapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAvailableVillas(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterAvailableVillas(query: String) {
        val filteredList = if (query.isEmpty()) {
            currentAvailableVillas.toList()
        } else {
            val nonSelectedVillas = allSelectableVillas.filter { villa ->
                currentSelectedVillas.none { selected -> selected.villa.villaId == villa.villa.villaId }
            }
            nonSelectedVillas.filter {
                it.villa.villaNo.toString().contains(query, ignoreCase = true) ||
                        it.defaultContactName?.contains(query, ignoreCase = true) == true
            }
        }
        availableVillasAdapter.differ.submitList(filteredList)
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                val villasFromDb: List<Villa> = appDatabase.villaDao().getAllVillasAsList()
                val tempAllSelectableVillas = mutableListOf<SelectableVilla>()

                for (villa in villasFromDb) {
                    var defaultContact: Contact? = appDatabase.villaContactDao()
                        .getRealOwnersForVillaNonFlow(villa.villaId)
                        .firstOrNull()

                    if (defaultContact == null) {
                        defaultContact = appDatabase.villaContactDao()
                            .getContactsForVillaNonFlow(villa.villaId)
                            .firstOrNull()
                    }
                    tempAllSelectableVillas.add(
                        SelectableVilla(
                            villa = villa,
                            defaultContactId = defaultContact?.contactId,
                            defaultContactName = defaultContact?.contactName
                        )
                    )
                }
                allSelectableVillas = tempAllSelectableVillas.sortedBy { it.villa.villaNo } // Villa No'ya göre sırala
                currentAvailableVillas.clear()
                currentAvailableVillas.addAll(allSelectableVillas)

                availableVillasAdapter.differ.submitList(currentAvailableVillas.toList())
                selectedVillasAdapter.differ.submitList(emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial data", e)
                Toast.makeText(context, "Veri yüklenirken hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun moveVillaToSelected(villaToSelect: SelectableVilla) {
        if (currentAvailableVillas.removeIf { it.villa.villaId == villaToSelect.villa.villaId }) {
            currentSelectedVillas.add(villaToSelect)
            currentSelectedVillas.sortBy { it.villa.villaNo } 
            updateAdapters()
        }
    }

    private fun moveVillaToAvailable(villaToDeselect: SelectableVilla) {
        if (currentSelectedVillas.removeIf { it.villa.villaId == villaToDeselect.villa.villaId }) {
            currentAvailableVillas.add(villaToDeselect)
            currentAvailableVillas.sortBy { it.villa.villaNo }
            updateAdapters()
        }
    }

    private fun updateAdapters() {
        filterAvailableVillas(binding.etSearch.text.toString()) 
        selectedVillasAdapter.differ.submitList(currentSelectedVillas.toList())
        binding.btnCreateCargos.isEnabled = currentSelectedVillas.isNotEmpty()
    }

    private fun getCurrentTimestampISO8601(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun setupCreateCargosButton() {
        binding.btnCreateCargos.setOnClickListener {
            if (currentSelectedVillas.isEmpty()) {
                Toast.makeText(context, "Lütfen en az bir villa seçin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch { 
                val newCargoIds = mutableListOf<Int>()
                val webSocketClient = (activity?.application as? SecuAsistApplication)?.webSocketClient

                currentSelectedVillas.forEach { selectableVilla ->
                    val currentDate = getCurrentTimestampISO8601()
                    val cargo = Cargo(
                        companyId = companyId,
                        villaId = selectableVilla.villa.villaId,
                        whoCalled = selectableVilla.defaultContactId, 
                        isCalled = 0,
                        isMissed = 0,
                        date = currentDate,
                        callDate = null,
                        callAttemptCount = 0
                    )
                    try {
                        val insertedId = appDatabase.cargoDao().insert(cargo) 
                        newCargoIds.add(insertedId.toInt()) 

                        if (webSocketClient?.isConnected() == true) { 
                            val cargoDto = CargoDto(
                                companyId = cargo.companyId,
                                villaId = cargo.villaId,
                                whoCalled = cargo.whoCalled,
                                isCalled = cargo.isCalled,
                                isMissed = cargo.isMissed,
                                date = cargo.date,
                                callDate = cargo.callDate,
                                callAttemptCount = cargo.callAttemptCount
                            )
                            val message = WebSocketMessage("add_cargo", cargoDto)
                            webSocketClient.sendMessage(gson.toJson(message))
                        } else {
                            Log.w(TAG, "WebSocket not connected. Cargo for villa ${cargo.villaId} not sent to server.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error inserting cargo for villa ${selectableVilla.villa.villaNo}", e)
                        Toast.makeText(context, "Villa ${selectableVilla.villa.villaNo} için kargo oluşturulamadı.", Toast.LENGTH_SHORT).show()
                    }
                }

                if (newCargoIds.isNotEmpty()) {
                    Toast.makeText(context, "${newCargoIds.size} kargo oluşturuldu.", Toast.LENGTH_LONG).show()
                    val intent: Intent = Intent(activity, CallingActivity::class.java)
                    intent.putIntegerArrayListExtra(EXTRA_CARGO_IDS, ArrayList(newCargoIds))
                    startActivity(intent)
                    dismiss() 
                } else {
                    Toast.makeText(context, "Hiç kargo oluşturulamadı.", Toast.LENGTH_LONG).show()
                }
            }
        }
        binding.btnCreateCargos.isEnabled = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            //window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
}
