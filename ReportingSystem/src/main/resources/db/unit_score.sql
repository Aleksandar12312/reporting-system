-- Problematic-unit scoring (MySQL 8) - prototype of the algorithm, plus a check against the answer key.
-- Run it in MySQL Workbench / DBeaver after the application has seeded the data.
--
-- Algorithm
--   1. Count only real faults: events whose SOURCE has category FAULT in the table event_source
--      (jams, payout errors, failures). Warnings, state changes and normal operations are ignored.
--   2. Rate = faults per day inside the window (last window_days, or since the unit was created if younger).
--   3. Compare with the model: ratio = unit rate / median rate of units of the SAME model.
--   4. Every unit is judged, new ones too: a new unit with many faults IS a problem.
--      The only protection against bad luck is min_faults: with fewer faults than that the unit is never flagged.
--      baseline_min_days only decides which units are used to compute the model median (a stable reference).
--   5. Tiers: ratio >= critical_ratio -> CRITICAL, ratio >= watch_ratio -> WATCH, otherwise OK.
--   6. Part or whole machine? For WATCH / CRITICAL units with at least part_min_faults faults, look at WHICH
--      component (PLACEHOLDER) the faults come from. If one component has at least part_share of all the unit's
--      faults, the problem scope is PART (replace that component); otherwise GENERAL (the whole machine is bad).
--      v_unit_fault_breakdown shows the faults per component and fault name (SOURCE).
--
-- Change the numbers in the "params" block below and re-run this script (CREATE OR REPLACE VIEW).

USE mock_lincstation;

    -- pronadje generalo koja masina je problematicna
