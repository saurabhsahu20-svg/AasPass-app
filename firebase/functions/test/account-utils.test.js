'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { recentlyAuthenticated } = require('../account-utils');
test('destructive deletion requires a recently authenticated session', () => {
  const now = 10_000_000;
  assert.equal(recentlyAuthenticated((now - 60_000) / 1000, now), true);
  assert.equal(recentlyAuthenticated((now - 301_000) / 1000, now), false);
  assert.equal(recentlyAuthenticated((now + 1_000) / 1000, now), false);
  assert.equal(recentlyAuthenticated(null, now), false);
});
