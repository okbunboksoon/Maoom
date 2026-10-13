# MaoomTool 프로젝트 흐름

이 문서는 처음 프로젝트를 보는 사람이 화면, API, 서비스, DB, 파일 처리의
연결 방향을 빠르게 파악하기 위한 안내서다. 세부 구현은 각 컨트롤러와 서비스의
클래스 주석을 따라가면 된다.

이 문서를 읽을 때는 다음 순서로 보면 된다.

1. 사용자가 어느 화면에서 작업을 시작하는지 확인한다.
2. 화면이 호출하는 URL과 Controller를 확인한다.
3. Controller가 넘기는 요청값과 Service의 실제 처리 단계를 확인한다.
4. 결과가 HTTP 응답, 서버 파일, DB 이력 중 어디에 남는지 확인한다.
5. 배포 PC에서 필요한 외부 프로그램, 공유 폴더 권한과 설정값을 확인한다.

경로를 입력받는 기능의 파일 처리는 브라우저를 연 사용자 PC가 아니라 MaoomTool이
실행되는 서버 PC에서 수행된다. 예를 들어 사용자가 `H:\project`를 입력하면 서버
프로세스가 그 경로를 읽는다. 따라서 같은 드라이브 문자가 사용자 PC에는 있어도 서버
PC에 없으면 작업할 수 없다.

## 전체 구조

```text
브라우저 화면
  -> Thymeleaf template / static JavaScript
  -> Controller
  -> Service
  -> Mapper / JdbcTemplate / 파일 처리 / XSL, Excel 처리
  -> DB 또는 결과 파일
```

- `src/main/resources/templates`  
  사용자가 보는 HTML 화면이다. 버튼 클릭, 팝업, 테이블 화면의 뼈대가 있다.
- `src/main/resources/static`  
  화면별 CSS와 JavaScript가 있다. 관리자 화면은 SB Admin 2와 DataTables를 쓴다.
- `src/main/java/maoomWeb/ire/user/controller`  
  일반 사용자 화면/API 진입점이다. 요청 값을 검증하고 서비스에 넘긴다.
- `src/main/java/maoomWeb/ire/user/service`  
  실제 업무 로직이 모여 있다. 파일 변환, 엑셀 생성, PDF 댓글, 실행 로그 기록 등이
  이 계층에서 처리된다.
- `src/main/java/maoomWeb/ire/user/mapper`  
  MyBatis XML mapper와 연결되는 DB 접근 인터페이스다.
- `src/main/java/maoomWeb/ire/admin/controller`  
  관리자 화면에서 호출하는 REST API 진입점이다.
- `src/main/java/maoomWeb/ire/admin/service`
  관리자 화면에서 쓰는 DB 조회/수정 서비스와 DTO가 있다.
- `src/main/java/com/sts/dita`
  DITA Schematron 검사 엔진, 검사 진행률 통지, HTML 보고서 생성 로직이 있다.
- `src/main/resources/mapper`  
  MyBatis SQL XML이다. DB 테이블과 Java DTO 필드 연결이 여기에 있다.
- `src/main/resources/xsl`, `src/main/resources/revision-tool`  
  DITA/XML 변환 파이프라인에서 사용하는 XSL, DTD, 기준 파일 자원이다.
- `schematron`
  운영 중 추가·교체하는 `.sch` 규칙 파일 폴더다. 배포 환경에서는 설정 파일로 실제
  위치를 지정한다.

## 로그인과 사용자 정보 흐름

```text
index.html
  -> UserController.login()
  -> UserService.checkLogin()
  -> UserMapper.getUserInfoById()
  -> tb_user
```

`UserService`는 기존 평문 비밀번호가 맞으면 BCrypt 해시로 자동 마이그레이션한다.
사용자 정보 API, 프로필 이미지 API, 계정 수정 API도 `UserController`에서 시작한다.

주요 URL과 역할은 다음과 같다.

| URL | 방식 | 역할 |
|---|---|---|
| `/` | GET | 로그인 화면 표시 |
| `/login` | POST | 아이디·비밀번호 확인 후 로그인 세션 생성 |
| `/api/user/me` | GET | 현재 로그인 사용자 정보 조회 |
| `/api/user/mention-list` | GET | 댓글에서 멘션할 사용자 목록 조회 |
| `/api/user/account` | POST | 본인 계정 정보 변경 |
| `/api/user/profile-image` | POST/GET/DELETE | 프로필 이미지 등록·조회·삭제 |

로그인이 필요한 화면은 세션의 사용자 정보를 기준으로 동작한다. 실행 이력을 남기는
기능은 이 사용자 ID를 실행자로 기록한다. 프로필 이미지 파일은
`app.user.profile-upload-dir`에 저장되므로 DB만 복사해서 서버를 옮기면 이미지가
누락될 수 있다.

## 메인 화면 이동 흐름

```text
UserController
  -> /main
  -> /pdf/list, /revision/list, /multilingual/list, /qsg/list
  -> 각 업무 화면 template
```

대부분의 업무 화면은 HTML에서 입력값을 받고, 별도 REST API를 호출해서 실제 작업을
시작한다. 작업 결과는 파일 다운로드 응답 또는 JSON 결과로 돌아온다.

화면 이동 URL은 작업을 실행하는 API와 구분한다. 예를 들어 `/pdf/ber`는 HTML을
보여주고 `/api/ber/apply`가 실제 BER 작업을 실행한다. 화면이 열린다는 사실만으로
파일 처리 서비스가 정상이라는 뜻은 아니다. 기능 확인 시에는 브라우저 Network 탭의
API 상태 코드, 서버 로그, 결과 파일과 DB 이력을 함께 확인한다.

