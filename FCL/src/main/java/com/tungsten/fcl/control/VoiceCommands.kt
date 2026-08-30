package com.tungsten.fcl.control

import java.util.Locale

/** Bir sesli komuttan çözümlenen tek bir eylem. Tek bir cümlede birden fazla eylem
 * olabilir (ör. "zıpla w tuşuna bas" -> [Tap(space), Hold(w)]). */
sealed class VoiceCommandResult {
    /** Kısa bas-bırak (F3, envanter, sohbet, zıpla, tıklama... aç/kapa tuşları). */
    data class Tap(val binding: String) : VoiceCommandResult()

    /** Basılı tut, açık bırak (W/A/S/D yürüme, sol tık kazma, shift/ctrl) - [Release] veya
     * [ReleaseAll] gelene kadar bırakılmaz. */
    data class Hold(val binding: String) : VoiceCommandResult()

    /** Daha önce [Hold] ile basılı tutulan belirli bir tuşu bırakır. */
    data class Release(val binding: String) : VoiceCommandResult()

    /** O an basılı tutulan tüm tuşları bırakır (ör. bağımsız "dur" komutu). */
    object ReleaseAll : VoiceCommandResult()

    /** Hedef belirtilmeden söylenen "kapat" gibi bir komut: en son basılan tuşu tekrar bas
     * (F3/envanter/sohbet gibi aç-kapa tuşları aynı tuşa ikinci kez basılınca kapanır). */
    object RepeatLast : VoiceCommandResult()

    /** Sohbeti aç, metni yaz ve gönder (ör. "sohbeti aç merhaba yaz gönder"). */
    data class Chat(val message: String) : VoiceCommandResult()

    /** Bakış açısını döndürür (ör. "sağa bak", "çok az sola dön"). dx/dy, sanal işaretçi
     * üzerinde uygulanacak piksel cinsinden yatay/dikey kaydırma miktarıdır; pozitif dx
     * sağa, pozitif dy aşağı bakışa karşılık gelir. */
    data class Look(val dx: Int, val dy: Int) : VoiceCommandResult()
}

/**
 * Türkçe sesli komutları Minecraft tuş bağlama adlarına (MinecraftKeyBindingMapper ile
 * FCLKeycodes'a çözümlenir) eşler. Tek bir cümlede birden fazla komut olabileceğinden
 * [match] bir liste döner ("zıpla w tuşuna bas" -> zıpla + w basılı tut).
 */
object VoiceCommands {

    /** W/A/S/D gibi yürüme tuşları ile sol tık (kazma/vurma) ve shift/ctrl: bunlar
     * Minecraft'ta anlamlı olması için basılı tutulması gereken eylemlerdir, bu yüzden
     * genel "<tuş> bas" kalıbıyla eşleştiklerinde kısa vuruş yerine [Hold] üretilir. */
    private val HOLDABLE = setOf(
        "key.keyboard.w", "key.keyboard.a", "key.keyboard.s", "key.keyboard.d",
        "key.mouse.left",
        "key.keyboard.left.shift", "key.keyboard.right.shift",
        "key.keyboard.left.control", "key.keyboard.right.control",
    )

