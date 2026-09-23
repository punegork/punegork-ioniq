# Ioniq Telemetry Starter — v0.1

Bu proje Ioniq 5 için planlanan telemetri/logger uygulamasının ilk çalışan iskeletidir.

## v0.1'de çalışan parçalar

- Foreground GPS sürüş kaydı
- Başlangıç / bitiş koordinatı
- Rota noktalarını Room veritabanına 1 Hz civarında kaydetme
- Mesafe, toplam süre ve hareket süresi altyapısı
- Trip geçmişi
- A → B rotasını Google Maps'te açma
- HV güç/akım/voltaj için genel telemetri modeli
- Batarya, motor, inverter ve hücre sıcaklık/voltaj alanları
- Enerji integrasyonu: çekilen kWh / regen kWh / net kWh
- Peak güç, akım ve G değerleri
- Gerçek OBD donanımından bağımsız `TelemetrySource` katmanı

## ÖNEMLİ: DEMO OBD

`MockObdTelemetrySource` içindeki OBD değerleri SENTETİKTİR. Sadece UI, veritabanı ve trip pipeline'ını gerçek dongle olmadan test etmek için vardır. GPS verisi cihazın gerçek konum servisinden gelir.

Gerçek araç verisine geçerken yalnızca `TelemetrySource` implementasyonu değiştirilecek. Hyundai/Ioniq PID byte haritaları doğrulanmadan hiçbir özel PID decoder bu projeye sabitlenmemiştir.

## Geliştirme ortamı

- Android Studio: güncel stable
- AGP 9.4.0
- compileSdk 37
- targetSdk 36
- JDK 17
- Compose BOM 2026.09.00
- Room 2.8.5
- Google Play Services Location 21.4.0

## Açma

1. Bu klasörü Android Studio'da `Open` ile aç.
2. Android SDK 37 kurulu değilse SDK Manager'dan yükle.
3. Gradle sync yap.
4. Telefonda konum iznini ver.
5. `Sürüşü başlat` butonuna bas.

Bu arşiv Gradle Wrapper binary'sini içermez. Android Studio kendi Gradle 9.6+ kurulumunu kullanabilir veya projeye wrapper eklenebilir.

## Sonraki teknik adımlar

1. Gerçek Bluetooth OBD transport (ELM327/STN veya seçilecek dongle)
2. Ioniq 5 2026 63 kWh üzerinde PID doğrulama
3. BMS sıcaklık sensörleri ve hücre min/max decoder'ları
4. MCU/inverter sıcaklık ve RPM decoder'ları
5. Telefon IMU fallback + kalibrasyon
6. CAN-FD/ADAS katmanı
7. Trip detail grafikleri ve rota polyline haritası
8. Android Auto Car App ekranı
9. CSV/JSON export

## Veri modeli

`trips` tablosu sürüş özetini tutar. `telemetry_samples` zaman serisini tutar. Bu ayrım sayesinde geçmişte bir sürüşte belirli bir noktadaki hız/güç/akım/sıcaklık verisini gösterebiliriz.

## Android Auto prototype (v0.2-aa)

The project now includes a Car App Library service under `auto/IoniqCarAppService.kt`.
The projected screen shows live speed, HV power/current, SOC, battery temperature and motor/inverter temperature, with a detail screen for G-force, cells, 12 V and ADAS placeholders.

Important: generic vehicle telemetry is not currently a standalone public Android Auto app category. This development build declares the IOT category only as an internal prototype surface. Do not publish it as an IoT app without changing the product to meet that category's requirements.

Android Auto templated apps also cannot be made available on a real head unit merely by enabling Android Auto's "unknown sources" switch. For real-vehicle testing, use a trusted distribution route such as Google Play Internal App Sharing or an Internal Testing track. The Desktop Head Unit can be used for local development/testing.

## APK build

A GitHub Actions workflow is included at `.github/workflows/android-apk.yml`. On push to `main`/`master`, it builds `app-debug.apk` and uploads it as the `IoniqTelemetry-debug-apk` workflow artifact.
