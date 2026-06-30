package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object AuddClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class SongResult(val title: String, val artist: String, val artworkUrl: String?)

    suspend fun identifySong(audioFile: File): SongResult? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_token", "test")
                .addFormDataPart("return", "apple_music,spotify")
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.audd.io/")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("AuddClient", "API request failed: ${response.code} ${response.message}")
                return@withContext null
            }

            val responseString = response.body?.string() ?: return@withContext null
            Log.d("AuddClient", "Raw response: $responseString")

            val jsonResponse = JSONObject(responseString)
            if (jsonResponse.optString("status") == "success") {
                val resultObj = jsonResponse.optJSONObject("result")
                if (resultObj != null) {
                    val title = resultObj.optString("title", "Unknown")
                    val artist = resultObj.optString("artist", "Unknown")

                    var artworkUrl: String? = null
                    val spotify = resultObj.optJSONObject("spotify")
                    if (spotify != null) {
                        val album = spotify.optJSONObject("album")
                        if (album != null) {
                            val images = album.optJSONArray("images")
                            if (images != null && images.length() > 0) {
                                artworkUrl = images.optJSONObject(0)?.optString("url")
                            }
                        }
                    }

                    if (artworkUrl == null) {
                        val appleMusic = resultObj.optJSONObject("apple_music")
                        if (appleMusic != null) {
                            val artwork = appleMusic.optJSONObject("artwork")
                            if (artwork != null) {
                                val urlPattern = artwork.optString("url")
                                if (urlPattern.isNotEmpty()) {
                                    artworkUrl = urlPattern.replace("{w}", "500").replace("{h}", "500")
                                }
                            }
                        }
                    }

                    val isInvalidTitle = title.isBlank() || 
                        title.trim().equals("Unknown", ignoreCase = true) ||
                        title.trim().equals("null", ignoreCase = true) ||
                        title.trim().equals("undefined", ignoreCase = true) ||
                        title.trim().equals("none", ignoreCase = true) ||
                        title.trim().equals("n/a", ignoreCase = true)

                    val isInvalidArtist = artist.isBlank() || 
                        artist.trim().equals("Unknown", ignoreCase = true) ||
                        artist.trim().equals("null", ignoreCase = true) ||
                        artist.trim().equals("undefined", ignoreCase = true) ||
                        artist.trim().equals("none", ignoreCase = true) ||
                        artist.trim().equals("n/a", ignoreCase = true)

                    if (!isInvalidTitle && !isInvalidArtist) {
                        return@withContext SongResult(title, artist, artworkUrl)
                    }
                }
            } else {
                Log.e("AuddClient", "Error from AudD: ${jsonResponse.optJSONObject("error")?.optString("error_message")}")
            }
        } catch (e: Exception) {
            Log.e("AuddClient", "Exception during song identification: ${e.message}", e)
        }
        return@withContext null
    }
}
