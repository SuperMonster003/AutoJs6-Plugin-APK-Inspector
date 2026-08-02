<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>فحص عميق للقراءة فقط لملفات APK وحاويات الحزم المجزأة وAndroid App Bundle في AutoJs6 Explorer</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### اللغات (Languages)

******

يدعم ملف README.md الحالي اللغات التالية:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- [Francais [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Espanol [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Russkii [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- العربية [ar] # الحالية

******

### مقدمة

******

توفر إضافة AutoJs6 APK Inspector إجراء الفحص الأساسي لملفات حزم Android في Explorer الرئيسي. تحلل نسخة خاصة بالتطبيق ذات حجم محدود ولا تعدل الحزمة المصدر أو تثبتها.

******

### الميزات

******

- تسجل إجراء Explorer Action أساسي ببروتوكول v2 لملفات APK وAPKS وXAPK وAPKM وAPKZ وAAB.
- تفك ترميز manifest النصي والثنائي في APK وmanifest بتنسيق protobuf في AAB وبيانات bundletool toc.pb.
- تعرض هوية الحزمة والإصدار ونطاق SDK والأذونات المطلوبة والمكونات والأجزاء المطابقة للجهاز وأصول OBB والمشكلات البنيوية.
- تكتشف وجود مخططات توقيع APK V1 وV2 وV3 دون ادعاء صلاحيتها التشفيرية.
- تعرض Android Manifest منسقا في عارض مستقل للقراءة فقط.
- توفر بوابة Android ACTION_VIEW مستقلة لأنواع MIME الدقيقة لحزم Android.

******

### الصيغ المدعومة

******

يطابق الإجراء الأساسي في Explorer الامتدادات التالية بدقة:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### واجهة الإضافة

******

يكتشف AutoJs6 الإضافة وينفذها بالهويات التالية:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5269
supported ABIs: unrestricted (supportedAbis = emptyArray())
```

الإصدار 1 مخصص للفحص فقط. لا يحتوي زر تثبيت أو إذن تثبيت أو مثبت حزم أو تحرير للمصدر أو استعراض للمجلد. تبقى مسارات التثبيت في التطبيق المضيف مستقلة. عند غياب الإضافة يستخدم AutoJs6 السلوك البديل في التطبيق المضيف.

الإضافة مكتوبة بالكامل على JVM ولا تحتوي مكتبة أصلية. تعلن supportedAbis = emptyArray() وتنشر كملف APK واحد مستقل عن ABI. يلزم AutoJs6 بالإصدار 5269 أو أحدث.

******

### الأمان

******

تتحقق بوابة Explorer المحمية من البروتوكول v2 وسطح الملفات الرئيسي ومعرف الإجراء وتسلسل content URI وClipData الدقيق واسم العرض والامتداد ونوع MIME والحجم ومنح القراءة فقط. تقبل بوابة ACTION_VIEW العامة أنواع MIME المخصصة للحزم فقط. ينسخ الإدخال مرة واحدة إلى نسخة خاصة محدودة الحجم للقراءة فقط مع حساب SHA-256. لا يتم استعراض URI الأب في البروتوكول.

******

### حدود الأمان

******

- الحد الأقصى لحجم الإدخال: 4 GiB.
- ملف هدف واحد لكل إجراء مع مساحة احتياطية لا تقل عن 128 MiB في الذاكرة المؤقتة.
- تخضع أعداد مدخلات الأرشيف وأسماؤها وأحجامها المعلنة والإجمالية وفحص APK المتداخل والبيانات الوصفية وprotobuf وإخراج manifest لحدود ثابتة.
- ترفض ACTION_VIEW الخارجية application/zip وapplication/octet-stream ومنح الكتابة والاستمرار وprefix.
- مخططات التوقيع من V1 إلى V3 هي فحوص وجود فقط. يحتاج V4 إلى إدخال idsig منفصل وهو خارج هذا البروتوكول.
- لا تثبت الإضافة الحزم ولا تطلب أذونات التخزين أو الشبكة أو تثبيت الحزم.

******

### سجل الإصدارات

******

# v1.0.0

###### 2026/08/02

* `ميزة` إضافة APK Inspector بمعرف `apk-inspector` ومعرف إجراء `inspect-android-package` ومحرك `explorer-action` ومتغير `default`
* `ميزة` فحص أساسي للقراءة فقط ببروتوكول Explorer Action v2 لملفات APK وAPKS وXAPK وAPKM وAPKZ وAAB
* `ميزة` فك ترميز للقراءة فقط لـ manifest النصي والثنائي في APK وmanifest بتنسيق protobuf في AAB وبيانات bundletool `toc.pb`
* `ميزة` تفاصيل الحزمة والأذونات المطلوبة والمكونات والأجزاء المطابقة للجهاز وأصول OBB والنتائج البنيوية وعرض manifest منسق واكتشاف وجود توقيعات APK V1-V3
* `ميزة` بوابتان منفصلتان لـ Explorer المحمي وAndroid `ACTION_VIEW` بنوع MIME دقيق مع حد 4 GiB ونسخة خاصة محدودة للقراءة فقط تحسب SHA-256
* `ميزة` تنفيذ JVM خالص دون مكتبة أصلية وABI غير مقيد عبر `supportedAbis = emptyArray()` وملف APK واحد مستقل عن ABI مع اشتراط AutoJs6 build 5269
* `ميزة` بيانات وصفية وواجهة وتعليمات وREADME وسجلات تغيير موطنة بالإسبانية والفرنسية والروسية والعربية واليابانية والكورية والإنجليزية والصينية المبسطة والصينية التقليدية لهونغ كونغ وتايوان
* `اعتماد` إضافة Gson الإصدار 2.13.2

##### لمزيد من الإصدارات

* [CHANGELOG-ar.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-ar.md)

******

### البناء

******

```powershell
.\gradlew.bat :app:assembleDebug
```

بناء Release:

```powershell
.\gradlew.bat :app:assembleRelease
```

تأتي معلمات البناء من version.properties. الحد الأدنى الحالي لـ SDK هو 24 وSDK الهدف هو 36.

******

### بنية الموارد

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

يوفر strings.xml توطين بيانات الإضافة ونصوص الواجهة. يوفر plugin_instruction.md تعليمات يعرضها التطبيق المضيف. ينشئ .python/generate_markdown.py ملفات README وسجلات التغيير الموطنة من مصادر JSON.

******

### الروابط

******

- توثيق AutoJs6: https://docs.autojs6.com
- مشاركة الملفات الآمنة في Android: https://developer.android.com/training/secure-file-sharing
