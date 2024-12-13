import org.sqlite.SQLiteException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final String DATABASE_URL = "jdbc:sqlite:identifier.db";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private static boolean allFieldsFilled ;

    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }
        setupDatabase();

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Internship Management System");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setLocationRelativeTo(null);

            JTabbedPane tabbedPane = new JTabbedPane();
            tabbedPane.addTab("Internship Acceptance Form", createAcceptanceFormPanel());
            tabbedPane.addTab("Intern Evaluation Form", createEvaluationFormPanel());
            tabbedPane.addTab("Internship Place Evaluation Form", createPlaceEvaluationFormPanel());

            frame.add(tabbedPane);
            frame.setVisible(true);
        });
    }

    private static JPanel createAcceptanceFormPanel() {
        return createFormPanel(new String[]{
                "Name-Surname:", "Student ID:", "Faculty:", "Internship Dates:",
                "Institution Name:", "Institution Address:", "Institution Phone:", "Responsible Name:"
        },"InternshipAcceptance" ,values -> executorService.execute(() -> saveAcceptanceForm(
                values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7]
        )));
    }

    private static JPanel createEvaluationFormPanel() {
        return createFormPanel(new String[]{
                "Name-Surname:", "Student ID:", "Evaluation Date:", "Responsible Name:", "Overall Evaluation:"
        }, "InternEvaluation",values -> executorService.execute(() -> saveEvaluationForm(
                values[0], values[1], values[2], values[3], values[4]
        )));
    }

    private static JPanel createPlaceEvaluationFormPanel() {
        return createFormPanel(new String[]{
                "Name-Surname:", "Student ID:", "Institution Name:", "Duration:", "Feedback:"
        },"InternshipPlaceEvaluation", values -> executorService.execute(() -> savePlaceEvaluationForm(
                values[0], values[1], values[2], values[3], values[4]
        )));
    }

    private static JPanel createFormPanel(String[] labels, String tableName, FormSubmitListener submitListener) {
        JPanel formPanel = new JPanel();
        GroupLayout layout = new GroupLayout(formPanel);
        formPanel.setLayout(layout);
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Set gaps between components
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel[] labelComponents = new JLabel[labels.length];
        JTextField[] textFields = new JTextField[labels.length];
        JLabel[] requiredLabels = new JLabel[labels.length];

        for (int i = 0; i < labels.length; i++) {
            labelComponents[i] = new JLabel(labels[i]);
            textFields[i] = new JTextField(20);
            requiredLabels[i] = new JLabel("(This place is required*)");
            requiredLabels[i].setForeground(Color.RED);
            requiredLabels[i].setVisible(false);
        }


        // Buttons
        JButton submitButton = new JButton("Submit");
        Color defaultColor = submitButton.getBackground();
        Color hoverColor = Color.decode("#228B22");
        submitButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                submitButton.setBackground(hoverColor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                submitButton.setBackground(defaultColor);
            }
        });

        submitButton.addActionListener(e -> {


            for (int i = 0; i < textFields.length; i++) {
                if (textFields[i].getText().trim().isEmpty()) {
                    requiredLabels[i].setVisible(true);
                    allFieldsFilled = false;
                } else {
                    requiredLabels[i].setVisible(false);
                    allFieldsFilled = true;
                }
            }

            String[] values = new String[textFields.length];
            for (int i = 0; i < textFields.length; i++) {
                values[i] = textFields[i].getText();
            }
            submitListener.onSubmit(values);

            for (JTextField textField : textFields) {
                textField.setText("");
            }
        } );

        JButton deleteButton = new JButton("Delete Record");
        deleteButton.addActionListener(e -> {
            String inputId = JOptionPane.showInputDialog(null, "Enter the ID of the record to delete:");
            if (inputId != null && !inputId.trim().isEmpty()) {
                executorService.execute(() -> deleteFromDatabase(tableName, inputId));
            }
        });

        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> {
            int option = JOptionPane.showConfirmDialog(null, "Are you sure you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                frame.dispose();
                showLoginScreen();
            }
        });

        // GroupLayout Configuration
        GroupLayout.SequentialGroup hGroup = layout.createSequentialGroup();
        GroupLayout.ParallelGroup labelsGroup = layout.createParallelGroup(GroupLayout.Alignment.TRAILING);
        GroupLayout.ParallelGroup fieldsGroup = layout.createParallelGroup();
        GroupLayout.ParallelGroup requiredLabelsGroup = layout.createParallelGroup();


        for (int i = 0; i < labels.length; i++) {
            labelsGroup.addComponent(labelComponents[i]);
            fieldsGroup.addComponent(textFields[i]);
            requiredLabelsGroup.addComponent(requiredLabels[i]);
        }

        hGroup.addGroup(labelsGroup);
        hGroup.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED);
        hGroup.addGroup(fieldsGroup);
        hGroup.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED);
        hGroup.addGroup(requiredLabelsGroup);

        // Buttons in horizontal group
        hGroup.addGroup(layout.createParallelGroup()
                .addGroup(layout.createSequentialGroup()
                        .addComponent(submitButton)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deleteButton)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(logoutButton)
                ));

        layout.setHorizontalGroup(hGroup);

        GroupLayout.SequentialGroup vGroup = layout.createSequentialGroup();
        for (int i = 0; i < labels.length; i++) {
            vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(labelComponents[i])
                    .addComponent(textFields[i])
                    .addComponent(requiredLabels[i]));
        }
        vGroup.addGap(20);
        vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(submitButton)
                .addComponent(deleteButton)
                .addComponent(logoutButton)
        );

        layout.setVerticalGroup(vGroup);

        return formPanel;
    }



    private static void setupDatabase() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String createAcceptanceTable = "CREATE TABLE IF NOT EXISTS InternshipAcceptance (" +
                    "id INTEGER PRIMARY KEY, name TEXT, studentID TEXT, faculty TEXT, dates TEXT, " +
                    "institutionName TEXT, institutionAddress TEXT, institutionPhone TEXT, responsibleName TEXT)";
            String createEvaluationTable = "CREATE TABLE IF NOT EXISTS InternEvaluation (" +
                    "id INTEGER PRIMARY KEY, name TEXT, studentID TEXT, evaluationDate TEXT, responsibleName TEXT, evaluation TEXT)";
            String createPlaceEvaluationTable = "CREATE TABLE IF NOT EXISTS InternshipPlaceEvaluation (" +
                    "id INTEGER PRIMARY KEY, name TEXT, studentID TEXT, institutionName TEXT, duration TEXT, feedback TEXT)";
            conn.createStatement().execute(createAcceptanceTable);
            conn.createStatement().execute(createEvaluationTable);
            conn.createStatement().execute(createPlaceEvaluationTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void saveAcceptanceForm(String name, String studentID, String faculty, String dates,
                                           String institutionName, String institutionAddress,
                                           String institutionPhone, String responsibleName) {
        saveToDatabase("INSERT INTO InternshipAcceptance (name, studentID, faculty, dates, institutionName, " +
                        "institutionAddress, institutionPhone, responsibleName) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                name, studentID, faculty, dates, institutionName, institutionAddress, institutionPhone, responsibleName);
    }

    private static void saveEvaluationForm(String name, String studentID, String evaluationDate,
                                           String responsibleName, String evaluation) {
        saveToDatabase("INSERT INTO InternEvaluation (name, studentID, evaluationDate, responsibleName, evaluation) " +
                "VALUES (?, ?, ?, ?, ?)", name, studentID, evaluationDate, responsibleName, evaluation);
    }

    private static void savePlaceEvaluationForm(String name, String studentID, String institutionName,
                                                String duration, String feedback) {
        saveToDatabase("INSERT INTO InternshipPlaceEvaluation (name, studentID, institutionName, duration, feedback) " +
                "VALUES (?, ?, ?, ?, ?)", name, studentID, institutionName, duration, feedback);
    }

    private static void saveToDatabase(String query, String... values) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(query)) {
            for (int i = 0; i < values.length; i++) {
                stmt.setString(i + 1, values[i]);
            }
            if(allFieldsFilled){
                stmt.executeUpdate();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Record saved successfully");
                });


            } else {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Please fill all the fields");
                });
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void deleteFromDatabase(String tableName, String studentID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "DELETE FROM " + tableName + " WHERE studentID = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, studentID);
            int rowsAffected =stmt.executeUpdate();

            if(rowsAffected >0) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Record deleted successfully");
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Record not found");
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FunctionalInterface
    interface FormSubmitListener {
        void onSubmit(String[] values);
    }
}
