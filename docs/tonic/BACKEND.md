# بک‌اند تستی TONIC (FastAPI)

مسیر: `backend/`

## اجرا
```bash
cd backend
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```
- برای **امولاتور اندروید**: چیزی لازم نیست؛ پیش‌فرض `PUBLIC_BASE_URL=http://10.0.2.2:8000`
  و اپ هم به همین آدرس وصل می‌شود (`BRAND_BACKEND_URL`).
- برای **گوشی واقعی** روی همان شبکه: بک‌اند را با IP کامپیوترت اجرا کن:
  ```bash
  PUBLIC_BASE_URL=http://192.168.1.10:8000 uvicorn main:app --host 0.0.0.0 --port 8000
  ```
  و در `brand/brand.properties` مقدار `BRAND_BACKEND_URL` را روی همان بگذار.

> اگر پورت ۸۰۰۰ اشغال بود، پورت دیگری بده و `BRAND_BACKEND_URL`/`PUBLIC_BASE_URL` را هماهنگ کن.

## قرارداد API

### `POST /api/login`
درخواست:
```json
{ "username": "demo", "password": "demo123" }
```
پاسخ ۲۰۰:
```json
{ "token": "…", "subscriptions": [ { "name": "TONIC", "url": "http://10.0.2.2:8000/sub/all" } ] }
```
پاسخ ۴۰۱ در صورت اشتباه بودن یوزر/پس.

### `GET /api/subscriptions`
هدر: `Authorization: Bearer <token>` → همان لیست ساب‌ها (به‌روز). بدون توکن ۴۰۱.

### `GET /sub/{group}`
بدنه‌ی ساب به‌صورت base64 از لیست لینک‌های `vmess://` (دقیقاً مثل ساب استاندارد v2ray).
اپ خودش این را fetch و پارس می‌کند.

## یوزرهای تستی
| username | password |
|----------|----------|
| `demo`   | `demo123` |
| `user1`  | `pass1` |

## تست سریع با curl
```bash
curl -s -X POST http://127.0.0.1:8000/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}'
```

## اتصال به پنل واقعی
این بک‌اند فقط تستی است (یوزرها در حافظه، سرورهای دمو). برای production:
- `USERS` و منطق احراز را با دیتابیس/پنل خودت (مثلاً Marzban) جایگزین کن.
- در `_subs_for` به‌جای `/sub/{group}` لینک ساب واقعی هر کاربر را برگردان.
- هر ساب می‌تواند چند کانفیگ داشته باشد؛ اپ همه را ایمپورت و سریع‌ترین را انتخاب می‌کند.
