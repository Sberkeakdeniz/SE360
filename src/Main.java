import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final String DATABASE_URL = "jdbc:sqlite:identifier.db";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return; // Exit if the driver is not found
        }
        setupDatabase();

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Internship Management System");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900, 700);

            JTabbedPane tabbedPane = new JTabbedPane();

            // Add the tabs
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
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.X_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField[] textFields = new JTextField[labels.length];
        for (int i = 0; i < labels.length; i++) {
            JPanel rowPanel = new JPanel();
            rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.Y_AXIS));
            JLabel label = new JLabel(labels[i]);
            label.setPreferredSize(new Dimension(400, 30));
            rowPanel.add(label);

            JTextField textField = new JTextField();
            textField.setMaximumSize(new Dimension(400, 30));
            textFields[i] = textField;
            rowPanel.add(textField);

            rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            formPanel.add(rowPanel);
            formPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        JButton submitButton = new JButton("Submit");
        submitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        submitButton.addActionListener(e -> {
            String[] values = new String[textFields.length];
            for (int i = 0; i < textFields.length; i++) {
                values[i] = textFields[i].getText();
            }
            submitListener.onSubmit(values); // Pass the array to the listener
        });

        JButton deleteButton = new JButton("Delete Record");
        deleteButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        deleteButton.addActionListener(e -> {
            String inputId = JOptionPane.showInputDialog(null, "Enter the ID of the record to delete:");
            if (inputId != null && !inputId.trim().isEmpty()) {
                try {
                    executorService.execute(() -> deleteFromDatabase(tableName, inputId));
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(null, "Invalid ID. Please enter a numeric value.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        formPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        formPanel.add(submitButton);
        formPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        formPanel.add(deleteButton);

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
            stmt.executeUpdate();
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
