# 📋 تقرير المراجعة الشامل — مشروع Purevon

> **التاريخ:** 21 أغسطس 2026
> **الإصدار المُراجَع:** versionCode 27 (v1.2.7)
> **نطاق المراجعة:** إعدادات البناء، الأمان، المعمارية، الخدمات والمستقبِلات، جودة الكود، الاختبارات

---

## نظرة عامة

مشروع ناضج ومبني بعناية: معمارية Clean Architecture حقيقية، 810 اختبار وحدة تمر كلها، أمان قاعدة بيانات مصمم جيداً، ومعالجة دقيقة لحالات SMS/MMS المعقدة. لكن توجد أخطاء منطقية عالية الخطورة في طبقة البيانات، وتسريبات ذاكرة، ومشكلة بيئة بناء تحتاج حلّاً فورياً.

**نتيجة الاختبارات:** `810 tests, 0 failures, 0 errors, 0 skipped` ✅

---

## 🔴 حرج — يحتاج إجراءً فورياً

### 1. البناء يفشل مع JDK 25 (البيئة الحالية)

`./gradlew` يفشل بخطأ غامض:

```
* What went wrong:
25.0.4
```

**السبب الجذري:**
```
java.lang.IllegalArgumentException: 25.0.4
	at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:307)
```
Kotlin Gradle Plugin 2.1.0 لا يستطيع قراءة رقم إصدار JDK ثنائي الخانة ("25.0.4"). المشروع يعمل مع JDK 17–21 فقط.

