package com.lelen

import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.lelen.Utils.CHATGPT_MODEL
import okhttp3.*
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
    private val chatService = ChatService(client)
    private lateinit var chatRequestConversation : ChatRequest
    private lateinit var chatRequestTranslationAndAlternative : ChatRequest
    private lateinit var chatRequestTranslation : ChatRequest
    private var timeRemaining: Long = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedLanguage = intent.getStringExtra("selectedValue") ?: "Default Value"
        chatRequestConversation = ChatRequest(
            messages = mutableListOf(
                Message(role = "system", content = "You are gonna act as a ${selectedLanguage} teacher who chats with me just for me to learn. So it doesn't matter which language I use you will respond accordingly but in ${selectedLanguage}, for example, if I ask you how to say something in ${selectedLanguage} always reply in ${selectedLanguage} don't use the language I use, always ${selectedLanguage}. No matter what I say, never change the language, ALWAYS speak ${selectedLanguage} with me. If I ask you to speak another language just tell me that is for my good that you will just reply in ${selectedLanguage}.\n"),
                Message(role = "assistant", content = "Of course")
            ),
            model = CHATGPT_MODEL
        )
        chatRequestTranslationAndAlternative = ChatRequest(
            messages = mutableListOf(
                //Message(role = "system", content = "You are gonna translate to ${selectedLanguage} what I say and if is not a colloquial phrase or people in a country that speaks that language say it different you will offer an alternative phrase in this structure 'Translation: the translated phrase - Alternative: an alternative phrase'"),
                Message(role = "system", content = "If I say something in ${selectedLanguage} you will make a correction and offer an alternative in this structure 'Correction: the correct phrase - Alternative: an alternative phrase for the same meaning' and if I say something in any other language you will translate that to ${selectedLanguage} and also offer an alternative in this structure 'Translation: the translated phrase - Alternative: an alternative phrase for the same meaning'"),
                Message(role = "assistant", content = "Of course, go ahead and tell me what you'd like to translate into ${selectedLanguage}."),
                Message(role = "user", content = "Just a placeholder")
            ),
            model = CHATGPT_MODEL
        )
        chatRequestTranslation = ChatRequest(
            messages = mutableListOf(
                Message(role = "system", content = "You are gonna translate to English what I say in this structure 'Translation: the translated phrase'"),
                Message(role = "assistant", content = "Of course, go ahead and tell me what you'd like to translate into ${selectedLanguage}."),
                Message(role = "user", content = "Just a placeholder")
            ),
            model = CHATGPT_MODEL
        )


        setContentView(R.layout.activity_main)
        val toast = Toast.makeText(applicationContext, "Say something clever.", Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100) // Adjust the Y offset to suit
        toast.show()

        drawMessage(Message(role = "assistant", content = "Say something, but make it count, because you can send just one message per minute. Learning takes time. I will answer you in ${selectedLanguage} and you will have a minute to really read and understand what I said. Tap over the messages to read a translation."), null)
        val audioFilePath = "${externalCacheDir?.absolutePath}/speech.mp3"
        getSpeechResponse("Say something, but make it count, because you can send just one message per minute. Learning takes time. I will answer you in ${selectedLanguage} and you will have a minute to really read and understand what I said. Tap over the messages to read a translation.", audioFilePath) {
            playAudio(audioFilePath)
        }
        etQuestion=findViewById<TextInputEditText>(R.id.etQuestion)

        etQuestion.setOnEditorActionListener(TextView.OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val question = etQuestion.text.toString().trim()
                if(question.isNotEmpty() && timeRemaining == 0L){
                    startCountdownTimer()
                    val userMessage = Message(role = "user", content = question)
                    addMessageToConversationThread(userMessage)
                    //chatRequestTranslationAndAlternative()
                    chatRequestTranslationAndAlternative.messages[2] = Message(role = "user", content = question)
                    chatService.getResponse(chatRequestTranslationAndAlternative) { translationAndAlternativeResult ->
                        runOnUiThread {
                            translationAndAlternativeResult.onSuccess { response ->
                                val translation = Message(role = "user", content = response.content)
                                drawMessage(userMessage, translation)
                            }
                            translationAndAlternativeResult.onFailure { exception ->
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
                                        }
                                        result.onFailure { exception ->
                                            Toast.makeText(this@MainActivity, "Wait a minute", Toast.LENGTH_LONG).show()
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
        mediaPlayer.release() // Release the MediaPlayer when the activity is destroyed
    }
    private fun startCountdownTimer() {
        val timerTextView = findViewById<TextView>(R.id.tvCountdownTimer)
        timerTextView.visibility = View.VISIBLE // Make the TextView visible
        timeRemaining = 60000 // Reset timer to full duration when starting
        object : CountDownTimer(timeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                val secondsRemaining = millisUntilFinished / 1000
                timerTextView.text = getString(R.string.seconds_remaining, secondsRemaining)
            }

            override fun onFinish() {
                timerTextView.text = "0"
                timerTextView.visibility = View.GONE
                timeRemaining = 0 // Ensure timeRemaining reflects the timer has finished
                // Optionally hide the TextView again or leave it visible
            }
        }.start()
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
            // Create a new LinearLayout to contain the TextView and Icon
            val messageContainer = LinearLayout(this@MainActivity)
            messageContainer.orientation = LinearLayout.HORIZONTAL

            // Create a new TextView to display the response
            val newTextView = TextView(this@MainActivity)

            // Set text
            newTextView.text = message.content

            // Set padding for spacing inside the text bubble
            val paddingDp = resources.getDimensionPixelSize(R.dimen.chat_message_padding)
            newTextView.setPadding(paddingDp, paddingDp, paddingDp, paddingDp)
            // Set text color
            newTextView.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.chat_message_text_color))

            // Set maxWidth
            val maxWidthDp = resources.getDimensionPixelSize(R.dimen.chat_message_max_width)
            newTextView.maxWidth = maxWidthDp

            // Set layout parameters with bottom margin
            var layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(8, 0, 8, resources.getDimensionPixelSize(R.dimen.chat_message_margin_bottom))
            newTextView.layoutParams = layoutParams

            // Adding the TextView to the container
            messageContainer.addView(newTextView)

            if (message.role == "assistant") {
                // Set message bubble background for assistant
                newTextView.setBackgroundResource(R.drawable.chat_message_assistant_background)
                messageContainer.gravity = Gravity.START

            } else if (message.role == "user") {
                // Set message bubble background for user
                newTextView.setBackgroundResource(R.drawable.chat_message_user_background)
                messageContainer.gravity = Gravity.END
            }

            newTextView.setTag(R.id.message_id, message)
            // Set click listener for the TextView
            newTextView.setOnClickListener {
                if (translation != null) {
                    translate(it, translation)
                }
            }

            // Add the messageContainer to llChatHistory
            val llChatHistory = findViewById<LinearLayout>(R.id.llChatHistory)
            llChatHistory.addView(messageContainer)
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