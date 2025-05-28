package com.opticsvalley.irc;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
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
            if (username != null && !username.trim().isEmpty()) {
                server.addClient(username, this);

                String message;
                while (isRunning && (message = in.readLine()) != null) {
                    server.broadcast(username, message);
                }
            }
        } catch (SocketException e) {
            // 套接字关闭或连接重置，这通常发生在服务器重启或客户端断开时
            System.out.println("客户端连接断开: " + (username != null ? username : "未知用户"));
        } catch (IOException e) {
            // 其他IO异常
            System.out.println("客户端连接异常: " + (username != null ? username : "未知用户") + " - " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    public void sendMessage(String message) {
        try {
            if (out != null && !socket.isClosed()) {
                out.println(message);
                out.flush(); // 确保消息立即发送
            }
        } catch (Exception e) {
            // 发送消息失败，可能是连接已关闭
            System.out.println("发送消息给 " + username + " 失败: " + e.getMessage());
            disconnect();
        }
    }

    public void disconnect() {
        if (!isRunning) {
            // 防止多次调用
            return;
        }
        
        isRunning = false;
        try {
            if (username != null) {
                server.removeClient(username);
                username = null;
            }
            
            // 关闭资源前，发送一个断开连接的通知
            if (out != null) {
                try {
                    out.println("&c[OpticsValleyIRC] 与服务器的连接已断开");
                    out.flush();
                } catch (Exception ignored) {
                    // 忽略发送断开通知时的异常
                }
                
                out.close();
                out = null;
            }
            
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // 忽略关闭输入流时的异常
                }
                in = null;
            }
            
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // 忽略关闭套接字时的异常
                }
                socket = null;
            }
        } catch (Exception e) {
            System.out.println("关闭连接时出错: " + e.getMessage());
        }
    }
} 