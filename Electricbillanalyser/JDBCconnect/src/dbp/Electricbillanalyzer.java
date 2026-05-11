package dbp;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

class ConsumerException extends Exception {
    public ConsumerException(String message) { super(message); }
}

class Consumer {
    private int id;
    private String name, address, mobile;
    public Consumer() {}
    public Consumer(int id, String name, String address, String mobile) {
        this.id = id; this.name = name; this.address = address; this.mobile = mobile;
    }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
}

public class Electricbillanalyzer {
    static JFrame frame;
    static User currentUser;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Electricbillanalyzer::showLoginScreen);
    }

    static void showLoginScreen() {
        frame = new JFrame("Electric Bill Analyzer Login");
        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        JTextField tfUser = new JTextField();
        JPasswordField pfPass = new JPasswordField();
        JComboBox<String> cbRole = new JComboBox<>(new String[]{"consumer", "admin"});
        JButton btnLogin = new JButton("Login");
        panel.add(new JLabel("Username:")); panel.add(tfUser);
        panel.add(new JLabel("Password:")); panel.add(pfPass);
        panel.add(new JLabel("Role:")); panel.add(cbRole);
        panel.add(new JLabel("")); panel.add(btnLogin);
        frame.add(panel);
        frame.setSize(350, 200);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        btnLogin.addActionListener(e -> {
            String uname = tfUser.getText().trim();
            String pass = new String(pfPass.getPassword());
            String role = cbRole.getSelectedItem().toString();
            try (Connection con = getDBConnection()) {
                String query = "SELECT * FROM users WHERE username=? AND password=? AND role=?";
                PreparedStatement ps = con.prepareStatement(query);
                ps.setString(1, uname);
                ps.setString(2, pass);
                ps.setString(3, role);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    currentUser = new User(rs.getString("username"), rs.getString("role"),
                        rs.getObject("customer_id") != null ? rs.getInt("customer_id") : null);
                    frame.dispose();
                    if (role.equals("admin")) showAdminPanel();
                    else showConsumerPanel();
                } else {
                    JOptionPane.showMessageDialog(frame, "Invalid credentials!");
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "DB error: " + ex.getMessage());
            }
        });
    }

    static Connection getDBConnection() throws SQLException {
        return DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/electricdb", "root", "snivila@2006"
        );
    }

    static void showAdminPanel() {
        frame = new JFrame("Admin - All Consumer Bills");
        frame.setLayout(new BorderLayout());

        String[] columns = {"CustomerID", "Name", "MeterNo", "Month", "Units", "Bill Amount",
            "Due Date", "Status", "Fine", "Total", "Payment Date"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);

        loadAdminTableData(model);

        table.getColumnModel().getColumn(7).setPreferredWidth(220);
        table.getColumnModel().getColumn(10).setPreferredWidth(130);

        JButton btnRegister = new JButton("Register Consumer");
        JButton btnUpdate = new JButton("Update Consumer");
        JButton btnAddUsageBill = new JButton("Add Usage & Bill");
        JButton logoutBtn = new JButton("Logout");

        JPanel btnPanel = new JPanel();
        btnPanel.add(btnRegister);
        btnPanel.add(btnUpdate);
        btnPanel.add(btnAddUsageBill);
        btnPanel.add(logoutBtn);

        frame.add(new JScrollPane(table), BorderLayout.CENTER);
        frame.add(btnPanel, BorderLayout.SOUTH);

        btnRegister.addActionListener(e -> {
            try {
                registerConsumer(model);
            } catch (ConsumerException ce) {
                JOptionPane.showMessageDialog(frame, ce.getMessage());
            }
        });

        btnUpdate.addActionListener(e -> {
            try {
                updateConsumer();
            } catch (ConsumerException ce) {
                JOptionPane.showMessageDialog(frame, ce.getMessage());
            }
        });

        btnAddUsageBill.addActionListener(e -> {
            JTextField tfCustId = new JTextField();
            JTextField tfBillingMonth = new JTextField("2025-10");
            JTextField tfUnits = new JTextField();
            Object[] usageMsg = {
                "Customer ID:", tfCustId,
                "Billing Month (YYYY-MM):", tfBillingMonth,
                "Units Consumed:", tfUnits
            };
            int opt = JOptionPane.showConfirmDialog(frame, usageMsg, "Add Usage & Bill", JOptionPane.OK_CANCEL_OPTION);
            if (opt == JOptionPane.OK_OPTION) {
                try (Connection con = getDBConnection()) {
                    int custId = Integer.parseInt(tfCustId.getText().trim());
                    String billingMonth = tfBillingMonth.getText().trim();
                    int units = Integer.parseInt(tfUnits.getText().trim());
                    double amount = calculateBillAmount(units);

                    PreparedStatement ps1 = con.prepareStatement("SELECT meter_id FROM meter WHERE customer_id=?");
                    ps1.setInt(1, custId);
                    ResultSet rs = ps1.executeQuery();
                    if (!rs.next()) throw new SQLException("No meter found for this customer!");
                    int meterId = rs.getInt(1);

                    PreparedStatement ps2 = con.prepareStatement(
                        "INSERT INTO usage_data(meter_id, billing_month, units_consumed) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                    ps2.setInt(1, meterId);
                    ps2.setString(2, billingMonth);
                    ps2.setInt(3, units);
                    ps2.executeUpdate();
                    int usageId;
                    ResultSet genKeys = ps2.getGeneratedKeys();
                    if (genKeys.next()) usageId = genKeys.getInt(1); else throw new SQLException("No usage_id!");

                    PreparedStatement ps3 = con.prepareStatement(
                        "INSERT INTO bills(usage_id, amount, due_date, status, fine, total_amount, payment_date) VALUES (?, ?, ?, 'Pending', 0, ?, NULL)");
                    ps3.setInt(1, usageId);
                    ps3.setDouble(2, amount);
                    ps3.setString(3, LocalDate.now().plusDays(25).toString());
                    ps3.setDouble(4, amount);
                    ps3.executeUpdate();

                    JOptionPane.showMessageDialog(frame, "Usage & bill added.");
                    model.setRowCount(0);
                    loadAdminTableData(model);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage());
                }
            }
        });

        logoutBtn.addActionListener(e -> {
            frame.dispose();
            showLoginScreen();
        });

        frame.setSize(1200, 400);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    static void loadAdminTableData(DefaultTableModel model) {
        try (Connection con = getDBConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(
                "SELECT c.customer_id, c.name, m.meter_number, u.billing_month, u.units_consumed, " +
                "b.amount, b.due_date, b.status, b.fine, b.total_amount, b.payment_date " +
                "FROM customer c " +
                "LEFT JOIN meter m ON c.customer_id = m.customer_id " +
                "LEFT JOIN usage_data u ON m.meter_id = u.meter_id " +
                "LEFT JOIN bills b ON u.usage_id = b.usage_id " +
                "ORDER BY c.customer_id, u.billing_month")) {
            while (rs.next()) {
                String status = rs.getString("status");
                String dueDateStr = rs.getString("due_date");
                double amount = rs.getDouble("amount");
                double fine = rs.getDouble("fine");
                double total = rs.getDouble("total_amount");
                String paymentDate = rs.getString("payment_date");

                if ("Pending".equalsIgnoreCase(status) && dueDateStr != null && amount > 0) {
                    LocalDate dueDate = LocalDate.parse(dueDateStr);
                    LocalDate now = LocalDate.now();
                    if (now.isAfter(dueDate)) {
                        long daysLate = ChronoUnit.DAYS.between(dueDate, now);
                        fine = daysLate * 5.0;
                        total = amount + fine;
                    }
                }

                String showStatus;
                if (status == null) showStatus = "No Bill";
                else if ("Pending".equalsIgnoreCase(status)) showStatus = "Pending - Amount to Pay: " + total;
                else if ("Paid".equalsIgnoreCase(status)) showStatus = "Paid, Date: " + paymentDate;
                else showStatus = status;

                model.addRow(new Object[]{
                        rs.getInt("customer_id"),
                        rs.getString("name"),
                        rs.getString("meter_number"),
                        rs.getString("billing_month"),
                        rs.getInt("units_consumed"),
                        amount,
                        dueDateStr,
                        showStatus,
                        fine,
                        total,
                        paymentDate
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(frame, "Error loading data: " + e.getMessage());
        }
    }

    static void registerConsumer(DefaultTableModel model) throws ConsumerException {
        JTextField tfName = new JTextField();
        JTextField tfAddress = new JTextField();
        JTextField tfMobile = new JTextField();
        JTextField tfMeter = new JTextField();
        JTextField tfUnits = new JTextField();
        Object[] msg = {
            "Name:", tfName,
            "Address:", tfAddress,
            "Mobile:", tfMobile,
            "Meter Number:", tfMeter,
            "Units Consumed (optional):", tfUnits
        };
        int opt = JOptionPane.showConfirmDialog(frame, msg, "Register Consumer", JOptionPane.OK_CANCEL_OPTION);
        if (opt == JOptionPane.OK_OPTION) {
            if (tfName.getText().trim().isEmpty() || tfMobile.getText().trim().isEmpty()) {
                throw new ConsumerException("Name and Mobile are required fields!");
            }
            int units = 0;
            double amount = 0;
            try {
                if (!tfUnits.getText().trim().isEmpty())
                    units = Integer.parseInt(tfUnits.getText().trim());
                amount = calculateBillAmount(units);
            } catch (NumberFormatException ne) {
                throw new ConsumerException("Units must be a number!");
            }
            try (Connection con = getDBConnection()) {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO customer(name, address, mobile) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, tfName.getText().trim());
                ps.setString(2, tfAddress.getText().trim());
                ps.setString(3, tfMobile.getText().trim());
                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                int custId = 0;
                if (rs.next()) custId = rs.getInt(1);

                if (!tfMeter.getText().trim().isEmpty()) {
                    PreparedStatement ps2 = con.prepareStatement(
                        "INSERT INTO meter(customer_id, meter_number) VALUES (?, ?)");
                    ps2.setInt(1, custId);
                    ps2.setString(2, tfMeter.getText().trim());
                    ps2.executeUpdate();
                }
                // Ask for login credentials now
                JTextField tfUsername = new JTextField();
                JPasswordField pfPassword = new JPasswordField();
                Object[] loginMsg = {
                    "Username:", tfUsername,
                    "Password:", pfPassword
                };
                int loginOpt = JOptionPane.showConfirmDialog(frame, loginMsg, "Set Login Credentials", JOptionPane.OK_CANCEL_OPTION);
                if (loginOpt == JOptionPane.OK_OPTION) {
                    String username = tfUsername.getText().trim();
                    String password = new String(pfPassword.getPassword());
                    if (username.isEmpty() || password.isEmpty()) {
                        JOptionPane.showMessageDialog(frame, "Username and password cannot be empty! Skipping login creation.");
                    } else {
                        PreparedStatement ps3 = con.prepareStatement(
                            "INSERT INTO users(username, password, role, customer_id) VALUES (?, ?, 'consumer', ?)");
                        ps3.setString(1, username);
                        ps3.setString(2, password);
                        ps3.setInt(3, custId);
                        ps3.executeUpdate();
                        JOptionPane.showMessageDialog(frame, "Consumer registered with login credentials.");
                    }
                } else {
                    JOptionPane.showMessageDialog(frame, "Login credentials creation skipped.");
                }
                model.addRow(new Object[]{
                    custId,
                    tfName.getText().trim(),
                    tfMeter.getText().trim(),
                    "",
                    units,
                    amount,
                    "",
                    "No Bill",
                    0.0,
                    amount,
                    ""
                });
            } catch (SQLException ex) {
                throw new ConsumerException("Database error: " + ex.getMessage());
            }
        }
    }

    static double calculateBillAmount(int units) {
        double amount = 0;
        if (units <= 100) {
            amount = units * 5.0;
        } else if (units <= 200) {
            amount = 100 * 5.0 + (units - 100) * 7.0;
        } else {
            amount = 100 * 5.0 + 100 * 7.0 + (units - 200) * 10.0;
        }
        return amount;
    }

    static void updateConsumer() throws ConsumerException {
        String id = JOptionPane.showInputDialog(frame, "Enter Customer ID to update:");
        if (id == null || id.isEmpty()) throw new ConsumerException("Customer ID required!");
        try (Connection con = getDBConnection()) {
            PreparedStatement ps = con.prepareStatement("SELECT * FROM customer WHERE customer_id=?");
            ps.setInt(1, Integer.parseInt(id));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                JTextField tfName = new JTextField(rs.getString("name"));
                JTextField tfAddress = new JTextField(rs.getString("address"));
                JTextField tfMobile = new JTextField(rs.getString("mobile"));
                Object[] msg = {
                    "Name:", tfName,
                    "Address:", tfAddress,
                    "Mobile:", tfMobile
                };
                int opt = JOptionPane.showConfirmDialog(frame, msg, "Update Consumer", JOptionPane.OK_CANCEL_OPTION);
                if (opt == JOptionPane.OK_OPTION) {
                    if (tfName.getText().trim().isEmpty()) throw new ConsumerException("Name cannot be empty!");
                    PreparedStatement ups = con.prepareStatement(
                        "UPDATE customer SET name=?, address=?, mobile=? WHERE customer_id=?");
                    ups.setString(1, tfName.getText().trim());
                    ups.setString(2, tfAddress.getText().trim());
                    ups.setString(3, tfMobile.getText().trim());
                    ups.setInt(4, Integer.parseInt(id));
                    ups.executeUpdate();
                    JOptionPane.showMessageDialog(frame, "Consumer updated successfully.");
                }
            } else {
                throw new ConsumerException("Consumer ID not found!");
            }
        } catch (SQLException ex) {
            throw new ConsumerException("Database error: " + ex.getMessage());
        }
    }

    static void showConsumerPanel() {
        frame = new JFrame("Your Electricity Bills");
        frame.setLayout(new BorderLayout());

        String[] columns = {"Billing Month", "Meter No.", "Units", "Amount", "Due Date", "Status",
                "Fine", "Total Amount", "Payment Date"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);

        try (Connection con = getDBConnection()) {
            String sql =
                    "SELECT u.billing_month, m.meter_number, u.units_consumed, b.amount, b.due_date, b.status, b.fine, b.total_amount, b.payment_date " +
                            "FROM customer c " +
                            "JOIN meter m ON c.customer_id = m.customer_id " +
                            "JOIN usage_data u ON m.meter_id = u.meter_id " +
                            "LEFT JOIN bills b ON u.usage_id = b.usage_id " +
                            "WHERE c.customer_id=?";
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, currentUser.customer_id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String status = rs.getString("status");
                String dueDateStr = rs.getString("due_date");
                double amount = rs.getDouble("amount");
                double fine = rs.getDouble("fine");
                double total = rs.getDouble("total_amount");
                String paymentDate = rs.getString("payment_date");

                if ("Pending".equalsIgnoreCase(status) && dueDateStr != null && amount > 0) {
                    LocalDate dueDate = LocalDate.parse(dueDateStr);
                    LocalDate now = LocalDate.now();
                    if (now.isAfter(dueDate)) {
                        long daysLate = ChronoUnit.DAYS.between(dueDate, now);
                        fine = daysLate * 5.0;
                        total = amount + fine;
                    }
                }

                String showStatus;
                if (status == null) showStatus = "No Bill";
                else if ("Pending".equalsIgnoreCase(status))
                    showStatus = "Pending - Amount to Pay: " + total;
                else if ("Paid".equalsIgnoreCase(status))
                    showStatus = "Paid, Date: " + paymentDate;
                else showStatus = status;

                model.addRow(new Object[]{
                        rs.getString("billing_month"),
                        rs.getString("meter_number"),
                        rs.getInt("units_consumed"),
                        amount,
                        dueDateStr,
                        showStatus,
                        fine,
                        total,
                        paymentDate
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(frame, "Error loading data: " + e.getMessage());
        }

        table.getColumnModel().getColumn(5).setPreferredWidth(220);
        table.getColumnModel().getColumn(8).setPreferredWidth(130);

        frame.add(new JScrollPane(table), BorderLayout.CENTER);
        JButton btnPayBill = new JButton("Pay Bill");
        JButton logoutBtn = new JButton("Logout");
        JPanel btnPanel = new JPanel();
        btnPanel.add(btnPayBill);
        btnPanel.add(logoutBtn);
        frame.add(btnPanel, BorderLayout.SOUTH);

        btnPayBill.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(frame, "Please select a bill to pay.");
                return;
            }
            String status = (String) model.getValueAt(selectedRow, 5);
            if (status != null && status.startsWith("Paid")) {
                JOptionPane.showMessageDialog(frame, "Bill is already paid.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(frame, "Confirm payment for this bill?", "Payment Confirmation", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    String billingMonth = (String) model.getValueAt(selectedRow, 0);
                    payBill(currentUser.customer_id, billingMonth);
                    model.setRowCount(0);
                    showConsumerPanel();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(frame, "Payment failed: " + ex.getMessage());
                }
            }
        });

        logoutBtn.addActionListener(e -> {
            frame.dispose();
            showLoginScreen();
        });

        frame.setSize(1000, 350);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    static void payBill(int customerId, String billingMonth) throws SQLException {
        try (Connection con = getDBConnection()) {
            String updateSql = "UPDATE bills b JOIN usage_data u ON b.usage_id = u.usage_id JOIN meter m ON u.meter_id = m.meter_id " +
                "SET b.status = 'Paid', b.payment_date = CURDATE() " +
                "WHERE m.customer_id = ? AND u.billing_month = ? AND b.status != 'Paid'";
            PreparedStatement ps = con.prepareStatement(updateSql);
            ps.setInt(1, customerId);
            ps.setString(2, billingMonth);
            int count = ps.executeUpdate();
            if (count == 0) {
                throw new SQLException("No pending bills found for payment.");
            }
            JOptionPane.showMessageDialog(frame, "Payment successful - Bill updated.");
        }
    }

    static class User {
        private String username, role;
        private Integer customer_id;
        public User(String un, String rl, Integer cid) {
            username = un; role = rl; customer_id = cid;
        }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public Integer getCustomerId() { return customer_id; }
    }
}
