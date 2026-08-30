package com.tungsten.fcl.control

import java.util.Locale

/** Bir sesli komutun çözümlenmiş sonucu. */
sealed class VoiceCommandResult {
    /** Belirli bir Minecraft tuş bağlamasına (ör. "key.keyboard.w", "key.mouse.right") basılsın. */
    data class Press(val binding: String) : VoiceCommandResult()

    /** Hedef belirtilmeden söylenen "kapat" gibi bir komut: en son basılan tuşu tekrar bas
     * (F3/envanter/sohbet gibi aç-kapa tuşları aynı tuşa ikinci kez basılınca kapanır). */
    object RepeatLast : VoiceCommandResult()
}

/**
 * Türkçe sesli komutları Minecraft tuş bağlama adlarına (MinecraftKeyBindingMapper ile
 * FCLKeycodes'a çözümlenir) eşler. İki katmanlı çalışır:
 * 1) Sabit deyimler (ör. "zıpla", "envanteri aç") - [PHRASE_ALIASES]
 * 2) Genel tuş adı tanıma (ör. "w'ya bas", "sağ shift'e bas", "sağ tıkla") - [KEY_NAMES]
 * Böylece her tek harf/rakam/F tuşu/fare tuşu, özel bir deyim tanımlamaya gerek kalmadan
 * "<tuş adı> bas/tıkla" kalıbıyla otomatik olarak çalışır.
 */
object VoiceCommands {

    /** Türkçe tuş adı (normalize edilmiş) -> Minecraft tuş bağlama adı. Çok kelimeli adlar
     * (ör. "sağ shift") tek kelimelilerden ([match] içinde) önce denenir. */
    private val KEY_NAMES: Map<String, String> = buildMap {
        for (c in 'a'..'z') put(c.toString(), "key.keyboard.$c")
        for (n in 0..9) put(n.toString(), "key.keyboard.$n")

        // Latince harflerin Türkçe'de sık söylenen telaffuzları (STT çoğu zaman harfi
        // doğrudan yazıya döker, ama bazı harfler için telaffuz da yaygın kullanılıyor)
        put("dabılyu", "key.keyboard.w"); put("dabılvı", "key.keyboard.w"); put("çift ve", "key.keyboard.w")
        put("ıks", "key.keyboard.x"); put("iks", "key.keyboard.x")
        put("kyu", "key.keyboard.q"); put("kü", "key.keyboard.q")

        for (f in 1..12) put("f$f", "key.keyboard.f$f")

        put("yukarı", "key.keyboard.up"); put("yukarı ok", "key.keyboard.up")
        put("aşağı", "key.keyboard.down"); put("aşağı ok", "key.keyboard.down")
        put("sol ok", "key.keyboard.left")
        put("sağ ok", "key.keyboard.right")

        put("sağ shift", "key.keyboard.right.shift")
        put("sol shift", "key.keyboard.left.shift")
        put("shift", "key.keyboard.left.shift")
        put("sağ kontrol", "key.keyboard.right.control"); put("sağ ctrl", "key.keyboard.right.control")
        put("sol kontrol", "key.keyboard.left.control"); put("sol ctrl", "key.keyboard.left.control")
        put("kontrol", "key.keyboard.left.control"); put("ctrl", "key.keyboard.left.control")
        put("sağ alt", "key.keyboard.right.alt")
        put("sol alt", "key.keyboard.left.alt")
        put("alt", "key.keyboard.left.alt")

        put("boşluk", "key.keyboard.space"); put("boşluğa", "key.keyboard.space")
        put("enter", "key.keyboard.enter"); put("giriş", "key.keyboard.enter")
        put("esc", "key.keyboard.escape"); put("kaçış", "key.keyboard.escape")
        put("tab", "key.keyboard.tab")
        put("backspace", "key.keyboard.backspace"); put("sil", "key.keyboard.backspace")
        put("caps lock", "key.keyboard.caps.lock")

        put("sol tık", "key.mouse.left"); put("sol tıkla", "key.mouse.left")
        put("sol fare", "key.mouse.left"); put("sol tık at", "key.mouse.left")
        put("sağ tık", "key.mouse.right"); put("sağ tıkla", "key.mouse.right")
        put("sağ fare", "key.mouse.right"); put("sağ tık at", "key.mouse.right")
        put("orta tık", "key.mouse.middle"); put("orta tıkla", "key.mouse.middle")
    }

