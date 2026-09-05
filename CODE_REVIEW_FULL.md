# المراجعة الشاملة النهائية — تطبيق Purevon / Rasmi

> مراجعة كاملة وإعادة فحص من الصفر، طبقة بطبقة، مع تَحقّق يدوي من كل ادعاء حرج قبل اعتماده.
> التاريخ: 31 أغسطس 2026
> النطاق: كامل الـ codebase (369 ملف Kotlin في main، ~77 ألف سطر) — data / domain / presentation / technical / security / tests / resources.

---

## 0) لقطة رقمية موضوعية

| المقياس | القيمة |
|---|---|
| ملفات Kotlin (main) | 369 |
| إجمالي الأسطر (main) | ~76,800 |
| أكبر ملف | `HistoryScreen.kt` = 1169 |
| ملفات تجاوزت 800 سطر | 14 |
| إصدار قاعدة البيانات | v15 (14 migrations) |
| اختبارات وحدة (@Test) | 810 |
| اختبارات موجهة (androidTest) | 9 |
| لغات الترجمة | 52 (51 لغة + default) |
| سلاسل في العربية/default | 702/702 (مكتمل) |
| سلاسل في الـ 49 لغة الأخرى | 674 (ناقص 28) |

---

## 1) البنية والمعمارية

### إيجابيات البنية
- **معمارية نظيفة حقيقية** بثلاث طبقات مفصولة (data/domain/presentation) مع Hilt.
- فصل ناجح للمستودع الضخم الأصلي (God class) إلى 9 delegates في `MessageRepositoryImpl` كما في `ConversationViewModel` (مقسم إلى `ConversationLoaderDelegate`, `MessageSendingDelegate`, `AttachmentDelegate`, `LocationSharingDelegate`, `DraftManager`...).
- **Roadmap SQLCipher + AndroidKeyStore متين**: كلمة مرور 256-bit عشوائية، تُشفر بـ AES-GCM-128 بمفتاح AndroidKeyStore، مع ترحيل آمن لقاعدة غير مشفرة (DatabaseModule.kt:114-175).
- `allowBackup="false"` مع `dataExtractionRules` — قرار أمني صحيح لمنع تسريب الرسائل عبر النسخ الاحتياطي.
- `network_security_config.xml`: قائمة بيضاء دقيقة لنطاقات MMSC فقط مع `cleartextTrafficPermitted="false"` للباقي — **مثال ممتاز** في أذونات الشبكة.

