package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ConsignmentNoteDTO;
import jooqdata.tables.Consignmentnote;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConsignmentNoteService {
    private DSLContext dsl;
    public ConsignmentNoteService(DSLContext dsl) {this.dsl = dsl;}
    public List<ConsignmentNoteDTO> getConsignmentNotes() {
        return dsl.selectFrom(Consignmentnote.CONSIGNMENTNOTE)
                .fetch()
                .stream()
                .map(record->
                {
                    ConsignmentNoteDTO dto = new ConsignmentNoteDTO();
                    dto.consignmentId = record.getConsignmentid();
                    dto.supplierId = record.getSupplierid();
                    dto.amount = record.getAmount();
                    dto.date = LocalDateTime.parse(record.getData());
                    return dto;
                }).toList();
    }
}
