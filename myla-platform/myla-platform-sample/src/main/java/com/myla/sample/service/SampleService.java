package com.myla.sample.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.myla.sample.entity.Sample;

public interface SampleService extends IService<Sample> {
    Sample register(Sample sample);
    void updateStatus(Long id, String fromStatus, String toStatus, String operator, String comment);
    Sample getByBarcode(String barcode);
    Sample getBySampleId(String sampleId);
    Page<Sample> pageByStatus(String status, int pageNum, int pageSize);
}
