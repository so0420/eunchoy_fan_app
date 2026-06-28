# 은초이 모아보기 (Eunchoy Hub)

치지직 스트리머 **은초이**의 모든 소식을 한곳에서 모아 보는 안드로이드 앱입니다.
밝고 깔끔한 파스텔 하늘색 테마로, 방송·게시글·영상을 한눈에 확인하고
원하는 소식은 **알람처럼**(방해금지/무음 모드에서도) 울리게 알림 받을 수 있습니다.

## 모아 보는 소식

| 소스 | 내용 |
| --- | --- |
| 치지직 방송 | 실시간 ON/OFF 상태, 제목·카테고리·시청자 수, **라이브 미리보기(HLS 영상 재생)** |
| 치지직 커뮤니티 | 커뮤니티 게시글 본문·이미지 |
| 치지직 다시보기 | 최근 VOD 목록 |
| 네이버 카페 | 작성 글 목록 + (로그인 시) 본문 |
| X (트위터) | `@Eun_choy` 타임라인(WebView) + 새 글 best-effort 알림 |
| 유튜브 (메인) | [@은초이Eunchoy](https://www.youtube.com/@%EC%9D%80%EC%B4%88%EC%9D%B4Eunchoy) 최신 영상 |
| 유튜브 (다시보기) | [@금초이](https://www.youtube.com/@%EA%B8%88%EC%B4%88%EC%9D%B4) 최신 영상 |

## 핵심 기능: 알람형 알림

- 방송 시작 등 원하는 소식을 **알람처럼** 받을 수 있습니다.
- **방해금지(DND)/무음/진동 모드에서도** 소리가 울리도록 알람 오디오 스트림 + 전체화면 알림을 사용합니다.
- 소스별로 알림 ON/OFF, 그리고 "일반 알림 / 알람형(소리 강제)"을 **개별 선택**할 수 있습니다.

## 기술 스택

- Kotlin + Jetpack Compose (Material 3), 단일 `app` 모듈
- Media3/ExoPlayer (라이브 HLS 미리보기)
- Retrofit + OkHttp + kotlinx.serialization (치지직/네이버/유튜브/X 데이터)
- WorkManager + Foreground Service (백그라운드 폴링·알람 재생)
- DataStore (설정·본 항목 추적), WebView (네이버 로그인 / X 타임라인)

데이터 소스 및 API 조사는 [`docs/research/`](docs/research) 참고.

## 빌드

```bash
./gradlew :app:assembleDebug
# 결과 APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- JDK 17+ 필요 (개발은 JDK 21), Android SDK Platform 35 / Build-Tools 35.
- `local.properties`의 `sdk.dir`로 SDK 경로 지정.

## 배포 (릴리즈)

서명된 릴리즈 APK는 R8로 축소되어 약 3MB입니다 (디버그는 ~23MB).

**서명 키 (필수, 분실 금지)** — `keystore.properties` + `eunchoy-release.keystore`는
gitignore되어 커밋되지 않습니다. 이 두 파일을 잃어버리면 **기존 앱 위에 업데이트를
배포할 수 없습니다**(서명이 달라짐). 반드시 안전한 곳에 백업하세요.

**로컬에서 릴리즈 빌드**
```bash
# gradle.properties 의 APP_VERSION_CODE 를 1씩 올린 뒤
./gradlew :app:assembleRelease
# 결과: app/build/outputs/apk/release/app-release.apk
```

**GitHub Releases 자동 배포** — `v*` 태그를 푸시하면
[`.github/workflows/release.yml`](.github/workflows/release.yml)가 서명 APK를 빌드해
릴리즈에 첨부합니다. 먼저 저장소 Settings → Secrets에 추가:
`KEYSTORE_BASE64`(키스토어 base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
```bash
# 키스토어를 base64로 (KEYSTORE_BASE64 값):
base64 -w0 eunchoy-release.keystore         # Windows Git Bash
# 릴리즈 발행:
git tag v1.0 && git push origin v1.0
```

사용자는 첨부된 APK를 직접 설치하거나, **Obtainium**에 이 저장소 URL을 등록해
새 릴리즈를 자동 업데이트로 받을 수 있습니다.

> 이 앱은 비공식 팬 제작물이며, 각 플랫폼의 비공개 API/RSS를 사용합니다.
> 플랫폼 정책 변경 시 일부 기능이 동작하지 않을 수 있습니다.
> 공개 배포 시 은초이 본인 동의 / 요청 시 내릴 준비 / 비영리를 권장합니다.
