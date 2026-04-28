package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safefnow2.R
import com.example.safefnow2.util.SessionManager
import com.example.safefnow2.ui.common.UiState
import com.example.safefnow2.ui.history.HistoryItemUi
import com.example.safefnow2.ui.history.HistoryViewModel

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvError: TextView
    private val items = mutableListOf<HistoryItemUi>()
    private val vm: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvHistory = findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)

        pbLoading = findViewById(R.id.pbHistoryLoading)
        tvEmpty = findViewById(R.id.tvHistoryEmpty)
        tvError = findViewById(R.id.tvHistoryError)

        val userId = SessionManager.getCurrentUserId(this).orEmpty()
        vm.uiState(userId).observe(this) { state ->
            when (state) {
                is UiState.Loading -> {
                    pbLoading.visibility = View.VISIBLE
                    tvEmpty.visibility = View.GONE
                    tvError.visibility = View.GONE
                    rvHistory.visibility = View.GONE
                }
                is UiState.Empty -> {
                    pbLoading.visibility = View.GONE
                    rvHistory.visibility = View.GONE
                    tvError.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                }
                is UiState.Error -> {
                    pbLoading.visibility = View.GONE
                    rvHistory.visibility = View.GONE
                    tvEmpty.visibility = View.GONE
                    tvError.visibility = View.VISIBLE
                    tvError.text = state.message
                }
                is UiState.Success -> {
                    pbLoading.visibility = View.GONE
                    tvEmpty.visibility = View.GONE
                    tvError.visibility = View.GONE
                    rvHistory.visibility = View.VISIBLE
                    items.clear()
                    items.addAll(state.data)
                    rvHistory.adapter = HistoryAdapter(items)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    inner class HistoryAdapter(private val items: List<HistoryItemUi>) :
        RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvHistoryUserName)
            val tvType: TextView = view.findViewById(R.id.tvHistoryType)
            val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_alert, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvType.text = item.type
            holder.tvDate.text = item.dateText

            holder.itemView.setOnClickListener {
                val meId = SessionManager.getCurrentUserId(this@HistoryActivity).orEmpty()
                val intent = Intent(this@HistoryActivity, AlertDetailActivity::class.java).apply {
                    putExtra(AlertDetailActivity.EXTRA_ALERT_ID, item.alertId)
                    putExtra(AlertDetailActivity.EXTRA_USER_ID, meId)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
    }
}
