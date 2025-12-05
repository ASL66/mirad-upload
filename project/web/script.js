// script.js
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
    const previewModal = document.getElementById('preview-modal');
    const previewTitle = document.getElementById('preview-title');
    const previewBody = document.getElementById('preview-body');
    const previewActions = document.getElementById('preview-actions');
    let fileToDelete = null;
    let mediaElement = null;
    let currentPdf = null;
    let currentPage = 1;
    let totalPages = 1;
    let currentScale = 1.0; // 当前缩放比例

    // 设置 PDF.js worker 路径
    pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/2.11.338/pdf.worker.min.js';

    // 支持预览的文件扩展名
    const previewableExtensions = {
        image: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'],
        text: ['txt', 'md', 'html', 'css', 'js', 'json', 'xml', 'log', 'ini', 'conf', 'sh', 'bat', 'py', 'java', 'c', 'cpp', 'h'],
        pdf: ['pdf'],
        video: ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv'],
        audio: ['mp3', 'wav', 'ogg', 'flac', 'aac']
    };

    // MIME类型映射
    const mimeTypes = {
        mp4: 'video/mp4',
        webm: 'video/webm',
        ogg: 'video/ogg',
        mov: 'video/quicktime',
        avi: 'video/x-msvideo',
        mkv: 'video/x-matroska',
        mp3: 'audio/mpeg',
        wav: 'audio/wav',
        ogg: 'audio/ogg',
        flac: 'audio/flac',
        aac: 'audio/aac'
    };

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

    // 渲染PDF页面
    function renderPdfPage(pdf, pageNum) {
        currentPage = pageNum;
        
        // 创建PDF容器
        const pdfContainer = document.createElement('div');
        pdfContainer.className = 'pdf-container';
        
        // 创建Canvas容器
        const canvasContainer = document.createElement('div');
        canvasContainer.style.position = 'relative';
        
        // 创建控件容器
        const controlsContainer = document.createElement('div');
        controlsContainer.className = 'pdf-controls-container';
        
        pdfContainer.appendChild(canvasContainer);
        pdfContainer.appendChild(controlsContainer);
        
        previewBody.innerHTML = '';
        previewBody.appendChild(pdfContainer);
        
        pdf.getPage(pageNum).then(page => {
            const viewport = page.getViewport({ scale: currentScale });
            
            const canvas = document.createElement('canvas');
            const context = canvas.getContext('2d');
            canvas.className = 'pdf-page';
            canvas.height = viewport.height;
            canvas.width = viewport.width;
            
            canvasContainer.appendChild(canvas);
            
            const renderContext = {
                canvasContext: context,
                viewport: viewport
            };
            
            page.render(renderContext).promise.then(() => {
                // 更新页码显示
                updatePagination(controlsContainer);
            });
        });
    }

    // 更新PDF导航控件
    function updatePagination(container) {
        container.innerHTML = '';
        
        if (!currentPdf) return;
        
        const controls = document.createElement('div');
        controls.className = 'pdf-controls';
        
        const prevBtn = document.createElement('button');
        prevBtn.className = 'preview-nav-btn';
        prevBtn.innerHTML = '<svg width="16" height="16" fill="currentColor" viewBox="0 0 24 24"><path d="M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6 1.41-1.41z"/></svg>';
        prevBtn.title = '上一页';
        prevBtn.addEventListener('click', () => {
            if (currentPage > 1) {
                currentPage--;
                renderPdfPage(currentPdf, currentPage);
            }
        });
        
        const nextBtn = document.createElement('button');
        nextBtn.className = 'preview-nav-btn';
        nextBtn.innerHTML = '<svg width="16" height="16" fill="currentColor" viewBox="0 0 24 24"><path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z"/></svg>';
        nextBtn.title = '下一页';
        nextBtn.addEventListener('click', () => {
            if (currentPage < totalPages) {
                currentPage++;
                renderPdfPage(currentPdf, currentPage);
            }
        });
        
        const pageInfo = document.createElement('span');
        pageInfo.className = 'page-info';
        pageInfo.textContent = `第 ${currentPage} 页 / 共 ${totalPages} 页`;
        
        // 添加缩放控制
        const zoomOutBtn = document.createElement('button');
        zoomOutBtn.className = 'preview-nav-btn';
        zoomOutBtn.textContent = '-';
        zoomOutBtn.title = '缩小';
        zoomOutBtn.addEventListener('click', () => {
            if (currentScale > 0.5) {
                currentScale -= 0.1;
                renderPdfPage(currentPdf, currentPage);
            }
        });
        
        const zoomInBtn = document.createElement('button');
        zoomInBtn.className = 'preview-nav-btn';
        zoomInBtn.textContent = '+';
        zoomInBtn.title = '放大';
        zoomInBtn.addEventListener('click', () => {
            if (currentScale < 2.5) {
                currentScale += 0.1;
                renderPdfPage(currentPdf, currentPage);
            }
        });
        
        const zoomInfo = document.createElement('span');
        zoomInfo.className = 'zoom-info';
        zoomInfo.textContent = `缩放: ${Math.round(currentScale * 100)}%`;
        
        controls.appendChild(prevBtn);
        controls.appendChild(pageInfo);
        controls.appendChild(nextBtn);
        controls.appendChild(zoomOutBtn);
        controls.appendChild(zoomInfo);
        controls.appendChild(zoomInBtn);
        
        container.appendChild(controls);
    }

    // 预览文件函数
    function previewFile(filename) {
        previewTitle.textContent = filename;
        previewBody.innerHTML = '<div class="unsupported-preview">正在加载预览内容...</div>';
        previewActions.innerHTML = '';
        previewModal.style.display = 'flex';
        
        // 根据文件类型确定预览方式
        const extension = filename.split('.').pop().toLowerCase();
        const fileUrl = `/download?file=${encodeURIComponent(filename)}`;
        
        // 清除之前的媒体元素引用
        mediaElement = null;
        currentPdf = null;
        currentPage = 1;
        totalPages = 1;
        currentScale = 1.0; // 初始化缩放比例
        
        // 创建下载按钮
        const downloadBtn = document.createElement('button');
        downloadBtn.className = 'preview-btn preview-btn-download';
        downloadBtn.innerHTML = '<svg width="16" height="16" fill="currentColor" viewBox="0 0 24 24" style="margin-right:5px"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>下载文件';
        downloadBtn.onclick = function() {
            downloadFile(filename);
        };
        previewActions.appendChild(downloadBtn);
        
        if (previewableExtensions.image.includes(extension)) {
            const img = document.createElement('img');
            img.src = fileUrl;
            img.alt = filename;
            img.style.maxWidth = '100%';
            img.style.maxHeight = '65vh';
            img.style.objectFit = 'contain';
            previewBody.innerHTML = '';
            previewBody.appendChild(img);
        } 
        else if (previewableExtensions.pdf.includes(extension)) {
            // 使用PDF.js预览PDF
            const loadingTask = pdfjsLib.getDocument(fileUrl);
            loadingTask.promise.then(pdf => {
                currentPdf = pdf;
                totalPages = pdf.numPages;
                renderPdfPage(pdf, 1);
            }).catch(error => {
                console.error('加载PDF失败:', error);
                previewBody.innerHTML = '<div class="unsupported-preview">加载PDF失败</div>';
            });
        } 
        else if (previewableExtensions.text.includes(extension)) {
            // 加载文本内容
            fetch(fileUrl)
                .then(response => response.text())
                .then(text => {
                    // 只加载前5000个字符以提高性能
                    const displayText = text.length > 5000 ? text.substring(0, 5000) + '...' : text;
                    
                    const pre = document.createElement('pre');
                    pre.textContent = displayText;
                    previewBody.innerHTML = '';
                    previewBody.appendChild(pre);
                })
                .catch(error => {
                    previewBody.innerHTML = '<div class="unsupported-preview">加载文本内容失败</div>';
                });
        } 
        else if (previewableExtensions.video.includes(extension)) {
            const video = document.createElement('video');
            video.controls = true;
            video.style.maxWidth = '100%';
            video.style.maxHeight = '65vh';
            
            // 使用正确的MIME类型
            const mimeType = mimeTypes[extension] || 'video/mp4';
            video.innerHTML = `<source src="${fileUrl}" type="${mimeType}">`;
            
            previewBody.innerHTML = '';
            previewBody.appendChild(video);
            mediaElement = video;
        } 
        else if (previewableExtensions.audio.includes(extension)) {
            const audio = document.createElement('audio');
            audio.controls = true;
            audio.style.width = '100%';
            audio.style.maxWidth = '500px';
            
            // 使用正确的MIME类型
            const mimeType = mimeTypes[extension] || 'audio/mpeg';
            audio.innerHTML = `<source src="${fileUrl}" type="${mimeType}">`;
            
            previewBody.innerHTML = '';
            previewBody.appendChild(audio);
            mediaElement = audio;
        } 
        else {
            previewBody.innerHTML = '<div class="unsupported-preview">该文件类型暂不支持预览</div>';
        }
    }

    function pauseMedia() {
        if (mediaElement) {
            mediaElement.pause();
            mediaElement = null;
        }
    }

    function downloadFile(filename) {
        const fileUrl = `/download?file=${encodeURIComponent(filename)}`;
        const a = document.createElement('a');
        a.href = fileUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }

    previewModal.addEventListener('click', function(e) {
        if (e.target === previewModal) {
            pauseMedia();
            previewModal.style.display = 'none';
        }
    });

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
                    <button class="btn btn-preview" data-filename="${file.name}">
                        <svg width="20" height="20" fill="currentColor" viewBox="0 0 24 24">
                            <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>
                        </svg>
                        预览
                    </button>
                    <button class="btn btn-download" data-filename="${file.name}">
                        <svg width="20" height="20" fill="currentColor" viewBox="0 0 24 24">
                            <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
                        </svg>
                        下载
                    </button>
                    <button class="btn btn-delete" data-filename="${file.name}">
                        <svg width="20" height="20" fill="currentColor" viewBox="0 0 24 24">
                            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                        </svg>
                        删除
                    </button>
                </div>
            </div>
            `;
        });
        fileListElement.innerHTML = html;

        // 添加预览按钮事件监听
        document.querySelectorAll('.btn-preview').forEach(button => {
            button.addEventListener('click', function () {
                const filename = this.getAttribute('data-filename');
                previewFile(filename);
            });
        });

        document.querySelectorAll('.btn-download').forEach(button => {
            button.addEventListener('click', async function () {
                const filename = this.getAttribute('data-filename');
                downloadFile(filename);
            });
        });

        document.querySelectorAll('.btn-delete').forEach(button => {
            button.addEventListener('click', function () {
                const filename = this.getAttribute('data-filename');
                showConfirmModal(filename);
            });
        });
    }

    function downloadFile(filename) {
        const fileUrl = `/download?file=${encodeURIComponent(filename)}`;
        const a = document.createElement('a');
        a.href = fileUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
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