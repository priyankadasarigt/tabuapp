package com.hunttv.streamtv.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hunttv.streamtv.R
import com.hunttv.streamtv.models.PlaylistEntry

class PlaylistAdapter(
    private val playlists: List<PlaylistEntry>,
    private val onOpen: (PlaylistEntry) -> Unit,
    private val onDelete: (PlaylistEntry) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    class PlaylistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_playlist_name)
        val tvUrl: TextView = view.findViewById(R.id.tv_playlist_url)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_playlist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.tvName.text = playlist.name
        holder.tvUrl.text = playlist.url

        holder.itemView.setOnClickListener { onOpen(playlist) }
        holder.btnDelete.setOnClickListener { onDelete(playlist) }
    }

    override fun getItemCount() = playlists.size
}
