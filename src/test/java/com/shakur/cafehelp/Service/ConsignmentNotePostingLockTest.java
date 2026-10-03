package com.shakur.cafehelp.Service;

import jooqdata.tables.Consignmentnote;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ConsignmentNotePostingLockTest {

    private static final DSLContext DATA = DSL.using(SQLDialect.POSTGRES);

    @Test
    void noteIsLockedBeforeCheckingWhetherItIsAlreadyPosted() {
        List<String> statements = new ArrayList<>();
        MockConnection connection = new MockConnection(context -> {
            String sql = context.sql().toLowerCase();
            statements.add(sql);
            if (sql.contains("from \"sales\".\"consignmentnote\"")) {
                var result = DATA.newResult(Consignmentnote.CONSIGNMENTNOTE.fields());
                var note = DATA.newRecord(Consignmentnote.CONSIGNMENTNOTE);
                note.setConsignmentid(5);
                note.setSupplierid(1);
                note.setDate(LocalDate.of(2026, 9, 30));
                result.add(note);
                return new MockResult[]{new MockResult(1, result)};
            }
            // select exists(...) по inventory_documents: накладная уже проведена
            var exists = DATA.newResult(DSL.field("exists", Boolean.class));
            exists.add(DATA.newRecord(DSL.field("exists", Boolean.class)).values(true));
            return new MockResult[]{new MockResult(1, exists)};
        });
        MovementService movements = mock(MovementService.class);
        ConsignmentNoteService service = new ConsignmentNoteService(
                DSL.using(connection, SQLDialect.POSTGRES), movements
        );

        assertThatThrownBy(() -> service.postConsignmentNote(5, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже проведена");

        assertThat(statements.get(0)).contains("from \"sales\".\"consignmentnote\"").endsWith("for update");
        verifyNoInteractions(movements);
    }
}
