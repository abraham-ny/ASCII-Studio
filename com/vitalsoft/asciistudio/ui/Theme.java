package com.vitalsoft.asciistudio.ui;

import javax.swing.*;
import java.awt.*;

public class Theme {
    public static final Color BG          = new Color(0x1A1A1A);
    public static final Color CONTROL_BG  = new Color(0x222222);
    public static final Color FIELD_BG    = new Color(0x2A2A2A);
    public static final Color FG          = new Color(0xDDDDDD);
    public static final Color DIM         = new Color(0x888888);
    public static final Color ACCENT      = new Color(0x00D488);
    public static final Color ACCENT_DARK = new Color(0x009955);

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(FG);
        return l;
    }

    public static JButton iconBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(FIELD_BG);
        b.setForeground(FG);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x444444)),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)
        ));
        return b;
    }

    public static JButton accentBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT_DARK);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT),
            BorderFactory.createEmptyBorder(4, 14, 4, 14)
        ));
        return b;
    }

    public static void applyDark(JTabbedPane tp) {
        tp.setBackground(BG);
        tp.setForeground(FG);
    }
}
