package com.example.oasis_mobile_client

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechRecognizerManager(context: Context) {
    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb = _rmsDb.asStateFlow()

    private var onResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer", "onReadyForSpeech")
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                _rmsDb.value = rmsdB
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognizer", "onEndOfSpeech")
                _isListening.value = false
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                Log.e("SpeechRecognizer", "onError: $message")
                _isListening.value = false
                onError?.invoke(message)
            }

            override fun onResults(results: Bundle?) {
                Log.d("SpeechRecognizer", "onResults")
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult?.invoke(matches[0])
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        this.onResult = onResult
        this.onError = onError
        try {
            speechRecognizer.startListening(recognitionIntent)
        } catch (e: Exception) {
            onError(e.message ?: "Failed to start listening")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer.stopListening()
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "stopListening failed", e)
        }
    }

    fun destroy() {
        try {
            speechRecognizer.destroy()
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "destroy failed", e)
        }
    }
}
