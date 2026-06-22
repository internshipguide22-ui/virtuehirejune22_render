package com.virtuehire.controller;

import com.virtuehire.model.Candidate;
import com.virtuehire.model.CandidateStatus;
import com.virtuehire.model.JobInterest;
import com.virtuehire.model.JobPost;
import com.virtuehire.repository.CandidateRepository;
import com.virtuehire.repository.JobInterestRepository;
import com.virtuehire.repository.JobPostRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
public class JobRestController {

    private final JobPostRepository jobPostRepository;
    private final JobInterestRepository jobInterestRepository;
    private final CandidateRepository candidateRepository;

    public JobRestController(JobPostRepository jobPostRepository,
            JobInterestRepository jobInterestRepository,
            CandidateRepository candidateRepository) {
        this.jobPostRepository = jobPostRepository;
        this.jobInterestRepository = jobInterestRepository;
        this.candidateRepository = candidateRepository;
    }

    @GetMapping
    public ResponseEntity<?> listJobs() {
        return ResponseEntity.ok(withCandidateResponses(jobPostRepository.findAllByOrderByCreatedAtDesc()));
    }

    @PostMapping
    public ResponseEntity<?> createJob(@RequestBody JobPost jobPost, HttpSession session) {
        if (!"HR".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            return ResponseEntity.status(403).body(Map.of("error", "Only HR users can publish jobs"));
        }

        if (isBlank(jobPost.getTitle()) || isBlank(jobPost.getCompany())
                || isBlank(jobPost.getLocation()) || isBlank(jobPost.getDescription())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Title, company, location, and description are required"
            ));
        }

        jobPost.setId(null);
        return ResponseEntity.ok(withCandidateResponses(jobPostRepository.save(jobPost)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateJob(@PathVariable Long id, @RequestBody JobPost payload, HttpSession session) {
        if (!"HR".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            return ResponseEntity.status(403).body(Map.of("error", "Only HR users can update jobs"));
        }

        return jobPostRepository.findById(id)
                .map(job -> {
                    job.setTitle(payload.getTitle());
                    job.setCompany(payload.getCompany());
                    job.setLocation(payload.getLocation());
                    job.setType(payload.getType());
                    job.setSalary(payload.getSalary());
                    job.setExperience(payload.getExperience());
                    job.setSkills(payload.getSkills());
                    job.setDescription(payload.getDescription());
                    if (!isBlank(payload.getStatus())) {
                        job.setStatus(payload.getStatus());
                    }
                    return ResponseEntity.ok(withCandidateResponses(jobPostRepository.save(job)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload,
            HttpSession session) {
        if (!"HR".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            return ResponseEntity.status(403).body(Map.of("error", "Only HR users can update jobs"));
        }

        String status = payload.get("status");
        if (!"open".equals(status) && !"paused".equals(status) && !"closed".equals(status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid job status"));
        }

        return jobPostRepository.findById(id)
                .map(job -> {
                    job.setStatus(status);
                    return ResponseEntity.ok(withCandidateResponses(jobPostRepository.save(job)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/responses")
    public ResponseEntity<?> submitResponse(@PathVariable Long id, @RequestBody Map<String, String> payload,
            HttpSession session) {
        String status = payload.get("status");
        if (!"interested".equals(status) && !"not_interested".equals(status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid response status"));
        }

        Candidate sessionCandidate = (Candidate) session.getAttribute("candidate");
        if (sessionCandidate == null || sessionCandidate.getId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Candidate login required"));
        }

        JobPost job = jobPostRepository.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        }
        if (!"open".equals(job.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "This job is not accepting responses"));
        }

        Candidate candidate = candidateRepository.findById(sessionCandidate.getId()).orElse(null);
        if (candidate == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Candidate not found"));
        }

        if (jobInterestRepository.findByJobIdAndCandidateId(id, candidate.getId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Response already submitted"));
        }

        JobInterest response = new JobInterest();
        response.setJobId(id);
        response.setCandidateId(candidate.getId());
        response.setFullName(candidate.getFullName());
        response.setEmail(candidate.getEmail());
        response.setPhoneNumber(candidate.getPhoneNumber());
        response.setSkills(candidate.getSkills());
        response.setExperience(candidate.getExperience());
        response.setStatus(status);
        JobInterest savedResponse = jobInterestRepository.save(response);

        if ("interested".equals(status)
                && candidate.getApplicationStatus() != CandidateStatus.APPROVED
                && candidate.getApplicationStatus() != CandidateStatus.REJECTED
                && candidate.getApplicationStatus() != CandidateStatus.TEST_ASSIGNED) {
            candidate.setApplicationStatus(CandidateStatus.INTERESTED);
            candidateRepository.save(candidate);
        }

        return ResponseEntity.ok(Map.of(
                "updated", true,
                "status", status,
                "response", toCandidateResponse(savedResponse)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable Long id, HttpSession session) {
        if (!"HR".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) {
            return ResponseEntity.status(403).body(Map.of("error", "Only HR users can delete jobs"));
        }

        if (!jobPostRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        jobPostRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private JobPost withCandidateResponses(JobPost job) {
        if (job == null || job.getId() == null) {
            return job;
        }
        List<Map<String, Object>> responses = jobInterestRepository.findByJobIdIn(List.of(job.getId()))
                .stream()
                .map(this::toCandidateResponse)
                .toList();
        job.setCandidateResponses(responses);
        return job;
    }

    private List<JobPost> withCandidateResponses(List<JobPost> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return jobs;
        }

        List<Long> jobIds = jobs.stream()
                .map(JobPost::getId)
                .filter(id -> id != null)
                .toList();
        Map<Long, List<Map<String, Object>>> responsesByJobId = new HashMap<>();
        for (JobInterest response : jobInterestRepository.findByJobIdIn(jobIds)) {
            responsesByJobId
                    .computeIfAbsent(response.getJobId(), ignored -> new ArrayList<>())
                    .add(toCandidateResponse(response));
        }

        return jobs.stream()
                .peek(job -> job.setCandidateResponses(
                        responsesByJobId.getOrDefault(job.getId(), List.of())
                ))
                .collect(Collectors.toList());
    }

    private Map<String, Object> toCandidateResponse(JobInterest response) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("candidateId", response.getCandidateId());
        data.put("fullName", response.getFullName());
        data.put("email", response.getEmail());
        data.put("phoneNumber", response.getPhoneNumber());
        data.put("skills", response.getSkills());
        data.put("experience", response.getExperience());
        data.put("status", response.getStatus());
        data.put("updatedAt", response.getUpdatedAt());
        return data;
    }
}
