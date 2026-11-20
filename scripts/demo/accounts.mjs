import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { hospitalId } from './data.mjs';

/** Two fictional identities, real invitation/membership APIs. No account credentials leave memory. */
export async function verifyAccounts(admin, member, seedWardId) {
  await assert.rejects(member('GET', `/api/v1/wards/${seedWardId}/staff`), /HTTP 404/);
  const { data: ward } = await admin('POST', '/api/v1/wards', {
    hospital: { source: 'HIRA', externalId: hospitalId }, wardName: 'B6 가상 가입 검증 병동', timeZone: 'Asia/Seoul',
  }, { 'Idempotency-Key': randomUUID() });
  const root = `/api/v1/wards/${ward.wardId}`;
  const { data: invite } = await admin('POST', `${root}/invitations`, { role: 'WARD_MEMBER' });
  await member('POST', '/api/v1/invitations/redeem', { token: invite.token, hospitalJoinedOn: '2024-01' });
  const mine = (await member('GET', '/api/v1/wards')).data.items.find(w => w.wardId === ward.wardId);
  assert.deepEqual(mine.roles, ['WARD_MEMBER']);
  const profile = (await member('GET', `${root}/my-profile`)).data;
  assert.equal(profile.profile.hospitalJoinedOn, '2024-01');
  await assert.rejects(member('GET', `${root}/staff`), /HTTP 403/);
  await assert.rejects(member('GET', `${root}/roster-eligibility`), /HTTP 403/);
  const eligibility = (await admin('GET', `${root}/roster-eligibility`)).data;
  assert.equal(eligibility.demoMode, false);
  assert.equal(eligibility.items.length, 1);
  assert.equal(eligibility.items[0].eligible, true);
  const staff = (await admin('GET', `${root}/staff`)).data.items;
  assert.equal(staff.length, 1); // Administrator never gets a nurse record.
  assert.equal(staff[0].linkedAccount, true);
  assert.equal(staff[0].nurseId, profile.nurseId);
}
