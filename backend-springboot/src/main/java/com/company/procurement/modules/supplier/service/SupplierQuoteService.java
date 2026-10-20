package com.company.procurement.modules.supplier.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.supplier.dto.SupplierAwardConfirmDTO;
import com.company.procurement.modules.supplier.dto.SupplierAwardWaiveDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuotePageQueryDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuoteSubmitDTO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteRequestVO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteVO;

public interface SupplierQuoteService {

    PageResult<SupplierQuoteRequestVO> pageQuoteRequests(SupplierQuotePageQueryDTO queryDTO);

    SupplierQuoteVO submitQuote(SupplierQuoteSubmitDTO dto);

    PurchaseRequestVO quoteRequestDetail(Long purchaseRequestId);

    SupplierQuoteVO myQuote(Long purchaseRequestId);

    SupplierQuoteVO awardConfirmationDetail(Long purchaseRequestId);

    SupplierQuoteVO confirmAward(Long purchaseRequestId, SupplierAwardConfirmDTO dto);

    SupplierQuoteVO waiveAward(Long purchaseRequestId, SupplierAwardWaiveDTO dto);

    void reassignPendingAwardsForSupplier(Long supplierId, String reason);

    ExportFile exportAward(Long purchaseRequestId);
}
