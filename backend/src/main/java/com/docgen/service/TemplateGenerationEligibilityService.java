package com.docgen.service;

import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Single place for rules about whether a template may produce persisted generated documents.
 * <p>
 * In-memory validation flows such as {@link DocumentGeneratorService#renderForTemplateTest(long, java.util.Map)}
 * intentionally skip this check so DRAFT templates can be exercised in the template test UI.
 */
@Service
public class TemplateGenerationEligibilityService {

    public static final String ACTIVE_TEMPLATE_STATUS = "ACTIVE";

    /**
     * Requires {@link Template#getStatus()} to be {@value #ACTIVE_TEMPLATE_STATUS} before running
     * {@link DocumentGeneratorService#generateDocument(Long, com.docgen.dto.GenerateDocumentRequest, Integer)} or equivalent
     * persisted output paths.
     *
     * @param template   loaded template (must not be null)
     * @param templateId id used in error messages
     */
    public void requireActiveForDocumentGeneration(Template template, long templateId) {
        if (!ACTIVE_TEMPLATE_STATUS.equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                    "Template must be ACTIVE for document generation (id=" + templateId
                            + ", status=" + template.getStatus() + ")",
                    HttpStatus.NOT_FOUND);
        }
    }
}
