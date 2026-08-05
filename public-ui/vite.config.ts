import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5174,
    proxy: {
      '/posts': 'http://localhost:8081',
      '/media': 'http://localhost:8081',
      '/parts': 'http://localhost:8081',
      '/kits': 'http://localhost:8081',
      '/drones': 'http://localhost:8081',
      '/part-categories': 'http://localhost:8081'
    }
  }
})
