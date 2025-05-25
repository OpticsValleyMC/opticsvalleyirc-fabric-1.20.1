package com.opticsvalley.irc;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private Socket socket;
    private final IRCServer server;
    private String username;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean isRunning = true;

    public ClientHandler(Socket socket, IRCServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // 使用UTF-8编码处理输入输出
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // First message should be the username
            username = in.readLine();
            server.addClient(username, this);

            String message;
            while (isRunning && (message = in.readLine()) != null) {
                server.broadcast(username, message);
            }
        } catch (IOException e) {
            // 连接异常，不打印堆栈跟踪，保持日志整洁
            System.out.println("客户端连接异常: " + (username != null ? username : "未知用户"));
        } finally {
            disconnect();
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
            out.flush(); // 确保消息立即发送
        }
    }

    public void disconnect() {
        isRunning = false;
        try {
            if (username != null) {
                server.removeClient(username);
                username = null;
            }
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
        } catch (IOException e) {
            System.out.println("关闭连接时出错: " + e.getMessage());
        }
    }
} 