package com.example;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

public class WebServer {
    private static final int PORT = 9090;
    private static final String UPLOAD_DIR = "uploads";
    private static final int BUFFER_SIZE = 1048576; // 1MB
    private static final int MAX_MEMORY = 10 * 1024 * 1024; // 10MB 内存缓冲区
    private static final int DIRECT_BUFFER_SIZE = 2 * 1024 * 1024; // 2MB直接缓冲区

    public static void main(String[] args) throws IOException {
        // 创建上传目录
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            System.out.println("创建上传目录: " + uploadPath.toAbsolutePath());
        }
        // 创建HTTP服务器
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // 设置上下文处理器
        server.createContext("/", new StaticFileHandler("web"));
        server.createContext("/upload", new UploadHandler());
        server.createContext("/list-files", new FileListHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/delete", new DeleteHandler());
        
        // 设置线程池
        int cores = Runtime.getRuntime().availableProcessors();
        server.setExecutor(Executors.newFixedThreadPool(cores * 4));
        
        System.out.println("服务器启动在 http://localhost:" + PORT);
        System.out.println("文件上传目录: " + uploadPath.toAbsolutePath());
        System.out.println("线程池大小: " + (cores * 4));
        System.out.println("按 Ctrl+C 停止服务");
        
        server.start();
    }

    // 静态文件处理器
    static class StaticFileHandler implements HttpHandler {
        private final String baseDir;
        public StaticFileHandler(String baseDir) {
            this.baseDir = baseDir;
        }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            Path filePath = Paths.get(baseDir + path).normalize();
            byte[] response;
            String contentType = "text/html; charset=UTF-8";
            
            try {
                if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                    // 根据文件类型设置Content-Type
                    if (path.endsWith(".css")) {
                        contentType = "text/css; charset=UTF-8";
                    } else if (path.endsWith(".js")) {
                        contentType = "application/javascript; charset=UTF-8";
                    } else if (path.endsWith(".png")) {
                        contentType = "image/png";
                    } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                        contentType = "image/jpeg";
                    } else if (path.endsWith(".gif")) {
                        contentType = "image/gif";
                    } else if (path.endsWith(".ico")) {
                        contentType = "image/x-icon";
                    }
                    