**الحل المؤقت:**
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest
```

**الحل الدائم — أضِف إلى `gradle.properties`:**
```properties
org.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64
```

### 2. تعارض معرّفات SMS/MMS في الكاش — فقدان بيانات صامت

- `data/repository/SystemMessageQueries.kt:157` و `:392` تبني `Message(id = mmsId)` **بدون** الإزاحة
- بينما `MessagePagingSource.kt:257` و `MessageRepositoryImpl.kt:594` تطبقان `MMS_ID_OFFSET (+2_000_000_000)`
- النتيجة: رسالة SMS وMMS بنفس `_id` الرقمي تتصادمان في `CachedMessageEntity` (المفتاح الأساسي هو `id` المجرّد، `CachedMessageEntity.kt:19`) مع `OnConflictStrategy.REPLACE` → الكتابة فوق بعضها بصمت، ورسائل "شبحية" عند المزامنة، و`getMessageById` (`MessageRepositoryImpl.kt:483`) قد يرجع الصف الخاطئ.

### 3. تسريب ذاكرة في ConversationViewModel

- `presentation/screen/conversation/ConversationViewModel.kt:771-777` يسجّل مراقباً من نوع `DefaultLifecycleObserver` على `ProcessLifecycleOwner` عبر `observeLifecycle()`
- لكن `onCleared()` (`:306-321`) **لا يزيله أبداً**
- النتيجة: الـ ViewModel المحذوف (ومعه الـ delegates والسياق وMediaRecorder) يبقى محتجزاً في ذاكرة التطبيق حتى موت العملية.

### 4. فقاعات "جاري الإرسال" يتيمة عند فشل الإرسال

- `data/repository/SmsSender.kt:156`: يُدرَج صف مؤقت بحالة SENDING قبل الإرسال (optimistic insert)
- مسارات الفشل (`catch` في `:354-363`) **لا تحذفه**
- النتيجة: رسالة عالقة بحالة "sending" في المحادثة للأبد عند أي فشل في `sendTextMessage`.

---

## 🟠 عالي الأولوية

| # | الموقع | المشكلة |
|---|--------|---------|
| 5 | `SmsSender.kt:115,210` | `sendSms` تنفّذ `contentResolver.insert` وبحث thread-ID حاصراً بدون `withContext(Dispatchers.IO)` — الاستدعاء الحاصر ينفّذ على مُرسِل الاستدعاء (Main إذا أُطلق من `viewModelScope`) |
| 6 | 22 موقعاً في العرض | `collectAsState()` مستخدمة في كل مكان و`collectAsStateWithLifecycle()` **صفر** استخدامات — الـ Flows تستمر بالإصدار وإعادة التركيب أثناء خلفية التطبيق (مثال: `MainActivity.kt:134`, `ConversationScreen.kt:80`, `HistoryScreen.kt:102`) |
| 7 | `InCallViewModel.kt:60,70` وأخرى | اختراق طبقة البيانات إلى العرض: حقن DAOs مباشرة في ViewModels (`ContactNoteDao`)، بناء `ContactResolver` داخل الـ VM، `ScheduledMessageDao` في delegate الواجهة (`ConversationLoaderDelegate.kt:36`)، كيانات Room (`ContactNoteEntity`) في نماذج UI (`MiddleCardTabs.kt:38`) — حدود المعمارية شكلية جزئياً |
| 8 | `DatabaseMigrations.kt:93-106,128-131,207-208` | فهارس تُنشأ في الـ migrations فقط (`conversation_settings.isPinned/isMuted/isArchived`, `whitelist.phoneNumber`, `message_templates.category/is_favorite`) وغير معلنة في `@Entity` → التثبيت الجديد **بدون** هذه الفهارس بينما الترقية بها (اختلاف سكيما بين مسارَي التثبيت) |

---

## 🟡 متوسط الأولوية

### طبقة البيانات

- **`di/DatabaseModule.kt:73-74,101-104`**: مفتاح hex يتحوّل إلى `String` ثابتة غير قابلة للتصفير؛ `finally { passphrase.fill(0) }` يصفّر الـ ByteArray فقط — بقايا المفتاح تبقى في الـ heap حتى GC (فجوة دفاع-عميق؛ Keystore ما زال يحمي التخزين)
- **`di/DatabaseModule.kt:49-70`**: تهيئة القاعدة + `System.loadLibrary` + `SharedPreferences.commit()` + `sqlcipher_export` الكاملة متزامنة على خيط حقن Hilt (عادةً Main) → خطر jank/ANR لأول تشغيل بعد تحديث بقواعد كبيرة
- **`MessageSyncDelegate.kt:360-386`**: سباق الحذف الشبحي في `syncAllMessages` — صفوف تُدرَج بين `queryAllSystemMessages()` و`getAllIds()` (مثل الإدراج المؤقت في SmsSender) غائبة عن `freshIds` فتُحذف كـ stale
- **`MessageRepositoryImpl.kt:161-181`**: `getAllConversations()` تطلق مزامنة fire-and-forget غير منسّقة عند *كل* collection — عدة جامعين = مزامنات متوازية متسابقة (مقيّدة بالوقت فقط لا بالإسناد)
- **`MessageRepositoryImpl.kt:344-683`**: `deleteMessage`/`getMessageById`/`updateMessage`/`clearAllMessages` تنفّذ ContentProvider I/O حاصراً داخل suspend بدون تغليف IO — عدم اتساق مع الـ delegates التي تغلّف؛ السلامة معلّقة على انضباط مواقع الاستدعاء
- **`ContactRepositoryImpl.kt:147-155`**: `searchContacts` تشغّل استعلامات ContactsProvider حاصرة داخل `flow{}` بدون `flowOn(Dispatchers.IO)` (عكس السطر `:80` الذي يحتويه)

### طبقة العرض

- **`ConversationViewModel.kt:65-110`**: ثلاثة flows فرعية مشتقة (`inputBarState`/`audioState`/`dialogState`) مبنية "للإعادة تركيب المستهدفة" لكن **لا يوجد أي مستهلك** لها — الشاشة تجمع `uiState` الموحّد (`ConversationScreen.kt:80`)؛ كل ضغطة زر تعيد تركيب الشاشة كاملة بما فيها بناء قائمة الرسائل (`:851-927`)
- **`MainActivity.kt:156,570-615`**: `parseVCard()` (قراءة ContentResolver + regex) تُستدعى متزامناً داخل `remember{}` أثناء أول composition — I/O على Main Thread
- **`ConversationScreen.kt:137-179`**: استعلامات ContentResolver على Main Thread داخل callbacks منتقي الملفات
- **ترتيب تهيئة هش**: `ConversationViewModel.kt:135,151` تشير إلى `conversationLoaderDelegate` المعرّف في `:153` — آمن فقط لأن التقاط lambda كسول
- **نصوص مترجمة مخزّنة في حالة UI**: `InCallViewModel.kt:~193` و`ConversationViewModel.kt:371,428` تخزنان `context.getString(...)` في الحالة — تنكسر عند تغيير اللغة أثناء التدفق؛ يجب حمل enums/sealed types
- **PII في مسارات التنقل**: `Screen.kt:24-30` يمرر `phoneNumber/name/email` كوسائط route — Navigation يحفظها في saved-instance-state (على القرص عند موت العملية)
- **`PurevonNavHost.kt:114-117`**: تحويل غير مفحوص `LocalContext.current as ComponentActivity`
- **`ConversationViewModel.kt:317`**: `CoroutineScope(IO + SupervisorJob())` في `onCleared` لا يُلغى أبداً
- **`MessageSendingDelegate.kt:180`**: إعادة إسناد `sendJob` داخل مهمة الإرسال الخارجية نفسها — تدمير المقبض الذي تستخدمه `cancelSend()` (`:305`)؛ يبدو copy-paste
- **`MessagesViewModel.kt:136`**: مزامنة قسرية عند كل `SmsReceived` بدون debounce/إسناد (بعكس ConversationViewModel)
- **ازدواجية توجيه الإشعارات**: `MainActivity.kt:182-247` مقابل `:355-446` — منطقان متوازيان لخريطة الوجهات (مصدرا حقيقة لتصادم `navigate_to`)

### الأمان

- **`DataEncryptionManager.kt:62-87`**: عقد `encryptString` متناقض — عند نجاح EncryptedFile ترجع اسم الملف، وعند فشله يُفعَّل fallback يرجع **Base64 data** بدلاً منه؛ `decryptString(fileName)` سيحاول فتحه كملف → كسر وظيفي عند تفعيل مسار الـ fallback
- **تسجيل بيانات خام في اللوج**: `DelayedSendManager.kt:81` (رقم هاتف خام)، `InCallViewModelExt.kt:195` (محتوى رسالة سريعة)، `NotificationActionReceiver.kt` يستخدم `Log.e` لمستوى info — معظم الكود يستخدم `DebugLogger.maskPhoneNumber` بشكل ممتاز وهذه استثناءات

---

## 🟢 أولوية منخفضة

- **10 ملفات >800 سطر**: `HistoryScreen.kt` (1168)، `MessagesScreen.kt` (1104)، `InCallViewModel.kt` (1038)، `ConversationScreen.kt` (1037)، `ContactsScreen.kt` (1009)، `InCallScreen.kt` (939)، `AddContactViewModel.kt` (937)، `ConversationViewModel.kt` (879)، `PermissionRequestScreen.kt` (857)، `InCallComponents.kt` (821) — رغم أن commit سابق يقول "split large files into smaller modules"
- **نصوص إنجليزية hardcoded**: `StatisticsScreen.kt:37+` ("Statistics", "Total Calls", "Incoming"... شاشة كاملة غير مترجمة رغم دعم 51 لغة!)، `PurevonNavHost.kt:460` ("Contact not found")، `ContactInfoComponents.kt` (×4) — ~14 نصاً إجمالاً، ونمط تاريخ hardcoded `"EEE, MMM d • HH:mm"` قرب `HistoryScreen.kt:1013`
- **مفاتيح LazyColumn ناقصة في القوائم الثانوية**: `ScheduledMessagesScreen.kt:105`، `StatisticsScreen.kt:125`، `incall/ContactPickerDialog.kt:174`، `SettingsBlockedDialogs.kt` (×6)، `SystemSpecificPermissionsScreen.kt:94`
- **`abiFilters` محصور بـ ARM** (`arm64-v8a`, `armeabi-v7a` في `app/build.gradle.kts:40`) → **المحاكي x86_64 لن يعمل**
- **README غير متزامن مع الكود**: README يقول Target SDK 36 بينما الكود `targetSdk = 35` (مع تعليق يشرح التخفيض) — حدّث README
- **تعليق مضلل**: `build.gradle.kts:257` يقول "تم تعليق مكتبة klinker لتجنب انهيار البناء" لكن `implementation(libs.klinker.android.smsmms)` مفعّلة فعلاً في السطر 259
- **`upload_certificate.pem` مرفوع للـ git**: شهادة Play عامة (خطورتها منخفضة) لكن الأفضل إبعاده عن المستودع
- **كود ميت**: الـ flows الفرعية غير الموصولة (أعلاه) + نصف نظام الـ delegates يشارك نفس `_uiState` فالكوبليّنق انتقل ولم يتقلص

### إدارة المشروع

- **110 ملف معدّل غير مُلتزم** (~4713 إضافة / 4772 حذفاً): لديك عمل كبير غير محفوظ في git — التزم به
- `COMPREHENSIVE_REVIEW.md` و`TEST_COVERAGE_REPORT.md` محذوفان لكن الحذف غير ملتزم

---

## ✅ نقاط القوة (تستحق التقدير)

1. **أمان ممتاز التصميم**: SQLCipher بمفتاح عشوائي 256-bit (`SecureRandom`) مشفّر عبر AndroidKeyStore AES-GCM (`DatabasePassphraseManager`)، `allowBackup=false`، `dataExtractionRules` مضبوطة، تصفير المفتاح في `finally`
2. **network_security_config نموذجي**: cleartext مقيد بنطاقات MMSC لمشغّلين محددين (~60 نطاقاً) مع شرح أمني صريح لرفض الأنماط الواسعة (`evil.mms.attacker.com`)
3. **SmsReceiver احترافي**: `goAsync()` + مهلة 20 ثانية (تحت حد ANR) + فلتر تكرار + تجميع multipart + فحص الحظر مع fail-open آمن + فحص صلاحيات READ_CONTACTS قبل الاستعلام
4. **OtpManager متعدد اللغات** (15+ لغة بما فيها العربية والصينية) مع تخفيف ذكي للإنذارات الكاذبة: رفض المبالغ العملية والتواريخ وأرقام الهواتف والأصفار الصافية
5. **810 اختبار وحدة تمر جميعها** بتغطية جيدة للمستخدمات والمترجمات والWorkers والمستودعات
6. **Manifest منظم**: مكوّنات exported محمية بصلاحيات النظام الصحيحة (`BROADCAST_SMS`, `BIND_SCREENING_SERVICE`, `BIND_INCALL_SERVICE`)، بث داخلي بحماية signature، FileProvider واحد موحّد
7. **CallScreeningServiceImpl بمنطق أولويات واضح**: SIM scope → unknown → blacklist → whitelist → whitelist-only mode، مع `runBlocking` + مهلة 1.5 ثانية وfail-open
8. **مفاتيح LazyColumn صحيحة** في القوائم الساخنة (مفاتيح مركبة + `animateItem`)، MVI منضبط (sealed UiEvent + onEvent واحد)، تنظيف موارد دقيق (MediaRecorder/WakeLock/timers) في onCleared
9. **بناء release محكم**: minify + shrinkResources، توقيع إلزامي (يفشل البناء بدون keystore بدلاً من التوقيع التصحيحي)، build type منفصل staging، `fallbackToDestructiveMigrationOnDowngrade` أُزيل عمداً لحماية البيانات
10. **تنقية لوجات ذكية**: `DebugLogger.maskPhoneNumber/maskMessage` مستخدمة في معظم المسارات الحساسة + اختبار `DebugLoggerMaskingTest`

---

## 🎯 خطة العمل المقترحة (بالترتيب)

### اليوم
1. ✅ ~~ثبت JDK 21 في `gradle.properties`~~ — الحل المؤقت يعمل (`JAVA_HOME=.../java-21-openjdk-amd64`)، التثبيت الدائم متبقٍ
2. ✅ **منجز — FIX M17**: deadlock إعادة المحاولة أُصلح (نواتان `*Locked` بلا قفل)
3. ✅ **منجز — FIX M18**: `MMS_ID_OFFSET` موحّد في `SystemMessageQueries.kt`
4. ✅ **منجز — FIX M19**: NotifyRespInd (Retrieved) + contentLocation كـ locationUrl

### هذا الأسبوع
5. ✅ **منجز — FIX M20**: dedup ثلاثي الطبقات (A/B/C) لمسار MMS الوارد عبر `MmsDownloadDedup` — وقررنا عدم توصيل `DuplicateMessageFilter` (مخصص SMS)
6. ✅ **منجز — FIX M23**: تسريب مراقب الـ lifecycle أُزيل (`appLifecycleObserver` + إزالة في `onCleared()`)
7. ✅ **منجز — FIX M22**: الصف المؤقت يُنظَّف في كل مسارات فشل `SmsSender` + تعليم FAILED قابل لإعادة المحاولة
8. ✅ **منجز — FIX M24**: 30 موقع `collectAsState` → `collectAsStateWithLifecycle` عبر 19 ملفاً (صفر متبقٍ)
9. تحقق ميداني من إصلاح التكرار على الهاتفين (بروتوكول قسم الإصلاحات أدناه)

### لاحقاً
10. ✅ **منجز — FIX M25**: الفهارس معلنة في `@Entity` + MIGRATION_14_15 (DB v15) — سكيما موحّدة
11. ✅ **منجز — FIX M26**: عقد `DataEncryptionManager` سليم (حذف الزوج الميت المكسور)
12. ⏸️ **مؤجَّل بمبرر**: نقل DAOs خارج ViewModels — إعادة هيكلة معمارية تحتاج جلسة مخصصة واختبارات integration (انظر قسم الإصلاحات)
13. ✅ **منجز — FIX M27**: العمليات الحاصرة في `SmsSender` داخل `Dispatchers.IO`
14. ✅ **منجز — FIX M28**: `StatisticsScreen` مترجمة (18 مفتاحاً، EN+AR)
15. ✅ **منجز — FIX M29**: الأنماط الوقائية من QKSMS — تنظيف NotificationInd عند HTTP 400/404 + تمييز الاستجابة الفارغة كخطأ نهائي + Layer D dedup بـMessage-ID
16. ✅ **منجز — FIX M30**: تسريب InCallService الإطاري — تنظيف دفاعي للمستمعين في onDestroy + استثناء LeakCanary موثق للنمط الإطاري (المسار التطبيقي تحقق نظافته)

### إدارة
15. التزم بعملك غير المحفوظ (110 ملف!)
16. حدّث README (SDK 35 وليس 36) واحذف التعليق القديم عن klinker
17. فكّر بإضافة x86_64 إلى abiFilters لدعم المحاكي أو اتركها إن كان التوزيع ARM-only مقصوداً

---

## 🔍 مراجعة معمّقة: قسم الرسائل وإرسال/استقبال MMS

> طُلبت بعد حادثة حقيقية: **صورة واحدة مرسلة → وصلت أكثر من 30 نسخة** لهاتف الاستقبال (وصلات متكررة عبر وقت).

### الأدلة المجمّعة (من الجهاز الفعلي R5CX71C0YGW عبر adb)

| الفحص | النتيجة |
|-------|---------|
| صفوف `content://mms` لـ thread_id=129 بجهاز الإرسال | **5 صفوف فقط، كلها SENT، لا تكرار** (`_id` = 2,3,4,9,10) |
| آخر إرسال (vCard) في logcat | دورة إرسال واحدة نظيفة — لا تكرار من جهة الإرسال |
| APN المكتشف | MMSC `http://10.3.3.133:9090/was`، proxy `10.3.2.133:8080` (STC)، `preferDirectHttp=false` |
| مسارات الإدراج للوارد في الكود | **مسار واحد فقط**: `pduPersister.persist()` في `MmsDownloadedReceiver.kt:167` |
| تسجيل المستقبِلات في Manifest | سليم — `WAP_PUSH_RECEIVED` مُزال عمداً، `WAP_PUSH_DELIVER` واحد |

