import http from "./http";

export function listFiles() {
  return http.get("/api/files/list");
}

export function uploadFiles(files, onProgress) {
  const formData = new FormData();
  files.forEach(file => {
    formData.append("files", file);
  });

  return http.post("/api/files/upload", formData, {
    headers: {
      "Content-Type": "multipart/form-data"
    },
    onUploadProgress(event) {
      if (!event.total || typeof onProgress !== "function") {
        return;
      }
      const percent = Math.round((event.loaded / event.total) * 100);
      onProgress(percent);
    }
  });
}

export function deleteFile(fileName) {
  return http.delete("/api/files/delete", {
    params: {
      file: fileName
    }
  });
}

export function buildDownloadUrl(fileName, preview = false) {
  const params = new URLSearchParams({
    file: fileName
  });

  if (preview) {
    params.set("preview", "true");
  }

  const base = process.env.VUE_APP_API_BASE_URL || "";
  return `${base}/api/files/download?${params.toString()}`;
}

export function fetchTextPreview(fileName) {
  return http.get("/api/files/download", {
    params: {
      file: fileName,
      preview: true
    },
    responseType: "text"
  });
}