### نقاط ضعف البنية (عالية)
- **`inflation of usecases`**: 70 usecase من أصل ~80 هي «pass-through» بلا منطق (كل استدعاء repository له usecase واحد بلا قيمة). يضاعف الصيانة بلا فائدة سلوكية. (domain/usecase/*)

---

## 2) طبقة الـ DATA

### حرجة
- **`queryUnreadCountOptimized()` لا يحسب رسائل MMS**: يعدّ FM `Telephony.Sms` فقط (SystemQueryHelper.kt:442-457) لذا العدد غير المقروء المعروض أقل من الواقع.

### عالية
- **`MMS_ID_OFFSET = 2_000_000_000L` مكرر في 4 ملفات** (SystemMessageQueries.kt:19، MessageRepositoryImpl.kt:77، MessagePagingSource.kt:34، MessageReadStatusDelegate.kt:24). تغييره في ملف واحد فقط يكسر معرفات MMS. إضافةً إلى خلط متعمّد بين المعرف الخام والمزوّد عبر مسارات مختلفة (المزامنة تقرأ metadata بالخام في SystemMessageQueries.kt:440-449 بينما التنجيم/الحذف تكتب بالزوّد في MessageRepositoryImpl.kt:737-750) — دون طبقة موحّدة للهوية.
- **جدول `conversation_settings` مكرر مع `conversation_preferences`** — كود/schema ميت بالكامل (لا `@Provides` له، ولا مستخدم).

### متوسطة
- **N+1 queries**: `ContactResolver.batchResolveContactPhotoUrisOptimized` (ContactResolver.kt:193-218) ترسل استعلام كل صورة على حدة رغم اسمها. و`BlockRepositoryImpl.isNumberBlocked` يجلب كل الأرقام المحظورة في كل طلب (BlockRepositoryImpl.kt:49-55).
- **استدعاء تكراري في `syncConversations`** (MessageSyncDelegate.kt:240-287) داخل `finally` قد يكدّس عمقاً تحت ضغط رسائل متتالية.
- **Scope غير مُلغى**: `SyncRepositoryImpl` و`MessageRepositoryImpl.repositoryScope` لا يُلغيان في cleanup (تناقض مع `CallLogRepositoryImpl`).

### منخفضة / كود ميت
- `updateSpamInfo()`, `insertMetadataList()`, `getRecentSearches()` معرّفة في DAOs ولا يُستدعاها أحد.
- `preferredSimSlot` يُعاد دائماً null في `ContactRepositoryImpl`.

---

## 3) طبقة الـ DOMAIN

### عالية (تسريبات للمعمارية)
- `MessageRepository` يعتمد على `PagingData` (androidx) ونوع `Uri` في توقيعات الـ domain (domain/repository/MessageRepository.kt:3,21,33,150).
- `InCallServiceBridge` داخل `domain/call` تُرجع أنواع `android.telecom.Call` الفعلية (domain/call/InCallServiceBridge.kt:3,15-21) — جسر وليس domain.
- **`ContactAnalysisResult.getEstimatedTimeString()` يبني نصوصاً عربية داخل نموذج domain** (domain/model/ContactAnalysisResult.kt:54-60) — مسؤولية الترجمة تسرّبت لنموذج domain.

### متوسطة
- `android.util.Log` مستخدم في 5 usecases داخل domain.
- أسماء مستعارة في النماذج (`id`/`threadId`, `name`/`displayName`...) في Message/Conversation/Contact/CallLog — ازدواج في المفاهيم.

---

## 4) طبقة الـ PRESENTATION

### حرجة (تسريبات معمارية)
- استعلامات `ContentResolver` مباشرة داخل Composables (`ConversationScreen.kt:139,150,172,180,195,203`؛ `ContactDetailScreen.kt:83,121`) — عمليات على خيط الـ UI.
- `AddContactViewModel.kt` يحوي **17+ استدعاء ContentResolver مباشر** (313..914) — ViewModel حلّ محل Repository.
- **40+ مرجعاً مباشراً لكيانات/DAOs طبقة data داخل presentation** (`MessageBubble.kt:93-96` يشير لـ data.local.entity.MessageType؛ `ScheduledMessagesViewModel` يحقن `ScheduledMessageDao`؛ `InCallViewModel.kt:60` يحقن `ContactNoteDao`...).

### عالية
- ملفات ضخمة غير قابلة للصيانة: HistoryScreen(1169) MessagesScreen(1104) InCallViewModel(1045) ConversationScreen(1038) ContactsScreen(1018) AddContactViewModel(937) InCallScreen(940).
- **15+ LazyColumn/LazyRow بدون `key`** (SettingsBlockedDialogs, TemplateComponents, MessageReactions, StatisticsScreen...).
- **نصوص حرفية عربية/إنجليزية داخل `Text()`** في ~25+ موضعاً (PermissionRequiredState, ConversationActionsSheet, MessageActionsSheet, MessagesScreen, ...) — تكسر الترجمة.

### متوسطة
- `InCallViewModel` يحقن 11 تبعية و`Context` مباشر.
- `ConversationViewModel.kt:320` ينشئ `CoroutineScope` غير مُدار في `onCleared()`.
- فقدان الحالة عند دوران الشاشة (ما لا يُحفظ عبر savedStateHandle).
- "UNUSED" file: `component/MessageStatusIndicators.kt` (224 سطر) كود ميت.

---

## 5) الطبقة الفنية (receivers/services/workers) والأمان

### حرجة
- **`<cache-path path="." />` في provider_paths.xml يكشّف جذر الكاش بالكامل** عبر FileProvider — ينقض العزل الذي تدّعيه `util/SharedCacheFiles.kt`.

### عالية
- **محتوى الرسائل (رقم + نص + مرفقات) يُمرَّر بنص صريح في `workDataOf`** في `DelayedSendManager.kt:51-76` — WorkManager يخزّن InputData غير مشفّر.
- **`runBlocking` على خيط Binder** في `CallScreeningServiceImpl.kt:61-68` للوصول لقاعدة البيانات — قد يسبب ANR لجهاز الهاتف.
- **`Uri.fromFile` محظور (FileUriExposedException) على Android 7+** في `ImageCompressor.kt:127,223` و`VideoCompressor.kt:135,151,163` — يجب استبداله بـ FileProvider.
- **اختبارات SMS/MMS بلا تحقق صارم من التطبيق الافتراضي**: SmsReceiver/MmsReceiver تعالج بيانات البث دون فحص `getDefaultSmsPackage`.
- `PhoneStateReceiver` و`BootCompletedReceiver` مُصدَّران دون إذن أو `exported="false"` حيث الأفضل.

### متوسطة
- `HeadlessSmsSendService` بدون `foregroundServiceType` (قد يفشل على Android 14+).
- بيانات حساسة (أسماء/أرقام/أكواد USSD) في SharedPreferences غير مشفرة أو في Log (FakeCallScheduleManager, CallbackReminderScheduleManager, PhoneUtil).
- MD5 لأسماء ملفات الكاش؛ مسار كاش لا ينظّف بالكامل.

### إيجابيات أمنية مؤكدة
- APN `toString()` يخفي كلمة المرور (ApnManager.kt:84).
- الخدمات الحساسة محمية بأذونات النظام الصحيحة (BIND_INCALL_SERVICE, BIND_SCREENING_SERVICE...).
- `MmsDownloadedReceiver`/`NotificationActionReceiver`... `exported="false"`.

---

## 6) الاختبارات

### البنية التحتية (ممتازة)
- `HiltTestRunner` مكوّن، مكتبات كاملة (MockK, Turbine, Robolectric, Room-testing, Work-testing).
- `SendMessageUseCaseTest` (9 اختبارات) يختبر المنطق الفعلي — نموذج جيد.

### نقاط الضعف
- **تغطية منخفضة 20%** (75 ملف اختبار / 369 ملف main).
- **لا اختبارات على الإطلاق لـ receiver (12 ملف) و service (14 ملف)** — وهي أجزاء حرجة.
- **صفر اختبارات Compose/UI** رغم وجود الاعتماديات (لا `createComposeRule`).
- **صفر اختبارات @HiltAndroidTest** رغم جاهزية HiltTestRunner.
- presentation: 4 ملفات اختبار مقابل 127 ملفاً في main (~3%).
- جزء من اختبارات domain يختبر «data class» (equality/hashCode/toString) بلا قيمة سلوكية — تغطية وهمية.

---

## 7) الموارد والترجمة

### حرجة
- **~144 سلسلة عربية حرفية مضمّنة في الكود داخل `Text()`** (TemplateComponents, ContactSelectionDialog, InitialSyncScreen, MessageFormatter, OtpManager, InCallActivity...) — تظهر عربية في كل اللغات.

### عالية
- **28 سلسلة UI مفقودة في كل الـ 49 لغة غير العربية/الإنجليزية** (مفاتيح `stat_*`, `nav_back`, `dialer_*`, `delete_note_*`, `call_blocking_*`) — لوحة الإحصائيات وشاشة الهاتف تظهر إنجليزية.

### متوسطة
- **عدم تطابق رموز المحلية**: `LanguageConfig` يستخدم `"id"` (Indonesian) بينما مجلد المورد `values-in`؛ و`"he"` (Hebrew) بينما المجلد `values-iw` → المستخدم لن يجد ترجمته.
- **الوضع الداكن إجباري**: `PurevonTheme` بلا `isSystemInDarkTheme()`/`lightColorScheme` (صفر استخدام للمعامل). ألوان `themes.xml` (purple/teal) منبثقة من `DayNight` لا تُستخدم في Compose إطلاقاً — وميض بصري محتمل + تجاهل لتفضيل وضع النظام.

---

## 8) مخاطر هندسية/تشغيلية إضافية (تستحق الانتباه)

- **150 ملف غير مؤكَّد (Uncommitted)**: 140 معدَّل + 7 جديد + 3 محذوف، منذ آخر commit (18 أغسطس 2026). خطر فقدان عمل حقيقي.
- `compileSdk=36 / targetSdk=36` مع `minSdk=26` — جيد، لكن **`HeadlessSmsSendService` بلا type** و`shortService` لفقاعات OTP قد تعني مشاكل توافق Android 14+.
- الاعتماد على `klinker android-smsmms` (مكتبة قديمة جداً، موقوفة) مع مسارات MMS مزدوجة (klinker + SmsManager الافتراضي).

---

## 9) الأولويات المقترحة حسب الخطورة

### يجب إصلاحها أولاً (منتج/أمان)
1. حذف `<cache-path path="." />` — كاش-root مكشوف. (provider_paths.xml)
2. استبدال `Uri.fromFile` بـ FileProvider. (ImageCompressor, VideoCompressor)
3. تشفير محتوى الرسائل قبل `workDataOf`. (DelayedSendManager)
4. إخراج `runBlocking` من خيط Binder في CallScreeningServiceImpl (استخدم cache في الذاكرة).
5. حساب عدّاد MMS في `queryUnreadCountOptimized`.
6. توحيد `MMS_ID_OFFSET` في ثابت واحد + توحيد هوية المعرف الخام/المزوّد.
7. إضافة `foregroundServiceType` لـ HeadlessSmsSendService.

### معالجة التركيب/الجودة (قبل الانتشار)
8. نقل 17+ استعلام `ContentResolver` من `AddContactViewModel` إلى repository.
9. إزالة ~144 نصاً حرفياً إلى `strings.xml` + إكمال الـ 28 مفتاحاً في 49 لغة.
10. تصحيح رموز LanguageConfig (`id`→`in`, `he`→`iw`).
11. إضافة `key` لكل Lazy* و`placeholder/error` لـ AsyncImage.
12. حذف كود ميت (`conversation_settings`, `MessageStatusIndicators`, دوال DAO غير المستخدمة).

### إستراتيجية (تقنية)
13. دمج الـ pass-through usecases.
14. كتابة اختبارات للمستقبِلات والخدمات + اختبارات Compose الفعلية.
15. اتخاذ قرار صريح بخصوص الوضع الداكن (Light أو توثيق أنه مقصود).

---

## الخلاصة

التطبيق **قوي البنية والأمان الأساسي** (SQLCipher + AndroidKeyStore مثال جيد، معمارية نظيفة فاعلة، 810 اختبارات، ترجمة عربية مكتملة). المشاكل الحقيقية الحاجلة ليست في الأساس بل في **الانسجام الداخلي**: ازدواجية في هوية المعرفات، نصوص حرفية تتجاوز نظام الترجمة، تسريبات ContentResolver في طبقة العرض، وتغطية اختبارات متدنية في الأجزاء الحرجة (receivers/services/UI). مع معالجة النقاط 1-7، يصبح التطبيق جاهزاً للانتشار بثقة أعلى.
