# ISM Social Gateway

هذا المجلد يحتوي Gateway API محليًا خاصًا بتطبيق ISM. يقدّم لوحة تحكم داخلية، SQLite، جدولة تلقائية، حالات المنشورات، وإعادة معالجة أولية في **Mock Mode**. لا يحتوي على أسرار منصات التواصل ولا يدّعي أن موفري Instagram أو Facebook أو TikTok أو YouTube أو X مهيؤون للإنتاج.

## التشغيل المحلي

من جذر المستودع:

```bash
cd gateway
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
PROVIDER_MODE=mock uvicorn main:app --host 0.0.0.0 --port 8787
```

افتح `http://127.0.0.1:8787` لعرض لوحة التحكم. في تطبيق ISM، ضع عنوان Gateway في حقل **Gateway API URL**. في APK Debug يمكن استخدام عنوان LAN مثل `http://192.168.1.10:8787` إذا كان الهاتف والخادم على الشبكة نفسها؛ أما العنوان العام فيجب أن يكون HTTPS.

## مكتبة المصادر v0.6

تدعم Gateway الآن معاينة وتنزيل رابط فيديو مفرد أو قناة أو قائمة تشغيل عبر `POST /v1/sources/inspect` و`POST /v1/sources/download`. يتابع التطبيق المهمة عبر `GET /v1/sources/jobs/{id}`، وتُخدم الملفات الناتجة من `GET /v1/sources/jobs/{id}/media/{filename}`. يطبق Gateway حدًا أقصى قدره 1000 عنصر للمهمة، ويمنع عناوين localhost والشبكات الخاصة لتقليل مخاطر SSRF. استخدم هذه الميزة فقط للمصادر التي يملك المستخدم حق تنزيلها أو معالجتها، والتزم بشروط كل منصة.

## Security and reliability v0.9

يستخدم تطبيق الواجهة عنوان Gateway في `localStorage`، بينما يُحفظ bearer token في `sessionStorage` فقط ويُرحّل الرمز القديم من التخزين الدائم ثم يُحذف. يقيّد Gateway CORS افتراضياً بأصول Tauri/التطوير، ويجب ضبط `CORS_ORIGINS` صراحة في الإنتاج. توجد اختبارات انحدار لرفض `localhost` وعناوين الشبكات الخاصة ومصادر `file://`، كما تُستخدم حدود الملفات وروابط الوسائط غير القابلة للتخمين. لا يعتمد النظام على تدوير IP أو proxy لتجاوز حظر المنصات؛ يستخدم بدلاً منه حدود الحساب والتبريد والتوقف الآمن عند أخطاء OAuth أو 401/403/429.

## AI Provider Registry وWorker Queue

يحتوي Gateway الآن على Registry مركزي لمزودي Gemini وOpenAI وAnthropic وOpenRouter وOllama، مع إضافة مزودات OpenAI-compatible كبيانات عبر `/v1/ai/providers` من دون تعديل المصدر. يعرض المسار أسماء الأسرار فقط ولا يعيد القيم. تُحفظ الأسرار في متغيرات البيئة أو في `ISM_AI_SECRET_FILE` الاختياري داخل `gateway/secrets/` بصلاحية `0600`، وهذا المجلد غير متعقب في Git.

توفر `/v1/ai/providers/{id}/health` مصفوفة حالات موحدة مثل `READY` و`NOT_CONFIGURED` و`AUTH_ERROR` و`NETWORK_ERROR` و`RATE_LIMITED`. لا يعني وجود مفتاح أن المزود جاهزاً؛ يجب أن ينجح فحص الشبكة والمصادقة والنموذج عند توفرها. راجع `docs/ai/PROVIDER_HEALTH.md` و`docs/ai/PROVIDER_REGISTRY.md`.

المعالجة والتنزيل يعملان عبر صفوف Worker منفصلة بحدود `MAX_ACTIVE_PROCESSING_JOBS` و`MAX_ACTIVE_SOURCE_JOBS` و`MIN_FREE_DISK_GB`. لا تنفذ طلبات HTTP عملية الفيديو الطويلة داخل request handler. عند إعادة تشغيل Gateway تعاد المهام `queued/running` إلى الصف، ويعاد استخدام `pipeline_job_id` للاستئناف عندما يكون checkpoint موجوداً. تعرض `/v1/diagnostics/workers` حالة العامل والمهام الحالية وheartbeat، ولا يُعرض artifact كمقطع جاهز قبل فحص وجوده وحجمه وامتداده.

