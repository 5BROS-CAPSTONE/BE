# 5BROS Backend

Backend của **Drone Mission Management & Decision Support System**.
Đợt triển khai đầu tiên gồm AUTH-01, AUTH-02 và AUTH-03 theo bản phân tích hiện tại,
kèm luồng tự đăng ký và Admin duyệt tài khoản đã thống nhất bổ sung.

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
| AUTH-01 | Đăng nhập bằng email/mật khẩu, JWT có thời hạn, đăng xuất thu hồi phiên hiện tại, xem hồ sơ bản thân; tài khoản chờ duyệt chưa có quyền nghiệp vụ |
| AUTH-02 | Admin tạo, xem, tìm kiếm, phân trang và sửa tài khoản; có thể đặt lại mật khẩu |
| AUTH-03 | Sáu role cố định, gán/thay thế role, kiểm tra quyền trên API |
| Đăng ký & duyệt | Người dùng tự đăng ký với role mong muốn; Admin xem hàng chờ, duyệt và cấp role chính thức hoặc từ chối với lý do |

Role cố định: `ADMIN`, `MANAGER`, `EXPERT`, `OPERATOR`, `TECHNICIAN`, `ANALYST`.
Một tài khoản có thể có nhiều role; đây là lựa chọn triển khai cho RBAC hiện tại.

Trong phạm vi ba chức năng này, Admin có `user:read`, `user:create`, `user:update`,
`user:assign-roles`, `user:review`. Mọi người dùng đã đăng nhập được xem hồ sơ bản thân và đăng xuất.
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

Đăng ký, đăng nhập và danh sách role có thể yêu cầu khi đăng ký là API công khai.
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

Đăng ký, đăng nhập và danh sách role khi đăng ký có thể thử mà không cần **Authorize**.
Để thử duyệt tài khoản, đăng ký với email mới, dùng JWT Admin gọi
`GET /api/v1/users?status=PENDING`, rồi `POST /api/v1/users/{id}/approve` hoặc `/reject`.
JWT của người dùng PENDING chỉ được xem hồ sơ và đăng xuất, chưa gọi được API nghiệp vụ.
Sau logout, token cũ sẽ bị từ chối ở API cần xác thực; xóa token đó khỏi **Authorize**
hoặc đăng nhập để lấy token mới.

Swagger mô tả API và gửi request thật; việc kiểm tra quyền vẫn do Spring Security thực thi.
Đặt `API_DOCS_ENABLED=false` để tắt cả Swagger UI lẫn tài liệu OpenAPI khi cần.

| Method | Endpoint | Quyền |
|---|---|---|
| POST | `/api/v1/auth/login` | Công khai |
| POST | `/api/v1/auth/register` | Công khai |
| GET | `/api/v1/auth/registration-roles` | Công khai; chỉ trả năm role nghiệp vụ, không có ADMIN |
| POST | `/api/v1/auth/logout` | Đã đăng nhập |
| GET | `/api/v1/auth/me` | Đã đăng nhập |
| GET | `/api/v1/users?search=&status=PENDING&page=0&size=20` | Admin; `status` tùy chọn |
| GET | `/api/v1/users/{id}` | Admin |
| POST | `/api/v1/users` | Admin |
| PUT | `/api/v1/users/{id}` | Admin |
| PUT | `/api/v1/users/{id}/roles` | Admin |
| POST | `/api/v1/users/{id}/approve` | Admin |
| POST | `/api/v1/users/{id}/reject` | Admin |
| GET | `/api/v1/roles` | Admin |

`PUT /users/{id}` cập nhật email và fullName. Trường `password` là tùy chọn;
bỏ trường này hoặc gửi `null` để giữ mật khẩu hiện tại. Đặt lại mật khẩu thu hồi mọi
phiên của tài khoản. `PUT /users/{id}/roles` thay thế toàn bộ tập role và cần ít nhất một role.
Không thể bỏ role Admin của Admin cuối cùng.

### Tự đăng ký và duyệt tài khoản

1. Người dùng lấy các lựa chọn ở `GET /auth/registration-roles`, điền email, fullName,
   password và một `requestedRole`, rồi gửi `POST /auth/register`.
2. BE tạo tài khoản `PENDING`, mã hóa mật khẩu bằng BCrypt và lưu role mong muốn riêng.
   Tập `roles` chính thức rỗng; response HTTP 201 không cấp JWT hay phiên đăng nhập.
3. Admin xem `GET /users?status=PENDING`, mở chi tiết tài khoản và quyết định:
   - `POST /users/{id}/approve` với `roles` không rỗng: cấp role chính thức, chuyển thành `ACTIVE`.
     Role được cấp có thể khác role mong muốn và có thể gồm nhiều role nghiệp vụ.
   - `POST /users/{id}/reject` với `reason` bắt buộc, tối đa 500 ký tự: chuyển thành `REJECTED`.
4. Tài khoản `PENDING` vẫn đăng nhập được và nhận JWT; response có `user.status=PENDING`
   và `user.roles=[]` để giao diện hiển thị trạng thái chờ duyệt. Phiên này chỉ được xem hồ sơ
   qua `GET /auth/me` và đăng xuất; API nghiệp vụ trả HTTP 403.
5. Khi Admin duyệt, tài khoản chuyển thành `ACTIVE` và quyền có hiệu lực trên JWT đã cấp
   ở request tiếp theo; người dùng không cần đăng nhập lại. Khi bị từ chối, mọi phiên bị thu hồi
   và đăng nhập trả HTTP 403 `ACCOUNT_REJECTED` nếu mật khẩu đúng.
   Mật khẩu sai vẫn trả HTTP 401 `INVALID_CREDENTIALS` ở mọi trạng thái.

