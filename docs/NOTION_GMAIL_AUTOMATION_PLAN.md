# Notion DB Gmail 자동 발송 구현 검토

## 목적

Notion DB에 입력된 값을 읽어 개인 Gmail 계정으로 자동 메일을 발송하는 시스템을 현재 MaoomTool 프로젝트에 구현할 수 있는지 검토한다.

Make 같은 외부 자동화 도구는 사용하지 않고, Notion API와 Gmail/Google API를 직접 사용한다.

## 현재 프로젝트 상태

- 프로젝트는 Spring Boot, Java, Maven 기반이다.
- Google Drive API와 Google OAuth 관련 의존성이 이미 일부 포함되어 있다.
- Google OAuth 성공 후 토큰을 처리하는 코드가 일부 존재한다.
- OAuth 토큰 저장용 `oauth2_authorized_client` 테이블 생성 SQL이 존재한다.
- Notion API 연동 코드는 아직 없다.
- Gmail API 발송 코드는 아직 없다.
- Spring Scheduler 기반 자동 실행 코드는 아직 없다.
- 현재 상태에서 `.\mvnw.cmd -DskipTests compile` 컴파일은 성공했다.

## 목표 발송 구조

```text
Notion DB
→ 수신인 열에서 이메일 주소 읽기
→ 제목/본문 열에서 메일 내용 읽기
→ 개인 Gmail 계정 OAuth 토큰으로 Gmail API 발송
→ Notion DB에 발송완료/발송일시/오류내용 업데이트
```

## Notion DB 권장 열

| 열 이름 | 용도 |
| --- | --- |
| 수신인 | 메일을 받을 이메일 주소 |
| 제목 | Gmail 제목 |
| 본문 | Gmail 본문 |
| 발송여부 | 중복 발송 방지용 상태값 |
| 발송일시 | 발송 성공 시각 |
| 오류내용 | 발송 실패 사유 기록 |

## Gmail 발신 방식

발신인은 개인 Gmail 계정이다.

개인 Gmail로 발송하려면 Google OAuth 동의가 필요하다. 한 번 발신 계정을 연결하면 refresh token을 저장해 두고, 이후 자동 발송 시 Gmail API의 `gmail.send` scope로 메일을 보낼 수 있다.

필요 작업:

- Google Cloud Console에서 Gmail API 활성화
- OAuth Client 설정
- redirect URI 설정
- OAuth scope에 Gmail 발송 권한 추가
- 프로젝트에 Gmail API 의존성 추가
- Gmail 발송 서비스 구현

## Notion 업데이트 감지 방식

Notion DB가 업데이트될 때 메일을 보내는 방식은 크게 두 가지다.

## 1. Webhook 방식

```text
Notion DB 업데이트
→ Notion이 MaoomTool 서버로 HTTP POST 요청 전송
→ MaoomTool 서버가 변경된 page ID 확인
→ Notion API로 최신 row 조회
→ Gmail API로 발송
→ Notion DB에 발송 결과 업데이트
```

장점:

- Notion 변경 이벤트를 비교적 빠르게 받을 수 있다.
- 서버가 계속 전체 DB를 조회하지 않아도 된다.

제약:

- Notion이 접근 가능한 공개 HTTPS URL이 필요하다.
- 현재 내부망 주소인 `192.168.10.76:8080`으로는 Notion이 접속할 수 없다.
- 회사망에서 외부 접근을 열려면 방화벽, 포트포워딩, HTTPS 인증서, 보안 검토가 필요하다.

## 2. Polling 방식

```text
Spring Scheduler가 1분마다 실행
→ MaoomTool 서버가 Notion API에 직접 요청
→ 발송여부=false인 row 조회
→ Gmail API로 발송
→ 성공 시 발송여부=true, 발송일시 기록
→ 실패 시 오류내용 기록
```

장점:

- 외부 공개 URL이 없어도 된다.
- 현재 내부망 서버 환경에서도 구현 가능하다.
- 회사 방화벽 설정을 변경하지 않아도 된다.

제약:

- 완전한 실시간은 아니다.
- 스케줄 간격에 따라 1분 또는 그 이상 지연될 수 있다.

## 현재 네트워크 조건

현재 MaoomTool 서버는 다음 주소로 접속한다.

```text
192.168.10.76:8080
```

이 주소는 사설 IP 대역이다. 같은 랜선, 같은 회사 내부망, 같은 공유기 안에 있는 PC들은 접속할 수 있지만 Notion 서버 같은 외부 인터넷 서비스는 접속할 수 없다.

```text
회사 내부 PC → 192.168.10.76:8080 접속 가능
Notion 서버 → 192.168.10.76:8080 접속 불가능
```

따라서 현재 상태에서는 Webhook 방식보다 Polling 방식이 현실적이다.

## Webhook을 쓰려면 필요한 조건

Webhook을 꼭 사용하려면 Notion이 접근 가능한 공개 HTTPS 주소가 필요하다.

예:

```text
https://maoomtool.example.com/notion/webhook
```

이를 위해 필요한 선택지는 다음과 같다.

- 도메인 구매 또는 회사 도메인 사용
- 공인 IP 연결
- 방화벽 및 포트포워딩 설정
- HTTPS 인증서 설정
- ngrok 또는 Cloudflare Tunnel 같은 터널링 서비스 사용
- 외부 VPS나 클라우드 서버에 MaoomTool 배포

운영용 Webhook은 도메인과 HTTPS 구성이 있는 것이 정석이다. 다만 현재 요구사항은 Polling 방식으로도 충분히 구현 가능하다.

## 최종 추천안

현재 환경에서는 Polling 방식을 추천한다.

```text
MaoomTool 서버
→ 1분마다 Notion DB 조회
→ 발송 대상 row만 선택
→ 개인 Gmail 계정으로 Gmail API 발송
→ Notion DB에 발송 결과 업데이트
```

추천 이유:

- `192.168.10.76:8080` 내부망 서버에서도 가능하다.
- 외부 도메인이나 HTTPS 공개 서버가 필요 없다.
- 회사 방화벽 설정을 건드리지 않아도 된다.
- 중복 발송 방지만 잘 설계하면 안정적으로 운영할 수 있다.

## 구현 시 안전장치

- `발송여부=false`인 row만 발송한다.
- 발송 성공 후 반드시 `발송여부=true`로 업데이트한다.
- `발송일시`를 기록한다.
- 실패 시 `오류내용`에 실패 사유를 기록한다.
- 수신인 열이 비어 있거나 이메일 형식이 잘못된 row는 발송하지 않는다.
- 같은 Notion page ID가 중복 발송되지 않도록 로그 또는 상태값을 관리한다.
- 제목/본문 수정 시 재발송할지, 신규 row만 발송할지 운영 규칙을 먼저 정한다.

## 결론

현재 MaoomTool 프로젝트에서 Notion DB 기반 Gmail 자동 발송 시스템 구현은 가능하다.

현재 네트워크 환경에서는 Webhook보다 Polling 방식이 적합하다. Webhook은 외부 공개 HTTPS 주소가 준비된 이후에 추가 검토하는 것이 좋다.