## Analytics v0.8

يوفر Gateway مسار `GET /v1/analytics/summary?days=30` لقراءة اللقطات اليومية، ومسار `POST /v1/analytics/snapshots` لإدخال نتيجة مزامنة من موصل رسمي. تُحفظ المشاهدات والإعجابات والتعليقات والمتابعون ووقت المشاهدة مع `source` و`fetched_at`، ويعرض التطبيق البيانات المفقودة بوضوح. هذا العقد لا يخترع أرقاماً ولا يجمع بيانات من صفحات المنصات؛ يجب أن يملأه موصل OAuth رسمي يملك الصلاحيات المناسبة.

## معالجة فيديو Android عن بُعد

نسخة Android لا تشغّل Python أو `uv` داخل APK. بعد نشر Gateway على جهاز يملك مجلد `pipeline` واعتمادياته، يرسل التطبيق رابط YouTube إلى `POST /v1/processing/jobs` ثم يستطلع `GET /v1/processing/jobs/{id}` ويعرض ملفات MP4 من مسار الوسائط. يجب ضبط `PUBLIC_BASE_URL` على عنوان HTTPS العام للخادم، وضبط `ISM_PIPELINE_DIR` على مجلد `pipeline`. يحصر Gateway المعالجة في مهمة واحدة افتراضياً والتنزيل في مهمتين، ويعيد المهام التي كانت queued/running إلى failed بعد إعادة تشغيل الخادم بدلاً من ترك التطبيق ينتظر بلا نهاية.

```bash
# من جذر المستودع، بعد تثبيت uv أو اعتماديات pipeline
cd gateway
cp .env.example .env
export PUBLIC_BASE_URL=https://your-gateway.example
export GATEWAY_TOKEN='ضع-رمزًا-طويلًا'
export ISM_PIPELINE_DIR="$PWD/../pipeline"
uvicorn main:app --host 0.0.0.0 --port 8787
```

في تطبيق Android، افتح **Social Hub** أو قسم **ANDROID PROCESSING GATEWAY** داخل Studio، أدخل عنوان HTTPS نفسه والرمز، ثم اضغط **SAVE GATEWAY**. استخدم رابط YouTube أو HTTPS فقط؛ اختيار ملف محلي من الهاتف لا يمكن للخادم الوصول إليه مباشرة.

للاختبار، أضف حسابًا تجريبيًا من لوحة التحكم ثم جدولة منشور بعد دقيقتين. العامل الخلفي يفحص المنشورات كل 30 ثانية ويحوّلها إلى `published` في Mock Mode. يمكنك مراقبة `/health` و`/v1/dashboard/summary` و`/v1/social/schedule`.

## تشغيل محمي محليًا

```bash
GATEWAY_TOKEN='ضع-رمزًا-محليًا-طويلًا' PROVIDER_MODE=mock uvicorn main:app --host 127.0.0.1 --port 8787
```

ضع الرمز نفسه في حقل **Session token** داخل ISM. في الإنتاج يجب استبدال Mock OAuth وMock Publisher بموفري OAuth الرسميين، وتشفير Refresh Tokens، واستخدام قاعدة بيانات مُدارة، وتفعيل HTTPS، وتحديد `CORS_ORIGINS` بدل `*`.

## حدود النسخة الحالية

النسخة الحالية هي **طبقة أساس قابلة للاختبار**. وظيفة `PROVIDER_MODE=mock` تحاكي النشر ولا تنشر إلى حسابات حقيقية. التكامل الحقيقي لكل منصة يحتاج تطبيق مطوّر، OAuth callback، صلاحيات معتمدة، رفعًا مجزأً للوسائط عند الحاجة، تحديثًا للرموز، ومراقبة حدود الاستخدام. لا تضع `Client Secret` أو `Refresh Token` في تطبيق ISM أو في GitHub.

## نشر متوافق وحماية الحسابات

يطبّق Gateway الآن سياسة نشر مستقرة لكل حساب. الإعدادات الأساسية هي `ACCOUNT_DAILY_LIMIT` و`ACCOUNT_MIN_GAP_SECONDS` و`MAX_PROVIDER_ATTEMPTS`، ويمكن تعديلها لكل حساب عبر `PATCH /v1/accounts/{account_id}/policy`. عند ردّ موفّر بـ `401` أو `403` يُوقف الحساب ويظهر سبب التوقف، وعند `429` يحترم Gateway قيمة `Retry-After` إن أرسلها الموفّر ويضع المنشور في انتظار تدريجي.

