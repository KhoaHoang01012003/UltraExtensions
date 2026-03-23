# Crawl Filter Burp Extension

Burp Suite extension viet bang Java su dung Montoya API.

## Chuc nang

- Tao mot tab rieng trong Burp ten `Crawl Filter`
- Chi log request di qua `Proxy` va `in-scope`
- Dedupe mac dinh theo `method + path`
- Bo qua toan bo query/body parameters khi dedupe
- Bo qua cac request co duoi `.js`, `.gif`, `.jpg`, `.png`, `.css`, `.json`, `.map`, `.svg`
- Luu request dau tien da thay cho moi fingerprint
- Ho tro `search`, `pause/resume`, `clear`
- Bang log tach rieng `scheme`, `host`, `port`, `path`, `query`, `full URL`
- Co the bat/tat static filter va sua danh sach suffix ngay tren UI
- Co the an/hien cot tuy y va copy chi cac cot dang hien thi
- Luu cau hinh UI qua moi lan reload extension
- Luu lai log request da bat duoc qua moi lan reload extension
- Khi load/reload extension, tu import cac request in-scope dang co trong Proxy history roi dedupe lai
- Co the day selected requests sang Repeater, Intruder, va Active Scan
- Khi copy ma dang an cot Query, cot Path se tu copy du lieu dang `path?query`
- Co tuy chon `Include host in dedupe key` neu ban muon tach rieng theo host/subdomain

## Build

```powershell
.\gradlew.bat build
```

Jar sau khi build:

```text
build/libs/crawlfilter-burp-extension-1.0.0.jar
```

## Load vao Burp

1. Mo `Extensions`
2. Chon `Add`
3. Extension type: `Java`
4. Chon file jar vua build

## Luu y

- Extension chi log request nam trong Burp Scope
- Khi bat/tat `Include host in dedupe key`, extension se `clear` du lieu hien tai de tranh tron hai kieu fingerprint
- Dedupe mac dinh la `method + path`, vi vay:
  - `GET /api/users?page=1`
  - `GET /api/users?page=2`
  - `GET /api/users?id=10`

  se chi giu lai request dau tien
