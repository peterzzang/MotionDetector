package com.example.motiondetector

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.motiondetector.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val adapter = DetectionLogAdapter(showDate = true)
    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    private enum class TypeFilter { ALL, PERSON, VEHICLE }
    private enum class RangeFilter { ALL, TODAY, WEEK }

    private var typeFilter = TypeFilter.ALL
    private var rangeFilter = RangeFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = adapter

        binding.backBtn.setOnClickListener { finish() }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch()
            }
        })

        binding.filterAll.setOnClickListener { setTypeFilter(TypeFilter.ALL) }
        binding.filterPerson.setOnClickListener { setTypeFilter(TypeFilter.PERSON) }
        binding.filterVehicle.setOnClickListener { setTypeFilter(TypeFilter.VEHICLE) }

        binding.rangeAll.setOnClickListener { setRangeFilter(RangeFilter.ALL) }
        binding.rangeToday.setOnClickListener { setRangeFilter(RangeFilter.TODAY) }
        binding.rangeWeek.setOnClickListener { setRangeFilter(RangeFilter.WEEK) }

        binding.deleteAllBtn.setOnClickListener { confirmDeleteAll() }

        applyChipStyles()
        performSearch()
    }

    private fun setTypeFilter(filter: TypeFilter) {
        typeFilter = filter
        applyChipStyles()
        performSearch()
    }

    private fun setRangeFilter(filter: RangeFilter) {
        rangeFilter = filter
        applyChipStyles()
        performSearch()
    }

    private fun applyChipStyles() {
        highlight(binding.filterAll, typeFilter == TypeFilter.ALL)
        highlight(binding.filterPerson, typeFilter == TypeFilter.PERSON)
        highlight(binding.filterVehicle, typeFilter == TypeFilter.VEHICLE)

        highlight(binding.rangeAll, rangeFilter == RangeFilter.ALL)
        highlight(binding.rangeToday, rangeFilter == RangeFilter.TODAY)
        highlight(binding.rangeWeek, rangeFilter == RangeFilter.WEEK)
    }

    private fun highlight(view: android.widget.TextView, selected: Boolean) {
        if (selected) {
            view.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green)
            view.setTextColor(ContextCompat.getColor(this, R.color.bg))
        } else {
            view.backgroundTintList = ContextCompat.getColorStateList(this, R.color.panel2)
            view.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun rangeMillis(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (rangeFilter) {
            RangeFilter.ALL -> 0L to Long.MAX_VALUE
            RangeFilter.TODAY -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            RangeFilter.WEEK -> (now - 7L * 24 * 60 * 60 * 1000) to now
        }
    }

    private fun performSearch() {
        val keyword = binding.searchInput.text?.toString()?.trim().orEmpty()
        val typeArg = when (typeFilter) {
            TypeFilter.ALL -> null
            TypeFilter.PERSON -> DetectionType.PERSON.name
            TypeFilter.VEHICLE -> DetectionType.VEHICLE.name
        }
        val (from, to) = rangeMillis()

        lifecycleScope.launch(Dispatchers.IO) {
            val results = db.detectionDao().search(keyword, typeArg, from, to)
            withContext(Dispatchers.Main) {
                adapter.submitList(results)
                binding.resultSummary.text = "${results.size}건"
                binding.historyEmptyText.visibility =
                    if (results.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                binding.historyList.visibility =
                    if (results.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            }
        }
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle("전체 기록 삭제")
            .setMessage("저장된 모든 감지 기록을 삭제할까요? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.detectionDao().clearAll()
                    withContext(Dispatchers.Main) { performSearch() }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
