# blisschat push relay (Cloudflare Worker)

Бесплатная замена Firebase Cloud Functions (для них нужен платный тариф Blaze).
Приложение после отправки сообщения / начала звонка дёргает этот воркер, а он шлёт push через FCM.

## Установка (один раз)

Нужны Node.js и бесплатный аккаунт на https://dash.cloudflare.com (карта не требуется).

```powershell
cd cloudflare-worker
npm install -g wrangler
wrangler login
```

### 1. Ключ сервисного аккаунта

Firebase Console → ⚙ Настройки проекта → «Сервисные аккаунты» → «Создать новый закрытый ключ».
Скачается JSON-файл. **Не кладите его в репозиторий и никому не отправляйте.**

Положите секрет в Cloudflare (PowerShell, замените путь к файлу):

```powershell
Get-Content "C:\путь\к\ключу.json" -Raw | wrangler secret put SERVICE_ACCOUNT_JSON
```

### 2. Деплой

```powershell
wrangler deploy
```

Адрес воркера: `https://blisschat-push.blisschat-relay.workers.dev`.

### 3. Адрес в приложении

Адрес уже вписан в `WORKER_URL` в `app/src/main/java/com/bliss/blisschat/data/push/PushRelay.kt`.
Если воркер будет пересоздан под другим адресом, поменяйте его там и пересоберите приложение.

### 4. Проверка

- `https://blisschat-push.blisschat-relay.workers.dev` в браузере → «blisschat push relay is running».
- Отправьте сообщение с одного телефона на другой при закрытом приложении: придёт уведомление.
- Логи: `wrangler tail`.

## Безопасность

- Воркер читает чат/сообщение/звонок **с токеном отправителя**, так что правила Firestore сами решают,
  можно ли ему слать push по этому чату.
- В push нет текста сообщения (он зашифрован), только «от кого» и «в какой чат».
- Если ключ сервисного аккаунта попал куда-то лишний раз: Firebase Console → Сервисные аккаунты →
  «Управление разрешениями» → «Ключи» → удалить старый, создать новый и снова выполнить шаг 1.
