import javax.swing.table.DefaultTableModel;
import javax.swing.*;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
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
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, studentID TEXT UNIQUE, evaluationDate TEXT, responsibleName TEXT, evaluation TEXT)";
            String createPlaceEvaluationTable = "CREATE TABLE IF NOT EXISTS InternshipPlaceEvaluation (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, " +
                    "studentID TEXT UNIQUE, " +
                    "institutionName TEXT, " +
                    "duration TEXT, " +
                    "salary TEXT, " +
                    "transportation TEXT, " +
                    "meals TEXT, " +
                    "practiceTheory TEXT, " +
                    "foreignLanguage TEXT, " +
                    "responsibilities TEXT, " +
                    "workingSpace TEXT, " +
                    "factoryConditions TEXT, " +
                    "recommendation TEXT, " +
                    "futureWork TEXT, " +
                    "processScore TEXT, " +
                    "decisionMaking TEXT, " +
                    "expectations TEXT, " +
                    "researchDevelopment TEXT, " +
                    "comments TEXT, " +
                    "reasonForChoice TEXT, " +
                    "analysisMethodsLearned TEXT, " +
                    "courseAssociation TEXT, " +
                    "workAssociation TEXT, " +
                    "knowledgeLacks TEXT, " +
                    "positiveAspects TEXT, " +
                    "negativeAspects TEXT)";
            String createInstructorsTable = "CREATE TABLE IF NOT EXISTS instructors (" +
                    "instructorId INTEGER PRIMARY KEY, password TEXT)";
            String createUser = "INSERT INTO instructors (instructorId, password) SELECT 123, '123' WHERE NOT EXISTS (SELECT 1 FROM instructors WHERE instructorId = 123)";
            conn.createStatement().execute(createAcceptanceTable);
            conn.createStatement().execute(createEvaluationTable);
            conn.createStatement().execute(createPlaceEvaluationTable);
            conn.createStatement().execute(createInstructorsTable);
            conn.createStatement().execute(createUser);
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

        frame.add(tabbedPane, BorderLayout.CENTER);
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
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Create a panel for the form with GridBagLayout
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Basic information fields
        String[] basicLabels = {"Name-Surname:", "Student ID:", "Institution Name:", "Duration:"};
        JTextField[] basicFields = new JTextField[basicLabels.length];
        
        // Add basic information fields
        for (int i = 0; i < basicLabels.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.gridwidth = 1;
            
            // Create panel for label to ensure left alignment
            JPanel labelPanel = new JPanel(new BorderLayout());
            JLabel label = new JLabel(basicLabels[i]);
            labelPanel.add(label, BorderLayout.WEST);
            labelPanel.setBackground(formPanel.getBackground());
            gbc.anchor = GridBagConstraints.WEST;
            formPanel.add(labelPanel, gbc);

            gbc.gridx = 1;
            gbc.gridwidth = 1;
            basicFields[i] = new JTextField(20);
            formPanel.add(basicFields[i], gbc);
        }

        // Salary field with Yes/No combo box
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        JPanel salaryLabelPanel = new JPanel(new BorderLayout());
        salaryLabelPanel.add(new JLabel("Was any salary paid?:"), BorderLayout.WEST);
        salaryLabelPanel.setBackground(formPanel.getBackground());
        formPanel.add(salaryLabelPanel, gbc);

        gbc.gridx = 1;
        JComboBox<String> salaryCombo = new JComboBox<>(new String[]{"Yes", "No"});
        salaryCombo.setPreferredSize(new Dimension(150, salaryCombo.getPreferredSize().height));
        formPanel.add(salaryCombo, gbc);

        // Evaluation questions with combo boxes
        String[] evaluationQuestions = {
            "Was any transportation opportunity provided?",
            "Was any meal opportunity provided?",
            "Were you able to practice the theoretical knowledge?",
            "Were you able to use your foreign languages?",
            "Did your foreign language level help you carry out your responsibilities?",
            "Were you provided with an individual working place?",
            "Factory/workshop conditions:",
            "Would you suggest this institution to your friends?",
            "Would you want to work at this institution after graduation?"
        };

        String[] evaluationOptions = {"Hiç/Never", "Az/Little", "Orta/Moderately", "Çok/Very much"};
        JComboBox<String>[] evaluationCombos = new JComboBox[evaluationQuestions.length];

        // Add evaluation questions with combo boxes
        for (int i = 0; i < evaluationQuestions.length; i++) {
            gbc.gridy++;
            gbc.gridx = 0;
            gbc.gridwidth = 1;
            
            // Create panel for label to ensure left alignment
            JPanel labelPanel = new JPanel(new BorderLayout());
            JLabel label = new JLabel(evaluationQuestions[i]);
            labelPanel.add(label, BorderLayout.WEST);
            labelPanel.setBackground(formPanel.getBackground());
            formPanel.add(labelPanel, gbc);

            gbc.gridx = 1;
            evaluationCombos[i] = new JComboBox<>(evaluationOptions);
            evaluationCombos[i].setPreferredSize(new Dimension(150, evaluationCombos[i].getPreferredSize().height));
            formPanel.add(evaluationCombos[i], gbc);
        }

        // Score fields (1-5)
        String[] scoreLabels = {
            "Internship process score (1-5):",
            "Decision making score (1-5):",
            "Expectations met score (1-5):",
            "Research and development contribution score (1-5):"
        };
        JSpinner[] scoreSpinners = new JSpinner[scoreLabels.length];

        // Add score fields with spinners
        for (int i = 0; i < scoreLabels.length; i++) {
            gbc.gridy++;
            gbc.gridx = 0;
            gbc.gridwidth = 1;
            
            JPanel labelPanel = new JPanel(new BorderLayout());
            JLabel label = new JLabel(scoreLabels[i]);
            labelPanel.add(label, BorderLayout.WEST);
            labelPanel.setBackground(formPanel.getBackground());
            formPanel.add(labelPanel, gbc);

            gbc.gridx = 1;
            SpinnerNumberModel spinnerModel = new SpinnerNumberModel(1, 1, 5, 1);
            scoreSpinners[i] = new JSpinner(spinnerModel);
            scoreSpinners[i].setPreferredSize(new Dimension(150, scoreSpinners[i].getPreferredSize().height));
            formPanel.add(scoreSpinners[i], gbc);
        }

        // After the score spinners section, add text areas
        String[] textAreaLabels = {
            "Please state your comments and suggestions:",
            "What is your reason in choosing the named institution for your internship?",
            "At the institution I performed my internship, I learned to use the analysis methods (optimization techniques, statistical analysis techniques, design etc.) I was thought in the education program:",
            "I was able to associate the courses I took during my education...",
            "... with the works",
            "During the internship process, I realized that I have lack of knowledge in the subjects such as...",
            "Identify the 3 most positive aspects of the institution you performed your internship at:",
            "Identify the 3 most negative aspects of the institution you performed your internship at:"
        };

        // Add text areas
        JTextArea[] textAreas = new JTextArea[textAreaLabels.length];
        
        for (int i = 0; i < textAreaLabels.length; i++) {
            gbc.gridy++;
            gbc.gridx = 0;
            gbc.gridwidth = 3; // Span across all columns
            
            // Create panel for label to ensure left alignment
            JPanel labelPanel = new JPanel(new BorderLayout());
            JLabel label = new JLabel(textAreaLabels[i]);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            labelPanel.add(label, BorderLayout.WEST);
            labelPanel.setBackground(formPanel.getBackground()); // Match parent background
            formPanel.add(labelPanel, gbc);

            gbc.gridy++;
            // Create and configure text area
            textAreas[i] = new JTextArea(4, 40); // 4 rows, 40 columns
            textAreas[i].setLineWrap(true);
            textAreas[i].setWrapStyleWord(true);
            
            // Add scrolling to text area
            JScrollPane scrollPane = new JScrollPane(textAreas[i]);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            
            // Add some margin between label and text area
            gbc.insets = new Insets(2, 5, 10, 5);
            formPanel.add(scrollPane, gbc);
            // Reset insets for next components
            gbc.insets = new Insets(5, 5, 5, 5);
        }

        // Buttons Panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton submitButton = new JButton("Submit");
        JButton deleteButton = new JButton("Delete Record");
        JButton searchButton = new JButton("Search/Edit");
        JButton logoutButton = new JButton("Logout");
        JButton exportAllFormsButton = new JButton("Export All Forms");
        
        // Style buttons
        logoutButton.setBackground(Color.RED);
        logoutButton.setForeground(Color.WHITE);
        exportAllFormsButton.setBackground(new Color(30, 144, 255));
        exportAllFormsButton.setForeground(Color.WHITE);
        exportAllFormsButton.setFocusPainted(false);
        exportAllFormsButton.setFont(new Font("Tahoma", Font.BOLD, 12));

        // Add action listeners
        submitButton.addActionListener(e -> {
            // Gather all form data
            List<String> formDataList = new ArrayList<>();
            
            // Add basic fields
            for (int i = 0; i < basicFields.length; i++) {
                formDataList.add(basicFields[i].getText().trim());
            }
            
            // Add salary selection
            formDataList.add((String) salaryCombo.getSelectedItem());
            
            // Add evaluation combo selections
            for (JComboBox<String> combo : evaluationCombos) {
                formDataList.add((String) combo.getSelectedItem());
            }
            
            // Add scores
            for (JSpinner spinner : scoreSpinners) {
                formDataList.add(spinner.getValue().toString());
            }
            
            // Add text area contents
            for (JTextArea textArea : textAreas) {
                formDataList.add(textArea.getText().trim());
            }

            String[] formData = formDataList.toArray(new String[0]);

            // Validate and save
            if (!validateFormData(formData)) {
                showInfoDialog("Please fill all required fields.");
                return;
            }

            executorService.execute(() -> savePlaceEvaluationForm(formData));
        });

        deleteButton.addActionListener(e -> {
            String inputId = JOptionPane.showInputDialog(frame, "Enter the Student ID of the record to delete:");
            if (inputId != null && !inputId.trim().isEmpty()) {
                if (!isNumeric(inputId.trim())) {
                    showInfoDialog("Student ID must be a number.");
                    return;
                }
                executorService.execute(() -> deleteFromDatabase("InternshipPlaceEvaluation", inputId.trim()));
            }
        });

        searchButton.addActionListener(e -> {
            String studentID = JOptionPane.showInputDialog(frame, "Enter the Student ID to search/edit:");
            if (studentID != null && !studentID.trim().isEmpty()) {
                if (!isNumeric(studentID.trim())) {
                    showInfoDialog("Student ID must be a number.");
                    return;
                }
                executorService.execute(() -> searchInDatabase(studentID.trim()));
            }
        });

        logoutButton.addActionListener(e -> {
            int option = JOptionPane.showConfirmDialog(frame, "Are you sure you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                frame.dispose();
                showLoginScreen();
            }
        });

        exportAllFormsButton.addActionListener(e -> {
            exportAllFormsToExcel();
        });

        // Add buttons to panel in correct order
        buttonsPanel.add(exportAllFormsButton);
        buttonsPanel.add(submitButton);
        buttonsPanel.add(deleteButton);
        buttonsPanel.add(searchButton);
        buttonsPanel.add(logoutButton);

        // Add components to main panel
        JScrollPane scrollPane = new JScrollPane(formPanel);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        return mainPanel;
    }

    // Helper method to create wrapped labels
    private static JLabel createWrappedLabel(String text) {
        JLabel label = new JLabel("<html><body style='width: 200px'>" + text + "</body></html>", SwingConstants.RIGHT);
        return label;
    }

    // Helper method to validate form data
    private static boolean validateFormData(String[] formData) {
        // Check basic fields
        for (int i = 0; i < 4; i++) {
            if (formData[i].isEmpty()) {
                return false;
            }
        }
        
        // Check student ID is numeric
        if (!isNumeric(formData[1])) {
            showInfoDialog("Student ID must be a number.");
            return false;
        }

        return true;
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

        // Add Export All Forms button
        JButton exportAllFormsButton = new JButton("Export All Forms");
        exportAllFormsButton.setBackground(new Color(30, 144, 255)); // Dodger Blue
        exportAllFormsButton.setForeground(Color.WHITE);
        exportAllFormsButton.setFocusPainted(false);
        exportAllFormsButton.setFont(new Font("Tahoma", Font.BOLD, 12));
        exportAllFormsButton.addActionListener(e -> {
            exportAllFormsToExcel();
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
            System.out.println("Search button clicked"); // Debug message
            String studentID = JOptionPane.showInputDialog(frame, "Enter the Student ID to search/edit:");
            if (studentID != null && !studentID.trim().isEmpty()) {
                if (!isNumeric(studentID.trim())) {
                    showInfoDialog("Student ID must be a number.");
                    return;
                }
                executorService.execute(() -> searchInDatabase(studentID.trim()));
            }
        });

        buttonsPanel.add(exportAllFormsButton); // Add Export All Forms button first
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

    private static void savePlaceEvaluationForm(String[] values) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("INSERT INTO InternshipPlaceEvaluation (name, studentID, institutionName, duration, ");
        queryBuilder.append("salary, transportation, meals, practiceTheory, foreignLanguage, responsibilities, ");
        queryBuilder.append("workingSpace, factoryConditions, recommendation, futureWork, processScore, ");
        queryBuilder.append("decisionMaking, expectations, researchDevelopment, comments, reasonForChoice, ");
        queryBuilder.append("analysisMethodsLearned, courseAssociation, workAssociation, knowledgeLacks, ");
        queryBuilder.append("positiveAspects, negativeAspects) VALUES ('");
        
        // Escape all values and join them
        String escapedValues = String.join("', '", 
            java.util.Arrays.stream(values)
                .map(v -> v.replace("'", "''"))
                .toArray(String[]::new));
        
        queryBuilder.append(escapedValues).append("')");
         try {
            String result = SQLiteClient.sendQuery(queryBuilder.toString());
             if (result.contains("UNIQUE constraint failed")) {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("A record with this Student ID already exists. Please use the Search/Edit feature to modify the existing record.");
                });
            } else if (result.toLowerCase().contains("error")) {
                SwingUtilities.invokeLater(() -> {
                    showErrorDialog("Error occurred while saving the record. " + result, null);
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record saved successfully.");
                });
            }
        } catch (Exception e) {
            showErrorDialog("Error occurred while communicating with the server.", e);
        } 
    }

    private static void searchInDatabase(String studentID) {
        try {
            // Initialize a map to hold table names and their corresponding query results
            Map<String, String> tableResults = new HashMap<>();

            // List of tables to search
            String[] tables = {"InternshipAcceptance", "InternEvaluation", "InternshipPlaceEvaluation"};

            // Query each table for the given studentID
            for (String table : tables) {
                String query = "SELECT * FROM " + table + " WHERE studentID = '" + studentID.replace("'", "''") + "'";
                String result = SQLiteClient.sendQuery(query);
                if (result != null && result.contains("\n")) { // Assuming that a successful query returns data separated by newlines
                    tableResults.put(table, result);
                }
            }

            // If no records found in any table
            if (tableResults.isEmpty()) {
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

            // Create dialog with a tabbed pane
            SwingUtilities.invokeLater(() -> {
                JDialog dialog = new JDialog(frame, "Search Results for Student ID: " + studentID, true);
                dialog.setLayout(new BorderLayout(10, 10));
                dialog.setSize(800, 600);

                JTabbedPane tabbedPane = new JTabbedPane();

                // Iterate through the results and add tabs accordingly
                for (Map.Entry<String, String> entry : tableResults.entrySet()) {
                    String tableName = entry.getKey();
                    String queryResult = entry.getValue();

                    // Parse the data
                    String[] rows = queryResult.split("\n");
                    if (rows.length < 2) {
                        continue; // Skip if there's no data
                    }
                    String[] headers = rows[0].split("\t");
                    String[] data = rows[1].split("\t");

                    // Determine which panel to create based on the table name
                    switch (tableName) {
                        case "InternshipAcceptance":
                            tabbedPane.addTab("Internship Acceptance", createAcceptanceSearchPanel(headers, data));
                            break;
                        case "InternEvaluation":
                            tabbedPane.addTab("Intern Evaluation", createEvaluationSearchPanel(headers, data));
                            break;
                        case "InternshipPlaceEvaluation":
                            tabbedPane.addTab("Internship Place Evaluation", new JScrollPane(createPlaceEvaluationSearchPanel(headers, data)));
                            break;
                        default:
                            tabbedPane.addTab(tableName, new JPanel());
                    }
                }

                // Buttons Panel
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
                JButton updateButton = new JButton("Update");
                updateButton.addActionListener(e -> {
                    // Retrieve the selected tab
                    int selectedIndex = tabbedPane.getSelectedIndex();
                    String selectedTab = tabbedPane.getTitleAt(selectedIndex);
                    Component selectedComponent = tabbedPane.getComponentAt(selectedIndex);

                    JPanel selectedPanel;

                    // Check if the selected component is a JScrollPane
                    if (selectedComponent instanceof JScrollPane) {
                        JScrollPane scrollPane = (JScrollPane) selectedComponent;
                        selectedPanel = (JPanel) scrollPane.getViewport().getView();
                    } else if (selectedComponent instanceof JPanel) {
                        selectedPanel = (JPanel) selectedComponent;
                    } else {
                        showErrorDialog("Unexpected component type in tabbed pane.", null);
                        return;
                    }

                    try {
                        switch (selectedTab) {
                            case "Internship Acceptance":
                                Object[] acceptanceFields = (Object[]) selectedPanel.getClientProperty("fields");
                                updateAcceptanceRecord(acceptanceFields, studentID);
                                break;
                            case "Intern Evaluation":
                                Object[] evaluationFields = (Object[]) selectedPanel.getClientProperty("fields");
                                updateEvaluationRecord(evaluationFields, studentID);
                                break;
                            case "Internship Place Evaluation":
                                Object[] placeFields = (Object[]) selectedPanel.getClientProperty("fields");
                                updatePlaceEvaluationRecord(placeFields, studentID);
                                break;
                            default:
                                // Handle other tabs if any
                                showInfoDialog("No update functionality implemented for this tab.");
                        }
                        dialog.dispose();
                    } catch (Exception ex) {
                        showErrorDialog("Error occurred while updating the record.", ex);
                    }
                });

                // **Add Export All Forms button**
                JButton exportAllFormsButton = new JButton("Export All Forms");
                exportAllFormsButton.setBackground(new Color(30, 144, 255)); // Dodger Blue
                exportAllFormsButton.setForeground(Color.WHITE); // White text for contrast
                exportAllFormsButton.setFocusPainted(false);
                exportAllFormsButton.setFont(new Font("Tahoma", Font.BOLD, 12));
                exportAllFormsButton.addActionListener(e -> {
                    exportAllFormsToExcel();
                });
                buttonPanel.add(exportAllFormsButton); // **Removed Export All Forms button**

                // **Add the Export to Excel button**
                JButton exportButton = new JButton("Export to Excel");
                exportButton.addActionListener(e -> {
                    exportRecordToExcel(tableResults, studentID);
                });
                buttonPanel.add(updateButton);
                buttonPanel.add(exportButton); // **Added Export button**

                dialog.add(tabbedPane, BorderLayout.CENTER);
                dialog.add(buttonPanel, BorderLayout.SOUTH);

                dialog.setLocationRelativeTo(frame);
                dialog.setVisible(true);
            });

        } catch (Exception e) {
            showErrorDialog("Error occurred while searching.", e);
        }
    }

    // Helper method for Internship Acceptance form results
    private static JPanel createAcceptanceSearchPanel(String[] headers, String[] data) {
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Center all components
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // Define the fields
        String[] labels = {"Name-Surname:", "Faculty:", "Dates:", "Institution Name:", "Institution Address:", "Institution Phone:", "Responsible Name:"};
        JTextField nameField = new JTextField(findValueByHeader(headers, data, "name"), 20);
        JTextField facultyField = new JTextField(findValueByHeader(headers, data, "faculty"), 20);
        JTextField datesField = new JTextField(findValueByHeader(headers, data, "dates"), 20);
        JTextField institutionNameField = new JTextField(findValueByHeader(headers, data, "institutionName"), 20);
        JTextField institutionAddressField = new JTextField(findValueByHeader(headers, data, "institutionAddress"), 20);
        JTextField institutionPhoneField = new JTextField(findValueByHeader(headers, data, "institutionPhone"), 20);
        JTextField responsibleNameField = new JTextField(findValueByHeader(headers, data, "responsibleName"), 20);
    
        // Array of labels and corresponding fields
        Object[][] fields = {
            {labels[0], nameField},
            {labels[1], facultyField},
            {labels[2], datesField},
            {labels[3], institutionNameField},
            {labels[4], institutionAddressField},
            {labels[5], institutionPhoneField},
            {labels[6], responsibleNameField}
        };
    
        // Add labels and fields to the formPanel
        for (Object[] field : fields) {
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            formPanel.add(new JLabel((String) field[0]), gbc);
    
            gbc.gridx = 1;
            formPanel.add((Component) field[1], gbc);
            
            gbc.gridy++;
        }
    
        // Store references to editable fields for later use
        formPanel.putClientProperty("fields", fields);
    
        return formPanel;
    }

    // Helper method for Intern Evaluation form results
    private static JPanel createEvaluationSearchPanel(String[] headers, String[] data) {
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Center all components
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Define the fields
        String[] labels = {"Name-Surname:", "Evaluation Date:", "Responsible Name:", "Evaluation:"};
        JTextField nameField = new JTextField(findValueByHeader(headers, data, "name"), 20);
        JTextField evaluationDateField = new JTextField(findValueByHeader(headers, data, "evaluationDate"), 20);
        JTextField responsibleNameField = new JTextField(findValueByHeader(headers, data, "responsibleName"), 20);
        JTextField evaluationField = new JTextField(findValueByHeader(headers, data, "evaluation"), 20);

        // Array of labels and corresponding fields
        Object[][] fields = {
            {labels[0], nameField},
            {labels[1], evaluationDateField},
            {labels[2], responsibleNameField},
            {labels[3], evaluationField}
        };

        // Add labels and fields to the formPanel
        for (Object[] field : fields) {
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            formPanel.add(new JLabel((String) field[0]), gbc);

            gbc.gridx = 1;
            formPanel.add((Component) field[1], gbc);
            
            gbc.gridy++;
        }

        // Store references to editable fields for later use
        formPanel.putClientProperty("fields", fields);

        return formPanel;
    }

    // Helper method for Place Evaluation form results
    private static JPanel createPlaceEvaluationSearchPanel(String[] headers, String[] data) {
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Define labels and corresponding fields
        String[] labels = {
            "Name-Surname:", "Institution Name:", "Duration:",
            "Salary Paid:", "Transportation Provided:", "Meals Provided:", "Practice Theory:",
            "Foreign Language Use:", "Responsibilities:", "Working Space:", "Factory Conditions:",
            "Recommendation:", "Future Work:", "Process Score:", "Decision Making:",
            "Expectations:", "Research Development:", "Comments:",
            "Reason for Choice:", "Analysis Methods Learned:", "Course Association:",
            "Work Association:", "Knowledge Lacks:", "Positive Aspects:", "Negative Aspects:"
        };

        // Initialize fields
        JTextField nameField = new JTextField(findValueByHeader(headers, data, "name"));
        JTextField institutionNameField = new JTextField(findValueByHeader(headers, data, "institutionName"));
        JTextField durationField = new JTextField(findValueByHeader(headers, data, "duration"));

        JComboBox<String> salaryCombo = new JComboBox<>(new String[]{"Yes", "No"});
        salaryCombo.setSelectedItem(findValueByHeader(headers, data, "salary"));

        JComboBox<String> transportationCombo = new JComboBox<>(new String[]{"Hiç/Never", "Az/Little", "Orta/Moderately", "Çok/Very much"});
        transportationCombo.setSelectedItem(findValueByHeader(headers, data, "transportation"));

        JComboBox<String> mealsCombo = new JComboBox<>(new String[]{"Hiç/Never", "Az/Little", "Orta/Moderately", "Çok/Very much"});
        mealsCombo.setSelectedItem(findValueByHeader(headers, data, "meals"));

        JComboBox<String> practiceTheoryCombo = new JComboBox<>(new String[]{"Hiç/Never", "Az/Little", "Orta/Moderately", "Çok/Very much"});
        practiceTheoryCombo.setSelectedItem(findValueByHeader(headers, data, "practiceTheory"));

        JComboBox<String> foreignLanguageCombo = new JComboBox<>(new String[]{"Hiç/Never", "Az/Little", "Orta/Moderately", "Çok/Very much"});
        foreignLanguageCombo.setSelectedItem(findValueByHeader(headers, data, "foreignLanguage"));

        JTextField responsibilitiesField = new JTextField(findValueByHeader(headers, data, "responsibilities"));
        JTextField workingSpaceField = new JTextField(findValueByHeader(headers, data, "workingSpace"));
        JTextField factoryConditionsField = new JTextField(findValueByHeader(headers, data, "factoryConditions"));
        JTextField recommendationField = new JTextField(findValueByHeader(headers, data, "recommendation"));
        JTextField futureWorkField = new JTextField(findValueByHeader(headers, data, "futureWork"));

        JSpinner processScoreSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(findValueByHeader(headers, data, "processScore")), 1, 5, 1));
        JSpinner decisionMakingSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(findValueByHeader(headers, data, "decisionMaking")), 1, 5, 1));
        JSpinner expectationsSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(findValueByHeader(headers, data, "expectations")), 1, 5, 1));
        JSpinner researchDevelopmentSpinner = new JSpinner(new SpinnerNumberModel(Integer.parseInt(findValueByHeader(headers, data, "researchDevelopment")), 1, 5, 1));

        JTextArea commentsArea = new JTextArea(findValueByHeader(headers, data, "comments"));
        JTextArea reasonForChoiceArea = new JTextArea(findValueByHeader(headers, data, "reasonForChoice"));
        JTextArea analysisMethodsLearnedArea = new JTextArea(findValueByHeader(headers, data, "analysisMethodsLearned"));
        JTextArea courseAssociationArea = new JTextArea(findValueByHeader(headers, data, "courseAssociation"));
        JTextArea workAssociationArea = new JTextArea(findValueByHeader(headers, data, "workAssociation"));
        JTextArea knowledgeLacksArea = new JTextArea(findValueByHeader(headers, data, "knowledgeLacks"));
        JTextArea positiveAspectsArea = new JTextArea(findValueByHeader(headers, data, "positiveAspects"));
        JTextArea negativeAspectsArea = new JTextArea(findValueByHeader(headers, data, "negativeAspects"));

        // Add labels and fields to the formPanel
        Object[][] fields = {
            {labels[0], nameField},
            {labels[1], institutionNameField},
            {labels[2], durationField},
            {labels[3], salaryCombo},
            {labels[4], transportationCombo},
            {labels[5], mealsCombo},
            {labels[6], practiceTheoryCombo},
            {labels[7], foreignLanguageCombo},
            {labels[8], responsibilitiesField},
            {labels[9], workingSpaceField},
            {labels[10], factoryConditionsField},
            {labels[11], recommendationField},
            {labels[12], futureWorkField},
            {labels[13], processScoreSpinner},
            {labels[14], decisionMakingSpinner},
            {labels[15], expectationsSpinner},
            {labels[16], researchDevelopmentSpinner},
            {labels[17], new JScrollPane(commentsArea)},
            {labels[18], new JScrollPane(reasonForChoiceArea)},
            {labels[19], new JScrollPane(analysisMethodsLearnedArea)},
            {labels[20], new JScrollPane(courseAssociationArea)},
            {labels[21], new JScrollPane(workAssociationArea)},
            {labels[22], new JScrollPane(knowledgeLacksArea)},
            {labels[23], new JScrollPane(positiveAspectsArea)},
            {labels[24], new JScrollPane(negativeAspectsArea)}
        };

        int row = 0;
        for (Object[] field : fields) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.3;
            formPanel.add(new JLabel((String) field[0]), gbc);

            gbc.gridx = 1;
            gbc.weightx = 0.7;
            formPanel.add((Component) field[1], gbc);
            row++;
        }

        // Store references to editable fields for later use
        formPanel.putClientProperty("fields", fields);

        return formPanel;
    }

    // Helper method to find value by header
    private static String findValueByHeader(String[] headers, String[] data, String headerName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equalsIgnoreCase(headerName)) {
                return (i < data.length) ? data[i] : "";
            }
        }
        return "";
    }

    // Helper method to add a detail row
    private static void addDetailRow(JPanel panel, GridBagConstraints gbc, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy++;
        panel.add(new JLabel(label + ":"), gbc);
        
        gbc.gridx = 1;
        panel.add(new JLabel(value), gbc);
    }

    // Helper method to add a text area detail
    private static void addTextAreaDetail(JPanel panel, GridBagConstraints gbc, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(new JLabel(label + ":"), gbc);
        
        gbc.gridy++;
        JTextArea textArea = new JTextArea(value);
        textArea.setRows(3);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 70));
        panel.add(scrollPane, gbc);
        
        gbc.gridwidth = 1;
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

    private static void updateAcceptanceRecord(Object[] fields, String studentID) {
        // Fix array indexing - fields array contains pairs of [label, component]
        JTextField nameField = (JTextField) ((Object[])fields[0])[1];
        JTextField facultyField = (JTextField) ((Object[])fields[1])[1];
        JTextField datesField = (JTextField) ((Object[])fields[2])[1];
        JTextField institutionNameField = (JTextField) ((Object[])fields[3])[1];
        JTextField institutionAddressField = (JTextField) ((Object[])fields[4])[1];
        JTextField institutionPhoneField = (JTextField) ((Object[])fields[5])[1];
        JTextField responsibleNameField = (JTextField) ((Object[])fields[6])[1];

        // Input Validation
        if (nameField.getText().trim().isEmpty() ||
            facultyField.getText().trim().isEmpty() ||
            datesField.getText().trim().isEmpty() ||
            institutionNameField.getText().trim().isEmpty() ||
            institutionAddressField.getText().trim().isEmpty() ||
            institutionPhoneField.getText().trim().isEmpty() ||
            responsibleNameField.getText().trim().isEmpty()) {
            showInfoDialog("All fields except Student ID must be filled.");
            return;
        }

        String updateQuery = String.format(
            "UPDATE InternshipAcceptance SET " +
            "name = '%s', " +
            "faculty = '%s', " +
            "dates = '%s', " +
            "institutionName = '%s', " +
            "institutionAddress = '%s', " +
            "institutionPhone = '%s', " +
            "responsibleName = '%s' " +
            "WHERE studentID = '%s'",
            nameField.getText().replace("'", "''"),
            facultyField.getText().replace("'", "''"),
            datesField.getText().replace("'", "''"),
            institutionNameField.getText().replace("'", "''"),
            institutionAddressField.getText().replace("'", "''"),
            institutionPhoneField.getText().replace("'", "''"),
            responsibleNameField.getText().replace("'", "''"),
            studentID.replace("'", "''")
        );

        try {
            String result = SQLiteClient.sendQuery(updateQuery);
            if (result.startsWith("Update Count:")) {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record updated successfully.");
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record updated.");
                });
            }
        } catch (Exception e) {
            showErrorDialog("Error occurred while updating the record.", e);
        }
    }

    private static void updateEvaluationRecord(Object[] fields, String studentID) {
        // Fix array indexing - fields array contains pairs of [label, component]
        JTextField nameField = (JTextField) ((Object[])fields[0])[1];
        JTextField evaluationDateField = (JTextField) ((Object[])fields[1])[1];
        JTextField responsibleNameField = (JTextField) ((Object[])fields[2])[1];
        JTextField evaluationField = (JTextField) ((Object[])fields[3])[1];

        String updateQuery = String.format(
            "UPDATE InternEvaluation SET " +
            "name = '%s', " +
            "evaluationDate = '%s', " +
            "responsibleName = '%s', " +
            "evaluation = '%s' " +
            "WHERE studentID = '%s'",
            nameField.getText().replace("'", "''"),
            evaluationDateField.getText().replace("'", "''"),
            responsibleNameField.getText().replace("'", "''"),
            evaluationField.getText().replace("'", "''"),
            studentID.replace("'", "''")
        );

        try {
            String result = SQLiteClient.sendQuery(updateQuery);
            if (result.startsWith("Update Count:")) {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record updated successfully.");
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record updated.");
                });
            }
        } catch (Exception e) {
            showErrorDialog("Error occurred while updating the record.", e);
        }
    }

    private static void updatePlaceEvaluationRecord(Object[] fields, String studentID) {
        // Extract fields - each field is in a pair [label, component]
        JTextField nameField = (JTextField) ((Object[])fields[0])[1];
        JTextField institutionNameField = (JTextField) ((Object[])fields[1])[1];
        JTextField durationField = (JTextField) ((Object[])fields[2])[1];
        JComboBox<String> salaryCombo = (JComboBox<String>) ((Object[])fields[3])[1];
        JComboBox<String> transportationCombo = (JComboBox<String>) ((Object[])fields[4])[1];
        JComboBox<String> mealsCombo = (JComboBox<String>) ((Object[])fields[5])[1];
        JComboBox<String> practiceTheoryCombo = (JComboBox<String>) ((Object[])fields[6])[1];
        JComboBox<String> foreignLanguageCombo = (JComboBox<String>) ((Object[])fields[7])[1];
        JTextField responsibilitiesField = (JTextField) ((Object[])fields[8])[1];
        JTextField workingSpaceField = (JTextField) ((Object[])fields[9])[1];
        JTextField factoryConditionsField = (JTextField) ((Object[])fields[10])[1];
        JTextField recommendationField = (JTextField) ((Object[])fields[11])[1];
        JTextField futureWorkField = (JTextField) ((Object[])fields[12])[1];
        JSpinner processScoreSpinner = (JSpinner) ((Object[])fields[13])[1];
        JSpinner decisionMakingSpinner = (JSpinner) ((Object[])fields[14])[1];
        JSpinner expectationsSpinner = (JSpinner) ((Object[])fields[15])[1];
        JSpinner researchDevelopmentSpinner = (JSpinner) ((Object[])fields[16])[1];
        JScrollPane commentsScrollPane = (JScrollPane) ((Object[])fields[17])[1];
        JScrollPane reasonScrollPane = (JScrollPane) ((Object[])fields[18])[1];
        JScrollPane analysisMethodsScrollPane = (JScrollPane) ((Object[])fields[19])[1];
        JScrollPane courseAssociationScrollPane = (JScrollPane) ((Object[])fields[20])[1];
        JScrollPane workAssociationScrollPane = (JScrollPane) ((Object[])fields[21])[1];
        JScrollPane knowledgeLacksScrollPane = (JScrollPane) ((Object[])fields[22])[1];
        JScrollPane positiveAspectsScrollPane = (JScrollPane) ((Object[])fields[23])[1];
        JScrollPane negativeAspectsScrollPane = (JScrollPane) ((Object[])fields[24])[1];

        // Retrieve text from text areas
        JTextArea commentsArea = (JTextArea) commentsScrollPane.getViewport().getView();
        JTextArea reasonForChoiceArea = (JTextArea) reasonScrollPane.getViewport().getView();
        JTextArea analysisMethodsLearnedArea = (JTextArea) analysisMethodsScrollPane.getViewport().getView();
        JTextArea courseAssociationArea = (JTextArea) courseAssociationScrollPane.getViewport().getView();
        JTextArea workAssociationArea = (JTextArea) workAssociationScrollPane.getViewport().getView();
        JTextArea knowledgeLacksArea = (JTextArea) knowledgeLacksScrollPane.getViewport().getView();
        JTextArea positiveAspectsArea = (JTextArea) positiveAspectsScrollPane.getViewport().getView();
        JTextArea negativeAspectsArea = (JTextArea) negativeAspectsScrollPane.getViewport().getView();

        // Input Validation
        if (nameField.getText().trim().isEmpty() ||
            institutionNameField.getText().trim().isEmpty() ||
            durationField.getText().trim().isEmpty() ||
            responsibilitiesField.getText().trim().isEmpty() ||
            workingSpaceField.getText().trim().isEmpty() ||
            factoryConditionsField.getText().trim().isEmpty() ||
            recommendationField.getText().trim().isEmpty() ||
            futureWorkField.getText().trim().isEmpty()) {
            showInfoDialog("All fields except Student ID must be filled.");
            return;
        }

        // Construct the UPDATE query
        String updateQuery = String.format(
            "UPDATE InternshipPlaceEvaluation SET " +
            "name = '%s', " +
            "institutionName = '%s', " +
            "duration = '%s', " +
            "salary = '%s', " +
            "transportation = '%s', " +
            "meals = '%s', " +
            "practiceTheory = '%s', " +
            "foreignLanguage = '%s', " +
            "responsibilities = '%s', " +
            "workingSpace = '%s', " +
            "factoryConditions = '%s', " +
            "recommendation = '%s', " +
            "futureWork = '%s', " +
            "processScore = '%s', " +
            "decisionMaking = '%s', " +
            "expectations = '%s', " +
            "researchDevelopment = '%s', " +
            "comments = '%s', " +
            "reasonForChoice = '%s', " +
            "analysisMethodsLearned = '%s', " +
            "courseAssociation = '%s', " +
            "workAssociation = '%s', " +
            "knowledgeLacks = '%s', " +
            "positiveAspects = '%s', " +
            "negativeAspects = '%s' " +
            "WHERE studentID = '%s'",
            nameField.getText().replace("'", "''"),
            institutionNameField.getText().replace("'", "''"),
            durationField.getText().replace("'", "''"),
            salaryCombo.getSelectedItem().toString().replace("'", "''"),
            transportationCombo.getSelectedItem().toString().replace("'", "''"),
            mealsCombo.getSelectedItem().toString().replace("'", "''"),
            practiceTheoryCombo.getSelectedItem().toString().replace("'", "''"),
            foreignLanguageCombo.getSelectedItem().toString().replace("'", "''"),
            responsibilitiesField.getText().replace("'", "''"),
            workingSpaceField.getText().replace("'", "''"),
            factoryConditionsField.getText().replace("'", "''"),
            recommendationField.getText().replace("'", "''"),
            futureWorkField.getText().replace("'", "''"),
            processScoreSpinner.getValue().toString().replace("'", "''"),
            decisionMakingSpinner.getValue().toString().replace("'", "''"),
            expectationsSpinner.getValue().toString().replace("'", "''"),
            researchDevelopmentSpinner.getValue().toString().replace("'", "''"),
            commentsArea.getText().replace("'", "''"),
            reasonForChoiceArea.getText().replace("'", "''"),
            analysisMethodsLearnedArea.getText().replace("'", "''"),
            courseAssociationArea.getText().replace("'", "''"),
            workAssociationArea.getText().replace("'", "''"),
            knowledgeLacksArea.getText().replace("'", "''"),
            positiveAspectsArea.getText().replace("'", "''"),
            negativeAspectsArea.getText().replace("'", "''"),
            studentID.replace("'", "''")
        );

        try {
            String result = SQLiteClient.sendQuery(updateQuery);
            if (result.startsWith("Update Count:")) {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record updated successfully.");
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    showInfoDialog("Record updated.");
                });
            }
        } catch (Exception e) {
            showErrorDialog("Error occurred while updating the record.", e);
        }
    }

    // **Implement the exportRecordToExcel method**
    private static void exportRecordToExcel(Map<String, String> tableResults, String studentID) {
        Workbook workbook = new XSSFWorkbook();
        try {
            for (Map.Entry<String, String> entry : tableResults.entrySet()) {
                String tableName = entry.getKey();
                String data = entry.getValue();

                Sheet sheet = workbook.createSheet(tableName);

                String[] rows = data.split("\n");
                for (int i = 0; i < rows.length; i++) {
                    Row row = sheet.createRow(i);
                    String[] cells = rows[i].split("\t");
                    for (int j = 0; j < cells.length; j++) {
                        Cell cell = row.createCell(j);
                        cell.setCellValue(cells[j]);
                    }
                }
            }

            // **Save the Excel file**
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Excel File");
            fileChooser.setSelectedFile(new File("export_" + studentID + ".xlsx"));
            int userSelection = fileChooser.showSaveDialog(frame);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                try (FileOutputStream fileOut = new FileOutputStream(fileToSave)) {
                    workbook.write(fileOut);
                    showInfoDialog("Data exported successfully to " + fileToSave.getAbsolutePath());
                }
            }
        } catch (IOException ex) {
            showErrorDialog("Error occurred while exporting to Excel.", ex);
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // **Implement the exportAllFormsToExcel method**
    private static void exportAllFormsToExcel() {
        Workbook workbook = new XSSFWorkbook();
        try {
            // Define tables and their corresponding sheet names
            Map<String, String> tables = new HashMap<>();
            tables.put("InternshipAcceptance", "Internship Acceptance");
            tables.put("InternEvaluation", "Intern Evaluation");
            tables.put("InternshipPlaceEvaluation", "Internship Place Evaluation");

            // Iterate through each table and populate sheets
            for (Map.Entry<String, String> entry : tables.entrySet()) {
                String tableName = entry.getKey();
                String sheetName = entry.getValue();

                String query = "SELECT * FROM " + tableName;
                String queryResult = SQLiteClient.sendQuery(query);

                if (queryResult != null && !queryResult.isEmpty()) {
                    Sheet sheet = workbook.createSheet(sheetName);
                    String[] rows = queryResult.split("\n");

                    for (int i = 0; i < rows.length; i++) {
                        Row row = sheet.createRow(i);
                        String[] cells = rows[i].split("\t");
                        for (int j = 0; j < cells.length; j++) {
                            Cell cell = row.createCell(j);
                            cell.setCellValue(cells[j]);
                        }
                    }
                }
            }

            // **Save the Excel file**
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save All Forms Excel File");
            fileChooser.setSelectedFile(new File("AllFormsExport.xlsx"));
            int userSelection = fileChooser.showSaveDialog(frame);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                try (FileOutputStream fileOut = new FileOutputStream(fileToSave)) {
                    workbook.write(fileOut);
                    showInfoDialog("All forms exported successfully to " + fileToSave.getAbsolutePath());
                }
            }
        } catch (IOException ex) {
            showErrorDialog("Error occurred while exporting all forms to Excel.", ex);
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}