import javax.swing.table.DefaultTableModel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class EditableTableModel extends DefaultTableModel {
    public EditableTableModel(Object[][] data, Object[] columnNames) {
        super(data, columnNames);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        if (column == 1) {
            return false; // Make the first column non-editable
        }
        return true; // Make all cells editable
    }
}

public class Main {
    private static final String DATABASE_URL = "jdbc:sqlite:identifier.db";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private static boolean allFieldsFilled;
    static JFrame frame = new JFrame("Internship Management System");

    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }
        setupDatabase();

        SwingUtilities.invokeLater(() -> showLoginScreen());
    }

    private static void showLoginScreen() {
        JFrame loginFrame = new JFrame("Login");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(400, 300);
        loginFrame.setLayout(new GridBagLayout());
        loginFrame.setLocationRelativeTo(null);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel instructorIdLabel = new JLabel("Instructor Id:");
        JTextField instructorIdField = new JTextField(15);

        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField(15);

        JButton loginButton = new JButton("Login");
        JLabel statusLabel = new JLabel();
        statusLabel.setForeground(Color.RED);

        gbc.gridx = 0;
        gbc.gridy = 0;
        loginFrame.add(instructorIdLabel, gbc);

        gbc.gridx = 1;
        loginFrame.add(instructorIdField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        loginFrame.add(passwordLabel, gbc);

        gbc.gridx = 1;
        loginFrame.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        loginFrame.add(loginButton, gbc);

        gbc.gridy = 3;
        loginFrame.add(statusLabel, gbc);

        loginButton.addActionListener(e -> {
            int instructorId = Integer.parseInt(instructorIdField.getText().trim());
            String password = new String(passwordField.getPassword()).trim();

            if (authenticate(instructorId, password)) {
                loginFrame.dispose();
                showMainApplication();
            } else {
                statusLabel.setText("Invalid instructorId or password.");
            }
        });

        loginFrame.setVisible(true);
    }

    private static boolean authenticate(int instructorId, String password) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            
            String query = "SELECT * FROM instructors WHERE instructorId = ? AND password = ?";
            PreparedStatement stmt = conn.prepareStatement(query);

            stmt.setInt(1, instructorId);
            stmt.setString(2, password);

            ResultSet resultSet = stmt.executeQuery();

            return resultSet.next();

        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Return false if an exception occurs or no matching record is found
        return false;
    }


    private static void showMainApplication() {

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Internship Acceptance Form", createAcceptanceFormPanel());
        tabbedPane.addTab("Intern Evaluation Form", createEvaluationFormPanel());
        tabbedPane.addTab("Internship Place Evaluation Form", createPlaceEvaluationFormPanel());

        frame.add(tabbedPane);
        frame.setVisible(true);

    }

    private static JPanel createAcceptanceFormPanel() {
        return createFormPanel(new String[]{
                "Name-Surname:", "Student ID:", "Faculty:", "Internship Dates:",
                "Institution Name:", "Institution Address:", "Institution Phone:", "Responsible Name:"
        }, "InternshipAcceptance", values -> executorService.execute(() -> saveAcceptanceForm(
                values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7]
        )));
    }

    //TODO: Overall evaluation will be text box with multiple lines
    private static JPanel createEvaluationFormPanel() {
        return createFormPanel(new String[]{
                "Name-Surname:", "Student ID:", "Evaluation Date:", "Responsible Name:", "Overall Evaluation:"
        }, "InternEvaluation", values -> executorService.execute(() -> saveEvaluationForm(
                values[0], values[1], values[2], values[3], values[4]
        )));
    }

    //TODO: Feedback will be text box with multiple lines
    private static JPanel createPlaceEvaluationFormPanel() {
        return createFormPanel(new String[]{
                "Name-Surname:", "Student ID:", "Institution Name:", "Duration:", "Feedback:"
        }, "InternshipPlaceEvaluation", values -> executorService.execute(() -> savePlaceEvaluationForm(
                values[0], values[1], values[2], values[3], values[4]
        )));
    }

    private static JPanel createFormPanel(String[] labels, String tableName, FormSubmitListener submitListener) {
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JPanel fieldsPanel = new JPanel();
        GroupLayout layout = new GroupLayout(fieldsPanel);
        fieldsPanel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel[] labelComponents = new JLabel[labels.length];
        JTextField[] textFields = new JTextField[labels.length];
        JLabel[] requiredLabels = new JLabel[labels.length];

        for (int i = 0; i < labels.length; i++) {
            labelComponents[i] = new JLabel(labels[i]);
            textFields[i] = new JTextField(20);
            textFields[i].setMaximumSize(new Dimension(200, 25));
            requiredLabels[i] = new JLabel("(This place is required*)");
            requiredLabels[i].setForeground(Color.RED);
            requiredLabels[i].setVisible(false);
        }


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
        });

        JButton deleteButton = new JButton("Delete Record");
        deleteButton.addActionListener(e -> {
            String inputId = JOptionPane.showInputDialog(null, "Enter the ID of the record to delete:");
            if (inputId != null && !inputId.trim().isEmpty()) {
                executorService.execute(() -> deleteFromDatabase(tableName, inputId));
            }
        });

        JButton logoutButton = new JButton("Logout");
        logoutButton.setBackground(Color.RED);
        logoutButton.addActionListener(e -> {
            int option = JOptionPane.showConfirmDialog(null, "Are you sure you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                frame.dispose();
                showLoginScreen();
            }
        });

        JButton searchButton = new JButton("Search/Edit");
        searchButton.addActionListener(e -> {
            String inputId = JOptionPane.showInputDialog(null, "Enter the ID of the student to search:");
            if (inputId != null && !inputId.trim().isEmpty()) {
                executorService.execute(() -> searchInDatabase(tableName, inputId));
            }
        });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        searchPanel.add(searchButton);
        formPanel.add(searchPanel, BorderLayout.NORTH);

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

        layout.setHorizontalGroup(hGroup);

        GroupLayout.SequentialGroup vGroup = layout.createSequentialGroup();

        for (int i = 0; i < labels.length; i++) {
            vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(labelComponents[i])
                    .addComponent(textFields[i])
                    .addComponent(requiredLabels[i])
            );
            vGroup.addGap(30); // Add gap between rows
        }
        layout.setVerticalGroup(vGroup);
        formPanel.add(fieldsPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(submitButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(logoutButton);
        formPanel.add(buttonPanel, BorderLayout.SOUTH);

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
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {

            // Check if the record already exists based on unique fields (e.g., studentID)
            String checkQuery = "SELECT COUNT(*) FROM InternshipAcceptance WHERE studentID = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, values[1]); // Assuming studentID is the second parameter (index 1)

                ResultSet resultSet = checkStmt.executeQuery();
                if (resultSet.next() && resultSet.getInt(1) > 0) {

                    // Record already exists
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "Record already exists with this Student ID. Please search/edit instead");
                    });
                    return; // Exit the method without saving
                }
            }

            // Proceed to save the record if all fields are filled
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                for (int i = 0; i < values.length; i++) {
                    stmt.setString(i + 1, values[i]);
                }

                if (allFieldsFilled) {
                    stmt.executeUpdate();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "Record saved successfully");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "Please fill all the fields");
                    });
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Error occurred while saving the record.", "Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }



    private static void searchInDatabase(String tableName, String studentID) {
    try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
        String query = "SELECT * FROM " + tableName + " WHERE studentID = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, studentID);
        ResultSet resultSet = stmt.executeQuery();

        if (!resultSet.isBeforeFirst()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Record not found");
            });
            return;
        }

        // Create a JTable to display the results, excluding the 'id' column
        JTable table = buildTableFromResultSet(resultSet);

        // Add an Update button
        JButton updateButton = new JButton("Update");
        updateButton.setPreferredSize(new Dimension(80, 30));

        JPanel updateButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,10));
        updateButtonPanel.add(updateButton);

        updateButton.addActionListener(e -> updateDatabase(table, tableName, studentID)

        );

        // Panel to hold the table and button
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(updateButton, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(750, 400));

        // Show the panel in a dialog
        JDialog dialog = new JDialog();
        dialog.setTitle("Search Results");
        dialog.setSize(800,400);
        dialog.setModal(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(null); // Center the dialog
        dialog.setVisible(true);

    } catch (SQLException e) {
        e.printStackTrace();
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Error occurred while searching", "Error", JOptionPane.ERROR_MESSAGE);
        });
    }
}


    private static JTable buildTableFromResultSet(ResultSet resultSet) throws SQLException {
        // Get metadata to determine the column names
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        // Create column names array, excluding the 'id' column (assumed to be the first column)
        String[] columnNames = new String[columnCount - 1];
        for (int i = 2; i <= columnCount; i++) {
            columnNames[i - 2] = metaData.getColumnName(i);
        }

        // Populate data rows, excluding the 'id' column
        java.util.List<String[]> data = new java.util.ArrayList<>();
        while (resultSet.next()) {
            String[] row = new String[columnCount - 1];
            for (int i = 2; i <= columnCount; i++) {
                row[i - 2] = resultSet.getString(i);
            }
            data.add(row);
        }

        // Create and return the JTable with an editable table model
        return new JTable(new EditableTableModel(data.toArray(new String[0][0]), columnNames));
    }

    private static void updateDatabase(JTable table, String tableName, String studentID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            // Get the column names from the table model
            EditableTableModel model = (EditableTableModel) table.getModel();
            int columnCount = model.getColumnCount();

            // Build the update query dynamically based on the columns
            StringBuilder query = new StringBuilder("UPDATE " + tableName + " SET ");
            for (int i = 0; i < columnCount; i++) {
                query.append(model.getColumnName(i)).append(" = ?");
                if (i < columnCount - 1) {
                    query.append(", ");
                }
            }
            query.append(" WHERE studentID = ?");

            PreparedStatement stmt = conn.prepareStatement(query.toString());

            // Set the values from the table
            for (int i = 0; i < columnCount; i++) {
                stmt.setString(i + 1, model.getValueAt(0, i).toString());
            }
            stmt.setString(columnCount + 1, studentID);

            // Execute the update
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Record updated successfully!");
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Update failed. Record not found.");
                });
            }

        } catch (SQLException e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Error occurred while updating", "Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }




    private static void deleteFromDatabase(String tableName, String studentID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "DELETE FROM " + tableName + " WHERE studentID = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, studentID);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
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
