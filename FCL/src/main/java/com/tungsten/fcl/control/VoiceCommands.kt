package com.tungsten.fcl.control

import java.util.Locale

/**
 * Türkçe sesli komut tetikleyicileri -> Minecraft tuş bağlama adı (MinecraftKeyBindingMapper
 * ile FCLKeycodes'a çözümlenir). Aynı eylem için birden çok söyleniş biçimi desteklenir.
 */
object VoiceCommands {

    private val COMMANDS: List<Pair<List<String>, String>> = listOf(
        listOf("f3'e bas", "f3'e basar mısın", "f3 bas", "hata ayıklama ekranı", "debug ekranı") to "key.keyboard.f3",
        listOf("envanteri aç", "envanter aç", "envanteri kapat", "çantayı aç") to "key.keyboard.e",
        listOf("sohbeti aç", "sohbet aç", "mesaj yaz", "chat aç") to "key.keyboard.t",
        listOf("ekran görüntüsü al", "ekran görüntüsü", "f2'ye bas", "f2 bas") to "key.keyboard.f2",
        listOf("eşyayı at", "eşya at", "düşür") to "key.keyboard.q",
        listOf("perspektifi değiştir", "kamera değiştir", "f5'e bas", "f5 bas") to "key.keyboard.f5",
        listOf("menüyü aç", "menüyü kapat", "esc'e bas", "kaçış tuşu") to "key.keyboard.escape",
        listOf("zıpla", "atla") to "key.keyboard.space",
        listOf("eğil", "sinsi yürü", "gizlen") to "key.keyboard.left.shift",
        listOf("koş", "koşmaya başla", "sprint") to "key.keyboard.left.control",
    )

    /** Tanınan metni bilinen tetikleyicilerle eşleştirir, eşleşme yoksa null döner. */
    fun match(recognizedText: String): String? {
        val normalized = normalize(recognizedText)
        for ((phrases, binding) in COMMANDS) {
            if (phrases.any { normalized.contains(normalize(it)) }) {
                return binding
            }
        }
        return null
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale("tr")).trim()
    }
}
