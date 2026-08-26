package com.example.domain.analysis

import com.example.data.model.Clip
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Intelligent Machine Learning & Heuristic Engine for short-form video virality analysis.
 * Evaluates pacing, 0-3s hook retention, keyword density, emotional cadence, and algorithmic platform fit.
 */
object ViralityHeuristicEngine {

    private val VIRAL_POWER_KEYWORDS = setOf(
        // High impact triggers & contrarian seeds
        "secret", "mistake", "never", "always", "truth", "hack", "algorithm", "strategy", "stop",
        "proven", "rule", "protocol", "framework", "fast", "money", "million", "ai", "growth",
        "power", "psychology", "focus", "habit", "system", "stealth", "leverage", "scale",
        "why", "how", "warning", "trap", "unlocked", "gamechanger", "insane", "viral",
        // Arabic high-impact triggers
        "سر", "خطأ", "انتبه", "طريقة", "استراتيجية", "نصيحة", "ذكاء", "نمو", "أرباح", "حقيقة",
        "فرصة", "تجربة", "تطوير", "قانون", "عادة", "بروتوكول", "تركيز", "دوبامين", "تحذير"
    )

    private val HIGH_SEARCH_TERMS = setOf(
        "tiktok", "reels", "shorts", "ai", "gemini", "chatgpt", "deepseek", "business",
        "productivity", "coding", "marketing", "freelance", "finance", "crypto", "mindset",
        "automation", "youtube", "podcast", "motivation", "algorithm", "content", "creator",
        "تجارة", "برمجة", "تسويق", "صناعة_محتوى", "عمل_حر", "ريادة_أعمال", "تقنية"
    )

    private val CONTRARIAN_HOOK_PATTERNS = listOf(
        "nobody tells you", "the biggest mistake", "stop doing", "here is why",
        "most people fail", "99% of people", "if you want to", "the secret to",
        "why you should never", "don't do this", "this will change", "how i achieved",
        "لا أحد يخبرك", "أكبر خطأ", "توقف عن", "السر الحقيقي", "لماذا يفشل 90%", "إياك أن تفعل"
    )

    /**
     * Complete heuristic analysis of a Clip object or raw transcript segment.
     */
    fun analyzeClip(clip: Clip): ViralityAnalysisResult {
        return analyzeTranscript(
            title = clip.title,
            transcript = clip.transcript,
            durationSec = if (clip.durationSec > 0) clip.durationSec else (clip.endTimeSec - clip.startTimeSec).coerceAtLeast(15)
        )
    }

    /**
     * Primary algorithmic evaluation of video script and timing parameters.
     */
    fun analyzeTranscript(
        title: String,
        transcript: String,
        durationSec: Int = 35
    ): ViralityAnalysisResult {
        val safeDurationSec = durationSec.coerceAtLeast(10)
        val words = extractCleanWords(transcript)
        val wordCount = words.size.coerceAtLeast(1)

        // 1. Pacing & Cadence Evaluation
        val pacing = computePacingAnalysis(words, safeDurationSec)

        // 2. 0-3 Second Hook & Audience Retention Analysis
        val hookRetention = computeHookRetentionAnalysis(words, transcript, safeDurationSec, pacing)

        // 3. Keyword Density & Searchability Index
        val keywordDensity = computeKeywordDensityAnalysis(words, title)

        // 4. Emotional Arc & Climax Calculation
        val emotional = computeEmotionalResonance(words, transcript, safeDurationSec)

        // 5. Composite Virality Score Formulation
        // Weights: Hook (30%), Pacing (25%), Keyword Density (20%), Emotional/Shareability (15%), Punchline (10%)
        val rawCompositeScore = (
            hookRetention.score * 0.30f +
            pacing.score * 0.25f +
            keywordDensity.score * 0.20f +
            emotional.shareabilityScore * 0.15f +
            emotional.punchlineClimaxScore * 0.10f
        ).roundToInt().coerceIn(45, 99)

        val tierGrade = when {
            rawCompositeScore >= 95 -> "S+"
            rawCompositeScore >= 90 -> "S"
            rawCompositeScore >= 80 -> "A+"
            rawCompositeScore >= 70 -> "A"
            else -> "B"
        }

        val algorithmTier = when {
            rawCompositeScore >= 95 -> "🔥 TOP 1% ALGORITHM TIER"
            rawCompositeScore >= 90 -> "⚡ ULTRA VIRAL POTENTIAL"
            rawCompositeScore >= 80 -> "🚀 HIGH ENGAGEMENT SWEETSPOT"
            rawCompositeScore >= 70 -> "📈 ABOVE AVERAGE MOMENTUM"
            else -> "💡 OPTIMIZATION NEEDED"
        }

        val summaryInsight = buildSummaryInsight(rawCompositeScore, pacing, hookRetention, keywordDensity)
        val platformFit = computePlatformFit(rawCompositeScore, pacing, hookRetention, safeDurationSec)
        val recommendations = generateRecommendations(pacing, hookRetention, keywordDensity, emotional)

        return ViralityAnalysisResult(
            overallScore = rawCompositeScore,
            tierGrade = tierGrade,
            algorithmTier = algorithmTier,
            summaryInsight = summaryInsight,
            pacing = pacing,
            hookRetention = hookRetention,
            keywordDensity = keywordDensity,
            emotionalResonance = emotional,
            platformFit = platformFit,
            recommendations = recommendations
        )
    }