    /** Sabit deyimler -> [KEY_NAMES] anahtarı. Aynı tuş için aç/kapat ikisi de aynı deyime
     * bağlanır, çünkü bu tuşların hepsi Minecraft'ta tek başlarına aç/kapa (toggle) tuşudur. */
    private val PHRASE_ALIASES: Map<String, String> = mapOf(
        "zıpla" to "boşluk", "atla" to "boşluk",
        "envanteri aç" to "e", "envanter aç" to "e", "envanteri kapat" to "e",
        "envanter kapat" to "e", "çantayı aç" to "e", "çantayı kapat" to "e",
        "sohbeti aç" to "t", "sohbet aç" to "t", "mesaj yaz" to "t", "chat aç" to "t",
        "eşyayı at" to "q", "eşya at" to "q", "düşür" to "q",
        "perspektifi değiştir" to "f5", "kamera değiştir" to "f5",
        "menüyü aç" to "esc", "menüyü kapat" to "esc",
        "hata ayıklama ekranı" to "f3", "hata ayıklama ekranını aç" to "f3",
        "hata ayıklama ekranını kapat" to "f3", "debug ekranı" to "f3",
        "debug ekranını aç" to "f3", "debug ekranını kapat" to "f3", "f3'ü kapat" to "f3",
        "ekran görüntüsü al" to "f2", "ekran görüntüsü" to "f2",
        "eğil" to "sol shift", "sinsi yürü" to "sol shift", "gizlen" to "sol shift",
        "koş" to "sol kontrol", "koşmaya başla" to "sol kontrol", "sprint" to "sol kontrol",
    )

    /** Hedef belirtilmeyen, "en son basılanı tekrar et" anlamına gelen bağımsız ifadeler. */
    private val REPEAT_LAST_PHRASES = listOf("kapat", "şimdi kapat", "onu kapat", "tekrar bas", "bir daha bas")

    /** "bas/tıkla" gibi bir fiil içeren komutlar. Tek harfli tuş adları ("a", "e", "o" gibi
     * gerçek Türkçe kelimelerle çakışabilen) yalnızca bu fiillerden biri de söylendiğinde
     * eşleşir; böylece sıradan konuşma yanlışlıkla tuşa basmaz. Çok karakterli adlar
     * (f3, shift, sağ tık…) zaten yeterince kendine özgü olduğu için bu şart aranmaz.
     */
    private val TRIGGER_VERBS = listOf("bas", "basar", "basıyor", "tıkla", "tıklar", "tuşuna", "tuşunu")

    fun match(recognizedText: String): VoiceCommandResult? {
        val normalized = normalize(recognizedText)

        for (phrase in REPEAT_LAST_PHRASES) {
            if (normalized == phrase || normalized == "$phrase.") {
                return VoiceCommandResult.RepeatLast
            }
        }

        for ((phrase, keyName) in PHRASE_ALIASES) {
            if (normalized.contains(phrase)) {
                KEY_NAMES[keyName]?.let { return VoiceCommandResult.Press(it) }
            }
        }

        // Genel tanıma: "<tuş adı> bas/tıkla" kalıbı - metni kelimelere ayırıp (kesme
        // işareti ekini atarak) 2 kelimelik ("sağ shift") ve 1 kelimelik pencerelerde ara.
        val tokens = normalized.split(Regex("\\s+")).map { it.replace(Regex("'.*$"), "") }
        val hasTriggerVerb = tokens.any { it in TRIGGER_VERBS }

        for (i in tokens.indices) {
            if (i + 1 < tokens.size) {
                val twoWord = "${tokens[i]} ${tokens[i + 1]}"
                KEY_NAMES[twoWord]?.let { return VoiceCommandResult.Press(it) }
            }
        }
        for (token in tokens) {
            val binding = KEY_NAMES[token] ?: continue
            if (token.length == 1 && !hasTriggerVerb) continue
            return VoiceCommandResult.Press(binding)
        }

        return null
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale("tr")).trim().trimEnd('.', '!', '?')
    }
}
