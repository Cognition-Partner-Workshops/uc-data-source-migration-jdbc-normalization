package com.workshop.loanservice.dto;

import java.util.List;

public class PaymentHistoryResponse {

    private List<PaymentDto> payments;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public PaymentHistoryResponse(List<PaymentDto> payments, int page, int size,
                                  long totalElements, int totalPages) {
        this.payments = payments;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<PaymentDto> getPayments() { return payments; }
    public void setPayments(List<PaymentDto> payments) { this.payments = payments; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