    private fun extractCleanWords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9ء-ي\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    private fun computePacingAnalysis(words: List<String>, durationSec: Int): PacingAnalysis {
        val durationMinutes = durationSec / 60.0
        val wpm = if (durationMinutes > 0) words.size / durationMinutes else 150.0
        val avgWordLen = if (words.isNotEmpty()) words.sumOf { it.length }.toFloat() / words.size else 5.0f

        // Ideal viral WPM sweetspot is 165 - 195 words per minute
        val wpmScore = when {
            wpm in 165.0..195.0 -> 98
            wpm in 150.0..215.0 -> 90
            wpm in 130.0..230.0 -> 82
            wpm in 110.0..250.0 -> 72
            else -> 60
        }

        val pausesPerMinute = (durationSec / 12f).coerceIn(1.5f, 6.0f)
        val cadenceScore = (wpmScore * 0.85f + (100 - (pausesPerMinute * 4))).roundToInt().coerceIn(55, 99)

        val category = when {
            wpm >= 195 -> "سرعة فائقة مكثفة (High-Energy Punchy)"
            wpm >= 160 -> "إيقاع فيروسي مثالي (Optimal Viral Cadence)"
            wpm >= 130 -> "سرد قصصي متوازن (Engaging Narrative)"
            else -> "إيقاع بطيء يتطلب تسريعاً (Sluggish / Low Pacing)"
        }

        val diagnosis = when {
            wpm in 160.0..195.0 -> "سرعة الكلام (${wpm.toInt()} كلمة/دقيقة) ممتازة وتتوافق مع تفضيلات خوارزميات TikTok و Shorts لمنع التخطي السريع."
            wpm > 195.0 -> "الإيقاع سريع جداً (${wpm.toInt()} ك/د)، مما يمنح طاقة عالية لكن ينصح بإضافة كابشن ملون لمساعدة القراءة."
            else -> "الإيقاع منخفض نسبياً (${wpm.toInt()} ك/د). يُفضل تسريع المقطع بمقدار 1.1x أو إزالة الوقفات الصامتة لرفع التفاعل."
        }

        return PacingAnalysis(
            score = cadenceScore,
            wordsPerMinute = (wpm * 10.0).roundToInt() / 10.0,
            totalWordCount = words.size,
            durationSec = durationSec,
            pacingCategory = category,
            tempoConsistencyScore = cadenceScore,
            averageWordLength = avgWordLen,
            pausesPerMinute = pausesPerMinute,
            diagnosis = diagnosis
        )
    }

