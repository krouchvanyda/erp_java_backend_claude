-- Backfill User.avatar_url from the linked Employee.avatar_url.
--
-- Avatars are uploaded to the employee record (POST /employees/me/avatar),
-- but chat surfaces (MemberDto / UserDto) read User.avatar_url. New uploads
-- now mirror onto the user in EmployeeAvatarService; this one-time backfill
-- copies the avatars that already existed before that sync was added so
-- peers' photos appear without forcing everyone to re-upload.
UPDATE users u
SET avatar_url = e.avatar_url
FROM employees e
WHERE e.user_id = u.id
  AND e.avatar_url IS NOT NULL
  AND (u.avatar_url IS NULL OR u.avatar_url <> e.avatar_url);
