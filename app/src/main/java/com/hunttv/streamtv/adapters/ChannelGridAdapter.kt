package com.hunttv.streamtv.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.hunttv.streamtv.R
import com.hunttv.streamtv.models.M3uChannel

class ChannelGridAdapter(
    private val channels: MutableList<M3uChannel>,
    private val onChannelClick: (M3uChannel) -> Unit,
    private val onFavoriteToggle: (M3uChannel) -> Unit
) : RecyclerView.Adapter<ChannelGridAdapter.ChannelViewHolder>() {

    class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_channel)
        val logo: ImageView = view.findViewById(R.id.iv_channel_logo)
        val name: TextView = view.findViewById(R.id.tv_channel_name)
        val favIndicator: View = view.findViewById(R.id.view_fav_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel_grid, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]

        holder.name.text = channel.name
        holder.favIndicator.visibility = if (channel.isFavorite) View.VISIBLE else View.GONE

        // Load logo with Glide
        if (!channel.logoUrl.isNullOrBlank()) {
            Glide.with(holder.logo.context)
                .load(channel.logoUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(holder.logo)
        } else {
            holder.logo.setImageResource(R.drawable.ic_channel_placeholder)
        }

        holder.card.setOnClickListener { onChannelClick(channel) }

        holder.card.setOnLongClickListener {
            onFavoriteToggle(channel)
            true
        }
    }

    override fun getItemCount() = channels.size

    fun updateChannels(newList: List<M3uChannel>) {
        channels.clear()
        channels.addAll(newList)
        notifyDataSetChanged()
    }
}
