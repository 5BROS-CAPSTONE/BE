# Hướng dẫn cho AI agent – 5BROS Backend

File này dành cho mọi AI coding agent làm việc trong repo (Claude Code, Cursor, Copilot, Codex…).
Người cũng nên đọc. Kiến trúc và cách chạy ở [README.md](README.md).

Nếu một yêu cầu của người dùng mâu thuẫn với file này, **nói ra chỗ mâu thuẫn trước khi làm**, đừng im lặng làm theo.

## Bắt buộc trước khi báo "xong"

```bash
./mvnw spotless:apply                      # tự format, chạy TRƯỚC
./mvnw --batch-mode clean verify           # build + 87 test + spotless:check
```

`verify` đã bao gồm `spotless:check`, nên **bỏ bước `spotless:apply` là CI đỏ chắc chắn**.
Đừng báo hoàn thành khi chưa chạy `verify`. Nếu fail mà không sửa được thì dán output ra, đừng giấu.

Trên Windows dùng `.\mvnw.cmd` thay cho `./mvnw`.

## Bật hook kiểm tra tại máy (làm một lần cho mỗi máy)

```bash
git config core.hooksPath .githooks
```

Hook `pre-commit` sẽ chặn commit nhầm lên `main` và chặn commit code Java chưa format.
Nó là lưới an toàn, không thay cho việc tự chạy `verify` ở trên.

## Tuyệt đối không

- **Không sửa trực tiếp trên `main`.** Làm trên nhánh mang tên thành viên (`phat`, `duy`). `main` có ruleset chặn push thẳng.
- **Không commit `.env`**, mật khẩu, khoá JWT, chuỗi kết nối Supabase, hay dán chúng vào code, test, ảnh chụp, file `.http`. Cấu hình mới thì thêm vào `.env.example` với **giá trị trống**.
- **Không trả entity thẳng từ controller.** Luôn qua DTO — entity chứa password hash.
- **Không chạy code ở máy khi đang nối Supabase.** Supabase của nhóm giờ là **production**.
  Lập trình thì dùng profile `local` (H2 trên máy). Chỉ nối Supabase khi cần xem dữ liệu thật, và chỉ đọc.
- **Không sửa file migration Flyway đã chạy.** Đổi schema thì **thêm file migration mới**; sửa file cũ làm hỏng checksum và vỡ database của cả nhóm.
- **Không đổi `ddl-auto`.** Đang là `validate` và phải giữ nguyên; schema do Flyway quản.
- **Không tự nâng version Spring Boot hay dependency khác** khi không được yêu cầu. Dependabot đã lo.
- **Không tắt test** hay thêm `@Disabled` để build qua. Test đỏ là tín hiệu.
- **Không nới quyền** trong `@PreAuthorize` để code chạy được. Thiếu quyền thì hỏi, đừng mở rộng.
- **Không sửa repo FE** từ trong repo này.

## Quy ước code

- **Java 21**, indent **4 space**, giới hạn **120 cột** — Spotless + Palantir Java Format tự lo, nên cứ chạy `spotless:apply` thay vì căn tay.
- **Thứ tự import**: `java`, `javax`, `jakarta`, thư viện bên thứ ba, rồi `com.fivebros`. Spotless tự sắp.
- **Phân lớp**: controller nhận request và trả response → service xử lý nghiệp vụ trong transaction → repository truy cập database. Không để logic nghiệp vụ trong controller, không gọi repository thẳng từ controller.
- **Mỗi module một package** như `account`, `auth`, `security`, `fleet`, với `dto/` riêng.
- **Lỗi nghiệp vụ** ném `ApiException` với `HttpStatus` phù hợp, không trả message thô ra client. `include-message` và `include-stacktrace` đang tắt có chủ đích.
- **Không hard-code thời gian**: dùng `Clock` đã cấu hình trong `config/` để test còn kiểm soát được.
- **Commit message** dạng `loại(phạm vi): mô tả`, ví dụ `feat(fleet): API tạo drone`.

## Viết test

Mỗi endpoint mới hoặc thay đổi luật nghiệp vụ phải kèm test. Test chạy trên **H2 in-memory** với profile `test`, không cần Supabase.

- **Integration test qua Spring Security** là mẫu chính — xem `src/test/java/com/fivebros/backend/fleet/FleetIntegrationTest.java`.
- **Phải test cả nhánh bị từ chối**, không chỉ nhánh thành công: sai quyền trả 403, chưa đăng nhập trả 401, dữ liệu sai trả 400. Đây là phần dễ bỏ sót nhất và cũng là phần quan trọng nhất.
- Migration mới thì thêm test xác nhận nó chạy được trên H2, xem `InternalAccountsMigrationTest`.

**Sửa bug thì viết test tái hiện bug trước**, rồi mới sửa.

## Việc nào phải hỏi người, không tự quyết

- Thêm hoặc sửa migration Flyway.
- Thêm dependency mới vào `pom.xml`.
- Đổi cấu hình CI, ruleset, Spotless.
- Đổi `SecurityConfig`, danh sách role, hay bảng permission trong `security/`.
- Đổi shape response của API đang có — FE đang gọi theo shape cũ.
- Xoá hoặc sửa test đang có.

## Quy trình đưa code lên

```bash
git switch phat                 # hoặc nhánh của bạn; KHÔNG phải main
git merge origin/main
# ... sửa code, viết test ...
./mvnw spotless:apply
./mvnw --batch-mode clean verify
git add -A
git commit -m "feat(...): ..."
git push
```

Rồi mở Pull Request vào `main`.

## Môi trường

- Cần **Java 21**. Không cần cài PostgreSQL hay Docker để chạy local.
- Profile mặc định là `local`, dùng H2 file `./.local-data/fivebros` ở chế độ PostgreSQL. Chạy: `./mvnw spring-boot:run`.
- Supabase của nhóm là **production**, không phải sân tập. Cách deploy ở [DEPLOY.md](DEPLOY.md).
- API mặc định ở `http://localhost:8080/api/v1`, Swagger UI ở `/swagger-ui.html`.
