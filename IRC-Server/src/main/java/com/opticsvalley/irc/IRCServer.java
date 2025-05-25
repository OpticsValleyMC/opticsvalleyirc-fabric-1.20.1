package com.opticsvalley.irc;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class IRCServer {
    private static final int PORT = 16688;
    private final ServerSocket serverSocket;
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Set<String> bannedUsers = ConcurrentHashMap.newKeySet();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Scanner consoleInput = new Scanner(System.in);

    public IRCServer() throws IOException {
        this.serverSocket = new ServerSocket(PORT);
        System.out.println("IRC Server started on port " + PORT);
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
        String[] parts = command.split("\\s+", 2);
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "ban":
            case "/ban":
                if (!arg.isEmpty()) {
                    banUser(arg);
                } else {
                    System.out.println("用法: ban <用户名>");
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

    public void banUser(String username) {
        bannedUsers.add(username);
        System.out.println("已封禁用户: " + username);
        
        // 向用户发送封禁通知，但不断开连接
        ClientHandler client = clients.get(username);
        if (client != null) {
            client.sendMessage("&c[OpticsValleyIRC] 你已被封禁，无法发送消息！");
            // 向其他用户广播
            broadcastSystemMessage("&c[OpticsValleyIRC] 用户 " + username + " 已被封禁");
        }
    }

    public void unbanUser(String username) {
        if (bannedUsers.remove(username)) {
            System.out.println("已解封用户: " + username);
            
            // 向用户发送解封通知
            ClientHandler client = clients.get(username);
            if (client != null) {
                client.sendMessage("&a[OpticsValleyIRC] 你已被解封，可以正常聊天了！");
            }
            
            // 广播解封通知
            String unbanMessage = "&a[OpticsValleyIRC] 用户 " + username + " 已被解封";
            broadcastSystemMessage(unbanMessage);
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
            // 如果被封禁，只通知该用户消息未发送
            ClientHandler sender = clients.get(username);
            if (sender != null) {
                sender.sendMessage("&c[OpticsValleyIRC] 你已被封禁，无法发送消息！");
            }
            return;
        }
        
        // 正常广播消息
        String formattedMessage = "&e[OpticsValleyIRC]&a<" + username + ">&r: " + message;
        clients.values().forEach(client -> client.sendMessage(formattedMessage));
    }

    public void addClient(String username, ClientHandler handler) {
        // 添加客户端，不管是否被封禁
        clients.put(username, handler);
        System.out.println("Client connected: " + username);
        
        // 如果用户被封禁，发送封禁通知
        if (isUserBanned(username)) {
            handler.sendMessage("&c[OpticsValleyIRC] 你已被封禁，无法发送消息！");
        }
    }

    public void removeClient(String username) {
        clients.remove(username);
        System.out.println("Client disconnected: " + username);
    }

    // 广播系统消息（不带用户名）
    public void broadcastSystemMessage(String message) {
        clients.values().forEach(client -> client.sendMessage(message));
    }

    public static void main(String[] args) {
        try {
            IRCServer server = new IRCServer();
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} 