    private fun computeHookRetentionAnalysis(
        words: List<String>,
        rawTranscript: String,
        durationSec: Int,
        pacing: PacingAnalysis
    ): HookRetentionAnalysis {
        val firstWordsCount = min(words.size, (pacing.wordsPerMinute / 60.0 * 3.5).roundToInt().coerceAtLeast(8))
        val firstWords = words.take(firstWordsCount)
        val firstThreeSecText = firstWords.joinToString(" ").lowercase()
        val rawLower = rawTranscript.lowercase()

        var hookScoreBonus = 0
        var detectedType = "Direct Narrative Hook"

        // Check for interrogative start (Questions grab attention immediately)
        if (firstThreeSecText.startsWith("why") || firstThreeSecText.startsWith("how") ||
            firstThreeSecText.startsWith("what") || firstThreeSecText.startsWith("لماذا") ||
            firstThreeSecText.startsWith("كيف") || firstThreeSecText.startsWith("هل")
        ) {
            hookScoreBonus += 15
            detectedType = "سؤال يفتح فجوة فضول (Curiosity Gap)"
        }

        // Check for contrarian patterns
        for (pattern in CONTRARIAN_HOOK_PATTERNS) {
            if (rawLower.take(120).contains(pattern)) {
                hookScoreBonus += 20
                detectedType = "كسر النمط وصدمة مفاهيم (Pattern Interrupt)"
                break
            }
        }

        // Check for numbers or statistics
        if (Regex("\\b\\d+%?\\b").containsMatchIn(firstThreeSecText)) {
            hookScoreBonus += 12
            detectedType = "أرقام وإحصائيات دقيقة (High-Impact Stat)"
        }

        // Check for viral power seeds in hook
        val viralSeedsInHook = firstWords.count { VIRAL_POWER_KEYWORDS.contains(it) }
        hookScoreBonus += (viralSeedsInHook * 5).coerceAtMost(15)

        val hookScore = (72 + hookScoreBonus).coerceIn(60, 99)

        // Generate synthetic predictive retention curve
        val retentionPoints = mutableListOf<RetentionPoint>()
        retentionPoints.add(RetentionPoint(0f, 100, "بداية المقطع"))
        val threeSecRetention = (100 - (100 - hookScore) * 0.35f).roundToInt().coerceIn(78, 98)
        retentionPoints.add(RetentionPoint(3f, threeSecRetention, "الخطاف (3 ثوانٍ)"))

        val tenSecRetention = (threeSecRetention - (100 - pacing.score) * 0.18f).roundToInt().coerceIn(68, 92)
        retentionPoints.add(RetentionPoint(10f, tenSecRetention, "الثبات السردي (10 ثوانٍ)"))

        val midSec = durationSec * 0.5f
        val midRetention = (tenSecRetention - 8).coerceIn(58, 85)
        retentionPoints.add(RetentionPoint(midSec, midRetention, "منتصف الفيديو"))

        val endRetention = (midRetention - 7).coerceIn(50, 78)
        retentionPoints.add(RetentionPoint(durationSec.toFloat(), endRetention, "خاتمة المقطع"))

        val dropOffRisk = if (hookScore >= 90) 8.5f else 3.2f

        val explanation = when {
            hookScore >= 90 -> "الخطاف في أول 3 ثوانٍ استثنائي ($hookScore%)، يقدم طرحاً فورياً يجبر المشاهد على التوقف عن التمرير دون أي مقدمات مملة."
            hookScore >= 80 -> "الخطاف قوي ($hookScore%) ويبدأ بفكرة جذابة، مع فرصة لتكثيف المؤثرات البصرية في أول ثانيتين لرفع الاحتفاظ لأكثر من 90%."
            else -> "الخطاف تقليدي ($hookScore%). ننصح ببدء المقطع فوراً بالنتيجة الصادمة أو السؤال المثير."
        }

        return HookRetentionAnalysis(
            score = hookScore,
            firstThreeSecondsWordCount = firstWordsCount,
            hookType = detectedType,
            hookStrengthScore = hookScore,
            dropOffRiskSec = dropOffRisk,
            retentionCurve = retentionPoints,
            explanation = explanation
        )
    }

