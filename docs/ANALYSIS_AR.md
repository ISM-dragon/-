# تحليل وتطوير مشروع ISM (Opus Pro AI)

> إعداد: مراجعة تقنية شاملة + تطويرات مطبَّقة في الجلسة الحالية
> التاريخ: 2026-09-09

---

## 1) نظرة عامة على المشروع

منتج **ISM** (الاسم التقني القديم: Opus Pro AI) هو تطبيق لإعادة توظيف الفيديو بذكاء اصطناعي بنقرة واحدة:
تحليل فيديو طويل، اكتشاف اللقطات القابلة للانتشار، توليد ترجمات حركية (karaoke)، حساب «درجة الانتشار»
(virality score)، تصدير بصيغ عمودية، ومقارنة تنافسية مع منشئين آخرين، مع لوحة نشر اجتماعي عبر بوابة
(Gateway) خارجية.

يتكوّن المستودع من أربع ركائز:

| المكوّن | المسار | التقنية | الدور |
|---|---|---|---|
| تطبيق أندرويد | `app/` | Kotlin + Jetpack Compose + Room + WorkManager + Media3 + Retrofit/OkHttp | واجهة المستخدم الكاملة (Compose)، قاعدة بيانات محلية، معالجة الخلفية، التصدير |
| خط المعالجة المحلي | `pipeline/` | Python + WhisperX / ONNX / FFmpeg | التحليل العميق (ASR، فصل المتحدثين، كاميرا، أحداث صوتية، ترجمات) — يعمل على سطح المكتب |
| بوابة المعالجة والنشر | `gateway/` | Python FastAPI | واجهة HTTP موحّدة للمعالجة البعيدة والنشر الاجتماعي (OAuth، جدولة، حدود يومية) |
| ملحقات التكامل | `.github/`، `app/src-tauri` | CI، Tauri | بناء APK تلقائي، قشرة سطح المكتب |

## 2) بنية التطبيق الداخلية (Android)

- **واجهة Compose بالكامل** بلا أنشطة متعددة: `MainActivity` واحدة + شريط تنقل سفلي بستة تبويبات
  (الرئيسية، لوحة الاستخدام، الاستوديو، المقارنة، المشاريع، بوابة التواصل) + إعدادات المزودين.
- **قاعدة بيانات Room** (حاليًا الإصدار 4): `projects`، `clips`، `video_processing_cache`،
  `viral_score_metrics`، `repurposing_history`، `video_processing_drafts`، `processing_jobs`،
  `pipeline_checkpoints`، `ai_usage`.
- **طبقة مجال نظيفة جزئيًا**: `domain/ai` (موجّه مزودين متعدد مع failover)، `domain/analysis`
  (محركات خالصة خالية من Android: تخطيط الترجمة، التحقق، كشف المرشحين، درجات الانتشار)،
  `domain/pipeline` (خط أنابيب موحّد المراحل)، `domain/security` (تشفير AES-GCM عبر Android Keystore).
- **معالجة الخلفية**: `VideoProcessingWorker` (WorkManager) ينفّذ إمّا المعالجة المحلية عبر
  `ProductionVideoPipeline` أو المعالجة البعيدة عبر `ProcessingGatewayClient`، مع إشعارات وإعادة محاولة
  أسية وتتبّع مراحل.
- **التصدير الفعلي على الجهاز**: `Media3VideoProcessor` يقتطع المقطع ويضبط النسبة ويحرق الترجمات
  والعلامة المائية عبر Media3 Transformer.

## 3) نقاط القوة التي رصدتها

1. **فصل المسؤوليات في المعالجة**: «القرار الدلالي» (Gemini) منفصل عن «التنفيذ الحتمي» (Media3 على
   الجهاز / Python على الخادم) — قرار معماري سليم.
2. **توجيه ذكي للمزودين** (`IntelligentAiRouter`) مع قدرات معلنة، مهلة قصوى، إعادة محاولة بتراجع
   أُسّي، وعدم اختراع نجاح وهمي عند الفشل.
3. **أمان جيد للمفاتيح**: `SecureKeyManager` يستخدم Android Keystore (AES-256-GCM) لتشفير مفاتيح
   API ورمز بوابة التواصل، مع خيار fallback للتوافق.
4. **قيود SSRF وHTTPS في بوابة التواصل**: رفض العناوين المحلية/الخاصة وفرض HTTPS خارج الشبكة المحلية
   على مستوى العميل والخادم (`validate_public_source`).
