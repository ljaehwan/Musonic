# Docker로 음악 서버 연결하기

Musonic은 Android 플레이어이고, Navidrome은 음악 폴더를 읽어 앱으로 스트리밍하는 별도의 오픈소스 서버입니다. PC·NAS·홈 서버에서 Docker로 실행할 수 있습니다. 이미 Subsonic/OpenSubsonic 서버가 있다면 설치 과정 없이 앱에 기존 서버 주소와 계정을 입력하면 됩니다.

## 다른 서버의 Docker 설치 안내

| 서버 | Docker 설치 |
| --- | --- |
| [Navidrome](https://www.navidrome.org/) | [공식 Docker 안내](https://www.navidrome.org/docs/installation/docker/) |
| [Gonic](https://github.com/sentriz/gonic) | [프로젝트 Docker 안내](https://github.com/sentriz/gonic/wiki/installation#with-docker) |
| [Airsonic-Advanced](https://github.com/kagemomiji/airsonic-advanced) | [Docker 안내](https://github.com/kagemomiji/airsonic-advanced#docker) · [Compose 예제](https://github.com/kagemomiji/airsonic-advanced/tree/main/install/compose) |

Navidrome은 Musonic 연결·재생을 확인했습니다. Gonic과 Airsonic-Advanced는 Subsonic API를 제공하지만 Musonic에서 직접 테스트하지는 않았으며, 서버별로 지원 기능이 다를 수 있습니다.

## 1. 서버 준비

Docker와 Compose 플러그인이 필요합니다. Docker Desktop에는 Compose가 포함되어 있습니다.

```sh
git clone https://github.com/ljaehwan/Musonic.git
cd Musonic/examples/navidrome
cp .env.example .env
mkdir -p data
```

Windows PowerShell에서는 마지막 두 줄 대신 `Copy-Item .env.example .env`와 `New-Item -ItemType Directory -Force data`를 사용하세요. Docker Desktop은 Linux 컨테이너 모드로 실행합니다.

`.env`의 `MUSIC_PATH`를 실제 음악 폴더의 절대 경로로 바꾸세요. Windows에서는 `C:/Users/you/Music` 같은 경로를 사용할 수 있습니다. 공백이 있는 경로는 따옴표로 감싸세요. `DATA_PATH`는 별도의 쓰기 가능한 폴더이며, 기본값 `./data`에는 서버 DB와 캐시가 저장됩니다.

Linux에서는 `id -u`, `id -g`로 확인한 값을 `NAVIDROME_UID`, `NAVIDROME_GID`에 입력하세요. 이 사용자에게 data 폴더의 쓰기 권한과 음악 폴더의 읽기 권한이 있어야 합니다. 예제는 음악을 읽기 전용으로 연결하며, 존재하지 않는 경로를 자동으로 만들지 않습니다.

```sh
docker compose up -d
```

Docker를 실행한 컴퓨터에서 **http://localhost:4533**을 열고 첫 관리자 계정을 만드세요. 음악 검색이 끝나면 앨범이 표시됩니다. 평소 앱에서 사용할 일반 사용자 계정은 Navidrome에서 별도로 만들 수 있습니다. 화면 없는 NAS라면 초기 설정에 SSH 터널을 쓰거나 신뢰할 수 있는 내부망 주소에 먼저 바인딩하세요.

## 2. 휴대폰에서 연결

1. `.env`의 `BIND_ADDRESS`를 서버의 내부 IP로 바꾸고 `docker compose up -d`를 다시 실행하세요. 신뢰할 수 있는 집 네트워크에서는 `0.0.0.0`으로 모든 호스트 인터페이스에 연결할 수도 있습니다.
2. 같은 Wi-Fi의 휴대폰 브라우저에서 `http://서버내부IP:4533`이 열리는지 확인하세요.
3. [Musonic APK](https://github.com/ljaehwan/Musonic-releases/releases/latest)를 설치하고 같은 주소와 Navidrome 계정을 입력하세요.
4. HTTP 주소라면 **고급 설정**에서 HTTP 허용 옵션을 켠 뒤 연결하세요. Play 빌드는 HTTPS만 지원합니다.

휴대폰에서 `localhost`는 서버가 아니라 휴대폰 자신입니다. Docker 서비스 이름인 `navidrome`도 보통 휴대폰에서는 접속 주소로 사용할 수 없습니다.

## 3. 집 밖에서 듣기

유효한 인증서가 있는 HTTPS 리버스 프록시를 설정해 `https://music.example.com` 같은 주소로 연결하거나, 개인 VPN으로 내부망에 접속하세요. 원본 HTTP 포트를 인터넷에 직접 공개하지 마세요. 브라우저 전용 로그인 화면을 추가하는 프록시는 앱의 API 인증을 막을 수 있습니다.

문제가 생기면 `docker compose logs --tail=50 navidrome`으로 경로·권한·스캔 오류를 확인하세요. data 폴더 권한 문제를 해결하려고 음악 폴더 전체 권한을 바꿀 필요는 없습니다. 업데이트 전에는 data 폴더와 음악을 백업하고 서버 릴리즈 안내를 확인하세요.

예제 버전은 **Navidrome 0.64.2**입니다. 더 자세한 설명은 [영문 연결 안내](SERVER-SETUP.md), [Navidrome Docker 공식 문서](https://www.navidrome.org/docs/installation/docker/)를 참고하세요.
