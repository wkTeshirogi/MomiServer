import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const currentFilePath = fileURLToPath(import.meta.url)
const currentDirectoryPath = path.dirname(currentFilePath)
const backendRootPath = path.resolve(currentDirectoryPath, '..', '..')
const workspaceRootPath = path.resolve(backendRootPath, '..')
const sourceCatalogPath = path.join(backendRootPath, 'src', 'main', 'resources', 'message-catalog', 'messages.ja.json')
const frontendCatalogPath = path.join(
  workspaceRootPath,
  '0001-src-smarthomewebserverfrontend',
  'src',
  'messages',
  'messageCatalog.ts',
)

const sourceCatalog = JSON.parse(fs.readFileSync(sourceCatalogPath, 'utf8'))
const messageEntries = Object.entries(sourceCatalog.messages ?? {})
  .filter(([, definition]) => definition.visibility === 'public')

function toPascalCase(messageKey) {
  return messageKey
    .split('.')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')
}

function quote(value) {
  return `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "\\'")}'`
}

const codeEntries = messageEntries
  .map(([messageKey]) => `  ${toPascalCase(messageKey)}: ${quote(messageKey)},`)
  .join('\n')
const textEntries = messageEntries
  .map(([messageKey, definition]) => `  [MessageCode.${toPascalCase(messageKey)}]: ${quote(definition.text)},`)
  .join('\n')

const frontendSource = `export const MessageCode = {
${codeEntries}
} as const

export type MessageCode = typeof MessageCode[keyof typeof MessageCode]

type MessageValues = Record<string, string | number | boolean | null | undefined>

const messages: Record<MessageCode, string> = {
${textEntries}
}

export function message(code: MessageCode, values: MessageValues = {}): string {
  let template = messages[code] ?? code

  Object.entries(values).forEach(([key, value]) => {
    template = template.replaceAll(\`{\${key}}\`, String(value ?? ''))
  })

  return template
}
`

fs.mkdirSync(path.dirname(frontendCatalogPath), { recursive: true })
fs.writeFileSync(frontendCatalogPath, frontendSource, 'utf8')