CREATE OR REPLACE VIEW v_unit_score AS
WITH params AS (
    SELECT 90   AS window_days,      -- how far back to look
           14   AS baseline_min_days,-- only units with at least this many days are used for the model median
           3    AS min_faults,       -- fewer faults than this -> never flagged (protects new units)
           2.0  AS watch_ratio,      -- x times worse than the model median -> WATCH
           4.0  AS critical_ratio,   -- x times worse than the model median -> CRITICAL
           8    AS part_min_faults,  -- fewer faults than this -> too few to say which part is the problem
           0.5  AS part_share,       -- one component with this share of all faults -> problem scope PART
           UTC_TIMESTAMP() AS now_utc
),
unit_window AS (
    SELECT u.id AS unit_id, u.serial_number, t.model_name,
           GREATEST(p.now_utc - INTERVAL p.window_days DAY, u.creation_date) AS win_start,
           p.now_utc
    FROM params p
    CROSS JOIN unit u
    JOIN unit_type t ON t.id = u.unit_type_id
),
unit_faults AS (
    SELECT w.unit_id, w.serial_number, w.model_name, w.win_start, w.now_utc,
           COUNT(e.id) AS faults
    FROM unit_window w
    LEFT JOIN event e
           ON e.unit_id = w.unit_id
          AND e.event_time >= w.win_start
          AND e.event_time <  w.now_utc
          AND e.source IN (SELECT fs.source FROM event_source fs WHERE fs.category = 'FAULT')
    GROUP BY w.unit_id, w.serial_number, w.model_name, w.win_start, w.now_utc
),
unit_rates AS (
    SELECT f.unit_id, f.serial_number, f.model_name, f.faults,
           TIMESTAMPDIFF(SECOND, f.win_start, f.now_utc) / 86400.0 AS exposure_days
    FROM unit_faults f
),
unit_rates2 AS (
    SELECT r.*,
           CASE WHEN r.exposure_days > 0 THEN r.faults / r.exposure_days ELSE 0 END AS rate_per_day
    FROM unit_rates r
),
eligible AS (   -- only units with enough history take part in the model median
    SELECT r.model_name, r.rate_per_day,
           ROW_NUMBER() OVER (PARTITION BY r.model_name ORDER BY r.rate_per_day, r.unit_id) AS rn,
           COUNT(*)     OVER (PARTITION BY r.model_name)                                    AS cnt
    FROM unit_rates2 r
    CROSS JOIN params p
    WHERE r.exposure_days >= p.baseline_min_days
),
model_median AS (   -- MySQL has no MEDIAN(): average of the middle one or two rows
    SELECT model_name, AVG(rate_per_day) AS median_rate
    FROM eligible
    WHERE rn IN (FLOOR((cnt + 1) / 2), FLOOR(cnt / 2) + 1)
    GROUP BY model_name
),
scored AS (
    SELECT r.*, m.median_rate,
           r.rate_per_day / NULLIF(m.median_rate, 0) AS ratio
    FROM unit_rates2 r
    LEFT JOIN model_median m ON m.model_name = r.model_name
),
tiered AS (
    SELECT s.*,
           CASE WHEN s.faults < p.min_faults      THEN 'OK'
                WHEN s.ratio IS NULL              THEN 'NO_BASELINE'   -- no unit of this model has enough history
                WHEN s.ratio >= p.critical_ratio  THEN 'CRITICAL'
                WHEN s.ratio >= p.watch_ratio     THEN 'WATCH'
                ELSE 'OK'
           END AS tier
    FROM scored s
    CROSS JOIN params p
),
part_faults AS (   -- faults per unit and component (placeholder) in the same window
    SELECT w.unit_id, e.placeholder, COUNT(*) AS part_faults
    FROM unit_window w
    JOIN event e
      ON e.unit_id = w.unit_id
     AND e.event_time >= w.win_start
     AND e.event_time <  w.now_utc
     AND e.source IN (SELECT fs.source FROM event_source fs WHERE fs.category = 'FAULT')
    GROUP BY w.unit_id, e.placeholder
),
top_part AS (      -- the component with the most faults, per unit
    SELECT pf.unit_id, pf.placeholder, pf.part_faults,
           ROW_NUMBER() OVER (PARTITION BY pf.unit_id ORDER BY pf.part_faults DESC, pf.placeholder) AS rn
    FROM part_faults pf
)
SELECT t.unit_id,
       t.serial_number,
       t.model_name,
       t.faults,
       ROUND(t.exposure_days, 1)  AS exposure_days,
       ROUND(t.rate_per_day, 3)   AS rate_per_day,
       ROUND(t.median_rate, 3)    AS model_median_rate,
       ROUND(t.ratio, 2)          AS ratio_to_model,
       t.tier,
       tp.placeholder             AS top_part,
       tp.part_faults             AS top_part_faults,
       ROUND(tp.part_faults / NULLIF(t.faults, 0), 2) AS top_part_share,
       CASE WHEN t.tier IN ('WATCH', 'CRITICAL') AND t.faults >= p.part_min_faults THEN
                 CASE WHEN tp.part_faults / t.faults >= p.part_share THEN 'PART' ELSE 'GENERAL' END
       END                        AS problem_scope   -- NULL = not flagged, or too few faults to say
FROM tiered t
CROSS JOIN params p
LEFT JOIN top_part tp ON tp.unit_id = t.unit_id AND tp.rn = 1;


-- Faults per unit, component (PLACEHOLDER) and fault name (SOURCE), same window as v_unit_score.
-- Keep window_days equal to the value in v_unit_score.
CREATE OR REPLACE VIEW v_unit_fault_breakdown AS
WITH params AS (
    SELECT 90 AS window_days, UTC_TIMESTAMP() AS now_utc
),
unit_window AS (
    SELECT u.id AS unit_id,
           GREATEST(p.now_utc - INTERVAL p.window_days DAY, u.creation_date) AS win_start,
           p.now_utc
    FROM params p
    CROSS JOIN unit u
),
counts AS (
    SELECT w.unit_id, e.placeholder, e.source, COUNT(*) AS faults
    FROM unit_window w
    JOIN event e
      ON e.unit_id = w.unit_id
     AND e.event_time >= w.win_start
     AND e.event_time <  w.now_utc
     AND e.source IN (SELECT fs.source FROM event_source fs WHERE fs.category = 'FAULT')
    GROUP BY w.unit_id, e.placeholder, e.source
)
SELECT c.unit_id,
       u.serial_number,
       c.placeholder AS part,
       c.source      AS fault,
       c.faults,
       ROUND(100 * c.faults / SUM(c.faults) OVER (PARTITION BY c.unit_id), 1) AS pct_of_unit_faults
