<p align="center">
  <img src="desktop/src/main/resources/icon.png" width="92" alt="Beacon logo">
</p>

<h1 align="center">Beacon</h1>

<p align="center">
  <b>Минималистичный VLESS Reality клиент для Windows, Linux и Android.</b><br>
  Свой сервер, локальные ключи, без аккаунтов, облака и телеметрии.
</p>

<p align="center">
  <a href="https://github.com/vincere-mori/beacon/releases/latest">
    <img src="https://img.shields.io/badge/Download-Beacon-1f8f5f?style=for-the-badge&logo=github&logoColor=white" alt="Download Beacon">
  </a>
</p>

| OS | Download |
| --- | --- |
| Windows | [Latest release](https://github.com/vincere-mori/beacon/releases/latest) |
| Android | [Latest release](https://github.com/vincere-mori/beacon/releases/latest) |
| Linux | [Latest release](https://github.com/vincere-mori/beacon/releases/latest) |

<p align="center">
  <img src=".assets/screenshot-off.png" width="47%" alt="Beacon отключен">
  <img src=".assets/screenshot-on.png" width="47%" alt="Beacon подключен">
</p>

## Что это

Beacon подключает ваш VLESS Reality сервер без сложной настройки. Вставьте `vless://` ключ или ссылку подписки, выберите сервер и включите подключение.

## Возможности

- `Proxy` - системный прокси для браузеров и приложений, которые его поддерживают. Права администратора не нужны.
- `TUN` - весь трафик системы через сервер, включая игры и мессенджеры. На desktop нужен запуск с правами администратора или root.
- `WARP` - отдельный маршрут для Google и Gemini через Cloudflare WireGuard.
- Подписки - импорт списка серверов, проверка задержки и выбор нужного профиля.
- Локальное хранение ключей: DPAPI на Windows, Android Keystore на Android, файл профиля в пользовательском конфиге на Linux.

## Как начать

1. Скачайте сборку для своей платформы из таблицы выше.
2. Подготовьте свой VLESS Reality сервер на Xray или sing-box.
3. Откройте Beacon и добавьте `vless://` ключ или ссылку подписки.
4. Выберите режим `Proxy` или `TUN` и нажмите подключение.

Beacon не продает VPN-доступ и не выдает серверы. Нужен свой сервер или ключ от него.

## Примечания

- Windows installer не подписан сертификатом, поэтому SmartScreen может показать предупреждение.
- В Linux `Proxy` режим настраивает системный прокси через `gsettings`, лучше всего работает в GNOME.
- Произвольный JSON не импортируется, поддерживаются `vless://` ключи и подписки с такими ключами.

## Сборка

Нужны JDK 21+ и Android SDK.

Запуск desktop:

```bat
dev\run-desktop-dev.bat
```

Windows installer:

```bat
dev\build-windows.bat 0.3.2
```

Android APK:

```bat
.\gradlew.bat assembleDebug
```

Linux package:

```bash
dev/package-linux.sh 0.3.2
```

## Стек

- Kotlin JVM + Android
- Jetpack Compose
- Swing + FlatLaf
- [sing-box](https://github.com/SagerNet/sing-box)
