package com.opticsvalley.irc;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class IRCServer {
    private static final int PORT = 16688;
    private static final String VERSION = "1.0.2";
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Set<String> bannedUsers = ConcurrentHashMap.newKeySet();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Scanner consoleInput = new Scanner(System.in);
    private final ConcurrentHashMap<String, String> banReasons = new ConcurrentHashMap<>();
    private ServerGUI gui;
    private volatile boolean isRunning = true;
    private Thread mainThread;

    public IRCServer() throws IOException {
        this.serverSocket = new ServerSocket(PORT);
        System.out.println("OpticsValley IRC, Version: " + VERSION);
        System.out.println("Service Started! PORT " + PORT);
    }

    public void start() {
        // 保存主线程引用，用于重启服务
        mainThread = Thread.currentThread();
        
        // 启动GUI
        SwingUtilities.invokeLater(() -> {
            gui = new ServerGUI(this);
            updateGUIUserList();
        });
        
        // 启动控制台命令处理线程
        startCommandProcessor();
        
        // 处理客户端连接
        while (isRunning && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                executorService.execute(clientHandler);
            } catch (IOException e) {
                if (isRunning) {
                    // 只有在服务器正常运行时才记录错误
                    // 避免在重启或关闭时的正常异常被记录
                    e.printStackTrace();
                }
            }
        }
    }

    private void startCommandProcessor() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    if (consoleInput.hasNextLine()) {
                        String command = consoleInput.nextLine().trim();
                        processCommand(command);
                    }
                } catch (NoSuchElementException | IllegalStateException e) {
                    // 控制台输入流可能在重启或关闭时被关闭
                    break;
                }
            }
        }).start();
    }

    public void processCommand(String command) {
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
            case "crash":
            case "/crash":
                if (!arg.isEmpty()) {
                    crashUser(arg);
                } else {
                    System.out.println("用法: crash <用户名>");
                }
                break;
            case "opengui":
            case "/opengui":
                openGUI();
                break;
            case "stop":
            case "/stop":
                System.out.println("正在执行停止服务命令...");
                shutdown();
                break;
            case "reboot":
            case "/reboot":
                System.out.println("正在执行重启服务命令...");
                restart();
                break;
            default:
                System.out.println("Command not found!");
                break;
        }
    }

    // 打开GUI
    private void openGUI() {
        SwingUtilities.invokeLater(() -> {
            if (gui == null) {
                gui = new ServerGUI(this);
            } else {
                gui.setVisible(true);
                gui.toFront();
                gui.requestFocus();
            }
            updateGUIUserList();
        });
        System.out.println("已重新打开GUI界面");
    }

    // 更新GUI用户列表
    private void updateGUIUserList() {
        if (gui != null) {
            List<String> userList = new ArrayList<>(clients.keySet());
            gui.updateUserList(userList);
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
        
        // 更新GUI用户列表
        updateGUIUserList();
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
        
        // 更新GUI用户列表
        updateGUIUserList();
    }

    public void removeClient(String username) {
        clients.remove(username);
        System.out.println("用户断开: " + username);
        
        // 通知所有用户有用户离开
        broadcastSystemMessage("&7[OpticsValleyIRC] 用户 " + username + " 已离开IRC");
        
        // 更新GUI用户列表
        updateGUIUserList();
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

    /**
     * 使指定用户的游戏崩溃
     * 通过发送特殊消息触发客户端崩溃
     */
    public void crashUser(String username) {
        ClientHandler client = clients.get(username);
        if (client != null) {
            System.out.println("正在使用户 " + username + " 的游戏崩溃...");
            
            // 发送特殊字符序列，触发客户端崩溃
            // 使用一个特殊的崩溃标记，客户端会将其识别为崩溃指令
            client.sendMessage("&c[CRASH_TRIGGER]&4");
            
            // 输出到服务端控制台
            System.out.println("已发送崩溃指令给用户: " + username);
            
            // 通知管理员
            broadcastSystemMessage("&4[OpticsValleyIRC] 管理员已使用户 " + username + " 的游戏崩溃");
        } else {
            System.out.println("用户 " + username + " 不在线");
        }
    }
    
    /**
     * 重启服务器
     */
    public void restart() {
        try {
            System.out.println("正在重启服务器...");
            
            // 通知所有客户端
            broadcastSystemMessage("&c[OpticsValleyIRC] 服务器正在重启，所有连接将被断开");
            
            // 标记服务器为非运行状态，以停止主循环
            isRunning = false;
            
            // 关闭所有客户端连接
            for (ClientHandler client : clients.values()) {
                client.disconnect();
            }
            
            // 清空客户端列表
            clients.clear();
            
            // 关闭当前ServerSocket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            // 等待一秒钟确保所有连接都已断开
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 重新创建ServerSocket
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("服务器已在端口 " + PORT + " 重新启动");
            } catch (IOException e) {
                System.out.println("重新创建ServerSocket失败: " + e.getMessage());
                e.printStackTrace();
                return;
            }
            
            // 重新设置状态
            isRunning = true;
            
            // 更新GUI
            updateGUIUserList();
            
            // 启动主循环
            Thread serverThread = new Thread(() -> {
                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                        executorService.execute(clientHandler);
                    } catch (IOException e) {
                        if (isRunning) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            serverThread.start();
            
            System.out.println("服务器已重启完成，等待新的连接");
            
        } catch (Exception e) {
            System.out.println("重启服务器时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 关闭服务器
     */
    public void shutdown() {
        try {
            isRunning = false;
            System.out.println("正在关闭服务器...");
            
            // 通知所有客户端
            broadcastSystemMessage("&c[OpticsValleyIRC] 服务器正在关闭，所有连接将被断开");
            
            // 关闭所有客户端连接
            for (ClientHandler client : clients.values()) {
                client.disconnect();
            }
            
            // 关闭服务器套接字
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            // 关闭线程池
            executorService.shutdownNow();
            
            // 关闭GUI
            if (gui != null) {
                gui.dispose();
            }
            
            System.out.println("服务器已关闭");
            
            // 完全退出程序，确保所有线程都被终止
            System.exit(0);
            
        } catch (Exception e) {
            System.out.println("关闭服务器时出错: " + e.getMessage());
            e.printStackTrace();
            // 即使出错也强制退出
            System.exit(1);
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