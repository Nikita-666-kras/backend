/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SITE_NAME?: string
  readonly VITE_POST_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
