import axios from "axios";

const http = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || "",
  withCredentials: true,
  timeout: 60000
});

http.interceptors.response.use(
  response => response.data,
  error => {
    const message = error.response?.data?.message || error.message || "请求失败";
    return Promise.reject(new Error(message));
  }
);

export default http;
