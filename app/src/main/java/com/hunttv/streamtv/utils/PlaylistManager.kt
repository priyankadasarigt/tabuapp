package com.hunttv.streamtv.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hunttv.streamtv.models.PlaylistEntry
import java.util.UUID

object PlaylistManager {
    private const val PREF_NAME = "playlists_prefs"
    private const val KEY_PLAYLISTS = "playlists"
    private const val KEY_FAVORITES_PREFIX = "favorites_"

    private val gson = Gson()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ─── Playlists ────────────────────────────────────────────────────────────

    fun getPlaylists(ctx: Context): List<PlaylistEntry> {
        val json = prefs(ctx).getString(KEY_PLAYLISTS, null) ?: return emptyList()
        val type = object : TypeToken<List<PlaylistEntry>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addPlaylist(ctx: Context, name: String, url: String): PlaylistEntry {
        val entry = PlaylistEntry(
            id = UUID.randomUUID().toString(),
            name = name,
            url = url.trim()
        )
        val list = getPlaylists(ctx).toMutableList()
        list.add(entry)
        savePlaylists(ctx, list)
        return entry
    }

    fun removePlaylist(ctx: Context, id: String) {
        val list = getPlaylists(ctx).filter { it.id != id }
        savePlaylists(ctx, list)
        // Also remove its favorites
        prefs(ctx).edit().remove(KEY_FAVORITES_PREFIX + id).apply()
    }

    private fun savePlaylists(ctx: Context, list: List<PlaylistEntry>) {
        prefs(ctx).edit().putString(KEY_PLAYLISTS, gson.toJson(list)).apply()
    }

    // ─── Favorites (per playlist) ─────────────────────────────────────────────

    fun getFavorites(ctx: Context, playlistId: String): Set<String> {
        val json = prefs(ctx).getString(KEY_FAVORITES_PREFIX + playlistId, null) ?: return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun toggleFavorite(ctx: Context, playlistId: String, channelName: String): Boolean {
        val favs = getFavorites(ctx, playlistId).toMutableSet()
        val added = if (favs.contains(channelName)) {
            favs.remove(channelName)
            false
        } else {
            favs.add(channelName)
            true
        }
        prefs(ctx).edit()
            .putString(KEY_FAVORITES_PREFIX + playlistId, gson.toJson(favs))
            .apply()
        return added
    }

    fun isFavorite(ctx: Context, playlistId: String, channelName: String): Boolean {
        return getFavorites(ctx, playlistId).contains(channelName)
    }
}
