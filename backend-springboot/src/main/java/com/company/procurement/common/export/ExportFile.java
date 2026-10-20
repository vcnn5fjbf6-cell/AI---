package com.company.procurement.common.export;

public record ExportFile(String fileName, String contentType, byte[] content) {
}
