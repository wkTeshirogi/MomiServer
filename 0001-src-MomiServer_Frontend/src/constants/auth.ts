// Storage keys shared by authentication and both HTTP clients.
export const ACCESS_TOKEN_KEY = 'access_token'
export const REFRESH_TOKEN_KEY = 'refresh_token'
export const USER_EMAIL_KEY = 'user_email'

export const ROLE_RANK = { USER: 10, OPERATOR: 100, ADMIN: 999 } as const
