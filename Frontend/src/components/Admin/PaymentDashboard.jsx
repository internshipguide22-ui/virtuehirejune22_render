import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  CheckCircle,
  CreditCard,
  Eye,
  FileDown,
  IndianRupee,
  Loader2,
  Receipt,
  RefreshCw,
  XCircle,
} from "lucide-react";
import api from "../../services/api";
import AdminLayout from "./AdminLayout";
import "./AdminDashboard.css";

const PaymentDashboard = () => {
  const navigate = useNavigate();
  const [payments, setPayments] = useState([]);
  const [stats, setStats] = useState({
    totalPayments: 0,
    successfulPayments: 0,
    failedPayments: 0,
    totalRevenue: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchPayments = async () => {
    setLoading(true);
    setError("");

    try {
      const res = await api.get("/payments/history");
      const data = Array.isArray(res.data) ? res.data : res.data?.payments || [];

      const successfulPayments = data.filter((p) => p.status === "SUCCESS");
      const failedPayments = data.filter((p) => p.status === "FAILED");

      setPayments(data);
      setStats({
        totalPayments: data.length,
        successfulPayments: successfulPayments.length,
        failedPayments: failedPayments.length,
        totalRevenue: successfulPayments.reduce(
          (sum, p) => sum + (Number(p.amount) || 0),
          0,
        ),
      });
    } catch (err) {
      console.error(err);
      setError("Failed to load payments.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPayments();
  }, []);

  const getStatusClass = (status) => {
    switch (status) {
      case "SUCCESS":
        return "adm-pay-status success";
      case "FAILED":
        return "adm-pay-status failed";
      case "PENDING":
        return "adm-pay-status pending";
      default:
        return "adm-pay-status neutral";
    }
  };

  const formatCurrency = (amount) =>
    new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 2,
    }).format(Number(amount) || 0);

  const formatDate = (date) => {
    if (!date) return "N/A";
    return new Date(date).toLocaleString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  return (
    <AdminLayout
      title="Payments"
      description="Track HR plan purchases, payment outcomes, and revenue activity from the admin workspace."
      contentClassName="adm-module-stack"
    >
      <div className="adm-stats-grid adm-payment-stats">
        <div className="adm-stat-card blue">
          <div className="adm-stat-icon">
            <IndianRupee size={20} />
          </div>
          <div>
            <div className="adm-stat-value">
              {formatCurrency(stats.totalRevenue)}
            </div>
            <div className="adm-stat-label">Total Revenue</div>
          </div>
        </div>

        <div className="adm-stat-card purple">
          <div className="adm-stat-icon">
            <Receipt size={20} />
          </div>
          <div>
            <div className="adm-stat-value">{stats.totalPayments}</div>
            <div className="adm-stat-label">Total Payments</div>
          </div>
        </div>

        <div className="adm-stat-card green">
          <div className="adm-stat-icon">
            <CheckCircle size={20} />
          </div>
          <div>
            <div className="adm-stat-value">{stats.successfulPayments}</div>
            <div className="adm-stat-label">Successful</div>
          </div>
        </div>

        <div className="adm-stat-card yellow">
          <div className="adm-stat-icon">
            <XCircle size={20} />
          </div>
          <div>
            <div className="adm-stat-value">{stats.failedPayments}</div>
            <div className="adm-stat-label">Failed</div>
          </div>
        </div>
      </div>

      {error && (
        <div className="adm-card adm-payment-alert">
          <XCircle size={18} />
          <span>{error}</span>
          <button type="button" onClick={fetchPayments}>
            Retry
          </button>
        </div>
      )}

      <div className="adm-card adm-table-card-lg">
        <div className="adm-table-card-head">
          <div>
            <h3>Recent Transactions</h3>
            <p>
              {payments.length} payment record
              {payments.length === 1 ? "" : "s"} found
            </p>
          </div>
          <button
            type="button"
            className="adm-refresh-btn"
            onClick={fetchPayments}
            disabled={loading}
          >
            {loading ? (
              <Loader2 size={16} className="adm-spin" />
            ) : (
              <RefreshCw size={16} />
            )}
            Refresh
          </button>
        </div>

        <div className="adm-table-container">
          <table>
            <thead>
              <tr>
                <th>HR Name</th>
                <th>Plan</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Date</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="6" className="adm-empty-table">
                    <Loader2 className="adm-spin" size={30} />
                    <p>Loading payment dashboard...</p>
                  </td>
                </tr>
              ) : payments.length > 0 ? (
                payments.map((payment) => (
                  <tr key={payment.id}>
                    <td>
                      <div className="adm-t-name">
                        {payment.hr?.fullName || payment.hrName || "N/A"}
                      </div>
                      <div className="adm-t-email">
                        {payment.hr?.email || payment.hrEmail || "No email"}
                      </div>
                    </td>
                    <td>
                      <span className="adm-plan-pill">
                        <CreditCard size={13} />
                        {payment.planType || "N/A"}
                      </span>
                    </td>
                    <td className="adm-pay-amount">
                      {formatCurrency(payment.amount)}
                    </td>
                    <td>
                      <span className={getStatusClass(payment.status)}>
                        {payment.status || "UNKNOWN"}
                      </span>
                    </td>
                    <td>{formatDate(payment.createdAt)}</td>
                    <td>
                      <button
                        type="button"
                        className="adm-icon-action"
                        title="View payment details"
                        onClick={() => navigate(`/admin/payments/${payment.id}`)}
                      >
                        <Eye size={16} />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="6" className="adm-empty-table">
                    <Receipt size={42} />
                    <p>No transactions found.</p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="adm-card adm-payment-export">
        <div className="adm-card-header">
          <div>
            <h3>Data Export</h3>
            <p className="adm-card-subtitle">
              Download payment records for reporting and reconciliation.
            </p>
          </div>
        </div>
        <div className="adm-export-grid">
          <button type="button" className="adm-export-btn">
            <FileDown size={18} />
            Export to Excel
          </button>
          <button type="button" className="adm-export-btn">
            <FileDown size={18} />
            Export to CSV
          </button>
          <button type="button" className="adm-export-btn">
            <Receipt size={18} />
            Generate Report
          </button>
        </div>
      </div>
    </AdminLayout>
  );
};

export default PaymentDashboard;
