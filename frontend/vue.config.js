module.exports = {
  outputDir: "dist",
  assetsDir: "static",
  devServer: {
    host: "0.0.0.0",
    port: 8080,
    proxy: {
      "^/api": {
        target: process.env.VUE_APP_DEV_API_TARGET || "http://127.0.0.1:8081",
        changeOrigin: true
      }
    }
  }
};
