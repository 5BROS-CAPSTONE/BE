# 5BROS Backend

Backend của **Drone Mission Management & Decision Support System**.
Đợt triển khai đầu tiên gồm AUTH-01, AUTH-02 và AUTH-03 theo bản phân tích hiện tại.

## Công nghệ

- Java 21, Spring Boot 4.1.1, Maven Wrapper.
- Spring Security: BCrypt, JWT HS256 và phân quyền theo role/permission.
- Spring Data JPA, Supabase PostgreSQL, Flyway.
- H2 cho môi trường local và kiểm thử độc lập.

## Cấu trúc code

```text
src/main/java/com/fivebros/backend/
├── BackendApplication.java
├── account/         # Tài khoản, quản lý role, khởi tạo Admin
│   └── dto/         # Dữ liệu vào/ra API tài khoản
├── auth/            # Đăng nhập, đăng xuất, phiên đăng nhập
│   └── dto/         # Dữ liệu vào/ra API đăng nhập
├── security/        # Spring Security, JWT, role và permission
├── config/          # Cấu hình có kiểu dữ liệu, Clock, PasswordEncoder
└── common/          # Phân trang, validation, xử lý lỗi dùng chung
src/main/resources/
├── application.yml
├── application-local.yml
├── application-supabase.yml
└── db/migration/
    ├── common/      # Bảng tài khoản, role và phiên đăng nhập
    └── postgresql/  # Bảo vệ schema trên PostgreSQL/Supabase
supabase/migrations/ # Khởi tạo role kết nối và schema, chạy một lần bởi quản trị DB
src/test/java/       # Kiểm tra API qua Spring Security và database thực của test
docs/auth.http       # Request mẫu cho IntelliJ HTTP Client
```

Controller nhận request và trả response; service xử lý nghiệp vụ trong transaction;
repository truy cập database. API trả DTO, không trả trực tiếp entity chứa password hash.
Các module nghiệp vụ tiếp theo có thể thêm package riêng như `fleet`, `battery`, `mission`.

## Các chức năng đã triển khai

| Mã | Chức năng |
|---|---|
| AUTH-01 | Đăng nhập bằng email/mật khẩu, JWT có thời hạn, đăng xuất thu hồi phiên hiện tại, xem hồ sơ bản thân |
| AUTH-02 | Admin tạo, xem, tìm kiếm, phân trang và sửa tài khoản; có thể đặt lại mật khẩu |
| AUTH-03 | Sáu role cố định, gán/thay thế role, kiểm tra quyền trên API |

Role cố định: `ADMIN`, `MANAGER`, `EXPERT`, `OPERATOR`, `TECHNICIAN`, `ANALYST`.
Một tài khoản có thể có nhiều role; đây là lựa chọn triển khai cho RBAC hiện tại.

Trong phạm vi ba chức năng này, Admin có `user:read`, `user:create`, `user:update`,
`user:assign-roles`. Mọi người dùng đã đăng nhập được xem hồ sơ bản thân và đăng xuất.
Permission cho các module nhiệm vụ, drone, pin và DSS sẽ được bổ sung khi triển khai các module đó.

## Chạy dự án đã cấu hình trên máy hiện tại

Yêu cầu Java 21. Mở terminal tại thư mục dự án:

```powershell
.\mvnw.cmd spring-boot:run
```

File `.env` tại thư mục gốc được Spring Boot đọc như Java properties và được Git bỏ qua.
Trên máy đã thiết lập, file này chứa cấu hình project Supabase `CAPSTONE`, thông tin
đăng nhập PostgreSQL cho role `fivebros_backend`, khóa JWT và thông tin Admin ban đầu.
Tài khoản Admin ban đầu là `admin@5bros.local`; lấy mật khẩu từ `BOOTSTRAP_ADMIN_PASSWORD`
trong `.env`. Không đưa `.env` hoặc mật khẩu vào Git, ảnh chụp hay request mẫu được chia sẻ.

API mặc định: `http://localhost:8080/api/v1`.
Trong IntelliJ, mở `pom.xml` dưới dạng Maven project và đặt Project SDK là Java 21.

## Thiết lập trên máy khác

Copy `.env.example` thành `.env` rồi điền giá trị, hoặc đặt các biến tương ứng trong
Run Configuration/biến môi trường. Biến môi trường có ưu tiên cao hơn file `.env`.

### Supabase PostgreSQL

1. Chọn project Supabase. Bản đã cấu hình dùng project ref `rtjwehaefpzxrvxorvkn`.
2. Cấu hình hiện tại dùng **Session pooler, cổng 5432** với host
   `aws-0-ap-southeast-1.pooler.supabase.com` và username
   `postgres.rtjwehaefpzxrvxorvkn`, theo thông tin Connect của project.
3. Với database mới, quản trị DB chạy SQL trong `supabase/migrations` một lần để tạo
   role `fivebros_backend` và schema riêng `app` dùng lưu lịch sử migration. Mật khẩu
   không nằm trong migration.
4. Đặt mật khẩu role `postgres` trong `SUPABASE_DB_PASSWORD` ở file `.env` trên máy chạy BE;
   không đưa mật khẩu vào URL hay commit lên Git. JDBC URL cần `sslmode=require`.
