package com.hunttv.streamtv.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hunttv.streamtv.R
import com.hunttv.streamtv.activities.PlaylistChannelsActivity
import com.hunttv.streamtv.adapters.PlaylistAdapter
import com.hunttv.streamtv.models.PlaylistEntry
import com.hunttv.streamtv.utils.PlaylistManager

class PlaylistsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: View
    private val playlists = mutableListOf<PlaylistEntry>()
    private lateinit var adapter: PlaylistAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_playlists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recycler_playlists)
        tvEmpty = view.findViewById(R.id.tv_empty)
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add_playlist)

        adapter = PlaylistAdapter(
            playlists,
            onOpen = { playlist ->
                val intent = Intent(requireContext(), PlaylistChannelsActivity::class.java).apply {
                    putExtra(PlaylistChannelsActivity.EXTRA_PLAYLIST_ID, playlist.id)
                    putExtra(PlaylistChannelsActivity.EXTRA_PLAYLIST_NAME, playlist.name)
                    putExtra(PlaylistChannelsActivity.EXTRA_PLAYLIST_URL, playlist.url)
                }
                startActivity(intent)
            },
            onDelete = { playlist ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Remove Playlist")
                    .setMessage("Remove \"${playlist.name}\"?")
                    .setPositiveButton("Remove") { _, _ ->
                        PlaylistManager.removePlaylist(requireContext(), playlist.id)
                        loadPlaylists()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        fab.setOnClickListener { showAddPlaylistDialog() }

        loadPlaylists()
    }

    override fun onResume() {
        super.onResume()
        loadPlaylists()
    }

    private fun loadPlaylists() {
        playlists.clear()
        playlists.addAll(PlaylistManager.getPlaylists(requireContext()))
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddPlaylistDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_playlist, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_playlist_name)
        val etUrl = dialogView.findViewById<EditText>(R.id.et_playlist_url)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Playlist")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                } else {
                    PlaylistManager.addPlaylist(requireContext(), name, url)
                    loadPlaylists()
                    Toast.makeText(requireContext(), "Playlist added!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
