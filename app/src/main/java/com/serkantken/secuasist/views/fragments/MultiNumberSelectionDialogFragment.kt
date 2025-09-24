package com.serkantken.secuasist.views.fragments // Kendi paket adınızı kullanın

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.DialogMultiNumberSelectionBinding
import com.serkantken.secuasist.databinding.ItemSelectPhoneNumberBinding
import com.serkantken.secuasist.models.ContactFromPhone

class MultiNumberSelectionDialogFragment : DialogFragment() {

    private var _binding: DialogMultiNumberSelectionBinding? = null
    private val binding get() = _binding!!

    private var contactsToSelectFrom: List<ContactFromPhone> = emptyList()
    private var listener: OnMultiNumberSelectionListener? = null

    // Seçilen numaraları tutmak için bir map (Rehber Contact ID -> Seçilen Telefon Numarası)
    private val selectedPhoneNumbersMap = mutableMapOf<String, String>()

    interface OnMultiNumberSelectionListener {
        fun onSelectionsCompleted(selectedContacts: Map<String, String>) // Key: phoneContactId, Value: selectedPhoneNumber
        fun onSelectionCancelled()
    }

    companion object {
        private const val ARG_CONTACTS_TO_SELECT = "contacts_to_select"

        fun newInstance(contacts: List<ContactFromPhone>): MultiNumberSelectionDialogFragment {
            val fragment = MultiNumberSelectionDialogFragment()
            val args = Bundle()
            args.putSerializable(ARG_CONTACTS_TO_SELECT, ArrayList(contacts))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            contactsToSelectFrom = (it.getSerializable(ARG_CONTACTS_TO_SELECT) as? ArrayList<ContactFromPhone>) ?: emptyList()
        }
        // Başlangıçta, her kişi için ilk numarayı seçili varsayalım (veya hiçbirini)
        contactsToSelectFrom.forEach { contact ->
            if (contact.phoneOptions.isNotEmpty()) {
                // Şimdilik hiçbirini seçili yapmayalım, kullanıcı seçsin.
                // selectedPhoneNumbersMap[contact.phoneContactId] = contact.phoneOptions.first().number
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogMultiNumberSelectionBinding.inflate(inflater, container, false)
        // DialogFragment'ın kendi başlığını ve butonlarını kullanmak yerine kendi layout'umuzu kullanacağız.
        // Bu yüzden onCreateDialog'u override edip AlertDialog.Builder kullanmak daha iyi olabilir
        // veya dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE) gibi bir şey yapabiliriz.
        // Şimdilik basit tutalım.
        return binding.root
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMultiNumberSelectionBinding.inflate(LayoutInflater.from(context))

        binding.tvDialogTitle.text = "Numara Seçimi (${contactsToSelectFrom.size} kişi)"

        val adapter = MultiNumberAdapter(contactsToSelectFrom) { contactId, selectedNumber ->
            selectedPhoneNumbersMap[contactId] = selectedNumber
            Log.d("MultiNumDialog", "Selected for $contactId: $selectedNumber")
            // Kaydet butonunu aktif/pasif yapma kontrolü eklenebilir
            // binding.btnSaveSelections.isEnabled = selectedPhoneNumbersMap.size == contactsToSelectFrom.size
        }
        binding.rvContactsWithMultipleNumbers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContactsWithMultipleNumbers.adapter = adapter

        // Başlangıçta kaydet butonunu pasif yap, tüm seçimler yapılınca aktifleşsin (opsiyonel)
        // binding.btnSaveSelections.isEnabled = false


        val dialog = AlertDialog.Builder(requireActivity())
            .setView(binding.root)
            // Butonları AlertDialog.Builder ile eklemek daha standarttır
            // Ancak layout'a eklediysek listener'larını burada set etmeliyiz.
            .create()

        // Layout'taki butonlara listener atama
        binding.btnSaveSelections.setOnClickListener {
            // Kontrol: Her kişi için bir numara seçilmiş mi?
            val allSelected = contactsToSelectFrom.all { selectedPhoneNumbersMap.containsKey(it.phoneContactId) }
            if (allSelected || contactsToSelectFrom.isEmpty()) { // Ya hepsi seçildi ya da liste boştu
                listener?.onSelectionsCompleted(selectedPhoneNumbersMap)
                dismiss()
            } else {
                // Kullanıcıya uyarı verilebilir.
                Log.w("MultiNumDialog", "Not all contacts have a selected number.")
                // Veya seçilmeyenler için bir varsayılan atanabilir ya da bu kişiler atlanabilir.
                // Şimdilik, sadece seçilenleri gönderelim.
                listener?.onSelectionsCompleted(selectedPhoneNumbersMap)
                dismiss()

            }
        }

        binding.btnCancelSelection.setOnClickListener {
            listener?.onSelectionCancelled()
            dismiss()
        }

        // Dialog'un dışına tıklanınca kapanmasını engellemek için:
        dialog.setCanceledOnTouchOutside(false)

        return dialog
    }


    fun setOnMultiNumberSelectionListener(listener: OnMultiNumberSelectionListener) {
        this.listener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- RecyclerView Adapter ---
    inner class MultiNumberAdapter(
        private val items: List<ContactFromPhone>,
        private val onNumberSelected: (contactId: String, selectedNumber: String) -> Unit
    ) : RecyclerView.Adapter<MultiNumberAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // ViewBinding için item_select_phone_number.xml layout'unu kullanacağız
            val itemBinding = ItemSelectPhoneNumberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contactItem = items[position]
            holder.bind(contactItem)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val itemBinding: ItemSelectPhoneNumberBinding) : RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(contact: ContactFromPhone) {
                itemBinding.tvContactName.text = contact.displayName
                itemBinding.rgPhoneOptions.removeAllViews() // Önceki RadioButton'ları temizle

                if (contact.phoneOptions.isEmpty()) {
                    itemBinding.tvNoOptionsMessage.visibility = View.VISIBLE
                    itemBinding.rgPhoneOptions.visibility = View.GONE
                } else {
                    itemBinding.tvNoOptionsMessage.visibility = View.GONE
                    itemBinding.rgPhoneOptions.visibility = View.VISIBLE

                    contact.phoneOptions.forEachIndexed { index, phoneOption ->
                        val radioButton = RadioButton(itemView.context)
                        radioButton.text = "${phoneOption.typeLabel}: ${phoneOption.number}"
                        radioButton.id = View.generateViewId() // Dinamik ID ata
                        itemBinding.rgPhoneOptions.addView(radioButton)

                        // Eğer bu numara daha önce seçilmişse, RadioButton'ı işaretli yap
                        if (selectedPhoneNumbersMap[contact.phoneContactId] == phoneOption.number) {
                            radioButton.isChecked = true
                        }
                    }

                    // RadioGroup için listener
                    itemBinding.rgPhoneOptions.setOnCheckedChangeListener { group, checkedId ->
                        val selectedRadioButton = group.findViewById<RadioButton>(checkedId)
                        if (selectedRadioButton != null) {
                            // Seçilen RadioButton'ın metninden telefon numarasını çıkarmamız gerek.
                            // Bu biraz karmaşık olabilir. Daha iyi bir yol, RadioButton'a tag olarak PhoneOption nesnesini atamak olabilir.
                            // Şimdilik, metinden ayıklamaya çalışalım (basit bir örnek)
                            val selectedText = selectedRadioButton.text.toString()
                            // Örnek: "Mobil: 05551234567" -> "05551234567"
                            val numberOnly = selectedText.substringAfterLast(":").trim()
                            onNumberSelected(contact.phoneContactId, numberOnly)
                        }
                    }
                }
            }
        }
    }
}