5. **دعم لغتين في المحتوى**: نصوص عربية/إنجليزية، كشف RTL في محرك الترجمة، و`supportsRtl` مفعّل.
6. **CI موجود** يبني APK (لكنه كان أحمر — انظر المشاكل).
7. **التوثيق جيد**: `IMPROVEMENTS.md` و`SOCIAL-HUB.md` و`ANDROID.md` تصف العقد والاتجاه بدقة.

## 4) المشاكل الجوهرية المكتشفة (والأدلة)

### 4.1 🔴 حرجة: آخر commit على main يكسر البناء (Build Broken)

الـ commit `aeedf00 "feat(db): replace processing jobs with video drafts"` أزال ثلاثة جداول من
مخطط Room (`processing_jobs`، `pipeline_checkpoints`، `ai_usage`) **دون تحديث المستهلكين**:

- `VideoProcessingWorker` يستدعي `database.processingJobDao()` الذي لم يعد موجودًا.
- الشاشات تستدعي دوال `OpusRepository` حُذفت بالكامل:
  `processingJobs`، `observeProcessingJob`، `enqueueVideoProcessing`، `cancelVideoProcessing`،
  `observeRecentAiUsageAggregates`، `savePipelineCheckpoint`، `transcribeLocalMediaDetailed`،
  `importRemoteProcessingResult`، `exportClipToFile`، `saveExportToMediaStore`،
  `saveGatewayConfig`، `testGatewayConnection`، `refreshGatewayStatus`، وغيرها (11+ استدعاء مفقودًا).
- `VideoProcessingDraftEntity` (البديل المقصود) بقي **هيكلًا ميتًا**: لا أحد يكتب فيه أو يقرؤه من الواجهة.

