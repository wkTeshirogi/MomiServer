// Preserve the configured base URL while sharing normalized HTTP/WebSocket origins.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
export const SERVER_URL = API_BASE_URL.replace(/\/$/, '')
export const BASE_URL = `${SERVER_URL}/api`
export const WEBSOCKET_SERVER_URL = SERVER_URL.replace(/^http:/, 'ws:').replace(/^https:/, 'wss:')
