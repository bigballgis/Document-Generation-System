package com.docgen.service;

import com.docgen.dto.CreateExpressionRequest;
import com.docgen.dto.ExpressionDTO;
import com.docgen.dto.UpdateExpressionRequest;
import com.docgen.entity.Expression;
import com.docgen.entity.ExpressionType;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ExpressionRepository;
import com.docgen.repository.TemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service handling expression CRUD operations.
 */
@Service
public class ExpressionCrudService {

    private static final Logger log = LoggerFactory.getLogger(ExpressionCrudService.class);

    private final ExpressionRepository expressionRepository;
    private final TemplateRepository templateRepository;

    public ExpressionCrudService(ExpressionRepository expressionRepository,
                                 TemplateRepository templateRepository) {
        this.expressionRepository = expressionRepository;
        this.templateRepository = templateRepository;
    }

    @Transactional
    public ExpressionDTO createExpression(Long templateId, CreateExpressionRequest request) {
        templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));

        validateExpressionType(request.getExpressionType());

        Expression expr = new Expression();
        expr.setTemplateId(templateId);
        expr.setName(request.getName());
        expr.setExpressionType(request.getExpressionType());
        expr.setExpressionText(request.getExpressionText());
        expr.setDescription(request.getDescription());
        expr.setExecutionOrder(request.getExecutionOrder());

        Expression saved = expressionRepository.save(expr);
        log.info("Expression created: name={}, id={}, templateId={}", saved.getName(), saved.getId(), templateId);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public ExpressionDTO getExpression(Long id) {
        return toDTO(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ExpressionDTO> listExpressions(Long templateId) {
        templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));

        return expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(templateId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public ExpressionDTO updateExpression(Long id, UpdateExpressionRequest request) {
        Expression expr = findOrThrow(id);

        if (request.getName() != null) {
            expr.setName(request.getName());
        }
        if (request.getExpressionType() != null) {
            validateExpressionType(request.getExpressionType());
            expr.setExpressionType(request.getExpressionType());
        }
        if (request.getExpressionText() != null) {
            expr.setExpressionText(request.getExpressionText());
        }
        if (request.getDescription() != null) {
            expr.setDescription(request.getDescription());
        }
        if (request.getExecutionOrder() != null) {
            expr.setExecutionOrder(request.getExecutionOrder());
        }

        Expression saved = expressionRepository.save(expr);
        log.info("Expression updated: id={}", saved.getId());
        return toDTO(saved);
    }

    @Transactional
    public void deleteExpression(Long id) {
        Expression expr = findOrThrow(id);
        expressionRepository.delete(expr);
        log.info("Expression deleted: id={}", id);
    }

    // ── Private helpers ──

    private Expression findOrThrow(Long id) {
        return expressionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.EXPRESSION_EVALUATION_FAILED, "表达式不存在"));
    }

    private void validateExpressionType(String type) {
        try {
            ExpressionType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "不支持的表达式类型: " + type + "，支持的类型: JAVASCRIPT, EXCEL_FORMULA",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private ExpressionDTO toDTO(Expression expr) {
        return new ExpressionDTO(
                expr.getId(),
                expr.getTemplateId(),
                expr.getName(),
                expr.getExpressionType(),
                expr.getExpressionText(),
                expr.getDescription(),
                expr.getExecutionOrder(),
                expr.getCreatedAt()
        );
    }
}
