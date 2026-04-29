ALTER TABLE test_results
    ADD COLUMN generated_pdf_document_id BIGINT REFERENCES generated_documents (id);

CREATE INDEX idx_test_results_generated_pdf_document_id
    ON test_results (generated_pdf_document_id);
