package com.financeAssitant.FinBrains.service;

import com.financeAssitant.FinBrains.dto.CreateExpenseRequest;
import com.financeAssitant.FinBrains.dto.ExpenseFilterRequest;
import com.financeAssitant.FinBrains.dto.ExpenseResponse;
import com.financeAssitant.FinBrains.dto.UpdateExpenseRequest;
import com.financeAssitant.FinBrains.entity.Expense;
import com.financeAssitant.FinBrains.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    public ExpenseResponse createExpense(String userId, CreateExpenseRequest request) {
        // Build expense entity
        Expense expense = Expense.builder()
                .userId(userId)
                .amount(request.getAmount())
                .description(request.getDescription())
                .date(request.getDate() != null ? request.getDate() : LocalDateTime.now())
                .subcategory(request.getSubcategory())
                .tags(request.getTags())
                .category(Expense.Category.builder()
                        .id(request.getCategoryId())
                        .name(request.getCategoryName())
                        .icon("💰") // Default icon
                        .color("#007bff") // Default color
                        .build())
                .paymentMethod(Expense.PaymentMethod.builder()
                        .type(request.getPaymentType() != null ? request.getPaymentType() : "cash")
                        .provider(request.getPaymentProvider())
                        .lastFourDigits(request.getLastFourDigits())
                        .build())
                .recurring(Expense.Recurring.builder()
                        .isRecurring(request.getIsRecurring() != null ? request.getIsRecurring() : false)
                        .frequency(request.getRecurringFrequency())
                        .build())
                .metadata(Expense.Metadata.builder()
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .createdBy("user")
                        .source("manual")
                        .version(1)
                        .build())
                .build();

        Expense savedExpense = expenseRepository.save(expense);
        return convertToResponse(savedExpense);
    }

    public ExpenseResponse updateExpense(String userId, String expenseId, UpdateExpenseRequest request) {
        Optional<Expense> expenseOptional = expenseRepository.findByIdAndUserId(expenseId, userId);

        if (expenseOptional.isEmpty()) {
            throw new RuntimeException("Expense not found or access denied!");
        }

        Expense expense = expenseOptional.get();

        // Update fields only if provided
        if (request.getAmount() != null) {
            expense.setAmount(request.getAmount());
        }
        if (request.getDescription() != null) {
            expense.setDescription(request.getDescription());
        }
        if (request.getDate() != null) {
            expense.setDate(request.getDate());
        }
        if (request.getCategoryId() != null || request.getCategoryName() != null) {
            if (expense.getCategory() == null) {
                expense.setCategory(new Expense.Category());
            }
            if (request.getCategoryId() != null) {
                expense.getCategory().setId(request.getCategoryId());
            }
            if (request.getCategoryName() != null) {
                expense.getCategory().setName(request.getCategoryName());
            }
        }
        if (request.getSubcategory() != null) {
            expense.setSubcategory(request.getSubcategory());
        }
        if (request.getTags() != null) {
            expense.setTags(request.getTags());
        }

        // Update payment method
        if (request.getPaymentType() != null || request.getPaymentProvider() != null) {
            if (expense.getPaymentMethod() == null) {
                expense.setPaymentMethod(new Expense.PaymentMethod());
            }
            if (request.getPaymentType() != null) {
                expense.getPaymentMethod().setType(request.getPaymentType());
            }
            if (request.getPaymentProvider() != null) {
                expense.getPaymentMethod().setProvider(request.getPaymentProvider());
            }
            if (request.getLastFourDigits() != null) {
                expense.getPaymentMethod().setLastFourDigits(request.getLastFourDigits());
            }
        }

        // Update recurring info
        if (request.getIsRecurring() != null || request.getRecurringFrequency() != null) {
            if (expense.getRecurring() == null) {
                expense.setRecurring(new Expense.Recurring());
            }
            if (request.getIsRecurring() != null) {
                expense.getRecurring().setIsRecurring(request.getIsRecurring());
            }
            if (request.getRecurringFrequency() != null) {
                expense.getRecurring().setFrequency(request.getRecurringFrequency());
            }
        }

        // Update metadata
        expense.getMetadata().setUpdatedAt(LocalDateTime.now());
        expense.getMetadata().setVersion(expense.getMetadata().getVersion() + 1);

        Expense savedExpense = expenseRepository.save(expense);
        return convertToResponse(savedExpense);
    }

    public ExpenseResponse getExpenseById(String userId, String expenseId) {
        Optional<Expense> expenseOptional = expenseRepository.findByIdAndUserId(expenseId, userId);

        if (expenseOptional.isEmpty()) {
            throw new RuntimeException("Expense not found or access denied!");
        }

        return convertToResponse(expenseOptional.get());
    }

    public List<ExpenseResponse> getUserExpenses(String userId) {
        List<Expense> expenses = expenseRepository.findByUserIdOrderByDateDesc(userId);
        return expenses.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public void deleteExpense(String userId, String expenseId) {
        Optional<Expense> expenseOptional = expenseRepository.findByIdAndUserId(expenseId, userId);

        if (expenseOptional.isEmpty()) {
            throw new RuntimeException("Expense not found or access denied!");
        }

        expenseRepository.deleteByIdAndUserId(expenseId, userId);
    }

    public List<ExpenseResponse> getExpensesByCategory(String userId, String categoryId) {
        List<Expense> expenses = expenseRepository.findByUserIdAndCategory_IdOrderByDateDesc(userId, categoryId);
        return expenses.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public Page<ExpenseResponse> getUserExpensesPaginated(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));
        Page<Expense> expensePage = expenseRepository.findByUserIdOrderByDateDesc(userId, pageable);

        return expensePage.map(this::convertToResponse);
    }

    public Page<ExpenseResponse> getFilteredExpenses(String userId, ExpenseFilterRequest filterRequest) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(filterRequest.getSortDirection()) ?
                        Sort.Direction.DESC : Sort.Direction.ASC,
                filterRequest.getSortBy()
        );

        Pageable pageable = PageRequest.of(
                filterRequest.getPage(),
                filterRequest.getSize(),
                sort
        );

        Page<Expense> expensePage = expenseRepository.findByUserIdWithFilters(
                userId,
                filterRequest.getStartDate(),
                filterRequest.getEndDate(),
                filterRequest.getCategoryId(),
                filterRequest.getMinAmount(),
                filterRequest.getMaxAmount(),
                filterRequest.getSearchTerm(),
                pageable
        );

        return expensePage.map(this::convertToResponse);
    }


    private ExpenseResponse convertToResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .subcategory(expense.getSubcategory())
                .date(expense.getDate())
                .tags(expense.getTags())
                .isRecurring(expense.getRecurring() != null ? expense.getRecurring().getIsRecurring() : false)
                .recurringFrequency(expense.getRecurring() != null ? expense.getRecurring().getFrequency() : null)
                .createdAt(expense.getMetadata() != null ? expense.getMetadata().getCreatedAt() : null)
                .updatedAt(expense.getMetadata() != null ? expense.getMetadata().getUpdatedAt() : null)
                .category(expense.getCategory() != null ?
                        ExpenseResponse.CategoryResponse.builder()
                                .id(expense.getCategory().getId())
                                .name(expense.getCategory().getName())
                                .icon(expense.getCategory().getIcon())
                                .color(expense.getCategory().getColor())
                                .build() : null)
                .paymentMethod(expense.getPaymentMethod() != null ?
                        ExpenseResponse.PaymentMethodResponse.builder()
                                .type(expense.getPaymentMethod().getType())
                                .provider(expense.getPaymentMethod().getProvider())
                                .lastFourDigits(expense.getPaymentMethod().getLastFourDigits())
                                .build() : null)
                .build();
    }
}

