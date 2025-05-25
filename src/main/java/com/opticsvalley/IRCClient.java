package com.opticsvalley;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class IRCClient {
    private static final String HOST = "localhost";
    private static final int PORT = 16688;
    private static final int RECONNECT_DELAY = 5000; // 5秒

    private final String username;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Thread messageListener;

    public IRCClient(String username) {
        this.username = username;
    }

    public void connect() {
        shouldReconnect = true;
        tryConnect();
    }

    private void tryConnect() {
        try {
            socket = new Socket(HOST, PORT);
            // 使用UTF-8编码
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // 发送用户名
            out.println(username);
            connected = true;
            
            // 发送游戏内消息
            sendGameMessage("§a[OpticsValleyIRC] 已连接到IRC服务器");

            // 启动消息监听线程
            startMessageListener();
        } catch (IOException e) {
            connected = false;
            sendGameMessage("§c[OpticsValleyIRC] 连接失败，5秒后重试...");
            
            if (shouldReconnect) {
                scheduler.schedule(this::tryConnect, RECONNECT_DELAY, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void startMessageListener() {
        messageListener = new Thread(() -> {
            try {
                String message;
                while (connected && (message = in.readLine()) != null) {
                    final String finalMessage = message;
                    
                    // 所有消息都正常显示，不需要特殊处理封禁消息
                    MinecraftClient.getInstance().execute(() ->
                        sendGameMessage(convertColorCodes(finalMessage))
                    );
                }
            } catch (IOException e) {
                if (connected && shouldReconnect) {
                    connected = false;
                    sendGameMessage("§c[OpticsValleyIRC] 连接断开，重连中...");
                    scheduler.schedule(this::tryConnect, RECONNECT_DELAY, TimeUnit.MILLISECONDS);
                }
            }
        });
        messageListener.start();
    }

    public void sendMessage(String message) {
        if (connected && out != null) {
            out.println(message);
            out.flush(); // 确保消息立即发送
        } else {
            sendGameMessage("§c[OpticsValleyIRC] 未连接到服务器，无法发送消息");
            // 如果没有连接，尝试重连
            if (!connected && shouldReconnect) {
                sendGameMessage("§e[OpticsValleyIRC] 正在尝试重新连接...");
                tryConnect();
            }
        }
    }

    public void disconnect() {
        shouldReconnect = false;
        connected = false;
        try {
            if (out != null) {
                out.close();
                out = null;
            }
            if (in != null) {
                in.close();
                in = null;
            }
            if (socket != null) {
                socket.close();
                socket = null;
            }
            if (messageListener != null) {
                messageListener.interrupt();
                messageListener = null;
            }
            scheduler.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
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