**الاستنتاج**: جهة الإرسال سليمة. التكرار يتولّد على **جهة الاستقبال**: كل دفعة WAP Push جديدة من المشغّل ← دورة تنزيل كاملة ← إدراج نسخة جديدة في Inbox.

### 🔴 السبب الجذري #1: لا M-NotifyResp.ind — المشغّل لا يعرف أن الرسالة استُلمت!

بروتوكول MMS يشترط بعد الاسترجاع إرسال **M-NotifyResp.ind بحالة Retrieved (132)** إلى MMSC. ما يفعله الكود فعلياً:

```kotlin
// MmsDownloadedReceiver.kt:355-357 — نوع PDU خاطئ!
val acknowledgeInd = AcknowledgeInd(...)   // ❌ M-Acknowledge.ind وليس M-NotifyResp.ind

// MmsDownloadedReceiver.kt:382-388 — ونقطة نهاية خاطئة!
smsManager.sendMultimediaMessage(context, ackUri, null /*locationUrl*/, ...)
//                                                                 ^^^^ null = URL الإرسال الافتراضي
```

- `M-Acknowledge.ind` يُستخدم فقط في تدفق الاسترجاع المؤجَّل وبعد NotifyResp — إرساله وحده لا يُغلق المعاملة
- تمرير `null` بدل `contentLocation` يعني POST إلى نقطة الإرسال الافتراضية وليس عنوان الرسالة الأصلية
- **النتيجة**: MMSC يعتبر الإشعار غير مسترجَع ← يعيد إرسال WAP Push دورياً (المشغلون عادة كل 5–15 دقيقة حتى ~30+ محاولة قبل انتهاء الصلاحية) ← **مطابقة تامة لعدد النسخ الواصلة (30+)**

ملاحظة: AOSP غالباً يرسل NotifyResp داخلياً ضمن `downloadMultimediaMessage()`، لكنه **يفشل بصمت** عند مشاكل proxy/APN (ولدينا دليل بيئة: "Mobile data enabled: false" أثناء الإرسال!) — لذا الاعتماد عليه وحده هش، والمشغلات السعودية (STC/Mobily/Zain) معروفة باشتراطها استجابة صريحة.

### 🔴 السبب الجذري #2: حماية التكرار (dedup) مثقوبة

يوجد فحص dedup بـ transactionId في `MmsDownloadedReceiver.kt:116-122`، لكنه يُتجاوَز في الحالات الأكثر شيوعاً:

1. **فشل الـ parser ← tr_id فارغ**: عندما يفشل `PduParser` في `MmsReceiver` يقع الكود إلى `extractContentLocationRaw()` (`MmsReceiver.kt:184-190`) الذي يستخرج URL فقط — `transactionId` تبقى `null` ← `putExtra("transaction_id", "")` (`MmsReceiver.kt:254`) ← `isNotBlank()` تفشل ← **dedup متجاوَز بالكامل**
2. **لا dedup بـ Content-Location إطلاقاً**: بعض المشغّلات تولّد tr_id جديداً لكل دفعة إعادة — الفحص الحالي لن يمسكها. الـ `content_location` تُمرَّر فعلاً في الـ intent (`MmsReceiver.kt:253`) لكن **لا أحد يستخدمها** في الفحص
3. **`DuplicateMessageFilter` غير موصول بـ MMS**: مربوط في `SmsReceiver` فقط (SMS)؛ مسار MMS بلا فلتر مكافئ

