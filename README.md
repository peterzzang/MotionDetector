# 실시간 움직임 감지 (Android)

카메라로 사람/차량을 실시간으로 감지하고, 화면 상단에 카메라 미리보기 + 감지 박스를,
하단에 감지 기록 리스트를 보여주는 안드로이드 앱 소스입니다.
(이전에 만든 웹 프로토타입과 동일한 UX/로직을 네이티브로 구현했습니다.)

- 카메라: CameraX (Preview + ImageAnalysis)
- 객체 감지: TensorFlow Lite Task Library (`ObjectDetector`) + SSD MobileNet(COCO) 모델
- 감지 대상: 사람(person), 자동차(car), 트럭(truck), 버스(bus), 오토바이(motorcycle), 자전거(bicycle)
- 최소 SDK: Android 7.0 (API 24) 이상

## ⚠️ 빌드 전 꼭 해야 할 일: 모델 파일 추가

용량이 큰 `.tflite` 모델 파일은 소스코드에 포함하지 않았습니다. 아래 중 한 곳에서
**메타데이터가 포함된 SSD MobileNet V1 (COCO) tflite 모델**을 받아서
`app/src/main/assets/model.tflite` 경로에 넣어주세요. (파일명은 정확히 `model.tflite` 이어야 합니다.)

1. TensorFlow 공식 예제 저장소 (권장, 이 코드와 동일한 Task Library 방식 사용):
   `https://github.com/tensorflow/examples` 저장소의
   `lite/examples/object_detection/android/app/src/main/assets/` 폴더에서
   `ssd_mobilenet_v1.tflite` (또는 동일 폴더의 다른 감지 모델)를 내려받아
   `model.tflite` 로 이름을 바꿔 넣으세요.
2. 또는 TensorFlow Hub / TensorFlow Lite 모델 페이지에서
   "메타데이터 포함 object detection COCO 모델(SSD MobileNet V1)"을 검색해 받으세요.

모델에는 COCO 라벨(person, car, truck, bus, motorcycle, bicycle 등)이 메타데이터로
내장되어 있어야 하며, 별도의 labelmap 파일은 필요 없습니다. 모델 파일이 없으면
앱 실행 시 카메라 화면에 안내 메시지가 표시됩니다.

## 프로젝트 열기 / 빌드

1. Android Studio (최신 버전 권장)에서 `File > Open`으로 이 폴더(`MotionDetectorApp`)를 엽니다.
2. Gradle Sync를 진행합니다. (Gradle Wrapper jar가 없으면 Android Studio가
   자동으로 wrapper를 생성하거나 내장 Gradle로 동기화하라는 안내가 뜹니다. 그대로 진행하세요.)
3. 위 안내대로 `app/src/main/assets/model.tflite` 파일을 추가합니다.
4. 실제 기기를 USB로 연결하고 `Run ▶`을 눌러 설치합니다. (카메라 기능은 에뮬레이터보다
   실제 기기에서 테스트하는 것을 권장합니다.)
5. 최초 실행 시 카메라 권한 요청이 뜹니다. 허용해주세요.

## 주요 파일 구조

```
app/src/main/java/com/example/motiondetector/
  MainActivity.kt            # CameraX 설정, 프레임 분석, 감지 로직, 리스트 갱신, 10초 녹화
  DetectionOverlayView.kt    # 미리보기 위에 그려지는 감지 박스/라벨 커스텀 뷰
  DetectionLogAdapter.kt     # 하단 감지 기록 리스트 (RecyclerView) 어댑터
  DetectionRecord.kt         # 감지 기록 데이터 모델 (Room 엔티티 겸용)
  DetectionDao.kt            # Room DAO - 저장/검색/삭제 쿼리
  AppDatabase.kt             # Room 데이터베이스 싱글톤
  Converters.kt              # Room용 DetectionType enum 변환기
  HistoryActivity.kt         # 감지 기록 검색 화면
app/src/main/res/layout/
  activity_main.xml          # 상단 카메라 + 오버레이, 컨트롤 버튼, 하단 리스트
  activity_history.xml       # 검색창 + 유형/기간 필터 + 검색 결과 리스트
  item_detection.xml         # 리스트 한 줄 레이아웃
app/src/main/assets/
  model.tflite                # (직접 추가해야 하는 모델 파일)
```

