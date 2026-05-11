package dbp;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DataDemo {
    public static void main(String[] args) throws SQLException {
        Connection con = DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/electricdb", "root", "snivila@2006");
        System.out.println("Connection Success");

        Statement stmt = con.createStatement();

        String sql =
            "SELECT c.customer_id, c.name, c.address, c.phone, c.email, " +
            "m.meter_number, m.installation_date, " +
            "u.billing_month, u.units_consumed, " +
            "b.bill_id, b.bill_date, b.due_date, b.amount, b.fine, b.total_amount, b.status, b.payment_date " +
            "FROM customer c " +
            "LEFT JOIN meter m ON c.customer_id = m.customer_id " +
            "LEFT JOIN usage_data u ON m.meter_id = u.meter_id " +
            "LEFT JOIN bills b ON u.usage_id = b.usage_id " +
            "ORDER BY c.customer_id, u.billing_month";
        ResultSet rs = stmt.executeQuery(sql);

        System.out.println("\nCustomerID | Name | MeterNo | Month | Units | Bill Amount | Due Date | Status | Fine | Total | Payment Date");

        while (rs.next()) {
            String status = rs.getString("status");
            String dueDateStr = rs.getString("due_date");
            double billAmount = rs.getDouble("amount");
            double fine = rs.getDouble("fine");
            double total = rs.getDouble("total_amount");
            String paymentDate = rs.getString("payment_date");

            // Calculate dynamic fine if overdue and status is pending
            if ("Pending".equalsIgnoreCase(status) && dueDateStr != null) {
                LocalDate dueDate = LocalDate.parse(dueDateStr);
                LocalDate now = LocalDate.now();
                if (now.isAfter(dueDate)) {
                    long daysLate = ChronoUnit.DAYS.between(dueDate, now);
                    fine = daysLate * 5.0;  // Example: 5 currency units per day late
                    total = billAmount + fine;
                }
            }

            String info =
                rs.getInt("customer_id") + " | " +
                rs.getString("name") + " | " +
                rs.getString("meter_number") + " | " +
                rs.getString("billing_month") + " | " +
                rs.getInt("units_consumed") + " | " +
                billAmount + " | " +
                dueDateStr + " | ";

            // Status handling
            if (status == null) {
                info += "No Bill";
            } else if ("Pending".equalsIgnoreCase(status)) {
                info += "Pending | " + fine + " | " + total + " | " + (paymentDate != null ? paymentDate : "--") +
                        " <-- Amount to be paid: " + total;
            } else if ("Paid".equalsIgnoreCase(status)) {
                info += "Paid | " + fine + " | " + total + " | " + (paymentDate != null ? paymentDate : "--");
            } else if ("Overdue".equalsIgnoreCase(status)) {
                info += "Overdue | " + fine + " | " + total + " | " + (paymentDate != null ? paymentDate : "--") +
                        " <-- Overdue, please pay!";
            } else {
                info += status + " | " + fine + " | " + total + " | " + (paymentDate != null ? paymentDate : "--");
            }

            System.out.println(info);
        }
    }
}
