package com.opticsvalley.irc;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class IRCServer {
    private static final int PORT = 16688;
    private static final String VERSION = "1.0.2";
    private final ServerSocket serverSocket;
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Set<String> bannedUsers = ConcurrentHashMap.newKeySet();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Scanner consoleInput = new Scanner(System.in);
    private final ConcurrentHashMap<String, String> banReasons = new ConcurrentHashMap<>();

    public IRCServer() throws IOException {
        this.serverSocket = new ServerSocket(PORT);
        System.out.println("OpticsValley IRC, Version: " + VERSION);
        System.out.println("Service Started! PORT " + PORT);
    }

    public void start() {
        // 启动控制台命令处理线程
        startCommandProcessor();
        
        // 处理客户端连接
        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                executorService.execute(clientHandler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startCommandProcessor() {
        new Thread(() -> {
            while (true) {
                String command = consoleInput.nextLine().trim();
                processCommand(command);
            }
        }).start();
    }

    private void processCommand(String command) {
        String[] parts = command.split("\\s+", 3);
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";
        String reason = parts.length > 2 ? parts[2] : "未指定原因";

        switch (cmd) {
            case "ban":
            case "/ban":
                if (!arg.isEmpty()) {
                    banUser(arg, reason);
                } else {
                    System.out.println("用法: ban <用户名> [原因]");
                }
                break;
            case "unban":
            case "/unban":
                if (!arg.isEmpty()) {
                    unbanUser(arg);
                } else {
                    System.out.println("用法: unban <用户名>");
                }
                break;
            default:
                System.out.println("Command not found!");
                break;
        }
    }

    public void banUser(String username, String reason) {
        bannedUsers.add(username);
        banReasons.put(username, reason);
        System.out.println("已封禁用户: " + username + " 原因: " + reason);
        
        // 向用户发送封禁通知，但不断开连接
        ClientHandler client = clients.get(username);
        if (client != null) {
            client.sendMessage("&c[OpticsValleyIRC] 你已被封禁，无法发送消息！原因: " + reason);
            
            // 向其他用户广播
            String banMessage = "&c[OpticsValleyIRC] 用户 " + username + " 已被封禁，原因: " + reason;
            broadcastSystemMessage(banMessage);
            
            // 输出到服务端控制台
            System.out.println("系统消息: 用户 " + username + " 已被封禁，原因: " + reason);
        }
    }

    public void unbanUser(String username) {
        if (bannedUsers.remove(username)) {
            String reason = banReasons.remove(username);
            System.out.println("已解封用户: " + username);
            
            // 向用户发送解封通知
            ClientHandler client = clients.get(username);
            if (client != null) {
                client.sendMessage("&a[OpticsValleyIRC] 你已被解封，可以正常聊天了！");
            }
            
            // 广播解封通知
            String unbanMessage = "&a[OpticsValleyIRC] 用户 " + username + " 已被解封";
            broadcastSystemMessage(unbanMessage);
            
            // 输出到服务端控制台
            System.out.println("系统消息: 用户 " + username + " 已被解封");
        } else {
            System.out.println("该用户未被封禁: " + username);
        }
    }

    public boolean isUserBanned(String username) {
        return bannedUsers.contains(username);
    }

    public void broadcast(String username, String message) {
        // 检查发送者是否被封禁
        if (isUserBanned(username)) {
            // 如果被封禁，只通知该用户消息未发送，并显示原因
            ClientHandler sender = clients.get(username);
            if (sender != null) {
                String reason = banReasons.getOrDefault(username, "未指定原因");
                sender.sendMessage("&c[OpticsValleyIRC] 你已被封禁，无法发送消息！原因: " + reason);
            }
            return;
        }
        
        try {
            // 输出消息到服务端控制台
            System.out.println("[" + username + "]: " + message);
            
            // 正常广播消息
            String formattedMessage = "&e[OpticsValleyIRC]&a<" + username + ">&r: " + message;
            for (ClientHandler client : clients.values()) {
                client.sendMessage(formattedMessage);
            }
        } catch (Exception e) {
            System.out.println("广播消息时出错: " + e.getMessage());
        }
    }

    public void addClient(String username, ClientHandler handler) {
        // 添加客户端，不管是否被封禁
        clients.put(username, handler);
        System.out.println("用户连接: " + username);
        
        // 通知所有用户有新用户加入
        broadcastSystemMessage("&a[OpticsValleyIRC] 用户 " + username + " 已加入IRC");
        
        // 如果用户被封禁，发送封禁通知
        if (isUserBanned(username)) {
            String reason = banReasons.getOrDefault(username, "未指定原因");
            handler.sendMessage("&c[OpticsValleyIRC] 你已被封禁，无法发送消息！原因: " + reason);
        }
    }

    public void removeClient(String username) {
        clients.remove(username);
        System.out.println("用户断开: " + username);
        
        // 通知所有用户有用户离开
        broadcastSystemMessage("&7[OpticsValleyIRC] 用户 " + username + " 已离开IRC");
    }

    // 广播系统消息（不带用户名）
    public void broadcastSystemMessage(String message) {
        try {
            // 简化的控制台输出（不带颜色代码）
            String plainMessage = message.replace("&c", "").replace("&a", "").replace("&e", "").replace("&r", "");
            System.out.println("System: " + plainMessage);
            
            for (ClientHandler client : clients.values()) {
                client.sendMessage(message);
            }
        } catch (Exception e) {
            System.out.println("广播系统消息时出错: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        
        try {
            IRCServer server = new IRCServer();
            server.start();
        } catch (IOException e) {
            System.err.println("启动服务器时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 