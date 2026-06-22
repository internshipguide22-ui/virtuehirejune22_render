import React, { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  Award,
  BriefcaseBusiness,
  CheckCircle2,
  ClipboardList,
  Eye,
  FileSearch,
  LayoutDashboard,
  List,
  LogOut,
  MessageSquare,
  FileText,
  PlusSquare,
  UserCheck,
  UserX,
  Users,
} from "lucide-react";
import {
  getAllInterestedCandidates,
  loadJobs,
  subscribeJobs,
} from "../../utils/jobsStore";
import api from "../../services/api";
import "../Jobs/JobsModule.css";
import "./HRInterestedCandidates.css";
import { useAppDialog } from "../common/AppDialog";
import AssignTestModal from "./AssignTestModal";
import FeedbackModal from "./FeedbackModal";

const parseStorageUser = () => {
  const rawSources = ["current_hr_user", "user"];
  for (const key of rawSources) {
    try {
      const parsed = JSON.parse(localStorage.getItem(key) || "null");
      if (parsed) return parsed;
    } catch {
      // no-op
    }
  }
  return null;
};

export default function HRInterestedCandidates() {
  const navigate = useNavigate();
  const location = useLocation();
  const [interestedCandidates, setInterestedCandidates] = useState([]);
  const [message, setMessage] = useState("");
  const [hrUser, setHrUser] = useState(parseStorageUser);
  const [workflowCandidates, setWorkflowCandidates] = useState([]);
  const [feedbackHistory, setFeedbackHistory] = useState([]);
  const [candidateResults, setCandidateResults] = useState({});
  const [candidateAssignments, setCandidateAssignments] = useState({});
  const [candidateSubmissions, setCandidateSubmissions] = useState({});
  const [workflowLoading, setWorkflowLoading] = useState(false);
  const selectedJobId = location.state?.selectedJobId || "";
  const selectedJobTitle = location.state?.selectedJobTitle || "";
  const { showAlert, dialogNode } = useAppDialog();
  const [assignTestModal, setAssignTestModal] = useState({
    open: false,
    candidate: null,
  });
  const [feedbackModal, setFeedbackModal] = useState({
    open: false,
    candidate: null,
    action: null,
  });
  const [hrProfileImageFailed, setHrProfileImageFailed] = useState(false);

  useEffect(() => {
    setInterestedCandidates(getAllInterestedCandidates());
    loadJobs().then(() => setInterestedCandidates(getAllInterestedCandidates()));
    const unsubscribeJobs = subscribeJobs(() =>
      setInterestedCandidates(getAllInterestedCandidates()),
    );

    return () => {
      unsubscribeJobs();
    };
  }, []);

  useEffect(() => {
    const syncHrSession = async () => {
      if (hrUser?.id || hrUser?.email) return;

      try {
        const response = await api.get("/hrs/dashboard");
        const activeHr = response.data?.hr || null;
        if (activeHr) {
          setHrUser(activeHr);
          localStorage.setItem("current_hr_user", JSON.stringify(activeHr));
          localStorage.setItem("user", JSON.stringify(activeHr));
        }
      } catch (error) {
        // Keep local fallback behavior; dashboard request already tells us if session is gone.
      }
    };

    syncHrSession();
  }, [hrUser]);

  useEffect(() => {
    const fetchWorkflowData = async () => {
      if (interestedCandidates.length === 0) {
        setWorkflowCandidates([]);
        setFeedbackHistory([]);
        setCandidateResults({});
        setCandidateAssignments({});
        setCandidateSubmissions({});
        return;
      }

      setWorkflowLoading(true);
      try {
        const [activeResponse, feedbackResponse] = await Promise.all([
          api.get("/hrs/candidates-for-action"),
          api.get("/hrs/feedback-history"),
        ]);

        const activeCandidates = activeResponse.data?.candidates || [];
        const feedbackCandidates = feedbackResponse.data?.candidates || [];
        const interestedIdSet = new Set(
          interestedCandidates.map((row) => String(row.candidateId)),
        );

        const activeMatched = activeCandidates.filter((candidate) =>
          interestedIdSet.has(String(candidate.id)),
        );
        const feedbackMatched = feedbackCandidates.filter((candidate) =>
          interestedIdSet.has(String(candidate.candidateId || candidate.id)),
        );

        setWorkflowCandidates(activeMatched);
        setFeedbackHistory(feedbackMatched);

        const idsToFetch = Array.from(
          new Set([
            ...activeMatched.map((candidate) => candidate.id),
            ...feedbackMatched.map((candidate) => candidate.candidateId || candidate.id),
          ]),
        ).filter(Boolean);

        const resultEntries = await Promise.all(
          idsToFetch.map(async (candidateId) => {
            const [resultsResponse, assignmentsResponse, submissionsResponse] = await Promise.all([
              api.get(`/hrs/candidates/${candidateId}/results`).catch(() => ({ data: { results: [] } })),
              api.get(`/hrs/candidates/${candidateId}/assigned-tests`).catch(() => ({ data: { assignedTests: [] } })),
              api.get(`/hrs/candidates/${candidateId}/submissions`).catch(() => ({ data: { submissions: [] } })),
            ]);

            return [
              candidateId,
              {
                results: resultsResponse.data?.results || [],
                assignments: assignmentsResponse.data?.assignedTests || [],
                submissions: submissionsResponse.data?.submissions || [],
              },
            ];
          }),
        );

        const nextResults = {};
        const nextAssignments = {};
        const nextSubmissions = {};

        resultEntries.forEach(([candidateId, data]) => {
          nextResults[candidateId] = data.results;
          nextAssignments[candidateId] = data.assignments;
          nextSubmissions[candidateId] = data.submissions;
        });

        setCandidateResults(nextResults);
        setCandidateAssignments(nextAssignments);
        setCandidateSubmissions(nextSubmissions);
      } catch (error) {
        setMessage(
          error?.response?.data?.error || "Failed to load hiring workflow data.",
        );
      } finally {
        setWorkflowLoading(false);
      }
    };

    fetchWorkflowData();
  }, [interestedCandidates]);

  const openContact = async (row) => {
    await showAlert({
      title: "Candidate Contact",
      message: `Email: ${row.email || "N/A"}\nPhone: ${row.phoneNumber || "N/A"}`,
      tone: "info",
      confirmLabel: "Close",
    });
  };

  const loadWorkflowCandidate = async (row) => {
    const response = await api.get(`/hrs/candidates/${row.candidateId}`);
    const candidate = response.data?.candidate;
    if (!candidate) {
      throw new Error("Candidate details not found");
    }

    return {
      ...candidate,
      id: candidate.id ?? row.candidateId,
      fullName: candidate.fullName ?? row.fullName,
      email: candidate.email ?? row.email,
      skills: candidate.skills ?? row.skills,
      experience: candidate.experience ?? row.experience,
      applicationStatus: candidate.applicationStatus ?? "INTERESTED",
    };
  };

  const handleAssignTest = async (row) => {
    try {
      const candidate = await loadWorkflowCandidate(row);
      setAssignTestModal({ open: true, candidate });
    } catch (error) {
      setMessage(
        error?.response?.data?.error || error.message || "Failed to load candidate workflow details.",
      );
    }
  };

  const handleFeedback = async (row, action) => {
    try {
      const candidate = await loadWorkflowCandidate(row);
      setFeedbackModal({ open: true, candidate, action });
    } catch (error) {
      setMessage(
        error?.response?.data?.error || error.message || "Failed to load candidate workflow details.",
      );
    }
  };

  const statusBadge = () => {
    return (
      <span className="hri-status approved">
        <CheckCircle2 size={14} /> Access Granted
      </span>
    );
  };

  const rows = useMemo(
    () =>
      interestedCandidates.map((row) => ({
        ...row,
        workflowCandidate:
          workflowCandidates.find(
            (candidate) => String(candidate.id) === String(row.candidateId),
          ) || null,
      })),
    [interestedCandidates, workflowCandidates],
  );

  const visibleRows = useMemo(
    () =>
      selectedJobId ? rows.filter((row) => row.jobId === selectedJobId) : rows,
    [rows, selectedJobId],
  );

  const handleWorkflowStatusChanged = (candidateId, newStatus) => {
    setMessage(`Candidate ${candidateId} updated to ${newStatus}.`);
    setFeedbackModal({ open: false, candidate: null, action: null });
    setFeedbackHistory((prev) => {
      const existing = prev.find(
        (item) => String(item.candidateId || item.id) === String(candidateId),
      );
      if (existing) {
        return prev.map((item) =>
          String(item.candidateId || item.id) === String(candidateId)
            ? { ...item, status: newStatus }
            : item,
        );
      }
      return prev;
    });
  };

  const handleWorkflowAssigned = (candidateId) => {
    setMessage(`Test assignment saved for candidate ${candidateId}.`);
    setAssignTestModal({ open: false, candidate: null });
  };

  const resultRows = useMemo(
    () =>
      visibleRows
        .map((row) => {
          const candidateId = row.workflowCandidate?.id || row.candidateId;
          const assignments = candidateAssignments[candidateId] || [];
          const submissions = candidateSubmissions[candidateId] || [];
          const results = candidateResults[candidateId] || [];
          const latestResult =
            [...results].sort(
              (a, b) =>
                new Date(b.attemptedAt || 0).getTime() -
                new Date(a.attemptedAt || 0).getTime(),
            )[0] || null;

          return {
            ...row,
            candidateId,
            assignments,
            submissions,
            results,
            latestResult,
          };
        })
        .filter(
          (row) =>
            row.assignments.length > 0 ||
            row.submissions.length > 0 ||
            row.results.length > 0,
        ),
    [visibleRows, candidateAssignments, candidateSubmissions, candidateResults],
  );

  const feedbackRows = useMemo(
    () =>
      feedbackHistory
        .map((item) => {
          const row = visibleRows.find(
            (candidate) =>
              String(candidate.candidateId) ===
              String(item.candidateId || item.id),
          );

          return row
            ? {
                ...item,
                fullName: item.candidateName || row.fullName,
                interestedFor: row.jobTitle || "N/A",
              }
            : null;
        })
        .filter(Boolean),
    [feedbackHistory, visibleRows],
  );

  const getWorkflowStatusLabel = (row) =>
    row.workflowCandidate?.applicationStatus || "INTERESTED";

  const assignedTestCount = resultRows.reduce(
    (total, row) => total + row.assignments.length,
    0,
  );
  const completedSubmissionCount = resultRows.reduce(
    (total, row) => total + row.submissions.length,
    0,
  );

  return (
    <div className="hri-layout">
      {dialogNode}
      <aside className="hri-sidebar">
        <div className="hri-brand">
          <div className="hri-mark">V</div>
          <div>
            <h2>VirtueHire</h2>
            <p>HR Workspace</p>
          </div>
        </div>

        {hrUser && (
          <div className="hri-profile-card">
            <div className="hri-profile-avatar">
              {hrUser.profilePic && !hrProfileImageFailed ? (
                <img
                  src={hrUser.profilePic}
                  alt={hrUser.fullName || hrUser.name}
                  onError={() => setHrProfileImageFailed(true)}
                />
              ) : (
                <span>{(hrUser.fullName || hrUser.name || "HR").charAt(0).toUpperCase()}</span>
              )}
            </div>
            <div className="hri-profile-info">
              <strong>{hrUser.fullName || hrUser.name || "HR User"}</strong>
              <span>{hrUser.designation || hrUser.role || "HR Manager"}</span>
            </div>
          </div>
        )}

        <ul className="hr-nav-list">
          <li
            className="hr-nav-item"
            onClick={() => navigate("/hr/dashboard")}
          >
            <LayoutDashboard size={20} /> Dashboard
          </li>
          <li
            className="hr-nav-item"
            onClick={() => navigate("/hr/dashboard", { state: { activeTab: "candidates" } })}
          >
            <Users size={20} /> Candidates
          </li>
          <li className="hr-nav-group">
            <button
              type="button"
              className="hr-nav-item hr-nav-group-trigger active"
              onClick={() => navigate("/hr/dashboard", { state: { activeTab: "view-jobs" } })}
            >
              <BriefcaseBusiness size={20} /> Job Portal
            </button>
            <div className="hr-nav-group-panel">
              <button
                type="button"
                className="hr-sub-nav-item"
                onClick={() => navigate("/hr/dashboard", { state: { activeTab: "create-job" } })}
              >
                <PlusSquare size={18} /> Create a Job
              </button>
              <button
                type="button"
                className="hr-sub-nav-item"
                onClick={() => navigate("/hr/dashboard", { state: { activeTab: "view-jobs" } })}
              >
                <List size={18} /> View Jobs
              </button>
            </div>
          </li>
          <li
            className="hr-nav-item"
            onClick={() => navigate("/hr/dashboard", { state: { activeTab: "manage-tests" } })}
          >
            <ClipboardList size={20} /> Manage Tests
          </li>
        </ul>

        <div className="hri-sidebar-footer">
          <button className="hri-nav-btn danger" onClick={() => navigate("/hrs/login")}>
            <LogOut size={20} /> Sign Out
          </button>
        </div>
      </aside>

      <main className="hri-main">
        <section className="hri-hero">
          <div className="hri-hero-copy">
            <p className="hri-hero-eyebrow">HR WORKSPACE</p>
            <h1>Interested Candidates</h1>
            <p>
              Review applicants who showed interest in your openings, assign
              assessments, and record hiring decisions from one focused view.
            </p>
          </div>
          <div className="hri-hero-meta">
            <div className="hri-hero-badge">
              <BriefcaseBusiness size={16} />
              <span>{selectedJobTitle || "All Job Posts"}</span>
            </div>
            <div className="hri-hero-user">
              <strong>{hrUser?.fullName || hrUser?.name || "HR Team"}</strong>
              <span>Candidate interest pipeline</span>
            </div>
          </div>
        </section>

        <section className="hri-stats-grid">
          <article className="hri-stat-card">
            <div className="hri-stat-icon blue">
              <Users size={22} />
            </div>
            <div>
              <span>Interested Candidates</span>
              <strong>{visibleRows.length}</strong>
            </div>
          </article>
          <article className="hri-stat-card">
            <div className="hri-stat-icon indigo">
              <ClipboardList size={22} />
            </div>
            <div>
              <span>Assigned Tests</span>
              <strong>{assignedTestCount}</strong>
            </div>
          </article>
          <article className="hri-stat-card">
            <div className="hri-stat-icon green">
              <CheckCircle2 size={22} />
            </div>
            <div>
              <span>Submitted Tests</span>
              <strong>{completedSubmissionCount}</strong>
            </div>
          </article>
        </section>

        {message ? <div className="hri-inline-msg">{message}</div> : null}

        <section className="hri-card">
          <div className="hri-section-header">
            <div>
              <h2>Candidate Interest Pipeline</h2>
              <p>
                Candidates listed here have shared interest in your posted role.
              </p>
            </div>
            <span className="jobs-summary-badge">
              {visibleRows.length} Interested Candidate
              {visibleRows.length === 1 ? "" : "s"}
              {selectedJobTitle ? ` for ${selectedJobTitle}` : ""}
            </span>
          </div>

          {visibleRows.length === 0 ? (
            <div className="hri-empty-state">
              <Users size={36} />
              <p>No interested candidates available yet.</p>
            </div>
          ) : (
            <div className="hri-table-wrap">
              <table className="hri-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Skills</th>
                    <th>Experience</th>
                    <th>Interested For</th>
                    <th>Workflow Status</th>
                    <th>Assigned Tests</th>
                    <th>Contact Access</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleRows.map((row) => (
                    <tr key={`${row.jobId}_${row.candidateId}`}>
                      <td>
                        <div className="hri-candidate-cell">
                          <div className="hri-avatar">
                            {(row.fullName || "C").charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <div className="hri-cell-main">{row.fullName}</div>
                            <div className="hri-cell-sub">
                              {row.email || "No email available"}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className="hri-skill-chip">
                          {row.skills || "Not provided"}
                        </span>
                      </td>
                      <td>{row.experience ?? 0} years</td>
                      <td>
                        <div className="hri-cell-main">{row.jobTitle || "N/A"}</div>
                      </td>
                      <td>
                        <span className="hri-status pending">
                          {getWorkflowStatusLabel(row)}
                        </span>
                      </td>
                      <td>{(candidateAssignments[row.workflowCandidate?.id || row.candidateId] || []).length}</td>
                      <td>{statusBadge()}</td>
                      <td>
                        <div className="hri-action-row">
                          <button
                            type="button"
                            className="hri-btn"
                            onClick={() =>
                              navigate(`/hr/candidate/${row.candidateId}`, {
                                state: {
                                  candidate: {
                                    id: row.candidateId,
                                    fullName: row.fullName,
                                    skills: row.skills,
                                    experience: row.experience,
                                  },
                                  from: "/hr/interested-candidates",
                                },
                              })
                            }
                          >
                            <Eye size={14} />
                            View Profile
                          </button>
                          <button
                            type="button"
                            className="hri-btn contact"
                            onClick={() => openContact(row)}
                          >
                            Contact Candidate
                          </button>
                          <button
                            type="button"
                            className="hri-btn"
                            onClick={() => handleAssignTest(row)}
                          >
                            <FileText size={14} />
                            Assign Test
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="hri-card">
          <div className="hri-section-header">
            <div>
              <h2>HR Test Results & Review</h2>
              <p>
                Review assigned assessment progress before approving or
                rejecting candidates.
              </p>
            </div>
            <span className="jobs-summary-badge">
              {resultRows.length} Result Record{resultRows.length === 1 ? "" : "s"}
            </span>
          </div>

          {workflowLoading ? (
            <div className="hri-empty-state">
              <FileSearch size={36} />
              <p>Loading candidate test results...</p>
            </div>
          ) : resultRows.length === 0 ? (
            <div className="hri-empty-state">
              <FileSearch size={36} />
              <p>No assigned-test results available for these interested candidates yet.</p>
            </div>
          ) : (
            <div className="hri-table-wrap">
              <table className="hri-table">
                <thead>
                  <tr>
                    <th>Candidate</th>
                    <th>Assigned</th>
                    <th>Submitted</th>
                    <th>Latest Result</th>
                    <th>Review</th>
                  </tr>
                </thead>
                <tbody>
                  {resultRows.map((row) => (
                    <tr key={`result_${row.candidateId}`}>
                      <td>
                        <div className="hri-cell-main">{row.fullName}</div>
                        <div className="hri-cell-sub">{row.jobTitle || "N/A"}</div>
                      </td>
                      <td>
                        <span className="hri-pill">
                          <ClipboardList size={14} />
                          {row.assignments.length} tests
                        </span>
                      </td>
                      <td>
                        <span className="hri-pill">
                          <CheckCircle2 size={14} />
                          {row.submissions.length} submissions
                        </span>
                      </td>
                      <td>
                        {row.latestResult ? (
                          <div>
                            <div className="hri-cell-main">
                              {row.latestResult.subject} - {row.latestResult.score}%
                            </div>
                            <div className="hri-cell-sub">
                              Level {row.latestResult.level}
                            </div>
                          </div>
                        ) : (
                          <span className="hri-cell-sub">Awaiting completion</span>
                        )}
                      </td>
                      <td>
                        <div className="hri-action-row">
                          <button
                            type="button"
                            className="hri-btn"
                            onClick={() => handleFeedback(row, "approve")}
                            disabled={row.submissions.length === 0 && row.results.length === 0}
                          >
                            <UserCheck size={14} />
                            Approve
                          </button>
                          <button
                            type="button"
                            className="hri-btn request"
                            onClick={() => handleFeedback(row, "reject")}
                            disabled={row.submissions.length === 0 && row.results.length === 0}
                          >
                            <UserX size={14} />
                            Reject
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="hri-card">
          <div className="hri-section-header">
            <div>
              <h2>Feedback History</h2>
              <p>
                Track final approve or reject decisions sent back to Admin.
              </p>
            </div>
            <span className="jobs-summary-badge">
              {feedbackRows.length} Feedback Record{feedbackRows.length === 1 ? "" : "s"}
            </span>
          </div>

          {feedbackRows.length === 0 ? (
            <div className="hri-empty-state">
              <MessageSquare size={36} />
              <p>No approve/reject feedback recorded for these interested candidates yet.</p>
            </div>
          ) : (
            <div className="hri-table-wrap">
              <table className="hri-table">
                <thead>
                  <tr>
                    <th>Candidate</th>
                    <th>Status</th>
                    <th>Feedback</th>
                    <th>Tests</th>
                  </tr>
                </thead>
                <tbody>
                  {feedbackRows.map((row) => (
                    <tr key={`feedback_${row.candidateId || row.id}`}>
                      <td>
                        <div className="hri-cell-main">{row.fullName}</div>
                        <div className="hri-cell-sub">{row.interestedFor}</div>
                      </td>
                      <td>
                        <span
                          className={`hri-status ${
                            row.status === "APPROVED" ? "approved" : "rejected"
                          }`}
                        >
                          {row.status}
                        </span>
                      </td>
                      <td>
                        <div className="hri-feedback-text">
                          <MessageSquare size={14} />
                          {row.feedback || "No feedback provided"}
                        </div>
                      </td>
                      <td>
                        <span className="hri-pill">
                          <Award size={14} />
                          {row.testCount || 0} tests
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>

      {assignTestModal.open && assignTestModal.candidate ? (
        <AssignTestModal
          candidate={assignTestModal.candidate}
          candidates={workflowCandidates}
          onClose={() => setAssignTestModal({ open: false, candidate: null })}
          onAssigned={handleWorkflowAssigned}
        />
      ) : null}

      {feedbackModal.open && feedbackModal.candidate ? (
        <FeedbackModal
          candidate={feedbackModal.candidate}
          action={feedbackModal.action}
          onClose={() =>
            setFeedbackModal({ open: false, candidate: null, action: null })
          }
          onStatusChanged={handleWorkflowStatusChanged}
        />
      ) : null}
    </div>
  );
}
