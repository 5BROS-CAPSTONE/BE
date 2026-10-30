# 5BROS Backend

Backend của **Drone Mission Management & Decision Support System**.
Đợt triển khai đầu tiên gồm AUTH-01, AUTH-02 và AUTH-03 theo bản phân tích hiện tại,
theo mô hình nội bộ: Admin tạo tài khoản và cấp role trực tiếp.

## Công nghệ

- Java 21, Spring Boot 4.1.1, Maven Wrapper.
- Spring Security: BCrypt, JWT HS256 và phân quyền theo role/permission.
- Spring Data JPA, Supabase PostgreSQL, Flyway.
- H2 cho môi trường local và kiểm thử độc lập.
- springdoc-openapi 3.1.1, Swagger UI và tài liệu OpenAPI với JWT Bearer.

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
| AUTH-01 | Đăng nhập bằng email/mật khẩu, JWT có thời hạn, đăng xuất thu hồi phiên hiện tại, xem hồ sơ và tự đổi mật khẩu |
| AUTH-02 | Admin tạo, xem, tìm kiếm, phân trang và sửa tài khoản; có thể đặt lại mật khẩu |
| AUTH-03 | Sáu role cố định, gán/thay thế role, kiểm tra quyền trên API |

Role cố định: `ADMIN`, `MANAGER`, `EXPERT`, `OPERATOR`, `TECHNICIAN`, `ANALYST`.
Một tài khoản có thể có nhiều role; đây là lựa chọn triển khai cho RBAC hiện tại.

Trong phạm vi ba chức năng này, Admin có `user:read`, `user:create`, `user:update`,
`user:assign-roles`. Mọi người dùng đang hoạt động đã đăng nhập được xem hồ sơ bản thân, tự đổi mật khẩu và đăng xuất.
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

## Lệnh thường dùng

| Lệnh | Tác dụng |
|---|---|
| `./mvnw spring-boot:run` | Chạy server (profile `local`, H2, không cần Postgres) |
| `./mvnw spotless:apply` | Tự format code theo Palantir Java Format |
| `./mvnw clean verify` | Build + test + `spotless:check` (CI chạy lệnh này) |
| `./mvnw test` | Chỉ chạy test |

CI gắn `spotless:check` vào phase `verify`, nên **chạy `spotless:apply` trước khi commit**,
không thì build đỏ. IntelliJ có plugin *Palantir Java Format* để format tự động khi lưu.

Quy ước dành cho AI coding agent (và cho người) ở [AGENTS.md](AGENTS.md).

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
Mật khẩu có ít nhất 8 ký tự và tối đa 72 byte UTF-8.
Rule dùng chung nằm ở `PasswordPolicy`; validator, thông báo lỗi và schema Swagger dùng cùng các giá trị này.
Sau khi đổi rule, build và khởi động lại BE rồi tải lại Swagger để nhận schema mới.
Sau khi Admin đã được tạo, đặt `BOOTSTRAP_ADMIN_ENABLED=false`.
Bootstrap giữ nguyên tài khoản Admin đã tồn tại, không đặt lại mật khẩu mỗi lần chạy.

