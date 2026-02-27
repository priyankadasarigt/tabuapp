package com.hunttv.streamtv.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.hunttv.streamtv.R
import com.hunttv.streamtv.models.StreamItem

/**
 * Adapter for displaying stream items in RecyclerView
 */
class StreamAdapter(
    private val streams: List<StreamItem>,
    private val onStreamClick: (StreamItem) -> Unit
) : RecyclerView.Adapter<StreamAdapter.StreamViewHolder>() {

    class StreamViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.card_stream)
        val titleTextView: TextView = view.findViewById(R.id.tv_stream_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stream, parent, false)
        return StreamViewHolder(view)
    }

    override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
        val stream = streams[position]
        holder.titleTextView.text = stream.title
        
        holder.cardView.setOnClickListener {
            onStreamClick(stream)
        }
    }

    override fun getItemCount() = streams.size
}
