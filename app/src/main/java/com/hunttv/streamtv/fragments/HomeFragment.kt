package com.hunttv.streamtv.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.hunttv.streamtv.R
import com.hunttv.streamtv.activities.PlayerActivity
import com.hunttv.streamtv.adapters.StreamAdapter
import com.hunttv.streamtv.models.StreamItem
import com.hunttv.streamtv.network.RetrofitClient
import com.hunttv.streamtv.utils.AppConfig
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvError: TextView
    private lateinit var tvPoweredBy: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        tvError = view.findViewById(R.id.tv_error)
        tvPoweredBy = view.findViewById(R.id.tv_powered_by)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        swipeRefresh.setOnRefreshListener { loadStreams() }
        swipeRefresh.setColorSchemeResources(R.color.accent_color)

        loadStreams()
    }

    private fun loadStreams() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                tvError.visibility = View.GONE

                val response = RetrofitClient.api.getStreams(
                    apiKey = AppConfig.API_KEY,
                    userAgent = AppConfig.USER_AGENT
                )

                tvPoweredBy.text = response.poweredBy

                val visible = response.streams.filter { !it.isHidden }

                if (visible.isEmpty()) {
                    showError("No streams available at the moment")
                } else {
                    val adapter = StreamAdapter(visible) { stream -> openPlayer(stream) }
                    recyclerView.adapter = adapter
                }

                showLoading(false)

            } catch (e: Exception) {
                showLoading(false)
                showError("Failed to load streams")
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openPlayer(stream: StreamItem) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, stream.streamUrl)
            putExtra(PlayerActivity.EXTRA_STREAM_TITLE, stream.title)
        }
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        swipeRefresh.isRefreshing = false
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
}
