/// <reference types="vitest" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  test: {
    environment: 'happy-dom',
    include: ['src/__tests__/**/*.test.ts'],
    globals: true,
    css: true,
    deps: {
      inline: ['element-plus'],
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    // ⚠️ 必须是 '0.0.0.0'，**不能**改 '::'。
    //
    // 实测（2026-10-12）：本机 `localhost` 解析为 `::1`(IPv6) 在前、`127.0.0.1` 在后，
    // 浏览器优先走 IPv6；而 WSL2 的 Windows↔WSL 端口转发**只转发 IPv4**，
    // 即便 vite 监听 `::`（实测监听地址确为 `*:3001`），Windows 侧 `[::1]:3001`
    // 依然超时 ⇒ 改 '::' 只会让 `127.0.0.1` 也一并失效，属倒退。
    //
    // ⇒ 结论：**访问前端请用 http://127.0.0.1:3001/（已实测 status=200）**。
    //    `localhost` 打不开是 WSL 端口转发的固有限制，非本项目配置问题。
    host: '0.0.0.0',
    port: 3001,
    proxy: {
      '/api': {
        // 同理：用 127.0.0.1 而非 localhost，避免代理端再踩 IPv6 回环
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
      },
    },
  },
})