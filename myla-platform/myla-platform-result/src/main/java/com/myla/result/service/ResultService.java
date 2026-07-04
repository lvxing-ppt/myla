package com.myla.result.service;

import com.myla.common.api.dto.UnifiedResult;
import com.myla.result.entity.OrganismResult;

public interface ResultService {
    OrganismResult saveResult(UnifiedResult unifiedResult);
    void reviewResult(Long id, String action, String reviewer);
}