5. Flyway tạo/cập nhật các bảng nghiệp vụ trong `public`; Hibernate dùng
   `default_schema: public` và `ddl-auto=validate`.

> Các bảng `public.user_accounts`, `public.user_roles` và `public.auth_sessions` dễ xem trong
> Table Editor. Flyway giữ bảng kỹ thuật `app.flyway_schema_history` ở schema riêng `app`.
> Các bảng nghiệp vụ bật RLS; quyền truy cập API của Supabase bị thu hồi để Spring Boot quản lý
> đăng nhập và phân quyền.

> Lưu ý: kết nối BE bằng role `postgres` có quyền database rộng. Khi triển khai, nên dùng
> role `fivebros_backend` với mật khẩu riêng.

Tham khảo [hướng dẫn Spring Boot của Supabase](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot)
và [các phương thức kết nối PostgreSQL](https://supabase.com/docs/guides/database/connecting-to-postgres).

### Tạo khóa JWT

Khóa phải là Base64 của ít nhất 32 byte ngẫu nhiên. Sinh khóa trong PowerShell và
gán trực tiếp vào biến môi trường:

```powershell
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:AUTH_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

Giữ cùng khóa trên các instance backend. Thay khóa làm các JWT hiện tại mất hiệu lực.

### Khởi tạo Admin cho database mới

Đặt `BOOTSTRAP_ADMIN_ENABLED=true`, email, tên và mật khẩu trước lần chạy đầu.
Mật khẩu có ít nhất 12 ký tự và tối đa 72 byte UTF-8.
Sau khi Admin đã được tạo, đặt `BOOTSTRAP_ADMIN_ENABLED=false`.
Bootstrap giữ nguyên tài khoản Admin đã tồn tại, không đặt lại mật khẩu mỗi lần chạy.

### Chạy với H2

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
```

H2 lưu dữ liệu ở `.local-data/`. Thiết lập biến bootstrap ở trên nếu cần Admin local.
Nếu profile local không có khóa JWT được cấu hình, ứng dụng sinh khóa tạm mỗi lần chạy.

## API

Ngoại trừ login, request cần header `Authorization: Bearer <accessToken>`.

| Method | Endpoint | Quyền |
|---|---|---|
| POST | `/api/v1/auth/login` | Công khai |
| POST | `/api/v1/auth/logout` | Đã đăng nhập |
| GET | `/api/v1/auth/me` | Đã đăng nhập |
| GET | `/api/v1/users?search=&page=0&size=20` | Admin |
| GET | `/api/v1/users/{id}` | Admin |
| POST | `/api/v1/users` | Admin |
| PUT | `/api/v1/users/{id}` | Admin |
| PUT | `/api/v1/users/{id}/roles` | Admin |
| GET | `/api/v1/roles` | Admin |

`PUT /users/{id}` cập nhật email và fullName. Trường `password` là tùy chọn;
bỏ trường này hoặc gửi `null` để giữ mật khẩu hiện tại. Đặt lại mật khẩu thu hồi mọi
phiên của tài khoản. `PUT /users/{id}/roles` thay thế toàn bộ tập role và cần ít nhất một role.
Không thể bỏ role Admin của Admin cuối cùng.

JWT mặc định sống 30 phút. Đổi bằng `AUTH_TOKEN_TTL`, ví dụ `PT1H`.
Mỗi request kiểm tra phiên và lấy quyền hiện tại từ database, nên logout và đổi role
có hiệu lực ngay trên những request kế tiếp. Đăng xuất chỉ thu hồi phiên đang sử dụng.

Lỗi dùng `application/problem+json`, gồm `status`, `detail`, `instance`, `code` và
`errors` khi validation trường dữ liệu thất bại. Mã thường gặp: 400 dữ liệu sai,
401 chưa xác thực, 403 thiếu quyền, 404 không tìm thấy, 409 dữ liệu xung đột.

## Dùng request mẫu trong IntelliJ

Copy `docs/http-client.env.example.json` thành `docs/http-client.private.env.json`,
điền mật khẩu Admin và mật khẩu cho tài khoản mới. File private được Git bỏ qua.
Trên máy đã thiết lập, file private này đã được chuẩn bị sẵn.
Mở `docs/auth.http`, chọn environment `local`, chạy login trước. HTTP Client lưu
accessToken sau login và userId sau tạo tài khoản để dùng trong các request tiếp theo.

## Kiểm tra và đóng gói

```powershell
.\mvnw.cmd verify
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Bộ 21 test API dùng H2 trong bộ nhớ, tắt việc đọc `.env`, không kết nối hoặc xóa dữ liệu
Supabase. Các kiểm tra bao gồm quyền của cả sáu role, tạo/sửa tài khoản, email trùng,
validation, JWT sai/hết hạn, thu hồi phiên, đổi quyền trên JWT đã cấp, reset mật khẩu
và bảo vệ Admin cuối cùng khi có hai yêu cầu đổi role đồng thời.

Đã kiểm tra JAR kết nối Supabase CAPSTONE, chạy migration, khởi tạo Admin, login,
xem/sửa tài khoản, cập nhật role và logout. JWT đã logout bị từ chối với HTTP 401.