**الدليل الموضوعي**: تشغيل `Build and Release APK` على main بعد ذلك الـ commit انتهى **بالفشل**
(run #32981778744، 2026-08-27) — أي أن main أحمر منذ 13 يومًا، والمشروع لا يُصرَّف ولا يُبنى.

### 4.2 🟠 متوسطة: ملفات/كود يتيم (dead code)

`AiUsageDao.kt`، `PipelineCheckpointDao.kt`، `AiUsageEntity` وغيرها بقيت كملفات بعد إخراجها من
المخطط (كانت ستُحذف بلا أثر لو اكتمل الـ refactor).

### 4.3 🟠 متوسطة: لا توجد اختبارات آلية إطلاقًا

`app/src/test` غير موجودة؛ منطق تحليل حرج (تجميع كلمات الترجمة، قواعد التحقق من المقاطع،
الاكتشاف، التقييم) بلا أي حماية من الانحدار.

### 4.4 🟡 ملاحظات أخرى

- **مسودات بلا قيمة حقيقية**: فكرة «استئناف مهمة انقطعت» ممتازة لكنها لم تصل للواجهة.
- **مزامنة قاعدة البيانات**: `fallbackToDestructiveMigration` يمسح بيانات المستخدمين عند أي تغيير
  في المخطط (مقبول في نسخة تجريبية، لكنه خطير قبل الإصدار).
- CI لا يعمل على الفروع (push إلى main فقط) فلا يمنع كسر main مستقبلًا.
- بوابة Android مشروطة على المعالجة البعيدة (pipeline بايثون لا يعمل داخل Android) — موثّق بذكاء في
  `IMPROVEMENTS.md` لكن يجب إبقاء خطة معالجة سحابية.

## 5) التطويرات المنفَّذة في هذه الجلسة

### 5.1 إصلاح البناء (الاكتمال بدل التراجع)
- **`OpusDatabase`**: إعادة تسجيل `ProcessingJobEntity` و`PipelineCheckpointEntity` و`AiUsageEntity`
  مع DAOs الثلاثة (إصدار المخطط 4) — فعاد `VideoProcessingWorker` والشاشات إلى العمل.
- **`OpusRepository`**: إعادة بناء كامل السطح المفقود (حوالي +480 سطرًا):
  - الطابور: `processingJobs`، `observeProcessingJob`، `enqueueVideoProcessing` (تخزين الصف +
    الجدولة + تنظيف المهام الأقدم من أسبوع)، `cancelVideoProcessing`.
  - نقاط التفتيش: `savePipelineCheckpoint`.
  - النسخ المحلي: `transcribeLocalMediaDetailed` (OpenAI/Groq عبر `SpeechToTextService`).
  - استيراد نتائج البوابة: `importRemoteProcessingResult` (إنشاء مشروع + مقاطع حقيقية).
  - التصدير: `exportClipToFile` (Media3: قص + نسبة أبعاد + ترجمات محروقة + علامة ISM)
    و`saveExportToMediaStore` (MediaStore API 29+ ومسار قديم للأجهزة الأقدم).
  - الاستخدام: `observeRecentAiUsageAggregates` + `persistAiUsage`.
  - بوابة التواصل: `gatewayConfig/gatewaySnapshot/gatewayError` + حفظ مشفّر عبر `SecureKeyManager`
    بنفس مفاتيح `SharedPreferences` التي يستخدمها العامل (`ism_gateway_settings`).
- **`PipelineWorkScheduler`**: تمرير `KEY_JOB_ID` إلى العامل حتى لا ينشئ العامل jobId جديدًا
  ويترك صفًا مكررًا.

### 5.2 تفعيل «المسودات» كطبقة autosave/resume حقيقية (إكمال نيّة الـ commit السابق)
- `VideoProcessingDraftEntity.jobId` + استعلامات upsert ثابتة بالمعرّف في الـ DAO.
- `VideoProcessingWorker` يعكس كل تقدّم/مرحلة إلى المسودة ويعلّمها منتهية عند الحالات النهائية
  (نجاح محلي/بعيد، فشل، إلغاء، إعادة محاولة).
- شاشة الرفع: شارة «مسودة معالجة غير مكتملة» تعرض العنوان والمرحلة والنسبة، مع زر
  **استئناف الإعدادات** (يعيد نمط الترجمة والقالب) وزر **حذف المسودة**؛ تُحفظ مسودة لكل دفعة
  رفع وتُغلق تلقائيًا عند اكتمال/فشل المهمة الملاحَظة.

### 5.3 بيانات حقيقية في Usage Dashboard
- `IntelligentAiRouter` يقبل `usageSink` اختياريًا؛ الـ repository يمرر دالة حفظ في Room، فتصبح
  لوحة الاستخدام (توكنز/تكلفة/كمون لكل مزود ونموذج) مبنية على استهلاك فعلي بعد كل طلب موجَّه.

### 5.4 اختبارات آلية (JVM خالصة — بلا محاكي)
- `CaptionLayoutEngineTest`: التجميع، حدود الكلمات/الأحرف، الحدود الدلالية، RTL، أنماط التمييز،
  الحوافز الآمنة.
- `AnalysisValidatorTest`: قواعد حدود المقاطع والدرجات والنصوص والتطبيع.
- تُشغَّل تلقائيًا في CI قبل بناء الـ APK (`:app:testDebugUnitTest`).

### 5.5 CI وقائي
- `build-apk.yml` يبني الآن على **كل pull request** + يجري اختبارات JVM، فتكرار كسر main كهذا
  سيُرفض قبل الدمج.

## 6) خارطة طريق مقترحة (مرتبة بالأولوية)

| الأولوية | المجال | التوصية |
|---|---|---|
| P0 | المعالجة البعيدة | نشر بوابة معالجة إنتاجية وربط OAuth كامل (callback + deep link + state) |
| P0 | الجدولة | جعل البوابة مصدر الحقيقة للجدولة مع idempotency keys ومزامنة خادم→جهاز |
| P1 | المخطط | تصدير مخطط Room (exportSchema) وكتابة Migrations بدل `fallbackToDestructiveMigration` |
| P1 | الحجم | استبعاد موارد سطح المكتب من APK وتوزيع AAB موقَّع |
| P1 | القصاصات | تفعيل «استئناف بذات الفيديو» بحفظ نسخة مُدارة من المصدر مع المسودة (بدل حذفها فورًا) |
| P2 | التعريب | استخراج النصوص إلى موارد strings وتوطين كامل RTL + تواريخ محلية |
| P2 | الجودة | اختبارات Robolectric للشاشات الحرجة + اختبارات تكامل للبوابة عبر httpx |
| P2 | الخصوصية | شاشة حذف البيانات/التصدير وضوابط استبقاء للمقاطع |

## 7) البناء والتحقق

```bash
# بناء APK + اختبارات (يُنفَّذ في CI على كل PR)
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon

# بوابة التواصل (اختبارات سريعة)
python3 -m venv .venv && .venv/bin/pip install -r gateway/requirements.txt
.venv/bin/python -m pytest gateway/tests -q
```

**حالة التحقق من هذا التقرير**: فتح PR (#2) من الفرع `arena/01a08395-repo` نحو `main`؛
تشغيل `Build and Release APK` (بناء APK + اختبارات JVM) هو دليل الإصلاح — بينما كان نفس الـ workflow
يفشل على آخر commit في main.
