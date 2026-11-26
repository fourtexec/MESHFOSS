import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateWeightedAverage,
  extractTokenFromText,
  getLessonCountdown,
  looksLikeToken,
  marksNeededForTarget,
  parseCookieJson,
  sanitizeNavLayout,
} from './meshCore';

const SAMPLE_JWT = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3OCIsInJvbGUiOiJzdHVkZW50In0.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';

test('looksLikeToken validates JWT and opaque tokens and rejects short strings', () => {
  assert.equal(looksLikeToken(SAMPLE_JWT), true);
  assert.equal(looksLikeToken('short'), false);
  assert.equal(looksLikeToken('has spaces inside token 1234567890'), false);
});

test('extractTokenFromText parses raw JWT, nested JSON, and cookie arrays', () => {
  assert.equal(extractTokenFromText(SAMPLE_JWT), SAMPLE_JWT);
  assert.equal(extractTokenFromText(JSON.stringify({ session: { auth_token: SAMPLE_JWT } })), SAMPLE_JWT);
  assert.equal(
    extractTokenFromText(JSON.stringify([{ name: 'aupd_token', value: SAMPLE_JWT }])),
    SAMPLE_JWT
  );
});

test('parseCookieJson validates empty/invalid input and extracts mos_cookies.json', () => {
  const empty = parseCookieJson('   ');
  assert.equal(empty.ok, false);
  assert.ok(empty.error);

  const valid = parseCookieJson(
    JSON.stringify([
      { name: 'aupd_token', value: SAMPLE_JWT },
      { name: 'session_id', value: 'abc123' },
    ])
  );
  assert.equal(valid.ok, true);
  assert.equal(valid.token, SAMPLE_JWT);
  assert.equal(valid.cookieCount, 2);
  assert.ok(valid.cookieHeader.includes('session_id=abc123'));
});

test('calculateWeightedAverage and marksNeededForTarget compute exact MЭШ weighted GPA', () => {
  const marks = [
    { value: 5, weight: 2 },
    { value: 4, weight: 1 },
    { value: 4, weight: 1 },
  ];
  assert.equal(calculateWeightedAverage(marks), 4.5);
  assert.equal(marksNeededForTarget(marks, 4.5, 5), 0);
  // (18 + 5n) / (4 + n) >= 4.6 => 18 + 5n >= 18.4 + 4.6n => 0.4n >= 0.4 => n = 1
  assert.equal(marksNeededForTarget(marks, 4.6, 5), 1);
  assert.equal(marksNeededForTarget(marks, 5.0, 5), null);
});

test('sanitizeNavLayout enforces max 4 bottom tabs and no duplicates', () => {
  const layout = sanitizeNavLayout(
    ['schedule', 'marks', 'marks', 'homeworks', 'ranking', 'food'],
    ['settings', 'schedule']
  );
  assert.deepEqual(layout.bottom, ['schedule', 'marks', 'homeworks', 'ranking']);
  assert.equal(layout.more.includes('food'), true);
  assert.equal(layout.more.includes('schedule'), false);
  assert.equal(layout.bottom.length + layout.more.length, 8);
});

test('getLessonCountdown detects in-progress, upcoming, and finished lessons', () => {
  const lessons = [
    { id: '1', subject: 'Алгебра', startMinutes: 510, endMinutes: 555, room: '304' },
    { id: '2', subject: 'Физика', startMinutes: 565, endMinutes: 610, room: '215' },
  ];

  const active = getLessonCountdown(lessons, 525);
  assert.equal(active.state, 'in_progress');
  assert.equal(active.lesson?.subject, 'Алгебра');
  assert.equal(active.minutesRemaining, 30);

  const upcoming = getLessonCountdown(lessons, 560);
  assert.equal(upcoming.state, 'upcoming');
  assert.equal(upcoming.lesson?.subject, 'Физика');
  assert.equal(upcoming.minutesRemaining, 5);

  const done = getLessonCountdown(lessons, 700);
  assert.equal(done.state, 'finished');
});