## Google Drive와 PDF 목록 흐름

```text
PDF 목록 화면
  -> DriveController / PdfController
  -> Google Drive API 또는 PDF DB
  -> 폴더·PDF 목록 / PDF 스트리밍 / 다운로드 / 즐겨찾기
```

- `/api/drive/items`, `/files`, `/folders`: Google Drive의 폴더와 파일을 조회한다.
- `/api/drive/pdf`, `/pdf/download`: 선택한 PDF를 브라우저에 표시하거나 내려받는다.
- `/api/drive/path`: Drive 항목의 경로 정보를 조회한다.
- `/api/pdf/find-by-drive-file-id`: Drive 파일 ID로 등록된 PDF 정보를 찾는다.
- `/api/pdf/favorites`, `/api/pdf/favorites/toggle`: 로그인 사용자별 PDF·폴더 즐겨찾기를
  조회하거나 설정/해제한다.
- `/google/connect`: 서버의 Google Drive 인증 연결을 시작한다.

Google 인증 정보는 소스에 넣지 않고 `app.google-drive.credentials-path`로 지정한다.
서버를 옮길 때는 인증 파일, 해당 파일의 읽기 권한, Google API 연결 상태를 함께
확인해야 한다.

## 제품사양서 비교 흐름

```text
productSpecComparison.html
  -> ProductSpecComparisonController.run()
  -> ProductSpecComparisonService
  -> resources/xsl/Convert_Xml_To_Excel_comparison.vbs 등 변환 자원
  -> 결과 엑셀 / 실행 로그
```

실행 시작/성공/실패 이력은 `ProjectExecutionLogService`가 `tb_project_execution_log`에
남긴다. 관리자 화면의 실행 로그 탭은 이 테이블을 다시 읽는다.

- 화면 URL: `/pdf/product-spec-comparison`
- 실행 API: `POST /api/product-spec-comparison/run`
- 결과 API: `GET /api/product-spec-comparison/download`
- 입력: 비교할 제품사양서 작업 경로와 화면 옵션
- 처리: 입력 경로 검증 → 변환 자원 실행 → 비교 결과 생성 → 다운로드용 결과 등록
- 결과: 실행 응답에 포함된 결과 식별자로 엑셀을 내려받는다.

실패 시에는 입력 파일 존재 여부뿐 아니라 Excel/VBS 실행 가능 여부, 결과 폴더 쓰기
권한, 변환 자원 배포 여부를 확인한다. 서비스가 시작한 실행 로그는 예외가 발생해도
실패 상태와 메시지로 종료되어야 한다.

## Index 추출 흐름

```text
index.html
  -> IndexExtractController.run()
  -> IndexExtractService
  -> resources/bat/07_make-excel-index.bat
  -> 날짜별 Index 결과 폴더 / 결과 엑셀 / index-extract.log / 실행 로그
```

사용자는 DITA 작업 폴더를 입력하고, 서버 PC는 해당 경로 아래에서 Index 검토용 엑셀을
생성한다. 입력 경로는 브라우저 PC가 아니라 서버 PC 기준으로 접근 가능해야 한다.

- 화면 URL: `/index-extract`
- 실행 API: `POST /api/index-extract/run`
- 입력: 서버가 접근할 수 있는 DITA 작업 폴더
- 처리: 경로 검증 → 배치 실행 → 날짜별 출력 폴더 생성 → 로그와 엑셀 생성
- 확인 파일: 결과 엑셀과 `index-extract.log`

배치 프로세스의 종료 코드가 0이 아니거나 결과 파일이 없으면 성공으로 처리하면 안 된다.
오래 걸리는 경우 Java 로그뿐 아니라 실행 폴더의 `index-extract.log`, Windows의 배치
프로세스와 대상 폴더 파일 변경 시각도 같이 본다.

## 자동 카운팅 흐름

```text
autoCounting.html
  -> AutoCountingController
  -> AutoCountingService
  -> PDF 목차/본문 분석 또는 초도 견적 엑셀 템플릿 작성
  -> JSON 분석 결과 / xlsx 다운로드
```

업로드 PDF는 서버 메모리에서 분석한다. 카운팅 모드와 32페이지 제본 옵션에 따라
집계 규칙이 달라지며, 초도 견적 엑셀 생성은 화면에서 전달한 필드와 프로젝트 내부
템플릿을 함께 사용한다.

| API | 입력 | 결과 |
|---|---|---|
| `POST /api/auto-counting/analyze` | PDF, `mode`, `pack32` | 페이지 분석 및 집계 JSON |
| `POST /api/auto-counting/first-edition/workbook` | PDF와 견적 입력 필드 | 초도 견적 XLSX |

PDF가 비어 있거나 지원하지 않는 형식이면 분석 전에 거부한다. `pack32`는 단순 화면
표시값이 아니라 페이지 묶음 계산에 영향을 주므로 화면과 서비스가 같은 boolean 값을
사용하는지 확인한다. 엑셀 다운로드 문제는 응답의 Content-Type, Content-Disposition과
실제 파일 크기를 함께 확인한다.

## 자동 고지문 생성 흐름

```text
automaticNotice.html
  -> AutomaticNoticeController
  -> AutomaticNoticeService
  -> AutomaticNoticeGen 모듈 / 고지문 규칙 DB
  -> 생성 결과 파일
```

고지문 규칙은 관리자 화면에서 `AdminAutomaticNoticeRuleController`와
`AutomaticNoticeRuleAdminService`를 통해 조회·수정하거나 엑셀로 일괄 등록한다.