    private fun computeKeywordDensityAnalysis(words: List<String>, title: String): KeywordDensityAnalysis {
        val totalWords = words.size.coerceAtLeast(1)
        val keywordFrequency = mutableMapOf<String, Int>()

        for (word in words) {
            if (word.length >= 3 && (VIRAL_POWER_KEYWORDS.contains(word) || HIGH_SEARCH_TERMS.contains(word))) {
                keywordFrequency[word] = keywordFrequency.getOrDefault(word, 0) + 1
            }
        }

        val totalViralKeywords = keywordFrequency.values.sum()
        val densityPercent = (totalViralKeywords.toFloat() / totalWords.toFloat()) * 100f

        val detectedList = keywordFrequency.entries
            .sortedByDescending { it.value }
            .take(6)
            .map { entry ->
                val cat = when {
                    VIRAL_POWER_KEYWORDS.contains(entry.key) -> "محفز فيروسي (Power Trigger)"
                    HIGH_SEARCH_TERMS.contains(entry.key) -> "بحث مرتفع (High Search Volume)"
                    else -> "مفتاح سردي (Key Anchor)"
                }
                ViralKeywordInfo(
                    word = entry.key,
                    occurrences = entry.value,
                    category = cat,
                    weight = min(1.0f, entry.value * 0.3f)
                )
            }

        // Optimal keyword density is 4% - 8%
        val densityScore = when {
            densityPercent in 3.5f..8.5f -> 95
            densityPercent in 2.0f..11.0f -> 85
            densityPercent in 1.0f..14.0f -> 75
            else -> 62
        }

        val recommendedTags = mutableListOf("#shorts", "#viral", "#foryou", "#fyp")
        detectedList.take(3).forEach {
            recommendedTags.add("#${it.word}")
        }
        if (title.isNotBlank()) {
            val titleWords = extractCleanWords(title).filter { it.length > 4 }
            titleWords.take(2).forEach { recommendedTags.add("#$it") }
        }

        val diagnosis = if (densityPercent in 3.5f..8.5f) {
            "كثافة الكلمات المفتاحية مثالية (${"%.1f".format(densityPercent)}%) مما يساعد خوارزميات محركات البحث (SEO) على تصنيف المقطع بدقة واقتراحه للجمهور المستهدف."
        } else if (densityPercent < 3.5f) {
            "كثافة الكلمات المفتاحية منخفضة (${"%.1f".format(densityPercent)}%). يُفضل تضمين مصطلحات قوية ترتبط بالمجال لتعزيز الاقتراح التلقائي."
        } else {
            "كثافة الكلمات المفتاحية مرتفعة جداً (${"%.1f".format(densityPercent)}%)."
        }

        return KeywordDensityAnalysis(
            score = densityScore,
            densityPercentage = (densityPercent * 10.0).roundToInt() / 10.0f,
            totalKeywordsCount = totalViralKeywords,
            searchabilityIndex = densityScore,
            detectedViralKeywords = detectedList,
            recommendedTags = recommendedTags.distinct(),
            diagnosis = diagnosis
        )
    }

    private fun computeEmotionalResonance(
        words: List<String>,
        transcript: String,
        durationSec: Int
    ): EmotionalResonanceAnalysis {
        val textLower = transcript.lowercase()
        val emotionalSeeds = listOf("incredible", "shocking", "amazing", "secret", "love", "fear", "money", "freedom", "success", "fail", "صدمة", "نجاح", "حرية", "قوة", "فشل", "تحدي")
        val matchCount = emotionalSeeds.count { textLower.contains(it) }

        val emotionalScore = (74 + matchCount * 6).coerceIn(65, 98)
        val curiosityScore = (76 + matchCount * 5).coerceIn(68, 97)
        val shareabilityScore = (emotionalScore * 0.6f + curiosityScore * 0.4f).roundToInt().coerceIn(60, 99)
        val punchlineScore = (shareabilityScore - 2).coerceIn(60, 96)

        return EmotionalResonanceAnalysis(
            emotionalScore = emotionalScore,
            curiosityGapScore = curiosityScore,
            shareabilityScore = shareabilityScore,
            punchlineClimaxScore = punchlineScore,
            punchlineTimeSec = durationSec * 0.88f,
            targetAudience = "جمهور السوشيال ميديا المهتم بالمحتوى السريع والملهم (Short-form consumers)"
        )
    }