Không được yêu cầu `ADMIN` khi tự đăng ký hoặc cấp `ADMIN` qua endpoint duyệt.
Các trường client gửi thêm như `roles`, `status` hoặc `reviewedBy` không cấp quyền.
Admin phải dùng endpoint duyệt; `PUT /users/{id}/roles` chỉ nhận tài khoản `ACTIVE`.
Mỗi đăng ký chỉ được quyết định một lần, kể cả khi có hai yêu cầu đồng thời.
BE lưu `reviewedBy`, `reviewedAt` và lý do từ chối để truy vết quyết định.
Trạng thái `REJECTED` là quyết định cuối cùng của luồng này; email vẫn được giữ và không thể
đăng ký lại để ghi đè hồ sơ. Luồng gửi email thông báo và xác minh email chưa được triển khai.

Ví dụ đăng ký:

```json
{
  "email": "operator@example.test",
  "fullName": "Drone Operator",
  "password": "Your-Own-Strong-Password!",
  "requestedRole": "OPERATOR"
}
```

Body duyệt: `{"roles":["OPERATOR"]}`. Body từ chối: `{"reason":"Không xác minh được thông tin"}`.

Migration mới giữ tài khoản cũ ở trạng thái `ACTIVE`, giữ nguyên mật khẩu, role và phiên đăng nhập.
Admin bootstrap và tài khoản được Admin tạo trực tiếp qua `POST /users` cũng là `ACTIVE`.
Migration H2 chuyển bảng bằng cách tạo bảng đích, sao chép dữ liệu và tạo lại đầy đủ ràng buộc;
H2 không hỗ trợ `ALTER TABLE ... SET SCHEMA` như PostgreSQL.

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

Bộ 53 test mặc định dùng H2 trong bộ nhớ, tắt việc đọc `.env`, không kết nối hoặc xóa dữ liệu
Supabase. Các kiểm tra bao gồm quyền của cả sáu role, tạo/sửa tài khoản, email trùng,
validation, JWT sai/hết hạn, thu hồi phiên, đổi quyền trên JWT đã cấp, reset mật khẩu
và bảo vệ Admin cuối cùng khi có hai yêu cầu đổi role đồng thời.

Các test đăng ký kiểm tra cả năm role nghiệp vụ, validation, email trùng, bảo vệ trước
client gửi quyền/trạng thái giả, hàng chờ có phân trang, cho phép tài khoản chờ duyệt đăng nhập
và xem hồ sơ/đăng xuất nhưng chặn API nghiệp vụ, quyền được cập nhật trên phiên đang dùng
sau khi duyệt, duyệt với role khác đề nghị, từ chối và thu hồi mọi phiên, bảo vệ API duyệt ở tất cả role khác Admin,
chặn gán role để bỏ qua duyệt, và chỉ lưu một quyết định khi duyệt/từ chối đồng thời.
Test migration nâng cấp từ schema cũ kiểm tra dữ liệu tài khoản, role, phiên còn nguyên,
trạng thái mặc định, ràng buộc role/trạng thái và khả năng chạy lại Flyway không áp dụng trùng.

Trước khi bổ sung luồng tự đăng ký, đã kiểm tra JAR kết nối Supabase CAPSTONE, chạy migration, khởi tạo Admin, login,
xem/sửa tài khoản, cập nhật role và logout. JWT đã logout bị từ chối với HTTP 401.

Các test Swagger kiểm tra HTML, CSS, JavaScript và cấu hình UI; tài liệu JSON/YAML;
JWT Bearer và API công khai; ẩn tham số JWT nội bộ; ví dụ/validation/role trong schema;
đồng thời xác nhận tài liệu công khai không bỏ qua phân quyền API.

Các test mật khẩu kiểm tra 8 ký tự được chấp nhận, 7 ký tự bị từ chối ở đăng ký/tạo tài khoản/reset,
đồng bộ schema Swagger và ranh giới ký tự Unicode/72 byte UTF-8.

Có thể chạy lại 52 test API trên PostgreSQL bằng các tham số dưới đây; test nâng cấp H2
vẫn chạy riêng trong cùng bộ test. Dùng database kiểm thử riêng: các test xóa tài khoản,
role và phiên trong database được truyền vào trước mỗi case. Database cần được thiết lập
role `fivebros_backend` theo bước cấu hình ở trên; tài khoản chạy migration cần quyền DDL,
tài khoản JDBC của BE cần quyền đọc/ghi các bảng auth và được RLS cho phép.

```powershell
.\mvnw.cmd test `
  "-Dspring.datasource.url=$env:FIVEBROS_TEST_DB_URL" `
  "-Dspring.datasource.username=$env:FIVEBROS_TEST_DB_USERNAME" `
  "-Dspring.datasource.password=$env:FIVEBROS_TEST_DB_PASSWORD" `
  "-Dspring.datasource.driver-class-name=org.postgresql.Driver" `
  "-Dspring.flyway.user=$env:FIVEBROS_TEST_MIGRATION_USERNAME" `
  "-Dspring.flyway.password=$env:FIVEBROS_TEST_MIGRATION_PASSWORD"
```

Luồng đăng ký/duyệt đã được kiểm tra trên H2 và PostgreSQL 17.11 local, bao gồm HTTP thật
qua JAR, nâng cấp schema cũ và quyền truy cập bằng role `fivebros_backend` dưới RLS.
Migration đăng ký chưa được áp dụng trực tiếp lên Supabase CAPSTONE trong đợt kiểm thử này.
