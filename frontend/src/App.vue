<template>
  <div>
    <div v-show="messageModal.visible" class="message-modal" @click.self="closeMessageModal">
      <div class="modal-content">
        <span class="close-btn" @click="closeMessageModal">&times;</span>
        <h3 :class="['message-title', messageModal.type]">{{ messageModal.title }}</h3>
        <p>{{ messageModal.content }}</p>
      </div>
    </div>

    <div v-show="preview.visible" class="preview-modal" @click.self="closePreviewModal">
      <div class="preview-content">
        <div class="preview-header">
          <div class="preview-title">{{ preview.title }}</div>
          <span class="close-preview" @click="closePreviewModal">&times;</span>
        </div>
        <div class="preview-body">
          <div v-if="preview.loading" class="unsupported-preview">正在加载预览内容...</div>

          <img
            v-else-if="preview.type === 'image'"
            :src="preview.url"
            class="preview-image"
            :alt="preview.title"
          >

          <template v-else-if="preview.type === 'pdf'">
            <div class="pdf-container">
              <canvas ref="pdfCanvas" class="pdf-page"></canvas>
            </div>
            <div class="pdf-controls-container">
              <div class="pdf-controls">
                <button class="preview-nav-btn" :disabled="preview.page <= 1" @click="changePdfPage(-1)">
                  上一页
                </button>
                <span class="page-info">{{ preview.page }} / {{ preview.totalPages || 1 }}</span>
                <button
                  class="preview-nav-btn"
                  :disabled="preview.page >= preview.totalPages"
                  @click="changePdfPage(1)"
                >
                  下一页
                </button>
              </div>
            </div>
          </template>

          <pre v-else-if="preview.type === 'text'">{{ preview.text }}</pre>

          <div v-else class="unsupported-preview">
            <p>{{ preview.unsupportedMessage }}</p>
            <button
              v-if="preview.fileName"
              class="download-preview-btn"
              @click="downloadFile(preview.fileName)"
            >
              下载文件
            </button>
          </div>
        </div>
        <div class="preview-nav"></div>
        <div class="preview-actions"></div>
      </div>
    </div>

    <div v-show="auth.showLogin" class="auth-modal" @click.self="closeLoginModal">
      <div class="modal-content">
        <span class="close-btn" @click="closeLoginModal">&times;</span>
        <h3>用户登录</h3>
        <form @submit.prevent="handleLogin">
          <div class="form-group">
            <label for="login-username">用户名</label>
            <input
              id="login-username"
              v-model.trim="auth.loginForm.username"
              type="text"
              maxlength="20"
              autocomplete="username"
              required
            >
          </div>
          <div class="form-group">
            <label for="login-password">密码</label>
            <input
              id="login-password"
              v-model="auth.loginForm.password"
              type="password"
              maxlength="128"
              autocomplete="current-password"
              required
            >
          </div>
          <div class="form-error">{{ auth.loginError }}</div>
          <button type="submit" class="auth-btn">登录</button>
          <div class="auth-switch">
            还没有账号？<a href="#" @click.prevent="switchToRegister">立即注册</a>
          </div>
        </form>
      </div>
    </div>

    <div v-show="auth.showRegister" class="auth-modal" @click.self="closeRegisterModal">
      <div class="modal-content">
        <span class="close-btn" @click="closeRegisterModal">&times;</span>
        <h3>用户注册</h3>
        <form @submit.prevent="handleRegister">
          <div class="form-group">
            <label for="register-username">用户名</label>
            <input
              id="register-username"
              v-model.trim="auth.registerForm.username"
              type="text"
              maxlength="20"
              autocomplete="username"
              required
            >
            <small>仅支持字母、数字和下划线，长度 3-20 位</small>
          </div>
          <div class="form-group">
            <label for="register-password">密码</label>
            <input
              id="register-password"
              v-model="auth.registerForm.password"
              type="password"
              maxlength="128"
              autocomplete="new-password"
              required
            >
            <small>密码至少 8 位，建议使用大小写字母、数字和符号组合</small>
          </div>
          <div class="form-error">{{ auth.registerError }}</div>
          <button type="submit" class="auth-btn">注册</button>
          <div class="auth-switch">
            已有账号？<a href="#" @click.prevent="switchToLogin">去登录</a>
          </div>
        </form>
      </div>
    </div>

    <div class="container">
      <header>
        <h1>Mirad 文件上传</h1>
        <div v-show="currentUser" class="user-info">
          <span class="username">{{ currentUser }}</span>
          <button class="logout-btn" @click="handleLogout">退出登录</button>
        </div>
      </header>

      <div class="dashboard">
        <div v-show="currentUser" class="upload-section">
          <h2>文件上传</h2>
          <div class="upload-box">
            <form @submit.prevent="uploadSelectedFiles">
              <div
                :class="['file-drop-area', { dragover: isDragover }]"
                @click="openFilePicker"
                @dragover.prevent="handleDragOver"
                @dragleave.prevent="handleDragLeave"
                @drop.prevent="handleDrop"
              >
                <span class="file-msg">{{ selectedFileMessage }}</span>
                <input
                  ref="fileInput"
                  type="file"
                  multiple
                  @change="handleFileSelect"
                >
              </div>
              <div class="progress-container">
                <div class="progress-bar" :style="{ width: `${uploadProgress}%` }"></div>
                <span class="progress-text">{{ uploadProgress }}%</span>
              </div>
              <div class="error-msg" :style="{ display: uploadError ? 'block' : 'none' }">{{ uploadError }}</div>
              <button type="button" class="upload-btn" @click="uploadSelectedFiles">上传文件</button>
            </form>
          </div>
        </div>
      </div>

      <div v-show="currentUser" class="file-list-section">
        <div class="section-header">
          <h2>已上传文件</h2>
          <button class="refresh-btn" @click="loadFileList">刷新列表</button>
        </div>
        <div class="file-list">
          <div v-if="fileListLoading" class="loading">加载中...</div>
          <div v-else-if="!files.length" class="no-files">暂无文件，请先上传</div>
          <table v-else class="files-table">
            <thead>
              <tr>
                <th>文件名</th>
                <th>大小</th>
                <th>修改时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="file in files" :key="`${file.name}-${file.date}`">
                <td class="file-name" data-label="文件名">{{ file.name }}</td>
                <td data-label="大小">{{ formatFileSize(file.size) }}</td>
                <td data-label="修改时间">{{ file.dateStr }}</td>
                <td class="file-actions" data-label="操作">
                  <button class="btn btn-preview" @click="openPreview(file)">预览</button>
                  <button class="btn btn-download" @click="downloadFile(file.name)">下载</button>
                  <button class="btn btn-delete" @click="confirmDelete(file.name)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-show="!currentUser" class="file-list-section">
        <div class="file-list">
          <div class="no-files">请先登录查看文件</div>
        </div>
      </div>

      <footer>
        <p>Mirad Upload</p>
      </footer>
    </div>
  </div>