- 화면 URL: `/pdf/automatic-notice`
- 실행 API: `POST /api/automatic-notice/run`
- 요청 방식: `multipart/form-data`
- 입력: 결과를 저장할 `inputPath`와 기준 PDF 파일
- 처리: PDF 내용 추출 → 지역/조건 판별 → 관리자 규칙 조회 → 고지문 파일 생성
- 결과: 생성 성공 여부와 결과 파일 정보를 담은 JSON

규칙을 변경하면 이후 실행부터 적용된다. 결과가 예상과 다를 때는 PDF 추출값, 선택된
지역, 규칙의 match type/key와 우선순위를 순서대로 확인한다. 서버 이전 시에는 규칙 DB와
자동 고지문 모듈/템플릿 자원이 모두 있어야 한다.

## 견적 흐름

```text
colorCheck.html
  -> ColorCheckController
  -> ColorCheckWorkflowService
  -> ColorCheckExportService / ColorCheckFinalWorkbookService
  -> DrawingColorCheckService
  -> DrawingColorCheckMapper
  -> tb_drawing_color_check
```

관리자 화면의 견적 탭은 같은 DB를 관리용으로 조회/수정한다. 사용자용 API와 관리자용
API는 URL만 다르고 핵심 DB 서비스는 공유한다.

| API | 설명 |
|---|---|
| `GET /api/pdf/color-check/items` | 사용자 화면의 기준 항목 조회 |
| `PUT /api/pdf/color-check/items` | 화면에서 수정한 기준값 반영 |
| `POST /api/pdf/color-check/import` | 일반 엑셀 업로드, DB 반영 여부 선택 |
| `POST /api/pdf/color-check/domestic-import` | 국내용 summary/manual 엑셀 조합 처리 |
| `POST /api/pdf/color-check/excel` | 업로드 PDF를 분석해 결과 엑셀 생성 |

`updateDatabase=false`이면 결과 엑셀만 생성하고 기준 DB는 바꾸지 않는다. 업로드 파일
형식과 필수 열을 먼저 검증한 후 행 단위로 처리하며, 생성된 파일은 HTTP 응답과
`color-check.output-dir`의 서버 파일을 모두 확인한다.

## 도안의뢰서 작성 흐름

```text
artworkRequest.html
  -> ArtworkRequestController.run()
  -> ArtworkRequestService
  -> PDFImageExtractor 계열 서비스
  -> 도안의뢰서 엑셀 / 실행 중 임시 PDF 정리
```

화면은 결과 저장 경로와 업로드 PDF를 `multipart/form-data`로 보낸다. 컨트롤러는
HTTP 요청 형식만 처리하고, PDF 검증과 도안의뢰서 엑셀 생성은 서비스 계층에서
처리한다.

- 화면 URL: `/pdf/artwork-request`
- 실행 API: `POST /api/artwork-request/run`
- 입력: 서버 결과 경로 `inputPath`, PDF 한 파일
- 처리: PDF 유효성 검사 → 필요한 이미지/페이지 추출 → 엑셀 템플릿 작성 → 임시 파일 정리
- 결과: 생성 결과와 저장 경로를 담은 JSON

작업 중 만든 임시 PDF와 이미지가 성공·실패 양쪽에서 정리되는지 확인한다. 결과 경로가
네트워크 공유라면 읽기뿐 아니라 생성·덮어쓰기·삭제 권한까지 필요하다.

## 인쇄데이터 검증 흐름

```text
printCheck.html
  -> PdfCheckScanViewerController.run()
  -> PdfCheckScanViewerService
  -> pdf-check-scan-viewer.exe-path에 설정된 로컬 CLI
  -> 결과 xlsx / 실행 로그
```

이 기능은 서버 PC에서 외부 CLI 실행 파일을 호출한다. 운영 설정의
`pdf-check-scan-viewer.exe-path`가 실제 EXE 위치를 가리켜야 하며, 대상 폴더와 결과
폴더도 서버 PC 기준으로 접근 가능해야 한다.

- 화면 URL: `/pdf/print-check`
- 실행 API: `POST /api/pdf-check-scan-viewer/run`
- 요청 형식: JSON
- 입력: 검사 대상 폴더와 결과 저장 폴더
- 외부 의존성: 검사 EXE와 `pdf-check-scan-viewer.match-table-path`의 매칭 테이블
- 결과: XLSX와 일반 프로젝트 실행 로그

CLI 프로세스 시작 실패, 시간 초과, 비정상 종료 코드, 결과 XLSX 미생성을 서로 다른
실패로 기록한다. 서버에서 수동으로 같은 EXE와 입력 경로를 실행해 보는 것이 환경 문제와
Java 연동 문제를 구분하는 가장 빠른 방법이다.

## BER/QSG/다국어/Revision 흐름

```text
각 업무 화면
  -> BerController / QsgController / MultilingualController / RevisionController
  -> 각 Service
  -> resources/xsl 또는 revision-tool 자원
  -> 결과 파일 / 실행 로그
```

이 계열은 파일 경로를 입력받아 서버 PC에서 접근 가능한 경로를 처리한다. 배포 PC에서
오류가 날 때는 Excel 프로세스, 파일 권한, 네트워크 드라이브 접근 권한, XSL/VBS 자원
복사 여부를 같이 확인해야 한다.

