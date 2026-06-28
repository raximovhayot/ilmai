UPDATE rooms r
SET goal = p.goal,
    target_date = p.target_date,
    daily_study_minutes = p.daily_study_minutes
FROM profiles p
WHERE r.owner_id = p.user_id
  AND r.personal;

ALTER TABLE profiles DROP COLUMN goal;
ALTER TABLE profiles DROP COLUMN target_date;
ALTER TABLE profiles DROP COLUMN daily_study_minutes;
