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
import com.tungsten.fclauncher.bridge.FCLBridge
import com.tungsten.fclauncher.keycodes.FCLKeycodes
import com.tungsten.fclauncher.keycodes.MinecraftKeyBindingMapper
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.util.Logging
import com.tungsten.fcllibrary.util.LocaleUtils
import java.util.logging.Level

/**
 * Oyun sırasında sürekli dinleyip Türkçe/İngilizce sesli komutları (bkz. [VoiceCommands])
 * oyuna tuş bastırma/bırakma veya bakış açısı döndürme olarak ileten yardımcı sınıf.
 * Tanınan dil, başlatıcının dil ayarına göre [recognitionLanguage] tarafından seçilir.
 * Yalnızca ayarlardan açıldığında ve RECORD_AUDIO izni verildiğinde [start] ile
 * etkinleştirilir; [stop] ile tamamen kapatılır. SpeechRecognizer çağrıları ana iş
 * parçacığında (Looper.getMainLooper) yapılmalıdır.
 */
class VoiceCommandListener(private val menu: GameMenu) : RecognitionListener {

    private val activity = menu.getActivity()
    private val input: FCLInput get() = menu.getInput()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var stopped = true

    /** En son basılan/bırakılan tek seferlik (tap) tuş bağlaması; hedefsiz "kapat" komutu
     * bunu tekrar basar. */
    private var lastTapBinding: String? = null

    /** Şu an basılı tutulan (Hold ile başlatılan) tuşlar: bağlama adı -> gönderilen keycode. */
    private val heldBindings = HashMap<String, Int>()

    companion object {
        private const val LOOK_POINTER_ID = "VoiceCommandLook"
    }

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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Logging.LOG.log(Level.WARNING, "VoiceCommandListener: failed to start listening", e)
            scheduleRestart()
        }
    }

    /** Başlatıcının dil ayarını takip eder: İngilizce seçiliyse İngilizce, aksi halde
     * (Türkçe veya diğer diller) Türkçe tanıma kullanılır - [VoiceCommands] her ikisini
     * de anlıyor, ama SpeechRecognizer'ın doğruluğu doğru dili seçmekle çok artıyor. */
    private fun recognitionLanguage(): String {
        val locale = LocaleUtils.getLocale(LocaleUtils.getLanguage(activity))
        return if (locale.language == "en") "en-US" else "tr-TR"
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
            is VoiceCommandResult.Look -> applyLook(result.dx, result.dy)
        }
    }

    /**
     * Bakış açısını döndürür: TouchPad'in dokunmatik sürükleme sırasında yaptığının
     * aynısı (setPointerId ile "sürükleyeni" bu çağrı olarak işaretleyip pointer'ı adım
     * adım kaydırmak) - tek seferde büyük bir sıçrama yerine kısa adımlarla, doğal bir
     * sürükleme hareketini taklit eder.
     */
    private fun applyLook(dx: Int, dy: Int) {
        // Envanter/GUI gibi imleç modunda pointerX/Y gerçek (sınırlı) ekran koordinatıdır,
        // kamera döndürme birikimcisi değil - bu modda bakış komutları anlamsız/zararlı
        // olur (imleci rastgele bir yere sıçratabilir), bu yüzden yalnızca normal oyun
        // görünümünde (imleç modu kapalıyken) çalışır.
        if (menu.getCursorMode() == FCLBridge.CursorEnabled) return
        Schedulers.io().execute {
            val startX = menu.getPointerX()
            val startY = menu.getPointerY()
            val steps = 12
            input.setPointerId(LOOK_POINTER_ID)
            for (step in 1..steps) {
                val x = startX + dx * step / steps
                val y = startY + dy * step / steps
                input.setPointer(x, y, LOOK_POINTER_ID)
                try {
                    Thread.sleep(12)
                } catch (_: InterruptedException) {
                }
            }
            if (LOOK_POINTER_ID == input.getPointerId()) {
                input.setPointerId(null)
            }
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
            val gameOption = menu.getGameOption()
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
