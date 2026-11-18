'use strict';
function recentlyAuthenticated(authTimeSeconds, nowMs = Date.now()) {
  const time = Number(authTimeSeconds) * 1000;
  return Number.isFinite(time) && time <= nowMs && nowMs - time <= 5 * 60 * 1000;
}
module.exports = { recentlyAuthenticated };
