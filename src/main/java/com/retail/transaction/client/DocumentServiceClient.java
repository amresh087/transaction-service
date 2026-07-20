package com.retail.transaction.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.retail.transaction.dto.TransformationJobStatusRequest;

@FeignClient(name = "document-service", url = "${document.service.url}")
public interface DocumentServiceClient {

    @PutMapping("/documents/jobs/{jobId}")
    void updateJobStatus(@PathVariable("jobId") UUID jobId, @RequestBody TransformationJobStatusRequest request);
}
