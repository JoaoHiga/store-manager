-- NubeFacT's sunatDescription (the rejection reason on a non-accepted document) can exceed 1000
-- characters in practice, confirmed against the real sandbox — the old VARCHAR(1000) bound made
-- that INSERT fail uncaught, breaking sale recording entirely. TEXT has no length limit, so no
-- truncation is needed and no information about SUNAT's rejection is ever lost.
ALTER TABLE vouchers ALTER COLUMN rejection_reason TYPE TEXT;