يتم إنشاء مفتاح idempotency ثابت لكل منشور لمنع تكرار نفس المحتوى والموعد. توجد أيضًا مسارات `POST /v1/accounts/{account_id}/resume` لاستئناف الحساب بعد معالجة التحذير، و`GET /v1/accounts` لإظهار حالة الحساب وعدد منشورات اليوم وآخر نشر وفترة التهدئة.

هذه الآليات مخصصة للاستقرار والالتزام بالحدود الرسمية، وليست لتجاوز الحظر أو إخفاء الأتمتة. لا يستخدم Gateway تدوير عناوين IP أو Proxies للتحايل على المنصات.

## Gemini للمعالجة البعيدة من Android

لا يرسل Android مفتاح Gemini مع طلب المعالجة. يحتفظ Gateway الشخصي بالمفتاح server-side عبر `GEMINI_API_KEY` أو ملف `ISM_GEMINI_KEY_FILE` غير متعقب بصلاحيات 600، ويمرره فقط إلى بيئة Pipeline الفرعية، ويعرض للتطبيق حالات آمنة دون إظهار المفتاح.

اضبط الخادم الذي يحتوي على Pipeline قبل التشغيل:

```bash
export GATEWAY_TOKEN='generate-a-long-random-token'
export REQUIRE_GATEWAY_TOKEN=true
export GEMINI_API_KEY='your-server-side-key'
export ISM_PROCESSING_ROOT=/srv/ism-processing
export ISM_PIPELINE_DIR=/srv/ism/pipeline
export PUBLIC_BASE_URL='https://your-private-gateway.example'
python3 -m uvicorn main:app --host 0.0.0.0 --port 8787
```

كبديل للبيئات التي تستخدم ملف أسرار محلياً، أنشئه خارج Git:

```bash
mkdir -p gateway/secrets
umask 077
printf '%s' "$GEMINI_API_KEY" > gateway/secrets/gemini.key
export ISM_GEMINI_KEY_FILE="$PWD/gateway/secrets/gemini.key"
```

لا تضع `GEMINI_API_KEY` في Android أو URL أو JSON الخاص بالمهمة أو معاملات CLI أو السجلات. على الهاتف، أدخل فقط عنوان Gateway و`GATEWAY_TOKEN` داخل Studio. استخدم عنوان LAN خاصاً في Debug أو شبكة VPN خاصة؛ الاتصالات العامة يجب أن تستخدم HTTPS.

اختبر الخادم قبل الضغط على CUT IT:

```bash
curl http://127.0.0.1:8787/health
curl -H "Authorization: Bearer $GATEWAY_TOKEN" http://127.0.0.1:8787/v1/processing/capabilities
curl -H "Authorization: Bearer $GATEWAY_TOKEN" -X POST http://127.0.0.1:8787/v1/diagnostics/pipeline
curl -H "Authorization: Bearer $GATEWAY_TOKEN" -X POST http://127.0.0.1:8787/v1/diagnostics/gemini
```

ينفذ diagnostic طلباً حقيقياً صغيراً باستخدام نفس `GeminiClient` الذي يستخدمه scoring، ويعيد حالات مثل `ready` و`not_configured` و`auth_failed` و`quota` و`network_error` و`timeout` فقط. لا يعيد المفتاح أو جزءاً منه أو hash أو headers أو environment dump. يجب أن تكون `pipeline`, `python`, `ffmpeg`, و`storage` جاهزة قبل الضغط على CUT IT، وإذا كان llm هو Gemini فيجب أن تكون حالة diagnostic جاهزة.

عند بدء مهمة Gemini، يضع Gateway المفتاح في `GEMINI_API_KEY` لعملية Pipeline الابنة ويضبط `PUBLIKCLIP_DISABLE_LOCAL_SECRETS=1`. تشمل الأخطاء المستقرة `GEMINI_NOT_CONFIGURED` و`GEMINI_AUTH_FAILED` و`GEMINI_QUOTA_EXCEEDED` و`GEMINI_TIMEOUT` و`PIPELINE_UNAVAILABLE` و`FFMPEG_UNAVAILABLE`. Android ينفذ **TEST SYSTEM** قبل **CUT IT** ولا ينشئ job إذا فشل أي فحص.
