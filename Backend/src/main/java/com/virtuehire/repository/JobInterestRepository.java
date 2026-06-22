package com.virtuehire.repository;

import com.virtuehire.model.JobInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobInterestRepository extends JpaRepository<JobInterest, Long> {
    List<JobInterest> findByJobIdIn(Collection<Long> jobIds);
    Optional<JobInterest> findByJobIdAndCandidateId(Long jobId, Long candidateId);
}
