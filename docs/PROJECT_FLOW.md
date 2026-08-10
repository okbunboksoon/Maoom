# MaoomTool 프로젝트 흐름

이 문서는 처음 프로젝트를 보는 사람이 화면, API, 서비스, DB, 파일 처리의
연결 방향을 빠르게 파악하기 위한 안내서다. 세부 구현은 각 컨트롤러와 서비스의
클래스 주석을 따라가면 된다.

## 전체 구조

```text
브라우저 화면
  -> Thymeleaf template / static JavaScript
  -> Controller
  -> Service
  -> Mapper / JdbcTemplate / 파일 처리 / XSL, Excel 처리
  -> DB 또는 결과 파일
```
12312
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
- `src/main/java/maoomWeb/ire/admin/service`  [userMain.html](../src/main/resources/templates/user/userMain.html)
  관리자 화면에서 쓰는 DB 조회/수정 서비스와 DTO가 있다.
- `src/main/resources/mapper`  
  MyBatis SQL XML이다. DB 테이블과 Java DTO 필드 연결이 여기에 있다.
- `src/main/resources/xsl`, `src/main/resources/revision-tool`  
  DITA/XML 변환 파이프라인에서 사용하는 XSL, DTD, 기준 파일 자원이다.

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

## 메인 화면 이동 흐름

```text
UserController
  -> /main
  -> /pdf/list, /revision/list, /multilingual/list, /qsg/list
  -> 각 업무 화면 template
```

대부분의 업무 화면은 HTML에서 입력값을 받고, 별도 REST API를 호출해서 실제 작업을
시작한다. 작업 결과는 파일 다운로드 응답 또는 JSON 결과로 돌아온다.

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

## 관리자 화면 흐름

```text
adminMain.html
  -> /admin/color-check/items
  -> /admin/project-logs
  -> /admin/users
```

관리자 화면은 한 HTML 안에서 탭을 바꿔가며 DataTables를 초기화한다. 탭별 데이터는
처음 열 때 API로 로드하고, 테이블 검색/정렬/페이지네이션은 DataTables가 처리한다.

- 견적 탭: `AdminColorCheckController`
- 실행 로그 탭: `AdminProjectExecutionLogController`
- 사용자 탭: `AdminUserController`

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
ditamap.builder.allowed-roots=\\192.168.10.221\QC_Docs;\\192.168.10.221\kia_om26;\\g-server\project
```

G서버를 추가하려면 마지막에 `;\\g-server\project`를 붙인다.

```properties
ditamap.builder.allowed-roots=\\192.168.10.221\QC_Docs;\\192.168.10.221\kia_om26;\\g-server\project
```

G서버를 빼려면 해당 항목만 지운다.

```properties
ditamap.builder.allowed-roots=\\192.168.10.221\QC_Docs;\\192.168.10.221\kia_om26
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

# 견적 결과 저장 폴더. 비워두면 Java 실행 계정의 Desktop/temp를 사용한다.
color-check.output-dir=D:/maoomtool-results/color-check

# PDF 인쇄데이터 검증 로컬 실행 파일 위치.
pdf-check-scan-viewer.exe-path=C:/maoomtool/tools/PdfCheckScanViewer-0.1.0.exe
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

## 테스트 확인 방법

전체 회귀 확인은 프로젝트 루트에서 다음 명령으로 한다.

```powershell
.\mvnw.cmd test
```

특정 서비스만 빠르게 확인할 때는 다음처럼 테스트 클래스를 지정한다.

```powershell
.\mvnw.cmd -Dtest=AdminUserServiceTest test
```
