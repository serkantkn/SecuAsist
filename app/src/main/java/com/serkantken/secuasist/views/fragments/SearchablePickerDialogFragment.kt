package com.serkantken.secuasist.views.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.serkantken.secuasist.R
import com.serkantken.secuasist.adapters.SearchablePickerAdapter
import com.serkantken.secuasist.databinding.DialogSearchablePickerBinding
import com.serkantken.secuasist.models.SearchableItem
import java.io.Serializable

class SearchablePickerDialogFragment : DialogFragment() {

    private var _binding: DialogSearchablePickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchableAdapter: SearchablePickerAdapter
    private var originalItems: List<SearchableItem> = emptyList()
    private var itemLayoutType: SearchablePickerAdapter.ItemLayoutType = SearchablePickerAdapter.ItemLayoutType.COMPANY
    private var dialogTitle: String = "Seçim Yapın"
    private var listener: OnItemSelectedListener? = null

    interface OnItemSelectedListener {
        fun onItemSelected(item: SearchableItem, Canceled:Boolean)
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_ITEMS = "arg_items"
        private const val ARG_LAYOUT_TYPE = "arg_layout_type"

        fun newInstance(
            title: String,
            items: List<SearchableItem>,
            layoutType: SearchablePickerAdapter.ItemLayoutType
        ): SearchablePickerDialogFragment {
            val fragment = SearchablePickerDialogFragment()
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
                putSerializable(ARG_ITEMS, ArrayList(items))
                putSerializable(ARG_LAYOUT_TYPE, layoutType)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.BlurredDialog)

        arguments?.let {
            dialogTitle = it.getString(ARG_TITLE, "Seçim Yapın")
            originalItems = (it.getSerializable(ARG_ITEMS) as? ArrayList<SearchableItem>) ?: emptyList()
            itemLayoutType = it.getSerializable(ARG_LAYOUT_TYPE) as? SearchablePickerAdapter.ItemLayoutType
                ?: SearchablePickerAdapter.ItemLayoutType.COMPANY
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSearchablePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBlurView()
        binding.windowTitle.text = dialogTitle

        setupSearch()
        setupButtons() // Sadece cancel butonu için
        setupRecyclerView()
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

    private fun setupRecyclerView() {
        searchableAdapter = SearchablePickerAdapter(originalItems, itemLayoutType) { item ->
            // Item tıklandığında listener'ı çağır ve diyaloğu kapat
            listener?.onItemSelected(item, false)
            dismiss()
        }

        binding.rvOptions.apply {
            layoutManager = when (itemLayoutType) {
                SearchablePickerAdapter.ItemLayoutType.SELECTED_VILLA -> GridLayoutManager(context, 5)
                SearchablePickerAdapter.ItemLayoutType.COMPANY -> LinearLayoutManager(context)
            }
            adapter = searchableAdapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchableAdapter.filter(s?.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupButtons() {
        binding.btnClose.setOnClickListener {
            listener?.onItemSelected(SearchableItemPlaceholder(),true)
            dismiss()
        }
    }

    fun setOnItemSelectedListener(listener: OnItemSelectedListener) {
        this.listener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class SearchableItemPlaceholder : SearchableItem, Serializable {
        override fun getDisplayId(): String = ""
        override fun getDisplayName(): String = ""
    }
}