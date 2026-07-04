package com.myla.result.controller;

import com.myla.common.core.util.R;
import com.myla.result.entity.OrganismResult;
import com.myla.result.service.ResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    @PutMapping("/{id}/review")
    public R<Void> review(@PathVariable Long id, @RequestBody Map<String, String> body) {
        resultService.reviewResult(
            id,
            body.get("action"),
            body.getOrDefault("reviewer", "SYSTEM")
        );
        return R.ok();
    }
}