| 기능 | 화면/실행 API | 핵심 처리 |
|---|---|---|
| BER | `/pdf/ber` → `POST /api/ber/apply` | 입력 폴더를 작업 폴더로 복사한 뒤 BER 기준 적용 |
| QSG | `/qsg/list` → `POST /api/qsg/run` | QSG 기준 DB와 입력 파일을 사용해 결과 생성 |
| 다국어 | `/multilingual/list` → `POST /api/multilingual/run` | 다국어 입력 파일과 XSL 자원으로 변환 |
| Revision | `/revision/list` → `GET /api/revision/options`, `POST /api/revision/run` | 선택한 파이프라인과 DB 반영 옵션 실행 |

BER 화면의 입력 경로 팝업은 원본 폴더를 작업 위치로 복사한 뒤 작업을 시작한다. 원본과
출력 위치를 명확히 구분하고, 실행 전에 서버가 원본을 읽고 대상 폴더를 만들 수 있는지
확인한다. QSG/다국어/Revision도 작업 완료 응답만 보지 말고 생성 파일과 실행 로그의
성공 상태를 함께 확인한다.

Revision은 화면에서 입력/출력 형식과 정제 옵션을 선택한다. 옵션 검증과 배치 조합은
`RevisionPipelineCatalog`, 작업 폴더 준비와 배치 실행은 `RevisionPipelineService`가
담당한다. NOTE/TEXT/BER DB 반영 옵션은 실행 시점의 관리자 기준 DB를 XML 자원으로
내보내 정제 파이프라인에 전달한다. BER 반영 결과에는 변경 리포트와 함께 실제 적용
지역의 AS-IS/TO-BE XML 한 개도 복사한다(EU는 `asis-tobe_eu.xml`, EU_RG는
`asis-tobe_eu_rg.xml`, US/CA/MX는 `asis-tobe_us.xml`). 원본과 출력 경로가 겹치면
실행하지 않는다.

## DITAMAP Builder 흐름

```text
ditamapBuilder.html
  -> DitamapBuilderController
  -> DitamapBuilderService
  -> 허용 루트 아래 DITAMAP/DITA 파일 읽기
  -> 트리 조회 / 비교 / 법규 편집 / 저장
```

DITAMAP Builder는 `ditamap.builder.allowed-roots`에 등록된 작업 루트 아래 파일만
읽고 쓴다. 메인 카드의 시작 화면은 작업 경로를 받고, 세부 화면은 아래처럼 나뉜다.

- `/ditamap-builder/view`: 작업 경로에서 읽은 DITAMAP 트리 조회 결과 화면.
- `/ditamap-builder/diff`: 법규 마스터와 실제 매뉴얼을 비교하는 화면.
- `/ditamap-builder/realtime`: DB 대상 파일명을 기준으로 법규 반영 상태를 확인하고 저장하는 화면.
- `/ditamap-builder/legal-editor`: 선택한 기준 topic을 최종 법규 DITAMAP 구조에 배치하는 팝업.
- `/ditamap-builder/otherprops-report`: otherprops 비교 실패/보류 상세를 확인하는 보조 팝업.

`/ditamap-builder-test` 계열 화면은 운영 화면과 분리해서 변경 사항을 검증하는 테스트
템플릿이다. 실제 API는 운영 Builder와 같은 `DitamapBuilderController`와
`DitamapBuilderService`를 기준으로 확인한다.

주요 API는 역할에 따라 다음처럼 묶인다.

| 구분 | API | 설명 |
|---|---|---|
| 구조 읽기 | `/api/ditamap-builder/tree`, `/tree-summary` | DITAMAP 계층과 요약 정보 조회 |
| 기준 읽기 | `/legal-template`, `/legal-master` | 법규 템플릿과 마스터 구조 조회 |
| 대상 찾기 | `/legal-target-files`, `/dita-files`, `/legal-targets` | 작업 폴더의 후보 파일 및 법규 대상 조회 |
| 비교 | `/cross-check` | 마스터와 실제 매뉴얼의 구조·속성 비교 |
| 편집 정보 | `/topic-title`, `/attributes` | 선택 topic의 제목과 속성 조회 |
| 저장 | `/legal` | 편집한 법규 구조를 실제 파일에 반영 |
| 보조 기능 | `/open-folder` | 서버 PC에서 허용된 결과 폴더 열기 |

허용 루트 검사는 문자열 접두사만 비교하면 우회될 수 있으므로 정규화한 실제 경로가
등록 루트 내부인지 확인한다. 저장 기능은 원본 파일을 바꾸므로 읽기 화면보다 더 엄격한
경로 검증과 쓰기 권한이 필요하다. 운영 변경 전에는 `/ditamap-builder-test` 화면과
`DitamapBuilderServiceTest`로 비교·저장 결과를 확인한다.

## DITA Schematron 검사 흐름

```text
UserMain의 DITA Validation Check 아이콘
  -> 입력 경로 선택 화면
  -> DitaValidationController
  -> DITAMAP·검사 범위·검사 유형 선택
  -> DitaValidationService
  -> SchematronValidationService / com.sts.dita 검사 엔진
  -> HTML 결과 보고서 생성
  -> Schematron 전용 실행 이력 저장
  -> 결과 보기
```

검사 화면에서 사용하는 주요 API는 다음과 같다.

- `/api/dita-validation/maps`: 입력 폴더에 있는 DITAMAP 목록을 찾는다.
- `/api/dita-validation/tree`: 선택한 DITAMAP의 계층 구조를 읽는다.
- `/api/dita-validation/schematrons`: 사용 가능한 원고·퍼블리싱 규칙과 선택 버전을
  조회한다.
