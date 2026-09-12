// Validates render.yaml against Render's published Blueprint schema before pushing,
// so a typo surfaces here rather than as a failed Blueprint sync.
import { readFileSync } from 'node:fs'
// The Render schema declares draft 2020-12, which the default Ajv build cannot compile.
import Ajv from 'ajv/dist/2020.js'
import addFormats from 'ajv-formats'
import { parse } from 'yaml'

const schemaUrl = 'https://render.com/schema/render.yaml.json'
const schema = await fetch(schemaUrl).then((r) => r.json())
const doc = parse(readFileSync(new URL('../../render.yaml', import.meta.url), 'utf8'))

const ajv = new Ajv({ allErrors: true, strict: false })
addFormats(ajv)

const validate = ajv.compile(schema)
if (validate(doc)) {
  console.log('render.yaml is valid against the Render Blueprint schema.')
  const names = doc.services.map((s) => `${s.name} (${s.type}/${s.runtime ?? '-'})`)
  console.log('services:  ' + names.join(', '))
  console.log('databases: ' + (doc.databases ?? []).map((d) => d.name).join(', '))
} else {
  console.error('render.yaml FAILED validation:\n')
  for (const err of validate.errors) {
    console.error(` ${err.instancePath || '/'} ${err.message}`)
    if (err.params?.allowedValues) {
      console.error(`   allowed: ${err.params.allowedValues.join(', ')}`)
    }
  }
  process.exit(1)
}