    private fun computePlatformFit(overallScore: Int, pacing: PacingAnalysis, hook: HookRetentionAnalysis, durationSec: Int): PlatformFitScores {
        val tiktokFit = (overallScore * 0.5f + hook.score * 0.3f + pacing.score * 0.2f).roundToInt().coerceIn(60, 99)
        val reelsFit = (overallScore * 0.6f + (if (durationSec <= 60) 15 else -5) + hook.score * 0.25f).roundToInt().coerceIn(58, 98)
        val shortsFit = (overallScore * 0.55f + pacing.score * 0.3f + hook.score * 0.15f).roundToInt().coerceIn(62, 99)

        return PlatformFitScores(
            tiktokScore = tiktokFit,
            reelsScore = reelsFit,
            shortsScore = shortsFit
        )
    }

    private fun buildSummaryInsight(
        overallScore: Int,
        pacing: PacingAnalysis,
        hook: HookRetentionAnalysis,
        keywords: KeywordDensityAnalysis
    ): String {
        return when {
            overallScore >= 90 -> "الذكاء الاصطناعي يتوقع وصول هذا المقطع إلى قائمة المقاطع الرائجة بفضل قوة الخطاف (${hook.score}%) وسرعة الإيقاع (${pacing.wordsPerMinute.toInt()} ك/د) مع كثافة سيو ممتازة (${keywords.densityPercentage}%)."
            overallScore >= 80 -> "المقطع يمتلك تماسكاً ممتازاً وسرعة سردية ملائمة لخوارزميات TikTok و Shorts، مع فرصة لتحسين سرعة الثواني الأولى لرفع معدل الانتشار."
            else -> "المحتوى واعد لكن يحتاج إلى تعديل سريع في الخطاف الافتتاحي وضبط الإيقاع لضمان عدم تخطي المشاهدين في أول 3 ثوانٍ."
        }
    }

    private fun generateRecommendations(
        pacing: PacingAnalysis,
        hook: HookRetentionAnalysis,
        keywords: KeywordDensityAnalysis,
        emotional: EmotionalResonanceAnalysis
    ): List<ViralityRecommendation> {
        val list = mutableListOf<ViralityRecommendation>()

        if (hook.score < 92) {
            list.add(
                ViralityRecommendation(
                    title = "تعزيز الخطاف في أول ثانيتين",
                    impactPoints = 6,
                    category = "Hook",
                    actionableStep = "قص أول ثانيتين من الصمت أو المقدمة والبدء مباشرة بالسؤال أو الجملة الجاذبة."
                )
            )
        }

        if (pacing.wordsPerMinute < 155) {
            list.add(
                ViralityRecommendation(
                    title = "تسريع وتيرة السرد والكلام (1.1x)",
                    impactPoints = 5,
                    category = "Pacing",
                    actionableStep = "تسريع الصوت بنسبة 8% إلى 12% للاقتراب من النطاق الفيروسي (170 كلمة/دقيقة)."
                )
            )
        }

        if (keywords.densityPercentage < 4.0f) {
            list.add(
                ViralityRecommendation(
                    title = "تضمين هاشتاغات وكلمات سيو قوية",
                    impactPoints = 4,
                    category = "SEO",
                    actionableStep = "أضف الكلمات المفتاحية المقترحة في الوصف والـ Caption لتعزيز ظهور المقطع في محركات بحث TikTok."
                )
            )
        }

        list.add(
            ViralityRecommendation(
                title = "إضافة مشاهد B-Roll سريعة عند الثانية 00:04",
                impactPoints = 4,
                category = "Visual",
                actionableStep = "إدراج لقطة داعمة أو مؤثر بصري في نقطة انخفاض الانتباه المتوقعة (عند 4 ثوانٍ) للحفاظ على المشاهد."
            )
        )

        return list
    }
}