- `/api/dita-validation/run`: 검사 요청을 실행하고 HTML 보고서와 이력을 만든다.
- `/api/dita-validation/progress/{token}`: 현재 처리 파일 수와 진행 단계를 조회한다.
- `/api/dita-validation/reports/{id}`: 서버에 저장된 결과 HTML을 연다.

검사 범위는 `ditamap 전체 검사`, `선택한 항목`, `선택한 항목과 모든 자손`으로
구분한다. 전체 검사는 트리 선택이 필요 없고, 선택 범위는 적어도 한 항목을 선택해야
한다. `ditamap 자체 검사` 옵션을 켜면 topic 파일뿐 아니라 DITAMAP 파일도 검사 대상에
포함한다. 선택 항목과 모든 자손을 고르면 화면에서도 자손 체크 상태를 같이 표시한다.

검사 유형은 파일명을 고정하지 않고 다음 접두사로 분류한다.

- 원고: `DOCS_CHECK_*.sch`
- 퍼블리싱: `PUB_CHECK_*.sch`

같은 유형의 파일이 여러 개면 `v1.0.10`, `v1.1.1`, `v1.2.5` 같은 숫자 버전을
구성 요소별로 비교하여 가장 높은 버전을 사용한다. 문자열 사전순 비교를 사용하면
`v1.0.10`을 잘못 고를 수 있으므로 버전 파싱 로직을 거친다. 검사 시작 시 선택한 규칙
파일의 스냅샷과 해시를 남겨, 실행 도중 운영 파일이 바뀌어도 한 번의 검사가 동일한
규칙으로 완료되게 한다.

진행 상태는 서버 메모리에 보관하고 화면이 토큰으로 주기적으로 조회한다. `run` 요청이
브라우저 Network 탭에서 `Pending`이면 서버가 검사 요청을 처리 중인 상태이며, 진행률
API의 처리 파일 수와 서버 로그로 실제 진행 여부를 함께 확인할 수 있다. 서버를
재시작하면 메모리의 진행 상태는 사라지지만 완료된 DB 이력과 HTML 보고서는 유지된다.

결과 HTML에는 검사 DITAMAP, 사용한 Schematron, 검사 범위와 오류 목록을 표시한다.
오류 목록의 헤더는 가운데 정렬하고, 본문의 순번·파일명·행·심각도는 가운데 정렬한다.
사용자가 확인 체크를 해제하면 해당 항목의 수정 시각도 제거한다. 검사 완료 뒤 화면의
`결과 보기` 버튼을 사용자가 눌러 새 창을 열기 때문에 미리 만든 `about:blank` 창이나
팝업 차단 문제가 발생하지 않는다.

검사 결과 한 건에는 최소한 대상 파일, 행 번호, 규칙 ID, `role` 심각도, XPath 위치와
메시지가 들어간다. 규칙 파일의 `<sch:assert>`와 `<sch:report>` 모두 검사 결과를 만들 수
있으므로 새 규칙을 추가할 때 `role`도 같이 지정한다. 현재 사용하는 심각도 값은
`none`, `Critical`, `Major`, `Minor`, `Preference`이며, 알 수 없는 값은 임의로 오류로
올리지 않고 원본 값 또는 미지정 상태로 취급한다.

HTML의 확인 상태와 수정 시각은 서버에 저장한다. 보고서의 상태 변경 UI를 수정할 때는
체크 → 수정 시각 생성, 체크 해제 → 수정 시각 제거, 보고서 재접속 → 저장 상태 복원의
세 경우를 모두 확인한다. 예전에 제공하던 상태 JSON 수동 저장/불러오기 버튼은 서버
저장으로 대체되었으므로 다시 노출하지 않는다.

Schematron 실행 이력은 일반 `tb_project_execution_log`에 넣지 않고 전용 이력에
저장한다. 관리 화면의 `/admin/schematron-history`에서 순번, 상태, 사용자, 시작 시각,
소요 시간, 입력 조건, 결과물을 확인한다. 입력 조건에는 DITAMAP명, 실제 사용 규칙명,
검사 범위와 선택 개수를 남기고 결과물 링크를 누르면 저장된 HTML을 연다.

## PDF 댓글 흐름

```text
pdfview.html
  -> CommentController
  -> CommentService / CommentAttachmentService
  -> CommentMapper
  -> 댓글, 첨부, 답글 테이블
```

PDF 화면의 실시간 협업은 `PdfCollaborationHandler` 웹소켓 핸들러가 담당한다.
DB 저장은 REST API, 사용자 간 화면 동기화는 웹소켓으로 나뉜다.

댓글 API는 목록·등록·수정·삭제 외에도 첨부파일 업로드/조회, 답글, 상태 변경,
좌표(geometry) 저장과 내보내기를 처리한다. 첨부파일 실물은
`app.comment.upload-dir`, 댓글과 상태 정보는 DB에 저장한다. 웹소켓 접속 허용 출처는
`app.websocket.allowed-origins`로 제한한다.

| 구분 | API |
|---|---|
| 댓글 | `/api/comment/list`, `/add`, `/update`, `/delete` |
| 위치·상태 | `/api/comment/geometry`, `/status` |
| 답글 | `/api/comment/reply/list`, `/add`, `/update`, `/delete` |
| 첨부 | `/api/comment/attachment/upload`, `/list`, `/download`, `/view` |
| 내보내기 | `/api/comment/export`, `/api/comment/pdf/export` |

