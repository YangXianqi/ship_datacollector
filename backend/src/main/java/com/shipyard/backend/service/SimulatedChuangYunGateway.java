package com.shipyard.backend.service;

import com.shipyard.backend.persistence.entity.UploadRecordEntity;
import org.springframework.stereotype.Component;

@Component
public class SimulatedChuangYunGateway implements ChuangYunGateway {

    @Override
    public GatewayResult write(UploadRecordEntity uploadRecord) {
        if (uploadRecord.getLocationName().toLowerCase().contains("fail")) {
            return new GatewayResult(false, "模拟氚云写入失败，请稍后重试");
        }
        return new GatewayResult(true, "已写入氚云");
    }
}