    /** Türkçe tuş adı (normalize edilmiş) -> Minecraft tuş bağlama adı. Çok kelimeli adlar
     * (ör. "sağ shift") tek kelimelilerden ([match] içinde) önce denenir. */
    private val KEY_NAMES: Map<String, String> = buildMap {
        for (c in 'a'..'z') put(c.toString(), "key.keyboard.$c")
        for (n in 0..9) put(n.toString(), "key.keyboard.$n")

        // Latince harflerin Türkçe'de sık söylenen/karıştırılan telaffuzları
        put("dabılyu", "key.keyboard.w"); put("dabılvı", "key.keyboard.w")
        put("dabl", "key.keyboard.w"); put("çift ve", "key.keyboard.w")
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
    private val REPEAT_LAST_PHRASES = setOf("kapat", "şimdi kapat", "onu kapat", "tekrar bas", "bir daha bas")

    /** Basılı tutulan her şeyi bırakan bağımsız ifadeler. */
    private val RELEASE_ALL_PHRASES = setOf("dur", "durdur", "bırak", "hepsini bırak")

    /** Bir tuşu basılı tutmayı bırakmak için kullanılan fiiller. */
    private val RELEASE_VERBS = setOf("bırak", "bırakıyor", "serbest bırak")

    /** "bas/tıkla" gibi bir fiil içeren komutlar. Tek harfli tuş adları ("a", "e", "o" gibi
     * gerçek Türkçe kelimelerle çakışabilen) yalnızca bu fiillerden biri de söylendiğinde
     * eşleşir; böylece sıradan konuşma yanlışlıkla tuşa basmaz. Çok karakterli adlar
     * (f3, shift, sağ tık…) zaten yeterince kendine özgü olduğu için bu şart aranmaz. */
    private val TRIGGER_VERBS = setOf("bas", "basar", "basıyor", "tıkla", "tıklar", "tuşuna", "tuşunu")

    /** W/A/S/D özellikle bare (fiilsiz) söylenince de çalışır - kullanıcı isteği bu yönde
     * ("wasd'den birisine bahsedeyim de basabilsin") ve zaten oyun sırasında sürekli tek
     * başına anılan hareket tuşlarıdır; diğer tek harfler (özellikle "a"/"e"/"o" gibi
     * gerçek Türkçe kelimeler) yine de [TRIGGER_VERBS] gerektirir. */
    private val ALWAYS_BARE_LETTERS = setOf("w", "a", "s", "d")

    /** "sohbeti aç <mesaj> yaz [gönder]" kalıbı. Türkçe ekli fiil biçimleri (ör. "açıyor")
     * yerine emir kipi ("aç"/"yaz") beklenir; sesli komutlarda doğal olan budur. */
    private val CHAT_PATTERN =
        Regex("""(sohbeti|sohbet|chat)\s+aç\s+(.+?)\s+yaz(\s+gönder)?$""")

    // --- Bakış açısı (kamera) döndürme ---
    // "sağa/sağ bak", "sola/sol dön", "yukarı", "aşağı", ve bunların herhangi bir
    // kombinasyonu (çapraz) - bir bakış fiili olmadan tetiklenmez, bu yüzden "sağ tık"
    // gibi mevcut tuş deyimleriyle çakışmaz (onlarda "bak/dön/çevir" fiili yoktur).
    private val LOOK_VERBS = setOf("bak", "dön", "çevir", "döndür", "baksın", "dönsün", "çevirsin")
    private val LOOK_RIGHT_WORDS = setOf("sağa", "sağ")
    private val LOOK_LEFT_WORDS = setOf("sola", "sol")
    private val LOOK_UP_WORDS = setOf("yukarı", "yukarıya", "yukari")
    private val LOOK_DOWN_WORDS = setOf("aşağı", "aşağıya", "asagi")

    // "çok az" gibi 2 kelimelik ifadeler, tek başına "çok" (büyük dönüş anlamına gelir)
    // ile çakışmasın diye önce denenir.
    private val LOOK_TINY_PHRASES = listOf("çok az", "çok hafif", "birazcık", "azıcık", "hafifçe", "hafif", "biraz")
    private val LOOK_LARGE_PHRASES = listOf("çok fazla", "fazlaca", "iyice", "büyük", "çok")

    private const val LOOK_DELTA_TINY = 15
    private const val LOOK_DELTA_MEDIUM = 65
    private const val LOOK_DELTA_LARGE = 220

    private fun detectLook(normalized: String, rawTokens: List<String>): VoiceCommandResult.Look? {
        if (rawTokens.none { it in LOOK_VERBS }) return null

        val right = rawTokens.any { it in LOOK_RIGHT_WORDS }
        val left = rawTokens.any { it in LOOK_LEFT_WORDS }
        val up = rawTokens.any { it in LOOK_UP_WORDS }
        val down = rawTokens.any { it in LOOK_DOWN_WORDS }
        if (!right && !left && !up && !down) return null

        val magnitude = when {
            LOOK_TINY_PHRASES.any { normalized.contains(it) } -> LOOK_DELTA_TINY
            LOOK_LARGE_PHRASES.any { normalized.contains(it) } -> LOOK_DELTA_LARGE
            else -> LOOK_DELTA_MEDIUM
        }

        var dx = 0
        var dy = 0
        if (right) dx += magnitude
        if (left) dx -= magnitude
        if (down) dy += magnitude
        if (up) dy -= magnitude
        return VoiceCommandResult.Look(dx, dy)
    }

    fun match(recognizedText: String): List<VoiceCommandResult> {
        val normalized = normalize(recognizedText)

        CHAT_PATTERN.find(normalized)?.let { m ->
            val message = m.groupValues[2].trim()
            if (message.isNotEmpty()) {
                return listOf(VoiceCommandResult.Chat(message))
            }
        }

        // Java/Kotlin regex \b Türkçe harfleri (ı, ş, ğ, ç, ö, ü) varsayılan olarak "kelime
        // karakteri" saymadığından \b burada kullanılmıyor; bunun yerine kelimelere ayırıp
        // (tek ve iki kelimelik) tam eşleşme aranıyor.
        val rawTokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        detectLook(normalized, rawTokens)?.let { return listOf(it) }

        if (normalized in RELEASE_ALL_PHRASES) {
            return listOf(VoiceCommandResult.ReleaseAll)
        }
        if (normalized in REPEAT_LAST_PHRASES) {
            return listOf(VoiceCommandResult.RepeatLast)
        }

        val rawPairs = rawTokens.zipWithNext { a, b -> "$a $b" }

        // Aynı cümlede yalnızca tek bir aç/kapa yönü uygulanır (ör. "w bas a'yı bırak"
        // gibi karışık cümleler desteklenmez); "bırak" geçerse tüm eylemler bırakma olur.
        val isRelease = rawTokens.any { it in RELEASE_VERBS } || rawPairs.any { it in RELEASE_VERBS }
        // Tek harfli tuş adları için "niyet" sinyali: ya bas/tıkla fiili ya da (bırakma
        // durumunda) bırak fiili - ikisi de "bu bir rastgele hece değil, bilinçli bir tuş
        // adı" anlamına gelir.
        val hasTriggerVerb = isRelease || rawTokens.any { it in TRIGGER_VERBS }

        val results = mutableListOf<VoiceCommandResult>()
        var remaining = normalized

        // En uzun deyimler önce denenir ki örneğin "f3'ü kapat" hem PHRASE_ALIASES'ta
        // hem de aşağıdaki genel "f3" tuş adı eşleşmesinde ayrı ayrı sayılıp çift
        // basmasın: eşleşen deyimin metni burada tüketilip metinden çıkarılıyor.
        for ((phrase, keyName) in PHRASE_ALIASES.entries.sortedByDescending { it.key.length }) {
            if (remaining.contains(phrase)) {
                KEY_NAMES[keyName]?.let { results += toAction(it, isRelease) }
                remaining = remaining.replace(phrase, " ")
            }
        }

        // Genel tanıma: kalan metinde "<tuş adı> bas/tıkla/bırak" kalıbı - 2 kelimelik
        // ("sağ shift") pencereleri önce, sonra tek kelimelik tuş adlarını dener.
        val tokens = remaining.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { it.replace(Regex("'.*$"), "") }

        val consumed = BooleanArray(tokens.size)
        var i = 0
        while (i < tokens.size) {
            if (consumed[i]) {
                i++; continue
            }
            if (i + 1 < tokens.size && !consumed[i + 1]) {
                val twoWord = "${tokens[i]} ${tokens[i + 1]}"
                val binding = KEY_NAMES[twoWord]
                if (binding != null) {
                    results += toAction(binding, isRelease)
                    consumed[i] = true; consumed[i + 1] = true
                    i += 2
                    continue
                }
            }
            val token = tokens[i]
            val binding = KEY_NAMES[token]
            val needsVerb = token.length == 1 && token !in ALWAYS_BARE_LETTERS && !hasTriggerVerb
            if (binding != null && !needsVerb) {
                results += toAction(binding, isRelease)
                consumed[i] = true
            }
            i++
        }

        return results
    }

    private fun toAction(binding: String, isRelease: Boolean): VoiceCommandResult {
        if (isRelease) return VoiceCommandResult.Release(binding)
        return if (binding in HOLDABLE) VoiceCommandResult.Hold(binding) else VoiceCommandResult.Tap(binding)
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale("tr")).trim().trimEnd('.', '!', '?')
    }
}
