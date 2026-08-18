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

افتح `http://127.0.0.1:8787` لعرض لوحة التحكم. في تطبيق ISM، ضع `http://127.0.0.1:8787` في حقل **Gateway API URL**. عناوين HTTP مسموحة محليًا فقط؛ يجب استخدام HTTPS خارج الجهاز.

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
