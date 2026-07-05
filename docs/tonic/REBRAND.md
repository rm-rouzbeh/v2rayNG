# راهنمای ری‌برند و تم

همه‌ی تنظیمات برند از **یک فایل** خوانده می‌شوند:
`V2rayNG/brand/brand.properties`

بعد از تغییر این فایل، فقط کافیست دوباره بیلد بگیری. هیچ کد دیگری لازم نیست عوض شود.

## فیلدهای `brand.properties`

| کلید | کاربرد |
|------|--------|
| `BRAND_APP_NAME` | نام نمایشی اپ (لانچر، نوتیفیکیشن، صفحه‌ها) |
| `BRAND_TILE_NAME` | نام روی Quick-Settings tile |
| `BRAND_APPLICATION_ID` | پکیج‌نیم اپ روی گوشی/پلی (هر برند باید یکتا باشد) |
| `BRAND_BACKEND_URL` | آدرس بک‌اند (بدون `/` انتها) |
| `BRAND_SUPPORT_URL` | لینک پشتیبانی |
| `BRAND_SUB_UPDATE_HOURS` | بازه‌ی آپدیت خودکار ساب (ساعت) — پیش‌فرض ۱۲ |
| `BRAND_LOCK_DOWN` | قفل کلاینت خصوصی (منع افزودن/اکسپورت) |
| `BRAND_ALLOW_LOCATION_PICKER` | نمایش لیست انتخاب لوکیشن (`false` = فقط یک دکمه) |
| `BRAND_COLOR_PRIMARY` | رنگ لهجه‌ی اصلی (دکمه‌ها، هایلایت) |
| `BRAND_COLOR_PRIMARY_DARK` | واریانت تیره‌تر (status bar) |
| `BRAND_COLOR_CONNECTED` | رنگ حالت «متصل» |
| `BRAND_COLOR_DISCONNECTED` | رنگ حالت «قطع» |
| `BRAND_COLOR_BACKGROUND` | پس‌زمینه‌ی اپ |
| `BRAND_COLOR_SURFACE` | پس‌زمینه‌ی کارت‌ها |

این مقادیر در زمان بیلد به منابع اندروید تزریق می‌شوند
(`app_name`, `@color/brand_*`) و به `BuildConfig` (`BACKEND_URL`, `LOCK_DOWN`, ...).
جزئیات در `V2rayNG/app/build.gradle.kts` بخش `defaultConfig`.

## لوگو و آیکون (فایل‌های تصویری)

چون تصویرها باینری‌اند، جدا از `brand.properties` عوض می‌شوند:

1. **آیکون لانچر:** در Android Studio → راست‌کلیک روی `app` → *New → Image Asset* →
   تصویر لوگوی برند را بده تا همه‌ی `mipmap-*/ic_launcher*` را بازتولید کند.
2. **لوگوی داخل صفحه‌ی ورود:** فایل `mipmap/ic_launcher_foreground` استفاده می‌شود؛
   با Image Asset همان بازسازی می‌شود. اگر لوگوی مجزا می‌خواهی، یک drawable مثل
   `tonic_logo.png` اضافه کن و در `res/layout/activity_login.xml` مقدار `android:src`
   آن `ImageView` را به آن تغییر بده.
3. دارایی‌های آماده‌ی TONIC: `TONIC.svg` و پوشه‌ی `last_logo/` در ریشه‌ی
   `C:\AnotherSpace\Dev\TONIC`.

## چک‌لیست ری‌برند سریع
- [ ] `brand.properties` را ویرایش کن (نام، `applicationId`، `BACKEND_URL`، رنگ‌ها).
- [ ] آیکون لانچر را با Image Asset عوض کن.
- [ ] در صورت نیاز لوگوی صفحه‌ی ورود را عوض کن.
- [ ] بیلد بگیر (به `BUILD.md` مراجعه کن).