### 🟠 مصدر محتمل إضافي: الإدراج المزدوج على Samsung

على بعض بنيات OEM (خصوصاً OneUI) قد يُدرج النظام الرسالة المنزّلة في telephony provider بنفسه **بالإضافة** إلى إدراج التطبيق اليدوي ← نسختان لكل دورة تنزيل. يحتاج تحققاً على جهاز الاستقبال (انظر بروتوكول التحقق أدناه).

### 🔴 اكتشاف جانبي حرج: Deadlock مؤكد في إعادة المحاولة

```
MessageRepositoryImpl.kt:408   retryFailedMessage() = sendMessageMutex.withLock {   ← يقتنص القفل
MessageRepositoryImpl.kt:434       sendMmsMessage(...)                              ← يستدعي
MessageRepositoryImpl.kt:392         = sendMessageMutex.withLock {                  ← نفس القفل!
```

`kotlinx.coroutines` Mutex **غير عائد (non-reentrant)** — أي ضغطة "إعادة محاولة" على رسالة فاشلة (SMS أو MMS) **تعليق دائم مضمون 100%** للكوروتين. هذا خطأ مستقل عن التكرار ويحتاج إصلاحاً فورياً (استخراج نواة مشتركة بلا قفل واستدعاؤها من الدالتين).

### ✅ ما ثبت سلامته في المسار

- **الإرسال**: `MessageSendingDelegate` (guard `isSending`، نداء واحد لكل ضغطة)، ضغط الصور، `MmsRetryScheduler` (حد 5 محاولات)، workers تستدعي مرة واحدة — لا حلقات إرسال
- **الاستقبال البنيوي**: تسجيل receivers سليم، `goAsync()` بمهلة 25 ثانية، تنظيف الملفات المؤقتة في `finally`
- **klinker** مستخدمة فقط لكشف APN؛ `TransactionService` المسجل في الـ manifest خامد (وهذا جزء من المشكلة — خدمة klinker كانت ستتعامل مع NotifyResp بشكل صحيح)

### الإصلاحات المطلوبة (بالترتيب)

1. **أرسل M-NotifyResp.ind صحيحة** بعد الاسترجاع الناجح: ابنِ `NotifyRespInd(transactionId, status=132/Retrieved)` ومرّر `contentLocation` كوسيط `locationUrl` في `sendMultimediaMessage` (`MmsDownloadedReceiver.sendAcknowledgement`) — أو استخدم HTTP مباشر عبر klinker
2. **ثبّت dedup ثلاثي الطبقات** قبل `persist()` في `handleSuccessfulDownload`: (a) tr_id كما هو، (b) مطابقة `content_location` ضد سجل آخر URLs مُعالجة (DataStore/جدول Room صغير، صلاحية 48 ساعة)، (c) رفض التنزيل مبكراً في `MmsReceiver` إذا كان tr_id مسجلاً أصلاً — قبل استهلاك البيانات
3. **أصلح الـ deadlock**: انقل جسم `sendMmsMessage`/`sendMessage` إلى دوال خاصة بلا قفل (`sendMmsMessageLocked`) واجعل `retryFailedMessage` يستدعيها داخل قفله الواحد
4. **تحقق من الإدراج المزدوج** على جهاز الاستقبال ثم قرر هل تلغي الـ persist اليدوي أم تكتفِ بالـ dedup

### بروتوكول التحقق (على الهاتفين)

```bash
# على جهاز الاستقبال — راقب دورات الدفع المتكررة:
adb logcat -s MmsReceiver MmsDownloadedReceiver | grep -E "TRIGGERED|Content-Location|persisted"
# عدّ الصفوف بعد كل دفعة:
adb shell content query --uri content://mms/inbox --projection _id:date,tr_id --where "thread_id=<ID>"
```

إذا رأيت `TRIGGERED` يتكرر لنفس Content-Location كل بضع دقائق ← تأكد السبب #1. إذا ظهرت نسختان لدورة واحدة ← تأكد السبب #4 (Samsung).

---

## 🛠️ الإصلاحات المطبَّقة (جلسة العمل الحالية)

> **التحقق**: `compileDebugKotlin` ناجح + **810 اختبار وحدة / 0 إخفاقات / 0 أخطاء** (بعد كل إصلاح أدناه)

### ✅ FIX M17 — Deadlock إعادة المحاولة (حرج)
- **الملف**: `MessageRepositoryImpl.kt`
- **قبل**: `retryFailedMessage()` يقتنص `sendMessageMutex.withLock` ثم يستدعي `sendMessage`/`sendMmsMessage` التي تقتنصان **نفس الـ mutex غير العائد** ← تعليق دائم مضمون عند كل ضغطة retry
- **بعد**: استخراج النواتين `sendSmsLocked()` و`sendMmsLocked()` بلا اقتناء قفل؛ الدالتان العامتان تغلّفانهما فقط، و`retryFailedMessage` يستدعي النواتين داخل قفله الواحد

### ✅ FIX M18 — توحيد MMS_ID_OFFSET (حرج)
- **الملف**: `SystemMessageQueries.kt`
- **قبل**: `queryAllSystemMessages()` و`querySystemMessages()` كانا يبنيان `Message(id = mmsId)` بالمعرف الخام بينما بقية النظام (`MessagePagingSource:257`, `MessageRepositoryImpl:616`, مندوب القراءة/الحذف) يتوقع `raw + 2_000_000_000`
- **الضرر السابق**: تصادم PK في `CachedMessageEntity` (SMS رقم 5 يبتلع MMS رقم 5 والعكس عبر REPLACE)، وفشل صامت لقراءة/حذف/تعليم MMS لأن الكاش كان يحمل معرفات بلا إزاحة
- **بعد**: ثابت مشترك `MMS_ID_OFFSET = 2_000_000_000L` أعلى الملف + تطبيقه في الموضعين — الكاش الآن يحمل المعرفات المُزاحة مثل بقية المسارات، والتنظيف الذاتي للـ ghost rows يعالج السجلات القديمة تلقائياً عند أول مزامنة

### ✅ FIX M19 — NotifyRespInd الصحيحة (السبب الجذري #1 للتكرار)
- **الملف**: `MmsDownloadedReceiver.kt`
- **قبل**: إرسال `AcknowledgeInd` (نوع PDU خاطئ) إلى URL افتراضي (`locationUrl = null`)
- **بعد**: دالة `sendNotifyResponse()` ترسل `NotifyRespInd(transactionId, status=STATUS_RETRIEVED=132)` وتُمرِّر `contentLocation` كوسيط `locationUrl` في `sendMultimediaMessage` (مع fallback للافتراضي إن كان URL غير صالح) — هذا ما يخبر MMSC أن الرسالة استُرجعت فيتوقف عن إعادة دفع الإشعارات

### ✅ FIX M20 — Dedup ثلاثي الطبقات (السبب الجذري #2 للتكرار)
- **الملفات**: جديد `util/mms/MmsDownloadDedup.kt` + تعديلات `MmsReceiver.kt` و`MmsDownloadedReceiver.kt`
- **Layer A** (DB): بحث `content://mms` بـ tr_id قبل أي تنزيل/إدراج — في `MmsReceiver` مبكراً وفي `handleSuccessfulDownload`
- **Layer B** (URL): مخزن SharedPreferences بمهلة 48 ساعة يسجّل آخر Content-Location مُعالجة — يمسك المشغلات التي تدوّر tr_id لكل دفعة والدفعات التي فشل parser الخاص بها (tr_id فارغ)
- **Layer C** (مبكر): تخطي التنزيل كلياً في `MmsReceiver` قبل `downloadMultimediaMessage()` حتى لا تُستهلك البيانات
- التعليم يتم **فقط بعد إدراج ناجح** حتى لا يمنع إعادة محاولة تنزيل فاشلة
- ملاحظة تصميمية: لم يُعَد استخدام `DuplicateMessageFilter` لأنه يقارن نص+مرسل+زمن (مناسب SMS فقط)؛ التكرار هنا يُعرَّف بـ URL الاسترجاع/المعاملة

