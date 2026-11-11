import type { components } from './schema.gen'

type Schemas = components['schemas']

export type WebSession = Schemas['WebSession']
export type WebKakaoAuthorization = Schemas['WebKakaoAuthorization']
export type CurrentUser = Schemas['CurrentUser']
export type WardMembership = Schemas['WardMembership']
export type WardRole = WardMembership['roles'][number]
export type ApiProblem = Schemas['ApiProblem']
export interface WardList {
  items: WardMembership[]
}
