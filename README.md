# Beacon

Клиент для VLESS Reality, без лишних настроек. Работает на Windows и Android.

<p align="center">
  <img src=".assets/screenshot-off.png" width="480" alt="Beacon — отключено"/>
  &nbsp;&nbsp;
  <img src=".assets/screenshot-on.png" width="480" alt="Beacon — подключено"/>
</p>

<p align="center">
  <img src=".assets/screenshot-keys.png" width="300" alt="Управление ключами"/>
  &nbsp;
  <img src=".assets/screenshot-settings.png" width="300" alt="Настройки"/>
  &nbsp;
  <img src=".assets/screenshot-subscriptions.png" width="300" alt="Подписки"/>
</p>

Есть свой сервер и ключ от него - Beacon подключит. Никаких аккаунтов, облаков и телеметрии.

---

## Что внутри

**Windows** - нативное приложение с анимированным интерфейсом. Два режима:

- `Proxy` - туннель только для браузеров и приложений с поддержкой системного прокси. Права администратора не нужны.
- `TUN` - весь трафик идёт через сервер, включая игры и мессенджеры. Нужен запуск от администратора.

Кнопка `WARP` рядом с режимами - отдельный маршрут для Google и Gemini через Cloudflare WireGuard.

**Android** - использует системный VpnService. Работает в фоне, ключи хранятся в Android Keystore.

---

## Как начать

### Нужен свой сервер

Beacon - клиент, не сервис. Сервер нужно поднять самому: VPS + Xray или sing-box с VLESS Reality. Хороший старт - [официальная документация Xray](https://xtls.github.io) или любой гайд по VLESS Reality selfhosted.

### Скачать

Актуальные сборки в [Releases](https://github.com/vincere-mori/beacon/releases).

| Платформа | Файл |
|-----------|------|
| Windows | `Beacon-Windows-v0.2.0.exe` |
| Android | `Beacon-Android-v0.2.0.apk` |

### Подключение

1. Скопируй ключ `vless://...` из конфига своего сервера.
2. Открой Beacon -> Управление ключами -> вставь ключ.
3. Выбери режим и нажми Подключить.

В TUN-режиме приложение само попросит права администратора.

---

## Безопасность

Ключи хранятся только локально:
- Windows: через DPAPI текущего пользователя.
- Android: через AES-GCM ключ из Android Keystore.

`sing-box.exe` в installer - фиксированная версия, проверяется по SHA-256 при сборке. Произвольный JSON не импортируется, только vless:// ссылки.

Windows installer не подписан сертификатом, SmartScreen при установке может предупредить - это нормально.

---

## Сборка

Нужны JDK 21+ и Android SDK.

Запуск desktop:

```bat
dev\run-desktop-dev.bat
```

Сборка Windows installer:

```bat
dev\build-windows.bat 0.2.0
```

Android APK:

```bat
.\gradlew.bat assembleDebug
```

---

## Структура

```
core       парсинг VLESS-ключей, генерация sing-box config
app        Android: UI + VpnService
desktop    Windows: UI + sing-box, системный прокси
dev        сборка installer, иконки
```

---

## Стек

- Kotlin (JVM + Android)
- Jetpack Compose - Android UI
- Swing + FlatLaf - Windows UI
- [sing-box](https://github.com/SagerNet/sing-box) - ядро туннеля