## 빌드 오류 수정 내역 4: Manifest merger failed (tensorflow-lite-gpu 네임스페이스 충돌)

`org.tensorflow:tensorflow-lite-gpu:2.14.0`과, 그 라이브러리가 함께 끌고 오는
`org.tensorflow:tensorflow-lite-api:2.14.0`이 **둘 다 매니페스트에 같은 네임스페이스
(`org.tensorflow.lite`)를 선언**하고 있어서 나는 오류입니다. 예전에는 경고로만 뜨던
문제였는데, AGP 9부터는 이걸 빌드 실패로 처리합니다. TensorFlow 쪽의 오래된 패키징
문제라 우리 프로젝트 코드로는 고칠 수 없습니다.

- 코드(`MainActivity.kt`)를 보면 **GPU 델리게이트를 전혀 사용하지 않습니다**
  (`BaseOptions.builder().setNumThreads(4)`로 CPU만 씁니다).
- 그래서 가장 간단하고 안전한 해결책으로 **`tensorflow-lite-gpu` 의존성 자체를
  제거**했습니다. `tensorflow-lite-task-vision`만으로도 지금 앱의 기능(사람/차량 감지)은
  전부 그대로 동작합니다.
- 나중에 GPU 가속을 꼭 써야 하면 코드도 GPU 델리게이트를 추가해야 하고, 이때는 이
  네임스페이스 충돌을 다른 방식(중복 전이 의존성 제외 등)으로 풀어야 합니다.

## 빌드 오류 수정 내역 3: "[ksp] unexpected jvm signature V"

이건 **Room 라이브러리 버전이 너무 오래돼서**(2.6.1) 생긴 문제예요. KSP2(최신 Kotlin/KSP 조합이
쓰는 새 분석 엔진)가 `suspend fun ...(): Unit` 형태의 DAO 함수(예: `clearAll()`)를 처리할 때,
Room 2.6.x에 있는 버그 때문에 JVM 시그니처를 잘못 해석해서 예외가 납니다. Kotlin/KSP 버전을
정확히 맞춰도 발생하는, Room 자체의 알려진 호환성 문제입니다.

- **Room**: 2.6.1 → **2.8.1**로 올렸습니다. (Room 라이브러리는 2.7.0부터 이 문제가 고쳐졌습니다.)
- 코드 변경은 없습니다 — `DetectionDao.kt`, `AppDatabase.kt` 등은 그대로 두고 버전만 올리면 됩니다.

## 빌드 오류 수정 내역 2: "Cannot add extension with name 'kotlin'"

AGP 9.0부터는 **Kotlin 지원이 AGP에 내장**되어 있어서, 예전처럼 `org.jetbrains.kotlin.android`
플러그인을 따로 적용하면 안 됩니다. 둘 다 적용하면 AGP가 만든 `kotlin` 확장(extension)에
KGP가 또 같은 이름의 확장을 추가하려다 충돌해서 이 오류가 납니다.

이번 수정에서:
- `build.gradle`(루트)과 `app/build.gradle`에서 **`id 'org.jetbrains.kotlin.android'` 플러그인
  적용을 완전히 제거**했습니다. (KSP 플러그인만 남습니다.)
- `app/build.gradle`의 `android { kotlinOptions { jvmTarget = '17' } }` 블록도 제거했습니다.
  AGP 내장 Kotlin에서는 `compileOptions.targetCompatibility` 값(여기선 17)을 그대로
  따라가므로 따로 지정할 필요가 없습니다.
- Room의 KSP 처리, `androidResources` 등 나머지 설정은 그대로입니다.

이 프로젝트는 순수 Kotlin 코드만 사용하고 별도의 Kotlin 컴파일러 플러그인(예: Compose
컴파일러)을 쓰지 않기 때문에, 위처럼 KGP를 완전히 제거해도 정상 동작합니다.

## 빌드 오류 수정 내역 1: kaptDebugKotlin 실패 / Gradle URL 404

`:app:kaptDebugKotlin` 태스크가 실패하는 오류는 프로젝트에 적어둔 옛날 Gradle/AGP/Kotlin
버전(Gradle 8.4, AGP 8.2.2, Kotlin 1.9.22)이 최신 Android Studio가 실제로 사용하는
Gradle 버전(9.x대)과 맞지 않아서 발생했습니다. 특히 `kapt`(옛 어노테이션 처리 방식)는
최신 Gradle/Kotlin 조합에서 더 이상 안정적으로 동작하지 않습니다.

