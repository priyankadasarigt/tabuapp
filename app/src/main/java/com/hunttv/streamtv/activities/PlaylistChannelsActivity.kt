package com.hunttv.streamtv.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hunttv.streamtv.R
import com.hunttv.streamtv.adapters.ChannelGridAdapter
import com.hunttv.streamtv.models.M3uChannel
import com.hunttv.streamtv.utils.M3uParser
import com.hunttv.streamtv.utils.PlaylistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistChannelsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlist_id"
        const val EXTRA_PLAYLIST_NAME = "playlist_name"
        const val EXTRA_PLAYLIST_URL = "playlist_url"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvTitle: TextView
    private lateinit var searchView: SearchView
    private lateinit var btnFavorites: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnBack: ImageButton

    private var playlistId = ""
    private var playlistUrl = ""
    private var allChannels = listOf<M3uChannel>()
    private var showingFavorites = false
    private lateinit var adapter: ChannelGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_channels)

        playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: ""
        val playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Channels"
        playlistUrl = intent.getStringExtra(EXTRA_PLAYLIST_URL) ?: ""

        recyclerView = findViewById(R.id.recycler_channels)
        progressBar = findViewById(R.id.progress_bar)
        tvError = findViewById(R.id.tv_error)
        tvTitle = findViewById(R.id.tv_title)
        searchView = findViewById(R.id.search_view)
        btnFavorites = findViewById(R.id.btn_favorites)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnBack = findViewById(R.id.btn_back)

        tvTitle.text = playlistName

        recyclerView.layoutManager = GridLayoutManager(this, 3)

        adapter = ChannelGridAdapter(
            channels = mutableListOf(),
            onChannelClick = { channel -> openPlayer(channel) },
            onFavoriteToggle = { channel ->
                val added = PlaylistManager.toggleFavorite(this, playlistId, channel.name)
                val msg = if (added) "Added to favorites" else "Removed from favorites"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (showingFavorites && !added) {
                    refreshDisplay()
                }
            }
        )
        recyclerView.adapter = adapter

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean {
                filterChannels(q ?: "")
                return true
            }
        })

        btnFavorites.setOnClickListener {
            showingFavorites = !showingFavorites
            btnFavorites.setImageResource(
                if (showingFavorites) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            refreshDisplay()
        }

        btnRefresh.setOnClickListener { loadChannels() }
        btnBack.setOnClickListener { finish() }

        loadChannels()
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                tvError.visibility = View.GONE

                val channels = withContext(Dispatchers.IO) {
                    M3uParser.parseFromUrl(playlistUrl)
                }

                allChannels = channels

                if (channels.isEmpty()) {
                    showError("No channels found in this playlist")
                } else {
                    val favs = PlaylistManager.getFavorites(this@PlaylistChannelsActivity, playlistId)
                    allChannels.forEach { it.isFavorite = favs.contains(it.name) }
                    refreshDisplay()
                }
                showLoading(false)

            } catch (e: Exception) {
                showLoading(false)
                showError("Failed to load: ${e.message}")
            }
        }
    }

    private fun refreshDisplay() {
        val query = searchView.query?.toString() ?: ""
        filterChannels(query)
    }

    private fun filterChannels(query: String) {
        val favs = PlaylistManager.getFavorites(this, playlistId)
        allChannels.forEach { it.isFavorite = favs.contains(it.name) }

        var filtered = if (showingFavorites) {
            allChannels.filter { it.isFavorite }
        } else {
            allChannels.toList()
        }

        if (query.isNotEmpty()) {
            filtered = filtered.filter { it.name.contains(query, ignoreCase = true) }
        }

        adapter.updateChannels(filtered)
    }

    private fun openPlayer(channel: M3uChannel) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, channel.streamUrl)
            putExtra(PlayerActivity.EXTRA_STREAM_TITLE, channel.name)
            putExtra(PlayerActivity.EXTRA_IS_PLAYLIST, true)  // ← THIS WAS MISSING
        }
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
}
