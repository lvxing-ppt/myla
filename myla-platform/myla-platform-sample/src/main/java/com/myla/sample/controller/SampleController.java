package com.myla.sample.controller;

import com.myla.common.core.util.R;
import com.myla.sample.entity.Sample;
import com.myla.sample.service.SampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    @PostMapping
    public R<Sample> register(@RequestBody Sample sample) {
        return R.ok(sampleService.register(sample));
    }

    @GetMapping("/{id}")
    public R<Sample> getById(@PathVariable Long id) {
        return R.ok(sampleService.getById(id));
    }

    @GetMapping("/sampleId/{sampleId}")
    public R<Sample> getBySampleId(@PathVariable String sampleId) {
        return R.ok(sampleService.getBySampleId(sampleId));
    }

    @GetMapping("/barcode/{barcode}")
    public R<Sample> getByBarcode(@PathVariable String barcode) {
        return R.ok(sampleService.getByBarcode(barcode));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        sampleService.updateStatus(
            id,
            body.get("fromStatus"),
            body.get("toStatus"),
            body.getOrDefault("operator", "SYSTEM"),
            body.getOrDefault("comment", "")
        );
        return R.ok();
    }
}