</template>

<script>
import { nextTick } from "vue";
import { checkSession, login, logout, register } from "./api/auth";
import {
  buildDownloadUrl,
  deleteFile,
  fetchTextPreview,
  listFiles,
  uploadFiles
} from "./api/files";
import { DEFAULT_FILE_MESSAGE, formatFileSize, getFileType } from "./utils/file";

export default {
  name: "App",
  data() {
    return {
      currentUser: "",
      files: [],
      fileListLoading: false,
      isDragover: false,
      selectedFiles: [],
      uploadError: "",
      uploadProgress: 0,
      messageTimer: null,
      loginErrorTimer: null,
      registerErrorTimer: null,
      progressResetTimer: null,
      messageModal: {
        visible: false,
        title: "提示",
        content: "",
        type: "success"
      },
      auth: {
        showLogin: false,
        showRegister: false,
        loginError: "",
        registerError: "",
        loginForm: {
          username: "",
          password: ""
        },
        registerForm: {
          username: "",
          password: ""
        }
      },
      preview: {
        visible: false,
        title: "文件预览",
        type: "",
        fileName: "",
        url: "",
        text: "",
        unsupportedMessage: "",
        loading: false,
        pdfDocument: null,
        page: 1,
        totalPages: 0
      }
    };
  },
  computed: {
    selectedFileMessage() {
      if (!this.selectedFiles.length) {
        return DEFAULT_FILE_MESSAGE;
      }

      const names = this.selectedFiles.map(file => file.name).join(", ");
      if (this.selectedFiles.length > 3) {
        return `${this.selectedFiles.length} 个文件：${names.substring(0, 50)}...`;
      }
      return names;
    }
  },
  async mounted() {
    await this.checkLoginStatus();
  },
  beforeUnmount() {
    window.clearTimeout(this.messageTimer);
    window.clearTimeout(this.loginErrorTimer);
    window.clearTimeout(this.registerErrorTimer);
    window.clearTimeout(this.progressResetTimer);
  },
  methods: {
    formatFileSize,
    async checkLoginStatus() {
      try {
        const data = await checkSession();
        if (data.loggedIn) {
          this.currentUser = data.username;
          this.closeAuthModals();
          await this.loadFileList();
          return;
        }

        this.currentUser = "";
        this.files = [];
        this.auth.showLogin = true;
      } catch (error) {
        this.showMessage("错误提示", `检查登录状态失败：${error.message}`, "error");
      }
    },
    async handleLogin() {
      try {
        await login(this.auth.loginForm.username, this.auth.loginForm.password);
        this.auth.loginError = "";
        this.auth.loginForm.username = "";
        this.auth.loginForm.password = "";
        this.closeAuthModals();
        this.showMessage("成功提示", "登录成功");
        await this.checkLoginStatus();
      } catch (error) {
        this.showInlineAuthError("loginError", error.message);
      }
    },
    async handleRegister() {
      const username = this.auth.registerForm.username.trim();
      const password = this.auth.registerForm.password;

      if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
        this.showInlineAuthError("registerError", "用户名只能包含字母、数字和下划线，长度 3-20 位");
        return;
      }
      if (password.length < 8) {
        this.showInlineAuthError("registerError", "密码长度不能少于 8 位");
        return;
      }

      try {
        await register(username, password);
        this.auth.registerError = "";
        this.auth.registerForm.username = "";
        this.auth.registerForm.password = "";
        this.switchToLogin();
        this.showMessage("成功提示", "注册成功，请登录");
      } catch (error) {
        this.showInlineAuthError("registerError", error.message);
      }
    },
    async handleLogout() {
      try {
        await logout();
        this.currentUser = "";
        this.files = [];
        this.selectedFiles = [];
        this.clearNativeFileInput();
        this.showMessage("成功提示", "已退出登录");
        this.auth.showLogin = true;
      } catch (error) {
        this.showMessage("错误提示", `退出登录失败：${error.message}`, "error");
      }
    },
    openFilePicker() {
      if (this.$refs.fileInput) {
        this.$refs.fileInput.click();
      }
    },
    handleFileSelect(event) {
      this.selectedFiles = Array.from(event.target.files || []);
      this.uploadError = "";
    },
    handleDragOver() {
      this.isDragover = true;
    },
    handleDragLeave() {
      this.isDragover = false;
    },
    handleDrop(event) {
      this.isDragover = false;
      this.selectedFiles = Array.from(event.dataTransfer?.files || []);

      if (this.$refs.fileInput) {
        this.$refs.fileInput.files = event.dataTransfer.files;
      }

      this.uploadError = "";
    },
    async uploadSelectedFiles() {
      if (!this.selectedFiles.length) {
        this.showUploadError("请先选择文件");
        return;
      }
      if (!this.currentUser) {
        this.showUploadError("请先登录");
        this.auth.showLogin = true;
        return;
      }

      try {
        const data = await uploadFiles(this.selectedFiles, percent => {
          this.uploadProgress = percent;
        });

        this.scheduleProgressReset();
        this.selectedFiles = [];
        this.clearNativeFileInput();
        this.uploadError = "";
        this.showMessage("成功提示", data.message || "上传成功");
        await this.loadFileList();
      } catch (error) {
        this.scheduleProgressReset();
        if (error.message.includes("Please sign in")) {
          this.currentUser = "";
          this.files = [];
          this.auth.showLogin = true;
          this.showUploadError("登录已失效，请重新登录");
          return;
        }
        this.showUploadError(error.message || "上传失败");
      }
    },
    async loadFileList() {
      if (!this.currentUser) {
        return;
      }

      this.fileListLoading = true;
      try {
        const data = await listFiles();
        this.files = Array.isArray(data.files) ? data.files : [];
      } catch (error) {
        if (error.message.includes("Please sign in")) {
          this.currentUser = "";
          this.files = [];
          this.auth.showLogin = true;
        } else {
          this.files = [];
          this.showMessage("错误提示", error.message, "error");
        }
      } finally {
        this.fileListLoading = false;
      }
    },
    async confirmDelete(fileName) {
      if (!window.confirm(`确定删除文件“${fileName}”吗？`)) {
        return;
      }

      try {
        await deleteFile(fileName);
        this.showMessage("成功提示", "文件已删除");
        await this.loadFileList();
      } catch (error) {
        this.showMessage("错误提示", `删除失败：${error.message}`, "error");
      }
    },
    downloadFile(fileName) {
      window.location.href = buildDownloadUrl(fileName, false);
    },
    async openPreview(file) {
      const fileType = getFileType(file.name);

      this.preview.visible = true;
      this.preview.title = file.name;
      this.preview.type = fileType;
      this.preview.fileName = file.name;
      this.preview.url = buildDownloadUrl(file.name, true);
      this.preview.text = "";
      this.preview.unsupportedMessage = "";
      this.preview.loading = true;
      this.preview.pdfDocument = null;
      this.preview.page = 1;
      this.preview.totalPages = 0;

      if (fileType === "image") {
        this.preview.loading = false;
        return;
      }

      if (fileType === "text") {
        try {
          this.preview.text = await fetchTextPreview(file.name);
          this.preview.loading = false;
        } catch (error) {
          this.preview.type = "other";
          this.preview.unsupportedMessage = `文本预览失败：${error.message}`;
          this.preview.loading = false;
        }
        return;
      }

      if (fileType === "pdf") {
        await nextTick();
        await this.loadPdfPreview();
        return;
      }

      this.preview.type = "other";
      this.preview.unsupportedMessage = `当前暂不支持预览 ${fileType} 类型文件`;
      this.preview.loading = false;
    },
    async loadPdfPreview() {
      if (!window.pdfjsLib) {
        this.preview.type = "other";
        this.preview.unsupportedMessage = "PDF 预览组件未加载，请直接下载文件";
        this.preview.loading = false;
        return;
      }

      try {
        const loadingTask = window.pdfjsLib.getDocument(this.preview.url);
        this.preview.pdfDocument = await loadingTask.promise;
        this.preview.totalPages = this.preview.pdfDocument.numPages;
        await this.renderPdfPage(1);
        this.preview.loading = false;
      } catch (error) {
        this.preview.type = "other";
        this.preview.unsupportedMessage = `PDF 预览失败：${error.message}`;
        this.preview.loading = false;
      }
    },
    async renderPdfPage(pageNumber) {
      if (!this.preview.pdfDocument || !this.$refs.pdfCanvas) {
        return;
      }

      const page = await this.preview.pdfDocument.getPage(pageNumber);
      const viewport = page.getViewport({ scale: 1.4 });
      const canvas = this.$refs.pdfCanvas;
      const context = canvas.getContext("2d");

      canvas.height = viewport.height;
      canvas.width = viewport.width;

      await page.render({
        canvasContext: context,
        viewport
      }).promise;

      this.preview.page = pageNumber;
    },
    async changePdfPage(step) {
      const nextPage = this.preview.page + step;
      if (nextPage < 1 || nextPage > this.preview.totalPages) {
        return;
      }
      await this.renderPdfPage(nextPage);
    },
    showMessage(title, content, type = "success") {
      this.messageModal.visible = true;
      this.messageModal.title = title;
      this.messageModal.content = content;
      this.messageModal.type = type;

      window.clearTimeout(this.messageTimer);
      this.messageTimer = window.setTimeout(() => {
        this.closeMessageModal();
      }, 3000);
    },
    showInlineAuthError(field, message) {
      this.auth[field] = message;
      const timerField = field === "loginError" ? "loginErrorTimer" : "registerErrorTimer";
      window.clearTimeout(this[timerField]);
      this[timerField] = window.setTimeout(() => {
        this.auth[field] = "";
      }, 3000);
    },
    showUploadError(message) {
      this.uploadError = message;
    },
    scheduleProgressReset() {
      window.clearTimeout(this.progressResetTimer);
      this.progressResetTimer = window.setTimeout(() => {
        this.uploadProgress = 0;
      }, 1000);
    },
    clearNativeFileInput() {
      if (this.$refs.fileInput) {
        this.$refs.fileInput.value = "";
      }
    },
    closeMessageModal() {
      this.messageModal.visible = false;
    },
    closePreviewModal() {
      this.preview.visible = false;
      this.preview.pdfDocument = null;
    },
    closeLoginModal() {
      this.auth.showLogin = false;
    },
    closeRegisterModal() {
      this.auth.showRegister = false;
    },
    closeAuthModals() {
      this.auth.showLogin = false;
      this.auth.showRegister = false;
    },
    switchToRegister() {
      this.auth.showLogin = false;
      this.auth.showRegister = true;
    },
    switchToLogin() {
      this.auth.showRegister = false;
      this.auth.showLogin = true;
    }
  }
};
</script>
