package com.lelen

import com.lelen.Utils.BASE_URL
import com.lelen.Utils.OPENAI_API_KEY
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.*
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class ChatService(private val client: OkHttpClient) {
    fun getResponse(chatRequest: ChatRequest, callback: (Result<Message>) -> Unit) {
        val jsonChatRequest = Json.encodeToString(chatRequest)
        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(jsonChatRequest.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (!response.isSuccessful) {
                    callback(Result.failure(IOException("Unexpected code $response")))
                    return
                }
                response.body?.string()?.let { body ->
                    try {
                        val jsonObject = JSONObject(body)
                        val jsonArray: JSONArray = jsonObject.getJSONArray("choices")
                        val textResult = jsonArray.getJSONObject(0).getJSONObject("message").getString("content")
                        val roleResult = jsonArray.getJSONObject(0).getJSONObject("message").getString("role")
                        val message = Message(role = roleResult, content = textResult)
                        callback(Result.success(message))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                } ?: run {
                    callback(Result.failure(IOException("Empty response")))
                }
            }
        })
    }
    fun getSpeechResponse(audioText: String, audioFilePath: String, callback: (Result<String>) -> Unit) {
        val jsonPayload = JSONObject().apply {
            put("model", "tts-1")
            put("input", audioText)
            put("voice", "echo")
        }.toString()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(jsonPayload.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback(Result.failure(IOException("Unexpected code $response")))
                    return
                }
                try {
                    response.body?.byteStream()?.use { inputStream ->
                        File(audioFilePath).outputStream().use { fileOutputStream ->
                            inputStream.copyTo(fileOutputStream)
                        }
                    }
                    callback(Result.success("Audio file saved successfully at $audioFilePath"))
                } catch (e: Exception) {
                    callback(Result.failure(e))
                } finally {
                    response.close()
                }
            }
        })
    }
}