FROM counts c
JOIN unit u ON u.id = c.unit_id;

-- 1) The result: all units, worst first
SELECT *
FROM v_unit_score
ORDER BY ratio_to_model DESC;


-- 2) Check against the answer key: what did the algorithm say for each planted profile?
SELECT p.profile,
       COUNT(*)                          AS units,
       SUM(s.tier = 'CRITICAL')          AS critical,
       SUM(s.tier = 'WATCH')             AS watch,
       SUM(s.tier = 'OK')                AS ok,
       SUM(s.tier = 'NO_BASELINE')       AS no_baseline
FROM v_unit_score s
JOIN mock_unit_profile p ON p.unit_id = s.unit_id
GROUP BY p.profile
ORDER BY FIELD(p.profile, 'BAD', 'BAD_ONE_COMPONENT', 'BORDERLINE', 'NORMAL', 'NEW_FEW_FAULTS', 'SILENT');
-- Expected with the default settings:
--   BAD, BAD_ONE_COMPONENT -> all CRITICAL      BORDERLINE -> mostly WATCH
--   NORMAL                 -> all OK            NEW_FEW_FAULTS -> usually OK; CRITICAL if it got 3+ faults in its few days
--   SILENT                 -> OK (nothing reported, so it looks healthy; there is no "no data" check)


-- 3) Only the mistakes: bad units that were not caught, and normal units that were flagged
--    (NEW_FEW_FAULTS is left out on purpose: if it has many faults, flagging it is correct)
SELECT p.profile, s.*
FROM v_unit_score s
JOIN mock_unit_profile p ON p.unit_id = s.unit_id
WHERE (p.profile IN ('BAD', 'BAD_ONE_COMPONENT') AND s.tier <> 'CRITICAL')
   OR (p.profile = 'NORMAL' AND s.tier IN ('CRITICAL', 'WATCH'))
ORDER BY s.ratio_to_model DESC;


-- 4) Flagged units: is it one part or the whole machine?
--    PART = one component causes at least part_share of the faults; GENERAL = faults are spread over many components.
SELECT unit_id, serial_number, model_name, tier, faults, top_part, top_part_faults, top_part_share, problem_scope
FROM v_unit_score
WHERE tier IN ('CRITICAL', 'WATCH')
ORDER BY ratio_to_model DESC;


-- 5) Breakdown for the flagged units: which component and which fault, most frequent first
SELECT b.*
FROM v_unit_fault_breakdown b
JOIN v_unit_score s ON s.unit_id = b.unit_id AND s.tier IN ('CRITICAL', 'WATCH')
ORDER BY b.unit_id, b.faults DESC;
-- One unit only:  SELECT * FROM v_unit_fault_breakdown WHERE unit_id = 12 ORDER BY faults DESC;


-- 6) Check against the answer key: did the part-or-machine decision match the planted profile?
SELECT p.profile,
       SUM(s.problem_scope = 'PART')    AS part,
       SUM(s.problem_scope = 'GENERAL') AS general,
       SUM(s.tier IN ('CRITICAL', 'WATCH') AND s.problem_scope IS NULL) AS flagged_too_few_faults
FROM v_unit_score s
JOIN mock_unit_profile p ON p.unit_id = s.unit_id
GROUP BY p.profile
ORDER BY FIELD(p.profile, 'BAD', 'BAD_ONE_COMPONENT', 'BORDERLINE', 'NORMAL', 'NEW_FEW_FAULTS', 'SILENT');
-- Expected: BAD and BORDERLINE -> GENERAL, BAD_ONE_COMPONENT -> PART.
