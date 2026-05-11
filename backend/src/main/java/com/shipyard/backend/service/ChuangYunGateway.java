package com.shipyard.backend.service;

import com.shipyard.backend.persistence.entity.UploadRecordEntity;

public interface ChuangYunGateway {
    GatewayResult write(UploadRecordEntity uploadRecord);

    record GatewayResult(boolean success, String message) {}
}
