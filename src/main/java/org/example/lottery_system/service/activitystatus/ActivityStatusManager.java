package org.example.lottery_system.service.activitystatus;


import org.example.lottery_system.service.dto.ConvertActivityStatusDTO;

public interface ActivityStatusManager {
    void handlerEvent(ConvertActivityStatusDTO convertActivityStatusDTO);

    void rollbackHandleEvent(ConvertActivityStatusDTO convertActivityStatusDTO);
}
