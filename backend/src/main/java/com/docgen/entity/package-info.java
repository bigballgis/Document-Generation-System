/**
 * JPA entity classes for the Document Generation System.
 * The tenantFilter FilterDef is defined here once to avoid duplicate definition errors in Hibernate 6.
 */
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
package com.docgen.entity;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
