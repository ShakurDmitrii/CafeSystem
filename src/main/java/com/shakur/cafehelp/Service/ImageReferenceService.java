package com.shakur.cafehelp.Service;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

@Service
public class ImageReferenceService {

    private static final Field<String> IMAGE_URL = DSL.field(DSL.name("image_url"), String.class);
    private final DSLContext dsl;

    public ImageReferenceService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean isReferenced(String objectKey) {
        String urlSuffix = "/" + objectKey;
        return isReferencedIn(DSL.table(DSL.name("sales", "product")), urlSuffix)
                || isReferencedIn(DSL.table(DSL.name("sales", "dish")), urlSuffix)
                || isReferencedIn(DSL.table(DSL.name("sales", "dish_set")), urlSuffix);
    }

    private boolean isReferencedIn(Table<?> table, String urlSuffix) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(table)
                        .where(IMAGE_URL.isNotNull().and(IMAGE_URL.endsWith(urlSuffix)))
        );
    }
}
