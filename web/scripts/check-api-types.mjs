// Fails when src/api/schema.gen.ts is out of date with docs/openapi.yaml.
import { execFileSync } from 'node:child_process'
import { readFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'

const temp = '.api-check.gen.ts'
const bin = join('node_modules', 'openapi-typescript', 'bin', 'cli.js')
try {
  execFileSync(process.execPath, [bin, '../docs/openapi.yaml', '-o', temp], { stdio: ['ignore', 'ignore', 'inherit'] })
  const normalize = (s) => s.replace(/\r\n/g, '\n')
  const fresh = normalize(readFileSync(temp, 'utf8'))
  const committed = normalize(readFileSync(join('src', 'api', 'schema.gen.ts'), 'utf8'))
  if (fresh !== committed) {
    console.error('src/api/schema.gen.ts is stale. Run "npm run api:gen" and commit the result.')
    process.exitCode = 1
  } else {
    console.log('API types match docs/openapi.yaml.')
  }
} finally {
  rmSync(temp, { force: true })
}