REST 저장이 성공하면 웹소켓으로 같은 PDF를 보고 있는 사용자에게 변경 사실을 전달한다.
웹소켓 메시지만 도착하고 DB 저장이 실패하면 새로고침 후 내용이 사라지므로 REST 응답을
먼저 성공 처리한 뒤 알림을 보내야 한다. 첨부파일 다운로드·보기에서는 DB의 파일 정보와
실제 저장 파일을 모두 확인하고 임의 경로를 직접 받지 않는다.

PDF 목록의 사용자별 즐겨찾기는 `PdfFavoriteService`와 `PdfFavoriteMapper`가 처리한다.
즐겨찾기 대상은 PDF와 폴더를 구분해 저장하며, 같은 항목을 다시 선택하면 해제된다.

## 사용자 가이드 흐름

```text
/guide 또는 /guide/{guideKey}
  -> GuideController
  -> 가이드 목록 또는 선택한 기능의 안내 화면
```

가이드 키는 화면과 컨트롤러가 알고 있는 기능 식별자를 사용한다. 새 기능을 추가할 때는
메인 화면 연결뿐 아니라 가이드 키, 제목, 설명 및 필요한 정적 자원도 함께 확인한다.

## 관리자 화면 흐름

```text
adminMain.html
  -> /admin/color-check/items
  -> /admin/project-logs
  -> /admin/users
  -> /admin/automatic-notice-rules/items
  -> /admin/ber-asis-tobe/items
  -> /admin/project-note-db/items
  -> /admin/project-text-db/items
  -> /admin/qsg-db/items
  -> /admin/replace-dark-symbol/items
  -> /admin/schematron-history
```

관리자 화면은 한 HTML 안에서 탭을 바꿔가며 DataTables를 초기화한다. 탭별 데이터는
처음 열 때 API로 로드하고, 테이블 검색/정렬/페이지네이션은 DataTables가 처리한다.

- 견적 탭: `AdminColorCheckController`
- 실행 로그 탭: `AdminProjectExecutionLogController`
- 사용자 탭: `AdminUserController`
- 자동 고지문 규칙 탭: `AdminAutomaticNoticeRuleController`
- BER AS-IS/TO-BE 탭: `AdminBerAsisTobeController`
- NOTE 기준 DB 탭: `AdminNoteDbController`
- TEXT 기준 DB 탭: `AdminProjectDbController`
- QSG 기준 DB 탭: `AdminQsgDbController`
- Replace Dark Symbol 탭: `AdminReplaceDarkSymbolController`
- Schematron 실행 이력 탭: `AdminSchematronHistoryController`

기준 DB 편집 API는 조회를 제외한 변경 작업에 `ADMIN` 또는 `DB_EDITOR` 권한을
요구한다. 엑셀/XML 가져오기는 서비스에서 행별 값을 검증하고, 지역과 hash 조합을
기준으로 신규 등록 또는 갱신한다.

Replace Dark Symbol은 항목 조회·수정·삭제와 엑셀 내보내기를 지원한다. Schematron
이력은 검사 결과를 조회하고 HTML 보고서를 여는 용도이며, 일반 프로젝트 실행 로그와
분리한다. 새 관리자 탭을 만들 때는 컨트롤러·서비스·DTO/Mapper뿐 아니라
`adminMain.html`의 탭, DataTables 초기화, 권한에 따른 버튼 표시도 함께 연결해야 한다.

관리자 API의 변경 범위는 다음과 같다.

| 관리 항목 | 조회 | 변경 및 부가 기능 |
|---|---|---|
| 사용자 | `/admin/users` | 등록, 권한/상태 수정, 삭제 |
| 실행 로그 | `/admin/project-logs` | 일반 작업 실행 결과 조회 |
| 견적/Color Check | `/admin/color-check/items` | 수정, 엑셀 import/export, 삭제 |
| 자동 고지문 | `/admin/automatic-notice-rules/items` | 수정, import/export, 단건·전체 삭제 |
| BER AS-IS/TO-BE | `/admin/ber-asis-tobe/items` | 수정, 문장/엑셀 import, export, 삭제 |
| NOTE DB | `/admin/project-note-db/items` | 수정, import/export, 삭제 |
| TEXT DB | `/admin/project-text-db/items` | 수정, import/export, 삭제 |
| QSG DB | `/admin/qsg-db/items` | import/export |
| Replace Dark Symbol | `/admin/replace-dark-symbol/items` | 수정, export, 삭제 |
| Schematron 이력 | `/admin/schematron-history` | 전용 실행 이력과 결과 HTML 조회 |

관리자 DataTables를 수정할 때는 열 이름과 JSON 필드, 가운데/왼쪽 정렬, 정렬 아이콘,
빈 결과 표시와 권한별 버튼 노출을 같이 확인한다. Import는 HTTP 성공만 확인하지 말고
신규·갱신·실패 건수와 실패 행 사유를 확인하고, Export는 현재 DB 내용을 다시 열 수
있는 형식으로 생성되는지 검증한다.

## 실행 결과와 이력 구분

기능마다 결과가 남는 위치가 다르다. 문제를 추적할 때 아래 세 종류를 혼동하지 않는다.

| 종류 | 저장 위치 | 대상 |
|---|---|---|
| 일반 실행 이력 | `tb_project_execution_log` | 제품사양서, Index, 인쇄 검증 등 일반 작업 |
| Schematron 전용 이력 | Schematron 이력 DB와 `/admin/schematron-history` | DITA Validation Check |
| 결과 파일 | 기능별 출력 폴더 또는 사용자가 입력한 서버 경로 | XLSX, XML, HTML 등 실물 결과 |