### Chạy với H2

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
```

H2 lưu dữ liệu ở `.local-data/`. Thiết lập biến bootstrap ở trên nếu cần Admin local.
Nếu profile local không có khóa JWT được cấu hình, ứng dụng sinh khóa tạm mỗi lần chạy.

## API

Đăng nhập là API xác thực công khai. Tài khoản nội bộ do Admin tạo và cấp role.
Các request còn lại cần header `Authorization: Bearer <accessToken>`.

### Thử API bằng Swagger UI

Chạy BE bằng `.\mvnw.cmd spring-boot:run`, sau đó mở
[Swagger UI](http://localhost:8080/swagger-ui/index.html).
[OpenAPI JSON](http://localhost:8080/v3/api-docs) và
[OpenAPI YAML](http://localhost:8080/v3/api-docs.yaml) cũng có sẵn.
Nếu đổi `SERVER_PORT`, thay cổng trong các URL tương ứng.

1. Mở nhóm **Authentication**, chọn `POST /api/v1/auth/login`, bấm **Try it out**.
   Điền email và mật khẩu thật của tài khoản rồi bấm **Execute**. Mật khẩu mẫu trong Swagger
   chỉ minh họa cấu trúc request; không phải mật khẩu Admin.
2. Copy riêng giá trị `accessToken` trong response.
3. Bấm **Authorize**, dán token vào ô **bearerAuth** rồi bấm **Authorize** và **Close**.
   Không thêm tiền tố `Bearer`; Swagger tự gắn header.
4. Thử `GET /api/v1/auth/me`. Nếu dùng tài khoản Admin, có thể thử nhóm **Users** và **Roles**.
5. Khi đổi người dùng, đăng xuất token cũ trong **Authorize** rồi dán token mới.
   JWT không được lưu qua lần tải lại trang Swagger.

Admin dùng `POST /api/v1/users` để tạo tài khoản kèm mật khẩu và role, rồi cung cấp
thông tin đăng nhập cho người dùng. Tài khoản được tạo ở trạng thái `ACTIVE`, dùng được ngay.
Sau logout, token cũ sẽ bị từ chối ở API cần xác thực; xóa token đó khỏi **Authorize**
hoặc đăng nhập để lấy token mới.

Swagger mô tả API và gửi request thật; việc kiểm tra quyền vẫn do Spring Security thực thi.
Đặt `API_DOCS_ENABLED=false` để tắt cả Swagger UI lẫn tài liệu OpenAPI khi cần.

| Method | Endpoint | Quyền |
|---|---|---|
| POST | `/api/v1/auth/login` | Công khai |
| POST | `/api/v1/auth/logout` | Đã đăng nhập |
| GET | `/api/v1/auth/me` | Đã đăng nhập |
| PUT | `/api/v1/auth/password` | Đã đăng nhập; tự đổi mật khẩu |
| GET | `/api/v1/users?search=&status=ACTIVE&page=0&size=20` | Admin; `status` tùy chọn |
| GET | `/api/v1/users/{id}` | Admin |
| POST | `/api/v1/users` | Admin |
| PUT | `/api/v1/users/{id}` | Admin; sửa hồ sơ/trạng thái |
| PUT | `/api/v1/users/{id}/password` | Admin; đặt lại mật khẩu |
| PUT | `/api/v1/users/{id}/roles` | Admin |
| GET | `/api/v1/roles` | Admin |

`PUT /users/{id}` cập nhật email, fullName và trường `status` tùy chọn. Bỏ `status` để giữ
trạng thái hiện tại. `PUT /users/{id}/password` chỉ nhận `password`, mã hóa trước khi lưu
và thu hồi mọi phiên của tài khoản. `PUT /users/{id}/roles` thay thế toàn bộ tập role,
cần ít nhất một role. Không thể bỏ role Admin hoặc khóa Admin đang hoạt động cuối cùng.

### Người dùng tự đổi mật khẩu

Mọi role, bao gồm Admin, được gọi `PUT /api/v1/auth/password` với JWT của chính mình:

```json
{
  "currentPassword": "Your-Current-Password!",
  "newPassword": "Your-New-Password!"
}
```

BE xác định tài khoản từ JWT, không nhận ID/email của tài khoản khác. Mật khẩu hiện tại
phải đúng; mật khẩu mới có ít nhất 8 ký tự và tối đa 72 byte UTF-8. Thành công trả HTTP 204,
mật khẩu được lưu bằng BCrypt và mọi phiên của tài khoản bị thu hồi, kể cả phiên đang dùng.
Đăng nhập lại bằng mật khẩu mới rồi cập nhật token trong Swagger **Authorize**.
Mật khẩu hiện tại sai trả HTTP 400 `CURRENT_PASSWORD_INCORRECT`; mật khẩu và phiên được giữ nguyên.
Nếu quên mật khẩu, nhờ Admin đặt lại qua `PUT /api/v1/users/{id}/password`.

### Admin cung cấp tài khoản nội bộ

1. Admin đăng nhập và xem các role tại `GET /api/v1/roles`.
2. Admin tạo tài khoản bằng `POST /api/v1/users`, nhập email, fullName, password và roles.
3. BE lưu mật khẩu bằng BCrypt và cấp role ngay. Response không chứa mật khẩu hay password hash.
4. Admin cung cấp email/mật khẩu cho người dùng qua kênh của nhóm. BE chưa có chức năng tự gửi thông tin này.
5. Người dùng đăng nhập bằng thông tin được cung cấp. Không có bước tự đăng ký hoặc duyệt tài khoản.

Ví dụ Admin tạo tài khoản:

```json
{
  "email": "operator@example.test",
  "fullName": "Drone Operator",
  "password": "Your-Own-Strong-Password!",
  "roles": ["OPERATOR"]
}
```

`ACTIVE` cho phép đăng nhập; `DISABLED` khóa đăng nhập và thu hồi các phiên hiện có.
Đăng nhập tài khoản bị khóa với mật khẩu đúng trả HTTP 403 `ACCOUNT_DISABLED`; mật khẩu
sai vẫn trả HTTP 401 `INVALID_CREDENTIALS`. Đặt lại mật khẩu hoặc gán role không tự kích hoạt tài khoản.
Admin có thể gán role, đặt lại mật khẩu rồi gọi API sửa tài khoản với `status=ACTIVE`.
Tài khoản chưa có role không thể kích hoạt.

Migration `internal_accounts_only` giữ nguyên dữ liệu và lịch sử Flyway. Tài khoản cũ
`PENDING`/`REJECTED` chuyển thành `DISABLED` và các phiên của chúng bị thu hồi. Tài khoản
`ACTIVE` giữ nguyên mật khẩu, role và phiên. Các cột thông tin đăng ký/duyệt cũ được giữ
trong DB để bảo toàn lịch sử, nhưng không còn được đọc/ghi hay trả ra API.

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

Các test mặc định dùng H2 trong bộ nhớ và tắt việc đọc `.env`. Kiểm tra gồm quyền của
cả sáu role, Admin tạo tài khoản kèm role để đăng nhập ngay, chặn các route đăng ký/duyệt
đã gỡ, email trùng, validation mật khẩu 8 ký tự và giới hạn 72 byte UTF-8, JWT sai/hết hạn,
logout/tự đổi mật khẩu/reset mật khẩu/khóa tài khoản thu hồi phiên, đổi role trên JWT đã cấp, và bảo vệ
Admin đang hoạt động cuối cùng khi có hai yêu cầu đồng thời.

Test migration nâng cấp từ schema cũ giữ tài khoản, role, mật khẩu và dữ liệu duyệt cũ,
chuyển tài khoản chưa được cấp phép sang DISABLED, thu hồi đúng các phiên của chúng và
kiểm tra Flyway có thể validate/chạy lại mà không áp dụng migration trùng.
Các test Swagger kiểm tra UI, JSON/YAML, JWT Bearer, schema tạo tài khoản/role/mật khẩu,
và việc gỡ toàn bộ endpoint/schema đăng ký hoặc duyệt.

Có thể chạy các test API trên PostgreSQL bằng các tham số dưới đây; test nâng cấp H2
vẫn chạy riêng. Dùng database kiểm thử riêng vì các test xóa tài khoản và phiên trước
mỗi case. Database cần role `fivebros_backend`; tài khoản migration cần quyền DDL,
tài khoản JDBC cần quyền đọc/ghi các bảng auth và được RLS cho phép.

```powershell
.\mvnw.cmd test `
  "-Dspring.datasource.url=$env:FIVEBROS_TEST_DB_URL" `
  "-Dspring.datasource.username=$env:FIVEBROS_TEST_DB_USERNAME" `
  "-Dspring.datasource.password=$env:FIVEBROS_TEST_DB_PASSWORD" `
  "-Dspring.datasource.driver-class-name=org.postgresql.Driver" `
  "-Dspring.flyway.user=$env:FIVEBROS_TEST_MIGRATION_USERNAME" `
  "-Dspring.flyway.password=$env:FIVEBROS_TEST_MIGRATION_PASSWORD"
```