이번 수정에서 다음을 반영했습니다.

- **AGP**: 8.2.2 → **9.3.0**
- **Kotlin**: 1.9.22 → **2.2.10**
- **Room 어노테이션 처리**: `kapt` → **KSP**(`com.google.devtools.ksp`)로 교체
  (Room 라이브러리 버전 자체는 2.6.1 그대로 유지, KSP도 정상 지원합니다.)
- `aaptOptions` (옛 DSL) → `androidResources` (현재 DSL)로 교체
- Gradle 래퍼 버전을 9.5.0으로 갱신 (AGP 9.3.0의 기본 요구 버전). Gradle 배포판 파일명은
  항상 `X.Y.Z` 전체 버전 형식을 써야 합니다 (예: `gradle-9.5.0-bin.zip`). `9.5-bin.zip`처럼
  패치 번호를 뺀 이름은 존재하지 않아 404가 납니다 — 혹시 직접 버전을 바꾸실 때 참고하세요.

만약 Gradle Sync 시 **"Android Gradle plugin requires a newer/older version of
Gradle"** 같은 메시지가 다시 뜬다면, Android Studio가 제안하는 **AGP Upgrade
Assistant**(Tools 메뉴 또는 알림 배너에 뜨는 링크)를 눌러 자동으로 맞춰주는 것이
가장 확실합니다. 이 경우 Android Studio가 자신이 실제로 사용하는 Gradle 버전에 맞는
AGP 버전을 알아서 추천해줍니다.

## 인식률 개선 (물건을 사람으로 오인식하는 문제)

SSD MobileNet V1은 가볍고 빠른 대신, 사람과 비슷한 형태의 물건(마네킹, 옷걸이, 의자 등)을
가끔 사람으로 잘못 인식할 수 있는 모델입니다. 코드만으로 고칠 수 있는 부분을 아래처럼
개선했습니다.

- **정확도 기준 상향**: `SCORE_THRESHOLD` 0.55 → **0.65**. 확신도가 낮은 애매한 결과는
  걸러냅니다.
- **연속 프레임 확인 추가**: 이제 한 프레임에서 딱 한 번 "사람"으로 잡혔다고 바로
  기록/녹화하지 않습니다. `CONFIRM_HITS`(기본 3프레임, 약 1초) 동안 연속으로 감지돼야
  "진짜" 감지로 인정합니다. 조명 변화나 순간적인 블러로 인한 한 프레임짜리 오탐을
  걸러내는 효과가 큽니다. 사라질 때는 `CONFIRM_MISSES`(기본 2프레임) 동안 연속으로
  안 보여야 "없어짐"으로 처리해서, 감지가 깜빡거리며 기록이 중복 생성되는 것도 막습니다.
  (`MainActivity.kt` 상단 `companion object`에서 두 값 모두 조정 가능합니다.)

이렇게 해도 특정 물건이 계속 "사람"으로 오인식된다면, 근본적으로 **더 정확한 모델로
교체**하는 게 가장 효과적입니다. 코드는 그대로 두고 `model.tflite` 파일만 아래 모델로
바꿔주면 됩니다 (같은 Task Library `ObjectDetector` API를 그대로 씁니다):

- **EfficientDet-Lite0** (SSD MobileNet V1보다 정확도가 꽤 높고, 속도는 약간 느립니다)
  TensorFlow Hub에서 "EfficientDet-Lite0 metadata" 또는
  "efficientdet_lite0.tflite (object detection, metadata)"로 검색해 받으시면 됩니다.
  받은 파일 이름을 `model.tflite`로 바꿔서 기존 파일을 덮어쓰기만 하면 됩니다.
- 오탐이 특정 각도/조명에서 반복된다면, 그 상황을 촬영한 사진을 몇 장 가지고
  있다가 계속 문제가 되는지 확인해보시는 것도 좋습니다 — 완전히 다른 물건이 매번
  다르게 오인식되는지, 특정 물건 하나가 반복해서 문제인지에 따라 해결 방향이 달라집니다.

## 화면 꺼짐 방지

