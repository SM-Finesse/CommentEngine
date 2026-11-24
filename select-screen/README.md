# 서비스 선택 화면

"어느 서비스를 사용하시겠습니까?"에서 칩 카드를 고르면 그 칩의 화면으로 이동합니다. 빌드 없는 정적 사이트(HTML/CSS/JS)이고 Nginx로 띄웁니다.

이 폴더는 본체입니다. **칩 이름·주소·그림을 여기에 적지 않습니다.** 칩이 자기 카드를 들고 와서 꽂습니다.

| 파일 | 내용 |
|---|---|
| `index.html`, `style.css` | 화면 뼈대와 스타일 (라이트/다크) |
| `app.js` | `config.json`과 칩 카드를 읽어 보여 주고, 고르면 이동 |
| `config.json` | 사이트 이름, 안내 문구, 카드 수 |
| `nginx.conf` | `/cards/` 목록을 JSON으로 내줌 |

## config.json

| 칸 | 뜻 | 없으면 |
|---|---|---|
| `siteName` | 왼쪽 위와 탭 제목 | `CommentEngine` |
| `question` | 안내 문구 | `어느 서비스를 사용하시겠습니까?` |
| `comingSoon` | "준비 중" 카드 수 | 1 |
| `emptySlots` | 오른쪽 빈 자리 수 (장식) | 2 |

## 칩 꽂기

`/cards/` 아래 **폴더 하나가 칩 하나**입니다. 칩의 `chips/<칩>/select-card/`를 컨테이너의 `/cards/<칩>`에 끼우면 카드가 나타납니다. 이 폴더의 파일은 고치지 않습니다.

```
/cards/<칩>/card.json
/cards/<칩>/<그림 파일>
```

`card.json`:

| 칸 | 필수 | 뜻 |
|---|---|---|
| `name` | ✅ | 카드 이름 |
| `url` | ✅ | 이동할 주소. `{host}`는 지금 접속한 주소로 바뀜 (`http://{host}:<포트>`) |
| `description` | | 카드 아래 설명 |
| `image` | | 같은 폴더의 그림 파일 이름. 없으면 이름 첫 글자 + `color` |
| `color` | | 그림이 없을 때 배경색 |
| `order` | | 작은 것부터. 같으면 폴더 이름순, 없으면 맨 뒤 |

- 칩이 하나도 없으면(`/cards/`가 404) "꽂힌 서비스가 없습니다"와 준비 중·빈 자리만 보입니다. 오류가 아닙니다.
- `card.json`이 깨졌거나 필수 칸이 없으면 그 카드만 건너뜁니다 (브라우저 콘솔에 경고).

## 띄우기

레포 루트에서 (PowerShell):

```powershell
docker run -d --name ce-select -p 8090:80 -v "${PWD}\select-screen:/usr/share/nginx/html:ro" -v "${PWD}\select-screen\nginx.conf:/etc/nginx/conf.d/default.conf:ro" -v "${PWD}\chips\<칩>\select-card:/cards/<칩>:ro" nginx:1.27-alpine
```

→ http://localhost:8090

## 조작

- 마우스: 카드에 올리면 선택, 누르면 이동
- 키보드: `←` `→` 선택, `Enter` 이동
- 아래 동그란 버튼: 테마(라이트/다크) 전환, 목록 새로고침
