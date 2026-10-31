-- BPMN "S5 - Người dùng & Phân quyền / Phân quyền" quy định Admin chọn đúng MỘT role cho
-- mỗi tài khoản. Khoá chính (user_id, role) vẫn cho phép một người nhiều dòng, nên thêm
-- ràng buộc duy nhất trên user_id làm chốt chặn cuối ở database, phòng khi có đường ghi
-- nào lọt qua tầng ứng dụng.
ALTER TABLE public.user_roles
    ADD CONSTRAINT uq_user_roles_one_per_user UNIQUE (user_id);