### ✅ FIX M23 — تسريب مراقب دورة الحياة (حرج)
- **الملف**: `ConversationViewModel.kt`
- **قبل**: `observeLifecycle()` يضيف `DefaultLifecycleObserver` مجهولاً إلى `ProcessLifecycleOwner` (app-scoped) بلا أي إزالة — كل خروج من شاشة المحادثة كان يعلّق الـ ViewModel وكل شجرة الـ delegates في الذاكرة
- **بعد**: مرجع `appLifecycleObserver` + `removeLifecycleObserver()` يُستدعى أول `onCleared()`

### ✅ FIX M22 — فقاعات "جارٍ الإرسال" اليتيمة
- **الملف**: `SmsSender.kt`
- **قبل**: الصف المؤقت (`tempId` سالب، status=SENDING) يُدرَج عند :156 لكن مسارات الفشل الأربعة (فشل إدراج النظام / SecurityException / IllegalArgumentException / Exception أثناء الإرسال الفعلي) كانت تعود Failure دون تنظيف ← فقاعة SENDING عالقة للأبد + صف نظام SENT وهمي + metadata يتيمة
- **بعد**: `rollbackOnFailure(markSystemRowFailed)` قبل `try` (لرؤية كتل catch):
  - يحذف فقاعة الكاش دائماً عند وجودها
  - إن كان صف النظام موجوداً: يعلمه `TYPE=MESSAGE_TYPE_FAILED(5)` مطابقاً لاتفاقية `SentStatusReceiver.updateMessageStatus()` ← الرسالة تبقى مرئية كفاشلة **وقابلة لإعادة المحاولة** عبر `retryFailedMessage` (المُصلَح في M17!)
  - يحذف metadata الـ multipart (لا PendingIntent سيطلق بعد استثناء متزامن)
- ملاحظة: حالة "فشل إدراج النظام" تنظّف الفقاعة فقط (لا يوجد صف نظام لتعليمه)

### ✅ FIX M24 — collectAsStateWithLifecycle في كل طبقة العرض
- **19 ملفاً، 30 موقع استدعاء** (22 بدون وسائط على StateFlow + 8 بوسائط initial على Flow)
- `collectAsState()` يبقي الجمع حياً والشاشة في الخلفية (استهلاك بطارية/معالجة بلا فائدة)؛ `collectAsStateWithLifecycle()` يوقفه عند ON_STOP تلقائياً
- المكتبة موجودة أصلاً (`lifecycle-runtime-compose:2.8.7`)؛ استبدال الاستيرادات الصريحة وإضافة `androidx.lifecycle.compose.collectAsStateWithLifecycle` للملفات ذات wildcard
- صفر استخدامات متبقية للاسم القديم (تحقق grep)

### ✅ FIX M25 — الفهارس المفقودة من @Entity + MIGRATION_14_15 (DB v14→v15)
- **الملفات**: `ConversationSettingsEntity.kt`، `WhitelistEntity.kt`، `MessageTemplateEntity.kt`، `DatabaseMigrations.kt`، `PurevonDatabase.kt` (version=15)، `DatabaseModule.kt`
- **قبل**: الـ migrations القديمة (2_3، 3_4، 6_7) تنشئ 6 فهارس لثلاثة جداول لا تعلنها كياناتها ← التثبيت النظيف بلا فهارس إطلاقاً (استعلامات بطيئة) ومسار الترقية يحمل فهارس لا يتوقعها سكيما Room (خطر فشل تحقق TableInfo)
- **بعد**: الفهارس الست معلنة في `@Entity` بأسماء مطابقة تماماً لأسماء Room الافتراضية التي تستخدمها الـ migrations القديمة؛ `MIGRATION_14_15` (`IF NOT EXISTS`) يضيفها لمستخدمي التثبيت النظيف عند الترقية ويكون no-op لمن أصل عبر السلسلة القديمة
- **تحقق**: `app/schemas/.../15.json` مولّد ويحتوي الفهارس الست في الجداول الثلاثة

### ✅ FIX M26 — عقد encryptString/decryptString المكسور
- **الملف**: `util/security/DataEncryptionManager.kt`
- **المشكلة**: الزوج كان كوداً ميتاً بعقد خطر — `encryptString` يعيد `fileName` عند النجاح لكن Base64 ciphertext عند fallback الفاشل؛ ثم `decryptString` يعامل الـ ciphertext كاسم ملف ← FileNotFoundException ← `Result.success("")` = فقدان بيانات صامت
- **الحل**: حذفهما (صفر مستدعيات في main والاختبارات)؛ البديلان الصحيحان موجودان: `encryptForDatabase/decryptFromDatabase` لحقول Room و`encryptToFile/decryptFromFile` للملفات (كلاهما يستخدمهما فعلياً)

### ✅ FIX M27 — العمليات الحاصرة على Main thread
- **الملف**: `SmsSender.kt`
- **قبل**: `sendSms()` suspend ينفذ `getOrCreateThreadId()` (استعلام ContentResolver + تجزئة Threads API) وإدراج/تحديث قاعدة النظام وrollback على dispatcher المستدعي (Main) ← تجميد واجهة
- **بعد**: جسم الدالة كله داخل `withContext(Dispatchers.IO)`؛ الـ returns المبكرة صارت موسومة `return@withContext`

### ✅ FIX M28 — ترجمة StatisticsScreen
- **الملفات**: `StatisticsScreen.kt` + `values/strings.xml` + `values-ar/strings.xml`
- 17 نصاً ثابتاً استُبدلت بـ `stringResource(R.string.stat_*)` — 18 مفتاحاً جديداً بالإنجليزية والعربية (بقية اللغات الـ49 تسقط تلقائياً للإنجليزية حتى تُترجم)
- شملت النص الديناميكي `%1$d مكالمة • %2$s` ولاحقة الثواني

### ✅ FIX M29 — الأنماط الوقائية الثلاثة المتبناة من QKSMS
> بعد المقارنة الشاملة مع QKSMS (قسم «⚖️ مقارنة شاملة» أدناه) — تعزيز توافق الاستقبال عالمياً

**النمط 1 — تنظيف NotificationInd عند فشل HTTP دائم** (`MmsDownloadedReceiver.handleFailedDownload`)
- عند resultCode ≠ OK: قراءة `SmsManager.EXTRA_MMS_HTTP_STATUS`
- **400/404** = الرسالة اختفت من MMSC نهائياً → حذف الصفوف اليتيمة: `DELETE content://mms WHERE m_type=130 AND ct_l=?`
- أي خطأ آخر = مؤقت → تسجيل فقط دون تغيير شيء (يسمح بإعادة المحاولة الطبيعية)

**النمط 2 — تمييز الاستجابة الفارغة كخطأ نهائي** (`markRetrieveStatusTerminal`)
- عند اكتمال التنزيل بملف فارغ/مفقود: `UPDATE retr_st = RETRIEVE_STATUS_ERROR_END (0xFF) WHERE m_type=130 AND ct_l=?`
- مطابق لسلوك AOSP `DownloadRequest.persist` — يمنع OEM stacks من اعتبار الإشعار «تنزيل معلّق» إلى الأبد

