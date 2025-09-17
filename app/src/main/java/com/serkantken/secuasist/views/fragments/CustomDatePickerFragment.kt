import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.fragment.app.DialogFragment
import com.serkantken.secuasist.R
import com.serkantken.secuasist.databinding.DialogDatePickerBinding
import java.util.Calendar

class CustomDatePickerFragment : DialogFragment() {

    private var _binding: DialogDatePickerBinding? = null
    private val binding get() = _binding!!

    // Aktivite ile iletişim kurmak için bir listener arayüzü
    interface OnDateSelectedListener {
        fun onDateSelected(calendar: Calendar)
    }
    private var listener: OnDateSelectedListener? = null

    companion object {
        private const val ARG_INITIAL_DATE = "initial_date"

        fun newInstance(initialDate: Calendar): CustomDatePickerFragment {
            val fragment = CustomDatePickerFragment()
            val args = Bundle()
            args.putSerializable(ARG_INITIAL_DATE, initialDate)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Oluşturduğumuz şeffaf, çerçevesiz stili diyaloğa uyguluyoruz.
        setStyle(STYLE_NO_FRAME, R.style.BlurredDialog)
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

    fun setOnDateSelectedListener(listener: OnDateSelectedListener) {
        this.listener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogDatePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBlurView()

        val initialDate = arguments?.getSerializable(ARG_INITIAL_DATE) as? Calendar ?: Calendar.getInstance()

        // Yıl seçici ayarları
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        binding.pickerYear.minValue = currentYear - 10
        binding.pickerYear.maxValue = currentYear + 10
        binding.pickerYear.value = initialDate.get(Calendar.YEAR)

        // Ay seçici ayarları
        val months = arrayOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
        binding.pickerMonth.minValue = 0
        binding.pickerMonth.maxValue = 11
        binding.pickerMonth.displayedValues = months
        binding.pickerMonth.value = initialDate.get(Calendar.MONTH)

        // Gün seçici ayarları (Başlangıçta doğru ayarlanıyor)
        binding.pickerDay.minValue = 1
        binding.pickerDay.maxValue = initialDate.getActualMaximum(Calendar.DAY_OF_MONTH)
        binding.pickerDay.value = initialDate.get(Calendar.DAY_OF_MONTH)

        // YENİ: Ay veya yıl değiştiğinde günleri güncelleyecek listener'lar
        binding.pickerYear.setOnValueChangedListener { _, _, _ -> updateDayPicker() }
        binding.pickerMonth.setOnValueChangedListener { _, _, _ -> updateDayPicker() }

        // Buton dinleyicileri
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener {
            val selectedCalendar = Calendar.getInstance().apply {
                set(binding.pickerYear.value, binding.pickerMonth.value, binding.pickerDay.value)
            }
            listener?.onDateSelected(selectedCalendar)
            dismiss()
        }
    }

    private fun updateDayPicker() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, binding.pickerYear.value)
            set(Calendar.MONTH, binding.pickerMonth.value)
        }
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Önceki değeri sakla
        val previousValue = binding.pickerDay.value

        // **İŞTE DÜZELTME BURADA**
        // NumberPicker'ı kendini yenilemeye zorlamak için:
        // 1. Görünen değerleri temizle (önbelleği sıfırlar)
        binding.pickerDay.displayedValues = null
        // 2. Yeni maksimum değeri ayarla
        binding.pickerDay.maxValue = maxDay

        // 3. Değerin yeni aralıkta olduğundan emin ol.
        // Eğer önceki değer (örn: 31) yeni maksimumdan (örn: 28) büyükse,
        // değeri yeni maksimuma ayarla. Değilse, kullanıcının seçtiği değeri koru.
        if (previousValue > maxDay) {
            binding.pickerDay.value = maxDay
        } else {
            binding.pickerDay.value = previousValue
        }
    }

        override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}