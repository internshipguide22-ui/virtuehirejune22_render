package com.virtuehire.repository;

import com.virtuehire.model.JobPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPostRepository extends JpaRepository<JobPost, Long> {
    List<JobPost> findAllByOrderByCreatedAtDesc();
}
