import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 8090,
    proxy: {
      '/auth': 'http://localhost:8080',
      '/manager': 'http://localhost:8080'
    }
  }
})
