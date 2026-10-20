package com.company.procurement.modules.procurement.bi.service;

import com.company.procurement.modules.procurement.bi.dto.ProcurementBiDashboardSaveDTO;
import com.company.procurement.modules.procurement.bi.dto.ProcurementBiQueryDTO;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiDashboardVO;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiDatasetVO;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiQueryResultVO;

import java.util.List;

public interface ProcurementBiService {

    List<ProcurementBiDatasetVO> listDatasets();

    List<ProcurementBiDatasetVO.FieldVO> listFields(String datasetCode);

    ProcurementBiQueryResultVO query(ProcurementBiQueryDTO dto);

    ProcurementBiDashboardVO getDefaultDashboard();

    ProcurementBiDashboardVO saveDefaultDashboard(ProcurementBiDashboardSaveDTO dto);
}