                    response = Files.readAllBytes(filePath);
                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.sendResponseHeaders(200, response.length);
                } else {
                    // 文件不存在时返回404
                    response = "404 找不到资源".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                    exchange.sendResponseHeaders(404, response.length);
                }
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "服务器内部错误: " + e.getMessage());
            }
        }
    }

    // 文件上传处理器 - 修复中文文件名显示异常
    static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "方法不允许");
                return;
            }
            
            try {
                // 获取Content-Type
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                
                // 检查是否是multipart/form-data
                if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                    sendErrorResponse(exchange, 400, "无效的请求类型");
                    return;
                }
                
                // 获取boundary
                String boundary = contentType.split("boundary=")[1];
                byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
                byte[] endBoundaryBytes = ("--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
                
                List<Path> savedFiles = new ArrayList<>();
                InputStream requestBody = exchange.getRequestBody();
                
                // 状态机变量
                int state = 0; // 0=查找边界, 1=读取头部, 2=读取文件内容
                int boundaryIndex = 0;
                int endBoundaryIndex = 0;
                FileChannel fileChannel = null;
                ByteBuffer directBuffer = null;
                String filename = null;
                ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
                ByteArrayOutputStream memoryBuffer = new ByteArrayOutputStream();
                boolean inFile = false;
                Path currentFilePath = null;
                
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                
                while ((bytesRead = requestBody.read(buffer)) != -1) {
                    for (int i = 0; i < bytesRead; i++) {
                        byte b = buffer[i];
                        
                        switch (state) {
                            case 0: // 查找边界
                                if (b == boundaryBytes[boundaryIndex]) {
                                    boundaryIndex++;
                                    if (boundaryIndex == boundaryBytes.length) {
                                        state = 1; // 进入头部读取
                                        boundaryIndex = 0;
                                        headerBuffer.reset();
                                    }
                                } else if (boundaryIndex > 0) {
                                    // 边界匹配失败，重置
                                    boundaryIndex = 0;
                                }
                                break;
                                
                            case 1: // 读取头部
                                headerBuffer.write(b);
                                // 检查是否到达头部结束 (两个CRLF)
                                byte[] headerBytes = headerBuffer.toByteArray();
                                int headerLen = headerBytes.length;
                                if (headerLen >= 4 && 
                                    headerBytes[headerLen-4] == '\r' && 
                                    headerBytes[headerLen-3] == '\n' && 
                                    headerBytes[headerLen-2] == '\r' && 
                                    headerBytes[headerLen-1] == '\n') {
                                    
                                    // 解析头部
                                    String headers = new String(headerBytes, 0, headerLen - 4, StandardCharsets.UTF_8);
                                    filename = extractFilename(headers);
                                    
                                    if (filename != null && !filename.isEmpty()) {
                                        filename = sanitizeFilename(filename);
                                        currentFilePath = Paths.get(UPLOAD_DIR, filename);
                                        
                                        // 防止文件覆盖
                                        if (Files.exists(currentFilePath)) {
                                            filename = System.currentTimeMillis() + "_" + filename;
                                            currentFilePath = Paths.get(UPLOAD_DIR, filename);
                                        }
                                        
                                        // 使用FileChannel和直接缓冲区
                                        fileChannel = FileChannel.open(currentFilePath, 
                                            StandardOpenOption.CREATE, 
                                            StandardOpenOption.WRITE);
                                        directBuffer = ByteBuffer.allocateDirect(DIRECT_BUFFER_SIZE);
                                        inFile = true;
                                        state = 2; // 进入文件内容读取
                                        memoryBuffer.reset();
                                    } else {
                                        // 没有找到文件名，跳过这个部分
                                        state = 3; // 跳过部分
                                    }
                                }
                                break;
                                
                            case 2: // 读取文件内容
                                if (b == boundaryBytes[boundaryIndex]) {
                                    boundaryIndex++;
                                    // 检查是否匹配完整边界
                                    if (boundaryIndex == boundaryBytes.length) {
                                        // 完成文件写入
                                        if (fileChannel != null) {
                                            // 写入内存缓冲区内容（减去边界部分）
                                            if (memoryBuffer.size() > boundaryBytes.length) {
                                                byte[] memBytes = memoryBuffer.toByteArray();
                                                writeToBufferSafely(directBuffer, 
                                                    Arrays.copyOfRange(memBytes, 0, memBytes.length - boundaryBytes.length),
                                                    fileChannel);
                                            }
                                            
                                            // 刷新直接缓冲区
                                            flushBuffer(directBuffer, fileChannel);
                                            
                                            fileChannel.close();
                                            savedFiles.add(currentFilePath);
                                            fileChannel = null;
                                            directBuffer = null;
                                            inFile = false;
                                        }
                                        
                                        // 重置状态
                                        state = 1;
                                        boundaryIndex = 0;
                                        headerBuffer.reset();
                                        memoryBuffer.reset();
                                        continue;
                                    }
                                    
                                    // 将当前字节存入内存缓冲区
                                    memoryBuffer.write(b);
                                    
                                    // 如果内存缓冲区太大，写入文件并重置
                                    if (memoryBuffer.size() > MAX_MEMORY) {
                                        if (fileChannel != null && directBuffer != null) {
                                            byte[] memBytes = memoryBuffer.toByteArray();
                                            writeToBufferSafely(directBuffer, memBytes, fileChannel);
                                        }
                                        memoryBuffer.reset();
                                    }
                                } else {
                                    // 边界匹配中断
                                    if (boundaryIndex > 0) {
                                        // 将已匹配的部分写入文件
                                        if (fileChannel != null && directBuffer != null && memoryBuffer.size() > 0) {
                                            byte[] memBytes = memoryBuffer.toByteArray();
                                            writeToBufferSafely(directBuffer, memBytes, fileChannel);
                                            memoryBuffer.reset();
                                        }
                                        boundaryIndex = 0;
                                    }
                                    
                                    // 写入当前字节
                                    if (fileChannel != null && directBuffer != null) {
                                        // 检查缓冲区空间
                                        if (!directBuffer.hasRemaining()) {
                                            flushBuffer(directBuffer, fileChannel);
                                        }
                                        
                                        // 将字节放入直接缓冲区
                                        directBuffer.put(b);
                                    }
                                }
                                break;
                                
                            case 3: // 跳过部分（无文件）
                                if (b == boundaryBytes[boundaryIndex]) {
                                    boundaryIndex++;
                                    if (boundaryIndex == boundaryBytes.length) {
                                        state = 1; // 回到头部读取
                                        boundaryIndex = 0;
                                        headerBuffer.reset();
                                    }
                                } else {
                                    boundaryIndex = 0;
                                }
                                break;
                        }
                        
                        // 检查结束边界
                        if (b == endBoundaryBytes[endBoundaryIndex]) {
                            endBoundaryIndex++;
                            if (endBoundaryIndex == endBoundaryBytes.length) {
                                // 结束处理
                                if (inFile && fileChannel != null) {
                                    // 写入剩余数据
                                    if (memoryBuffer.size() > 0) {
                                        byte[] memBytes = memoryBuffer.toByteArray();
                                        writeToBufferSafely(directBuffer, memBytes, fileChannel);
                                    }
                                    
                                    flushBuffer(directBuffer, fileChannel);
                                    
                                    fileChannel.close();
                                    savedFiles.add(currentFilePath);
                                }
                                break;
                            }
                        } else {
                            endBoundaryIndex = 0;
                        }
                    }
                }
                
                // 关闭最后一个文件流
                if (inFile && fileChannel != null) {
                    // 写入剩余数据
                    if (memoryBuffer.size() > 0) {
                        byte[] memBytes = memoryBuffer.toByteArray();
                        writeToBufferSafely(directBuffer, memBytes, fileChannel);
                    }
                    
                    flushBuffer(directBuffer, fileChannel);
                    fileChannel.close();
                }
                
                if (savedFiles.isEmpty()) {
                    sendJsonError(exchange, 400, "未找到有效文件");
                } else {
                    // 返回成功响应
                    String jsonResponse = String.format(
                        "{\"success\": true, \"count\": %d, \"message\": \"成功上传 %d 个文件\"}",
                        savedFiles.size(), savedFiles.size()
                    );
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonError(exchange, 500, "上传失败: " + e.getMessage());
            }
        }
        
        // 安全写入缓冲区 - 防止溢出
        private void writeToBufferSafely(ByteBuffer buffer, byte[] data, FileChannel channel) throws IOException {
            int offset = 0;
            while (offset < data.length) {
                if (!buffer.hasRemaining()) {
                    flushBuffer(buffer, channel);
                }
                
                int length = Math.min(buffer.remaining(), data.length - offset);
                buffer.put(data, offset, length);
                offset += length;
            }
        }
        
        // 刷新缓冲区到文件
        private void flushBuffer(ByteBuffer buffer, FileChannel channel) throws IOException {
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            buffer.clear();
        }
        
    // 关键修改：处理UTF-8编码的文件名（修复双重解码问题）
        private String extractFilename(String headers) {
            for (String line : headers.split("\r\n")) {
                if (line.startsWith("Content-Disposition:")) {
                    String[] parts = line.split(";");
                    for (String part : parts) {
                        part = part.trim();
                        if (part.startsWith("filename=")) {
                            String filename = part.substring("filename=".length());
                            if (filename.startsWith("\"") && filename.endsWith("\"")) {
                                filename = filename.substring(1, filename.length() - 1);
                            }
                            try {
                                // 仅进行一次URL解码（使用UTF-8）
                                filename = URLDecoder.decode(filename, StandardCharsets.UTF_8.name());
                                return filename;
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                            return filename;
                        }
                    }
                }
            }
            return null;
        }
        
        // 关键修改：允许中文字符（\u4e00-\u9fa5）
        private String sanitizeFilename(String filename) {
            filename = filename.replace("\\", "/");
            int lastSlash = filename.lastIndexOf("/");
            if (lastSlash != -1) {
                filename = filename.substring(lastSlash + 1);
            }
            // 过滤非法字符，保留中文、字母、数字和部分符号
            return filename.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "_");
        }
    }


    // 文件列表处理器
    static class FileListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "方法不允许");
                return;
            }
            
            try {
                Path uploadDir = Paths.get(UPLOAD_DIR);
                List<Map<String, Object>> files = new ArrayList<>();
                
                if (Files.exists(uploadDir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(uploadDir)) {
                        for (Path file : stream) {
                            if (!Files.isDirectory(file)) {
                                Map<String, Object> fileInfo = new HashMap<>();
                                fileInfo.put("name", file.getFileName().toString());
                                fileInfo.put("size", Files.size(file));
                                fileInfo.put("date", Files.getLastModifiedTime(file).toMillis());
                                files.add(fileInfo);
                            }
                        }
                    }
                }
                
                // 按修改时间降序排序
                files.sort((a, b) -> 
                    Long.compare((Long)b.get("date"), (Long)a.get("date"))
                );
                
                // 构建JSON响应
                StringBuilder json = new StringBuilder("{\"files\":[");
                for (int i = 0; i < files.size(); i++) {
                    Map<String, Object> file = files.get(i);
                    json.append(String.format(
                        "{\"name\":\"%s\",\"size\":%d,\"date\":%d}", 
                        file.get("name").toString().replace("\"", "\\\""), 
                        (Long)file.get("size"),
                        (Long)file.get("date")
                    ));
                    if (i < files.size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]}");
                
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] responseBytes = json.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonError(exchange, 500, "获取文件列表失败: " + e.getMessage());
            }
        }
    }

    // 文件下载处理器（使用零拷贝优化 + 中文文件名支持）
    static class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "方法不允许");
                return;
            }
            
            try {
                String query = exchange.getRequestURI().getQuery();
                if (query == null || !query.startsWith("file=")) {
                    sendErrorResponse(exchange, 400, "缺少文件参数");
                    return;
                }
                
                // 关键修改：使用UTF-8解码文件名
                String filename = URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8.name());
                Path filePath = Paths.get(UPLOAD_DIR, filename).normalize();
                Path uploadDir = Paths.get(UPLOAD_DIR).normalize().toAbsolutePath();
                
                // 安全验证：确保文件在指定目录内
                if (!filePath.toAbsolutePath().startsWith(uploadDir)) {
                    sendErrorResponse(exchange, 403, "禁止访问: 无效的文件路径");
                    return;
                }
                
                if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                    sendErrorResponse(exchange, 404, "文件不存在");
                    return;
                }
                
                // 关键修改：设置带UTF-8编码声明的Content-Disposition
                String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.name())
                        .replace("+", "%20")  // 替换空格为%20
                        .replace("\"", "%22"); // 替换引号为%22
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().set("Content-Disposition", 
                    "attachment; filename*=UTF-8''" + encodedFilename);
                
                // 发送文件内容（使用零拷贝）
                long fileSize = Files.size(filePath);
                exchange.sendResponseHeaders(200, fileSize);
                
                try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
                     OutputStream os = exchange.getResponseBody()) {
                    
                    // 使用transferTo进行零拷贝传输
                    fileChannel.transferTo(0, fileSize, Channels.newChannel(os));
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "下载失败: " + e.getMessage());
            }
        }
    }

    // 文件删除处理器
    static class DeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "方法不允许");
                return;
            }
            
            try {
                String query = exchange.getRequestURI().getQuery();
                if (query == null || !query.startsWith("file=")) {
                    sendErrorResponse(exchange, 400, "缺少文件参数");
                    return;
                }
                
                // 关键修改：使用UTF-8解码文件名
                String filename = URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8.name());
                Path filePath = Paths.get(UPLOAD_DIR, filename).normalize();
                Path uploadDir = Paths.get(UPLOAD_DIR).normalize().toAbsolutePath();
                
                // 安全验证
                if (!filePath.toAbsolutePath().startsWith(uploadDir)) {
                    sendErrorResponse(exchange, 403, "禁止访问: 无效的文件路径");
                    return;
                }
                
                if (!Files.exists(filePath)) {
                    sendErrorResponse(exchange, 404, "文件不存在");
                    return;
                }
                
                Files.delete(filePath);
                sendResponse(exchange, 200, "文件删除成功: " + filename);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "删除失败: " + e.getMessage());
            }
        }
    }
    
    // 辅助方法：发送响应
    private static void sendResponse(HttpExchange exchange, int statusCode, String message) 
            throws IOException {
        byte[] response = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
    
    // 辅助方法：发送错误响应
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String error) 
            throws IOException {
        sendResponse(exchange, statusCode, error);
    }
    
    // 辅助方法：发送JSON错误响应
    private static void sendJsonError(HttpExchange exchange, int statusCode, String error) 
            throws IOException {
        String jsonResponse = String.format(
            "{\"success\": false, \"message\": \"%s\"}", 
            error.replace("\"", "\\\"")
        );
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
        }
    }
}