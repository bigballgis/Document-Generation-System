-- Link trial runs to stored sample documents (additive, nullable for historical rows).
ALTER TABLE test_results
    ADD COLUMN generated_document_id BIGINT REFERENCES generated_documents(id) ON DELETE SET NULL;

CREATE INDEX idx_test_results_generated_document_id
    ON test_results(generated_document_id);
