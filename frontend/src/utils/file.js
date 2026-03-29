export const DEFAULT_FILE_MESSAGE = "拖拽文件到这里，或点击选择文件";

export function formatFileSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

export function getFileType(fileName) {
  const extension = fileName.includes(".")
    ? fileName.split(".").pop().toLowerCase()
    : "";

  if (["jpg", "jpeg", "png", "gif", "bmp", "webp"].includes(extension)) {
    return "image";
  }
  if (extension === "pdf") {
    return "pdf";
  }
  if (["txt", "md", "json", "xml", "csv", "log", "html", "css", "js"].includes(extension)) {
    return "text";
  }
  return "other";
}
