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
import com.tungsten.fcl.setting.GameOption
import com.tungsten.fclauncher.keycodes.FCLKeycodes
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
    private val input: FCLInput,
    private val gameOption: GameOption?
) : RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var stopped = true

    /** En son basılan/bırakılan tek seferlik (tap) tuş bağlaması; hedefsiz "kapat" komutu
     * bunu tekrar basar. */
    private var lastTapBinding: String? = null

    /** Şu an basılı tutulan (Hold ile başlatılan) tuşlar: bağlama adı -> gönderilen keycode. */
    private val heldBindings = HashMap<String, Int>()

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
        releaseAll()
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

    /** Yalnızca hata/no-match sonrası kısa gecikmeyle yeniden dinlemeye başlar (sıkı hata
     * döngüsünü önler); başarılı bir sonuçtan sonra [listenOnce] doğrudan çağrılır, art arda
     * söylenen komutların ("zıpla", "zıpla", "zıpla"...) arasında gecikme birikmesin diye. */
    private fun scheduleRestart() {
        if (stopped) return
        mainHandler.postDelayed({ listenOnce() }, 400)
    }

    private fun triggerCommands(recognizedText: String) {
        for (result in VoiceCommands.match(recognizedText)) {
            applyCommand(result)
        }
    }

    private fun applyCommand(result: VoiceCommandResult) {
        when (result) {
            is VoiceCommandResult.Tap -> tap(result.binding)
            is VoiceCommandResult.Hold -> hold(result.binding)
            is VoiceCommandResult.Release -> release(result.binding)
            VoiceCommandResult.ReleaseAll -> releaseAll()
            VoiceCommandResult.RepeatLast -> lastTapBinding?.let { tap(it) }
            is VoiceCommandResult.Chat -> sendChatMessage(result.message)
        }
    }

    private fun tap(binding: String) {
        val keycode = resolveKeycode(binding) ?: return
        lastTapBinding = binding
        Schedulers.io().execute {
            input.sendKeyEvent(keycode, true)
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
            }
            input.sendKeyEvent(keycode, false)
        }
    }

    private fun hold(binding: String) {
        if (heldBindings.containsKey(binding)) return
        val keycode = resolveKeycode(binding) ?: return
        heldBindings[binding] = keycode
        Schedulers.io().execute { input.sendKeyEvent(keycode, true) }
    }

    private fun release(binding: String) {
        val keycode = heldBindings.remove(binding) ?: resolveKeycode(binding) ?: return
        Schedulers.io().execute { input.sendKeyEvent(keycode, false) }
    }

    private fun releaseAll() {
        if (heldBindings.isEmpty()) return
        val keycodes = heldBindings.values.toList()
        heldBindings.clear()
        Schedulers.io().execute {
            keycodes.forEach { input.sendKeyEvent(it, false) }
        }
    }

    /** Sohbeti açar (kullanıcının ayarladığı tuşla, GameOption üzerinden), metni yazıp
     * Enter'a basar - ControlButton'daki "Send Text" olay işleyicisiyle aynı zamanlama. */
    private fun sendChatMessage(message: String) {
        Schedulers.io().execute {
            if (gameOption != null) {
                input.sendBoundKeyEvent(gameOption, MinecraftKeyBindingMapper.BINDING_CHAT, FCLKeycodes.KEY_T, true)
                input.sendBoundKeyEvent(gameOption, MinecraftKeyBindingMapper.BINDING_CHAT, FCLKeycodes.KEY_T, false)
            } else {
                input.sendKeyEvent(FCLKeycodes.KEY_T, true)
                input.sendKeyEvent(FCLKeycodes.KEY_T, false)
            }
            try {
                Thread.sleep(150)
            } catch (_: InterruptedException) {
            }
            for (c in message) {
                input.sendChar(c)
            }
            input.sendKeyEvent(FCLKeycodes.KEY_ENTER, true)
            input.sendKeyEvent(FCLKeycodes.KEY_ENTER, false)
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
            ?.let { triggerCommands(it) }
        listenOnce()
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
