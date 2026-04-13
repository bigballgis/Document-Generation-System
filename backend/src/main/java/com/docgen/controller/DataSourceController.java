package com.docgen.controller;

import com.docgen.dto.CreateDataSourceRequest;
import com.docgen.dto.DataSourceDTO;
import com.docgen.dto.UpdateDataSourceRequest;
import com.docgen.entity.DataSourceType;
import com.docgen.service.DataSourceCrudService;
import com.docgen.service.DatabaseDataSourceService;
import com.docgen.service.HttpApiDataSourceService;
import com.docgen.service.InternalSystemDataSourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for data source CRUD and connection testing.
 */
@RestController
public class DataSourceController {

    private final DataSourceCrudService dataSourceCrudService;
    private final HttpApiDataSourceService httpApiDataSourceService;
    private final DatabaseDataSourceService databaseDataSourceService;
    private final InternalSystemDataSourceService internalSystemDataSourceService;

    public DataSourceController(DataSourceCrudService dataSourceCrudService,
                                HttpApiDataSourceService httpApiDataSourceService,
                                DatabaseDataSourceService databaseDataSourceService,
                                InternalSystemDataSourceService internalSystemDataSourceService) {
        this.dataSourceCrudService = dataSourceCrudService;
        this.httpApiDataSourceService = httpApiDataSourceService;
        this.databaseDataSourceService = databaseDataSourceService;
        this.internalSystemDataSourceService = internalSystemDataSourceService;
    }

    @PostMapping("/api/templates/{templateId}/data-sources")
    public ResponseEntity<DataSourceDTO> createDataSource(
            @PathVariable Long templateId,
            @Valid @RequestBody CreateDataSourceRequest request) {
        DataSourceDTO created = dataSourceCrudService.createDataSource(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/api/templates/{templateId}/data-sources")
    public ResponseEntity<List<DataSourceDTO>> listDataSources(@PathVariable Long templateId) {
        return ResponseEntity.ok(dataSourceCrudService.listDataSources(templateId));
    }

    @GetMapping("/api/data-sources/{id}")
    public ResponseEntity<DataSourceDTO> getDataSource(@PathVariable Long id) {
        return ResponseEntity.ok(dataSourceCrudService.getDataSource(id));
    }

    @PutMapping("/api/data-sources/{id}")
    public ResponseEntity<DataSourceDTO> updateDataSource(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDataSourceRequest request) {
        return ResponseEntity.ok(dataSourceCrudService.updateDataSource(id, request));
    }

    @DeleteMapping("/api/data-sources/{id}")
    public ResponseEntity<Void> deleteDataSource(@PathVariable Long id) {
        dataSourceCrudService.deleteDataSource(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Test data source connection.
     * Delegates to the appropriate service based on data source type.
     */
    @PostMapping("/api/data-sources/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        DataSourceDTO ds = dataSourceCrudService.getDataSourceRaw(id);
        String type = ds.getType();

        if (DataSourceType.HTTP_API.name().equals(type)) {
            Map<String, Object> result = httpApiDataSourceService.testConnection(ds.getConfigJson());
            return ResponseEntity.ok(result);
        }

        if (DataSourceType.DATABASE.name().equals(type)) {
            Map<String, Object> result = databaseDataSourceService.testConnection(ds.getConfigJson());
            return ResponseEntity.ok(result);
        }

        if (DataSourceType.INTERNAL_SYSTEM.name().equals(type)) {
            Map<String, Object> result = internalSystemDataSourceService.testConnection(ds.getConfigJson());
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "该数据源类型的连接测试功能尚未实现"
        ));
    }
}
