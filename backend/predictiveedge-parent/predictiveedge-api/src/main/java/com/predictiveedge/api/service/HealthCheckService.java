package com.predictiveedge.api.service;

import org.springframework.stereotype.Service;

import com.predictiveedge.common.Result;
import com.predictiveedge.domain.model.ApplicationStatus;
import com.predictiveedge.domain.port.HealthCheckUseCase;

@Service
public class HealthCheckService implements HealthCheckUseCase {

    @Override
    public Result<ApplicationStatus> health() {
        return Result.success(ApplicationStatus.UP);
    }
}
