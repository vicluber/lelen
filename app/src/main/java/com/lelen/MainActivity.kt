package com.lelen

import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.lelen.Utils.CHATGPT_MODEL
import okhttp3.*
import java.util.Locale
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    lateinit var etQuestion: TextInputEditText
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var selectedLanguage: String
    private lateinit var languageDisplayNameInEnglish: String
    private val chatService = ChatService(client)
    private lateinit var chatRequestConversation : ChatRequest
    private lateinit var chatRequestTranslationAndAlternative : ChatRequest
    private lateinit var chatRequestTranslation : ChatRequest
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedLanguage = intent.getStringExtra("selectedValue") ?: "Default Value"
        val locale = Locale.getDefault()
        languageDisplayNameInEnglish = Locale(locale.language, locale.country).getDisplayLanguage(Locale.ENGLISH)
        chatRequestConversation = ChatRequest(
            messages = mutableListOf(
                Message(role = "system", content = "You are gonna act as a ${selectedLanguage} teacher who chats with me just for me to learn. So it doesn't matter which language I use you will respond accordingly but in ${selectedLanguage}, for example, if I ask you how to say something in ${selectedLanguage} always reply in ${selectedLanguage} don't use the language I use, always ${selectedLanguage}. No matter what I say, never change the language, ALWAYS speak ${selectedLanguage} with me. If I ask you to speak another language just tell me that is for my good that you will just reply in ${selectedLanguage}.\n I'm also not good in conversation making so if I don't ask anything you will propose easy topics of conversations."),
                Message(role = "assistant", content = "Of course")
            ),
            model = CHATGPT_MODEL
        )
        chatRequestTranslationAndAlternative = ChatRequest(
            messages = mutableListOf(
                Message(role = "system", content = "If I say something in ${selectedLanguage} you will make a correction if it needs one and offer an alternative in this structure 'Correction: the correct phrase - Alternative: an alternative phrase for the same meaning' and if I say something in any other language than ${selectedLanguage} you will translate that to ${selectedLanguage} but only if I say something in other language than ${selectedLanguage}"),
                Message(role = "assistant", content = "Of course"),
                Message(role = "user", content = "Just a placeholder")
            ),
            model = CHATGPT_MODEL
        )
        chatRequestTranslation = ChatRequest(
            messages = mutableListOf(
                Message(role = "system", content = "You are gonna translate to ${languageDisplayNameInEnglish} what I say in this structure 'the translated phrase' so nothing more than the translation"),
                Message(role = "assistant", content = "Of course, go ahead and tell me what you'd like to translate into ${selectedLanguage}."),
                Message(role = "user", content = "Just a placeholder")
            ),
            model = CHATGPT_MODEL
        )


        setContentView(R.layout.activity_main)

        etQuestion=findViewById<TextInputEditText>(R.id.etQuestion)

        etQuestion.setOnEditorActionListener(TextView.OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val question = etQuestion.text.toString().trim()
                if(question.isNotEmpty()){
                    val userMessage = Message(role = "user", content = question)
                    addMessageToConversationThread(userMessage)
                    //chatRequestTranslationAndAlternative()
                    chatRequestTranslationAndAlternative.messages[2] = Message(role = "user", content = question)
                    chatService.getResponse(chatRequestTranslationAndAlternative) { translationAndAlternativeResult ->
                        translationAndAlternativeResult.onSuccess { response ->
                            val translation = Message(role = "user", content = response.content)
                            drawMessage(userMessage, translation)
                            Log.v("OpenAI response", response.toString())
                        }
                        translationAndAlternativeResult.onFailure { exception ->
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Wait a minute", Toast.LENGTH_LONG).show()
                            }
                        }


                        chatService.getResponse(chatRequestConversation) { conversationResult ->
                            runOnUiThread {
                                conversationResult.onSuccess { conversationResponse ->
                                    val assistantMessage = Message(role = conversationResponse.role, content = conversationResponse.content)
                                    chatRequestTranslation.messages[2] = (Message(role = "user", content = conversationResponse.content))
                                    addMessageToConversationThread(assistantMessage)
                                    chatService.getResponse(chatRequestTranslation) { result ->
                                        result.onSuccess { translateResponse ->
                                            val translated = Message(role = "user", content = translateResponse.content)
                                            drawMessage(assistantMessage, translated)
                                            Log.v("OpenAI response", translateResponse.toString())
                                        }
                                        result.onFailure { exception ->
                                            runOnUiThread {
                                                Toast.makeText(this@MainActivity, "Wait a minute", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                                conversationResult.onFailure { exception ->
                                    Toast.makeText(this@MainActivity, "Wait a minute", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }else{
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "The minute is not over yet.", Toast.LENGTH_LONG).show()
                    }
                }
                return@OnEditorActionListener true
            }
            false
        })


    }
    private fun getSpeechResponse(text: String, filePath: String, callback: (Result<String>) -> Unit) {
        chatService.getSpeechResponse(text, filePath) { result ->
            runOnUiThread {
                result.onSuccess {
                    callback(Result.success(it))
                }
                result.onFailure { exception ->
                    callback(Result.failure(exception))
                }
            }
        }
    }

    private fun playAudio(filePath: String) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare() // Prepare the MediaPlayer asynchronously (use prepareAsync() for streaming)
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release() // Release the MediaPlayer if it has been initialized
        }
    }

    fun addMessageToConversationThread(message: Message){
        // Adding the message to the chatRequest for further API request
        val toAdd = chatRequestConversation.messages.add(message)
        // Wiping the input prompt text input
        etQuestion.setText("")
    }

    fun drawMessage(message: Message, translation: Message?) {
        // Adding message to UI
        runOnUiThread {
            // Create an ImageView
            val profileThumbnail = ImageView(this@MainActivity)

            // Set an image resource
            profileThumbnail.setImageResource(R.drawable.bot) // replace 'your_image_name' with your image's name without the extension

            // Optionally, set layout parameters for the button
            // imageButton.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT)

            // Set padding, if needed
            // imageButton.setPadding(16, 16, 16, 16) // values are in pixels



            // Create a new LinearLayout to contain the TextView and Icon
            val messageContainer = LinearLayout(this@MainActivity)
            messageContainer.orientation = LinearLayout.HORIZONTAL
            val container = LinearLayout(this@MainActivity)
            container.orientation = LinearLayout.HORIZONTAL

            // Create a new TextView to display the response
            val newTextView = TextView(this@MainActivity)

            // Set text
            newTextView.text = message.content

            // Set padding for spacing inside the text bubble
            val paddingDp = resources.getDimensionPixelSize(R.dimen.chat_message_padding)
            newTextView.setPadding(paddingDp, paddingDp, paddingDp, paddingDp)
            // Set text color
            newTextView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.chat_message_text_color))

            // Create an ImageButton
            val playButton = ImageView(this@MainActivity)
            // Set an image resource for the button
            playButton.setImageResource(R.drawable.ic_play)
            val translateButton = ImageView(this@MainActivity)
            translateButton.setImageResource(R.drawable.ic_translate)

             // Adding the TextView to the container

            container.addView(playButton)
            container.addView(translateButton)
            if (message.role == "assistant") {
                // Set message bubble background for assistant
                profileThumbnail.setPadding(0, 0, paddingDp, 0)
                messageContainer.addView(profileThumbnail)
                messageContainer.addView(newTextView)
                newTextView.setBackgroundResource(R.drawable.chat_message_assistant_background)
                playButton.setPadding(0, paddingDp, paddingDp, paddingDp)
                translateButton.setPadding(0, paddingDp, paddingDp, paddingDp)

                messageContainer.gravity = Gravity.START
                container.gravity = Gravity.START
            } else if (message.role == "user") {
                // Set message bubble background for user
                newTextView.setBackgroundResource(R.drawable.chat_message_user_background)
                messageContainer.addView(newTextView)
                playButton.setPadding(paddingDp, paddingDp, 0, paddingDp)
                translateButton.setPadding(paddingDp, paddingDp, 0, paddingDp)
                messageContainer.gravity = Gravity.END
                container.gravity = Gravity.END
            }

            // Add a click listener for the button, if needed
            playButton.setOnClickListener {
                val audioFilePath = "${externalCacheDir?.absolutePath}/speech.mp3"
                getSpeechResponse(message.content, audioFilePath) {
                    playAudio(audioFilePath)
                }
            }

            newTextView.setTag(R.id.message_id, message)
            // Set click listener for the TextView
            newTextView.setOnClickListener {
                if (translation != null) {
                    translate(it, translation)
                }
            }

            translateButton.setOnClickListener {
                if (translation != null) {
                    translate(newTextView, translation)
                }
            }

            // Add the messageContainer to llChatHistory
            val llChatHistory = findViewById<LinearLayout>(R.id.llChatHistory)
            llChatHistory.addView(messageContainer)
            llChatHistory.addView(container)
            llChatHistory.post {
                val scrollView = findViewById<ScrollView>(R.id.scrollView2)
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }


    fun translate(clickedView: View, translation: Message) {
        // Verifica si el TextView ya muestra el texto traducido
        val textView = clickedView as TextView
        val currentText = textView.text.toString()
        if (currentText == translation.content) {
            // Si el texto actual es la traducción, cambia al texto original
            val originalMessage = clickedView.getTag(R.id.message_id) as Message
            textView.text = originalMessage.content
        } else {
            // Si el texto actual no es la traducción, cambia al texto traducido
            textView.text = translation.content
        }
    }
}