- 카메라를 시작하면(감지 중일 때) 화면이 자동으로 꺼지지 않도록 `FLAG_KEEP_SCREEN_ON`을 켭니다.
- "카메라 정지"를 누르거나 앱을 벗어나면 다시 일반 화면 꺼짐 정책으로 돌아갑니다.

## 감지 기록 검색 (DB 저장)

- 모든 감지 이벤트(사람/차량, 라벨, 정확도, 시각)는 **Room(SQLite) 데이터베이스**에도 함께 저장됩니다.
  앱을 껐다 켜도 기록이 남아있습니다.
- 메인 화면의 "감지 기록" 버튼을 누르면 검색 화면(`HistoryActivity`)이 열립니다.
  - 라벨 검색창: "사람", "자동차", "트럭" 등 키워드로 검색
  - 유형 필터: 전체 / 사람 / 차량
  - 기간 필터: 전체 기간 / 오늘 / 최근 7일
  - "전체 삭제"로 저장된 모든 기록을 지울 수 있습니다 (확인 대화상자 표시).
- 관련 파일: `DetectionRecord.kt`(Room 엔티티), `DetectionDao.kt`, `AppDatabase.kt`,
  `Converters.kt`, `HistoryActivity.kt` + `res/layout/activity_history.xml`.
- Room 어노테이션 처리(`kapt`)가 추가되었으니, 프로젝트를 다시 열면 **Gradle Sync**를 한 번 더 해주세요.

## 동작 방식

- 화면에 사람/차량이 "없다가 나타날 때"만 하단 리스트에 새 기록을 추가합니다
  (같은 대상이 계속 화면에 있으면 중복으로 쌓이지 않습니다).
- 상단 배지에 사람/차량 누적 감지 횟수가 표시됩니다.
- `↺` 버튼으로 전/후면 카메라를 전환할 수 있습니다.
- "지우기" 버튼으로 리스트와 카운트를 초기화할 수 있습니다.
- 감지 정확도 기준은 `MainActivity.kt`의 `SCORE_THRESHOLD` (기본 0.55)에서 조정할 수 있습니다.

## 감지 시 10초 영상 자동 저장

- 하단 컨트롤에 있는 **"감지 시 10초 영상 자동 저장"** 체크박스로 켜고 끌 수 있습니다 (기본값: 켜짐).
- 체크된 상태에서 사람 또는 차량이 새로 감지되면, 그 시점부터 10초짜리 영상을 촬영해 저장합니다.
  - 저장 중에는 카메라 화면 위에 빨간 **REC 0:xx** 배지가 표시됩니다.
  - 녹화 중에는 전/후면 전환 버튼이 잠깐 비활성화됩니다 (녹화가 끊기는 것을 방지).
  - 이미 녹화 중일 때 새로운 감지가 일어나도 중복으로 녹화를 시작하지 않고, 진행 중인 10초 녹화가 끝날 때까지 기다립니다.
- 저장 위치: `MediaStore`를 통해 기기의 **`영화(Movies)/MotionDetector`** 폴더에 `.mp4` 파일로 저장됩니다.
  (Android 10 이상은 앱 갤러리/파일 앱에서 바로 보입니다. Android 9 이하는
  `WRITE_EXTERNAL_STORAGE` 권한이 필요하며, 최초 실행 시 카메라 권한과 함께 요청됩니다.)
- 오디오는 녹음하지 않습니다 (영상만 저장, `RECORD_AUDIO` 권한 불필요).
- 클립 길이는 `MainActivity.kt`의 `CLIP_DURATION_MS` (기본 10000ms)에서 조정할 수 있습니다.

## 참고 / 제한사항

- 프레임 분석 해상도와 미리보기 화면의 비율이 다를 경우, 박스 위치가 약간 어긋날 수 있습니다.
  더 정확한 정합이 필요하면 `PreviewView`의 `scaleType`과 `DetectionOverlayView`의 스케일 계산을
  동일한 기준으로 맞춰 조정하세요.
- 더 빠르고 가벼운 감지가 필요하면 모델을 EfficientDet-Lite0/1 (TFLite, 메타데이터 포함)로
  교체해도 동일한 `ObjectDetector` API로 그대로 동작합니다 (파일명만 `model.tflite`로 유지).
