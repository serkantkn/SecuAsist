package com.serkantken.secuasist.views.fragments

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ArrayAdapter
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.serkantken.secuasist.R
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.FragmentAddEditVillaDialogBinding
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.activities.MainActivity
import kotlinx.coroutines.launch

class AddEditVillaDialogFragment : DialogFragment() {
    private var _binding: FragmentAddEditVillaDialogBinding? = null
    private val binding get() = _binding!!
    interface DialogListener {
        fun onVillaSaved()
    }
    var listener: DialogListener? = null
    private var villaToEdit: Villa? = null

    companion object {
        private const val ARG_VILLA = "villa_to_edit"

        fun newInstance(villa: Villa?): AddEditVillaDialogFragment {
            val fragment = AddEditVillaDialogFragment()
            val args = Bundle()
            args.putSerializable(ARG_VILLA, villa)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.BlurredDialog)
        arguments?.let {
            villaToEdit = it.getSerializable(ARG_VILLA) as Villa?
        }
    }

    private fun setupBlurView() {
        val radius = 20f
        val decorView = requireActivity().window.decorView
        val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)
        val windowBackground = decorView.background
        binding.root.setupWith(rootView)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(radius)
            .setBlurAutoUpdate(true)
        binding.root.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.root.clipToOutline = true
    }

    @OptIn(ExperimentalBadgeUtils::class)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FragmentAddEditVillaDialogBinding.inflate(layoutInflater)
        val isEditMode = villaToEdit != null
        val appDatabase = AppDatabase.getDatabase(requireContext())
        setupBlurView()

        val streets = resources.getStringArray(R.array.street_names)
        val streetArrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, streets)
        binding.actvVillaStreet.setAdapter(streetArrayAdapter)

        if (isEditMode) {
            binding.windowTitle.text = "Villa Düzenle"
            binding.etVillaNo.setText(villaToEdit?.villaNo?.toString())
            binding.etVillaNotes.setText(villaToEdit?.villaNotes)
            binding.actvVillaStreet.setText(villaToEdit?.villaStreet, false)
            binding.etVillaNavigationA.setText(villaToEdit?.villaNavigationA)
            binding.etVillaNavigationB.setText(villaToEdit?.villaNavigationB)
            binding.chipIsUnderConstruction.isChecked = villaToEdit?.isVillaUnderConstruction == 1
            binding.chipIsSpecial.isChecked = villaToEdit?.isVillaSpecial == 1
            binding.chipIsRental.isChecked = villaToEdit?.isVillaRental == 1
            binding.chipIsCallFromHome.isChecked = villaToEdit?.isVillaCallFromHome == 1
            binding.chipIsCallForCargo.isChecked = villaToEdit?.isVillaCallForCargo == 1
            binding.chipIsEmpty.isChecked = villaToEdit?.isVillaEmpty == 1
            binding.etVillaNo.isEnabled = false
        } else {
            binding.windowTitle.text = "Villa Ekle"
        }

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        binding.btnSave.setOnClickListener {
            val villaNo = binding.etVillaNo.text.toString().toIntOrNull()
            if (villaNo == null) {
                binding.etVillaNo.error = "Geçerli bir numara girin."
                return@setOnClickListener
            }

            val selectedStreet = binding.actvVillaStreet.text.toString()
            if (selectedStreet.isBlank()) {
                binding.tilVillaStreet.error = "Sokak seçimi zorunludur."
                return@setOnClickListener
            }

            val villa = Villa(
                villaId = villaToEdit?.villaId ?: 0,
                villaNo = villaNo,
                villaNotes = binding.etVillaNotes.text.toString().takeIf { it.isNotBlank() },
                villaStreet = selectedStreet,
                villaNavigationA = binding.etVillaNavigationA.text.toString().takeIf { it.isNotBlank() },
                villaNavigationB = binding.etVillaNavigationB.text.toString().takeIf { it.isNotBlank() },
                isVillaUnderConstruction = if (binding.chipIsUnderConstruction.isChecked) 1 else 0,
                isVillaSpecial = if (binding.chipIsSpecial.isChecked) 1 else 0,
                isVillaRental = if (binding.chipIsRental.isChecked) 1 else 0,
                isVillaCallFromHome = if (binding.chipIsCallFromHome.isChecked) 1 else 0,
                isVillaCallForCargo = if (binding.chipIsCallForCargo.isChecked) 1 else 0,
                isVillaEmpty = if (binding.chipIsEmpty.isChecked) 1 else 0
            )

            lifecycleScope.launch {
                if (isEditMode) {
                    appDatabase.villaDao().update(villa)
                    (requireActivity().application as SecuAsistApplication).sendUpsert(villa)
                } else {
                    val existingVilla = appDatabase.villaDao().getVillaByNo(villa.villaNo)
                    if (existingVilla == null) {
                        val newId = appDatabase.villaDao().insert(villa)
                        val newVillaWithId = villa.copy(villaId = newId.toInt())
                        (requireActivity().application as SecuAsistApplication).sendUpsert(newVillaWithId)
                    } else {
                        Tools(requireActivity()).createAlertDialog("Hata", "Bu villa numarası zaten mevcut.")
                    }
                }
                listener?.onVillaSaved()
                dismiss()
            }
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnVillaContacts.setOnClickListener {
            villaToEdit?.let {
                dismiss()
                (activity as? MainActivity)?.showManageVillaContactsDialog(it)
            }
        }

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return alertDialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Bellek sızıntılarını önlemek için.
    }
}