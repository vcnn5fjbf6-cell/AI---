package com.company.procurement.modules.procurement.monthly.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyAggregateDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandCreateDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandQueryDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandQuantityUpdateDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandSupplementDTO;
import com.company.procurement.modules.procurement.monthly.vo.MonthlyAggregateResultVO;
import com.company.procurement.modules.procurement.monthly.vo.MonthlyDemandVO;

public interface MonthlyProcurementService {

    MonthlyDemandVO submitDemand(MonthlyDemandCreateDTO dto);

    PageResult<MonthlyDemandVO> pageMyDemands(MonthlyDemandQueryDTO queryDTO);

    PageResult<MonthlyDemandVO> pageCurrentMonthDemands(MonthlyDemandQueryDTO queryDTO);

    MonthlyDemandVO submitSupplementDemand(Long sourceDemandId, MonthlyDemandSupplementDTO dto);

    MonthlyDemandVO reduceOwnDemandQuantity(Long sourceDemandId, MonthlyDemandQuantityUpdateDTO dto);

    void deleteOwnDemand(Long sourceDemandId);

    PageResult<MonthlyDemandVO> pagePendingDemands(MonthlyDemandQueryDTO queryDTO);

    MonthlyAggregateResultVO aggregate(MonthlyAggregateDTO dto);
}
