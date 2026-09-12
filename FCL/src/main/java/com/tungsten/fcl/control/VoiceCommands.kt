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

    /** Ekrana yeni bir kontrol butonu ekler (ör. "buton oluştur f5"). [text] butonun üstünde
     * yazan etiket, [binding] basıldığında gönderilecek tuş bağlaması. */
    data class CreateButton(val text: String, val binding: String) : VoiceCommandResult()

    /** Sesle oluşturulan son butonu taşır. Değerler ekranın yüzdesi cinsindendir
     * (pozitif dx sağa, pozitif dy aşağı). */
    data class MoveButton(val dxPercent: Int, val dyPercent: Int) : VoiceCommandResult()

    /** Sesle oluşturulan son butonun boyutunu ayarlar (ekran yüzdesi).
     * null olan eksen değiştirilmez. */
    data class ResizeButton(val widthPercent: Int?, val heightPercent: Int?) : VoiceCommandResult()
}

/**
 * Türkçe ve İngilizce sesli komutları Minecraft tuş bağlama adlarına (MinecraftKeyBindingMapper
 * ile FCLKeycodes'a çözümlenir) eşler - hangi dilin tanındığı [VoiceCommandListener]'ın
 * başlatıcı dil ayarına göre seçtiği SpeechRecognizer diline bağlıdır, ama buradaki eşleştirme
 * ikisini de anlar. Tek bir cümlede birden fazla komut olabileceğinden [match] bir liste
 * döner ("zıpla w tuşuna bas" / "jump press w" -> zıpla + w basılı tut).
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

    /** Türkçe/İngilizce tuş adı (normalize edilmiş) -> Minecraft tuş bağlama adı. Çok
     * kelimeli adlar (ör. "sağ shift"/"right shift") tek kelimelilerden ([match] içinde)
     * önce denenir. */
    private val KEY_NAMES: Map<String, String> = buildMap {
        for (c in 'a'..'z') put(c.toString(), "key.keyboard.$c")
        for (n in 0..9) put(n.toString(), "key.keyboard.$n")

        // Latince harflerin Türkçe'de sık söylenen/karıştırılan telaffuzları
        put("dabılyu", "key.keyboard.w"); put("dabılvı", "key.keyboard.w")
        put("dabl", "key.keyboard.w"); put("çift ve", "key.keyboard.w")
        put("ıks", "key.keyboard.x"); put("iks", "key.keyboard.x")
        put("kyu", "key.keyboard.q"); put("kü", "key.keyboard.q")

        // Tanıyıcının Türkçe modda sık ürettiği yazımlar (yaklaşık eşleştirmenin
        // yakalayamayacağı kadar uzak olanlar burada birebir tabloda tutuluyor)
        put("şift", "key.keyboard.left.shift"); put("şıft", "key.keyboard.left.shift")
        put("sağ şift", "key.keyboard.right.shift"); put("sol şift", "key.keyboard.left.shift")
        put("kontırol", "key.keyboard.left.control"); put("kontrl", "key.keyboard.left.control")
        put("espeys", "key.keyboard.space"); put("enter tuşu", "key.keyboard.enter")

        for (f in 1..12) put("f$f", "key.keyboard.f$f")

        put("yukarı", "key.keyboard.up"); put("yukarı ok", "key.keyboard.up")
        put("aşağı", "key.keyboard.down"); put("aşağı ok", "key.keyboard.down")
        put("sol ok", "key.keyboard.left")
        put("sağ ok", "key.keyboard.right")
        put("up arrow", "key.keyboard.up"); put("down arrow", "key.keyboard.down")
        put("left arrow", "key.keyboard.left"); put("right arrow", "key.keyboard.right")

        put("sağ shift", "key.keyboard.right.shift")
        put("sol shift", "key.keyboard.left.shift")
        put("shift", "key.keyboard.left.shift")
        put("sağ kontrol", "key.keyboard.right.control"); put("sağ ctrl", "key.keyboard.right.control")
        put("sol kontrol", "key.keyboard.left.control"); put("sol ctrl", "key.keyboard.left.control")
        put("kontrol", "key.keyboard.left.control"); put("ctrl", "key.keyboard.left.control")
        put("sağ alt", "key.keyboard.right.alt")
        put("sol alt", "key.keyboard.left.alt")
        put("alt", "key.keyboard.left.alt")
        put("right shift", "key.keyboard.right.shift"); put("left shift", "key.keyboard.left.shift")
        put("right control", "key.keyboard.right.control"); put("right ctrl", "key.keyboard.right.control")
        put("left control", "key.keyboard.left.control"); put("left ctrl", "key.keyboard.left.control")
        put("control", "key.keyboard.left.control")
        put("right alt", "key.keyboard.right.alt"); put("left alt", "key.keyboard.left.alt")

        put("boşluk", "key.keyboard.space"); put("boşluğa", "key.keyboard.space")
        put("enter", "key.keyboard.enter"); put("giriş", "key.keyboard.enter")
        put("esc", "key.keyboard.escape"); put("kaçış", "key.keyboard.escape")
        put("tab", "key.keyboard.tab")
        put("backspace", "key.keyboard.backspace"); put("sil", "key.keyboard.backspace")
        put("caps lock", "key.keyboard.caps.lock")
        put("space", "key.keyboard.space"); put("spacebar", "key.keyboard.space")
        put("escape", "key.keyboard.escape")
        put("delete", "key.keyboard.delete")

        put("sol tık", "key.mouse.left"); put("sol tıkla", "key.mouse.left")
        put("sol fare", "key.mouse.left"); put("sol tık at", "key.mouse.left")
        put("sağ tık", "key.mouse.right"); put("sağ tıkla", "key.mouse.right")
        put("sağ fare", "key.mouse.right"); put("sağ tık at", "key.mouse.right")
        put("orta tık", "key.mouse.middle"); put("orta tıkla", "key.mouse.middle")
        put("left click", "key.mouse.left"); put("right click", "key.mouse.right")
        put("middle click", "key.mouse.middle")
    }

    /** Sabit deyimler -> [KEY_NAMES] anahtarı. Aynı tuş için aç/kapat ikisi de aynı deyime
     * bağlanır, çünkü bu tuşların hepsi Minecraft'ta tek başlarına aç/kapa (toggle) tuşudur. */
    private val PHRASE_ALIASES: Map<String, String> = mapOf(
        "zıpla" to "boşluk", "atla" to "boşluk", "jump" to "space",
        "envanteri aç" to "e", "envanter aç" to "e", "envanteri kapat" to "e",
        "envanter kapat" to "e", "çantayı aç" to "e", "çantayı kapat" to "e",
        "open inventory" to "e", "close inventory" to "e",
        "eşyayı at" to "q", "eşya at" to "q", "düşür" to "q",
        "drop item" to "q", "throw item" to "q", "drop" to "q",
        "perspektifi değiştir" to "f5", "kamera değiştir" to "f5",
        "change perspective" to "f5", "change camera" to "f5",
        "menüyü aç" to "esc", "menüyü kapat" to "esc",
        "open menu" to "esc", "close menu" to "esc",
        "hata ayıklama ekranı" to "f3", "hata ayıklama ekranını aç" to "f3",
        "hata ayıklama ekranını kapat" to "f3", "debug ekranı" to "f3",
        "debug ekranını aç" to "f3", "debug ekranını kapat" to "f3", "f3'ü kapat" to "f3",
        "debug screen" to "f3", "open debug screen" to "f3", "close debug screen" to "f3",
        "ekran görüntüsü al" to "f2", "ekran görüntüsü" to "f2",
        "take screenshot" to "f2", "screenshot" to "f2",
        "eğil" to "sol shift", "sinsi yürü" to "sol shift", "gizlen" to "sol shift",
        "crouch" to "left shift", "sneak" to "left shift",
        "koş" to "sol kontrol", "koşmaya başla" to "sol kontrol", "sprint" to "sol kontrol",
        "run" to "left control",
    )

    /** Hedef belirtilmeyen, "en son basılanı tekrar et" anlamına gelen bağımsız ifadeler. */
    private val REPEAT_LAST_PHRASES = setOf(
        "kapat", "şimdi kapat", "onu kapat", "tekrar bas", "bir daha bas",
        "close", "close it", "press again",
    )

    /** Basılı tutulan her şeyi bırakan bağımsız ifadeler. */
    private val RELEASE_ALL_PHRASES = setOf("dur", "durdur", "bırak", "hepsini bırak", "stop", "release", "release all")

    /** Bir tuşu basılı tutmayı bırakmak için kullanılan fiiller. */
    private val RELEASE_VERBS = setOf("bırak", "bırakıyor", "serbest bırak", "release", "let go")

    /** "bas/tıkla"/"press/click" gibi bir fiil içeren komutlar. Tek harfli tuş adları
     * ("a", "e", "o"/"a", "i" gibi gerçek Türkçe veya İngilizce kelimelerle çakışabilen)
     * yalnızca bu fiillerden biri de söylendiğinde eşleşir; böylece sıradan konuşma
     * yanlışlıkla tuşa basmaz. Çok karakterli adlar (f3, shift, sağ tık…) zaten yeterince
     * kendine özgü olduğu için bu şart aranmaz. */
    private val TRIGGER_VERBS = setOf(
        "bas", "basar", "basıyor", "tıkla", "tıklar", "tuşuna", "tuşunu",
        "press", "click", "hit", "key",
    )

    /** W/A/S/D özellikle bare (fiilsiz) söylenince de çalışır - kullanıcı isteği bu yönde
     * ("wasd'den birisine bahsedeyim de basabilsin") ve zaten oyun sırasında sürekli tek
     * başına anılan hareket tuşlarıdır; diğer tek harfler (özellikle "a"/"e"/"o" gibi
     * gerçek Türkçe kelimeler) yine de [TRIGGER_VERBS] gerektirir. */
    private val ALWAYS_BARE_LETTERS = setOf("w", "a", "s", "d")

    /** "sohbeti aç <mesaj> yaz [gönder]" / "open chat <message> type [send]" kalıpları.
     * Türkçe ekli fiil biçimleri (ör. "açıyor") yerine emir kipi ("aç"/"yaz") beklenir;
     * sesli komutlarda doğal olan budur. */
    private val CHAT_PATTERN =
        Regex("""(sohbeti|sohbet|chat)\s+aç\s+(.+?)\s+yaz(\s+gönder)?$""")
    private val CHAT_PATTERN_EN =
        Regex("""(open\s+chat|chat)\s+(.+?)\s+type(\s+(and\s+)?send)?$""")

    // --- Bakış açısı (kamera) döndürme ---
    // "sağa/sağ bak", "sola/sol dön", "yukarı", "aşağı", "look right", "turn left"... ve
    // bunların herhangi bir kombinasyonu (çapraz) - bir bakış fiili olmadan tetiklenmez,
    // bu yüzden "sağ tık"/"right click" gibi mevcut tuş deyimleriyle çakışmaz (onlarda
    // "bak/dön/çevir"/"look/turn" fiili yoktur).
    private val LOOK_VERBS = setOf(
        "bak", "dön", "çevir", "döndür", "baksın", "dönsün", "çevirsin",
        "look", "turn", "rotate", "spin",
    )
    private val LOOK_RIGHT_WORDS = setOf("sağa", "sağ", "right")
    private val LOOK_LEFT_WORDS = setOf("sola", "sol", "left")
    private val LOOK_UP_WORDS = setOf("yukarı", "yukarıya", "yukari", "up")
    private val LOOK_DOWN_WORDS = setOf("aşağı", "aşağıya", "asagi", "down")

    // "çok az"/"a little" gibi çok kelimeli ifadeler, tek başına "çok"/büyük dönüş anlamına
    // gelen kelimelerle çakışmasın diye önce denenir.
    private val LOOK_TINY_PHRASES = listOf(
        "çok az", "çok hafif", "birazcık", "azıcık", "hafifçe", "hafif", "biraz",
        "a little", "a little bit", "a tiny bit", "slightly", "a bit",
    )
    private val LOOK_LARGE_PHRASES = listOf(
        "çok fazla", "fazlaca", "iyice", "büyük", "çok",
        "a lot", "all the way", "fully", "far", "big turn",
    )

    private const val LOOK_DELTA_TINY = 15
    private const val LOOK_DELTA_MEDIUM = 65
    private const val LOOK_DELTA_LARGE = 220

    private fun detectLook(normalized: String, rawTokens: List<String>): VoiceCommandResult.Look? {
        if (rawTokens.none { matchesAny(it, LOOK_VERBS) }) return null

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

    // --- Sesle buton oluşturma / düzenleme ---------------------------------------------
    //
    // "buton oluştur f5"          -> F5 yazan, F5 tuşuna basan bir buton ekler
    // "butonu sağa 5"             -> son eklenen butonu %5 sağa taşır
    // "buton sağ sol 5"           -> genişliğini %5 yapar (bir eksenin iki yönü = o eksenin boyutu)
    // "buton aşağı yukarı 8 sağ sol 5" -> yükseklik %8, genişlik %5
    //
    // Taşıma/boyutlandırma komutlarının hepsi cümlede "buton" kelimesini şart koşar.
    // Şart olmasa "yukarı 5" hem yukarı ok tuşu + 5 tuşu hem de buton taşıma olarak
    // okunurdu; "buton" kelimesi bu ikiliği tek başına çözüyor.

    private val CREATE_VERBS = setOf("oluştur", "oluşturt", "ekle", "yarat", "create", "add", "make")
    private val BUTTON_WORDS = setOf("buton", "butonu", "butonun", "butona", "tuş", "düğme", "button")

    /** Sözle söylenen sayılar; tanıyıcı bazen "5" bazen "beş" üretiyor. */
    private val NUMBER_WORDS: Map<String, Int> = mapOf(
        "bir" to 1, "iki" to 2, "üç" to 3, "dört" to 4, "beş" to 5,
        "altı" to 6, "yedi" to 7, "sekiz" to 8, "dokuz" to 9, "on" to 10,
        "on beş" to 15, "yirmi" to 20, "yirmi beş" to 25, "otuz" to 30, "kırk" to 40, "elli" to 50,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
    )

    /** Taşımada varsayılan adım, boyutlandırmada varsayılan boyut (ekran yüzdesi). */
    private const val DEFAULT_STEP_PERCENT = 5

    private fun parseNumber(token: String): Int? =
        token.toIntOrNull() ?: NUMBER_WORDS[token]

    /**
     * "buton oluştur <tuş>" kalıbı. Etiket ve tuş bağlaması, cümlede geçen ilk tuş adından
     * alınır: konuşurken "f5 olsun ismi ve f5 butonuna bassın" gibi uzun kurulan cümlelerde
     * de, kısa "buton oluştur f5" cümlesinde de aynı sonuç çıkar.
     */
    private fun detectCreateButton(tokens: List<String>): VoiceCommandResult.CreateButton? {
        if (tokens.none { it in BUTTON_WORDS }) return null
        if (tokens.none { it in CREATE_VERBS }) return null
        for (i in tokens.indices) {
            if (i + 1 < tokens.size) {
                val twoWord = "${tokens[i]} ${tokens[i + 1]}"
                KEY_NAMES[twoWord]?.let {
                    return VoiceCommandResult.CreateButton(buttonLabel(twoWord), it)
                }
            }
            KEY_NAMES[tokens[i]]?.let {
                return VoiceCommandResult.CreateButton(buttonLabel(tokens[i]), it)
            }
        }
        return null
    }

    /** Buton etiketi: söylenen tuş adı, kısa adlar büyük harfe çevrilerek ("f5" -> "F5"). */
    private fun buttonLabel(spokenKey: String): String =
        if (spokenKey.length <= 3) spokenKey.uppercase(Locale("tr")) else spokenKey

    /**
     * "buton sağa 5" / "buton sağ sol 5 aşağı yukarı 8" kalıpları.
     *
     * Bir eksenin iki yönü birlikte söylenirse (sağ+sol, yukarı+aşağı) o eksenin *boyutu*
     * ayarlanır; tek yön söylenirse buton o yöne *taşınır*. Sayı, o yön grubundan sonra
     * gelen ilk sayıdır; sayı söylenmezse [DEFAULT_STEP_PERCENT] kullanılır.
     */
    private fun detectButtonEdit(tokens: List<String>): List<VoiceCommandResult> {
        if (tokens.none { it in BUTTON_WORDS }) return emptyList()
        if (tokens.any { it in CREATE_VERBS }) return emptyList()

        // Yön kelimelerini ve onları izleyen sayıyı sırayla topla
        data class Hit(val right: Boolean, val left: Boolean, val up: Boolean, val down: Boolean, val amount: Int)

        var right = false
        var left = false
        var up = false
        var down = false
        var amount: Int? = null
        val hits = mutableListOf<Hit>()

        fun flush() {
            if (right || left || up || down) {
                hits += Hit(right, left, up, down, amount ?: DEFAULT_STEP_PERCENT)
            }
            right = false; left = false; up = false; down = false; amount = null
        }

        for (token in tokens) {
            when {
                token in LOOK_RIGHT_WORDS -> {
                    if (amount != null) flush()
                    right = true
                }
                token in LOOK_LEFT_WORDS -> {
                    if (amount != null) flush()
                    left = true
                }
                token in LOOK_UP_WORDS -> {
                    if (amount != null) flush()
                    up = true
                }
                token in LOOK_DOWN_WORDS -> {
                    if (amount != null) flush()
                    down = true
                }
                else -> parseNumber(token)?.let { if (right || left || up || down) amount = it }
            }
        }
        flush()
        if (hits.isEmpty()) return emptyList()

        val results = mutableListOf<VoiceCommandResult>()
        var width: Int? = null
        var height: Int? = null
        var dx = 0
        var dy = 0
        for (hit in hits) {
            when {
                hit.right && hit.left -> width = hit.amount
                hit.up && hit.down -> height = hit.amount
                hit.right -> dx += hit.amount
                hit.left -> dx -= hit.amount
                hit.down -> dy += hit.amount
                hit.up -> dy -= hit.amount
            }
        }
        if (width != null || height != null) results += VoiceCommandResult.ResizeButton(width, height)
        if (dx != 0 || dy != 0) results += VoiceCommandResult.MoveButton(dx, dy)
        return results
    }

    fun match(recognizedText: String): List<VoiceCommandResult> {
        val normalized = normalize(recognizedText)

        (CHAT_PATTERN.find(normalized) ?: CHAT_PATTERN_EN.find(normalized))?.let { m ->
            val message = m.groupValues[2].trim()
            if (message.isNotEmpty()) {
                return listOf(VoiceCommandResult.Chat(message))
            }
        }

        // Java/Kotlin regex \b Türkçe harfleri (ı, ş, ğ, ç, ö, ü) varsayılan olarak "kelime
        // karakteri" saymadığından \b burada kullanılmıyor; bunun yerine kelimelere ayırıp
        // (tek ve iki kelimelik) tam eşleşme aranıyor.
        val rawTokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }

        // Buton komutları tuş/bakış eşleştirmesinden önce denenir: "buton oluştur f5"
        // cümlesindeki "f5" aksi hâlde ayrıca F5 tuşuna basardı.
        detectCreateButton(rawTokens)?.let { return listOf(it) }
        detectButtonEdit(rawTokens).takeIf { it.isNotEmpty() }?.let { return it }

        detectLook(normalized, rawTokens)?.let { return listOf(it) }

        if (normalized in RELEASE_ALL_PHRASES || closestMatch(normalized, RELEASE_ALL_PHRASES) != null) {
            return listOf(VoiceCommandResult.ReleaseAll)
        }
        if (normalized in REPEAT_LAST_PHRASES || closestMatch(normalized, REPEAT_LAST_PHRASES) != null) {
            return listOf(VoiceCommandResult.RepeatLast)
        }

        val rawPairs = rawTokens.zipWithNext { a, b -> "$a $b" }

        // Aynı cümlede yalnızca tek bir aç/kapa yönü uygulanır (ör. "w bas a'yı bırak"
        // gibi karışık cümleler desteklenmez); "bırak" geçerse tüm eylemler bırakma olur.
        val isRelease = rawTokens.any { matchesAny(it, RELEASE_VERBS) } ||
                rawPairs.any { it in RELEASE_VERBS }
        // Tek harfli tuş adları için "niyet" sinyali: ya bas/tıkla fiili ya da (bırakma
        // durumunda) bırak fiili - ikisi de "bu bir rastgele hece değil, bilinçli bir tuş
        // adı" anlamına gelir.
        val hasTriggerVerb = isRelease || rawTokens.any { matchesAny(it, TRIGGER_VERBS) }

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
                val binding = lookupKeyName(twoWord, hasTriggerVerb)
                if (binding != null) {
                    results += toAction(binding, isRelease)
                    consumed[i] = true; consumed[i + 1] = true
                    i += 2
                    continue
                }
            }
            val token = tokens[i]
            val binding = lookupKeyName(token, hasTriggerVerb)
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

    // --- Esnek (yaklaşık) eşleştirme -------------------------------------------------
    //
    // Konuşma tanıma çıktısı nadiren tabloya birebir uyar: ek düşer/eklenir ("envanteri"
    // yerine "envanterı"), tanıyıcı harf karıştırır ("şift" / "shift"), kullanıcı kelimeyi
    // biraz farklı söyler. Bu yüzden tam eşleşme başarısız olduğunda kelimeler Levenshtein
    // mesafesiyle tablodaki adlara yaklaştırılır.
    //
    // Eşik bilerek dar tutuldu: kısa adlarda hiç, orta uzunlukta 1, uzun adlarda 2 harf.
    // Amaç sıradan konuşmanın yanlışlıkla tuşa basmasını önlemek - tek harfli tuş adları
    // (a/e/o gibi gerçek kelimeler) yaklaşık eşleştirmeye hiç sokulmaz.

    /** Bir kelimenin yaklaşık eşleşme için kabul edeceği en fazla harf farkı. */
    private fun maxDistanceFor(word: String): Int = when {
        word.length <= 3 -> 0
        word.length <= 6 -> 1
        else -> 2
    }

    /** Klasik Levenshtein düzenleme mesafesi; [limit] aşılırsa erken çıkar (limit + 1 döner). */
    private fun editDistance(a: String, b: String, limit: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    /**
     * [candidates] içinde [word]'e en yakın olanı döner; hiçbiri eşiğin içinde değilse null.
     * Beraberlik durumunda eşleşme belirsiz sayılır ve null dönülür - yanlış tuşa basmaktansa
     * hiç basmamak yeğdir.
     */
    private fun closestMatch(word: String, candidates: Iterable<String>): String? {
        val limit = maxDistanceFor(word)
        if (limit == 0) return null
        var best: String? = null
        var bestDistance = limit + 1
        var tied = false
        for (candidate in candidates) {
            // Tek harfli tuş adları gerçek kelimelerle çakışır, yaklaşık eşleşmeye girmezler
            if (candidate.length <= 3) continue
            val d = editDistance(word, candidate, limit)
            if (d > limit) continue
            when {
                d < bestDistance -> {
                    bestDistance = d; best = candidate; tied = false
                }
                d == bestDistance -> tied = true
            }
        }
        return if (tied) null else best
    }

    /**
     * Tuş bağlaması bulur. Tam eşleşme her zaman geçerlidir; yaklaşık eşleşme yalnızca
     * cümlede bir niyet fiili ("bas", "tıkla", "bırak"...) varsa denenir.
     *
     * Bu şart olmadan yaklaşık eşleşme sıradan konuşmayı tuşa çevirir: "yukarıda bekle"
     * cümlesindeki "yukarıda", "yukarı" tuş adına bir harf uzaklıkta olduğu için yukarı ok
     * tuşuna basardı. Fiil şartıyla bu tür cümleler eşleşmez, "şifte bas" gibi bilinçli ama
     * bozuk telaffuz edilmiş komutlar eşleşmeye devam eder.
     */
    private fun lookupKeyName(word: String, allowFuzzy: Boolean): String? {
        KEY_NAMES[word]?.let { return it }
        if (!allowFuzzy) return null
        return closestMatch(word, KEY_NAMES.keys)?.let { KEY_NAMES[it] }
    }

    /** Bir kelimenin verilen ifade kümesine (fiil listeleri gibi) yaklaşık olarak uyup uymadığı. */
    private fun matchesAny(word: String, phrases: Set<String>): Boolean =
        word in phrases || closestMatch(word, phrases) != null
}
