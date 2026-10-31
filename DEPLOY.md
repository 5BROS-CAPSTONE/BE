# Deploy backend lên Railway

Tài liệu này dành cho người deploy. Cách chạy ở máy xem [README.md](README.md).

## Một database duy nhất

Nhóm dùng **một database Supabase duy nhất**, chính là database đang có, vừa làm production
cho Railway vừa là nơi dữ liệu thật nằm. Không tạo database thứ hai.

| Môi trường | Profile | Database |
| --- | --- | --- |
| Máy cá nhân | `local` | H2 file `./.local-data`, nằm trên máy mỗi người |
| Production | `supabase` | Supabase của nhóm |

**Lập trình hằng ngày thì chạy profile `local`.** Đây không phải lời khuyên cho vui:
chỉ còn một database thật, nên mọi thứ bạn làm khi nối thẳng vào nó đều đụng vào dữ liệu
production. Xoá một bản ghi để thử là mất thật. Chạy một migration nửa vời là cả nhóm
và buổi demo cùng chịu. H2 ở máy hỏng thì xoá thư mục `.local-data` làm lại, không ai biết.

Chỉ nối vào Supabase khi thật sự cần kiểm tra dữ liệu thật, và khi đó **chỉ đọc**.

## Biến môi trường đặt trong Railway

Đặt ở tab **Variables** của service, không đặt trong `railway.toml` và không commit.

| Biến | Giá trị | Ghi chú |
| --- | --- | --- |
| `spring.profiles.active` | `supabase` | |
| `SUPABASE_DB_URL` | `jdbc:postgresql://...pooler.supabase.com:5432/postgres?sslmode=require` | Session pooler, **không** kèm mật khẩu |
| `SUPABASE_DB_USERNAME` | `postgres.<project-ref>` | |
| `SUPABASE_DB_PASSWORD` | mật khẩu database | |
| `AUTH_JWT_SECRET` | khoá Base64 | Xem bên dưới |
| `AUTH_TOKEN_TTL` | `PT30M` | |
| `AUTH_REFRESH_TOKEN_TTL` | `P30D` | |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` | Database đã có sẵn tài khoản admin |
| `BOOTSTRAP_ADMIN_EMAIL` | email admin | Không dùng tới khi biến trên là `false` |
| `BOOTSTRAP_ADMIN_PASSWORD` | mật khẩu mạnh | Không dùng tới khi biến trên là `false` |
| `BOOTSTRAP_ADMIN_NAME` | `5BROS Administrator` | |
| `CORS_ALLOWED_ORIGINS` | `https://frontendcapstone-eight.vercel.app,https://frontendcapstone-*.vercel.app,http://localhost:5173` | Ngăn bằng dấu phẩy. Chấp nhận `*` |
| `DB_POOL_SIZE` | `5` | Supabase bản free giới hạn số kết nối |
| `API_DOCS_ENABLED` | `true` | Phải bật vì healthcheck gọi `/v3/api-docs` |

Không đặt `SERVER_PORT`. Railway tự tiêm `PORT` và `application.yml` đã ưu tiên biến đó.

Sinh khoá JWT:

```bash
openssl rand -base64 32
```

Khoá hiện tại đã được chia sẻ qua chat trong nhóm. Ai có nó đều tự ký được token đăng nhập
giả với quyền bất kỳ. Trước khi domain Railway công khai, hãy **sinh khoá mới** và đặt vào
Railway. Đổi khoá sẽ làm mọi phiên đăng nhập hiện có hết hiệu lực — mọi người đăng nhập lại
là xong.

## Tài khoản admin

Database đã có sẵn tài khoản admin, nên để `BOOTSTRAP_ADMIN_ENABLED=false`.

Bật `true` sẽ khiến app tạo tài khoản ở mỗi lần khởi động và mật khẩu admin phải nằm
trong biến môi trường của Railway — ai vào được dashboard cũng đọc được.

## Các bước deploy

1. Railway → **New Project → Deploy from GitHub repo** → chọn `5BROS-CAPSTONE/BE`.
2. Branch deploy: `main`.
3. Railway đọc `railway.toml` và build bằng `Dockerfile`, không cần chỉnh gì thêm.
4. Điền đủ biến ở bảng trên rồi bấm deploy.
5. **Settings → Networking → Generate Domain** để lấy domain công khai.
6. Đưa domain đó cho FE đặt vào `VITE_API_BASE_URL`, dạng `https://<domain>/api/v1`.

## Kiểm tra sau khi deploy

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://<domain>/v3/api-docs
curl -s -X POST https://<domain>/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"sai@example.com","password":"sai"}'
```

Lần lượt phải ra `200` và `401 INVALID_CREDENTIALS`.
Ra `500` nghĩa là sai cấu hình database — xem log trong Railway.

## Lỗi hay gặp

**`Migration checksum mismatch`** — ai đó sửa file migration đã chạy. Đừng tắt validate.
Xem lại `git log` của file bị báo, rồi chạy `flyway repair`. Phòng bệnh: không bao giờ
sửa file migration đã chạy, chỉ thêm file mới.

**`Connection refused` hoặc treo lúc khởi động** — dùng nhầm chuỗi Direct connection.
Phải dùng **Session pooler** cổng `5432`.

**FE gọi API bị chặn CORS** — `CORS_ALLOWED_ORIGINS` thiếu domain Vercel.
Vercel sinh một domain preview mới cho mỗi pull request, nên khai báo thêm dạng mẫu
`https://frontendcapstone-*.vercel.app`. Dấu `*` chỉ thay cho một đoạn, không vượt qua
dấu chấm, nên `...vercel.app.evil.example` vẫn bị chặn.
