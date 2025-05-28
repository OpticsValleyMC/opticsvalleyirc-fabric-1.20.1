package com.opticsvalley.irc;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class ServerGUI extends JFrame {
    private IRCServer server;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JButton banUnbanButton;
    private JButton crashButton;
    private JButton restartButton;
    private JButton shutdownButton;
    private JTextField commandField;
    private JButton executeButton;
    private JLabel onlineCountLabel;
    private final Font msYaHeiFont = new Font("微软雅黑", Font.PLAIN, 12);

    public ServerGUI(IRCServer server) {
        this.server = server;
        
        // 设置窗口基本属性
        setTitle("OpticsValleyIRC Server GUI");
        setSize(750, 400);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // 添加窗口关闭监听器
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("GUI窗口已关闭，使用/opengui指令可以重新打开GUI界面");
            }
        });
        
        // 初始化组件
        initComponents();
        
        // 布局组件
        layoutComponents();
        
        // 设置可见
        setVisible(true);
    }
    
    private void initComponents() {
        // 用户列表
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setFont(msYaHeiFont);
        
        // 按钮
        banUnbanButton = new JButton("封禁/解封");
        crashButton = new JButton("Crash");
        restartButton = new JButton("重启服务");
        shutdownButton = new JButton("关闭服务");
        
        // 设置所有按钮的字体
        banUnbanButton.setFont(msYaHeiFont);
        crashButton.setFont(msYaHeiFont);
        restartButton.setFont(msYaHeiFont);
        shutdownButton.setFont(msYaHeiFont);
        
        // 命令执行区域
        commandField = new JTextField(20);
        executeButton = new JButton("执行");
        
        commandField.setFont(msYaHeiFont);
        executeButton.setFont(msYaHeiFont);
        
        // 在线人数标签
        onlineCountLabel = new JLabel("总在线人数: <人数>");
        onlineCountLabel.setFont(msYaHeiFont);
        
        // 添加事件监听器
        banUnbanButton.addActionListener(e -> handleBanUnban());
        crashButton.addActionListener(e -> handleCrash());
        restartButton.addActionListener(e -> handleRestart());
        shutdownButton.addActionListener(e -> handleShutdown());
        executeButton.addActionListener(e -> handleExecuteCommand());
        commandField.addActionListener(e -> handleExecuteCommand());
    }
    
    private void layoutComponents() {
        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 用户列表面板
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        
        // 在线人数标签放在用户列表面板的顶部右侧
        JPanel userHeaderPanel = new JPanel(new BorderLayout());
        JLabel userListLabel = new JLabel("当前用户列表");
        userListLabel.setFont(msYaHeiFont);
        userHeaderPanel.add(userListLabel, BorderLayout.WEST);
        userHeaderPanel.add(onlineCountLabel, BorderLayout.EAST);
        userPanel.add(userHeaderPanel, BorderLayout.NORTH);
        
        // 操作按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel selectUserLabel = new JLabel("选择1个用户进行操作");
        selectUserLabel.setFont(msYaHeiFont);
        buttonPanel.add(selectUserLabel);
        buttonPanel.add(banUnbanButton);
        buttonPanel.add(crashButton);
        
        // 右侧操作面板
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(restartButton, BorderLayout.NORTH);
        rightPanel.add(shutdownButton, BorderLayout.SOUTH);
        
        // 命令执行面板
        JPanel commandPanel = new JPanel(new BorderLayout());
        JLabel cmdLabel = new JLabel("指令执行");
        cmdLabel.setFont(msYaHeiFont);
        commandPanel.add(cmdLabel, BorderLayout.WEST);
        commandPanel.add(commandField, BorderLayout.CENTER);
        commandPanel.add(executeButton, BorderLayout.EAST);
        
        // 组合面板
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(userPanel, BorderLayout.CENTER);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 添加到主面板
        mainPanel.add(leftPanel, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);
        mainPanel.add(commandPanel, BorderLayout.SOUTH);
        
        // 设置内容面板
        setContentPane(mainPanel);
    }
    
    private void handleBanUnban() {
        String selectedUser = userList.getSelectedValue();
        if (selectedUser != null && !selectedUser.isEmpty()) {
            if (server.isUserBanned(selectedUser)) {
                // 解封用户
                server.unbanUser(selectedUser);
            } else {
                // 封禁用户
                String reason = JOptionPane.showInputDialog(this, "请输入封禁原因:", "封禁用户", JOptionPane.QUESTION_MESSAGE);
                if (reason != null) {
                    server.banUser(selectedUser, reason);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "请先选择一个用户", "操作提示", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void handleCrash() {
        String selectedUser = userList.getSelectedValue();
        if (selectedUser != null && !selectedUser.isEmpty()) {
            int confirm = JOptionPane.showConfirmDialog(this, 
                    "确定要使用户 " + selectedUser + " 的游戏崩溃吗？", 
                    "确认操作", 
                    JOptionPane.YES_NO_OPTION);
                    
            if (confirm == JOptionPane.YES_OPTION) {
                server.crashUser(selectedUser);
            }
        } else {
            JOptionPane.showMessageDialog(this, "请先选择一个用户", "操作提示", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void handleRestart() {
        int confirm = JOptionPane.showConfirmDialog(this, 
                "确定要重启服务器吗？所有用户将断开连接。", 
                "确认重启", 
                JOptionPane.YES_NO_OPTION);
                
        if (confirm == JOptionPane.YES_OPTION) {
            server.restart();
        }
    }
    
    private void handleShutdown() {
        int confirm = JOptionPane.showConfirmDialog(this, 
                "确定要关闭服务器吗？所有用户将断开连接。", 
                "确认关闭", 
                JOptionPane.YES_NO_OPTION);
                
        if (confirm == JOptionPane.YES_OPTION) {
            server.shutdown();
        }
    }
    
    private void handleExecuteCommand() {
        String command = commandField.getText().trim();
        if (!command.isEmpty()) {
            server.processCommand(command);
            commandField.setText("");
        }
    }
    
    // 更新用户列表
    public void updateUserList(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String user : users) {
                userListModel.addElement(user);
            }
            updateOnlineCount(users.size());
        });
    }
    
    // 更新在线人数
    private void updateOnlineCount(int count) {
        onlineCountLabel.setText("总在线人数: " + count);
    }
} 