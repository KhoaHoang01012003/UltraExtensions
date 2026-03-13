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
