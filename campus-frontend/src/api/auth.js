import http from './http'

export const authApi = {
  login: data => http.post('/auth/login', data),
  register: data => http.post('/auth/register', data),
  changePassword: data => http.post('/auth/change-password', data)
}
