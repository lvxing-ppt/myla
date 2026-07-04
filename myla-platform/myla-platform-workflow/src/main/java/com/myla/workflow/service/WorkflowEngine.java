package com.myla.workflow.service;

import com.myla.workflow.entity.CriticalValueAlert;
import com.myla.workflow.entity.WorkflowRule;

public interface WorkflowEngine {
    void executeRule(WorkflowRule rule, Object context);
    CriticalValueAlert createCriticalAlert(CriticalValueAlert alert);
}