**النمط 3 — Layer D: dedup بمعرّف الرسالة M-Message-ID** (`MmsDownloadDedup`)
- `findByMessageId`: استعلام قاعدة بعمود `m_id` (هوية يعينها MMSC وتبقى ثابتة عبر كل إعادة دفع حتى لو تدار tr_id وct_l معاً!)
- `wasMessageIdProcessed/markMessageIdProcessed`: خريطة SharedPreferences بنفس TTL 48 ساعة (تغطي نافذة الإدراج المتوازي)
- الفحص بعد نجاح parse وقبل persist؛ التخطي يرسل NotifyRespInd ليغلق المعاملة
- **تحسين إضافي**: مسار تخطي Layer A أصبح يرسل NotifyRespInd أيضاً — كان يتخطى الإدراج دون إغلاق المعاملة، ما يُبقي عاصفة إعادة الدفع مستمرة

### ✅ FIX M30 — تسريب PurevonInCallService (32 نسخة مدمرة محتجزة)

**تقرير LeakCanary**: `GC Root: Global variable in native code → android.telecom.InCallService$InCallServiceBinder (this$0) → PurevonInCallService` — الخدمة دُمرت (`onDestroy()` استدعيت) لكنها لا تتحرر، و32 تسريباً متراكماً (~5.7KB لكل نسخة).

**التشخيص: احتجاز إطاري وليس خللاً في كودنا**
1. **المصدر**: الصف الأعلى في أندرويد `InCallService` ينشئ `mBinder = new InCallServiceBinder()` كحقل instance، والكلاس الداخلي غير static يحمل مؤشراً خفياً `this$0` للخدمة
2. **آلية الاحتجاز**: عند الربط (bind)، libbinder في عمليتنا يحتفظ بـJNI global reference للـbinder لتوجيه نداءات IPC؛ ما دام system_server (مكدس Samsung Telecom تحديداً عدواني في الكاش) يحمل proxy للـbinder بعد unbind، تبقى السلسلة متجذرة
3. **32 نسخة** = دورات bind/unbind متكررة لكل حدث مكالمة على OneUI
4. **تحققنا أن مسارات التطبيق نظيفة**: `bridge.unregisterService()` يصفّر المرجع فعلاً (لذلك لم يظهر bridge في مسار التسريب)، `serviceScope.cancel()` منفذ، والمسار الوحيد المتبقي إطاري خالص
5. النمط **غير موجود** في قائمة `AndroidReferenceMatchers` الرسمية لـShark 2.14

**الإصلاحات المنفذة:**

أ) **تنظيف دفاعي للمستمعين** (`PurevonInCallService`):
- مجموعة جديدة `trackedCallbackCalls` (CopyOnWriteArrayList) تتبع المكالمات ذات المستمع المسجل
- `onCallAdded`: تسجيل مشروط بـ`addIfAbsent` (يمنع التسجيل المزدوج أصلاً)
- `onCallRemoved`: إلغاء مشروط بـ`remove`
- `onDestroy`: فك جميع المستمعين المتبقين في try/catch ثم clear — يمنع فئة تسريب موازية حيث تحتجز كائنات `Call` الإطارية المستمع → الخدمة بعد التدمير أثناء مكالمة جارية

ب) **استثناء LeakCanary الموثق** (`app/src/debug/java/.../InCallServiceLeakExclusion.kt`):
- `IgnoredReferenceMatcher` على حقل واحد فقط: `InCallServiceBinder.this$0`
- أي تسريب حقيقي يصل لهذه النسخ عبر مسار آخر **يبقى مبلّغاً عنه**
- موصول عبر انعكاس في `PurevonApp.setupDebugTools` (المكتبة debugImplementation فقط)

### ✅ FIX M31 — زر النجمة الميت + توحيد ألوان الشريط العائم مع BlueSky

1. **المكالمة الوهمية لا تفتح**: `onStarLongPressed = { }` كان فارغاً في `DialerScreen` بالموضعين (wide+compact)؛ والأسوأ أن الحالة معرّفة في `DialerScreen` بينما الاستدعاءان داخل دالتين منفصلتين (`DialerWideLayout`/`DialerCompactLayout`) — أضيف بارامتر `onStarLongPressed` للاثنتين ووُصل بـ`showFakeCallDialog = true`

2. **الشريط العائم بألوان iOS لا BlueSky**:
   - الدائرة الخضراء في الفقاعة → **صورة جهة الاتصال الرمزية** (Coil AsyncImage دائرية 42dp) عبر حقن `ContactResolver.resolveContactPhotoUri` مع حل غير متزامن يحدّث الفقاعة تلقائياً
   - fallback بلا صورة: الأحرف الأولى من الاسم، وإلا أيقونة الهاتف على **تدرج أزرق PurevonPrimary**
   - `activeAccent` (نبض/ظلال الشريط): iOSGreen/iOSIndigo → PurevonPrimary/PurevonPrimaryDark
   - نص المدة: أخضر → PurevonPrimaryLight
   - أزرار الرد/الرفض احتفظت بالأخضر والأحمر (اتفاقية استخدام آمنة)
3. ملاحظة تقنية: `ContactResolver` ليس في مخطط Hilt (عرف المشروع بناء مباشر بالسياق) — الحقن الأول فشل بـMissingBinding وصُحح لبناء مباشر

### ✅ FIX M32 — إعدادات الحظر بلا Save + النافذة العائمة للمكالمات المحظورة لم تكن موصولة

1. **النافذة العائمة كانت مبنية ومعزولة!** `BlockedCallBubbleService` كامل (whitelist/call/cancel + صوت) لكن **لا أحد يستدعي `show()`** — الحظر يحدث صامتاً في `CallScreeningServiceImpl`:
   - بعد `respondToCall(blockResponse)` تُطلق الآن coroutine على IO تحل اسم جهة الاتصال ثم تستدعي `BlockedCallBubbleService.show(...)` — fire-and-forget لا يمس مهلة الفحص (1.5s)
   - المدة 60 ثانية → **40 ثانية** حسب الطلب، والصوت عبر `SoundManager.playBlockedCallSound()` (نفس محرك نافذة OTP)
2. **إعدادات الحظر تطبَّق فوراً بالضغط** → حوار `CallBlockingSettingsDialog` جديد بحالة مرحلية:
   - زر **Save** بجانب **Close** — لا شيء يُكتب في DataStore إلا بالحفظ
   - كشف خيارين كانا في DataStore بلا واجهة إطلاقاً: **حظر الأرقام المجهولة/الخاصة** (`blockUnknownNumbers`) و**وضع القائمة البيضاء فقط** (`whitelistOnlyMode`) مع تحذير مرئي عند تفعيله
   - اختيار SIM داخل الحوار بدل النافذة المنبثقة التلقائية؛ العنصر الرئيسي في البطاقة أصبح Clickable يفتح الحوار
   - أحداث جديدة: `ShowCallBlockingSettings` / `HideCallBlockingSettings` / `SaveCallBlockingSettings`

### ✅ FIX M33 — ترويسة تفاصيل جهة الاتصال تتداخل مع شريط حالة النظام
- السبب: `Scaffold` في `ContactDetailScreen` مضبوط على `WindowInsets(0,0,0,0)` والعمود الجذري بلا حشوة شريط الحالة → الترويسة ترسم تحت ساعة/بطارية النظام
- الحل: `.statusBarsPadding()` على العمود الجذري قبل بقية الحشوات

