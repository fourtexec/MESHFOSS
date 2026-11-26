import test from 'node:test';
import assert from 'node:assert/strict';
import {
  circleMap,
  computeChromaticScales,
  computeRefractionVector,
  computeSpecularRim,
  DEFAULT_LIQUID_OPTICS,
  gradSdRoundedRect,
  sdRoundedRect,
} from './liquidGlassMath';

test('sdRoundedRect is negative inside, zero on straight edge, positive outside', () => {
  assert.ok(sdRoundedRect(0, 0, 50, 30, 12) < 0);
  assert.equal(sdRoundedRect(50, 0, 50, 30, 12), 0);
  assert.equal(sdRoundedRect(0, 30, 50, 30, 12), 0);
  assert.ok(sdRoundedRect(60, 0, 50, 30, 12) > 0);
});

test('gradSdRoundedRect returns outward unit normal', () => {
  const right = gradSdRoundedRect(49, 0, 50, 30, 10);
  assert.equal(right.x, 1);
  assert.equal(right.y, 0);

  const corner = gradSdRoundedRect(46, 26, 50, 30, 10);
  const mag = Math.hypot(corner.x, corner.y);
  assert.ok(Math.abs(mag - 1) < 1e-6);
  assert.ok(corner.x > 0 && corner.y > 0);
});

test('circleMap bends steeply near edge x->1 and stays 0 at x=0', () => {
  assert.equal(circleMap(0), 0);
  assert.equal(circleMap(1), 1);
  assert.ok(circleMap(0.9) > 0.5);
  assert.ok(circleMap(0.2) < 0.05);
});

test('computeRefractionVector is zero in flat interior and bends inward near bezel rim', () => {
  const center = computeRefractionVector(0, 0, 200, 80, 24, DEFAULT_LIQUID_OPTICS);
  assert.equal(center.x, 0);
  assert.equal(center.y, 0);

  const nearRightEdge = computeRefractionVector(97, 0, 200, 80, 24, DEFAULT_LIQUID_OPTICS);
  assert.ok(nearRightEdge.x < -10, 'right bezel should refract light inward (negative x)');
  assert.ok(Math.abs(nearRightEdge.y) < 1e-4);
});

test('computeChromaticScales disperses red > green > blue wavelengths', () => {
  const scales = computeChromaticScales(24, 1.0);
  assert.ok(scales.red > scales.green);
  assert.ok(scales.green > scales.blue);
});

test('computeSpecularRim peaks along light angle and drops perpendicular', () => {
  const aligned = computeSpecularRim({ x: Math.SQRT1_2, y: -Math.SQRT1_2 }, -Math.PI / 4, 2);
  const perp = computeSpecularRim({ x: Math.SQRT1_2, y: Math.SQRT1_2 }, -Math.PI / 4, 2);
  assert.ok(aligned > 0.99);
  assert.ok(perp < 0.001);
});
