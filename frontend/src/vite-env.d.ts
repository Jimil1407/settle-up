/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Bare hostname of the API in production builds; unset in development. */
  readonly VITE_API_HOST?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
