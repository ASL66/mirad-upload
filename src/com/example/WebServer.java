package com.example;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebServer {
    private static final int PORT = 9090;
    private static final String UPLOAD_DIR = "uploads";
    private static final String USERS_DIR = "users";
    private static final int BUFFER_SIZE = 1048576; // 1MB
    private static final int MAX_MEMORY = 10 * 1024 * 1024; // 10MB 内存缓冲区
    private static final int DIRECT_BUFFER_SIZE = 2 * 1024 * 1024; // 2MB直接缓冲区
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws IOException {
        // 创建必要目录
        createDirectory(UPLOAD_DIR);
        createDirectory(USERS_DIR);
        
        // 创建HTTP服务器
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // 设置上下文处理器
        server.createContext("/", new StaticFileHandler("web"));
        server.createContext("/upload", new UploadHandler());
        server.createContext("/list-files", new FileListHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/delete", new DeleteHandler());
        server.createContext("/register", new RegisterHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/logout", new LogoutHandler());
        server.createContext("/check-login", new CheckLoginHandler());
        
        // 设置线程池
        int cores = Runtime.getRuntime().availableProcessors();
        server.setExecutor(Executors.newFixedThreadPool(cores * 4));
        
        System.out.println("服务器启动在 http://localhost:" + PORT);
        System.out.println("文件上传根目录: " + Paths.get(UPLOAD_DIR).toAbsolutePath());
        System.out.println("用户数据目录: " + Paths.get(USERS_DIR).toAbsolutePath());
        System.out.println("线程池大小: " + (cores * 4));
        System.out.println("按 Ctrl+C 停止服务");
        
        server.start();
    }

    private static void createDirectory(String dir) throws IOException {
        Path path = Paths.get(dir);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            System.out.println("创建目录: " + path.toAbsolutePath());
        }
    }

    // 用户管理工具类（修复语法错误）
    static class UserManager {
        // 验证用户凭证
        static boolean validateUser(String username, String password) {
            try {
                Path userFile = Paths.get(USERS_DIR, username + ".user");
                if (!Files.exists(userFile)) {
                    return false;
                }

                String content = new String(Files.readAllBytes(userFile), StandardCharsets.UTF_8);
                String storedHash = content.trim();
                String inputHash = hashPassword(password);

                return storedHash.equals(inputHash); // 原代码写错成storedHash，已修正
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        // 注册新用户
        static boolean registerUser(String username, String password) {
            try {
                // 验证用户名格式
                if (!isValidUsername(username)) { // 修复后正确调用
                    return false;
                }

                Path userFile = Paths.get(USERS_DIR, username + ".user");
                if (Files.exists(userFile)) {
                    return false; // 用户已存在
                }

                // 创建用户文件和专属上传目录
                String passwordHash = hashPassword(password);
                Files.write(userFile, passwordHash.getBytes(StandardCharsets.UTF_8));
                Files.createDirectories(Paths.get(UPLOAD_DIR, username));

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        // 生成密码哈希
        static String hashPassword(String password) throws NoSuchAlgorithmException {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        // 修复重复的isValid拼写错误！！！
        static boolean isValidUsername(String username) {
            // 只允许字母、数字、下划线，长度3-20
            return username != null && Pattern.matches("^[a-zA-Z0-9_]{3,20}$", username);
        }

        // 获取用户的上传目录
        static String getUserUploadDir(String username) {
            return Paths.get(UPLOAD_DIR, username).toString();
        }

        // 删除用户的所有文件
        static boolean deleteUserFiles(String username) {
            try {
                Path userDir = Paths.get(getUserUploadDir(username));
                if (Files.exists(userDir)) {
                    Files.walkFileTree(userDir, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    // 会话管理工具类（修复方法名）
    static class SessionManager {
        private static final Map<String, Session> sessions = new HashMap<>();
        private static final long SESSION_TIMEOUT = 3600 * 1000; // 1小时

        // 创建新会话
        static String createSession(String username) {
            String sessionId = UUID.randomUUID().toString();
            Session session = new Session(sessionId, username, System.currentTimeMillis());
            sessions.put(sessionId, session);
            return sessionId;
        }

        // 修复方法名：原代码是validateValidSession，改为validateSession
        static String validateSession(String sessionId) {
            if (sessionId == null) return null;

            Session session = sessions.get(sessionId);
            if (session == null) return null;

            // 检查会话是否过期
            if (System.currentTimeMillis() - session.timestamp > SESSION_TIMEOUT) {
                sessions.remove(sessionId);
                return null;
            }

            // 更新会话时间戳
            session.timestamp = System.currentTimeMillis();
            return session.username;
        }

        // 销毁会话
        static void invalidateSession(String sessionId) {
            sessions.remove(sessionId);
        }

        // 清理过期会话
        static void cleanExpiredSessions() {
            long now = System.currentTimeMillis();
            sessions.values().removeIf(session -> now - session.timestamp > SESSION_TIMEOUT);
        }

        static class Session {
            String id;
            String username;
            long timestamp;

            Session(String id, String username, long timestamp) {
                this.id = id;
                this.username = username;
                this.timestamp = timestamp;
            }
        }
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

    // 文件上传处理器
    static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 检查登录状态
            String sessionId = getSessionIdFromCookies(exchange.getRequestHeaders().getFirst("Cookie"));
            String username = SessionManager.validateSession(sessionId);
            if (username == null) {
                sendJsonError(exchange, 401, "请先登录");
                return;
            }
            
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
                String userUploadDir = UserManager.getUserUploadDir(username);
                
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
                                        currentFilePath = Paths.get(userUploadDir, filename);
                                        
                                        // 防止文件覆盖
                                        if (Files.exists(currentFilePath)) {
                                            filename = System.currentTimeMillis() + "_" + filename;
                                            currentFilePath = Paths.get(userUploadDir, filename);
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
        
        // 提取文件名
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
        
        // 清理文件名
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
            // 检查登录状态
            String sessionId = getSessionIdFromCookies(exchange.getRequestHeaders().getFirst("Cookie"));
            String username = SessionManager.validateSession(sessionId);
            if (username == null) {
                sendJsonError(exchange, 401, "请先登录");
                return;
            }
            
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "方法不允许");
                return;
            }
            
            try {
                String userUploadDir = UserManager.getUserUploadDir(username);
                Path uploadDir = Paths.get(userUploadDir);
                List<Map<String, Object>> files = new ArrayList<>();
                
                if (Files.exists(uploadDir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(uploadDir)) {
                        for (Path file : stream) {
                            if (!Files.isDirectory(file)) {
                                Map<String, Object> fileInfo = new HashMap<>();
                                fileInfo.put("name", file.getFileName().toString());
                                fileInfo.put("size", Files.size(file));
                                fileInfo.put("date", Files.getLastModifiedTime(file).toMillis());
                                fileInfo.put("dateStr", DATE_FORMAT.format(new Date(Files.getLastModifiedTime(file).toMillis())));
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
                        "{\"name\":\"%s\",\"size\":%d,\"date\":%d,\"dateStr\":\"%s\"}", 
                        file.get("name").toString().replace("\"", "\\\""), 
                        (Long)file.get("size"),
                        (Long)file.get("date"),
                        file.get("dateStr").toString().replace("\"", "\\\"")
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

    // 文件下载处理器
    static class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 检查登录状态
            String sessionId = getSessionIdFromCookies(exchange.getRequestHeaders().getFirst("Cookie"));
            String username = SessionManager.validateSession(sessionId);
            if (username == null) {
                sendErrorResponse(exchange, 401, "请先登录");
                return;
            }
            
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
                
                // 解码文件名
                String filename = URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8.name());
                String userUploadDir = UserManager.getUserUploadDir(username);
                Path filePath = Paths.get(userUploadDir, filename).normalize();
                Path uploadDir = Paths.get(userUploadDir).normalize().toAbsolutePath();
                
                // 安全验证：确保文件在用户的上传目录内
                if (!filePath.toAbsolutePath().startsWith(uploadDir)) {
                    sendErrorResponse(exchange, 403, "禁止访问: 无效的文件路径");
                    return;
                }
                
                if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                    sendErrorResponse(exchange, 404, "文件不存在");
                    return;
                }
                
                // 设置响应头，支持中文文件名
                String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.name())
                        .replace("+", "%20")
                        .replace("\"", "%22");
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().set("Content-Disposition", 
                    "attachment; filename*=UTF-8''" + encodedFilename);
                
                // 发送文件内容（使用零拷贝）
                long fileSize = Files.size(filePath);
                exchange.sendResponseHeaders(200, fileSize);
                
                try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
                     OutputStream os = exchange.getResponseBody()) {
                    
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
            // 检查登录状态
            String sessionId = getSessionIdFromCookies(exchange.getRequestHeaders().getFirst("Cookie"));
            String username = SessionManager.validateSession(sessionId);
            if (username == null) {
                sendErrorResponse(exchange, 401, "请先登录");
                return;
            }
            
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
                
                // 解码文件名
                String filename = URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8.name());
                String userUploadDir = UserManager.getUserUploadDir(username);
                Path filePath = Paths.get(userUploadDir, filename).normalize();
                Path uploadDir = Paths.get(userUploadDir).normalize().toAbsolutePath();
                
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
                sendJsonResponse(exchange, 200, "{\"success\": true, \"message\": \"文件删除成功\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "删除失败: " + e.getMessage());
            }
        }
    }

    // 注册处理器
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "方法不允许");
                return;
            }
            
            try {
                // 读取请求体
                String requestBody = new String(readAllBytes(exchange.getRequestBody()), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(requestBody);
                
                String username = params.get("username");
                String password = params.get("password");
                
                // 验证参数
                if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                    sendJsonError(exchange, 400, "用户名和密码不能为空");
                    return;
                }
                
                // 验证用户名格式
                if (!UserManager.isValidUsername(username)) {
                    sendJsonError(exchange, 400, "用户名只能包含字母、数字和下划线，长度3-20");
                    return;
                }
                
                // 验证密码长度
                if (password.length() < 6) {
                    sendJsonError(exchange, 400, "密码长度不能少于6位");
                    return;
                }
                
                // 注册用户
                if (UserManager.registerUser(username, password)) {
                    sendJsonResponse(exchange, 200, "{\"success\": true, \"message\": \"注册成功，请登录\"}");
                } else {
                    sendJsonError(exchange, 400, "用户名已存在");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonError(exchange, 500, "注册失败: " + e.getMessage());
            }
        }
    }

    // 登录处理器
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "方法不允许");
                return;
            }
            
            try {
                // 读取请求体
                String requestBody = new String(readAllBytes(exchange.getRequestBody()), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(requestBody);
                
                String username = params.get("username");
                String password = params.get("password");
                
                // 验证参数
                if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                    sendJsonError(exchange, 400, "用户名和密码不能为空");
                    return;
                }
                
                // 验证用户
                if (UserManager.validateUser(username, password)) {
                    // 创建会话
                    String sessionId = SessionManager.createSession(username);
                    
                    // 设置Cookie
                    exchange.getResponseHeaders().add("Set-Cookie", 
                        "sessionId=" + sessionId + "; Path=/; HttpOnly; Max-Age=3600");
                    
                    sendJsonResponse(exchange, 200, 
                        String.format("{\"success\": true, \"message\": \"登录成功\", \"username\": \"%s\"}", username));
                } else {
                    sendJsonError(exchange, 401, "用户名或密码错误");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonError(exchange, 500, "登录失败: " + e.getMessage());
            }
        }
    }

    // 登出处理器
    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String sessionId = getSessionIdFromCookies(exchange.getRequestHeaders().getFirst("Cookie"));
            if (sessionId != null) {
                SessionManager.invalidateSession(sessionId);
            }
            
            // 清除Cookie
            exchange.getResponseHeaders().add("Set-Cookie", 
                "sessionId=; Path=/; HttpOnly; Max-Age=0");
            
            sendJsonResponse(exchange, 200, "{\"success\": true, \"message\": \"已登出\"}");
        }
    }

    // 检查登录状态处理器
    static class CheckLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String sessionId = getSessionIdFromCookies(exchange.getRequestHeaders().getFirst("Cookie"));
            String username = SessionManager.validateSession(sessionId);
            
            if (username != null) {
                sendJsonResponse(exchange, 200, 
                    String.format("{\"loggedIn\": true, \"username\": \"%s\"}", username));
            } else {
                sendJsonResponse(exchange, 200, "{\"loggedIn\": false}");
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
    
    // 辅助方法：发送JSON响应
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) 
            throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
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
        sendJsonResponse(exchange, statusCode, jsonResponse);
    }
    
    // 辅助方法：从Cookie中获取sessionId
    private static String getSessionIdFromCookies(String cookieHeader) {
        if (cookieHeader == null) return null;
        
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "sessionId".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }
    
    // 辅助方法：读取输入流所有字节
    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
    
    // 辅助方法：解析表单数据
    private static Map<String, String> parseFormData(String data) {
        Map<String, String> params = new HashMap<>();
        for (String pair : data.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                try {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    params.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        return params;
    }
}