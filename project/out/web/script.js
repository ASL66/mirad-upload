document.addEventListener('DOMContentLoaded', function () {
    const fileInput = document.getElementById('file-input');
    const dropZone = document.getElementById('drop-zone');
    const uploadBtn = document.getElementById('upload-btn');
    const uploadError = document.getElementById('upload-error');
    const errorModal = document.getElementById('error-modal');
    const errorMessage = document.getElementById('error-message');
    const closeErrorModal = document.getElementById('close-error-modal');
    const progressBar = document.getElementById('progress-bar');
    const progressText = document.getElementById('progress-text');
    const refreshBtn = document.getElementById('refresh-btn');
    const fileListElement = document.getElementById('file-list');
    const confirmModal = document.getElementById('confirm-modal');
    const confirmMessage = document.getElementById('confirm-message');
    const cancelDelete = document.getElementById('cancel-delete');
    const confirmDelete = document.getElementById('confirm-delete');
    let fileToDelete = null;

    function updateTime() {
        const now = new Date();
        const options = {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
        };
        document.getElementById('current-time').textContent =
            now.toLocaleDateString('zh-CN', options);
    }

    updateTime();
    setInterval(updateTime, 1000);

    function showError(message) {
        errorMessage.textContent = message;
        errorModal.style.display = 'flex';
    }

    closeErrorModal.addEventListener('click', () => {
        errorModal.style.display = 'none';
    });

    errorModal.addEventListener('click', (e) => {
        if (e.target === errorModal) {
            errorModal.style.display = 'none';
        }
    });

    function updateUploadButtonState() {
        uploadBtn.disabled = fileInput.files.length === 0;
    }

    dropZone.addEventListener('click', () => {
        fileInput.click();
    });

    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, preventDefaults, false);
    });

    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, highlight, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, unhighlight, false);
    });

    function highlight() {
        dropZone.classList.add('dragover');
    }

    function unhighlight() {
        dropZone.classList.remove('dragover');
    }

    dropZone.addEventListener('drop', handleDrop, false);

    function handleDrop(e) {
        const dt = e.dataTransfer;
        fileInput.files = dt.files;
        updateFileMessage();
        updateUploadButtonState();
    }

    fileInput.addEventListener('change', function () {
        updateFileMessage();
        updateUploadButtonState();
    });

    function updateFileMessage() {
        if (fileInput.files.length > 0) {
            if (fileInput.files.length === 1) {
                dropZone.querySelector('.file-msg').textContent =
                    `已选择: ${fileInput.files[0].name}`;
            } else {
                dropZone.querySelector('.file-msg').textContent =
                    `已选择 ${fileInput.files.length} 个文件`;
            }
        } else {
            dropZone.querySelector('.file-msg').textContent =
                '拖放文件到此处或点击选择';
        }
    }

    uploadBtn.addEventListener('click', function () {
        if (fileInput.files.length === 0) {
            showError('请先选择要上传的文件');
            return;
        }

        uploadBtn.disabled = true;
        uploadBtn.textContent = '上传中...';
        uploadError.style.display = 'none';

        const formData = new FormData();
        for (let i = 0; i < fileInput.files.length; i++) {
            const file = fileInput.files[i];
            formData.append('files', file);
        }

        const xhr = new XMLHttpRequest();
        xhr.upload.addEventListener('progress', function (e) {
            if (e.lengthComputable) {
                const percent = Math.round((e.loaded / e.total) * 100);
                progressBar.style.width = percent + '%';
                progressText.textContent = percent + '%';
            }
        });

        xhr.addEventListener('load', function () {
            if (xhr.status === 200) {
                progressBar.style.width = '100%';
                progressText.textContent = '100%';
                setTimeout(() => {
                    progressBar.style.width = '0%';
                    progressText.textContent = '0%';
                    fileInput.value = '';
                    dropZone.querySelector('.file-msg').textContent =
                        '拖放文件到此处或点击选择';
                    fetchFileList();
                    resetUploadButton();
                }, 500);
            } else {
                try {
                    const errorResponse = JSON.parse(xhr.responseText);
                    showError(errorResponse.message || `文件上传失败: ${xhr.status}`);
                } catch {
                    showError(`文件上传失败: ${xhr.status} ${xhr.statusText}`);
                }
                resetUploadButton();
            }
        });

        xhr.addEventListener('error', function () {
            showError('网络错误，请检查服务器连接');
            resetUploadButton();
        });

        xhr.addEventListener('abort', function () {
            showError('上传已取消');
            resetUploadButton();
        });

        xhr.open('POST', '/upload', true);
        // 不手动设置 Content-Type，让浏览器自动处理
        xhr.send(formData);
    });

    function showSuccessMessage(message) {
        const successMsg = document.createElement('div');
        successMsg.className = 'success-msg';
        successMsg.textContent = message;
        successMsg.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #198754;
            color: white;
            padding: 15px 25px;
            border-radius: 5px;
            box-shadow: 0 4px 8px rgba(0,0,0,0.2);
            z-index: 1000;
            animation: fadeInOut 3s forwards;
        `;
        document.body.appendChild(successMsg);
        const style = document.createElement('style');
        style.textContent = `
            @keyframes fadeInOut {
                0% { opacity: 0; transform: translateY(-20px); }
                10% { opacity: 1; transform: translateY(0); }
                90% { opacity: 1; transform: translateY(0); }
                100% { opacity: 0; transform: translateY(-20px); }
            }
        `;
        document.head.appendChild(style);
        setTimeout(() => {
            successMsg.remove();
            style.remove();
        }, 3000);
    }

    function resetUploadButton() {
        uploadBtn.disabled = false;
        uploadBtn.textContent = '上传文件';
        updateUploadButtonState();
    }

    function fetchFileList() {
        fileListElement.innerHTML = '<div class="no-files">正在加载文件列表...</div>';
        fetch('/list-files')
            .then(response => {
                if (!response.ok) {
                    throw new Error('获取文件列表失败');
                }
                return response.json();
            })
            .then(data => {
                renderFileList(data.files);
            })
            .catch(error => {
                console.error('获取文件列表失败:', error);
                fileListElement.innerHTML =
                    '<div class="no-files">无法加载文件列表: ' + error.message + '</div>';
            });
    }

    function showConfirmModal(filename) {
        fileToDelete = filename;
        confirmMessage.textContent = `确定要删除文件 "${filename}" 吗？`;
        confirmModal.style.display = 'flex';
    }

    function hideConfirmModal() {
        confirmModal.style.display = 'none';
        fileToDelete = null;
    }

    async function deleteFile(filename) {
        try {
            const response = await fetch(`/delete?file=${encodeURIComponent(filename)}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error('删除失败');
            }
            fetchFileList();
        } catch (error) {
            showError('删除文件失败: ' + error.message);
        }
    }

    cancelDelete.addEventListener('click', hideConfirmModal);
    confirmDelete.addEventListener('click', function () {
        if (fileToDelete) {
            deleteFile(fileToDelete);
            hideConfirmModal();
        }
    });

    confirmModal.addEventListener('click', (e) => {
        if (e.target === confirmModal) {
            hideConfirmModal();
        }
    });

    function renderFileList(files) {
        if (!files || files.length === 0) {
            fileListElement.innerHTML = '<div class="no-files">暂无上传文件</div>';
            return;
        }
        let html = '';
        files.forEach(file => {
            const size = formatFileSize(file.size);
            const date = new Date(file.date).toLocaleString();
            html += `
            <div class="file-item">
                <div class="file-info">
                    <div class="file-name">${file.name}</div>
                    <div class="file-meta">
                        <span class="file-size">${size}</span>
                        <span class="file-date">${date}</span>
                    </div>
                </div>
                <div class="file-actions">
                    <button class="btn btn-download" data-filename="${file.name}">
                        <svg width="16" height="16" fill="currentColor" viewBox="0 0 16 16">
                            <path d="M.5 9.9a.5.5 0 0 1 .5.5v2.5a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-2.5a.5.5 0 0 1 1 0v2.5a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2v-2.5a.5.5 0 0 1 .5-.5zm2.5 0a.5.5 0 0 1 .5.5v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5zm3 .5a.5.5 0 0 0-1 0v6a.5.5 0 0 0 1 0V6z"/>
                            <path d="M7.646 11.854a.5.5 0 0 0 .708 0l3-3a.5.5 0 0 0-.708-.708L8.5 10.293V1.5a.5.5 0 0 0-1 0v8.793L5.354 8.146a.5.5 0 1 0-.708.708l3 3z"/>
                        </svg>
                        下载
                    </button>
                    <button class="btn btn-delete" data-filename="${file.name}">
                        <svg width="16" height="16" fill="currentColor" viewBox="0 0 16 16">
                            <path d="M5.5 5.5A.5.5 0 0 1 6 6v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5zm2.5 0a.5.5 0 0 1 .5.5v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5zm3 .5a.5.5 0 0 0-1 0v6a.5.5 0 0 0 1 0V6z"/>
                            <path fill-rule="evenodd" d="M14.5 3a1 1 0 0 1-1 1H13v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4h-.5a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1H6a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1h3.5a1 1 0 0 1 1 1v1zM4.118 4 4 4.059V13a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V4.059L11.882 4H4.118zM2.5 3V2h11v1h-11z"/>
                        </svg>
                        删除
                    </button>
                </div>
            </div>
            `;
        });
        fileListElement.innerHTML = html;

        document.querySelectorAll('.btn-download').forEach(button => {
            button.addEventListener('click', async function () {
                const filename = this.getAttribute('data-filename');
                try {
                    const response = await fetch(`/download?file=${encodeURIComponent(filename)}`);
                    if (!response.ok) throw new Error('下载失败');
                    const blob = await response.blob();
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = filename;
                    document.body.appendChild(a);
                    a.click();
                    window.URL.revokeObjectURL(url);
                    document.body.removeChild(a);
                } catch (error) {
                    showError('下载文件失败: ' + error.message);
                }
            });
        });

        document.querySelectorAll('.btn-delete').forEach(button => {
            button.addEventListener('click', function () {
                const filename = this.getAttribute('data-filename');
                showConfirmModal(filename);
            });
        });
    }

    function formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    refreshBtn.addEventListener('click', function () {
        fetchFileList();
    });

    fetchFileList();
    updateUploadButtonState();
});