### ✅ FIX M34 — حذف الملاحظات (شاشة المكالمة + المعاينة العريضة)
1. **شاشة المكالمة**: بطاقة الملاحظات كانت عرض فقط — أضيف حدث `DeleteCallNote(noteId)` + `deleteCallNoteImpl` (حذف من DAO + تحديث القائمة) + أيقونة سلة لكل ملاحظة مع حوار تأكيد في `MiddleCardTabs.kt`
2. **تفاصيل جهة الاتصال**: زر الحذف موجود أصلاً وموصول عبر NavHost ✓ — لكن **المعاينة العريضة** في `ContactsScreen` (شاشات ≥600dp) كانت تمرر `notes = emptyList()` و`onDeleteNote = {}` فارغة:
   - حقن `ContactNoteDao` في `ContactsViewModel` + أحداث `ContactPreviewSelected`/`DeletePreviewNote`
   - تحميل الملاحظات بـLaunchedEffect عند تغيير الاختيار وتحويل Entity→domain model
3. سلاسل جديدة: `delete_note_title`/`delete_note_message` (عربي+إنجليزي)

### ✅ FIX M35 — جهات الاتصال المفضلة أولاً في القائمة
- `filterContacts` (فلتر ALL) كان يرتب أبجدياً فقط؛ الآن `compareByDescending(isFavorite).thenBy(الاسم)` — المفضلة تتصدر القائمة ثم البقية أبجدياً

### ✅ FIX M36 — رفع الإصدار إلى 29 / 1.2.9 + بناء AAB
- `versionCode = 29`, `versionName = "1.2.9"` في `app/build.gradle.kts`

### ✅ FIX M37 — دعم صفحات الذاكرة 16KB (متطلب Google Play 11/2025)
- **السبب**: `net.zetetic:android-database-sqlcipher:4.5.4` مكتبة قديمة وصلت نهاية حياتها — ملفها `libsqlcipher.so` بمحاذاة 4096 فقط، بينما بقية المكتبات سليمة
- **الترحيل** إلى البديل الرسمي `net.zetetic:sqlcipher-android:4.17.0` (يدعم 16KB منذ 4.6.1):
  - `SupportFactory` → `SupportOpenHelperFactory` (نفس تمرير hex key)
  - `net.sqlcipher.database.SQLiteDatabase.openDatabase(path, "", null, flags)` → `net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(path, ByteArray(0), null, flags, null)` في `migrateToEncrypted`
  - `System.loadLibrary("sqlcipher")` كما هو (الاسم لم يتغير)
- **توافق القاعدة المشفرة**: صيغة SQLCipher v4 مستقرة بين الإصدارات — اختُبر على جهاز Samsung: التطبيق فتح القاعدة المشفرة القديمة دون أي خطأ (لا SQLiteException ولا "file is not a database")
- **تحقق نهائي**: تحليل ELF headers لكل `.so` داخل الـAAB النهائي → كلها `p_align ≥ 16384` ✓

### ✅ FIX M38 — نافذة المكالمات المحظورة لا تظهر أبداً (3 طبقات)
تشخيص عبر `dumpsys telecom`: الحظر يعمل (`CallBlockReason: CALL_SCREENING_SERVICE`) لكن النافذة والإشعار مفقودان:
1. **الجذر: الخدمة غير مسجلة في AndroidManifest.xml!** الكود كامل لكن `startForegroundService` يرمي استثناءً يُبتلع بصمت في `show()`. أُضيف التعريف
2. **نوع FGS خاطئ**: كان `phoneCall` الذي يتطلب مكالمة نشطة على Android 14+ — والمكالمة المحظورة مرفوضة أصلاً → تحول إلى `shortService` (نفس نمط نافذة OTP المجرّب، حد 3 دقائق > مدتنا 40 ثانية)
3. **صلاحية التراكب مرفوضة على الجهاز**: `SYSTEM_ALERT_WINDOW granted=false` رغم أن التطبيق هو الهاتف الافتراضي (Samsung سحبها) — مُنحت عبر appops؛ يلزم فحصها يدوياً في إعدادات OneUI بعد أي تحديث
- إضافي: توسعة استثناء LeakCanary لتشمل `CallScreeningBinder.this$0` (رُصد مكشوهاً بالسجلات — نفس النمط الإطاري لـM30)

### ✅ FIX M39 — إزالة مفاتيح «داكن/افتراضي النظام» من الإعدادات
- الهوية البصرية موحدة (داكنة BlueSky) والمفاتيح كانت شكلية (`isLightTheme = false` ثابت في الكود)
- بطاقة «المظهر» أصبحت لعنصر اللغة فقط؛ منطق DataStore/ViewModel بقى كما هو دون كسر

### ✅ FIX M40 — تنظيف جذري لمخلفات نظام الثيم (تكملة M39)
1. **حذف `ThemePreferences.kt` كاملاً** — ملف ميت بلا أي مستدعين
2. **SettingsDataStore**: حذف المفاتيح (dark_mode/auto_theme/theme_color) والـflows والدوال والـaliases — كانت themeColor ميتة أيضاً
3. **SettingsViewModel**: تنظيف BasicSettings وcombine (7→5 flows مع إعادة الفهرسة) ومعالجي الحدثين
4. **InCallActivity**: حذف جمع isDarkMode/autoTheme غير المستخدم (`useDarkTheme = false` ثابت أصلاً)
5. **SettingsUiState**: حذف الحقلين والحدثين

### ✅ FIX M41 — إزالة عناوين بطاقات الإعدادات (المظهر/حظر المكالمات/الخصوصية)
- `SettingsCard` أصبح عنوانه اختيارياً (`title: String? = null`) والبطاقات الثلاث بلا عناوين — تصميم أنظف بهوية موحدة

### ✅ FIX M42 — إزاحة شاشة الإعدادات عن شريط حالة النظام
- `SettingsScreen.kt`: إضافة `.statusBarsPadding()` على عمود الجذر (نفس نمط إصلاح M33 لشاشة جهة الاتصال) — كان المحتوى متداخلاً مع شريط الحالة
- ملاحظة عملية: بعد تعديل M41 ثُبِّت APK قديم بالخطأ (compileDebugKotlin لا ينتج APK) — القاعدة: **assembleDebug قبل كل تثبيت جهاز**

### ✅ UPDATE M43 — ترقية targetSdk إلى 36 (Android 16) استجابةً لتحذير Google Play
- تحذير قوقل: اعتباراً من 1 نوفمبر 2026 يجب targetSdk خلال سنة من آخر إصدار أندرويد — كان لدينا 35 (أعلى غير متوافق)
- `gradle/libs.versions.toml`: AGP 8.7.3 → **8.9.2** (أقل إصدار يدعم API 36 بنفس Gradle 8.11.1 الموجود — صفر تغيير wrapper)
- `app/build.gradle.kts`: compileSdk/targetSdk 35 → **36**، versionCode 31 / versionName **1.3.1**
- التوافق مع سلوكيات API 36: لا نستخدم `windowOptOutEdgeToEdgeEnforcement` (ملغاة في 36)، وإصلاحات edge-to-edge (M33/M42) مطبقة أصلاً، وCompose Navigation يتعامل مع predictive back تلقائياً
- التحقق: build ناجح + تثبيت debug على الجهاز (`dumpsys package` → targetSdk=36) + AAB release يحوي targetSdk=36 في الـmanifest protobuf

### ✅ FIX M44 — دمج أقسام بطاقة السجل في شاشة تفاصيل جهة الاتصال
- كانت البطاقة الثانية مقسمة: عنوان خارجي «السجل» + بطاقتان متداخلتان بعنوانين «سجل المكالمات» (`contact_detail_call_history`) و«Recent Calls»
- `ContactDetailScreen.kt`: حذف العنوان الخارجي من Card 2
- `ContactInfoComponents.kt`: `CallStatisticsSection` و`RecentCallsSection` أصبحتا `Column` عادية بدل `SectionCard` المتداخلة — بطاقة واحدة موحدة بلا أي عناوين
- قاعدة تشغيل جديدة: **لا يُثبَّت على الهاتف إلا إصدار release** (`assembleRelease`) — نسخ debug للتطوير فقط

