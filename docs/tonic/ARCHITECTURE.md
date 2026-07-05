# معماری TONIC

اصل کار: **موتور v2rayNG دست‌نخورده می‌ماند؛ فقط UI و منطق ورود داده عوض می‌شود.**

```
┌───────────────┐   login/subs    ┌────────────────────┐
│ FastAPI backend│◄───────────────│  TonicApi (OkHttp) │
└───────────────┘                 └─────────┬──────────┘
        ▲ /sub/{group}                       │
        │ (base64 configs)                    ▼
        │                          ┌────────────────────┐
        │        fetch sub ◄───────│    TonicManager     │  (ارکستراسیون)
        │                          └───┬─────────┬──────┘
        │                              │         │
                     AngConfigManager ◄┘         └► MmkvManager (ذخیره ساب/سرور)
              (parse & import configs)                 │
                              │                        ▼
                              ▼                 CoreServiceManager → CoreVpnService
                        SubscriptionUpdater            (تونل Xray)
                        (آپدیت دوره‌ای ۱۲ساعته)
```

## چیزهایی که **بازاستفاده** شدند (بدون تغییر)
- `core/CoreServiceManager` — استارت/استاپ تونل (`startVService` / `stopVService`).
- `service/CoreVpnService` و برودکست وضعیت (`AppConfig.BROADCAST_ACTION_ACTIVITY`,
  `MSG_STATE_*`).
- `handler/AngConfigManager` — دانلود ساب و پارس/ایمپورت کانفیگ‌ها.
- `handler/MmkvManager` — ذخیره‌سازی ساب‌ها و سرورها (MMKV).
- `handler/SubscriptionUpdater` — آپدیت دوره‌ای هر ساب با WorkManager (interval را
  روی ۱۲ ساعت ست می‌کنیم).

## چیزهایی که **جدید** ساخته شدند (پکیج `com.v2ray.ang.tonic` + چند Activity)
- `tonic/TonicApi.kt` — کلاینت OkHttp/Gson برای `/api/login` و `/api/subscriptions`.
- `tonic/TonicStore.kt` — ذخیره‌ی توکن/یوزرنیم در MMKV (multi-process).
- `tonic/TonicManager.kt` — پل بین بک‌اند و موتور: لاگین، تبدیل لینک ساب به
  `SubscriptionItem`، فراخوانی import، انتخاب خودکار سریع‌ترین سرور، logout.
- `tonic/TonicSync.kt` — worker دوره‌ای که **لیست ساب‌ها** را از بک‌اند تازه می‌کند.
- `ui/LoginActivity.kt` — صفحه‌ی ورود.
- `ui/TonicMainActivity.kt` — صفحه‌ی اصلی تک‌دکمه‌ای + وضعیت.
- `ui/LocationPickerActivity.kt` + `ui/TonicLocationAdapter.kt` — انتخاب اختیاری لوکیشن.

## چیزهایی که **قفل/حذف** شدند (کلاینت خصوصی)
- launcher از `MainActivity` به `LoginActivity` منتقل شد؛ `MainActivity` قدیمی و
  همه‌ی صفحات مدیریت ساب/سرور دیگر نقطه‌ی ورود نیستند.
- `UrlSchemeActivity` و intent-filterهای `SEND`/`VIEW`/`v2rayng://` حذف شدند تا
  ایمپورت کانفیگ/ساب از بیرون ممکن نباشد.
- در UI جدید هیچ گزینه‌ی افزودن/اکسپورت/اشتراک/اسکن QR وجود ندارد.
- فلگ `BuildConfig.LOCK_DOWN` برای دفاع لایه‌ای در دسترس است.

## جریان اجرا
1. کاربر لاگین می‌کند → `TonicManager.login` توکن و لیست ساب را می‌گیرد.
2. لینک‌های ساب به‌صورت `SubscriptionItem` (با `autoUpdate=true`, `updateInterval=720`)
   ذخیره و با `AngConfigManager.updateConfigViaSubAll()` به کانفیگ تبدیل می‌شوند.
3. `SubscriptionUpdater.sync()` آپدیت هر ۱۲ ساعت هر ساب را زمان‌بندی می‌کند و
   `TonicSync` هر ۱۲ ساعت لیست ساب‌ها را از بک‌اند تازه می‌کند.
4. دکمه‌ی اتصال → انتخاب سریع‌ترین سرور → `CoreServiceManager.startVService`.