일반 이력은 시작 시 먼저 행을 만들고 성공 또는 실패로 종료한다. 실패할 때도 소요 시간과
사용자, 입력 경로, 오류 요약이 남아야 한다. Schematron은 규칙 파일명과 검사 범위를
추가로 기록하고 HTML 실물 경로가 이력과 연결되어야 한다. DB 행만 있고 파일이 삭제된
경우에는 결과 링크가 정상적으로 실패 안내를 보여야 한다.

## 서버 경로 설정 방법

배포 후 서버 경로, DB, 업로드 폴더 같은 운영 설정은 소스의
`src/main/resources/application.properties`를 직접 고치지 않고, 실행 JAR 옆에
`config/application-local.properties` 파일을 만들어 관리한다.

```text
MaoomTool.jar
config/
  application-local.properties
```

애플리케이션은 시작할 때 다음 설정 때문에 이 파일을 자동으로 읽는다.

```properties
spring.config.import=optional:file:./config/application-local.properties
```

### G서버/V서버/H서버 경로 개념

사용자가 화면에 입력하는 `V서버`, `H서버`, `G서버` 경로는 사용자 PC 기준이 아니라
서버 PC 기준 경로다. 즉 브라우저를 다른 컴퓨터에서 열어도 실제 파일 접근은 배포용
PC에서 일어난다.

따라서 서버를 추가할 때는 먼저 배포용 PC에서 해당 경로가 열리는지 확인한다.

```powershell
Test-Path "\\fileserver\share"
```

`False`가 나오면 프로그램 설정 문제가 아니라 배포용 PC의 네트워크 연결, 공유 권한,
실행 계정 권한 문제다. Windows 서비스로 실행 중이면 로그인 사용자 권한과 서비스 실행
계정 권한이 다를 수 있으므로 서비스 계정에서도 같은 공유 폴더를 읽고 쓸 수 있어야 한다.

### G서버 같은 작업 루트 추가/삭제

DITAMAP Builder처럼 허용된 루트 아래 파일만 읽고 쓰는 기능은
`ditamap.builder.allowed-roots`에 서버 루트를 세미콜론(`;`)으로 구분해 등록한다.

예시:

```properties
ditamap.builder.allowed-roots=\\172.16.10.15\QC_Docs;\\172.16.10.15\kia_om26;\\g-server\project
```

G서버를 추가하려면 마지막에 `;\\g-server\project`를 붙인다.

```properties
ditamap.builder.allowed-roots=\\172.16.10.15\QC_Docs;\\172.16.10.15\kia_om26;\\g-server\project
```

G서버를 빼려면 해당 항목만 지운다.

```properties
ditamap.builder.allowed-roots=\\172.16.10.15\QC_Docs;\\172.16.10.15\kia_om26
```

수정 후에는 서버를 재시작해야 반영된다.

### 자주 쓰는 운영 설정

```properties
# 접속 포트. 생략하면 Spring Boot 기본값 8080을 사용한다.
server.port=8080

# DB 접속 정보.
spring.datasource.url=jdbc:mysql://localhost:3306/maoomtool?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=비밀번호

# 댓글 첨부파일 저장 위치.
app.comment.upload-dir=D:/maoomtool-uploads/comments

# 사용자 프로필 이미지 저장 위치.
app.user.profile-upload-dir=D:/maoomtool-uploads/profiles

# Google Drive 인증 정보 파일 위치.
app.google-drive.credentials-path=D:/maoomtool-config/google/credentials.json

# Slack 알림과 PDF 협업 웹소켓 설정. 사용하지 않는 값은 운영 정책에 맞게 비워 둔다.
app.slack.webhook-url=
app.slack.bot-token=
app.websocket.allowed-origins=http://172.16.10.105:8080

# 견적 결과 저장 폴더. 비워두면 Java 실행 계정의 Desktop/temp를 사용한다.
color-check.output-dir=D:/maoomtool-results/color-check

# PDF 인쇄데이터 검증 로컬 실행 파일 위치.
pdf-check-scan-viewer.exe-path=C:/maoomtool/tools/PdfCheckScanViewer-0.1.0.exe

# PDF 인쇄데이터 검증에서 사용하는 매칭 테이블 위치.
pdf-check-scan-viewer.match-table-path=D:/maoomtool-config/pdf-check/match-table.xlsx

# Schematron 규칙 파일과 생성된 HTML 결과 보고서 저장 위치.
dita.validation.schematron-dir=D:/maoomtool/schematron
dita.validation.report-dir=D:/maoomtool-results/schematron

# 업로드 파일 크기 제한.
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
```

환경변수로도 같은 설정을 줄 수 있다. 예를 들어 서버 루트는
`DITAMAP_BUILDER_ALLOWED_ROOTS`, DB는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를
사용할 수 있다. 다만 운영에서는 한눈에 보기 쉬운
`config/application-local.properties` 파일 방식을 우선 권장한다.

### 서버 추가 후 확인 순서

1. 배포용 PC에서 공유 폴더가 열리는지 확인한다.
2. 배포용 PC에서 실행 계정이 읽기/쓰기 권한을 갖는지 확인한다.
3. `config/application-local.properties`에 필요한 경로를 추가하거나 삭제한다.
4. 애플리케이션을 재시작한다.
5. 화면에서 해당 경로를 입력해 실제 작업이 되는지 확인한다.

경로 입력형 기능인 제품사양서 비교, BER, QSG, 다국어, Revision, 도안의뢰서 작성은
기본적으로 사용자가 입력한 경로를 서버 PC에서 그대로 검사한다. 별도 허용 루트 설정이
없는 기능은 설정 파일에 G서버를 추가하지 않아도 되지만, 배포용 PC에서 그 경로에
접근할 수 있어야 한다.