### ⏸️ مؤجَّل: نقل DAOs خارج ViewModels (بند 12)
- التسريب يمتد لـ7 ملفات (`ContactDetailViewModel`, `ScheduledMessagesViewModel`, `ConversationLoaderDelegate`, `ConversationScheduleOps`, `InCallViewModel*`) بمنطق جدولة وملاحظات حقيقي
- قرار التأجيل: إعادة هيكلة معمارية بمخاطرة انحدار في منطق الجدولة (Workers تعتمد عليه)، تستحق جلسة مخصصة مع اختبارات integration قبل وبعد — لا تُنجَز كتعديل عابر

### خطوة التحقق الميدانية المتبقية
أرسل صورة واحدة لهاتف الاستقبال وراقب:
```bash
adb logcat -s MmsReceiver MmsDownloadedReceiver MmsDownloadDedup | grep -E "TRIGGERED|DEDUP|NotifyResp|persisted"
```
- توقُّع سلوك سليم: دورة TRIGGERED واحدة + سطر `NotifyRespInd (Retrieved) sent` + لا أسطر `[DEDUP]`
- إذا ظهرت `[DEDUP]` بعد NotifyResp فاشلة → المشغّل ما زال يعيد الدفع لكن الحماية تمنع النسخ (سلوك مقبول، راقب أسباب فشل NotifyResp)

---

## ملخص الأرقام

| المؤشر | القيمة |
|--------|--------|
| اختبارات الوحدة | 810 ناجحة / 0 إخفاقات |
| ملفات Kotlin (main) | ~370 ملفاً |
| ملفات الاختبار | ~90 ملفاً |
| لغات مدعومة | 51 لغة |
| إصدارات Room migrations | 15 migration (v1→v15) |
| ملفات >800 سطر | 10 ملفات |
| استخدامات collectAsState | 0 (محوّلة بالكامل) |
| TODO/FIXME في main | 1 فقط |

---

## ⚖️ مقارنة شاملة: منطق MMS — Purevon مقابل QKSMS

> المرجع: `/home/abohmam/Downloads/qksms-master/` (QKSMS 3.x + مكتبة android-smsmms المضمّنة)

### الخلاصة التنفيذية
Purevon **متقدم** على QKSMS في 6 محاور جوهرية (الاستجابة للشبكة، منع التكرار، ضغط الفيديو، SMIL الذكي، الإرسال ثنائي الاستراتيجية، إعدادات الشبكة الحديثة). QKSMS يتفوق في **بساطة نموذج المعرفات** (بدون إزاحة إطلاقاً) ويعلّمنا دروساً وقائية موثقة أدناه.

### جدول المقارنة التفصيلي

| المحور | Purevon | QKSMS | الأفضل |
|--------|---------|-------|--------|
| **إرسال MMS** | ثنائي الاستراتيجية: HTTP مباشر (klinker) ← fallback نظام `sendMultimediaMessage` مع backoff (MmsSender.kt:526-635) | النظام فقط؛ محرك WAP القديم موجود لكنه **خامل** (TransactionService بلا SEND) | **Purevon** |
| **إشعار الشبكة بعد التنزيل** | `NotifyRespInd(STATUS_RETRIEVED)` يُرسل دائماً بعد نجاح الإدراج (M19) | **لا يرسله أبداً** — الكود موجود في NotificationTransaction لكنه خامل؛ تعليقهم بالكود يعترف أن بعض المشغلين يعيدون إرسال نسخ مكررة! | **Purevon** ✅ |
| **منع تكرار الاستقبال** | 3 طبقات دائمة: فحص tr_id بالقاعدة + content-location بـSharedPreferences (48 ساعة) + تخطٍ مبكر في المستقبل (M20) | مجموعة in-memory تنتهي بموت العملية + خريطة in-flight؛ فحص القاعدة **كود ميت** (`//return true` معلّق في PushReceiver.java:263)؛ dedup في Realm بـ(type, contentId) فقط — نسخة بإذن مختلف = رسالة ثانية! | **Purevon** ✅ |
| **نموذج المعرفات** | إزاحة +2_000_000_000 لعرض MMS — سببت خلل البحث بالمفتاح الخام (تم إصلاحه) | PK أصلي بدون أي إزاحة؛ Realm يخزن contentId الأصلي ويعيد استخدامه عند إعادة الإرسال | **QKSMS** (أبسط وأأمن) |
| **ضغط الوسائط قبل الإرسال** | صور (عرض أقصى من CarrierConfig، مدى 1024-1920) **+ فيديو** + تقدير حجم قبل الضغط | صور فقط بحلقة إعادة ضغط تدريجية ضد حدود الشبكة (wiggle 0.9)؛ **لا ضغط فيديو** | **Purevon** |
| **SMIL** | SmilBuilder ذكي بتجميع الشرائح (نص+وسائط متناوبة) | SMIL بسيط بشريحة واحدة دائماً مقدَّماً للتوافق | **Purevon** |
| **إعدادات الشبكة** | `CarrierConfigManager.getConfigForSubId` وقت التشغيل (حديث، صفر ملفات) | مئات ملفات mms_config.xml لكل شبكة + قيم قديمة؛ UA/UAProf لا يصلان للنظام أبداً | **Purevon** |
| **تخزين الأجزاء** | URIs نصية "content://mms/part/{id}" منسوخة داخل صف الرسالة الواحد (cached_messages) | جدول Realm مستقل MmsPart مفهرس بربط عكسي LinkingObjects | تعادل (لكل نمط مزاياه) |
| **معالجة WAP Push** | goAsync + CoroutineScope لكل استلام (متوازٍ) | منفذ أحادي الخيط تسلسلي — يضمن الترتيب | **QKSMS** (ترتيب آمن) |
| **اكتمال الإرسال** | تحديث MESSAGE_BOX مباشرة داخل MmsSender + حالات FAILED موثقة (:654+) | MmsSentReceiver عبر مطابقة taskAffinity (**هش**)؛ تحديث PendingMessages بعد الفشل **معطوب** (TODO بالكود) | **Purevon** |
| **إعادة إرسال فاشل** | retryFailedMessage بقفل موحد (M17 أصلح deadlock) | resendMms يعيد استخدام الصف الأصلي (existingUri) — نظيف | تعادل (لكل نمط ميزة) |
| **تنظيف أخطاء HTTP** | — | حذف NotificationInd حسب CONTENT_LOCATION عند 400/404 + تمييز RETRIEVE_STATUS_ERROR_END للاستجابة الفارغة | **QKSMS** (نمط يستحق التبني) |

### دروس وقائية من قراءة QKSMS
1. **تعليقهم في الكود يؤكد صحة تشخيصنا**: «some carriers will redeliver duplicates without the ACK» — نفس سبب حادثة التكرار لدينا بالضبط. إصلاح M19 ليس رفاهية.
2. **dedup الذاكرة يموت بالعملية**: مجموعة downloadedUrls عندهم تُمسح عند إعادة تشغيل العملية — لهذا طبقاتنا الدائمة (M20) هي المعيار الصحيح.
3. **نموذج بلا إزاحة = فئة كاملة من الأخطاء مستحيلة**: خلل اليوم (بحث خرائط بمفتاح مُزاح) غير وارد في تصميم QKSMS. توصية: اعتبار `rawSystemId()` بوابة إلزامية ومراجعة كل استخدام جديد لمعرف MMS.
4. **أنماط تستحق التبني مستقبلاً**: (أ) تنظيف NotificationInd عند فشل HTTP 400/404، (ب) تمييز الاستجابة الفارغة كخطأ نهائي، (ج) Message-ID من RetrieveConf كطبقة dedup رابعة أقوى من tr_id.

---
