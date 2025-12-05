// script.js
document.addEventListener('DOMContentLoaded', function() {
    // DOM元素
    const dropZone = document.getElementById('drop-zone');
    const fileInput = document.getElementById('file-input');
    const uploadBtn = document.getElementById('upload-btn');
    const progressBar = document.getElementById('progress-bar');
    const progressText = document.getElementById('progress-text');
    const uploadError = document.getElementById('upload-error');
    const fileList = document.getElementById('file-list');
    const refreshBtn = document.getElementById('refresh-btn');
    const messageModal = document.getElementById('message-modal');
    const messageTitle = document.getElementById('message-title');
    const messageContent = document.getElementById('message-content');
    const closeMessageModal = document.getElementById('close-message-modal');
    const previewModal = document.getElementById('preview-modal');
    const previewTitle = document.getElementById('preview-title');
    const previewBody = document.getElementById('preview-body');
    const closePreview = document.getElementById('close-preview');
    const loginModal = document.getElementById('login-modal');
    const registerModal = document.getElementById('register-modal');
    const closeLoginModal = document.getElementById('close-login-modal');
    const closeRegisterModal = document.getElementById('close-register-modal');
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const loginError = document.getElementById('login-error');
    const registerError = document.getElementById('register-error');
    const switchToRegister = document.getElementById('switch-to-register');
    const switchToLogin = document.getElementById('switch-to-login');
    const userInfo = document.getElementById('user-info');
    const usernameDisplay = document.getElementById('username-display');
    const logoutBtn = document.getElementById('logout-btn');

    let currentUser = null;

    // 初始化时确保只显示一个认证弹窗
    registerModal.style.display = 'none';
    loginModal.style.display = 'none';
    
    // 检查登录状态
    checkLoginStatus();

    // 事件监听
    dropZone.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', handleFileSelect);
    dropZone.addEventListener('dragover', handleDragOver);
    dropZone.addEventListener('dragleave', handleDragLeave);
    dropZone.addEventListener('drop', handleDrop);
    uploadBtn.addEventListener('click', uploadFiles);
    refreshBtn.addEventListener('click', loadFileList);
    closeMessageModal.addEventListener('click', () => messageModal.style.display = 'none');
    closePreview.addEventListener('click', () => previewModal.style.display = 'none');
    closeLoginModal.addEventListener('click', () => loginModal.style.display = 'none');
    closeRegisterModal.addEventListener('click', () => registerModal.style.display = 'none');
    switchToRegister.addEventListener('click', (e) => {
        e.preventDefault();
        loginModal.style.display = 'none';
        registerModal.style.display = 'flex';
    });
    switchToLogin.addEventListener('click', (e) => {
        e.preventDefault();
        registerModal.style.display = 'none';
        loginModal.style.display = 'flex';
    });
    loginForm.addEventListener('submit', handleLogin);
    registerForm.addEventListener('submit', handleRegister);
    logoutBtn.addEventListener('click', handleLogout);

    // 点击模态框外部关闭
    window.addEventListener('click', (e) => {
        if (e.target === messageModal) messageModal.style.display = 'none';
        if (e.target === previewModal) previewModal.style.display = 'none';
        if (e.target === loginModal) loginModal.style.display = 'none';
        if (e.target === registerModal) registerModal.style.display = 'none';
    });

    // 检查登录状态
    function checkLoginStatus() {
        fetch('/check-login')
            .then(response => response.json())
            .then(data => {
                if (data.loggedIn) {
                    currentUser = data.username;
                    showLoggedInState();
                    loadFileList();
                } else {
                    currentUser = null;
                    showLoggedOutState();
                    loginModal.style.display = 'flex';
                }
            })
            .catch(error => showMessage('错误提示', '检查登录状态失败: ' + error.message, 'error'));
    }

    // 显示登录状态
    function showLoggedInState() {
        usernameDisplay.textContent = currentUser;
        userInfo.style.display = 'flex';
        document.querySelector('.upload-section').style.display = 'block';
        document.querySelector('.file-list-section').style.display = 'block';
    }

    // 显示未登录状态
    function showLoggedOutState() {
        userInfo.style.display = 'none';
        document.querySelector('.upload-section').style.display = 'none';
        document.querySelector('.file-list-section').style.display = 'none';
        fileList.innerHTML = '<div class="no-files">请先登录以查看您的文件</div>';
    }

    // 处理登录
    function handleLogin(e) {
        e.preventDefault();
        const username = document.getElementById('login-username').value;
        const password = document.getElementById('login-password').value;

        fetch('/login', {
            method: 'POST',
            body: new URLSearchParams({
                'username': username,
                'password': password
            })
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(data => {
                    throw new Error(data.message || '登录失败');
                });
            }
            return response.json();
        })
        .then(data => {
            loginModal.style.display = 'none';
            showMessage('成功提示', '登录成功');
            checkLoginStatus();
            loginForm.reset();
        })
        .catch(error => {
            loginError.textContent = error.message;
            // 3秒后自动清除错误信息
            setTimeout(() => {
                loginError.textContent = '';
            }, 3000);
        });
    }

    // 处理注册
    function handleRegister(e) {
        e.preventDefault();
        const username = document.getElementById('register-username').value;
        const password = document.getElementById('register-password').value;

        // 简单的客户端验证
        if (!/^[a-zA-Z0-9_]{3,20}$/.test(username)) {
            registerError.textContent = '用户名只能包含字母、数字和下划线，长度3-20';
            return;
        }
        
        if (password.length < 6) {
            registerError.textContent = '密码长度不能少于6位';
            return;
        }

        fetch('/register', {
            method: 'POST',
            body: new URLSearchParams({
                'username': username,
                'password': password
            })
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(data => {
                    throw new Error(data.message || '注册失败');
                });
            }
            return response.json();
        })
        .then(data => {
            registerModal.style.display = 'none';
            loginModal.style.display = 'flex';
            showMessage('成功提示', '注册成功，请登录');
            registerForm.reset();
        })
        .catch(error => {
            registerError.textContent = error.message;
            // 3秒后自动清除错误信息
            setTimeout(() => {
                registerError.textContent = '';
            }, 3000);
        });
    }

    // 处理登出
    function handleLogout() {
        fetch('/logout', { method: 'GET' })
            .then(response => response.json())
            .then(data => {
                showMessage('成功提示', '已成功登出');
                checkLoginStatus();
            })
            .catch(error => showMessage('错误提示', '登出失败: ' + error.message, 'error'));
    }

    // 处理文件选择
    function handleFileSelect(e) {
        const files = e.target.files;
        if (files.length > 0) {
            showSelectedFiles(files);
            uploadError.style.display = 'none';
        }
    }

    // 处理拖放事件
    function handleDragOver(e) {
        e.preventDefault();
        dropZone.classList.add('dragover');
    }

    function handleDragLeave() {
        dropZone.classList.remove('dragover');
    }

    function handleDrop(e) {
        e.preventDefault();
        dropZone.classList.remove('dragover');
        
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            fileInput.files = files;
            showSelectedFiles(files);
            uploadError.style.display = 'none';
        }
    }

    // 显示选中的文件
    function showSelectedFiles(files) {
        let fileNames = Array.from(files).map(file => file.name).join(', ');
        if (files.length > 3) {
            fileNames = `${files.length}个文件: ${fileNames.substring(0, 50)}...`;
        }
        document.querySelector('.file-msg').textContent = fileNames;
        uploadError.textContent = '';
    }

    // 上传文件
    function uploadFiles() {
        const files = fileInput.files;
        if (files.length === 0) {
            uploadError.textContent = '请先选择文件';
            uploadError.style.display = 'block';
            return;
        }

        if (!currentUser) {
            uploadError.textContent = '请先登录';
            uploadError.style.display = 'block';
            return;
        }

        const formData = new FormData();
        Array.from(files).forEach(file => {
            formData.append('files', file);
        });

        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/upload', true);

        xhr.upload.addEventListener('progress', (e) => {
            if (e.lengthComputable) {
                const percent = Math.round((e.loaded / e.total) * 100);
                progressBar.style.width = percent + '%';
                progressText.textContent = percent + '%';
            }
        });

        xhr.addEventListener('load', () => {
            try {
                const response = JSON.parse(xhr.responseText);
                if (xhr.status === 200 && response.success) {
                    showMessage('成功提示', response.message);
                    loadFileList();
                    fileInput.value = '';
                    document.querySelector('.file-msg').textContent = '拖放文件到此处或点击选择';
                } else {
                    throw new Error(response.message || '上传失败');
                }
            } catch (error) {
                uploadError.textContent = error.message;
                uploadError.style.display = 'block';
            } finally {
                // 重置进度条
                setTimeout(() => {
                    progressBar.style.width = '0%';
                    progressText.textContent = '0%';
                }, 1000);
            }
        });

        xhr.addEventListener('error', () => {
            uploadError.textContent = '上传失败，请重试';
            uploadError.style.display = 'block';
            progressBar.style.width = '0%';
            progressText.textContent = '0%';
        });

        xhr.send(formData);
    }

    // 加载文件列表
    function loadFileList() {
        if (!currentUser) return;

        fileList.innerHTML = '<div class="loading">加载中...</div>';
        
        fetch('/list-files')
            .then(response => {
                if (!response.ok) {
                    if (response.status === 401) {
                        currentUser = null;
                        showLoggedOutState();
                        loginModal.style.display = 'flex';
                        throw new Error('登录已过期，请重新登录');
                    }
                    throw new Error('获取文件列表失败');
                }
                return response.json();
            })
            .then(data => {
                if (data.files && data.files.length > 0) {
                    renderFileList(data.files);
                } else {
                    fileList.innerHTML = '<div class="no-files">暂无文件，请上传文件</div>';
                }
            })
            .catch(error => {
                fileList.innerHTML = `<div class="no-files error">${error.message}</div>`;
            });
    }

    // 渲染文件列表
    function renderFileList(files) {
        let html = '<table class="files-table">';
        html += `
            <tr>
                <th>文件名</th>
                <th>大小</th>
                <th>修改时间</th>
                <th>操作</th>
            </tr>
        `;
        
        files.forEach(file => {
            // 为响应式设计添加data-label属性
            html += `<tr>
                <td data-label="文件名" class="file-name">${escapeHtml(file.name)}</td>
                <td data-label="大小">${formatFileSize(file.size)}</td>
                <td data-label="修改时间">${file.dateStr}</td>
                <td data-label="操作" class="file-actions">
                    <button class="btn btn-preview" data-filename="${encodeURIComponent(file.name)}" data-type="${getFileType(file.name)}">预览</button>
                    <button class="btn btn-download" data-filename="${encodeURIComponent(file.name)}">下载</button>
                    <button class="btn btn-delete" data-filename="${encodeURIComponent(file.name)}">删除</button>
                </td>
            </tr>`;
        });
        
        html += '</table>';
        fileList.innerHTML = html;

        // 添加按钮事件
        document.querySelectorAll('.btn-download').forEach(btn => {
            btn.addEventListener('click', () => {
                const filename = btn.getAttribute('data-filename');
                window.location.href = `/download?file=${filename}`;
            });
        });

        document.querySelectorAll('.btn-delete').forEach(btn => {
            btn.addEventListener('click', () => {
                const filename = btn.getAttribute('data-filename');
                const fileNameDecoded = decodeURIComponent(filename);
                
                if (confirm(`确定要删除文件 "${fileNameDecoded}" 吗？`)) {
                    deleteFile(filename);
                }
            });
        });

        document.querySelectorAll('.btn-preview').forEach(btn => {
            btn.addEventListener('click', () => {
                const filename = btn.getAttribute('data-filename');
                const fileType = btn.getAttribute('data-type');
                previewFile(filename, fileType);
            });
        });
    }

    // 删除文件
    function deleteFile(filename) {
        fetch(`/delete?file=${filename}`, { method: 'DELETE' })
            .then(response => {
                if (!response.ok) {
                    throw new Error('删除失败');
                }
                return response.json();
            })
            .then(data => {
                showMessage('成功提示', '文件已删除');
                loadFileList();
            })
            .catch(error => {
                showMessage('错误提示', '删除失败: ' + error.message, 'error');
            });
    }

    // 预览文件
    function previewFile(filename, fileType) {
        const fileNameDecoded = decodeURIComponent(filename);
        previewTitle.textContent = fileNameDecoded;
        
        // 根据文件类型显示不同的预览内容
        if (fileType === 'image') {
            previewBody.innerHTML = `<img src="/download?file=${filename}" class="preview-image" alt="${escapeHtml(fileNameDecoded)}">`;
        } else if (fileType === 'pdf') {
            previewBody.innerHTML = `
                <div class="pdf-container">
                    <canvas id="pdf-canvas"></canvas>
                </div>
                <div class="pdf-controls">
                    <button id="prev-page" class="preview-nav-btn">上一页</button>
                    <span id="page-num" class="page-info">1</span>
                    <button id="next-page" class="preview-nav-btn">下一页</button>
                </div>
            `;
            loadPdfPreview(filename);
        } else if (fileType === 'text') {
            // 文本文件预览
            fetch(`/download?file=${filename}`)
                .then(response => response.text())
                .then(text => {
                    previewBody.innerHTML = `<pre>${escapeHtml(text)}</pre>`;
                })
                .catch(error => {
                    previewBody.innerHTML = `
                        <div class="unsupported-preview">
                            <p>预览文本文件失败: ${error.message}</p>
                            <button class="download-preview-btn" data-filename="${filename}">下载文件</button>
                        </div>
                    `;
                    document.querySelector('.download-preview-btn').addEventListener('click', () => {
                        window.location.href = `/download?file=${filename}`;
                    });
                });
        } else {
            previewBody.innerHTML = `
                <div class="unsupported-preview">
                    <p>不支持预览此类型文件 (${fileType})</p>
                    <button class="download-preview-btn" data-filename="${filename}">下载文件</button>
                </div>
            `;
            document.querySelector('.download-preview-btn').addEventListener('click', () => {
                window.location.href = `/download?file=${filename}`;
            });
        }
        
        previewModal.style.display = 'flex';
    }

    // 加载PDF预览
    function loadPdfPreview(filename) {
        const url = `/download?file=${filename}`;
        const canvas = document.getElementById('pdf-canvas');
        const ctx = canvas.getContext('2d');
        const pageNumEl = document.getElementById('page-num');
        const prevPageBtn = document.getElementById('prev-page');
        const nextPageBtn = document.getElementById('next-page');
        
        let pdfDoc = null;
        let currentPage = 1;
        
        // 加载PDF
        pdfjsLib.getDocument(url).promise.then(pdfDoc_ => {
            pdfDoc = pdfDoc_;
            pageNumEl.textContent = `${currentPage} / ${pdfDoc.numPages}`;
            renderPage(currentPage);
            
            prevPageBtn.addEventListener('click', () => {
                if (currentPage <= 1) return;
                currentPage--;
                renderPage(currentPage);
            });
            
            nextPageBtn.addEventListener('click', () => {
                if (currentPage >= pdfDoc.numPages) return;
                currentPage++;
                renderPage(currentPage);
            });
        }).catch(error => {
            previewBody.innerHTML = `
                <div class="unsupported-preview">
                    <p>预览PDF失败: ${error.message}</p>
                    <button class="download-preview-btn" data-filename="${filename}">下载文件</button>
                </div>
            `;
            document.querySelector('.download-preview-btn').addEventListener('click', () => {
                window.location.href = `/download?file=${filename}`;
            });
        });
        
        function renderPage(num) {
            pdfDoc.getPage(num).then(page => {
                const viewport = page.getViewport({ scale: 1.5 });
                canvas.height = viewport.height;
                canvas.width = viewport.width;
                
                const renderContext = {
                    canvasContext: ctx,
                    viewport: viewport
                };
                
                page.render(renderContext).promise.then(() => {
                    pageNumEl.textContent = `${currentPage} / ${pdfDoc.numPages}`;
                });
            });
        }
    }

    // 工具函数：格式化文件大小
    function formatFileSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        else if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
        else if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
        else return (bytes / 1073741824).toFixed(1) + ' GB';
    }

    // 工具函数：获取文件类型
    function getFileType(filename) {
        const ext = filename.split('.').pop().toLowerCase();
        if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext)) return 'image';
        if (ext === 'pdf') return 'pdf';
        if (['txt', 'html', 'css', 'js', 'json', 'xml', 'csv', 'md', 'log'].includes(ext)) return 'text';
        if (['doc', 'docx'].includes(ext)) return 'word';
        if (['xls', 'xlsx'].includes(ext)) return 'excel';
        if (['ppt', 'pptx'].includes(ext)) return 'powerpoint';
        if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'archive';
        return 'other';
    }

    // 工具函数：显示消息提示
    function showMessage(title, content, type = 'success') {
        messageTitle.textContent = title;
        messageContent.textContent = content;
        
        // 设置提示类型样式
        messageTitle.className = 'message-title ' + type;
        
        messageModal.style.display = 'flex';
        
        // 3秒后自动关闭
        setTimeout(() => {
            messageModal.style.display = 'none';
        }, 3000);
    }

    // 工具函数：HTML转义
    function escapeHtml(text) {
        return text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }
});