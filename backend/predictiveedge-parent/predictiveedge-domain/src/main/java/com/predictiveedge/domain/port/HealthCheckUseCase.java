package com.predictiveedge.domain.port;

import com.predictiveedge.common.Result;
import com.predictiveedge.domain.model.ApplicationStatus;

public interface HealthCheckUseCase {
    Result<ApplicationStatus> health();
}
