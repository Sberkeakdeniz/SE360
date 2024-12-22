import javax.swing.table.DefaultTableModel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.*;
import java.net.*;

class SQLiteServer {
    private static final String DATABASE_URL = "jdbc:sqlite:identifier.db"; // Path to your SQLite database
    private static final int PORT = 12345; // Port number for the server

    public static void main(String[] args) {
        setupDatabase();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
                InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                PrintWriter writer = new PrintWriter(output, true);
                Connection conn = DriverManager.getConnection(DATABASE_URL)
        ) {
            String query;
            while ((query = reader.readLine()) != null) {
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    if (stmt.execute()) {
                        ResultSet rs = stmt.getResultSet();
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        // Send column headers
                        StringBuilder header = new StringBuilder();
                        for (int i = 1; i <= columnCount; i++) {
                            header.append(metaData.getColumnName(i));
                            if (i < columnCount) {
                                header.append("\t");
                            }
                        }
                        writer.println(header.toString());

                        // Send all rows with all columns
                        while (rs.next()) {
                            StringBuilder row = new StringBuilder();
                            for (int i = 1; i <= columnCount; i++) {
                                String value = rs.getString(i);
                                // Replace nulls with empty strings to avoid "null" in the table
                                row.append(value != null ? value : "");
                                if (i < columnCount) {
                                    row.append("\t");
                                }
                            }
                            writer.println(row.toString());
                        }
                        writer.println("END");
                    } else {
                        writer.println("Update Count: " + stmt.getUpdateCount());
                        writer.println("END"); // **Added "END" signal for non-SELECT queries**
                    }
                } catch (SQLException e) {
                    writer.println("ERROR: " + e.getMessage());
                    writer.println("END"); // **Ensure "END" is sent even on errors**
                }
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    private static void setupDatabase() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String createAcceptanceTable = "CREATE TABLE IF NOT EXISTS InternshipAcceptance (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, studentID TEXT UNIQUE, faculty TEXT, dates TEXT, " +
                    "institutionName TEXT, institutionAddress TEXT, institutionPhone TEXT, responsibleName TEXT)";
            String createEvaluationTable = "CREATE TABLE IF NOT EXISTS InternEvaluation (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, studentID TEXT, evaluationDate TEXT, responsibleName TEXT, evaluation TEXT)";
            String createPlaceEvaluationTable = "CREATE TABLE IF NOT EXISTS InternshipPlaceEvaluation (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, studentID TEXT, institutionName TEXT, duration TEXT, feedback TEXT)";
            String createInstructorsTable = "CREATE TABLE IF NOT EXISTS instructors (" +
                    "instructorId INTEGER PRIMARY KEY, password TEXT)";
            conn.createStatement().execute(createAcceptanceTable);
            conn.createStatement().execute(createEvaluationTable);
            conn.createStatement().execute(createPlaceEvaluationTable);
            conn.createStatement().execute(createInstructorsTable);
            System.out.println("Database setup complete.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

class SQLiteClient {
    private static final String SERVER_ADDRESS = "localhost"; // Replace with server's IP address
    private static final int SERVER_PORT = 12345; // Port number of the server

    public static String sendQuery(String query) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            writer.println(query);
            StringBuilder result = new StringBuilder();
            String response;
            while ((response = reader.readLine()) != null && !response.equals("END")) {
                result.append(response).append("\n");
            }
            return result.toString().trim(); // Trim to remove the trailing newline
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }
}

class EditableTableModel extends DefaultTableModel {
    public EditableTableModel(Object[][] data, Object[] columnNames) {
        super(data, columnNames);
    }
    @Override
    public boolean isCellEditable(int row, int column) {
        String columnName = getColumnName(column);
        // Make 'studentID' column non-editable
        if (columnName.equalsIgnoreCase("studentID")) {
            return false;
        }
        return true;
    }
}

public class Main {
    //private static final String DATABASE_URL = "jdbc:sqlite:identifier.db";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private static boolean allFieldsFilled;
    static JFrame frame = new JFrame("Internship Management System");
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy"); // Date format for Internship Dates

    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            showErrorDialog("JDBC Driver not found.", e);
            return;
        }

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
            String instructorIdText = instructorIdField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();

            if (instructorIdText.isEmpty() || password.isEmpty()) {
                statusLabel.setText("Please enter Instructor ID and Password.");
                return;
            }

            if (!isNumeric(instructorIdText)) {
                statusLabel.setText("Instructor ID must be a number.");
                return;
            }

            int instructorId = Integer.parseInt(instructorIdText);

            executorService.execute(() -> {
                boolean authenticated = authenticate(instructorId, password);
                SwingUtilities.invokeLater(() -> {
                    if (authenticated) {
                        loginFrame.dispose();
                        showMainApplication();
                    } else {
                        statusLabel.setText("Invalid Instructor ID or Password.");
                    }
                });
            });
        });

        loginFrame.setVisible(true);
    }

    private static boolean authenticate(int instructorId, String password) {
        // Sanitize inputs to prevent SQL injection
        String sanitizedPassword = password.replace("'", "''"); // Escape single quotes

        String query = "SELECT COUNT(*) FROM instructors WHERE instructorId = " + instructorId +
                " AND password = '" + sanitizedPassword + "'";

        try {
            String result = SQLiteClient.sendQuery(query); // Send query to the server
           

            if (result != null && !result.isEmpty()) {
                String[] lines = result.split("\n");
                if (lines.length >= 2) {
                    // The second line contains the count
                    return lines[1].trim().equals("1");
                }
            }
            return false;
        } catch (Exception e) {
            showErrorDialog("Error during authentication.", e);
            return false;
        }
    }

    private static void showMainApplication() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Internship Acceptance Form", createAcceptanceFormPanel());
        tabbedPane.addTab("Intern Evaluation Form", createEvaluationFormPanel());
        tabbedPane.addTab("Internship Place Evaluation Form", createPlaceEvaluationFormPanel());

        JPanel mainTabPanel = new JPanel(new BorderLayout());
        mainTabPanel.add(tabbedPane, BorderLayout.CENTER);

        frame.add(mainTabPanel);
        frame.setVisible(true);
    }

    private static JPanel createAcceptanceFormPanel() {
        return createFormPanel(new String[]{
                "Name-Surname:", "Student ID:", "Faculty:", "Internship Dates (dd.MM.yy-dd.MM.yy):",
                "Institution Name:", "Institution Address:", "Institution Phone:", "Responsible Name:"
        }, "InternshipAcceptance", values -> {
            // Input Validation
            String nameSurname = values[0].trim();
            String studentId = values[1].trim();
            String faculty = values[2].trim();
            String internshipDates = values[3].trim();
            String institutionName = values[4].trim();
            String institutionAddress = values[5].trim();
            String institutionPhone = values[6].trim();
            String responsibleName = values[7].trim();

            if (nameSurname.isEmpty() || studentId.isEmpty() || faculty.isEmpty() ||
                internshipDates.isEmpty() || institutionName.isEmpty() ||
                institutionAddress.isEmpty() || institutionPhone.isEmpty() ||
                responsibleName.isEmpty()) {
                showInfoDialog("Please fill all the required fields.");
                return;
            }

            if (!isNumeric(studentId)) {
                showInfoDialog("Student ID must be a number.");
                return;
            }

            if (!isValidDateRange(internshipDates)) {
                showInfoDialog("Internship Dates must be in the format dd.MM.yy-dd.MM.yy and the start date must be before the end date.");
                return;
            }

            // Proceed to save the form data
            executorService.execute(() -> saveAcceptanceForm(
                    nameSurname, studentId, faculty, internshipDates, institutionName, institutionAddress, institutionPhone, responsibleName
            ));
        });
    }

    private static JPanel createEvaluationFormPanel() {
        return createFormPanel(new String[]{
                "Name-Surname:", "Student ID:", "Evaluation Date:", "Responsible Name:", "Overall Evaluation:"
        }, "InternEvaluation", values -> {
            // Input Validation
            String nameSurname = values[0].trim();
            String studentId = values[1].trim();
            String evaluationDate = values[2].trim();
            String responsibleName = values[3].trim();
            String overallEvaluation = values[4].trim();

            if (nameSurname.isEmpty() || studentId.isEmpty() || evaluationDate.isEmpty() ||
                responsibleName.isEmpty() || overallEvaluation.isEmpty()) {
                showInfoDialog("Please fill all the required fields.");
                return;
            }

            if (!isNumeric(studentId)) {
                showInfoDialog("Student ID must be a number.");
                return;
            }

            // Proceed to save the form data
            executorService.execute(() -> saveEvaluationForm(
                    nameSurname, studentId, evaluationDate, responsibleName, overallEvaluation
            ));
        });
    }

    private static JPanel createPlaceEvaluationFormPanel() {
        return createFormPanel(new String[]{
                "Name-Surname:", "Student ID:", "Institution Name:", "Duration:", "Feedback:"
        }, "InternshipPlaceEvaluation", values -> {
            // Input Validation
            String nameSurname = values[0].trim();
            String studentId = values[1].trim();
            String institutionName = values[2].trim();
            String duration = values[3].trim();
            String feedback = values[4].trim();

            if (nameSurname.isEmpty() || studentId.isEmpty() || institutionName.isEmpty() ||
                duration.isEmpty() || feedback.isEmpty()) {
                showInfoDialog("Please fill all the required fields.");
                return;
            }

            if (!isNumeric(studentId)) {
                showInfoDialog("Student ID must be a number.");
                return;
            }

            // Proceed to save the form data
            executorService.execute(() -> savePlaceEvaluationForm(
                    nameSurname, studentId, institutionName, duration, feedback
            ));
        });
    }

    private static JPanel createFormPanel(String[] labels, String tableName, FormSubmitListener submitListener) {
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BorderLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.EAST; // Align labels to the right (East)

        JLabel[] labelComponents = new JLabel[labels.length];
        JTextField[] textFields = new JTextField[labels.length];
        JLabel[] requiredLabels = new JLabel[labels.length];

        // Determine the maximum width of labels to ensure uniform alignment
        int maxLabelWidth = 0;
        for (String label : labels) {
            JLabel tempLabel = new JLabel(label);
            Dimension size = tempLabel.getPreferredSize();
            if (size.width > maxLabelWidth) {
                maxLabelWidth = size.width;
            }
        }

        for (int i = 0; i < labels.length; i++) {
            labelComponents[i] = new JLabel(labels[i]);
            labelComponents[i].setPreferredSize(new Dimension(maxLabelWidth, labelComponents[i].getPreferredSize().height));

            textFields[i] = new JTextField(20);
            textFields[i].setMaximumSize(new Dimension(200, 25));
            requiredLabels[i] = new JLabel("(This field is required*)");
            requiredLabels[i].setForeground(Color.RED);
            requiredLabels[i].setVisible(false);
        }

        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.anchor = GridBagConstraints.EAST; // Ensure labels are right-aligned
            fieldsPanel.add(labelComponents[i], gbc);

            gbc.gridx = 1;
            gbc.anchor = GridBagConstraints.WEST; // Align text fields to the left
            fieldsPanel.add(textFields[i], gbc);

            gbc.gridx = 2;
            fieldsPanel.add(requiredLabels[i], gbc);
        }

        // Buttons Panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton submitButton = new JButton("Submit");
        Color defaultColor = submitButton.getBackground();
        Color hoverColor = Color.decode("#228B22"); // Forest Green
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
            allFieldsFilled = true;
            for (int i = 0; i < textFields.length; i++) {
                if (textFields[i].getText().trim().isEmpty()) {
                    requiredLabels[i].setVisible(true);
                    allFieldsFilled = false;
                } else {
                    requiredLabels[i].setVisible(false);
                }
            }

            if (!allFieldsFilled) {
                JOptionPane.showMessageDialog(null, "Please fill all the required fields.");
                return;
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
            String inputId = JOptionPane.showInputDialog(null, "Enter the Student ID of the record to delete:");
            if (inputId != null && !inputId.trim().isEmpty()) {
                if (!isNumeric(inputId.trim())) {
                    showInfoDialog("Student ID must be a number.");
                    return;
                }
                executorService.execute(() -> deleteFromDatabase(tableName, inputId.trim()));
            }
        });

        JButton logoutButton = new JButton("Logout");
        logoutButton.setBackground(Color.RED);
        logoutButton.setForeground(Color.WHITE);
        logoutButton.addActionListener(e -> {
            int option = JOptionPane.showConfirmDialog(null, "Are you sure you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                frame.dispose();
                showLoginScreen();
            }
        });

        JButton searchButton = new JButton("Search/Edit");
        searchButton.addActionListener(e -> {
            String studentID = JOptionPane.showInputDialog(frame, "Enter the Student ID to search/edit:");
            if (studentID != null && !studentID.trim().isEmpty()) {
                if (!isNumeric(studentID.trim())) {
                    showInfoDialog("Student ID must be a number.");
                    return;
                }
                executorService.execute(() -> searchInDatabase(tableName, studentID.trim()));
            }
        });

        buttonsPanel.add(submitButton);
        buttonsPanel.add(deleteButton);
        buttonsPanel.add(searchButton);
        buttonsPanel.add(logoutButton);

        formPanel.add(fieldsPanel, BorderLayout.CENTER);
        formPanel.add(buttonsPanel, BorderLayout.SOUTH);

        return formPanel;
    }

    private static void saveAcceptanceForm(String name, String studentID, String faculty, String dates,
                                           String institutionName, String institutionAddress,
                                           String institutionPhone, String responsibleName) {
        String insertQuery = String.format(
                "INSERT INTO InternshipAcceptance (name, studentID, faculty, dates, institutionName, institutionAddress, institutionPhone, responsibleName) " +
                        "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                name.replace("'", "''"), // Escape single quotes
                studentID.replace("'", "''"),
                faculty.replace("'", "''"),
                dates.replace("'", "''"),
                institutionName.replace("'", "''"),
                institutionAddress.replace("'", "''"),
                institutionPhone.replace("'", "''"),
                responsibleName.replace("'", "''")
        );

        try {
            String result = SQLiteClient.sendQuery(insertQuery); // Send the query to the server
           

            if (result.contains("UNIQUE constraint failed")) {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("A record with this Student ID already exists. Please use the Search/Edit feature to modify the existing record.");
                });
            } else if (result.toLowerCase().contains("error")) {
                SwingUtilities.invokeLater(() -> {
                    showErrorDialog("Error occurred while saving the record. " + result, null);
                });
            } else if (result.startsWith("Update Count:")) {
                // Optionally, parse the update count
                String[] parts = result.split(":");
                if (parts.length == 2) {
                    String countStr = parts[1].trim();
                    if (countStr.equals("1")) {
                        SwingUtilities.invokeLater(() -> {
                            showInfoDialog("Record saved successfully.");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            showInfoDialog("Record saved. Rows affected: " + countStr);
                        });
                    }
                } else {
                    // Fallback in case of unexpected format
                    SwingUtilities.invokeLater(() -> {
                        showInfoDialog("Record saved successfully.");
                    });
                }
            } else {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record saved successfully.");
                });
            }
        } catch (Exception e) {
            showErrorDialog("Error occurred while communicating with the server.", e);
        }
    }

    private static void saveEvaluationForm(String name, String studentID, String evaluationDate,
                                           String responsibleName, String evaluation) {
        String insertQuery = String.format(
                "INSERT INTO InternEvaluation (name, studentID, evaluationDate, responsibleName, evaluation) " +
                        "VALUES ('%s', '%s', '%s', '%s', '%s')",
                name.replace("'", "''"), // Escape single quotes
                studentID.replace("'", "''"),
                evaluationDate.replace("'", "''"),
                responsibleName.replace("'", "''"),
                evaluation.replace("'", "''")
        );

        try {
            String result = SQLiteClient.sendQuery(insertQuery); // Send the query to the server
        

            if (result.contains("UNIQUE constraint failed")) {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("A record with this Student ID already exists. Please use the Search/Edit feature to modify the existing record.");
                });
            } else if (result.toLowerCase().contains("error")) {
                SwingUtilities.invokeLater(() -> {
                    showErrorDialog("Error occurred while saving the record. " + result, null);
                });
            } else if (result.startsWith("Update Count:")) {
                String[] parts = result.split(":");
                if (parts.length == 2) {
                    String countStr = parts[1].trim();
                    if (countStr.equals("1")) {
                        SwingUtilities.invokeLater(() -> {
                            showInfoDialog("Record saved successfully.");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            showInfoDialog("Record saved. Rows affected: " + countStr);
                        });
                    }
                } else {
                    SwingUtilities.invokeLater(() -> {
                        showInfoDialog("Record saved successfully.");
                    });
                }
            } else {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record saved successfully.");
                });
            }
        } catch (Exception e) {
            showErrorDialog("Error occurred while communicating with the server.", e);
        }
    }

    private static void savePlaceEvaluationForm(String name, String studentID, String institutionName,
                                                String duration, String feedback) {
        String insertQuery = String.format(
                "INSERT INTO InternshipPlaceEvaluation (name, studentID, institutionName, duration, feedback) " +
                        "VALUES ('%s', '%s', '%s', '%s', '%s')",
                name.replace("'", "''"), // Escape single quotes
                studentID.replace("'", "''"),
                institutionName.replace("'", "''"),
                duration.replace("'", "''"),
                feedback.replace("'", "''")
        );

        try {
            String result = SQLiteClient.sendQuery(insertQuery); // Send the query to the server

            if (result.contains("UNIQUE constraint failed")) {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("A record with this Student ID already exists. Please use the Search/Edit feature to modify the existing record.");
                });
            } else if (result.toLowerCase().contains("error")) {
                SwingUtilities.invokeLater(() -> {
                    showErrorDialog("Error occurred while saving the record. " + result, null);
                });
            } else if (result.startsWith("Update Count:")) {
                String[] parts = result.split(":");
                if (parts.length == 2) {
                    String countStr = parts[1].trim();
                    if (countStr.equals("1")) {
                        SwingUtilities.invokeLater(() -> {
                            showInfoDialog("Record saved successfully.");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            showInfoDialog("Record saved. Rows affected: " + countStr);
                        });
                    }
                } else {
                    SwingUtilities.invokeLater(() -> {
                        showInfoDialog("Record saved successfully.");
                    });
                }
            } else {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record saved successfully.");
                });
            }
        } catch (Exception e) {
            showErrorDialog("Error occurred while communicating with the server.", e);
        }
    }

    private static void searchInDatabase(String tableName, String studentID) {
        try {
            // Query for all relevant tables
            String acceptanceQuery = "SELECT * FROM InternshipAcceptance WHERE studentID = '" + studentID + "'";
            String evaluationQuery = "SELECT * FROM InternEvaluation WHERE studentID = '" + studentID + "'";
            String placeEvaluationQuery = "SELECT * FROM InternshipPlaceEvaluation WHERE studentID = '" + studentID + "'";
            // Send each query to the server
            String acceptanceResult = SQLiteClient.sendQuery(acceptanceQuery);
            String evaluationResult = SQLiteClient.sendQuery(evaluationQuery);
            String placeEvaluationResult = SQLiteClient.sendQuery(placeEvaluationQuery);
            boolean hasData = acceptanceResult.contains("\n") || evaluationResult.contains("\n") || placeEvaluationResult.contains("\n");

            if (!hasData) {
                // Show "No Records Found" dialog
                SwingUtilities.invokeLater(() -> {
                    JDialog dialog = new JDialog(frame, "Search Results for Student ID: " + studentID, true);
                    dialog.setSize(400, 200);
                    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    dialog.setLayout(new BorderLayout());
                    JLabel noDataLabel = new JLabel("No records found for this Student ID", SwingConstants.CENTER);
                    dialog.add(noDataLabel, BorderLayout.CENTER);
                    dialog.setLocationRelativeTo(frame);
                    dialog.setVisible(true);
                });
                return;
            }
            // Create a JTabbedPane to display results
            JTabbedPane tabbedPane = new JTabbedPane();
            // Add Internship Acceptance Tab
            if (!acceptanceResult.trim().isEmpty()) {
                JTable acceptanceTable = buildTableFromServerResponse(acceptanceResult);
                JScrollPane acceptanceScrollPane = new JScrollPane(acceptanceTable);
                tabbedPane.addTab("Internship Acceptance", acceptanceScrollPane);
            } else {
                tabbedPane.addTab("Internship Acceptance", new JLabel("No data found.", SwingConstants.CENTER));
            }
            // Add Intern Evaluation Tab
            if (!evaluationResult.trim().isEmpty()) {
                JTable evaluationTable = buildTableFromServerResponse(evaluationResult);
                JScrollPane evaluationScrollPane = new JScrollPane(evaluationTable);
                tabbedPane.addTab("Intern Evaluation", evaluationScrollPane);
            } else {
                tabbedPane.addTab("Intern Evaluation", new JLabel("No data found.", SwingConstants.CENTER));
            }
            // Add Internship Place Evaluation Tab
            if (!placeEvaluationResult.trim().isEmpty()) {
                JTable placeEvaluationTable = buildTableFromServerResponse(placeEvaluationResult);
                JScrollPane placeEvaluationScrollPane = new JScrollPane(placeEvaluationTable);
                tabbedPane.addTab("Place Evaluation", placeEvaluationScrollPane);
            } else {
                tabbedPane.addTab("Place Evaluation", new JLabel("No data found.", SwingConstants.CENTER));
            }
            // Create Update Button
            JButton updateButton = new JButton("Update");
            updateButton.addActionListener(e -> updateStudentData(tabbedPane, studentID));
            // Panel to hold tabbedPane and update button
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(tabbedPane, BorderLayout.CENTER);
            panel.add(updateButton, BorderLayout.SOUTH);
            // Show the panel in a dialog
            SwingUtilities.invokeLater(() -> {
                JDialog dialog = new JDialog(frame, "Search Results for Student ID: " + studentID, true);
                dialog.setSize(800, 600);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.getContentPane().add(panel);
                dialog.pack();
                dialog.setLocationRelativeTo(frame);
                dialog.setVisible(true);
            });
        } catch (Exception e) {
            showErrorDialog("Error occurred while searching.", e);
        }
    }

    private static JTable buildTableFromServerResponse(String serverResponse) {
        // Parse the server response into rows and columns
        String[] rows = serverResponse.split("\n");
        if (rows.length < 2) { // At least headers and one row
            return new JTable(); // Return an empty table if no data
        }
        String[] allColumns = rows[0].split("\t"); // First line contains column headers
        List<String> columnsList = new ArrayList<>();
        // Exclude 'id' column
        int idColumnIndex = -1;
        for (int i = 0; i < allColumns.length; i++) {
            if (allColumns[i].equalsIgnoreCase("id")) {
                idColumnIndex = i;
                continue; // Skip adding 'id' to columnsList
            }
            columnsList.add(allColumns[i]);
        }
        String[] columns = columnsList.toArray(new String[0]);
        // Prepare data excluding the 'id' column
        String[][] data = new String[rows.length - 1][columns.length];
        for (int i = 1; i < rows.length; i++) {
            String[] rowData = rows[i].split("\t");
            for (int j = 0; j < columns.length; j++) {
                if (idColumnIndex != -1 && j >= idColumnIndex) {
                    data[i - 1][j] = rowData[j + 1]; // Shift by 1 if 'id' is present
                } else {
                    data[i - 1][j] = rowData[j];
                }
            }
        }
        // Create and return the JTable
        return new JTable(new EditableTableModel(data, columns));
    }

    private static void updateStudentData(JTabbedPane tabbedPane, String studentID) {
        int selectedTabIndex = tabbedPane.getSelectedIndex();
        String selectedTabTitle = tabbedPane.getTitleAt(selectedTabIndex);
        JTable table = null;
        String updateQuery = "";
        // Determine which table to update based on the selected tab
        if (selectedTabTitle.equals("Internship Acceptance")) {
            table = (JTable) ((JScrollPane) tabbedPane.getComponentAt(selectedTabIndex)).getViewport().getView();
            updateQuery = "UPDATE InternshipAcceptance SET ";
        } else if (selectedTabTitle.equals("Intern Evaluation")) {
            table = (JTable) ((JScrollPane) tabbedPane.getComponentAt(selectedTabIndex)).getViewport().getView();
            updateQuery = "UPDATE InternEvaluation SET ";
        } else if (selectedTabTitle.equals("Place Evaluation")) {
            table = (JTable) ((JScrollPane) tabbedPane.getComponentAt(selectedTabIndex)).getViewport().getView();
            updateQuery = "UPDATE InternshipPlaceEvaluation SET ";
        } else {
            JOptionPane.showMessageDialog(null, "Unknown tab selected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        final JTable finalTable = table; // Make 'table' final for use in lambda
        final String finalUpdateQuery = updateQuery; // Make 'updateQuery' final
        if (finalTable != null && finalTable.getRowCount() > 0) {
            executorService.execute(() -> updateTableData(finalTable, finalUpdateQuery, studentID));
        } else {
            JOptionPane.showMessageDialog(null, "No data to update in the selected tab.", "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void updateTableData(JTable table, String updateQuery, String studentID) {
        EditableTableModel model = (EditableTableModel) table.getModel();
        // Ensure only one record per studentID
        if (model.getRowCount() != 1) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Multiple records found. Update is not supported for multiple records.", "Error", JOptionPane.ERROR_MESSAGE);
            });
            return;
        }
        // Construct the UPDATE query with values from the table
        StringBuilder queryBuilder = new StringBuilder(updateQuery);
        for (int i = 0; i < model.getColumnCount(); i++) {
            String columnName = model.getColumnName(i);
            String value = model.getValueAt(0, i).toString().trim().replace("'", "''"); // Escape single quotes
            queryBuilder.append(columnName).append(" = '").append(value).append("', ");
        }
        queryBuilder.setLength(queryBuilder.length() - 2); // Remove the trailing comma and space
        queryBuilder.append(" WHERE studentID = '").append(studentID).append("'");
        String finalQuery = queryBuilder.toString();
        try {
            // Send the query to the server
            String result = SQLiteClient.sendQuery(finalQuery);
            if (result.toLowerCase().contains("error")) {
                SwingUtilities.invokeLater(() -> {
                    showErrorDialog("Error occurred while updating the record. " + result, null);
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Record updated successfully!");
                });
            }
        } catch (Exception e) {
            showErrorDialog("Error occurred while communicating with the server.", e);
        }
    }

    private static void deleteFromDatabase(String tableName, String studentID) {
        String confirmMessage = "Are you sure you want to delete the record for Student ID: " + studentID + "?";
        int confirm = JOptionPane.showConfirmDialog(null, confirmMessage, "Delete Confirmation", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION){
            return;
        }

        String query = String.format("DELETE FROM  " + tableName + " WHERE studentID = '%s'",
                studentID.replace("'", "''")
        );

        String result = SQLiteClient.sendQuery(query);
        int rowsAffected = Integer.parseInt(result.split(" ")[2]);

        if (rowsAffected > 0){
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Record deleted successfully.");
            });
        } else {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Record not found.");
            });
        }
    }

    @FunctionalInterface
    interface FormSubmitListener {
        void onSubmit(String[] values);
    }

    // Utility Methods for Validation and Error Handling

    private static boolean isNumeric(String str) {
        return str.matches("\\d+");
    }

    private static boolean isValidDateRange(String dateRange) {
        if (dateRange == null || !dateRange.contains("-")) {
            return false;
        }
        
        String[] dates = dateRange.split("-");
        if (dates.length != 2) {
            return false;
        }
        
        String startDateStr = dates[0].trim();
        String endDateStr = dates[1].trim();
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");
        dateFormat.setLenient(false);
        
        try {
            Date startDate = dateFormat.parse(startDateStr);
            Date endDate = dateFormat.parse(endDateStr);
            
            // Optional: Check if startDate is before endDate
            if (startDate.after(endDate)) {
                return false;
            }
            
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private static void showErrorDialog(String message, Exception e) {
        SwingUtilities.invokeLater(() -> {
            String fullMessage = message;
            if (e != null) {
                fullMessage += "\n" + e.getMessage();
            }
            JOptionPane.showMessageDialog(null, fullMessage, "Error", JOptionPane.ERROR_MESSAGE);
        });
        if (e != null) {
            e.printStackTrace();
        }
    }

    private static void showInfoDialog(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, message, "Information", JOptionPane.INFORMATION_MESSAGE);
        });
    }
}