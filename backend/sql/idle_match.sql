-- Which idle resources can take which open tasks? (same data the Python scorer uses)
-- Run with EXPLAIN (Postgres) / EXPLAIN QUERY PLAN (SQLite) to see ix_tasks_status_site and ix_resources_status used.
SELECT t.id        AS task_id,
       t.title,
       r.id        AS resource_id,
       r.name,
       CASE WHEN r.zone = t.zone THEN 5 ELSE 0 END AS zone_bonus
FROM tasks t
JOIN resources r
  ON r.status = 'idle'
 AND (',' || r.skills || ',') LIKE '%,' || t.required_skill || ',%'
WHERE t.status = 'open'
  AND t.site_id = 1
ORDER BY t.priority DESC, t.created_at, zone_bonus DESC
LIMIT 20;
