package com.tungsten.fcl.control

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.tungsten.fclauncher.keycodes.MinecraftKeyBindingMapper
import com.tungsten.fcllibrary.component.FCLActivity
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.util.Logging
import java.util.logging.Level

/**
 * Oyun sırasında sürekli dinleyip Türkçe sesli komutları (bkz. [VoiceCommands]) oyuna
 * tuş bastırma/bırakma olarak ileten yardımcı sınıf. Yalnızca ayarlardan açıldığında ve
 * RECORD_AUDIO izni verildiğinde [start] ile etkinleştirilir; [stop] ile tamamen kapatılır.
 * SpeechRecognizer çağrıları ana iş parçacığında (Looper.getMainLooper) yapılmalıdır.
 */
class VoiceCommandListener(
    private val activity: FCLActivity,
    private val input: FCLInput
) : RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var stopped = true

    /** En son basılan tuş bağlaması; hedefsiz "kapat" komutu bunu tekrar basar. */
    private var lastBinding: String? = null

    fun start() {
        if (!stopped) return
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        stopped = false
        mainHandler.post {
            if (stopped) return@post
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
                setRecognitionListener(this@VoiceCommandListener)
            }
            listenOnce()
        }
    }

    fun stop() {
        stopped = true
        mainHandler.post {
            recognizer?.let {
                try {
                    it.stopListening()
                    it.destroy()
                } catch (_: Exception) {
                }
            }
            recognizer = null
        }
    }

    private fun listenOnce() {
        if (stopped) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Logging.LOG.log(Level.WARNING, "VoiceCommandListener: failed to start listening", e)
            scheduleRestart()
        }
    }

    /** Hata/no-match sonrası kısa gecikmeyle yeniden dinlemeye başlar; sıkı döngüyü önler. */
    private fun scheduleRestart() {
        if (stopped) return
        mainHandler.postDelayed({ listenOnce() }, 400)
    }

    private fun triggerCommand(recognizedText: String) {
        val result = VoiceCommands.match(recognizedText) ?: return
        val binding = when (result) {
            is VoiceCommandResult.Press -> result.binding
            VoiceCommandResult.RepeatLast -> lastBinding
        } ?: return
        val keycode = resolveKeycode(binding) ?: return
        lastBinding = binding
        Schedulers.io().execute {
            input.sendKeyEvent(keycode, true)
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
            }
            input.sendKeyEvent(keycode, false)
        }
    }

    /**
     * "key.mouse.*" bağlamaları, MinecraftKeyBindingMapper.getGlfwKeycode() üzerinden
     * ham GLFW fare düğmesi değerlerini (0/1/2) döndürür - ama FCLInput.sendKeyEvent()
     * fare tuşlarını 1000+ aralığındaki kendi sabitleriyle (MOUSE_LEFT/RIGHT/MIDDLE)
     * bekler; 0/1/2 değerleri orada yanlışlıkla klavye tuş kodu (ör. 1 = KEY_ESC)
     * olarak yorumlanır. Bu yüzden fare düğmeleri burada doğrudan eşleniyor.
     */
    private fun resolveKeycode(binding: String): Int? {
        return when (binding) {
            "key.mouse.left" -> FCLInput.MOUSE_LEFT
            "key.mouse.right" -> FCLInput.MOUSE_RIGHT
            "key.mouse.middle" -> FCLInput.MOUSE_MIDDLE
            else -> MinecraftKeyBindingMapper.getGlfwKeycode(binding)?.toInt()
        }
    }

    override fun onResults(results: Bundle) {
        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.let { triggerCommand(it) }
        scheduleRestart()
    }

    override fun onError(error: Int) {
        scheduleRestart()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