Schematron 검사도 같은 원칙으로 배포용 PC가 입력 DITAMAP과 연결 topic을 읽을 수
있어야 한다. `.sch` 파일을 추가·삭제할 때는 `dita.validation.schematron-dir`에만
반영하고, 원고/퍼블리싱 접두사와 버전 형식을 지킨 뒤 화면의 검사 유형에 새 버전이
표시되는지 확인한다. 결과 폴더는 애플리케이션 실행 계정에 읽기·쓰기 권한이 있어야
관리자 이력의 결과물 링크가 정상 동작한다.

## 장애 확인 순서

### 화면은 열리지만 실행이 안 되는 경우

1. 브라우저 개발자 도구의 Network 탭에서 실제 실행 API가 호출됐는지 확인한다.
2. HTTP 상태 코드와 응답 본문의 오류 메시지를 확인한다.
3. 같은 시간대의 서버 로그에서 Controller 진입과 Service 예외를 확인한다.
4. 입력 경로를 서버 PC에서 직접 열어 본다.
5. 외부 EXE/VBS/배치를 쓰는 기능은 서버 계정으로 수동 실행한다.
6. DB 이력에 시작 행만 있고 완료 상태가 없는지 확인한다.

### 실행 중 표시가 오래 유지되는 경우

- Network의 요청이 `Pending`인지, 이미 오류 응답을 받았는지 구분한다.
- Java 프로세스의 CPU와 메모리, 외부 프로세스의 실행 여부를 확인한다.
- 결과 폴더의 파일 크기나 수정 시각이 변하는지 확인한다.
- 네트워크 공유 응답 지연과 잠긴 Excel/PDF 파일이 있는지 확인한다.
- Schematron은 진행률 API의 단계와 처리 파일 수가 증가하는지 확인한다.

진행 문구만 움직인다고 실제 작업 중인 것은 아니다. 반대로 긴 동기 요청이 Network에서
`Pending`인 것은 정상일 수 있다. 진행률, 서버 로그, 프로세스와 파일 변경을 함께 봐야
한다.

### 결과 파일은 있는데 화면에서 열리지 않는 경우

1. DB 또는 응답에 저장된 결과 경로와 실제 파일 경로를 비교한다.
2. 애플리케이션 실행 계정의 읽기 권한을 확인한다.
3. 다운로드/보고서 URL이 서버의 내부 경로를 그대로 브라우저에 노출하지 않는지 본다.
4. Content-Type과 Content-Disposition이 결과 형식에 맞는지 확인한다.
5. 파일명에 한글, 공백, 괄호가 있을 때 URL 인코딩이 정상인지 확인한다.

## 기능 추가 시 연결 체크리스트

새 작업을 프로젝트에 넣을 때는 화면 하나만 추가하고 끝내지 않는다.

1. `UserMain` 카드·아이콘과 화면 이동 URL을 추가한다.
2. 입력 화면에서 필수값, 서버 경로 의미와 취소 동작을 정의한다.
3. Controller에 화면 또는 실행 API를 연결한다.
4. Service에 업무 처리와 입력 검증을 구현한다.
5. DB가 필요하면 DTO, Mapper/XML, 초기화 SQL과 관리자 조회를 추가한다.
6. 결과 파일의 저장 위치, 이름 충돌, 정리 정책과 다운로드 방식을 정한다.
7. 일반 실행 로그인지 전용 이력인지 결정하고 성공·실패 양쪽을 기록한다.
8. 배포용 설정 키와 기본값, 외부 도구/템플릿 복사 항목을 문서화한다.
9. 단위 테스트와 화면/컨트롤러 테스트를 추가한다.
10. 실제 배포 PC 계정으로 공유 폴더·DB·외부 프로그램을 포함한 통합 확인을 한다.

기존 기능을 수정할 때는 같은 이름의 새 코드를 만들기 전에 현재 화면, Controller,
Service, Mapper와 테스트를 먼저 찾는다. 기존 사용자의 데이터와 설정을 유지해야 하므로
추측으로 파일을 교체하거나 DB 테이블을 초기화하지 않는다.

## 테스트 확인 방법

전체 회귀 확인은 프로젝트 루트에서 다음 명령으로 한다.

```powershell
.\mvnw.cmd test
```

특정 서비스만 빠르게 확인할 때는 다음처럼 테스트 클래스를 지정한다.

```powershell
.\mvnw.cmd "-Dtest=AdminUserServiceTest" test
```

Schematron 변경은 규칙 선택, 실제 검사, 진행률, 이력과 보고서가 서로 연결되어 있으므로
관련 테스트를 묶어서 실행한다.

```powershell
.\mvnw.cmd "-Dtest=DitaValidationWorkflowServiceTest,DitaSchematronServiceTest,SchematronFileServiceTest,AdminMainTemplateTest" test
```

문서나 템플릿만 수정했더라도 최소한 다음을 확인한다.

1. `git diff --check`로 잘못된 공백과 충돌 표식이 없는지 확인한다.
2. 한글 파일을 UTF-8로 다시 읽어 Unicode 대체 문자가 생기지 않았는지 확인한다.
3. 변경한 화면의 컨트롤러 테스트 또는 애플리케이션 컨텍스트 테스트를 실행한다.
4. 배포용 PC와 다른 경로를 사용하는 기능은 로컬 성공만으로 끝내지 말고 운영 설정과
   실행 계정의 파일 권한까지 확인한다.
