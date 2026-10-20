package com.company.procurement.modules.procurement.labor.service;

import com.company.procurement.modules.procurement.labor.dto.PersonalLaborRequestDTO;
import com.company.procurement.modules.procurement.labor.vo.PersonalLaborItemStatusVO;
import com.company.procurement.modules.procurement.labor.vo.PersonalLaborSubmitResultVO;

import java.util.List;

public interface PersonalLaborService {

    List<PersonalLaborItemStatusVO> listMyItems();

    PersonalLaborSubmitResultVO submitRequest(PersonalLaborRequestDTO dto);

    PersonalLaborItemStatusVO confirmReceived(Long recordId);
}
