Подпись APK (Ru Store / push смотрят на SHA-256 подписи)

РЕКОМЕНДУЕТСЯ (один отпечаток = как в Play / Ru Store)
  В local.properties задайте ОДИН РАЗ:
    release.store.file=путь\\к\\upload.jks
    release.store.password=...
    release.key.alias=upload   (или ваш alias)
    release.key.password=...
  Тогда и Debug, и Release собираются ЭТИМ ключом — на любом ПК одинаково,
  если файл .jks доступен (общая сетевая папка / копия у каждого разработчика).

  В Ru Store укажите SHA-256 этого upload-ключа (./gradlew :app:signingReport).

ЗАПАСНОЙ вариант (без upload.jks на машине)
  Файл debug-shared.jks в этом каталоге — общий debug для команды.
  SHA-256 см. ниже (добавьте в Ru Store, если не используете release.store.*).

  E3:14:4B:CF:DB:45:39:26:3E:BC:1B:22:EF:41:5E:42:AA:74:81:72:03:BD:A0:81:59:33:3B:8A:F5:97:BD:07

  Пароли: android / alias androiddebugkey / android

Отключить общий debug-shared: local.properties → debug.use.shared.keystore=false
Принудительно debug из shared, даже если задан upload: debug.force.shared.keystore=true

Проверка: ./gradlew :app:signingReport
