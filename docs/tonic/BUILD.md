# راهنمای بیلد TONIC (قدم‌به‌قدم، ویندوز)

این راهنما برای کسیه که تا حالا اپ اندروید نساخته. دو مسیر داری:

- **مسیر A — CI (ساده‌ترین):** بدون نصب هیچ‌چی، APK رو از GitHub Actions می‌گیری.
- **مسیر B — لوکال (Android Studio):** روی کامپیوتر خودت بیلد می‌کنی.

> نکته درباره‌ی وابستگی نیتیو: اپ به دو چیز نیاز داره که در سورس نیستن و باید تأمین شن:
> `libv2ray.aar` (هسته‌ی Xray) و فایل‌های `.so` مربوط به tun2socks. ساده‌ترین راه اینه که
> یک‌بار CI رو اجرا کنی و artifact به اسم **native-libs** رو دانلود کنی (مسیر A این‌ها رو می‌سازه).

---

## مسیر A — گرفتن APK از GitHub Actions (بدون نصب)

1. یک ریپازیتوری روی GitHub بساز و این پروژه رو پوش کن (برنچ `tonic-client`):
   ```bash
   cd C:/AnotherSpace/Dev/TONIC/app/android
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin tonic-client
   ```
2. برو به تب **Actions** ریپو → workflow با اسم **«TONIC Build (debug APK, no secrets)»** → دکمه‌ی **Run workflow**.
3. صبر کن تا سبز شه (چند دقیقه). بعد پایین همون run، بخش **Artifacts**:
   - **TONIC-debug-apk** → همین APK رو دانلود و روی گوشی نصب کن.
   - **native-libs** → اگه خواستی لوکال هم بیلد کنی، این رو نگه دار (مرحله‌ی مسیر B).

این APK «دیباگ» و بدون امضای رسمیه ولی روی گوشی نصب و اجرا می‌شه. برای نسخه‌ی امضاشده‌ی
انتشار، بخش «APK امضاشده» پایین رو ببین.

---

## مسیر B — بیلد لوکال با Android Studio

### ۱) نصب ابزار
1. **Android Studio** را از https://developer.android.com/studio دانلود و نصب کن.
   موقع نصب، گزینه‌ی *Android SDK* و *Android SDK Platform* را تیک بزن. اندروید استودیو
   خودش **JDK ۱۷** و **SDK** را می‌آورد؛ لازم نیست جاوا را جدا نصب کنی.
2. یک‌بار Android Studio را باز کن تا SDK را کامل کند.

### ۲) تأمین کتابخانه‌های نیتیو (`libs`)
پوشه‌ی `V2rayNG/app/libs/` باید شامل این‌ها باشد:
- `libv2ray.aar`
- زیرپوشه‌های `arm64-v8a/`, `armeabi-v7a/`, ... با فایل‌های `.so` داخلشان.

**ساده‌ترین راه:** artifact **native-libs** از مسیر A را دانلود کن و محتوایش را داخل
`V2rayNG/app/libs/` کپی کن (اگر پوشه‌ی `libs` نیست، بسازش).

### ۳) باز کردن پروژه
1. در Android Studio: *Open* → پوشه‌ی **`V2rayNG`** (نه ریشه‌ی مخزن) را انتخاب کن.
2. صبر کن Gradle سینک شود. اگر SDK/NDK خواست، اجازه‌ی نصب بده.

### ۴) بیلد و نصب
- **از داخل Android Studio:** گوشی را با USB وصل کن (حالت Developer + USB Debugging روشن)
  یا یک Emulator بساز، بعد دکمه‌ی ▶ **Run** را بزن.
- **از خط فرمان** (PowerShell، داخل پوشه‌ی `V2rayNG`):
  ```powershell
  .\gradlew assembleFdroidDebug
  ```
  خروجی APK اینجاست:
  `V2rayNG\app\build\outputs\apk\fdroid\debug\`
  فایل را روی گوشی کپی و نصب کن.

> اگر با امولاتور تست می‌کنی، بک‌اند را با `PUBLIC_BASE_URL=http://10.0.2.2:8000` اجرا کن
> (پیش‌فرض همین است). اگر با گوشی واقعی روی همان وای‌فای تست می‌کنی، در
> `brand/brand.properties` مقدار `BRAND_BACKEND_URL` را روی IP کامپیوترت بگذار
> (مثلاً `http://192.168.1.10:8000`) و بک‌اند را با همان `PUBLIC_BASE_URL` اجرا کن.

---

## APK امضاشده (release)

برای انتشار واقعی نیاز به یک **keystore** داری.

### ساخت keystore (یک‌بار)
```powershell
keytool -genkey -v -keystore tonic.jks -keyalg RSA -keysize 2048 -validity 10000 -alias tonic
```
(از پوشه‌ای که `keytool` در PATH باشد — معمولاً داخل JDK اندروید استودیو:
`...\Android\Android Studio\jbr\bin`.)

### بیلد امضاشده‌ی لوکال
```powershell
cd V2rayNG
.\gradlew assembleRelease `
  -Pandroid.injected.signing.store.file=C:\path\to\tonic.jks `
  -Pandroid.injected.signing.store.password=<STORE_PASS> `
  -Pandroid.injected.signing.key.alias=tonic `
  -Pandroid.injected.signing.key.password=<KEY_PASS>
```
خروجی: `V2rayNG\app\build\outputs\apk\fdroid\release\`

### بیلد امضاشده در CI
workflow آماده‌ی `.github/workflows/build.yml` این کار را می‌کند. کافی است این
secretها را در ریپو ست کنی (*Settings → Secrets and variables → Actions*):
`APP_KEYSTORE_BASE64` (خروجی base64 فایل jks)، `APP_KEYSTORE_PASSWORD`،
`APP_KEYSTORE_ALIAS`، `APP_KEY_PASSWORD`. برای base64 کردن:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("tonic.jks")) | Out-File tonic.b64
```

---

## مشکلات رایج
- **Gradle sync خطا می‌دهد / `libv2ray.aar` پیدا نشد:** پوشه‌ی `libs` را طبق مرحله ۲ پر کن.
- **اپ نصب نمی‌شود «app not installed»:** نسخه‌ی قبلی با applicationId متفاوت نصب است؛ حذفش کن.
- **لاگین «network error»:** بک‌اند بالا نیست یا `BRAND_BACKEND_URL`/`PUBLIC_BASE_URL` هم‌خوان نیستند.
- **اتصال برقرار نمی‌شود:** کانفیگ‌های دموی بک‌اند سرور واقعی ندارند؛ برای تست واقعی، لینک ساب واقعی در بک‌اند بگذار.
