package com.vitalsoft.asciistudio;

import com.vitalsoft.asciistudio.ui.MainWindow;
import javax.swing.*;

public class AsciiStudio {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
