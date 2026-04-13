package com.docgen.service;

import com.docgen.dto.CreateExpressionRequest;
import com.docgen.dto.ExpressionDTO;
import com.docgen.dto.UpdateExpressionRequest;
import com.docgen.entity.Expression;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ExpressionRepository;
import com.docgen.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpressionCrudServiceTest {

    @Mock
    private ExpressionRepository expressionRepository;

    @Mock
    private TemplateRepository templateRepository;

    @InjectMocks
    private ExpressionCrudService service;

    private Template template;

    @BeforeEach
    void setUp() {
        template = new Template();
        template.setId(1L);
        template.setTenantId(10L);
        template.setName("Test Template");
    }

    // ── createExpression ──

    @Test
    void createExpression_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(expressionRepository.save(any(Expression.class))).thenAnswer(inv -> {
            Expression e = inv.getArgument(0);
            e.setId(100L);
            e.setCreatedAt(Instant.now());
            return e;
        });

        CreateExpressionRequest request = new CreateExpressionRequest();
        request.setName("totalPrice");
        request.setExpressionType("JAVASCRIPT");
        request.setExpressionText("data.price * data.quantity");
        request.setDescription("Calculate total price");
        request.setExecutionOrder(1);

        ExpressionDTO result = service.createExpression(1L, request);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(1L, result.getTemplateId());
        assertEquals("totalPrice", result.getName());
        assertEquals("JAVASCRIPT", result.getExpressionType());
        assertEquals("data.price * data.quantity", result.getExpressionText());
        assertEquals("Calculate total price", result.getDescription());
        assertEquals(1, result.getExecutionOrder());
    }

    @Test
    void createExpression_templateNotFound_throws() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        CreateExpressionRequest request = new CreateExpressionRequest();
        request.setName("expr");
        request.setExpressionType("JAVASCRIPT");
        request.setExpressionText("1+1");

        assertThrows(ResourceNotFoundException.class, () -> service.createExpression(999L, request));
    }

    @Test
    void createExpression_invalidType_throws() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        CreateExpressionRequest request = new CreateExpressionRequest();
        request.setName("expr");
        request.setExpressionType("PYTHON");
        request.setExpressionText("1+1");

        assertThrows(BusinessException.class, () -> service.createExpression(1L, request));
    }

    @Test
    void createExpression_excelFormula_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(expressionRepository.save(any(Expression.class))).thenAnswer(inv -> {
            Expression e = inv.getArgument(0);
            e.setId(101L);
            e.setCreatedAt(Instant.now());
            return e;
        });

        CreateExpressionRequest request = new CreateExpressionRequest();
        request.setName("sumTotal");
        request.setExpressionType("EXCEL_FORMULA");
        request.setExpressionText("SUM(A1:A10)");

        ExpressionDTO result = service.createExpression(1L, request);

        assertEquals("EXCEL_FORMULA", result.getExpressionType());
        assertEquals("SUM(A1:A10)", result.getExpressionText());
    }

    // ── getExpression ──

    @Test
    void getExpression_success() {
        Expression expr = createSampleExpression();
        when(expressionRepository.findById(100L)).thenReturn(Optional.of(expr));

        ExpressionDTO result = service.getExpression(100L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("totalPrice", result.getName());
    }

    @Test
    void getExpression_notFound_throws() {
        when(expressionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getExpression(999L));
    }

    // ── listExpressions ──

    @Test
    void listExpressions_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        Expression e1 = createSampleExpression();
        e1.setExecutionOrder(0);
        Expression e2 = createSampleExpression();
        e2.setId(101L);
        e2.setName("discount");
        e2.setExecutionOrder(1);
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L))
                .thenReturn(List.of(e1, e2));

        List<ExpressionDTO> result = service.listExpressions(1L);

        assertEquals(2, result.size());
        assertEquals("totalPrice", result.get(0).getName());
        assertEquals("discount", result.get(1).getName());
    }

    @Test
    void listExpressions_templateNotFound_throws() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.listExpressions(999L));
    }

    // ── updateExpression ──

    @Test
    void updateExpression_success() {
        Expression expr = createSampleExpression();
        when(expressionRepository.findById(100L)).thenReturn(Optional.of(expr));
        when(expressionRepository.save(any(Expression.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpressionRequest request = new UpdateExpressionRequest();
        request.setName("updatedName");
        request.setExpressionText("data.price * 2");
        request.setExecutionOrder(5);

        ExpressionDTO result = service.updateExpression(100L, request);

        assertEquals("updatedName", result.getName());
        assertEquals("data.price * 2", result.getExpressionText());
        assertEquals(5, result.getExecutionOrder());
    }

    @Test
    void updateExpression_partialUpdate_success() {
        Expression expr = createSampleExpression();
        when(expressionRepository.findById(100L)).thenReturn(Optional.of(expr));
        when(expressionRepository.save(any(Expression.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpressionRequest request = new UpdateExpressionRequest();
        request.setDescription("Updated description only");

        ExpressionDTO result = service.updateExpression(100L, request);

        assertEquals("totalPrice", result.getName()); // unchanged
        assertEquals("Updated description only", result.getDescription());
    }

    @Test
    void updateExpression_invalidType_throws() {
        Expression expr = createSampleExpression();
        when(expressionRepository.findById(100L)).thenReturn(Optional.of(expr));

        UpdateExpressionRequest request = new UpdateExpressionRequest();
        request.setExpressionType("INVALID");

        assertThrows(BusinessException.class, () -> service.updateExpression(100L, request));
    }

    @Test
    void updateExpression_notFound_throws() {
        when(expressionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateExpression(999L, new UpdateExpressionRequest()));
    }

    // ── deleteExpression ──

    @Test
    void deleteExpression_success() {
        Expression expr = createSampleExpression();
        when(expressionRepository.findById(100L)).thenReturn(Optional.of(expr));

        service.deleteExpression(100L);

        verify(expressionRepository).delete(expr);
    }

    @Test
    void deleteExpression_notFound_throws() {
        when(expressionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteExpression(999L));
    }

    // ── Helpers ──

    private Expression createSampleExpression() {
        Expression expr = new Expression();
        expr.setId(100L);
        expr.setTemplateId(1L);
        expr.setName("totalPrice");
        expr.setExpressionType("JAVASCRIPT");
        expr.setExpressionText("data.price * data.quantity");
        expr.setDescription("Calculate total price");
        expr.setExecutionOrder(0);
        expr.setCreatedAt(Instant.now());
        return expr;
    }
}
