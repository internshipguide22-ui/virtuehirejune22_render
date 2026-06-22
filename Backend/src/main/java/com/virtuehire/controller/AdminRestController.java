package com.virtuehire.controller;

import com.virtuehire.model.*;
import com.virtuehire.service.*;
import com.virtuehire.util.StoragePathResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminRestController {

    private static final Logger logger = LoggerFactory.getLogger(AdminRestController.class);

    private final HrService hrService;
    private final PaymentService paymentService;
    private final CandidateService candidateService;
    private final CandidateAccessRequestService candidateAccessRequestService;
    private final QuestionService questionService;
    private final AssessmentResultService assessmentResultService;
    private final AssessmentService assessmentService;
    private final AdminNotificationService adminNotificationService;
    private final HiringWorkflowService hiringWorkflowService;
    private final TestAllocationService testAllocationService;
    private final Path uploadDir;

    public AdminRestController(HrService hrService, PaymentService paymentService,
            CandidateService candidateService, CandidateAccessRequestService candidateAccessRequestService,
            QuestionService questionService,
            AssessmentResultService assessmentResultService, AssessmentService assessmentService,
            AdminNotificationService adminNotificationService,
            HiringWorkflowService hiringWorkflowService,
            TestAllocationService testAllocationService,
            @Value("${file.upload-dir}") String uploadDirPath) {
        this.hrService = hrService;
        this.paymentService = paymentService;
        this.candidateService = candidateService;
        this.candidateAccessRequestService = candidateAccessRequestService;
        this.questionService = questionService;
        this.assessmentResultService = assessmentResultService;
        this.assessmentService = assessmentService;
        this.adminNotificationService = adminNotificationService;
        this.hiringWorkflowService = hiringWorkflowService;
        this.testAllocationService = testAllocationService;
        this.uploadDir = StoragePathResolver.resolveUploadDir(uploadDirPath);
    }

    private ResponseEntity<Map<String, String>> requireAdmin(HttpSession session) {
        Object role = session.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        return null;
    }

    // ---------------------- DASHBOARD ------------------------
    @GetMapping("/dashboard")
    public ResponseEntity<?> adminDashboard() {

        List<Hr> allHrs = hrService.findAll();
        List<Candidate> allCandidates = candidateService.findAll();
        List<Payment> allPayments = paymentService.getAllPayments();

        long totalHrs = allHrs.size();
        long verifiedHrs = allHrs.stream().filter(hr -> Boolean.TRUE.equals(hr.getVerified())).count();
        long unverifiedHrs = allHrs.stream()
                .filter(hr -> Boolean.TRUE.equals(hr.getEmailVerified()) && !Boolean.TRUE.equals(hr.getVerified()))
                .count();

        long totalCandidates = allCandidates.size();
        long candidatesWithTest = assessmentResultService.getTotalAssessmentTracksTaken();

        long pendingCandidates = allCandidates.stream()
                .filter(c -> Boolean.FALSE.equals(c.getApproved()))
                .count();

        Map<String, Object> paymentStats = paymentService.getPaymentStatistics();

        double totalRevenue = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount)
                .sum();

        Map<String, Object> response = new HashMap<>();
        response.put("hrs", allHrs);
        response.put("candidates", allCandidates);
        response.put("payments", allPayments);
        response.put("totalHrs", totalHrs);
        response.put("verifiedHrs", verifiedHrs);
        response.put("unverifiedHrs", unverifiedHrs);
        response.put("totalCandidates", totalCandidates);
        response.put("candidatesWithTest", candidatesWithTest);
        response.put("pendingCandidates", pendingCandidates);
        response.put("paymentStats", paymentStats);
        response.put("totalRevenue", totalRevenue);
        response.put("pendingAccessRequests", candidateAccessRequestService.countPending());
        response.put("pendingCombinedAssessmentRequests",
                adminNotificationService.countOpenCombinedAssessmentNotifications());
        response.put("combinedAssessmentNotifications",
                adminNotificationService.getOpenCombinedAssessmentNotifications().stream()
                        .map(notification -> Map.of(
                                "id", notification.getId(),
                                "message", notification.getMessage(),
                                "createdAt", notification.getCreatedAt()))
                        .toList());

        return ResponseEntity.ok(response);
    }

    // ---------------------- HR LIST ------------------------
    @GetMapping("/hrs")
    public ResponseEntity<?> showAllHrs(@RequestParam(required = false, defaultValue = "all") String filter) {

        List<Hr> hrs;

        if ("verified".equals(filter)) {
            hrs = hrService.findAll().stream()
                    .filter(hr -> Boolean.TRUE.equals(hr.getVerified()))
                    .toList();
        } else if ("unverified".equals(filter)) {
            hrs = hrService.findAll().stream()
                    .filter(hr -> Boolean.TRUE.equals(hr.getEmailVerified()) && !Boolean.TRUE.equals(hr.getVerified()))
                    .toList();
        } else {
            hrs = hrService.findAll();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("hrs", hrs);
        response.put("filter", filter);

        return ResponseEntity.ok(response);
    }

    // ---------------------- PENDING CANDIDATES ------------------------
    @GetMapping("/candidates/pending")
    public ResponseEntity<?> showPendingCandidates() {
        List<Candidate> pendingCandidates = candidateService.findAll().stream()
                .filter(c -> Boolean.FALSE.equals(c.getApproved()))
                .toList();
        return ResponseEntity.ok(Map.of("candidates", pendingCandidates));
    }

    // ---------------------- APPROVE CANDIDATE ------------------------
    @PostMapping("/candidates/approve/{id}")
    public ResponseEntity<?> approveCandidate(@PathVariable Long id) {

        Candidate candidate = candidateService.findById(id).orElse(null);
        if (candidate != null) {
            int currentYear = java.time.Year.now().getValue();
            if (candidate.getYearOfGraduation() != null && candidate.getYearOfGraduation() <= currentYear + 1) {
                candidate.setApproved(true);
                candidateService.save(candidate);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Candidate approved"));
    }

    // ---------------------- REJECT CANDIDATE ------------------------
    @PostMapping("/candidates/reject/{id}")
    public ResponseEntity<?> rejectCandidate(@PathVariable Long id,
            @RequestParam String reason) {

        Candidate candidate = candidateService.findById(id).orElse(null);
        if (candidate != null) {
            candidate.setRejectionReason(reason);
            candidate.setApproved(false);
            candidateService.save(candidate);
        }

        return ResponseEntity.ok(Map.of("message", "Candidate rejected"));
    }

    // ---------------------- VERIFY HR ------------------------
    @PostMapping("/verify/{id}")
    public ResponseEntity<?> verifyHr(@PathVariable Long id) {
        Hr hr = hrService.findById(id).orElse(null);
        if (hr != null) {
            hr.setVerified(true);
            hrService.save(hr);
            try {
                hrService.sendApprovalMail(hr);
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok(Map.of("message", "HR verified"));
    }

    // ---------------------- UNVERIFY HR ------------------------
    @PostMapping("/unverify/{id}")
    public ResponseEntity<?> unverifyHr(@PathVariable Long id) {
        Hr hr = hrService.findById(id).orElse(null);
        if (hr != null) {
            hr.setVerified(false);
            hrService.save(hr);
        }
        return ResponseEntity.ok(Map.of("message", "HR unverified"));
    }

    @DeleteMapping("/hrs/{id}")
    public ResponseEntity<?> deleteHr(@PathVariable Long id) {
        try {
            hrService.deleteHrById(id);
            return ResponseEntity.ok(Map.of("message", "HR deleted successfully"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of(
                    "error",
                    ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage()
                            : "Failed to delete HR account"));
        }
    }

    // ---------------------- QUESTIONS ------------------------
    @GetMapping("/questions")
    public ResponseEntity<?> questionManagement(@RequestParam(required = false) String subject) {

        List<Question> questions = (subject != null && !subject.trim().isEmpty())
                ? questionService.getQuestionsBySubject(subject)
                : questionService.getAllQuestionsFromRepository();

        List<String> subjects = questionService.getAllSubjects();

        Map<String, Object> response = new HashMap<>();
        response.put("questions", questions);
        response.put("subjects", subjects);
        response.put("selectedSubject", subject);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/questions/add")
    public ResponseEntity<?> addQuestion(@RequestBody Question question) {
        questionService.saveQuestionViaRepository(question);
        return ResponseEntity.ok(Map.of("message", "Question added"));
    }

    @PostMapping("/questions/upload")
    public ResponseEntity<?> uploadQuestions(@RequestParam("file") MultipartFile file,
            @RequestParam("testName") String testName,
            @RequestParam(value = "input1", required = false) String input1,
            @RequestParam(value = "output1", required = false) String output1,
            @RequestParam(value = "input2", required = false) String input2,
            @RequestParam(value = "output2", required = false) String output2) {
        try {
            questionService.saveQuestionsFromUpload(file, testName, input1, output1, input2, output2);
            return ResponseEntity.ok(Map.of("message", "Questions uploaded successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload: " + e.getMessage()));
        }
    }

    @PostMapping("/assessment/config")
    public ResponseEntity<?> saveAssessmentConfig(@RequestBody List<AssessmentConfig> configs) {
        try {
            questionService.saveConfigs(configs);
            return ResponseEntity.ok(Map.of("message", "Configuration saved"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save: " + e.getMessage()));
        }
    }

    @GetMapping("/assessment/config/{subject}")
    public ResponseEntity<?> getAssessmentConfig(@PathVariable String subject) {
        return ResponseEntity.ok(questionService.getConfigs(subject));
    }

    @GetMapping("/questions/edit/{id}")
    public ResponseEntity<?> getQuestion(@PathVariable Long id) {
        Question question = questionService.getQuestionByIdFromRepository(id);
        if (question != null)
            return ResponseEntity.ok(question);
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/questions/update/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable Long id,
            @RequestBody Question updatedData) {
        Question existing = questionService.getQuestionByIdFromRepository(id);
        if (existing != null) {
            existing.setSubject(updatedData.getSubject());
            existing.setSectionName(updatedData.getSectionName());
            existing.setLevel(updatedData.getLevel());
            existing.setText(updatedData.getText());
            existing.setOptions(updatedData.getOptions());
            existing.setCorrectAnswer(updatedData.getCorrectAnswer());
            questionService.saveQuestionViaRepository(existing);
        }
        return ResponseEntity.ok(Map.of("message", "Question updated"));
    }

    @PostMapping("/questions/delete/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestionViaRepository(id);
        return ResponseEntity.ok(Map.of("message", "Question deleted"));
    }

    // ---------------------- CANDIDATE DETAILS ------------------------
    @GetMapping("/candidates/{id}")
    public ResponseEntity<?> viewCandidate(@PathVariable Long id) {
        Candidate candidate = candidateService.findById(id).orElse(null);
        if (candidate == null)
            return ResponseEntity.notFound().build();

        List<AssessmentResult> results = assessmentResultService.getCandidateResults(id);

        // FIX: Add cache-control headers to prevent browser caching candidate data
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(Map.of(
                        "candidate", candidate,
                        "results", results,
                        "statusSummary", assessmentResultService.getCandidateStatusSummary(id)));
    }

    @GetMapping("/candidates")
    public ResponseEntity<?> getAllCandidates() {
        // FIX: Add cache-control headers to prevent browser caching candidate data
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(Map.of("candidates", candidateService.findAll()));
    }

    @DeleteMapping("/candidates/{id}")
    public ResponseEntity<?> deleteCandidate(@PathVariable Long id) {
        try {
            candidateService.deleteCandidateById(id);
            return ResponseEntity.ok(Map.of("message", "Candidate deleted successfully"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/candidates/{id}")
    public ResponseEntity<?> updateCandidate(@PathVariable Long id, @RequestBody Candidate payload) {
        Candidate candidate = candidateService.findById(id).orElse(null);
        if (candidate == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        }

        if (payload.getEmail() != null && !payload.getEmail().isBlank()) {
            Candidate existingByEmail = candidateService.findByEmail(payload.getEmail());
            if (existingByEmail != null && !existingByEmail.getId().equals(candidate.getId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email already registered. Please use a different email."));
            }
        }

        candidate.setFullName(payload.getFullName());
        candidate.setEmail(payload.getEmail());
        candidate.setPhoneNumber(payload.getPhoneNumber());
        candidate.setAlternatePhoneNumber(payload.getAlternatePhoneNumber());
        candidate.setGender(payload.getGender());
        candidate.setDateOfBirth(payload.getDateOfBirth());
        candidate.setCity(payload.getCity());
        candidate.setState(payload.getState());
        candidate.setHighestEducation(payload.getHighestEducation());
        candidate.setCollegeUniversity(payload.getCollegeUniversity());
        candidate.setYearOfGraduation(payload.getYearOfGraduation());
        candidate.setExperience(payload.getExperience());
        candidate.setExperienceLevel(payload.getExperienceLevel());
        candidate.setSkills(payload.getSkills());
        candidate.setBadge(payload.getBadge());
        candidate.setApproved(payload.getApproved());
        candidate.setAssessmentTaken(payload.getAssessmentTaken());
        candidate.setSelectionStatus(payload.getSelectionStatus());
        candidate.setSelectionNote(payload.getSelectionNote());
        candidate.setRejectionReason(payload.getRejectionReason());

        Candidate savedCandidate = candidateService.save(candidate);
        return ResponseEntity.ok(Map.of(
                "message", "Candidate updated successfully",
                "candidate", savedCandidate));
    }

    @GetMapping("/candidate-access-requests")
    public ResponseEntity<?> getCandidateAccessRequests(
            @RequestParam(required = false) String status,
            HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        CandidateAccessRequestStatus requestStatus = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            try {
                requestStatus = CandidateAccessRequestStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status filter."));
            }
        }

        List<Map<String, Object>> requests = candidateAccessRequestService.getRequests(requestStatus).stream()
                .map(request -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", request.getId());
                    data.put("status", request.getStatus().name());
                    data.put("createdAt", request.getCreatedAt());
                    data.put("updatedAt", request.getUpdatedAt());
                    data.put("reviewedAt", request.getReviewedAt());
                    data.put("hrId", request.getHr().getId());
                    data.put("hrName", request.getHr().getFullName());
                    data.put("hrEmail", request.getHr().getEmail());
                    data.put("candidateId", request.getCandidate().getId());
                    data.put("candidateName", request.getCandidate().getFullName());
                    data.put("candidateRole",
                            request.getCandidate().getBadge() != null && !request.getCandidate().getBadge().isBlank()
                                    ? request.getCandidate().getBadge()
                                    : request.getCandidate().getExperienceLevel() != null
                                            && !request.getCandidate().getExperienceLevel().isBlank()
                                                    ? request.getCandidate().getExperienceLevel()
                                                    : "Candidate");
                    data.put("candidateExperience",
                            request.getCandidate().getExperience() != null ? request.getCandidate().getExperience()
                                    : 0);
                    return data;
                })
                .toList();

        return ResponseEntity.ok(Map.of("requests", requests));
    }

    @PostMapping("/candidate-access-requests/{requestId}/approve")
    public ResponseEntity<?> approveCandidateAccessRequest(@PathVariable Long requestId, HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            candidateAccessRequestService.approve(requestId);
            return ResponseEntity.ok(Map.of("message", "Access request approved."));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/candidate-access-requests/{requestId}/reject")
    public ResponseEntity<?> rejectCandidateAccessRequest(@PathVariable Long requestId, HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            candidateAccessRequestService.reject(requestId);
            return ResponseEntity.ok(Map.of("message", "Access request rejected."));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/candidate-access-requests/{requestId}/decline")
    public ResponseEntity<?> declineCandidateAccessRequest(@PathVariable Long requestId, HttpSession session) {
        return rejectCandidateAccessRequest(requestId, session);
    }

    // ---------------------- HR DETAILS ------------------------
    @GetMapping("/hrs/{id}")
    public ResponseEntity<?> viewHrDetails(@PathVariable Long id) {

        Hr hr = hrService.findById(id).orElse(null);
        if (hr == null)
            return ResponseEntity.notFound().build();

        return ResponseEntity.ok(Map.of(
                "hr", hr,
                "payments", paymentService.getPaymentsByHr(id)));
    }

    // ---------------------- CANDIDATE FILES ------------------------
    @GetMapping("/candidates/{candidateId}/resume")
    public ResponseEntity<Resource> accessCandidateResume(
            @PathVariable Long candidateId,
            @RequestParam(defaultValue = "inline") String disposition,
            HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null) {
            return ResponseEntity.status(forbidden.getStatusCode()).build();
        }

        Candidate candidate = candidateService.findById(candidateId).orElse(null);
        if (candidate == null || candidate.getResumePath() == null || candidate.getResumePath().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            // CHANGED: Admin resume view/download now uses the same safe file resolver
            // as profile images, so files work from Backend/uploads or sibling uploads.
            return serveStoredFile(candidate.getResumePath(), disposition);
        } catch (Exception e) {
            logger.error("Failed to serve admin candidate resume for candidate {}", candidateId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/candidates/{candidateId}/profile-picture")
    public ResponseEntity<Resource> accessCandidateProfilePicture(
            @PathVariable Long candidateId,
            @RequestParam(defaultValue = "inline") String disposition,
            HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null) {
            return ResponseEntity.status(forbidden.getStatusCode()).build();
        }

        Candidate candidate = candidateService.findById(candidateId).orElse(null);
        if (candidate == null || candidate.getProfilePic() == null || candidate.getProfilePic().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            // CHANGED: Added an admin profile-image endpoint so the candidate
            // details page no longer depends on the public candidate file route.
            return serveStoredFile(candidate.getProfilePic(), disposition);
        } catch (Exception e) {
            logger.error("Failed to serve admin candidate profile picture for candidate {}", candidateId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Backward-compatible route kept for older admin screens.
    @GetMapping("/download/resume/{candidateId}")
    public ResponseEntity<Resource> downloadResume(@PathVariable Long candidateId, HttpSession session) {
        return accessCandidateResume(candidateId, "attachment", session);
    }

    // ---------------------- PAYMENTS ------------------------
    @GetMapping("/payments")
    public ResponseEntity<?> showAllPayments() {

        List<Payment> allPayments = paymentService.getAllPayments();

        double totalRevenue = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount)
                .sum();

        long successfulPayments = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .count();

        return ResponseEntity.ok(Map.of(
                "payments", allPayments,
                "totalRevenue", totalRevenue,
                "successfulPayments", successfulPayments,
                "totalPayments", allPayments.size()));
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<?> viewPaymentDetails(@PathVariable Long id) {
        Payment payment = paymentService.getPaymentById(id);
        if (payment != null)
            return ResponseEntity.ok(payment);
        return ResponseEntity.notFound().build();
    }

    // ---------------------- ADMIN TEST MANAGEMENT ------------------------
    @GetMapping("/subjects-info")
    public ResponseEntity<?> getSubjectsInfo(HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        List<String> subjects = questionService.getAllSubjects();
        List<Map<String, Object>> infoList = new ArrayList<>();

        for (String subject : subjects) {
            List<Question> questions = questionService.getQuestionsBySubject(subject);
            int compilerCount = (int) questions.stream().filter(Question::isHasCompiler).count();
            infoList.add(Map.of(
                    "subject", subject,
                    "count", questions.size(),
                    "compilerCount", compilerCount,
                    "noCompilerCount", questions.size() - compilerCount));
        }

        return ResponseEntity.ok(Map.of("subjects", infoList));
    }

    @PostMapping("/questions/upload-csv")
    public ResponseEntity<?> uploadQuestionsCsv(@RequestParam("file") MultipartFile file,
            @RequestParam("testName") String testName,
            @RequestParam(value = "input1", required = false) String input1,
            @RequestParam(value = "output1", required = false) String output1,
            @RequestParam(value = "input2", required = false) String input2,
            @RequestParam(value = "output2", required = false) String output2,
            HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            questionService.saveQuestionsFromUpload(file, testName, input1, output1, input2, output2);
            return ResponseEntity.ok(Map.of("message", "Questions uploaded successfully for " + testName));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error uploading CSV: " + e.getMessage()));
        }
    }

    @PostMapping("/assessments/create")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> createAssessment(@RequestBody Map<String, Object> payload, HttpSession session) {
        try {
            ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
            if (forbidden != null)
                return forbidden;

            String assessmentName = (String) payload.get("assessmentName");
            String description = (String) payload.getOrDefault("description", "");
            List<Map<String, Object>> sections = (List<Map<String, Object>>) payload.get("sections");

            if (assessmentName == null || assessmentName.trim().isEmpty() || sections == null || sections.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid assessment data."));
            }

            Assessment assessment = assessmentService.createAssessment(assessmentName, description, sections);
            candidateService.refreshAllAssessmentAssignments();
            return ResponseEntity.ok(Map.of(
                    "message", "Assessment created successfully",
                    "assessmentId", assessment.getId()));
        } catch (RuntimeException e) {
            logger.warn("Admin assessment creation failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Admin assessment creation failed unexpectedly", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create assessment: " + e.getMessage()));
        } catch (Throwable e) {
            logger.error("Admin assessment creation failed fatally", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error",
                    "Failed to create assessment: " + e.getClass().getSimpleName()
                            + (e.getMessage() != null && !e.getMessage().isBlank() ? " - " + e.getMessage() : "")));
        }
    }

    @GetMapping("/assessments/live")
    public ResponseEntity<?> getLiveAssessments(HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        List<Assessment> assessments = assessmentService.getAllAssessments();
        List<Map<String, Object>> liveList = new ArrayList<>();

        for (Assessment assessment : assessments) {
            List<AssessmentSection> sections = assessmentService.getAssessmentSections(assessment.getId());
            int totalQuestions = sections.stream().mapToInt(AssessmentSection::getQuestionCount).sum();
            int totalTime = sections.stream().mapToInt(AssessmentSection::getSectionTime).sum();

            // Get section modes
            List<String> sectionModes = sections.stream()
                    .map(AssessmentSection::getSectionMode)
                    .distinct()
                    .toList();

            liveList.add(Map.of(
                    "id", assessment.getId(),
                    "assessmentName", assessment.getAssessmentName(),
                    "description", assessment.getDescription() != null ? assessment.getDescription() : "",
                    "sectionCount", sections.size(),
                    "totalQuestions", totalQuestions,
                    "totalTime", totalTime,
                    "isLocked", assessment.isLocked(),
                    "sectionModes", sectionModes));
        }

        return ResponseEntity.ok(Map.of("assessments", liveList));
    }

    @GetMapping("/tests")
    public ResponseEntity<?> getAdminTests(HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        return ResponseEntity.ok(Map.of("tests", testAllocationService.getAvailableTests()));
    }

    @GetMapping("/feedback")
    public ResponseEntity<?> getAdminFeedback(HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        return ResponseEntity.ok(Map.of(
                "candidates", hiringWorkflowService.getAllApprovedRejectedCandidates(),
                "allocationHistory", testAllocationService.getTestAssignmentReport()));
    }

    @DeleteMapping("/assessments/{id}")
    public ResponseEntity<?> deleteAssessment(@PathVariable Long id, HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            assessmentService.deleteAssessment(id);
            return ResponseEntity.ok(Map.of("message", "Assessment deleted successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage() != null && !e.getMessage().isBlank()
                            ? e.getMessage()
                            : "Failed to delete assessment."));
        }
    }

    @PutMapping("/assessments/{id}/lock")
    public ResponseEntity<?> toggleLock(@PathVariable Long id,
            @RequestParam boolean lock,
            HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            assessmentService.toggleLock(id, lock);
            return ResponseEntity
                    .ok(Map.of("message", "Assessment " + (lock ? "locked" : "unlocked") + " successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update assessment status."));
        }
    }

    @PutMapping("/assessments/{id}/section-mode")
    public ResponseEntity<?> updateSectionMode(@PathVariable Long id,
            @RequestParam String sectionMode,
            @RequestParam(required = false) String supportedLanguages,
            HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            assessmentService.updateSectionMode(id, sectionMode, supportedLanguages);
            return ResponseEntity.ok(Map.of("message", "Section mode updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update section mode: " + e.getMessage()));
        }
    }

    // ------------------ CANDIDATE CUMULATIVE RESULTS (BADGES/EXPERT STATUS)
    // ------------------
    // FIX: Added endpoint so Admin can see candidate Expert badges with verified
    // indicator
    @GetMapping("/candidates/{candidateId}/cumulative-results")
    public ResponseEntity<?> getCandidateCumulativeResults(@PathVariable Long candidateId, HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        Candidate candidate = candidateService.findById(candidateId).orElse(null);
        if (candidate == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        }

        // Get cumulative results with badge info (e.g., "Java1 Expert")
        List<Map<String, Object>> cumulativeResults = assessmentResultService
                .getCandidateCumulativeResults(candidateId);

        return ResponseEntity.ok(Map.of(
                "candidateId", candidateId,
                "candidateName", candidate.getFullName(),
                "cumulativeResults", cumulativeResults,
                "currentBadge", candidate.getBadge()));
    }

    private ResponseEntity<Resource> serveStoredFile(String storedFileName, String disposition) throws IOException {
        String normalizedStoredFileName = extractFileName(storedFileName);
        if (normalizedStoredFileName.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        Path path = resolveStoredFilePath(normalizedStoredFileName);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }

        Path alternateDir = getAlternateUploadDir();
        boolean inUploadDir = path.startsWith(uploadDir);
        boolean inAlternateDir = alternateDir != null && path.startsWith(alternateDir);

        if (!inUploadDir && !inAlternateDir) {
            logger.warn("Admin file path outside allowed directories: {}", path);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Resource resource = new UrlResource(path.toUri());
        if (!Files.exists(path) || !resource.isReadable()) {
            logger.warn("Admin file not found or unreadable: {}", path);
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(path);
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        String safeDisposition = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        String downloadName = getOriginalFileName(normalizedStoredFileName);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, safeDisposition + "; filename=\"" + downloadName + "\"")
                .body(resource);
    }

    private Path resolveStoredFilePath(String storedFileName) {
        Path primaryPath = uploadDir.resolve(storedFileName).normalize();
        if (Files.exists(primaryPath) && Files.isReadable(primaryPath)) {
            return primaryPath;
        }

        Path alternateUploadDir = getAlternateUploadDir();
        if (alternateUploadDir != null) {
            Path alternatePath = alternateUploadDir.resolve(storedFileName).normalize();
            if (Files.exists(alternatePath) && Files.isReadable(alternatePath)) {
                return alternatePath;
            }
        }

        Path fuzzyMatch = findMatchingStoredFile(storedFileName);
        return fuzzyMatch != null ? fuzzyMatch : primaryPath;
    }

    private Path findMatchingStoredFile(String storedFileName) {
        String normalizedRequestedName = extractFileName(storedFileName);
        String requestedOriginalName = getOriginalFileName(normalizedRequestedName);

        List<Path> candidateDirs = new ArrayList<>();
        candidateDirs.add(uploadDir);
        Path alternateUploadDir = getAlternateUploadDir();
        if (alternateUploadDir != null && !alternateUploadDir.equals(uploadDir)) {
            candidateDirs.add(alternateUploadDir);
        }

        for (Path dir : candidateDirs) {
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }

            try (var stream = Files.list(dir)) {
                Optional<Path> match = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String candidateName = extractFileName(path.getFileName().toString());
                            String candidateOriginalName = getOriginalFileName(candidateName);
                            return candidateName.equalsIgnoreCase(normalizedRequestedName)
                                    || candidateOriginalName.equalsIgnoreCase(normalizedRequestedName)
                                    || candidateOriginalName.equalsIgnoreCase(requestedOriginalName);
                        })
                        .findFirst();

                if (match.isPresent()) {
                    return match.get().normalize();
                }
            } catch (IOException ignored) {
            }
        }

        return null;
    }

    private Path getAlternateUploadDir() {
        Path current = uploadDir.toAbsolutePath().normalize();
        Path parent = current.getParent();
        if (parent == null) {
            return null;
        }

        Path currentName = current.getFileName();
        Path parentName = parent.getFileName();

        if (currentName != null && "uploads".equalsIgnoreCase(currentName.toString())
                && parentName != null && "Backend".equalsIgnoreCase(parentName.toString())
                && parent.getParent() != null) {
            Path siblingUploads = parent.getParent().resolve("uploads").normalize();
            if (Files.exists(siblingUploads)) {
                return siblingUploads;
            }
        }

        return null;
    }

    private String extractFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return Paths.get(fileName).getFileName().toString().trim();
    }

    private String getOriginalFileName(String storedFileName) {
        String safeFileName = extractFileName(storedFileName);
        int separatorIndex = safeFileName.indexOf('_');
        if (separatorIndex > -1 && separatorIndex < safeFileName.length() - 1) {
            return safeFileName.substring(separatorIndex + 1);
        }
        return safeFileName;
    }
}
